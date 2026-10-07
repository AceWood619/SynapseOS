package com.acewood.synapse.core

import android.app.ActivityManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.acewood.synapse.logic.ApiRouter
import com.acewood.synapse.logic.BleTracker
import com.acewood.synapse.logic.EntityIds
import com.acewood.synapse.logic.Json
import com.acewood.synapse.logic.NodeActions
import com.acewood.synapse.logic.NodeConfig
import com.acewood.synapse.logic.NodePublisher
import com.acewood.synapse.logic.NodeSensors
import com.acewood.synapse.logic.PresenceFusion
import com.acewood.synapse.logic.PublishPolicy
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The always-on "sense organ": reads the phone's sensors, fuses presence,
 * publishes to Home Assistant, and serves the LAN control API.
 */
class NodeService : Service(), SensorEventListener, NodeActions {
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private val net = Executors.newSingleThreadExecutor()

    private var cfg: NodeConfig? = null
    private var ha: HaClient? = null
    private var policy = PublishPolicy(60_000)
    private var publisher: NodePublisher? = null
    private val presence = PresenceFusion()
    private var server: ControlServer? = null
    private var speech: Speech? = null
    private var bleTracker: BleTracker? = null
    private var ble: BleScanner? = null
    private var camera: Snapshotter? = null
    private val startedMs = SystemClock.elapsedRealtime()

    // Latest readings (written on the sensor/receiver threads, read on the node thread).
    @Volatile private var lux: Double? = null
    @Volatile private var pressure: Double? = null
    @Volatile private var near: Boolean? = null
    @Volatile private var battery: Map<String, Any?> = emptyMap()
    private var gravity = DoubleArray(3)
    private var lastMotionMs = 0L
    private var luxBaseline: Double? = null
    @Volatile private var lastPublishOk = 0L
    @Volatile private var publishErrors = 0
    @Volatile private var thermalReadable = true  // stop polling after first SELinux denial (avc spam)

    private val busEvents: (NodeBus.Event) -> Unit = { e ->
        if (e == NodeBus.Event.TOUCH) presence.fire(PresenceFusion.Signal.TOUCH, now())
    }
    private val busCommands: (NodeBus.Command) -> Unit = { c ->
        if (c == NodeBus.Command.CONFIG_CHANGED) handler.post { reloadConfig() }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, i: Intent) = updateBattery(i)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground("Starting…")
        thread = HandlerThread("synapse-node").also { it.start() }
        handler = Handler(thread.looper)
        speech = Speech(this)
        NodeBus.onEvent(busEvents)
        NodeBus.onCommand(busCommands)
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { updateBattery(it) }
        val sm = getSystemService(SensorManager::class.java)
        for (type in intArrayOf(Sensor.TYPE_LIGHT, Sensor.TYPE_PRESSURE, Sensor.TYPE_PROXIMITY, Sensor.TYPE_ACCELEROMETER)) {
            sm.getDefaultSensor(type)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, handler) }
        }
        handler.post { reloadConfig() }
        handler.postDelayed(loop, 3_000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        getSystemService(SensorManager::class.java).unregisterListener(this)
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        NodeBus.removeEvent(busEvents)
        NodeBus.removeCommand(busCommands)
        server?.stop()
        ble?.stop()
        camera?.shutdown()
        speech?.shutdown()
        net.shutdownNow()
        thread.quitSafely()
        super.onDestroy()
    }

    private fun now() = SystemClock.elapsedRealtime()

    // ---------- config ----------

    private fun reloadConfig() {
        if (ConfigStore.importIfPresent(this)) NodeBus.send(NodeBus.Command.RELOAD)
        val c = ConfigStore.load(this)
        if (c == cfg && c != null) return
        cfg = c
        server?.stop(); server = null
        ble?.stop(); ble = null; bleTracker = null
        camera?.shutdown(); camera = null
        if (c == null) {
            ha = null
            goForeground("Not configured — waiting for config.json")
            return
        }
        ha = HaClient(c)
        policy = PublishPolicy(c.heartbeatSeconds * 1000L)
        publisher = NodePublisher(c.nodeId, c.room, policy)
        if (c.apiKey.isNotEmpty()) {
            server = ControlServer(c.apiPort, ApiRouter(c.apiKey, this)).also { it.start() }
        }
        if (c.bleKnown.isNotEmpty()) {
            val t = BleTracker(c.bleKnown)
            bleTracker = t
            ble = BleScanner(this, t).also { it.start() }
        }
        if (c.camera.isNotEmpty()) camera = Snapshotter(this, c.camera)
        goForeground("${c.nodeId} · ${c.room}")
    }

    // ---------- main loop ----------

    private var tick = 0L
    private val loop = object : Runnable {
        override fun run() {
            try { step() } catch (e: Exception) { Log.w(SynapseApp.TAG, "loop error", e) }
            handler.postDelayed(this, 5_000)
        }
    }

    private fun step() {
        tick++
        if (tick % 6 == 0L) reloadConfig() // pick up a newly pushed config.json within ~30 s
        val c = cfg ?: return
        val client = ha ?: return
        val t = now()
        ble?.let { if (tick % 240 == 0L || !it.running) it.restart() } // Android degrades scans older than 30 min
        if (tick % 6 == 0L) speech?.reinitIfNeeded()   // retry TTS until a voice engine is ready
        if (bleTracker?.snapshot(t)?.anyKnownNear == true) presence.fire(PresenceFusion.Signal.BLE, t)
        val pub = publisher ?: return
        val toSend = pub.decide(readings(t), t)
        if (toSend.isEmpty()) return
        net.execute {
            val wasReachable = client.reachable
            var ok = true
            for (post in toSend) if (!client.postState(post.entityId, post.body)) { ok = false; break }
            if (ok) {
                lastPublishOk = System.currentTimeMillis()
                if (!wasReachable) handler.post { pub.resetAfterReconnect() } // HA came back: re-send everything
            } else {
                publishErrors++
                handler.post { pub.resetAfterReconnect() } // retry the whole set next time
            }
        }
    }

    private fun readings(t: Long): Map<String, Pair<Any?, Map<String, Any?>>> {
        val p = presence.evaluate(t)
        val b = battery
        val c = cfg
        return linkedMapOf(
            "battery" to (b["level"] to emptyMap()),
            "battery_temp" to (b["temp"] to emptyMap()),
            "battery_voltage" to (b["voltage"] to emptyMap()),
            "charging" to (b["charging"] to emptyMap()),
            "power_source" to (b["source"] to emptyMap()),
            "illuminance" to (lux to emptyMap()),
            "pressure" to (pressure to emptyMap()),
            "proximity" to (near to emptyMap()),
            "occupancy" to (p.occupied to mapOf("confidence" to p.confidence, "signals" to p.signals)),
            "wifi_rssi" to (wifiRssi() to emptyMap()),
            "screen" to (NodeBus.screenMode to emptyMap()),
            "uptime" to (((t - startedMs) / 60_000).toDouble() to emptyMap()),
            "mem_free" to (memFreeMb() to emptyMap()),
            "cpu_temp" to (cpuTemp() to emptyMap()),
            "ble_devices" to (bleTracker?.snapshot(t)?.devicesSeen?.toDouble() to emptyMap()),
            "ble_known" to bleKnownReading(t),
            "status" to ("online" to mapOf(
                "version" to BuildConfigInfo.versionName(this),
                "ip" to ipAddress(),
                "api_port" to (if (server != null) c?.apiPort else null),
                "tts_engine" to speech?.engine,
                "device_owner" to Kiosk.isDeviceOwner(this),
            )),
        )
    }

    private fun bleKnownReading(t: Long): Pair<Any?, Map<String, Any?>> {
        val snap = bleTracker?.snapshot(t) ?: return null to emptyMap()
        val near = snap.known.filter { it.near }.map { it.name }
        val devices = snap.known.map { mapOf("name" to it.name, "rssi" to it.rssi, "near" to it.near, "age_s" to it.ageS) }
        return (if (near.isEmpty()) "none" else near.joinToString(", ")) to mapOf("devices" to devices)
    }

    // ---------- sensors ----------

    override fun onSensorChanged(e: SensorEvent) {
        val t = now()
        when (e.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                val v = e.values[0].toDouble()
                lux = v
                val base = luxBaseline
                // A big, sudden jump (lamp switched on/off) counts as a weak presence hint.
                if (base != null && abs(v - base) > maxOf(10.0, base * 0.5)) presence.fire(PresenceFusion.Signal.LIGHT_CHANGE, t)
                luxBaseline = if (base == null) v else base * 0.9 + v * 0.1
            }
            Sensor.TYPE_PRESSURE -> pressure = e.values[0].toDouble()
            Sensor.TYPE_PROXIMITY -> {
                val isNear = e.values[0] < e.sensor.maximumRange
                if (near != null && near != isNear) {
                    presence.fire(PresenceFusion.Signal.PROXIMITY, t)
                    if (isNear) NodeBus.send(NodeBus.Command.WAKE)
                }
                near = isNear
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // High-pass filter: remove gravity, keep the "bump".
                val a = 0.9
                var mag = 0.0
                for (i in 0..2) {
                    gravity[i] = a * gravity[i] + (1 - a) * e.values[i]
                    val lin = e.values[i] - gravity[i]
                    mag += lin * lin
                }
                if (sqrt(mag) > 0.8 && t - lastMotionMs > 2_000 && t - startedMs > 10_000) {
                    lastMotionMs = t
                    presence.fire(PresenceFusion.Signal.MOTION, t)
                    NodeBus.send(NodeBus.Command.WAKE)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        battery = mapOf(
            "level" to (if (level >= 0 && scale > 0) (level * 100.0 / scale) else null),
            "temp" to i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }?.let { it / 10.0 },
            "voltage" to i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it > 0 }?.toDouble(),
            "charging" to (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL),
            "source" to when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "ac"
                BatteryManager.BATTERY_PLUGGED_USB -> "usb"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                0 -> "battery"
                else -> "other"
            },
        )
    }

    @Suppress("DEPRECATION")
    private fun wifiRssi(): Double? = try {
        val r = getSystemService(WifiManager::class.java).connectionInfo.rssi
        if (r in -126..-1) r.toDouble() else null
    } catch (e: Exception) { null }

    private fun memFreeMb(): Double? = try {
        val mi = ActivityManager.MemoryInfo()
        getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        (mi.availMem / (1024 * 1024)).toDouble()
    } catch (e: Exception) { null }

    /** Thermal zone readable by apps on this device: tries the MTK AP zone, then zone0.
     *  On this GSI, untrusted_app is SELinux-denied from /sys/class/thermal (avc spam every tick),
     *  so after the first failure we stop polling and report null. */
    private fun cpuTemp(): Double? {
        if (!thermalReadable) return null
        try {
            val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }
                ?: run { thermalReadable = false; return null }
            val pick = zones.firstOrNull { runCatching { File(it, "type").readText().trim() == "mtktsAP" }.getOrDefault(false) }
                ?: zones.firstOrNull { it.name == "thermal_zone0" } ?: return null
            val raw = File(pick, "temp").readText().trim().toDouble()
            return if (raw > 1000) raw / 1000.0 else raw
        } catch (e: Exception) { thermalReadable = false; return null }
    }

    private fun ipAddress(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address }?.hostAddress
    } catch (e: Exception) { null }

    // ---------- notification ----------

    private fun goForeground(text: String) {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, SynapseApp.CHANNEL_NODE)
            .setContentTitle("Synapse node")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    // ---------- NodeActions (control API) ----------

    override fun status(): Map<String, Any?> {
        val t = now()
        val p = presence.evaluate(t)
        val c = cfg
        return linkedMapOf(
            "ok" to true,
            "version" to BuildConfigInfo.versionName(this),
            "config" to c?.redacted(),
            "ha" to mapOf(
                "reachable" to ha?.reachable, "last_error" to ha?.lastError,
                "last_publish_ok_ms" to lastPublishOk, "publish_errors" to publishErrors,
            ),
            "screen" to NodeBus.screenMode,
            "presence" to mapOf("occupied" to p.occupied, "confidence" to p.confidence, "signals" to p.signals),
            "sensors" to readings(t).mapValues { it.value.first },
            "tts" to mapOf("ready" to speech?.ready, "engine" to speech?.engine),
            "device_owner" to Kiosk.isDeviceOwner(this),
            "ble" to ble?.let { b -> mapOf("running" to b.running, "error" to b.lastError, "adverts" to b.adverts,
                "known" to bleTracker?.snapshot(t)?.known?.map { mapOf("name" to it.name, "rssi" to it.rssi, "near" to it.near) }) },
            "camera" to camera?.let { cam -> mapOf("facing" to c?.camera, "permitted" to cam.permitted(), "last_error" to cam.lastError, "size" to cam.lastSize) },
            "uptime_s" to (t - startedMs) / 1000,
        )
    }

    override fun wake() = NodeBus.send(NodeBus.Command.WAKE)
    override fun ambient() = NodeBus.send(NodeBus.Command.AMBIENT)
    override fun reload() { NodeBus.send(NodeBus.Command.RELOAD); handler.post { reloadConfig() } }
    override fun speak(text: String): Boolean = speech?.speak(text) ?: false
    override fun snapshot(): ByteArray? = camera?.take()
    override fun presence(source: String) {
        presence.fire(PresenceFusion.Signal.EXTERNAL, now())
        Log.i(SynapseApp.TAG, "external presence from ${Json.write(source)}")
    }
}

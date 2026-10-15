package com.acewood.synapse.core

import com.acewood.synapse.logic.EntityCache
import com.acewood.synapse.logic.IntercomTarget
import com.acewood.synapse.logic.NodeConfig
import com.acewood.synapse.logic.Room
import com.acewood.synapse.logic.Rooms
import com.acewood.synapse.logic.RoomsConfig
import com.acewood.synapse.logic.WeatherForecast
import com.acewood.synapse.logic.WeatherForecastParser
import com.acewood.synapse.logic.HaSnapshot
import com.acewood.synapse.logic.HaSnapshotCodec
import com.acewood.synapse.logic.LinkStatus
import com.acewood.synapse.logic.SnapshotSchedule
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

/**
 * Process-wide live HA state for the native UI. NodeService owns the connection; the dashboard
 * Activity observes the cache here (decoupled, no binding). One client at a time per config.
 */
object HaRepository {
    @Volatile private var client: HaWsClient? = null
    @Volatile var config: NodeConfig? = null; private set
    @Volatile var rooms: List<Room> = emptyList(); private set
    @Volatile var hourlyForecast: WeatherForecast? = null; private set
    @Volatile var dailyForecast: WeatherForecast? = null; private set
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val blockedListeners = CopyOnWriteArraySet<() -> Unit>()

    // ---- R-124 Part 5: offline snapshot + link status ----
    @Volatile private var storageDir: File? = null
    @Volatile private var lastAreas: List<com.acewood.synapse.logic.Area> = emptyList()
    private val schedule = SnapshotSchedule(60_000)
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "synapse-snapshot").apply { isDaemon = true } }
    /** Shared link model the screen reads for the offline strip. Guarded by `this`. */
    val link = LinkStatus()

    val cache: EntityCache? get() = client?.cache
    val connected: Boolean get() = client?.connected == true
    val entityCount: Int get() = client?.cache?.size() ?: 0
    val lastError: String? get() = client?.lastError

    fun onChange(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    /** Fired when a control was pressed while offline and therefore not sent. */
    fun onBlocked(l: () -> Unit) { blockedListeners.add(l) }
    fun removeBlocked(l: () -> Unit) { blockedListeners.remove(l) }

    /** Where the last-known-state snapshot lives (app-private). Call before [start]; idempotent. */
    fun attachStorage(dir: File) { if (storageDir == null) storageDir = dir }
    private fun snapshotFile(): File? = storageDir?.let { File(it, "ha_snapshot.json") }

    /** Network back / RETRY tapped / screen woke: try now instead of waiting out the backoff. */
    fun reconnectNow() { client?.reconnectNow() }

    @Synchronized
    fun start(cfg: NodeConfig) {
        if (config == cfg && client != null) return
        client?.stop()
        config = cfg
        val seeded = EntityCache()
        loadSnapshot(cfg)?.let { snap ->
            seeded.applyStates(snap.entities)
            lastAreas = snap.areas
            if (snap.areas.isNotEmpty()) setRooms(snap.areas, RoomsConfig(order = cfg.roomOrder))
            synchronized(this) { link.showingSnapshot(snap.savedAtMs) }
        }
        client = HaWsClient(cfg, cache = seeded,
            onChange = { schedule.markDirty(); maybeSave(); notifyChanged() },
            onAreas = { areas ->
                lastAreas = areas
                setRooms(areas, RoomsConfig(order = cfg.roomOrder))
                schedule.markDirty(); maybeSave(force = true)
                notifyChanged()
            },
            onConnection = { up ->
                synchronized(this) { link.update(up, System.currentTimeMillis()) }
                if (!up) maybeSave(force = true)
                notifyChanged()
            }).also { it.start() }
        notifyChanged()
    }

    private fun loadSnapshot(cfg: NodeConfig): HaSnapshot? {
        val f = snapshotFile() ?: return null
        return runCatching {
            if (!f.isFile || f.length() > HaSnapshotCodec.MAX_BYTES) null
            else HaSnapshotCodec.decode(f.readText(), cfg.haUrl)
        }.getOrNull()
    }

    /**
     * Write the snapshot off the UI thread. Routine saves happen only while live, at most once a
     * minute and only if something changed. [force] (areas just loaded / link just dropped) bypasses
     * the minute but is still capped at one per 10 s; the cache at drop time is the last live picture.
     */
    private fun maybeSave(force: Boolean = false) {
        val f = snapshotFile() ?: return
        val c = client ?: return
        val cfg = config ?: return
        if (c.cache.size() == 0) return
        val now = System.currentTimeMillis()
        synchronized(schedule) {
            if (force) {
                if (now - lastForcedMs < 10_000) { schedule.markDirty(); return }
                lastForcedMs = now
            } else if (!schedule.shouldSave(c.connected, now)) return
            schedule.saved(now)
        }
        val snap = HaSnapshot(now, HaSnapshotCodec.source(cfg.haUrl), lastAreas, c.cache.all())
        io.execute {
            runCatching {
                val tmp = File(f.parentFile, f.name + ".tmp")
                tmp.writeText(HaSnapshotCodec.encode(snap))
                if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
            }.onFailure { android.util.Log.w(SynapseApp.TAG, "snapshot save failed: ${it.message}") }
        }
    }
    private var lastForcedMs = 0L

    @Synchronized
    fun stop() {
        client?.stop(); client = null; config = null; rooms = emptyList(); hourlyForecast = null; dailyForecast = null
        lastAreas = emptyList(); synchronized(this) { link.update(false, System.currentTimeMillis()) }
    }

    /** Rooms the native UI shows; set once we have HA areas (from the WS registry or a pushed map). */
    fun setRooms(areas: List<com.acewood.synapse.logic.Area>, cfg: RoomsConfig = RoomsConfig()) {
        rooms = Rooms.build(areas, cfg)
    }

    /** Toggle with an optimistic local update, so the key lights up instantly; HA's own echo confirms it. */
    fun toggle(entityIds: List<String>, on: Boolean) {
        if (!connected) { blocked(); return }
        optimistic(entityIds, if (on) "on" else "off")
        client?.toggle(entityIds, on)
    }
    fun callService(domain: String, service: String, entityIds: List<String>, data: Map<String, Any?> = emptyMap()) {
        if (!connected) { blocked(); return }
        when (service) {
            "turn_on" -> if (domain in OPTIMISTIC_DOMAINS) optimistic(entityIds, "on")
            "turn_off" -> if (domain in OPTIMISTIC_DOMAINS) optimistic(entityIds, "off")
        }
        client?.callService(domain, service, entityIds, data)
    }

    /** Update a displayed HA attribute immediately; the next HA state event remains authoritative. */
    fun optimisticAttribute(entityIds: List<String>, key: String, value: Any?) {
        val c = cache ?: return
        if (client?.connected != true) return
        var changed = false
        for (id in entityIds) {
            val e = c.get(id) ?: continue
            val attrs = e.attributes.toMutableMap()
            attrs[key] = value
            c.applyStateChanged(e.copy(attributes = attrs)); changed = true
        }
        if (changed) notifyChanged()
    }

    fun converse(text: String, conversationId: String?, onReply: (String, String?) -> Unit) {
        val c = client
        if (c == null) onReply("Home Assistant isn't connected right now.", conversationId) else c.converse(text, conversationId, onReply)
    }

    fun startAssist(onReady: (Int) -> Unit, onEvent: (String, Map<String, Any?>?) -> Unit, onError: (String) -> Unit) {
        client?.startAssist(onReady, onEvent, onError) ?: onError("Home Assistant isn't connected right now.")
    }
    fun sendAssistAudio(handlerId: Int, pcm: ByteArray) { client?.sendAssistAudio(handlerId, pcm) }
    fun stopAssist() { client?.stopAssist() }

    fun requestForecast(kind: String, onDone: () -> Unit = {}) {
        val weather = cache?.byDomain("weather")?.firstOrNull() ?: run { onDone(); return }
        client?.callServiceWithResponse("weather", "get_forecasts", listOf(weather.entityId), mapOf("type" to kind)) { response ->
            val parsed = WeatherForecastParser.parse(response, kind)
            if (kind == "hourly") hourlyForecast = parsed else if (kind == "daily") dailyForecast = parsed
            notifyChanged(); onDone()
        } ?: onDone()
    }

    /** Select the hand-picked room, set the discovered message helper, then run the HA script. */
    fun sendIntercom(target: IntercomTarget, room: String, message: String): Boolean {
        val text = message.trim()
        val inputId = target.messageInputId ?: return false
        if (room.isBlank() || text.isBlank()) return false
        callService("input_select", "select_option", listOf(target.roomSelectId), mapOf("option" to room))
        callService("input_text", "set_value", listOf(inputId), mapOf("value" to text))
        // HA may run WS service calls concurrently. Give the helpers a moment to land before the script reads them.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            callService("script", "turn_on", listOf(target.scriptId))
        }, 500)
        return true
    }

    private val OPTIMISTIC_DOMAINS = setOf("light", "switch", "input_boolean", "fan")

    /** Flip the cached state right away (UI only). The real state_changed from HA overwrites it. */
    private fun optimistic(entityIds: List<String>, state: String) {
        val c = cache ?: return
        if (client?.connected != true) return
        var changed = false
        for (id in entityIds) {
            val e = c.get(id) ?: continue
            if (e.state == "unavailable" || e.state == state) continue
            c.applyStateChanged(e.copy(state = state)); changed = true
        }
        if (changed) notifyChanged()
    }

    private fun notifyChanged() { listeners.forEach { runCatching { it() } } }
    private fun blocked() { blockedListeners.forEach { runCatching { it() } } }
}

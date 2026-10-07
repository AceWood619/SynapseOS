package com.acewood.synapse.core

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import com.acewood.synapse.logic.BleParser
import com.acewood.synapse.logic.BleTracker

/**
 * Low-power continuous BLE scan feeding a [BleTracker].
 * Uses BLUETOOTH_SCAN *with* location (not neverForLocation), because Android filters
 * iBeacons out of neverForLocation scans. Android quietly downgrades scans that run
 * longer than 30 min, so the service restarts the scan every 20 min.
 */
class BleScanner(private val ctx: Context, private val tracker: BleTracker) {
    @Volatile var lastError: String? = null
        private set
    @Volatile var running = false
        private set
    @Volatile var adverts = 0L
        private set

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = handle(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach { handle(it) } }
        override fun onScanFailed(errorCode: Int) {
            lastError = "scan failed: $errorCode"
            running = false
        }
    }

    private fun handle(r: ScanResult) {
        adverts++
        val apple = r.scanRecord?.getManufacturerSpecificData(0x004C)
        tracker.onAdvert(r.device.address, r.rssi, BleParser.iBeaconId(apple), SystemClock.elapsedRealtime())
    }

    fun permitted(): Boolean =
        ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (!permitted()) { lastError = "missing BLUETOOTH_SCAN / ACCESS_FINE_LOCATION permission"; return false }
        val scanner = ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
        if (scanner == null) { lastError = "Bluetooth off or no LE scanner"; return false }
        return try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .setReportDelay(0)
                .build()
            scanner.startScan(null, settings, callback)
            running = true
            lastError = null
            true
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            Log.w(SynapseApp.TAG, "BLE scan start failed", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try { ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(callback) } catch (_: Exception) {}
        running = false
    }

    fun restart(): Boolean { stop(); return start() }
}

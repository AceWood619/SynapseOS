package com.acewood.synapse.logic

import kotlin.math.exp
import kotlin.math.ln

/**
 * Multi-signal presence with confidence and hysteresis.
 *
 * Each signal is an "ember" that glows when it fires and cools with a half-life.
 * Confidence = 1 - exp(-sum of glowing embers). Occupied switches ON at [onThreshold]
 * and only back OFF below [offThreshold], so it doesn't flicker (like a thermostat's swing).
 */
class PresenceFusion(
    private val onThreshold: Double = 0.6,
    private val offThreshold: Double = 0.25,
) {
    enum class Signal(val weight: Double, val halfLifeSec: Double) {
        TOUCH(2.0, 180.0),        // someone is definitely here
        PROXIMITY(1.0, 90.0),     // hand or face near the sensor
        MOTION(0.6, 60.0),        // phone was bumped or moved
        LIGHT_CHANGE(0.5, 60.0),  // room light switched on or off
        VOICE(1.5, 120.0),        // wake word or voice activity
        EXTERNAL(1.0, 120.0),     // HA or another node says someone's here
        BLE(0.8, 90.0),           // a known Bluetooth device is near this node
    }

    private val lastFired = HashMap<Signal, Long>()
    var occupied = false
        private set

    @Synchronized fun fire(signal: Signal, nowMs: Long) { lastFired[signal] = nowMs }

    data class Result(val occupied: Boolean, val confidence: Double, val signals: List<String>)

    @Synchronized fun evaluate(nowMs: Long): Result {
        var score = 0.0
        val active = ArrayList<String>()
        for ((sig, t) in lastFired) {
            val ageSec = (nowMs - t).coerceAtLeast(0) / 1000.0
            val contrib = sig.weight * exp(-ln(2.0) * ageSec / sig.halfLifeSec)
            if (contrib >= 0.05) active.add(sig.name.lowercase())
            score += contrib
        }
        val conf = 1.0 - exp(-score)
        occupied = if (occupied) conf >= offThreshold else conf >= onThreshold
        return Result(occupied, (conf * 100).toLong() / 100.0, active.sorted())
    }
}

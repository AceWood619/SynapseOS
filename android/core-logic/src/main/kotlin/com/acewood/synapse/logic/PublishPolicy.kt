package com.acewood.synapse.logic

/**
 * Decides when a reading is worth sending to HA, so the node doesn't flood HA
 * (like a thermostat's dead-band): send on first reading, on a meaningful change,
 * or when the heartbeat interval has passed. Changes are rate-limited per key.
 */
class PublishPolicy(
    private val heartbeatMs: Long,
    private val minIntervalMs: Long = 2_000,
) {
    private data class Sent(val value: Any?, val atMs: Long)
    private val last = HashMap<String, Sent>()

    fun shouldSend(key: String, value: Any?, deadband: Double, nowMs: Long): Boolean {
        val prev = last[key] ?: return true
        val age = nowMs - prev.atMs
        if (age >= heartbeatMs) return true
        if (age < minIntervalMs) return false
        return changed(prev.value, value, deadband)
    }

    fun markSent(key: String, value: Any?, nowMs: Long) { last[key] = Sent(value, nowMs) }

    /** Forget everything, e.g. after HA restarts, so all states get re-sent. */
    fun reset() = last.clear()

    companion object {
        fun changed(a: Any?, b: Any?, deadband: Double): Boolean {
            if (a is Number && b is Number) return kotlin.math.abs(a.toDouble() - b.toDouble()) >= deadband.coerceAtLeast(1e-9)
            return a != b
        }
    }
}

package com.acewood.synapse.logic

/**
 * Screen state machine. ACTIVE = dashboard at normal brightness.
 * AMBIENT = dimmed clock face, entered after [idleMs] with no activity.
 * Any wake event returns to ACTIVE.
 */
class IdleController(var idleMs: Long, private val graceMs: Long = 4_000) {
    enum class Mode { ACTIVE, AMBIENT }

    var mode = Mode.ACTIVE
        private set
    private var lastActivityMs = -1L
    private var ambientSinceMs = -1L

    /**
     * A wake event (touch, proximity, motion). Within [graceMs] of entering ambient, sensor-driven
     * wakes are ignored so dimming doesn't bounce the screen back on; a real touch ([force]=true)
     * always wakes. Returns true if the mode changed.
     */
    fun activity(nowMs: Long, force: Boolean = false): Boolean {
        if (mode == Mode.AMBIENT && !force && ambientSinceMs >= 0 && nowMs - ambientSinceMs < graceMs) {
            return false
        }
        lastActivityMs = nowMs
        if (mode != Mode.ACTIVE) { mode = Mode.ACTIVE; return true }
        return false
    }

    /** Force ambient (e.g. from HA "good night"). Returns true if changed. */
    fun forceAmbient(nowMs: Long = 0L): Boolean {
        if (mode == Mode.AMBIENT) return false
        mode = Mode.AMBIENT
        ambientSinceMs = nowMs
        return true
    }

    /** Call periodically. Returns true if the mode changed. */
    fun tick(nowMs: Long): Boolean {
        if (lastActivityMs < 0) lastActivityMs = nowMs
        if (mode == Mode.ACTIVE && nowMs - lastActivityMs >= idleMs) { mode = Mode.AMBIENT; ambientSinceMs = nowMs; return true }
        return false
    }
}

package com.acewood.synapse.logic

/**
 * Screen state machine. ACTIVE = dashboard at normal brightness.
 * AMBIENT = dimmed clock face, entered after [idleMs] with no activity.
 * Any wake event returns to ACTIVE.
 */
class IdleController(var idleMs: Long) {
    enum class Mode { ACTIVE, AMBIENT }

    var mode = Mode.ACTIVE
        private set
    private var lastActivityMs = -1L

    /** Returns true if the mode changed. */
    fun activity(nowMs: Long): Boolean {
        lastActivityMs = nowMs
        if (mode != Mode.ACTIVE) { mode = Mode.ACTIVE; return true }
        return false
    }

    /** Force ambient (e.g. from HA "good night"). Returns true if changed. */
    fun forceAmbient(): Boolean {
        if (mode == Mode.AMBIENT) return false
        mode = Mode.AMBIENT
        return true
    }

    /** Call periodically. Returns true if the mode changed. */
    fun tick(nowMs: Long): Boolean {
        if (lastActivityMs < 0) lastActivityMs = nowMs
        if (mode == Mode.ACTIVE && nowMs - lastActivityMs >= idleMs) { mode = Mode.AMBIENT; return true }
        return false
    }
}

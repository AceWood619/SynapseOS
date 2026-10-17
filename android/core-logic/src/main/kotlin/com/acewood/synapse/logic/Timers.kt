package com.acewood.synapse.logic

data class TimerInfo(
    val entityId: String,
    val name: String,
    val state: String,
    val remaining: String,
) {
    val isActive: Boolean get() = state == "active"
    val isPaused: Boolean get() = state == "paused"
}

object Timers {
    fun snapshot(cache: EntityCache?): List<TimerInfo> = cache?.byDomain("timer").orEmpty().map { e ->
        TimerInfo(
            entityId = e.entityId,
            name = e.friendlyName,
            state = e.state,
            remaining = (e.attributes["remaining"] as? String)?.takeIf { it.isNotBlank() }
                ?: (e.attributes["duration"] as? String)?.takeIf { it.isNotBlank() }
                ?: e.state,
        )
    }
}

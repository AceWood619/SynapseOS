package com.acewood.synapse.logic

data class AmbientTimer(val name: String, val state: String, val remaining: String)
data class AmbientSnapshot(
    val weather: String?,
    val timer: AmbientTimer?,
    val nowPlaying: NowPlayingState?,
)

object AmbientData {
    fun snapshot(cache: EntityCache?): AmbientSnapshot {
        val weather = cache?.byDomain("weather")?.firstOrNull()?.let { e ->
            val temp = e.attrDouble("temperature")?.let { "${Math.round(it)}°" }
            val label = e.state.replace('-', ' ').replaceFirstChar { it.uppercase() }
            listOfNotNull(label.takeIf { it.isNotBlank() }, temp).joinToString("  ").ifBlank { null }
        }
        val timer = cache?.byDomain("timer")
            ?.filter { it.state == "active" || it.state == "paused" }
            ?.minByOrNull { (it.attributes["finishes_at"] as? String).orEmpty() }
            ?.let { e ->
                val remaining = (e.attributes["remaining"] as? String)?.takeIf { it.isNotBlank() } ?: e.state
                AmbientTimer(e.friendlyName, e.state, remaining)
            }
        return AmbientSnapshot(weather, timer, NowPlaying.first(cache))
    }
}

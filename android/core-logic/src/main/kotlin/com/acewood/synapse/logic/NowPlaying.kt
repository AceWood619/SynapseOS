package com.acewood.synapse.logic

/** Normalized media state shared by the home card and room pad. */
data class NowPlayingState(
    val entityId: String,
    val title: String,
    val appName: String?,
    val state: String,
    val isPlaying: Boolean,
    val albumArt: String?,
    val volumePct: Int?,
    val canTransport: Boolean,
    val caps: MediaCaps = MediaCaps.NONE,
    val isMuted: Boolean = false,
) {
    val isActive: Boolean get() = state in setOf("playing", "paused", "buffering")
}

object NowPlaying {
    private const val PLAY = 16384
    private const val PAUSE = 1
    private const val NEXT = 32

    fun first(cache: EntityCache?): NowPlayingState? =
        cache?.byDomain("media_player")
            ?.firstOrNull { it.state == "playing" }
            ?.let { from(it) }

    fun from(e: Entity): NowPlayingState {
        val attrs = e.attributes
        val title = (attrs["media_title"] as? String)?.takeIf { it.isNotBlank() }
            ?: (attrs["app_name"] as? String)?.takeIf { it.isNotBlank() }
            ?: e.friendlyName
        val features = e.attrDouble("supported_features")?.toInt() ?: 0
        val volume = e.attrDouble("volume_level")?.let { (it * 100).toInt().coerceIn(0, 100) }
        val caps = MediaCaps.of(e)
        return NowPlayingState(
            entityId = e.entityId,
            title = title,
            appName = (attrs["app_name"] as? String)?.takeIf { it.isNotBlank() },
            state = e.state,
            isPlaying = e.state == "playing",
            albumArt = attrs["entity_picture"] as? String,
            volumePct = if (caps.volumeSet) volume else null,
            canTransport = (features and (PLAY or PAUSE or NEXT)) != 0,
            caps = caps,
            isMuted = attrs["is_volume_muted"] == true,
        )
    }
}

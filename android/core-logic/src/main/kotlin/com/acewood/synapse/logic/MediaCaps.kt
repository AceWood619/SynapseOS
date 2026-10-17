package com.acewood.synapse.logic

/**
 * The one shared answer to "which volume / mute keys can this player actually use?"
 *
 * Every media surface (Home now-playing card, Room Pad, Audio app) reads this instead of checking
 * `supported_features` bits on its own. Rules mirror Home Assistant core
 * (`homeassistant/components/media_player/services.py`, checked 2026-10-15):
 *
 *  - `volume_up` / `volume_down` are accepted when the player has **VOLUME_SET or VOLUME_STEP**.
 *    With only VOLUME_SET, HA's base entity steps `volume_level` itself. The old gate required
 *    VOLUME_STEP alone, which hid VOL± on Cast/Sonos/most speakers even though HA runs the service.
 *  - `volume_set` (the slider) needs **VOLUME_SET** only. A VOLUME_STEP-only player (e.g. a Roku TV)
 *    must not get a slider: HA rejects `volume_set` for it.
 *  - `volume_mute` needs **VOLUME_MUTE**.
 *  - If the media_player can't do a key but the room has a Roku-style `remote.*`, the key falls back
 *    to `remote.send_command` with the Roku ECP volume keys, so the controls don't disappear.
 */
data class MediaCaps(
    val volumeSet: Boolean,
    val volumeStep: Boolean,
    val mute: Boolean,
    /** A remote.* that can send VolumeUp/VolumeDown/VolumeMute when the player itself can't. */
    val remoteEntityId: String? = null,
) {
    /** Show VOL − / VOL + keys. */
    val showVolumeKeys: Boolean get() = volumeStep || remoteEntityId != null
    /** Show the MUTE key. */
    val showMute: Boolean get() = mute || remoteEntityId != null
    /** Whether mute state is known/settable exactly (false = remote toggle, state unknown). */
    val exactMute: Boolean get() = mute

    fun volumeUp(mediaEntityId: String): HaCall? = when {
        volumeStep -> MediaRemote.volumeUp(mediaEntityId)
        remoteEntityId != null -> HaCall("remote", "send_command", remoteEntityId, mapOf("command" to "VolumeUp"))
        else -> null
    }

    fun volumeDown(mediaEntityId: String): HaCall? = when {
        volumeStep -> MediaRemote.volumeDown(mediaEntityId)
        remoteEntityId != null -> HaCall("remote", "send_command", remoteEntityId, mapOf("command" to "VolumeDown"))
        else -> null
    }

    /** [muted] is the desired state; the remote fallback can only toggle. */
    fun mute(mediaEntityId: String, muted: Boolean): HaCall? = when {
        mute -> MediaRemote.mute(mediaEntityId, muted)
        remoteEntityId != null -> HaCall("remote", "send_command", remoteEntityId, mapOf("command" to "VolumeMute"))
        else -> null
    }

    companion object {
        // HA MediaPlayerEntityFeature bits.
        const val FEAT_VOLUME_SET = 4
        const val FEAT_VOLUME_MUTE = 8
        const val FEAT_VOLUME_STEP = 1024

        val NONE = MediaCaps(volumeSet = false, volumeStep = false, mute = false)

        fun features(e: Entity?): Int = (e?.attrDouble("supported_features") ?: 0.0).toInt()

        /**
         * @param remoteEntityId a Roku-style remote paired with this player (room pad); null elsewhere.
         *   Only used when the player is not unavailable, so a dead TV doesn't show live keys.
         */
        fun of(e: Entity?, remoteEntityId: String? = null): MediaCaps {
            val f = features(e)
            val set = (f and FEAT_VOLUME_SET) != 0
            val step = (f and FEAT_VOLUME_STEP) != 0
            val mute = (f and FEAT_VOLUME_MUTE) != 0
            val alive = e != null && e.state != "unavailable"
            val remote = remoteEntityId?.takeIf { alive && (!(set || step) || !mute) }
            return MediaCaps(volumeSet = set, volumeStep = set || step, mute = mute, remoteEntityId = remote)
        }
    }
}

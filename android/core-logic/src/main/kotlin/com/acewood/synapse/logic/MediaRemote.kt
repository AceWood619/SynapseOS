package com.acewood.synapse.logic

/**
 * The "remote" half of the room pad: turns D-pad / transport button presses into Home Assistant
 * service calls. Pure + testable — the Android side just dispatches the returned [HaCall] over the
 * WebSocket. Directional keys go to a Roku-style `remote.*` entity via remote.send_command; media
 * transport (play/pause/skip/volume) goes to the `media_player.*` entity.
 *
 * Feel target: this is a physical remote for the house, so the button set mirrors a Roku clicker.
 */

/** One Home Assistant service call, ready for HaWsClient.callService(domain, service, [entityId], data). */
data class HaCall(
    val domain: String,
    val service: String,
    val entityId: String,
    val data: Map<String, Any?> = emptyMap(),
)

object MediaRemote {
    /** The clicker buttons. OK = center/select. */
    enum class Button { UP, DOWN, LEFT, RIGHT, OK, BACK, HOME, PLAY, REV, FWD, REPLAY, INFO }

    /** Roku ECP key names that HA's `remote.send_command` accepts. */
    private val ROKU_KEY = mapOf(
        Button.UP to "Up", Button.DOWN to "Down", Button.LEFT to "Left", Button.RIGHT to "Right",
        Button.OK to "Select", Button.BACK to "Back", Button.HOME to "Home", Button.PLAY to "Play",
        Button.REV to "Rev", Button.FWD to "Fwd", Button.REPLAY to "InstantReplay", Button.INFO to "Info",
    )

    /**
     * A D-pad / clicker press. Prefers the room's `remote.*` entity (true Roku navigation). If there
     * is no remote entity, the transport-style buttons fall back to the media_player so Play/skip
     * still work; pure navigation (arrows/OK/Back/Info) has no media_player equivalent and returns null.
     */
    fun press(button: Button, remoteEntityId: String?, mediaEntityId: String?): HaCall? {
        if (remoteEntityId != null) {
            val key = ROKU_KEY[button] ?: return null
            return HaCall("remote", "send_command", remoteEntityId, mapOf("command" to key))
        }
        // No remote entity: map the transport-capable buttons onto the media_player.
        val media = mediaEntityId ?: return null
        return when (button) {
            Button.PLAY -> HaCall("media_player", "media_play_pause", media)
            Button.FWD -> HaCall("media_player", "media_next_track", media)
            Button.REV -> HaCall("media_player", "media_previous_track", media)
            else -> null
        }
    }

    // ---- media_player transport (independent of the D-pad) ----
    fun playPause(mediaEntityId: String) = HaCall("media_player", "media_play_pause", mediaEntityId)
    fun stop(mediaEntityId: String) = HaCall("media_player", "media_stop", mediaEntityId)
    fun next(mediaEntityId: String) = HaCall("media_player", "media_next_track", mediaEntityId)
    fun previous(mediaEntityId: String) = HaCall("media_player", "media_previous_track", mediaEntityId)
    fun volumeUp(mediaEntityId: String) = HaCall("media_player", "volume_up", mediaEntityId)
    fun volumeDown(mediaEntityId: String) = HaCall("media_player", "volume_down", mediaEntityId)
    fun mute(mediaEntityId: String, muted: Boolean) =
        HaCall("media_player", "volume_mute", mediaEntityId, mapOf("is_volume_muted" to muted))
    fun power(mediaEntityId: String, on: Boolean) =
        HaCall("media_player", if (on) "turn_on" else "turn_off", mediaEntityId)
}

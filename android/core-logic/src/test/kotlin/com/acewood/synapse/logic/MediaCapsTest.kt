package com.acewood.synapse.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regression: volume/mute keys vanished whenever HA didn't advertise VOLUME_STEP (bit 1024). */
class MediaCapsTest {
    private fun cache(vararg e: Entity) = EntityCache().apply { applyStates(e.toList()) }
    private fun player(id: String, features: Int, state: String = "playing", extra: Map<String, Any?> = emptyMap()) =
        Entity(id, state, mapOf("supported_features" to features.toDouble()) + extra)

    @Test fun volumeSetOnlyStillGetsVolumeKeys() {
        // Cast/Sonos style: VOLUME_SET | VOLUME_MUTE, no VOLUME_STEP. HA accepts volume_up/down here.
        val caps = MediaCaps.of(player("media_player.kitchen_speaker", 4 + 8))
        assertTrue(caps.volumeSet); assertTrue(caps.volumeStep); assertTrue(caps.mute)
        assertTrue(caps.showVolumeKeys); assertTrue(caps.showMute)
        assertEquals(HaCall("media_player", "volume_up", "media_player.kitchen_speaker"), caps.volumeUp("media_player.kitchen_speaker"))
    }

    @Test fun stepOnlyGetsKeysButNoSlider() {
        // Roku TV style: VOLUME_STEP | VOLUME_MUTE. volume_set would be rejected, so no slider.
        val caps = MediaCaps.of(player("media_player.den_roku", 1024 + 8))
        assertFalse(caps.volumeSet); assertTrue(caps.showVolumeKeys); assertTrue(caps.showMute)
    }

    @Test fun noVolumeBitsAndNoRemoteHidesKeys() {
        val caps = MediaCaps.of(player("media_player.plain", 1 /* PAUSE */))
        assertFalse(caps.showVolumeKeys); assertFalse(caps.showMute)
        assertNull(caps.volumeUp("media_player.plain")); assertNull(caps.mute("media_player.plain", true))
    }

    @Test fun rokuRemoteBacksMissingVolumeAndMute() {
        val caps = MediaCaps.of(player("media_player.den_roku", 1), "remote.den_roku")
        assertTrue(caps.showVolumeKeys); assertTrue(caps.showMute); assertFalse(caps.exactMute)
        assertEquals(HaCall("remote", "send_command", "remote.den_roku", mapOf("command" to "VolumeUp")), caps.volumeUp("media_player.den_roku"))
        assertEquals(HaCall("remote", "send_command", "remote.den_roku", mapOf("command" to "VolumeMute")), caps.mute("media_player.den_roku", true))
    }

    @Test fun playerOwnCapsWinOverRemote() {
        val caps = MediaCaps.of(player("media_player.den_roku", 1024 + 8), "remote.den_roku")
        assertNull(caps.remoteEntityId)
        assertEquals("media_player", caps.mute("media_player.den_roku", true)?.domain)
    }

    @Test fun unavailablePlayerGetsNoRemoteFallback() {
        val caps = MediaCaps.of(Entity("media_player.den_roku", "unavailable", emptyMap()), "remote.den_roku")
        assertFalse(caps.showVolumeKeys); assertFalse(caps.showMute)
    }

    @Test fun roomPadMediaTileUsesSharedGate() {
        val room = Rooms.build(listOf(Area("living", "Living", listOf("media_player.living_speaker")))).first()
        val c = cache(player("media_player.living_speaker", 4 + 8, extra = mapOf("volume_level" to 0.3)))
        val t = RoomControl.build(room, c).media.single()
        assertTrue(t.canVolumeStep); assertTrue(t.canMute); assertEquals(30, t.volumePct)
    }

    @Test fun roomPadStepOnlyHasNoSliderValue() {
        val room = Rooms.build(listOf(Area("den", "Den", listOf("media_player.den_tv")))).first()
        val c = cache(player("media_player.den_tv", 1024 + 8, extra = mapOf("volume_level" to 0.5)))
        assertNull(RoomControl.build(room, c).media.single().volumePct)
    }

    @Test fun roomPadRokuFallsBackToRemote() {
        val room = Rooms.build(listOf(Area("den", "Den", listOf("media_player.den_roku", "remote.den_roku")))).first()
        val c = cache(player("media_player.den_roku", 16384 + 1), Entity("remote.den_roku", "on", emptyMap()))
        val t = RoomControl.build(room, c).media.single()
        assertTrue(t.canVolumeStep); assertTrue(t.canMute)
        assertEquals("remote", t.caps.volumeDown(t.entityId)?.domain)
    }

    @Test fun capsForUsesRoomWhenPlayerIsInOne() {
        val rooms = Rooms.build(listOf(Area("den", "Den", listOf("media_player.den_roku", "remote.den_roku"))))
        val c = cache(player("media_player.den_roku", 1), Entity("remote.den_roku", "on", emptyMap()),
            player("media_player.loose", 4))
        assertEquals("remote.den_roku", RoomControl.capsFor("media_player.den_roku", rooms, c).remoteEntityId)
        assertTrue(RoomControl.capsFor("media_player.loose", rooms, c).showVolumeKeys)
        assertFalse(RoomControl.capsFor("media_player.loose", rooms, c).showMute)
    }

    @Test fun nowPlayingCarriesCapsAndMute() {
        val np = NowPlaying.from(player("media_player.tv", 16384 + 4 + 8, extra = mapOf("volume_level" to 0.2, "is_volume_muted" to true)))
        assertTrue(np.caps.showVolumeKeys); assertTrue(np.isMuted); assertEquals(20, np.volumePct)
        val stepOnly = NowPlaying.from(player("media_player.tv2", 16384 + 1024, extra = mapOf("volume_level" to 0.2)))
        assertNull(stepOnly.volumePct); assertTrue(stepOnly.caps.showVolumeKeys)
    }
}

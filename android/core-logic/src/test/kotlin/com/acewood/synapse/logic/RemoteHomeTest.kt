package com.acewood.synapse.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteHomeTest {
    private fun cache(vararg e: Entity) = EntityCache().apply { applyStates(e.toList()) }
    private val rooms = Rooms.build(listOf(
        Area("living_room", "Living Room", listOf("light.living_lamp")),
        Area("dining_room", "Dining Room", listOf("light.dining", "switch.dining_plug")),
        Area("kids_room", "Kids Room", listOf("media_player.juniors_roku", "remote.juniors_roku", "light.kids_lamp")),
    ))

    @Test fun wrapsChannelsBothWays() {
        assertEquals(0, RemoteHome.wrap(3, 3)); assertEquals(2, RemoteHome.wrap(-1, 3)); assertEquals(0, RemoteHome.wrap(5, 0))
    }

    @Test fun startPrefersRememberedThenPlayingThenPanelRoom() {
        val c = cache(Entity("media_player.juniors_roku", "playing", emptyMap()))
        assertEquals(1, RemoteHome.startIndex(rooms, "dining_room", "living_room", c))
        assertEquals(2, RemoteHome.startIndex(rooms, "gone_room", "living_room", c))
        assertEquals(0, RemoteHome.startIndex(rooms, null, "Living Room", cache()))
        assertEquals(0, RemoteHome.startIndex(emptyList(), null, null, null))
    }

    @Test fun kidsRoomChannelDrivesRokuWithRemoteFallback() {
        val c = cache(
            Entity("media_player.juniors_roku", "playing", mapOf("supported_features" to (16384 + 1).toDouble(), "app_name" to "HBO Max", "media_title" to "HBO Max")),
            Entity("remote.juniors_roku", "on", emptyMap()),
            Entity("light.kids_lamp", "off", emptyMap()),
        )
        val ch = RemoteHome.channel(rooms, 2, c)!!
        assertEquals("CH 3", ch.label); assertEquals(3, ch.count)
        assertEquals("HBO Max playing · lights off", ch.status)
        assertEquals("media_player.juniors_roku", ch.mediaId); assertEquals("remote.juniors_roku", ch.remoteId)
        assertTrue(ch.isPlaying); assertTrue(ch.mediaOn); assertTrue(ch.hasDpad)
        assertTrue(ch.caps.showVolumeKeys)          // Roku remote backs VOL± even without player bits
        assertFalse(ch.anyLightOn)
        assertEquals(true to listOf("light.kids_lamp"), RemoteHome.lightsCall(ch))
    }

    @Test fun lightsAndPlugsCountTogetherAndToggleOff() {
        val c = cache(Entity("light.dining", "on", mapOf("brightness" to 255.0)), Entity("switch.dining_plug", "on", emptyMap()))
        val ch = RemoteHome.channel(rooms, 1, c)!!
        assertEquals("2 lights on", ch.status); assertEquals(2, ch.lightsTotal)
        assertFalse(ch.hasMedia); assertFalse(ch.hasDpad)
        val (on, targets) = RemoteHome.lightsCall(ch)!!
        assertFalse(on); assertEquals(listOf("light.dining", "switch.dining_plug"), targets)
    }

    @Test fun statusWording() {
        assertEquals("All off", RemoteHome.channel(rooms, 0, cache(Entity("light.living_lamp", "off", emptyMap())))!!.status)
        assertEquals("1 light on", RemoteHome.channel(rooms, -3, cache(Entity("light.living_lamp", "on", emptyMap())))!!.status)
        val off = cache(Entity("media_player.juniors_roku", "off", emptyMap()))
        val ch = RemoteHome.channel(rooms, 2, off)!!
        assertFalse(ch.mediaOn); assertEquals("All off", ch.status)
        assertNull(RemoteHome.channel(emptyList(), 0, off))
    }
}

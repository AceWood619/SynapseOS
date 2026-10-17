package com.acewood.synapse.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HaSnapshotTest {
    private val url = "http://ha.local:8123/"
    private fun snap(vararg e: Entity, areas: List<Area> = listOf(Area("den", "Den", listOf("light.den")))) =
        HaSnapshot(1_700_000_000_000, HaSnapshotCodec.source(url), areas, e.toList())

    @Test fun roundTripsStatesAndAreas() {
        val text = HaSnapshotCodec.encode(snap(
            Entity("light.den", "on", mapOf("friendly_name" to "Den Lamp", "brightness" to 128.0, "supported_color_modes" to listOf("hs"))),
            Entity("media_player.tv", "playing", mapOf("supported_features" to 1036.0, "volume_level" to 0.4))))
        val back = assertNotNull(HaSnapshotCodec.decode(text, "http://HA.local:8123"))
        assertEquals(1_700_000_000_000, back.savedAtMs)
        assertEquals(listOf(Area("den", "Den", listOf("light.den"))), back.areas)
        val lamp = back.entities.first { it.entityId == "light.den" }
        assertEquals("on", lamp.state); assertEquals("Den Lamp", lamp.friendlyName); assertEquals(128.0, lamp.attrDouble("brightness"))
        assertEquals(listOf("hs"), lamp.attributes["supported_color_modes"])
        // Rebuilt cache drives the same room model as live state.
        val cache = EntityCache().apply { applyStates(back.entities) }
        assertTrue(MediaCaps.of(cache.get("media_player.tv")).showMute)
    }

    @Test fun dropsSignedUrlsAndSecrets() {
        val clean = HaSnapshotCodec.sanitize(Entity("media_player.tv", "playing", mapOf(
            "entity_picture" to "/api/media_player_proxy/media_player.tv?token=abc",
            "access_token" to "abc", "api_key" to "k", "Media_Token" to "t",
            "media_title" to "Show", "thumb" to "http://x/y?token=zzz")))
        assertEquals(setOf("media_title"), clean.attributes.keys)
        assertFalse(HaSnapshotCodec.encode(snap(Entity("media_player.tv", "on", mapOf("entity_picture" to "p?token=abc")))).contains("abc"))
    }

    @Test fun capsLongStringsAndLists() {
        val clean = HaSnapshotCodec.sanitize(Entity("sensor.x", "1", mapOf(
            "long" to "a".repeat(1000), "list" to (1..500).map { it.toDouble() })))
        assertEquals(HaSnapshotCodec.MAX_STRING, (clean.attributes["long"] as String).length)
        assertEquals(HaSnapshotCodec.MAX_LIST, (clean.attributes["list"] as List<*>).size)
    }

    @Test fun rejectsOtherHaCorruptAndOldVersions() {
        val text = HaSnapshotCodec.encode(snap(Entity("light.den", "on", emptyMap())))
        assertNull(HaSnapshotCodec.decode(text, "http://other:8123"))
        assertNull(HaSnapshotCodec.decode(text.dropLast(7), url))
        assertNull(HaSnapshotCodec.decode(text.replace("\"v\":1", "\"v\":99"), url))
        assertNull(HaSnapshotCodec.decode("[]", url))
        assertNotNull(HaSnapshotCodec.decode(text, null))
    }

    @Test fun skipsMalformedEntityRows() {
        val text = """{"v":1,"saved_at":5,"source":"x","areas":[{"name":"no id"}],"entities":[{"s":"on"},{"id":"bad","s":"on"},{"id":"light.ok","s":"on"}]}"""
        val back = assertNotNull(HaSnapshotCodec.decode(text, null))
        assertEquals(listOf("light.ok"), back.entities.map { it.entityId })
        assertTrue(back.areas.isEmpty())
    }
}

class LinkStatusTest {
    @Test fun bootWithSnapshotShowsSavedAgeAfterGrace() {
        val l = LinkStatus()
        l.showingSnapshot(savedAtMs = 0)
        l.update(false, nowMs = 600_000)
        assertNull(l.banner(600_100))  // within grace
        assertEquals("Connecting to Home Assistant · showing saved state from 10m ago", l.banner(600_000 + LinkStatus.GRACE_MS))
        assertFalse(l.controlsEnabled)
    }

    @Test fun liveClearsBannerAndSnapshotFlag() {
        val l = LinkStatus(); l.showingSnapshot(0); l.update(false, 0)
        assertTrue(l.update(true, 1_000))
        assertNull(l.banner(1_000)); assertTrue(l.controlsEnabled); assertNull(l.snapshotSavedAtMs)
        l.showingSnapshot(5)   // ignored while live
        assertNull(l.snapshotSavedAtMs)
    }

    @Test fun dropAfterLiveCountsFromDropAndReportsChangeOnce() {
        val l = LinkStatus(); l.update(true, 0)
        assertTrue(l.update(false, 10_000))
        assertFalse(l.update(false, 20_000))   // repeated down events don't reset the clock
        assertEquals("Offline · reconnecting · last update 2m ago", l.banner(10_000 + 120_000))
        assertTrue(l.update(true, 200_000)); assertNull(l.banner(200_000))
    }

    @Test fun ageFormatting() {
        assertEquals("5s", LinkStatus.age(5_000)); assertEquals("3m", LinkStatus.age(180_000))
        assertEquals("2h 5m", LinkStatus.age(7_500_000)); assertEquals("2d", LinkStatus.age(2 * 86_400_000L)); assertEquals("0s", LinkStatus.age(-5))
    }

    @Test fun snapshotScheduleOnlyWhileLiveAndDirty() {
        val s = SnapshotSchedule(60_000)
        assertFalse(s.shouldSave(true, 0))           // nothing changed
        s.markDirty()
        assertFalse(s.shouldSave(false, 0))          // never while offline
        assertTrue(s.shouldSave(true, 0)); s.saved(0)
        s.markDirty(); assertFalse(s.shouldSave(true, 30_000))
        assertTrue(s.shouldSave(true, 60_000))
    }
}

class HaSnapshotNestedSecretsTest {
    @Test fun nestedCredentialKeysAndSignedTextAreDropped() {
        val clean = HaSnapshotCodec.sanitize(Entity("sensor.x", "1", mapOf(
            "config" to mapOf("access_token" to "abc", "Api_Key" to "k", "name" to "ok", "inner" to mapOf("password" to "p", "mode" to "auto")),
            "list" to listOf(mapOf("secret" to "s", "label" to "a"), "https://cam/x?sig=123", "plain"),
            "url" to "http://x/y?api_key=zzz")))
        @Suppress("UNCHECKED_CAST") val cfg = clean.attributes["config"] as Map<String, Any?>
        assertEquals(setOf("name", "inner"), cfg.keys)
        @Suppress("UNCHECKED_CAST") assertEquals(mapOf("mode" to "auto"), cfg["inner"] as Map<String, Any?>)
        assertEquals(listOf(mapOf("label" to "a"), "plain"), clean.attributes["list"])
        assertFalse(clean.attributes.containsKey("url"))
        assertFalse(HaSnapshotCodec.encode(HaSnapshot(1, "x", emptyList(), listOf(Entity("sensor.x", "1", mapOf("c" to mapOf("token" to "SECRET")))))).contains("SECRET"))
    }
}

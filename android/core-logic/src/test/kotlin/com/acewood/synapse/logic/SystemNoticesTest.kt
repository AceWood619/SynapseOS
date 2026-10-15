package com.acewood.synapse.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemNoticesTest {
    private fun n(id: String, title: String?, msg: String, at: String) =
        mapOf("notification_id" to id, "title" to title, "message" to msg, "created_at" to at)

    @Test fun subscribeEventsBuildTheList() {
        val book = NoticeBook()
        assertTrue(book.apply("current", mapOf(
            "a" to n("a", "Login attempt failed", "Login attempt failed from **unknown**", "2026-10-15T10:00:00"),
            "b" to n("b", null, "Integration needs [reconfigure](/config/integrations)", "2026-10-15T12:00:00"))))
        assertEquals(listOf("b", "a"), book.list().map { it.id })              // newest first
        assertEquals("Integration needs reconfigure", book.list().first().message)
        assertEquals("Login attempt failed from unknown", book.list().last().message)
        assertTrue(book.apply("added", mapOf("c" to n("c", "New", "x", "2026-10-15T13:00:00"))))
        assertTrue(book.apply("removed", mapOf("a" to n("a", null, "", ""))))
        assertEquals(listOf("c", "b"), book.list().map { it.id })
        assertFalse(book.apply("updated", mapOf("c" to n("c", "New", "x", "2026-10-15T13:00:00"))))  // no visible change
        assertFalse(book.apply("bogus", null))
        assertTrue(book.apply("current", emptyMap()))                          // reconnect replaces everything
        assertTrue(book.list().isEmpty())
    }

    @Test fun subscribeEventFrameIsParsedWithRawPayload() {
        val f = HaWs.parse("""{"id":7,"type":"event","event":{"type":"current","notifications":{"a":{"notification_id":"a","title":"T","message":"M","created_at":"2026"}}}}""")
        f as HaWs.Frame.AssistEvent
        assertEquals(7, f.id); assertEquals("current", f.eventType)
        @Suppress("UNCHECKED_CAST")
        val book = NoticeBook().apply { apply(f.eventType, f.raw?.get("notifications") as Map<String, Any?>) }
        assertEquals("T", book.list().single().title)
        assertEquals("""{"id":3,"type":"persistent_notification/subscribe"}""", SystemNotices.subscribeMessage(3))
        assertEquals("""{"id":4,"type":"call_service","domain":"persistent_notification","service":"dismiss","service_data":{"notification_id":"a"}}""",
            HaWs.callServiceMessage(4, "persistent_notification", "dismiss", SystemNotices.dismissData("a")))
    }

    @Test fun pendingUpdatesFromUpdateEntities() {
        val cache = EntityCache().apply { applyStates(listOf(
            Entity("update.home_assistant_core_update", "on", mapOf("title" to "Home Assistant Core", "installed_version" to "2026.10.1", "latest_version" to "2026.10.2")),
            Entity("update.esphome_kitchen", "off", mapOf("installed_version" to "1", "latest_version" to "1")),
            Entity("update.zigbee", "on", mapOf("friendly_name" to "Zigbee firmware", "in_progress" to true)),
        )) }
        val ups = SystemNotices.updates(cache)
        assertEquals(listOf("Home Assistant Core", "Zigbee firmware"), ups.map { it.name })
        assertEquals("2026.10.2", ups.first().latest); assertTrue(ups.last().inProgress)
    }

    @Test fun badgeCountsOnlyUnseenAndReappearsOnNewVersion() {
        val notices = listOf(Notice("a", "T", "M", null))
        val v1 = listOf(UpdateNotice("update.core", "Core", "1", "2", false))
        val seen = SystemNotices.markSeen(SystemNotices.keys(notices, v1))
        assertEquals(0, SystemNotices.unseenCount(SystemNotices.keys(notices, v1), seen))
        val v2 = listOf(UpdateNotice("update.core", "Core", "1", "3", false))
        assertEquals(1, SystemNotices.unseenCount(SystemNotices.keys(notices, v2), seen))
        val edited = listOf(Notice("a", "T", "M changed", null))
        assertEquals(2, SystemNotices.unseenCount(SystemNotices.keys(edited, v2), seen))
    }

    @Test fun plainCapsLength() {
        assertEquals(SystemNotices.MAX_MESSAGE, SystemNotices.plain("x".repeat(5000)).length)
        assertEquals("Title\n\nBody", SystemNotices.plain("## Title\n\n\n\n`Body`"))
    }
}

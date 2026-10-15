package com.acewood.synapse.logic

/**
 * R-124 Part 4 — admin "system notices": HA persistent notifications + pending `update.*` entities.
 *
 * Persistent notifications stopped being entities in HA 2023.6; they come from the WS command
 * `persistent_notification/subscribe`, which first sends `{"type":"current","notifications":{id:{…}}}`
 * then `added` / `updated` / `removed` deltas (HA core, persistent_notification/__init__.py).
 * Dismiss = service `persistent_notification.dismiss` with `notification_id` and no target.
 *
 * Updates are read from live `update.*` states: state `on` means an update is available (HA already
 * folds in skipped versions). Synapse lists them; it never installs anything.
 */
data class Notice(val id: String, val title: String?, val message: String, val createdAt: String?) {
    /** Badge/seen key: changes if HA re-issues the same id with new text. */
    val key: String get() = "pn:$id:${(title.orEmpty() + message).hashCode()}"
}

data class UpdateNotice(val entityId: String, val name: String, val installed: String?, val latest: String?, val inProgress: Boolean) {
    val key: String get() = "up:$entityId:${latest.orEmpty()}"
}

class NoticeBook {
    private val map = LinkedHashMap<String, Notice>()

    /** Apply one subscription event. Returns true when the visible list changed. */
    @Synchronized
    fun apply(type: String, notifications: Map<String, Any?>?): Boolean {
        val parsed = SystemNotices.parseNotifications(notifications)
        val before = map.toMap()
        when (type) {
            "current" -> { map.clear(); parsed.forEach { map[it.id] = it } }
            "added", "updated" -> parsed.forEach { map[it.id] = it }
            "removed" -> (notifications?.keys ?: emptySet()).forEach { map.remove(it) }
            else -> return false
        }
        return before != map
    }

    @Synchronized fun remove(id: String) = map.remove(id) != null
    @Synchronized fun clear() = map.clear()
    /** Newest first when HA gives ISO timestamps (they sort lexically); otherwise insertion order. */
    @Synchronized fun list(): List<Notice> = map.values.sortedByDescending { it.createdAt.orEmpty() }
}

object SystemNotices {
    const val MAX_MESSAGE = 600

    fun subscribeMessage(id: Long): String =
        Json.write(linkedMapOf("id" to id, "type" to "persistent_notification/subscribe"))

    /** Service call that dismisses one notification (no entity target). */
    fun dismissData(notificationId: String): Map<String, Any?> = mapOf("notification_id" to notificationId)

    @Suppress("UNCHECKED_CAST")
    fun parseNotifications(raw: Map<String, Any?>?): List<Notice> = raw.orEmpty().mapNotNull { (key, v) ->
        val m = v as? Map<String, Any?> ?: return@mapNotNull null
        val id = (m["notification_id"] as? String)?.takeIf { it.isNotBlank() } ?: key
        Notice(
            id = id,
            title = (m["title"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
            message = plain(m["message"] as? String ?: ""),
            createdAt = m["created_at"] as? String,
        )
    }

    /** Light markdown cleanup for a plain TextView: links -> text, drop emphasis/headers, cap length. */
    fun plain(md: String): String {
        var s = md.replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        s = s.replace(Regex("(?m)^#{1,6}\\s*"), "").replace("**", "").replace("__", "").replace("`", "")
        s = s.replace(Regex("\\n{3,}"), "\n\n").trim()
        return if (s.length > MAX_MESSAGE) s.take(MAX_MESSAGE - 1).trimEnd() + "…" else s
    }

    fun updates(cache: EntityCache?): List<UpdateNotice> = cache?.byDomain("update").orEmpty()
        .filter { it.state == "on" }
        .map { e ->
            UpdateNotice(
                entityId = e.entityId,
                name = (e.attributes["title"] as? String)?.takeIf { it.isNotBlank() } ?: e.friendlyName,
                installed = e.attributes["installed_version"] as? String,
                latest = e.attributes["latest_version"] as? String,
                inProgress = e.attributes["in_progress"] == true || (e.attributes["in_progress"] as? Double ?: 0.0) > 0.0,
            )
        }
        .sortedBy { it.name.lowercase() }

    fun keys(notices: List<Notice>, updates: List<UpdateNotice>): Set<String> =
        notices.map { it.key }.toSet() + updates.map { it.key }

    /** Badge count: things present now that this panel's admin hasn't opened the notices screen to see. */
    fun unseenCount(current: Set<String>, seen: Set<String>): Int = (current - seen).size

    /** What to remember as seen after opening the screen (drops keys that no longer exist, so the set can't grow forever). */
    fun markSeen(current: Set<String>): Set<String> = current
}

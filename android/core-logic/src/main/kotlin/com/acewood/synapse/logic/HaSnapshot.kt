package com.acewood.synapse.logic

/**
 * R-124 Part 5 — the "last known good" picture of the house, saved to app-private storage so a
 * reboot or an HA outage still shows rooms and states (greyed) instead of an empty screen.
 *
 * Think of it as a photo of the control panel: useful to look at, never something to act on.
 * The app never sends service calls from a snapshot; it only renders until the live link returns.
 *
 * Privacy: the file lives in the app's private files dir (never the repo, never published). Even so,
 * attributes that can carry credentials or signed URLs are dropped before saving.
 */
data class HaSnapshot(
    val savedAtMs: Long,
    /** Which HA this came from (normalized base URL); a snapshot from another HA is ignored. */
    val source: String,
    val areas: List<Area>,
    val entities: List<Entity>,
)

object HaSnapshotCodec {
    const val VERSION = 1
    const val MAX_ENTITIES = 4000
    const val MAX_STRING = 240
    const val MAX_LIST = 64
    /** Reject absurd files before parsing (e.g. a corrupted write that never got truncated). */
    const val MAX_BYTES = 4 * 1024 * 1024

    /** Attribute keys never written to disk: signed URLs, tokens, codes. */
    private val DROP_EXACT = setOf("entity_picture", "entity_picture_local", "access_token", "code_format", "media_image_url")
    private val DROP_FRAGMENTS = listOf("token", "password", "secret", "api_key", "apikey")

    fun source(haUrl: String): String = haUrl.trim().trimEnd('/').lowercase()

    private fun sensitiveKey(k: String): Boolean {
        val key = k.lowercase()
        return key in DROP_EXACT || DROP_FRAGMENTS.any { key.contains(it) }
    }

    private val SECRET_IN_TEXT = Regex("(token|access_token|api_key|apikey|key|password|secret|sig|signature)=", RegexOption.IGNORE_CASE)

    fun sanitize(e: Entity): Entity {
        val attrs = LinkedHashMap<String, Any?>()
        for ((k, v) in e.attributes) {
            if (sensitiveKey(k)) continue
            if (v == null) { attrs[k] = null; continue }
            attrs[k] = cleanValue(v, depth = 0) ?: continue
        }
        return Entity(e.entityId, e.state.take(MAX_STRING), attrs)
    }

    /**
     * Returns null for values that should be dropped: too deep, or text carrying a credential-looking
     * query parameter. Maps are filtered by key at every level (review finding, ChatGPT 2026-10-16).
     */
    private fun cleanValue(v: Any?, depth: Int): Any? = when (v) {
        null, is Boolean, is Number -> v
        is String -> if (SECRET_IN_TEXT.containsMatchIn(v)) null else v.take(MAX_STRING)
        is List<*> -> if (depth >= 2) null else v.take(MAX_LIST).mapNotNull { cleanValue(it, depth + 1) }
        is Map<*, *> -> if (depth >= 2) null else v.entries.take(MAX_LIST)
            .filterNot { (k, _) -> sensitiveKey(k.toString()) }
            .mapNotNull { (k, x) -> cleanValue(x, depth + 1)?.let { k.toString() to it } }.toMap()
        else -> v.toString().take(MAX_STRING)
    }

    fun encode(s: HaSnapshot): String = Json.write(mapOf(
        "v" to VERSION,
        "saved_at" to s.savedAtMs,
        "source" to s.source,
        "areas" to s.areas.map { mapOf("id" to it.id, "name" to it.name, "entities" to it.entities) },
        "entities" to s.entities.take(MAX_ENTITIES).map { e ->
            val clean = sanitize(e)
            mapOf("id" to clean.entityId, "s" to clean.state, "a" to clean.attributes)
        },
    ))

    /** Null when the text is corrupt, from another version, or from a different HA instance. */
    @Suppress("UNCHECKED_CAST")
    fun decode(text: String, expectedSource: String?): HaSnapshot? {
        if (text.length > MAX_BYTES) return null
        val root = runCatching { Json.parseObject(text) }.getOrNull() ?: return null
        if ((root["v"] as? Double)?.toInt() != VERSION) return null
        val source = root["source"] as? String ?: return null
        if (expectedSource != null && source != source(expectedSource)) return null
        val saved = (root["saved_at"] as? Double)?.toLong() ?: return null
        val areas = (root["areas"] as? List<Any?>).orEmpty().mapNotNull { a ->
            val m = a as? Map<String, Any?> ?: return@mapNotNull null
            val id = m["id"] as? String ?: return@mapNotNull null
            Area(id, m["name"] as? String ?: id, (m["entities"] as? List<Any?>).orEmpty().mapNotNull { it as? String })
        }
        val entities = (root["entities"] as? List<Any?>).orEmpty().take(MAX_ENTITIES).mapNotNull { x ->
            val m = x as? Map<String, Any?> ?: return@mapNotNull null
            val id = m["id"] as? String ?: return@mapNotNull null
            if (!id.contains('.')) return@mapNotNull null
            Entity(id, m["s"] as? String ?: "unknown", (m["a"] as? Map<String, Any?>) ?: emptyMap())
        }
        return HaSnapshot(saved, source, areas, entities)
    }
}

/**
 * Pure model of the HA link as the screen should present it. The app feeds it events; the UI reads
 * [banner] and [controlsEnabled]. Times are elapsed/wall millis supplied by the caller (testable).
 */
class LinkStatus {
    var live = false; private set
    var everLive = false; private set
    var offlineSinceMs: Long? = null; private set
    /** Wall time the on-screen snapshot was saved, while showing one before the first live connect. */
    var snapshotSavedAtMs: Long? = null; private set

    fun showingSnapshot(savedAtMs: Long) { if (!live) snapshotSavedAtMs = savedAtMs }

    /** Returns true if the state changed (callers redraw on change only). */
    fun update(connected: Boolean, nowMs: Long): Boolean {
        if (connected == live && (connected || offlineSinceMs != null)) return false
        live = connected
        if (connected) { everLive = true; offlineSinceMs = null; snapshotSavedAtMs = null }
        else if (offlineSinceMs == null) offlineSinceMs = nowMs
        return true
    }

    /** Controls send only while live; offline presses are refused with feedback (never queued). */
    val controlsEnabled: Boolean get() = live

    /** Strip text, or null when live (no strip). */
    fun banner(nowMs: Long): String? {
        if (live) return null
        // Grace: a normal boot connect or a sub-second blip shouldn't flash the strip.
        val since0 = offlineSinceMs
        if (since0 != null && nowMs - since0 < GRACE_MS) return null
        val saved = snapshotSavedAtMs
        if (!everLive) {
            return if (saved != null) "Connecting to Home Assistant · showing saved state from ${age(nowMs - saved)} ago"
            else "Connecting to Home Assistant…"
        }
        val since = offlineSinceMs ?: nowMs
        return "Offline · reconnecting · last update ${age(nowMs - since)} ago"
    }

    companion object {
        const val GRACE_MS = 2_500L
        fun age(ms: Long): String {
            val s = (ms.coerceAtLeast(0) / 1000)
            return when {
                s < 60 -> "${s}s"
                s < 3600 -> "${s / 60}m"
                s < 86_400 -> "${s / 3600}h ${(s % 3600) / 60}m"
                else -> "${s / 86_400}d"
            }
        }
    }
}

/** When to write the snapshot: only while live, at most every [minIntervalMs], and only if something changed. */
class SnapshotSchedule(private val minIntervalMs: Long = 60_000) {
    private var lastSaveMs: Long? = null
    private var dirty = false
    fun markDirty() { dirty = true }
    fun shouldSave(live: Boolean, nowMs: Long): Boolean {
        if (!live || !dirty) return false
        val last = lastSaveMs
        return last == null || nowMs - last >= minIntervalMs
    }
    fun saved(nowMs: Long) { lastSaveMs = nowMs; dirty = false }
}

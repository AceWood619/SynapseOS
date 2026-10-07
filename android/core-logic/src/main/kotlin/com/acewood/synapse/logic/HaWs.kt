package com.acewood.synapse.logic

/**
 * Home Assistant WebSocket protocol — the pure, testable half.
 * The Android side owns the actual socket; this builds the messages, parses frames,
 * and keeps a live entity cache so the native dashboard has fast local state.
 *
 * Flow: connect ws://host:8123/api/websocket -> server sends auth_required ->
 * we send auth(token) -> auth_ok -> we subscribe_events(state_changed) + get_states.
 */
object HaWs {
    fun authMessage(token: String): String =
        Json.write(linkedMapOf("type" to "auth", "access_token" to token))

    fun subscribeStatesMessage(id: Long): String =
        Json.write(linkedMapOf("id" to id, "type" to "subscribe_events", "event_type" to "state_changed"))

    fun getStatesMessage(id: Long): String =
        Json.write(linkedMapOf("id" to id, "type" to "get_states"))

    // Registry lists — used once after auth to learn HA areas and which entity lives in which area.
    fun listAreasMessage(id: Long): String =
        Json.write(linkedMapOf("id" to id, "type" to "config/area_registry/list"))
    fun listEntitiesMessage(id: Long): String =
        Json.write(linkedMapOf("id" to id, "type" to "config/entity_registry/list"))
    fun listDevicesMessage(id: Long): String =
        Json.write(linkedMapOf("id" to id, "type" to "config/device_registry/list"))

    fun callServiceMessage(id: Long, domain: String, service: String, data: Map<String, Any?>, target: Map<String, Any?>? = null): String {
        val m = linkedMapOf<String, Any?>("id" to id, "type" to "call_service", "domain" to domain, "service" to service)
        if (data.isNotEmpty()) m["service_data"] = data
        if (target != null) m["target"] = target
        return Json.write(m)
    }

    /** Ask HA's conversation agent (Assist / Jarvis) something in plain text. */
    fun conversationMessage(id: Long, text: String, conversationId: String? = null, agentId: String? = null): String {
        val m = linkedMapOf<String, Any?>("id" to id, "type" to "conversation/process", "text" to text, "language" to "en")
        if (conversationId != null) m["conversation_id"] = conversationId
        if (agentId != null) m["agent_id"] = agentId
        return Json.write(m)
    }

    /** Pull the spoken reply + conversation id out of a conversation/process result. */
    @Suppress("UNCHECKED_CAST")
    fun conversationReply(result: Map<String, Any?>?): Pair<String, String?> {
        val resp = result?.get("response") as? Map<String, Any?>
        val speech = ((resp?.get("speech") as? Map<String, Any?>)?.get("plain") as? Map<String, Any?>)?.get("speech") as? String
        return (speech?.takeIf { it.isNotBlank() } ?: "(no reply)") to (result?.get("conversation_id") as? String)
    }

    fun pingMessage(id: Long): String = Json.write(linkedMapOf("id" to id, "type" to "ping"))

    sealed class Frame {
        object AuthRequired : Frame()
        object AuthOk : Frame()
        data class AuthInvalid(val message: String) : Frame()
        /** A reply to one of our commands. states is set for a get_states result; rows is the raw
         *  result array (registry lists etc.) matched to the request id. */
        data class Result(val id: Long, val success: Boolean, val states: List<Entity>?, val error: String?,
                          val rows: List<Map<String, Any?>>? = null,
                          /** The result when it's an object (e.g. conversation/process), else null. */
                          val obj: Map<String, Any?>? = null) : Frame()
        /** A state_changed event carrying the entity's new state (null if the entity was removed). */
        data class StateChanged(val entity: Entity?) : Frame()
        object Pong : Frame()
        data class Other(val type: String) : Frame()
    }

    @Suppress("UNCHECKED_CAST")
    fun parse(text: String): Frame {
        val m = try { Json.parseObject(text) } catch (e: Exception) { return Frame.Other("parse_error") }
        return when (m["type"]) {
            "auth_required" -> Frame.AuthRequired
            "auth_ok" -> Frame.AuthOk
            "auth_invalid" -> Frame.AuthInvalid(m["message"] as? String ?: "invalid auth")
            "pong" -> Frame.Pong
            "result" -> {
                val id = (m["id"] as? Double)?.toLong() ?: -1
                val success = m["result"] != null && m["success"] != false || m["success"] == true
                val result = m["result"]
                val states = (result as? List<Any?>)?.mapNotNull { Entity.fromState(it as? Map<String, Any?>) }
                val rows = (result as? List<Any?>)?.mapNotNull { it as? Map<String, Any?> }
                val err = (m["error"] as? Map<String, Any?>)?.get("message") as? String
                Frame.Result(id, m["success"] == true, states, err, rows, result as? Map<String, Any?>)
            }
            "event" -> {
                val event = m["event"] as? Map<String, Any?>
                if (event?.get("event_type") == "state_changed") {
                    val data = event["data"] as? Map<String, Any?>
                    Frame.StateChanged(Entity.fromState(data?.get("new_state") as? Map<String, Any?>))
                } else Frame.Other(event?.get("event_type") as? String ?: "event")
            }
            else -> Frame.Other(m["type"] as? String ?: "unknown")
        }
    }
}

/** One Home Assistant entity, trimmed to what a dashboard needs. */
data class Entity(
    val entityId: String,
    val state: String,
    val attributes: Map<String, Any?>,
) {
    val domain: String get() = entityId.substringBefore('.', "")
    val friendlyName: String get() = attributes["friendly_name"] as? String ?: entityId.substringAfter('.').replace('_', ' ')
    val on: Boolean get() = state == "on" || state == "open" || state == "playing" || state == "home"
    fun attrDouble(key: String): Double? = (attributes[key] as? Double) ?: (attributes[key] as? String)?.toDoubleOrNull()

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromState(m: Map<String, Any?>?): Entity? {
            val id = m?.get("entity_id") as? String ?: return null
            return Entity(id, m["state"] as? String ?: "unknown", (m["attributes"] as? Map<String, Any?>) ?: emptyMap())
        }
    }
}

/** Thread-safe live cache of HA entities, updated from get_states + state_changed. */
class EntityCache {
    private val map = LinkedHashMap<String, Entity>()

    @Synchronized fun applyStates(entities: List<Entity>) { entities.forEach { map[it.entityId] = it } }
    @Synchronized fun applyStateChanged(e: Entity?) { if (e != null) map[e.entityId] = e }
    @Synchronized fun get(entityId: String): Entity? = map[entityId]
    @Synchronized fun all(): List<Entity> = ArrayList(map.values)
    @Synchronized fun byDomain(domain: String): List<Entity> = map.values.filter { it.domain == domain }
    @Synchronized fun size(): Int = map.size
    @Synchronized fun clear() = map.clear()

    /** Entities in an HA area, if the area is encoded in attributes (best-effort without the registry). */
    @Synchronized fun withPrefix(prefix: String): List<Entity> = map.values.filter { it.entityId.startsWith(prefix) }
}

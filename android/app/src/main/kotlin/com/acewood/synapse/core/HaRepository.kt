package com.acewood.synapse.core

import com.acewood.synapse.logic.EntityCache
import com.acewood.synapse.logic.IntercomTarget
import com.acewood.synapse.logic.NodeConfig
import com.acewood.synapse.logic.Room
import com.acewood.synapse.logic.Rooms
import com.acewood.synapse.logic.RoomsConfig
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Process-wide live HA state for the native UI. NodeService owns the connection; the dashboard
 * Activity observes the cache here (decoupled, no binding). One client at a time per config.
 */
object HaRepository {
    @Volatile private var client: HaWsClient? = null
    @Volatile var config: NodeConfig? = null; private set
    @Volatile var rooms: List<Room> = emptyList(); private set
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    val cache: EntityCache? get() = client?.cache
    val connected: Boolean get() = client?.connected == true
    val entityCount: Int get() = client?.cache?.size() ?: 0
    val lastError: String? get() = client?.lastError

    fun onChange(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    @Synchronized
    fun start(cfg: NodeConfig) {
        if (config == cfg && client != null) return
        client?.stop()
        config = cfg
        client = HaWsClient(cfg, onChange = { notifyChanged() }, onAreas = { areas ->
            setRooms(areas, RoomsConfig(order = cfg.roomOrder))
            notifyChanged()
        }).also { it.start() }
    }

    @Synchronized
    fun stop() { client?.stop(); client = null; config = null; rooms = emptyList() }

    /** Rooms the native UI shows; set once we have HA areas (from the WS registry or a pushed map). */
    fun setRooms(areas: List<com.acewood.synapse.logic.Area>, cfg: RoomsConfig = RoomsConfig()) {
        rooms = Rooms.build(areas, cfg)
    }

    /** Toggle with an optimistic local update, so the key lights up instantly; HA's own echo confirms it. */
    fun toggle(entityIds: List<String>, on: Boolean) {
        optimistic(entityIds, if (on) "on" else "off")
        client?.toggle(entityIds, on)
    }
    fun callService(domain: String, service: String, entityIds: List<String>, data: Map<String, Any?> = emptyMap()) {
        when (service) {
            "turn_on" -> if (domain in OPTIMISTIC_DOMAINS) optimistic(entityIds, "on")
            "turn_off" -> if (domain in OPTIMISTIC_DOMAINS) optimistic(entityIds, "off")
        }
        client?.callService(domain, service, entityIds, data)
    }

    /** Update a displayed HA attribute immediately; the next HA state event remains authoritative. */
    fun optimisticAttribute(entityIds: List<String>, key: String, value: Any?) {
        val c = cache ?: return
        if (client?.connected != true) return
        var changed = false
        for (id in entityIds) {
            val e = c.get(id) ?: continue
            val attrs = e.attributes.toMutableMap()
            attrs[key] = value
            c.applyStateChanged(e.copy(attributes = attrs)); changed = true
        }
        if (changed) notifyChanged()
    }

    fun converse(text: String, conversationId: String?, onReply: (String, String?) -> Unit) {
        val c = client
        if (c == null) onReply("Home Assistant isn't connected right now.", conversationId) else c.converse(text, conversationId, onReply)
    }

    fun startAssist(onReady: (Int) -> Unit, onEvent: (String, Map<String, Any?>?) -> Unit, onError: (String) -> Unit) {
        client?.startAssist(onReady, onEvent, onError) ?: onError("Home Assistant isn't connected right now.")
    }
    fun sendAssistAudio(handlerId: Int, pcm: ByteArray) { client?.sendAssistAudio(handlerId, pcm) }
    fun stopAssist() { client?.stopAssist() }

    /** Select the hand-picked room, set the discovered message helper, then run the HA script. */
    fun sendIntercom(target: IntercomTarget, room: String, message: String): Boolean {
        val text = message.trim()
        val inputId = target.messageInputId ?: return false
        if (room.isBlank() || text.isBlank()) return false
        callService("input_select", "select_option", listOf(target.roomSelectId), mapOf("option" to room))
        callService("input_text", "set_value", listOf(inputId), mapOf("value" to text))
        // HA may run WS service calls concurrently. Give the helpers a moment to land before the script reads them.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            callService("script", "turn_on", listOf(target.scriptId))
        }, 500)
        return true
    }

    private val OPTIMISTIC_DOMAINS = setOf("light", "switch", "input_boolean", "fan")

    /** Flip the cached state right away (UI only). The real state_changed from HA overwrites it. */
    private fun optimistic(entityIds: List<String>, state: String) {
        val c = cache ?: return
        if (client?.connected != true) return
        var changed = false
        for (id in entityIds) {
            val e = c.get(id) ?: continue
            if (e.state == "unavailable" || e.state == state) continue
            c.applyStateChanged(e.copy(state = state)); changed = true
        }
        if (changed) notifyChanged()
    }

    private fun notifyChanged() { listeners.forEach { runCatching { it() } } }
}

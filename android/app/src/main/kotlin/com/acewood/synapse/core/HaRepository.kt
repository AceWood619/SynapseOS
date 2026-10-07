package com.acewood.synapse.core

import com.acewood.synapse.logic.EntityCache
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
        client = HaWsClient(cfg, onChange = { notifyChanged() }).also { it.start() }
    }

    @Synchronized
    fun stop() { client?.stop(); client = null; config = null; rooms = emptyList() }

    /** Rooms the native UI shows; set once we have HA areas (from the WS registry or a pushed map). */
    fun setRooms(areas: List<com.acewood.synapse.logic.Area>, cfg: RoomsConfig = RoomsConfig()) {
        rooms = Rooms.build(areas, cfg)
    }

    fun toggle(entityIds: List<String>, on: Boolean) = client?.toggle(entityIds, on)
    fun callService(domain: String, service: String, entityIds: List<String>, data: Map<String, Any?> = emptyMap()) =
        client?.callService(domain, service, entityIds, data)

    private fun notifyChanged() { listeners.forEach { runCatching { it() } } }
}

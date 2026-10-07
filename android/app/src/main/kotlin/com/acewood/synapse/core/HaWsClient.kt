package com.acewood.synapse.core

import android.util.Log
import com.acewood.synapse.logic.EntityCache
import com.acewood.synapse.logic.HaWs
import com.acewood.synapse.logic.NodeConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Live Home Assistant connection for the native UI: opens the WebSocket, authenticates with the
 * node token, subscribes to state_changed, keeps the EntityCache fresh, and sends service calls.
 * Auto-reconnects with backoff. One instance per node config.
 */
class HaWsClient(
    private val cfg: NodeConfig,
    val cache: EntityCache = EntityCache(),
    private val onChange: () -> Unit = {},
) {
    @Volatile var connected = false; private set
    @Volatile var lastError: String? = null; private set
    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var ws: WebSocket? = null
    private val id = AtomicLong(1)
    @Volatile private var authed = false
    @Volatile private var closed = false
    @Volatile private var backoffMs = 1_000L

    private fun wsUrl(): String {
        val base = cfg.haUrl.trimEnd('/')
        val wsBase = when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
            base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
            else -> "ws://$base"
        }
        return "$wsBase/api/websocket"
    }

    fun start() { closed = false; connect() }
    fun stop() { closed = true; try { ws?.close(1000, "bye") } catch (_: Exception) {}; ws = null }

    private fun connect() {
        if (closed) return
        authed = false
        val req = Request.Builder().url(wsUrl()).build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
            override fun onOpen(webSocket: WebSocket, response: Response) { Log.i(SynapseApp.TAG, "HA ws open") }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false; lastError = t.message; scheduleReconnect()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false; if (!closed) scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (closed) return
        val delay = backoffMs
        backoffMs = (backoffMs * 2).coerceAtMost(30_000)
        Thread {
            try { Thread.sleep(delay) } catch (_: Exception) {}
            if (!closed) connect()
        }.start()
    }

    private fun handle(text: String) {
        when (val f = HaWs.parse(text)) {
            is HaWs.Frame.AuthRequired -> ws?.send(HaWs.authMessage(cfg.haToken))
            is HaWs.Frame.AuthOk -> {
                authed = true; connected = true; backoffMs = 1_000; lastError = null
                ws?.send(HaWs.subscribeStatesMessage(id.getAndIncrement()))
                ws?.send(HaWs.getStatesMessage(id.getAndIncrement()))
            }
            is HaWs.Frame.AuthInvalid -> { lastError = "auth invalid: ${f.message}"; closed = true; ws?.close(1000, "auth") }
            is HaWs.Frame.Result -> { val st = f.states; if (st != null) { cache.applyStates(st); onChange() } }
            is HaWs.Frame.StateChanged -> { cache.applyStateChanged(f.entity); onChange() }
            else -> {}
        }
    }

    /** Fire a service call (e.g. light.turn_on) at one or more entities. Optimistic: UI updates first. */
    fun callService(domain: String, service: String, entityIds: List<String>, data: Map<String, Any?> = emptyMap()) {
        if (!authed || entityIds.isEmpty()) return
        ws?.send(HaWs.callServiceMessage(id.getAndIncrement(), domain, service, data, mapOf("entity_id" to entityIds)))
    }

    fun toggle(entityIds: List<String>, on: Boolean) {
        if (entityIds.isEmpty()) return
        val domain = entityIds.first().substringBefore('.')
        callService(domain, if (on) "turn_on" else "turn_off", entityIds)
    }
}

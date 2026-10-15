package com.acewood.synapse.core

import android.util.Log
import com.acewood.synapse.logic.Area
import com.acewood.synapse.logic.EntityCache
import com.acewood.synapse.logic.HaRegistry
import com.acewood.synapse.logic.HaWs
import com.acewood.synapse.logic.NodeConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
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
    /** Called once the HA area/entity/device registries have loaded, with the derived areas. */
    private val onAreas: (List<Area>) -> Unit = {},
    /** Live-link changes (true = authenticated, false = dropped). Called from OkHttp threads. */
    private val onConnection: (Boolean) -> Unit = {},
    /** persistent_notification/subscribe events: (current|added|updated|removed, notifications by id). */
    private val onNotices: (String, Map<String, Any?>?) -> Unit = { _, _ -> },
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
    /** An open/auth attempt is in flight; guards [reconnectNow] against doubled sockets. */
    @Volatile private var connecting = false
    /** Bumped on every manual reconnect so stale backoff timers don't open a second socket. */
    private val generation = AtomicLong(0)

    // Registry fetch bookkeeping: request id -> which list, plus the rows as they land.
    private var areaReqId = -1L
    private var entityReqId = -1L
    private var deviceReqId = -1L
    @Volatile private var noticeReqId = -1L
    private var areaRows: List<Map<String, Any?>>? = null
    private var entityRows: List<Map<String, Any?>>? = null
    private var deviceRows: List<Map<String, Any?>>? = null
    /** One-shot reply handlers for request/response commands (conversation etc.), by request id. */
    private val pending = java.util.concurrent.ConcurrentHashMap<Long, (HaWs.Frame.Result) -> Unit>()
    @Volatile private var assistRunId = -1L
    @Volatile private var assistHandlerId: Int = -1
    @Volatile private var assistReady: ((Int) -> Unit)? = null
    @Volatile private var assistEvents: ((String, Map<String, Any?>?) -> Unit)? = null

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

    /**
     * Skip the backoff wait (network came back, user tapped RETRY, screen woke). No-op while
     * connected, while an attempt is already in flight, or after an auth failure (needs new config).
     */
    fun reconnectNow() {
        if (closed || connected || connecting) return
        generation.incrementAndGet()
        backoffMs = 1_000L
        connect()
    }

    private val attempt = AtomicLong(0)

    private fun dropped(myAttempt: Long) {
        if (myAttempt != attempt.get()) return   // a superseded socket closing late must not mark the live one down
        val was = connected
        connected = false; authed = false; connecting = false
        failPending()
        if (was) onConnection(false)
        scheduleReconnect()
    }

    /** Request/response callers (Jarvis, forecasts) must not wait forever on a dead socket. */
    private fun failPending() {
        val waiting = pending.keys.toList()
        waiting.forEach { id -> pending.remove(id)?.let { cb -> runCatching { cb(HaWs.Frame.Result(id, false, null, "connection lost")) } } }
    }

    private fun connect() {
        if (closed) return
        authed = false; connecting = true
        val myAttempt = attempt.incrementAndGet()
        val req = Request.Builder().url(wsUrl()).build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { /* TTS is not requested; STT events are text frames. */ }
            override fun onOpen(webSocket: WebSocket, response: Response) { Log.i(SynapseApp.TAG, "HA ws open") }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                lastError = t.message; dropped(myAttempt)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closed) dropped(myAttempt)
            }
        })
    }

    private fun scheduleReconnect() {
        if (closed) return
        val delay = backoffMs
        backoffMs = (backoffMs * 2).coerceAtMost(30_000)
        val gen = generation.get()
        Thread {
            try { Thread.sleep(delay) } catch (_: Exception) {}
            if (!closed && gen == generation.get() && !connected && !connecting) connect()
        }.start()
    }

    private fun handle(text: String) {
        when (val f = HaWs.parse(text)) {
            is HaWs.Frame.AuthRequired -> ws?.send(HaWs.authMessage(cfg.haToken))
            is HaWs.Frame.AuthOk -> {
                authed = true; connected = true; connecting = false; backoffMs = 1_000; lastError = null
                onConnection(true)
                areaRows = null; entityRows = null; deviceRows = null
                ws?.send(HaWs.subscribeStatesMessage(id.getAndIncrement()))
                ws?.send(HaWs.getStatesMessage(id.getAndIncrement()))
                areaReqId = id.getAndIncrement();   ws?.send(HaWs.listAreasMessage(areaReqId))
                entityReqId = id.getAndIncrement(); ws?.send(HaWs.listEntitiesMessage(entityReqId))
                deviceReqId = id.getAndIncrement(); ws?.send(HaWs.listDevicesMessage(deviceReqId))
                noticeReqId = id.getAndIncrement(); ws?.send(com.acewood.synapse.logic.SystemNotices.subscribeMessage(noticeReqId))
            }
            is HaWs.Frame.AuthInvalid -> { lastError = "auth invalid: ${f.message}"; closed = true; connecting = false; ws?.close(1000, "auth") }
            is HaWs.Frame.Result -> {
                when (f.id) {
                    areaReqId -> { areaRows = f.rows ?: emptyList(); maybeBuildAreas() }
                    entityReqId -> { entityRows = f.rows ?: emptyList(); maybeBuildAreas() }
                    deviceReqId -> { deviceRows = f.rows ?: emptyList(); maybeBuildAreas() }
                    else -> {
                        val cb = pending.remove(f.id)
                        if (cb != null) { runCatching { cb(f) } }
                        else { val st = f.states; if (st != null) { cache.applyStates(st); onChange() } }
                    }
                }
            }
            is HaWs.Frame.StateChanged -> { cache.applyStateChanged(f.entity); onChange() }
            is HaWs.Frame.AssistEvent -> if (f.id == noticeReqId && noticeReqId >= 0) {
                @Suppress("UNCHECKED_CAST")
                onNotices(f.eventType, f.raw?.get("notifications") as? Map<String, Any?>)
            } else if (f.id == assistRunId) {
                if (f.eventType == "error") Log.w(SynapseApp.TAG, "HA Assist error: ${f.data}")
                // HA puts the binary handler id in the run-start event (runner_data), not in the result.
                if (f.eventType == "run-start") {
                    @Suppress("UNCHECKED_CAST")
                    val rd = f.data?.get("runner_data") as? Map<String, Any?>
                    val h = (rd?.get("stt_binary_handler_id") as? Number)?.toInt()
                    if (h != null) { assistHandlerId = h; assistReady?.invoke(h); assistReady = null }
                }
                assistEvents?.invoke(f.eventType, f.data)
                if (f.eventType == "run-end" || f.eventType == "error") {
                    assistRunId = -1L; assistHandlerId = -1; assistEvents = null; assistReady = null
                }
            }
            else -> {}
        }
    }

    private fun maybeBuildAreas() {
        val a = areaRows ?: return
        val e = entityRows ?: return
        val d = deviceRows ?: return
        try { onAreas(HaRegistry.buildAreas(a, e, d)) }
        catch (ex: Exception) { Log.w(SynapseApp.TAG, "area build failed: ${ex.message}") }
    }

    /** Fire a service call (e.g. light.turn_on) at one or more entities. Optimistic: UI updates first. */
    fun callService(domain: String, service: String, entityIds: List<String>, data: Map<String, Any?> = emptyMap()) {
        if (!authed || entityIds.isEmpty()) return
        ws?.send(HaWs.callServiceMessage(id.getAndIncrement(), domain, service, data, mapOf("entity_id" to entityIds)))
    }

    /** A service call with data but no entity target (e.g. persistent_notification.dismiss). */
    fun callServiceData(domain: String, service: String, data: Map<String, Any?>) {
        if (!authed) return
        ws?.send(HaWs.callServiceMessage(id.getAndIncrement(), domain, service, data))
    }

    fun callServiceWithResponse(domain: String, service: String, entityIds: List<String>, data: Map<String, Any?> = emptyMap(), onResult: (Map<String, Any?>?) -> Unit) {
        if (!authed || entityIds.isEmpty()) { onResult(null); return }
        val rid = id.getAndIncrement()
        pending[rid] = { result -> onResult(if (result.success) result.obj else null) }
        ws?.send(HaWs.callServiceResponseMessage(rid, domain, service, data, mapOf("entity_id" to entityIds)))
    }

    /** Send text to HA Assist (Jarvis). onReply gets (speech, conversationId) or an error string. */
    fun converse(text: String, conversationId: String?, onReply: (String, String?) -> Unit) {
        if (!authed) { onReply("Home Assistant isn't connected right now.", conversationId); return }
        val rid = id.getAndIncrement()
        pending[rid] = { f ->
            if (f.success) { val (speech, conv) = HaWs.conversationReply(f.obj); onReply(speech, conv ?: conversationId) }
            else onReply("Jarvis error: ${f.error ?: "unknown"}", conversationId)
        }
        ws?.send(HaWs.conversationMessage(rid, text, conversationId))
    }

    /** Start HA's Assist pipeline (STT -> intent). onReady gets the binary handler id that prefixes PCM frames. */
    fun startAssist(onReady: (Int) -> Unit, onEvent: (String, Map<String, Any?>?) -> Unit, onError: (String) -> Unit) {
        if (!authed) { onError("Home Assistant isn't connected right now."); return }
        val rid = id.getAndIncrement()
        assistRunId = rid; assistHandlerId = -1
        assistEvents = onEvent; assistReady = onReady
        pending[rid] = assistReply@{ f ->
            if (!f.success) { assistEvents = null; assistReady = null; assistRunId = -1L; onError("Assist error: ${f.error ?: "pipeline refused"}") }
        }
        ws?.send(HaWs.assistPipelineMessage(rid))
    }

    fun sendAssistAudio(handlerId: Int, pcm: ByteArray) {
        if (!authed || assistRunId < 0) return
        ws?.send(ByteString.of(*com.acewood.synapse.logic.AssistPipeline.binaryFrame(handlerId, pcm)))
    }

    /** End of speech: HA's protocol is a binary frame holding only the handler byte. Keep listening for intent-end/run-end. */
    fun stopAssist() {
        val h = assistHandlerId
        if (h >= 0 && authed && assistRunId >= 0) ws?.send(ByteString.of(*com.acewood.synapse.logic.AssistPipeline.binaryFrame(h, ByteArray(0))))
        assistHandlerId = -1
    }

    fun toggle(entityIds: List<String>, on: Boolean) {
        if (entityIds.isEmpty()) return
        val domain = entityIds.first().substringBefore('.')
        callService(domain, if (on) "turn_on" else "turn_off", entityIds)
    }
}

package com.acewood.synapse.logic

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.security.MessageDigest

/** Minimal HTTP/1.1 request parsing + routing for the node's local control API. */
data class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: String,
)

data class HttpResponse(val status: Int, val body: String, val contentType: String = "application/json") {
    fun toBytes(): ByteArray {
        val payload = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 404 -> "Not Found"
            405 -> "Method Not Allowed"; 413 -> "Payload Too Large"; else -> "Error"
        }
        val head = "HTTP/1.1 $status $reason\r\nContent-Type: $contentType; charset=utf-8\r\n" +
            "Content-Length: ${payload.size}\r\nConnection: close\r\n\r\n"
        return head.toByteArray(Charsets.US_ASCII) + payload
    }

    companion object {
        fun json(status: Int, value: Any?) = HttpResponse(status, Json.write(value))
        fun error(status: Int, msg: String) = json(status, mapOf("ok" to false, "error" to msg))
    }
}

object HttpParser {
    const val MAX_HEADER = 8 * 1024
    const val MAX_BODY = 16 * 1024

    class BadRequest(val status: Int, msg: String) : Exception(msg)

    fun read(input: InputStream): HttpRequest {
        val head = ByteArrayOutputStream()
        var matched = 0
        val end = byteArrayOf(13, 10, 13, 10)
        while (matched < 4) {
            val b = input.read()
            if (b < 0) throw BadRequest(400, "connection closed in headers")
            head.write(b)
            matched = if (b.toByte() == end[matched]) matched + 1 else if (b == 13) 1 else 0
            if (head.size() > MAX_HEADER) throw BadRequest(413, "headers too large")
        }
        val lines = head.toString(Charsets.US_ASCII.name()).split("\r\n").filter { it.isNotEmpty() }
        val parts = lines.firstOrNull()?.split(" ") ?: throw BadRequest(400, "empty request")
        if (parts.size < 2) throw BadRequest(400, "bad request line")
        val headers = HashMap<String, String>()
        for (l in lines.drop(1)) {
            val i = l.indexOf(':')
            if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        val len = headers["content-length"]?.toIntOrNull() ?: 0
        if (len < 0 || len > MAX_BODY) throw BadRequest(413, "body too large")
        val body = ByteArray(len)
        var got = 0
        while (got < len) {
            val n = input.read(body, got, len - got)
            if (n < 0) throw BadRequest(400, "body truncated")
            got += n
        }
        val target = parts[1]
        val q = target.indexOf('?')
        val path = if (q >= 0) target.substring(0, q) else target
        val query = if (q >= 0) parseQuery(target.substring(q + 1)) else emptyMap()
        return HttpRequest(parts[0].uppercase(), path, query, headers, String(body, Charsets.UTF_8))
    }

    fun parseQuery(s: String): Map<String, String> = s.split("&").filter { it.isNotEmpty() }.associate {
        val i = it.indexOf('=')
        if (i < 0) dec(it) to "" else dec(it.substring(0, i)) to dec(it.substring(i + 1))
    }

    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")
}

/** Actions the API can trigger; implemented by the Android service. */
interface NodeActions {
    fun status(): Map<String, Any?>
    fun wake()
    fun ambient()
    fun speak(text: String): Boolean
    fun reload()
    fun presence(source: String)
}

class ApiRouter(private val apiKey: String, private val actions: NodeActions) {
    fun handle(req: HttpRequest): HttpResponse {
        if (req.path == "/api/ping") return HttpResponse.json(200, mapOf("ok" to true))
        if (apiKey.isEmpty()) return HttpResponse.error(401, "control API disabled (no api_key set)")
        val given = req.headers["x-synapse-key"] ?: bearer(req.headers["authorization"]) ?: ""
        if (!constantTimeEquals(given, apiKey)) return HttpResponse.error(401, "bad or missing X-Synapse-Key")
        return try {
            route(req)
        } catch (e: Exception) {
            HttpResponse.error(400, e.message ?: "error")
        }
    }

    private fun route(req: HttpRequest): HttpResponse {
        fun needPost(): HttpResponse? = if (req.method != "POST") HttpResponse.error(405, "use POST") else null
        return when (req.path) {
            "/api/status" -> HttpResponse.json(200, actions.status())
            "/api/wake" -> needPost() ?: run { actions.wake(); ok() }
            "/api/ambient" -> needPost() ?: run { actions.ambient(); ok() }
            "/api/reload" -> needPost() ?: run { actions.reload(); ok() }
            "/api/presence" -> needPost() ?: run {
                val src = body(req)["source"] as? String ?: "external"
                actions.presence(src); ok()
            }
            "/api/speak" -> needPost() ?: run {
                val text = (body(req)["text"] as? String)?.trim().orEmpty()
                if (text.isEmpty() || text.length > 1000) HttpResponse.error(400, "text must be 1-1000 chars")
                else if (actions.speak(text)) ok() else HttpResponse.error(503, "TTS not ready")
            }
            else -> HttpResponse.error(404, "unknown endpoint ${req.path}")
        }
    }

    private fun ok() = HttpResponse.json(200, mapOf("ok" to true))
    private fun body(req: HttpRequest): Map<String, Any?> =
        if (req.body.isBlank()) emptyMap() else Json.parseObject(req.body)

    private fun bearer(h: String?): String? =
        h?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)?.trim()

    companion object {
        fun constantTimeEquals(a: String, b: String): Boolean =
            MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }
}

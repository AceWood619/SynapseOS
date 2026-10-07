package com.acewood.synapse.core

import com.acewood.synapse.logic.NodeConfig
import java.net.HttpURLConnection
import java.net.URL

/** Talks to Home Assistant's REST API with the node's long-lived token. Call off the main thread. */
class HaClient(private val cfg: NodeConfig) {
    @Volatile var reachable = false
        private set
    @Volatile var lastError: String? = null
        private set
    @Volatile var lastOkMs = 0L
        private set

    fun ping(): Boolean = request("GET", "/api/", null) == 200

    fun postState(entityId: String, body: String): Boolean = request("POST", "/api/states/$entityId", body) in 200..299

    fun fireEvent(eventType: String, body: String): Boolean = request("POST", "/api/events/$eventType", body) in 200..299

    private fun request(method: String, path: String, body: String?): Int {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(cfg.haUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 4_000
                readTimeout = 6_000
                setRequestProperty("Authorization", "Bearer ${cfg.haToken}")
                setRequestProperty("Content-Type", "application/json")
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = conn.responseCode
            (if (code < 400) conn.inputStream else conn.errorStream)?.use { it.readBytes() }
            if (code in 200..299) {
                reachable = true; lastOkMs = System.currentTimeMillis(); lastError = null
            } else {
                lastError = "HTTP $code on $method $path"
                if (code == 401) reachable = false
            }
            code
        } catch (e: Exception) {
            reachable = false
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            -1
        } finally {
            conn?.disconnect()
        }
    }
}

package com.acewood.synapse.core

import android.util.Log
import com.acewood.synapse.logic.ApiRouter
import com.acewood.synapse.logic.HttpParser
import com.acewood.synapse.logic.HttpResponse
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.Executors

/** Tiny LAN HTTP server so Home Assistant (rest_command) can poke the node. */
class ControlServer(private val port: Int, private val router: ApiRouter) {
    private var server: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(2)
    @Volatile var running = false
        private set

    fun start() {
        if (running) return
        running = true
        Thread({
            try {
                val s = ServerSocket()
                s.reuseAddress = true
                s.bind(InetSocketAddress(port))
                server = s
                Log.i(SynapseApp.TAG, "control API listening on :$port")
                while (running) {
                    val c = s.accept()
                    pool.execute {
                        c.use { sock ->
                            sock.soTimeout = 5_000
                            val resp = try {
                                router.handle(HttpParser.read(sock.getInputStream()))
                            } catch (e: HttpParser.BadRequest) {
                                HttpResponse.error(e.status, e.message ?: "bad request")
                            } catch (e: Exception) {
                                HttpResponse.error(400, e.message ?: "error")
                            }
                            try { sock.getOutputStream().write(resp.toBytes()) } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                if (running) Log.w(SynapseApp.TAG, "control API stopped: $e")
            } finally {
                running = false
            }
        }, "synapse-api").start()
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        pool.shutdownNow()
    }
}

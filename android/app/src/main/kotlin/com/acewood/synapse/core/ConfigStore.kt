package com.acewood.synapse.core

import android.content.Context
import android.util.Log
import com.acewood.synapse.logic.Json
import com.acewood.synapse.logic.NodeConfig
import java.io.File

/**
 * Config lives in app-private storage (on the phone's encrypted /data).
 * Provisioning drops a JSON file at
 *   /sdcard/Android/data/com.acewood.synapse.core/files/config.json
 * The app imports it, writes config.result.json next to it, and deletes the original
 * so the token doesn't sit on shared storage.
 */
object ConfigStore {
    private const val PREFS = "synapse"
    private const val KEY = "config_json"

    fun load(ctx: Context): NodeConfig? {
        val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        return try { NodeConfig.fromJson(s) } catch (e: Exception) { null }
    }

    /** Returns true if a new config was imported. Synchronized: the service and the screen both poll for the file. */
    @Synchronized
    fun importIfPresent(ctx: Context): Boolean {
        val dir = ctx.getExternalFilesDir(null) ?: return false
        val f = File(dir, "config.json")
        if (!f.exists()) return false
        val result = File(dir, "config.result.json")
        return try {
            val cfg = NodeConfig.fromJson(f.readText())
            val errors = cfg.validate()
            if (errors.isNotEmpty()) {
                result.writeText(Json.write(mapOf("ok" to false, "errors" to errors, "at" to System.currentTimeMillis())))
                Log.w(SynapseApp.TAG, "config.json rejected: $errors")
                false
            } else {
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, cfg.toJson()).commit()
                result.writeText(Json.write(mapOf("ok" to true, "config" to cfg.redacted(), "at" to System.currentTimeMillis())))
                Log.i(SynapseApp.TAG, "config imported for node ${cfg.nodeId}")
                true
            }
        } catch (e: Exception) {
            result.writeText(Json.write(mapOf("ok" to false, "errors" to listOf(e.message ?: e.toString()))))
            false
        } finally {
            f.delete()
        }
    }
}

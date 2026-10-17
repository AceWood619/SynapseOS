package com.acewood.synapse.core

import android.content.Context
import com.acewood.synapse.logic.NodeConfig
import com.acewood.synapse.logic.Profile
import com.acewood.synapse.logic.Profiles

/** Stores only the profile JSON (PIN hashes and layouts), never a clear PIN. */
object ProfileStore {
    private const val PREFS = "synapse_profiles"
    private const val KEY = "profiles_json"

    @Synchronized
    fun loadOrCreate(ctx: Context, cfg: NodeConfig): Profiles? {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY, null)
        if (!saved.isNullOrBlank()) {
            return runCatching { Profiles.fromJson(saved) }.getOrNull()
                ?.takeIf { it.validate().isEmpty() }
        }
        // Existing provisioning supplies the admin PIN. It is hashed immediately and is never
        // copied into SharedPreferences or logs in clear text.
        if (cfg.pin.isBlank()) return null
        val created = Profiles.starter(cfg.ownerName.ifBlank { "Admin" }, cfg.pin)
        prefs.edit().putString(KEY, created.toJson()).apply()
        return created
    }

    private const val LAST = "last_profile_id"
    /** Remembers who used the remote last, so a reboot/app update goes straight back to the home screen. */
    fun lastProfileId(ctx: Context): String? = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LAST, null)
    fun rememberProfile(ctx: Context, id: String?) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(LAST, id).apply()
    }

    fun save(ctx: Context, profiles: Profiles) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, profiles.toJson()).apply()
    }
}

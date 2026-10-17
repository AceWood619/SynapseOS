package com.acewood.synapse.core

import android.content.Context
import com.acewood.synapse.logic.PanelSettings

/** Stores non-secret panel preferences separately from the provisioned HA config. */
object PanelSettingsStore {
    private const val PREFS = "synapse_panel"

    fun load(ctx: Context): PanelSettings {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return PanelSettings(
            idleSeconds = p.getInt("idle_seconds", 120),
            ambientBrightness = p.getFloat("ambient_brightness", 0.02f),
            nightDimEnabled = p.getBoolean("night_dim_enabled", false),
            nightStart = p.getString("night_start", "21:00") ?: "21:00",
            nightEnd = p.getString("night_end", "06:00") ?: "06:00",
            nightBrightness = p.getFloat("night_brightness", 0.01f),
            hapticsEnabled = p.getBoolean("haptics_enabled", true),
            speakReplies = p.getBoolean("speak_replies", true),
        )
    }

    fun save(ctx: Context, settings: PanelSettings) {
        require(settings.validate().isEmpty()) { settings.validate().joinToString("; ") }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("idle_seconds", settings.idleSeconds)
            .putFloat("ambient_brightness", settings.ambientBrightness)
            .putBoolean("night_dim_enabled", settings.nightDimEnabled)
            .putString("night_start", settings.nightStart)
            .putString("night_end", settings.nightEnd)
            .putFloat("night_brightness", settings.nightBrightness)
            .putBoolean("haptics_enabled", settings.hapticsEnabled)
            .putBoolean("speak_replies", settings.speakReplies)
            .apply()
    }
}

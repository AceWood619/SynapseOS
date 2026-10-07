package com.acewood.synapse.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Log
import com.acewood.synapse.logic.Profile
import com.acewood.synapse.logic.Role

/**
 * The apps Synapse offers on its home dock and in the app drawer. Two kinds:
 *  - built-in Synapse screens (Jarvis chat, Sensors, Home Assistant, Settings), and
 *  - real Android apps that are installed AND allowed in kiosk (lock-task) mode.
 * Settings-type entries are PIN-gated by the caller.
 */
object AppCatalog {
    sealed class Target {
        data class Pkg(val pkg: String) : Target()
        data class Internal(val id: String) : Target()
    }

    data class App(val label: String, val glyph: String, val target: Target, val adminOnly: Boolean = false) {
        val pkg: String? get() = (target as? Target.Pkg)?.pkg
    }

    /** Android apps the kiosk lets through. Order = dock/drawer order. Missing ones are skipped. */
    val ANDROID_APPS = listOf(
        App("Clock", "◷", Target.Pkg("com.android.deskclock")),
        App("Calendar", "▦", Target.Pkg("org.lineageos.etar")),
        App("Calculator", "±", Target.Pkg("com.android.calculator2")),
        App("Recorder", "◉", Target.Pkg("org.lineageos.recorder")),
        App("Files", "▤", Target.Pkg("com.android.documentsui")),
        App("Voice", "♪", Target.Pkg("org.woheller69.ttsengine"), adminOnly = true),
        App("Android settings", "⚙", Target.Pkg("com.android.settings"), adminOnly = true),
    )

    val INTERNAL_APPS = listOf(
        App("Jarvis", "✦", Target.Internal("jarvis")),
        App("Sensors", "≋", Target.Internal("sensors")),
        App("Home Assistant", "⌂", Target.Internal("ha")),
        App("Music", "♫", Target.Internal("music")),
        App("Synapse Browser", "◍", Target.Internal("browser")),
        App("Synapse Camera", "◎", Target.Internal("camera")),
        App("Synapse settings", "⚙", Target.Internal("settings"), adminOnly = true),
    )

    /** The dock on the home screen (first row). */
    val DOCK_IDS = listOf("jarvis", "camera", "sensors", "music", "browser", "com.android.deskclock")

    fun key(a: App) = when (val t = a.target) { is Target.Pkg -> t.pkg; is Target.Internal -> t.id }

    fun installed(ctx: Context, pkg: String): Boolean =
        try { ctx.packageManager.getLaunchIntentForPackage(pkg) != null } catch (_: Exception) { false }

    fun all(ctx: Context, profile: Profile? = null): List<App> {
        val available = INTERNAL_APPS + ANDROID_APPS.filter { a -> a.pkg?.let { installed(ctx, it) } ?: true }
        if (profile == null || profile.role == Role.ADMIN) return available
        val requested = profile.layout.homeApps.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return available.filter { !it.adminOnly && (requested.isEmpty() || key(it) in requested) }
    }

    fun dock(ctx: Context, profile: Profile? = null): List<App> {
        val byKey = all(ctx, profile).associateBy { key(it) }
        return DOCK_IDS.mapNotNull { byKey[it] }
    }

    /** Packages that must be in the lock-task allowlist so the dock/drawer can open them. */
    fun lockTaskPackages(ctx: Context): List<String> =
        ANDROID_APPS.filter { !it.adminOnly }.mapNotNull { it.pkg }.filter { installed(ctx, it) }

    fun icon(ctx: Context, a: App): Drawable? =
        a.pkg?.let { try { ctx.packageManager.getApplicationIcon(it) } catch (_: Exception) { null } }

    fun launch(activity: Activity, pkg: String): Boolean = try {
        val i = activity.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        activity.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        Log.w(SynapseApp.TAG, "can't open $pkg", e); false
    }
}

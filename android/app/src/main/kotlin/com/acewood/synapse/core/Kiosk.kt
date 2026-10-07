package com.acewood.synapse.core

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.util.Log

/**
 * Device-owner lockdown. Only takes effect after provisioning ran:
 *   adb shell dpm set-device-owner com.acewood.synapse.core/.AdminReceiver
 * Without device owner the app still works as a normal launcher (no lockdown).
 */
object Kiosk {
    /** When set in the future, MainActivity won't re-pin lock-task (so "Leave kiosk" actually sticks). */
    @Volatile var pausedUntilMs: Long = 0L
    fun kioskPaused() = android.os.SystemClock.elapsedRealtime() < pausedUntilMs
    fun pauseKiosk(ms: Long = 180_000) { pausedUntilMs = android.os.SystemClock.elapsedRealtime() + ms }
    fun resumeKiosk() { pausedUntilMs = 0L }

    fun admin(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)

    fun isDeviceOwner(ctx: Context): Boolean =
        ctx.getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(ctx.packageName)

    /** Idempotent; safe to call on every start. */
    fun applyPolicies(ctx: Context) {
        if (!isDeviceOwner(ctx)) return
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        val a = admin(ctx)
        try {
            val companions = ConfigStore.load(ctx)?.companionApps.orEmpty()
            val extra = if (kioskPaused()) listOf("com.android.settings") else emptyList()
            // Apps on the Synapse dock/drawer (camera, clock, browser…) may open inside the kiosk.
            val dockApps = AppCatalog.lockTaskPackages(ctx)
            dpm.setLockTaskPackages(a, (listOf(ctx.packageName) + companions + dockApps + extra).distinct().toTypedArray())
            // Keep the power menu, and a working Home button so any opened app can always return to Synapse.
            dpm.setLockTaskFeatures(a, DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or DevicePolicyManager.LOCK_TASK_FEATURE_HOME)
            val home = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(a, home, ComponentName(ctx, MainActivity::class.java))
            dpm.setKeyguardDisabled(a, true)
            dpm.setStatusBarDisabled(a, true)
            // Screen stays on whenever plugged in (AC|USB|wireless = 7).
            dpm.setGlobalSetting(a, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, "7")
            dpm.setPermissionGrantState(
                a, ctx.packageName, android.Manifest.permission.POST_NOTIFICATIONS,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )
        } catch (e: Exception) {
            Log.w(SynapseApp.TAG, "applyPolicies failed", e)
        }
    }

    fun enterLockTask(activity: Activity) {
        if (kioskPaused()) return
        val dpm = activity.getSystemService(DevicePolicyManager::class.java)
        if (dpm.isLockTaskPermitted(activity.packageName)) {
            try { activity.startLockTask() } catch (e: Exception) { Log.w(SynapseApp.TAG, "startLockTask", e) }
        }
    }

    fun exitLockTask(activity: Activity) {
        try { activity.stopLockTask() } catch (_: Exception) {}
    }

    /** Temporarily allow the status bar and Settings app (from the PIN-protected settings screen). */
    fun relax(ctx: Context) {
        if (!isDeviceOwner(ctx)) return
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        try { dpm.setStatusBarDisabled(admin(ctx), false) } catch (_: Exception) {}
    }
}

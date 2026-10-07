package com.acewood.synapse.core

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.acewood.synapse.logic.Json

/** PIN-protected maintenance screen (5 taps top-left on the dashboard). */
class SettingsActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        status = TextView(this).apply { typeface = Typeface.MONOSPACE; textSize = 12f }
        col.addView(status)
        fun button(label: String, action: () -> Unit) = col.addView(Button(this).apply {
            text = label
            setOnClickListener { action(); refresh() }
        })
        button("Back to dashboard") { finish() }
        button("Import config.json now") {
            ConfigStore.importIfPresent(this)
            NodeBus.send(NodeBus.Command.CONFIG_CHANGED)
        }
        button("Reload dashboard") { NodeBus.send(NodeBus.Command.RELOAD) }
        button("Clear dashboard login/cache") {
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
            NodeBus.send(NodeBus.Command.RELOAD)
        }
        button("Leave kiosk → Android Settings") {
            Kiosk.pauseKiosk()                          // stop MainActivity re-pinning
            Kiosk.relax(this)
            Kiosk.applyPolicies(this)                   // re-publish lock-task list incl. com.android.settings
            try { stopLockTask() } catch (_: Exception) {}
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        button("Return to kiosk") {
            Kiosk.resumeKiosk()
            Kiosk.applyPolicies(this)
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            finish()
        }
        button("Remove device owner (undo kiosk lockdown)") {
            if (Kiosk.isDeviceOwner(this)) {
                Kiosk.relax(this)
                try { stopLockTask() } catch (_: Exception) {}
                getSystemService(android.app.admin.DevicePolicyManager::class.java).clearDeviceOwnerApp(packageName)
            }
        }
        setContentView(ScrollView(this).apply { addView(col) })
        refresh()
    }

    private fun refresh() {
        val cfg = ConfigStore.load(this)
        val am = getSystemService(ActivityManager::class.java)
        val lock = when (am.lockTaskModeState) {
            ActivityManager.LOCK_TASK_MODE_LOCKED -> "locked"
            ActivityManager.LOCK_TASK_MODE_PINNED -> "pinned"
            else -> "off"
        }
        status.text = buildString {
            appendLine("Synapse ${BuildConfigInfo.versionName(this@SettingsActivity)}")
            appendLine("device owner: ${Kiosk.isDeviceOwner(this@SettingsActivity)}   lock task: $lock")
            appendLine("screen: ${NodeBus.screenMode}")
            appendLine()
            appendLine(if (cfg == null) "NOT CONFIGURED" else Json.write(cfg.redacted()).replace(",", ",\n "))
        }
    }
}

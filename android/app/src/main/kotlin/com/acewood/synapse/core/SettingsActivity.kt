package com.acewood.synapse.core

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.acewood.synapse.logic.Json
import com.acewood.synapse.logic.Role

/** Synapse-native settings and maintenance surface. Only the admin profile can open it. */
class SettingsActivity : Activity() {
    private val g = Glass
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = g.ground()
            setPadding(g.dp(this@SettingsActivity, 18f), g.dp(this@SettingsActivity, 16f),
                g.dp(this@SettingsActivity, 18f), g.dp(this@SettingsActivity, 20f))
        }
        outer.addView(g.row(this).apply {
            addView(TextView(context).apply {
                text = "‹  HOME"; textSize = 12f; setTextColor(Glass.BLUE); letterSpacing = 0.1f
                background = g.tile(context, Glass.BLUE, false, 14f)
                setPadding(g.dp(context, 14f), g.dp(context, 10f), g.dp(context, 14f), g.dp(context, 10f))
                tap { finish() }
            })
            addView(TextView(context).apply {
                text = "SYNAPSE SETTINGS"; textSize = 20f; setTextColor(Glass.INK); typeface = g.disp(context)
                setPadding(g.dp(context, 14f), 0, 0, 0)
            })
        })
        outer.addView(g.spacer(this, h = 16))
        status = TextView(this).apply { textSize = 12f; setTextColor(Glass.INK_DIM); typeface = g.body(context) }
        outer.addView(section("NODE STATUS", status))
        outer.addView(g.spacer(this, h = 12))
        outer.addView(section("PROFILES", TextView(this).apply {
            text = profileSummary(); textSize = 13f; setTextColor(Glass.INK_DIM)
        }))
        outer.addView(g.spacer(this, h = 12))
        addAction(outer, "Manage profiles (admin PIN)") { openProfileAdmin() }
        outer.addView(g.spacer(this, h = 4))
        outer.addView(g.label(this, "MAINTENANCE"))
        outer.addView(g.spacer(this, h = 7))
        addAction(outer, "Reload Synapse dashboard") { NodeBus.send(NodeBus.Command.RELOAD) }
        addAction(outer, "Import config.json") {
            ConfigStore.importIfPresent(this); NodeBus.send(NodeBus.Command.CONFIG_CHANGED); refreshStatus()
        }
        addAction(outer, "Clear HA login and browser cache") {
            CookieManager.getInstance().removeAllCookies(null); WebStorage.getInstance().deleteAllData(); NodeBus.send(NodeBus.Command.RELOAD)
        }
        addAction(outer, "Leave kiosk → Android Settings") {
            Kiosk.pauseKiosk(); Kiosk.relax(this); Kiosk.applyPolicies(this)
            runCatching { stopLockTask() }
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        addAction(outer, "Return to Synapse kiosk") {
            Kiosk.resumeKiosk(); Kiosk.applyPolicies(this)
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); finish()
        }
        addAction(outer, "Remove device owner (dangerous)", Glass.RED) {
            if (Kiosk.isDeviceOwner(this)) {
                Kiosk.relax(this); runCatching { stopLockTask() }
                getSystemService(android.app.admin.DevicePolicyManager::class.java).clearDeviceOwnerApp(packageName)
            }
        }
        setContentView(ScrollView(this).apply { addView(outer) })
        refreshStatus()
    }

    private fun section(title: String, body: TextView): LinearLayout = g.col(this).apply {
        background = g.panel(this@SettingsActivity, 18f)
        setPadding(g.dp(context, 14f), g.dp(context, 12f), g.dp(context, 14f), g.dp(context, 12f))
        addView(g.label(context, title)); addView(g.spacer(context, h = 7)); addView(body)
    }

    private fun addAction(parent: LinearLayout, label: String, color: Int = Glass.BLUE, action: () -> Unit) {
        parent.addView(TextView(this).apply {
            text = label; textSize = 14f; setTextColor(if (color == Glass.RED) Color.WHITE else Glass.INK)
            background = g.tile(context, color, false, 14f)
            setPadding(g.dp(context, 14f), g.dp(context, 15f), g.dp(context, 14f), g.dp(context, 15f))
            layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = g.dp(context, 8f) }
            tap { action() }
        })
    }

    private fun profileSummary(): String {
        val cfg = ConfigStore.load(this) ?: return "Profiles are created after provisioning."
        val ps = ProfileStore.loadOrCreate(this, cfg) ?: return "No profile PIN is configured."
        return ps.list.joinToString("\n") { p -> "${p.name} · ${p.role.name.lowercase()} · ${if (p.pinHash.isEmpty()) "no PIN" else "PIN protected"}" }
    }

    private fun openProfileAdmin() {
        val cfg = ConfigStore.load(this) ?: return
        val profiles = ProfileStore.loadOrCreate(this, cfg)
        if (profiles == null) { Toast.makeText(this, "Profiles are not configured", Toast.LENGTH_SHORT).show(); return }
        val input = EditText(this).apply {
            hint = "Admin PIN"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this).setTitle("Profile admin").setMessage("Enter an admin profile PIN to continue.").setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("UNLOCK") { _, _ ->
                val admin = profiles.resolve(input.text.toString())?.role == Role.ADMIN
                if (!admin) Toast.makeText(this, "Admin PIN required", Toast.LENGTH_SHORT).show()
                else setContentView(ProfileAdminView(this, profiles) { recreate() })
            }.show()
    }

    private fun refreshStatus() {
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
            appendLine(if (cfg == null) "NOT CONFIGURED" else Json.write(cfg.redacted()).replace(",", ",\n"))
        }
    }
}

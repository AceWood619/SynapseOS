package com.acewood.synapse.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.acewood.synapse.logic.NodeConfig
import com.acewood.synapse.logic.SetupForm
import com.acewood.synapse.logic.SetupInput
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * First-run setup for a fresh panel (originally by ChatGPT Desktop Commander, commit 0a20e34; landed by
 * Claude with its own review fixes): SAVE only after a successful Home Assistant test of the same URL
 * and token, no hard-coded LAN address, and the new Glass fonts. Secrets stay in app-private storage.
 */
@SuppressLint("ViewConstructor")
class SetupWizardView(context: Context, private val onConfigured: (NodeConfig) -> Unit) : ScrollView(context) {
    private val g = Glass
    private fun dp(v: Float) = g.dp(context, v)
    private val ink = Glass.INK
    private val muted = Color.rgb(151, 164, 190)

    private val owner = field("Your name", "")
    private val nodeId = field("Device ID", "synapse-panel")
    private val room = field("Room / location", "livingroom")
    private val haUrl = field("Home Assistant URL (e.g. ${SetupForm.URL_HINT})", "").apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
    }
    private val haToken = field("Long-lived access token", "").apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }
    private val pin = field("Create admin PIN (4+ digits)", "").apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }
    private val confirmPin = field("Confirm admin PIN", "").apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }
    private val status = TextView(context).apply {
        text = "Your token and PIN stay on this device."; textSize = 13f; setTextColor(muted); typeface = g.body(context)
    }
    private lateinit var saveKey: TextView
    private var testing = false
    /** Fingerprint of the URL+token that last tested OK; null = not verified. */
    private var verified: String? = null

    init {
        isFillViewport = true
        background = g.ground()
        isVerticalScrollBarEnabled = false
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20f), dp(28f), dp(20f), dp(28f)) }
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        content.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            addView(NeuronOrbView(context), LinearLayout.LayoutParams(dp(84f), dp(84f)))
            addView(text("SYNAPSE", 13f, Glass.MINT, g.bodyBold(context)).apply { letterSpacing = 0.3f; gravity = Gravity.CENTER; setPadding(0, dp(16f), 0, dp(6f)) })
            addView(text("Your home. One remote.", 24f, ink, g.disp(context)).apply { gravity = Gravity.CENTER })
            addView(text("A quick setup, then you're home.", 13f, muted, g.body(context)).apply { gravity = Gravity.CENTER; setPadding(0, dp(8f), 0, dp(22f)) })
        })
        content.addView(step("1", "This panel", "Give it a name and say which room it lives in.", owner, nodeId, room))
        content.addView(gap(12f))
        content.addView(step("2", "Connect Home Assistant", "Home Assistant runs your lights, TVs, sensors and Jarvis. Paste its address and a long-lived token (HA → Profile → Security).", haUrl, haToken))
        content.addView(gap(12f))
        content.addView(step("3", "Secure the remote", "Create an admin PIN. Add household profiles later in Settings.", pin, confirmPin))
        content.addView(gap(14f))
        content.addView(button("Test Home Assistant", primary = false) { testConnection() })
        content.addView(gap(10f))
        content.addView(status)
        content.addView(gap(14f))
        saveKey = button("Save & open Synapse", primary = true) { saveConfig() }
        content.addView(saveKey)
        content.addView(gap(12f))
        content.addView(text("Add devices in Home Assistant first, then come back. You can change the connection later in Settings.", 12f, muted, g.body(context)).apply { gravity = Gravity.CENTER })

        // Editing the address or token un-verifies the connection.
        val unverify = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { if (verified != null) { verified = null; bindSave() } }
        }
        haUrl.addTextChangedListener(unverify); haToken.addTextChangedListener(unverify)
        bindSave()
    }

    private fun input() = SetupInput(owner.text.toString(), nodeId.text.toString(), room.text.toString(),
        haUrl.text.toString(), haToken.text.toString(), pin.text.toString(), confirmPin.text.toString())

    private fun bindSave() { saveKey.alpha = if (verified != null) 1f else 0.5f }

    private fun say(msg: String, color: Int) { status.text = msg; status.setTextColor(color) }

    private fun testConnection() {
        if (testing) return
        val i = input()
        SetupForm.problem(i)?.let { say(it, Color.rgb(255, 132, 145)); return }
        val cfg = SetupForm.toConfig(i)
        testing = true; verified = null; bindSave()
        say("Checking Home Assistant…", muted)
        Thread({
            val code = runCatching {
                val client = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()
                val req = Request.Builder().url(cfg.haUrl + "/api/").header("Authorization", "Bearer " + cfg.haToken).header("Accept", "application/json").build()
                client.newCall(req).execute().use { it.code }
            }.getOrNull()
            val (ok, msg) = SetupForm.testMessage(code)
            post {
                testing = false
                verified = if (ok) SetupForm.fingerprint(i.haUrl, i.haToken) else null
                bindSave()
                say(msg, if (ok) Glass.MINT else Color.rgb(255, 174, 113))
            }
        }, "synapse-setup-check").start()
    }

    private fun saveConfig() {
        val i = input()
        SetupForm.saveBlocker(i, verified)?.let { say(it, Color.rgb(255, 174, 113)); return }
        val cfg = SetupForm.toConfig(i)
        runCatching { ConfigStore.save(context, cfg) }
            .onSuccess { say("Saved on this device. Opening Synapse…", Glass.MINT); onConfigured(cfg) }
            .onFailure { say("Couldn't save setup: ${it.message ?: "storage error"}", Color.rgb(255, 132, 145)) }
    }

    // ---------- building blocks ----------

    private fun step(n: String, title: String, sub: String, vararg fields: EditText): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { cornerRadius = dp(22f).toFloat(); setColor(Color.argb(12, 255, 255, 255)); setStroke(dp(1f), Color.argb(56, 130, 170, 255)) }
        setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(text(n, 13f, Glass.MINT, g.bodyBold(context)).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Glass.withAlpha(Glass.MINT, 36)) }
            }, LinearLayout.LayoutParams(dp(32f), dp(32f)))
            addView(text(title, 16f, ink, g.bodyBold(context)).apply { setPadding(dp(12f), 0, 0, 0) })
        })
        addView(text(sub, 13f, muted, g.body(context)).apply { setPadding(0, dp(10f), 0, dp(12f)) })
        fields.forEachIndexed { i, f -> if (i > 0) addView(gap(8f)); addView(f) }
    }

    private fun field(hintText: String, initial: String) = EditText(context).apply {
        hint = hintText; setText(initial); textSize = 15f; setTextColor(ink); typeface = g.body(context)
        setHintTextColor(Color.rgb(103, 119, 148)); setSingleLine(true)
        background = GradientDrawable().apply { cornerRadius = dp(14f).toFloat(); setColor(Color.rgb(8, 14, 27)); setStroke(dp(1f), Color.rgb(37, 53, 78)) }
        setPadding(dp(14f), dp(13f), dp(14f), dp(13f))
        minHeight = dp(48f)
    }

    private fun button(label: String, primary: Boolean, run: () -> Unit) = text(label, 15f, if (primary) Color.rgb(4, 16, 31) else ink, g.bodyBold(context)).apply {
        gravity = Gravity.CENTER; minHeight = dp(52f)
        background = GradientDrawable().apply {
            cornerRadius = dp(16f).toFloat()
            if (primary) setColor(Glass.BLUE) else { setColor(Color.argb(14, 255, 255, 255)); setStroke(dp(1f), Glass.withAlpha(Glass.BLUE, 140)) }
        }
        tap { run() }
    }

    private fun text(t: String, size: Float, color: Int, tf: android.graphics.Typeface) = TextView(context).apply {
        text = t; textSize = size; setTextColor(color); typeface = tf
    }

    private fun gap(h: Float) = View(context).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
}

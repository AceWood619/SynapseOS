package com.acewood.synapse.core

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.acewood.synapse.logic.PanelSettings

/** Admin-only editor for local panel behavior; no HA credentials are exposed here. */
class PanelSettingsView(
    context: Context,
    initial: PanelSettings,
    private val onSaved: () -> Unit,
) : ScrollView(context) {
    private val g = Glass
    private val idle = number("Idle timeout (seconds)", initial.idleSeconds.toString())
    private val ambient = decimal("Ambient brightness (0..1)", initial.ambientBrightness.toString())
    private val nightEnabled = Switch(context).apply { text = "Night dim schedule"; isChecked = initial.nightDimEnabled; setTextColor(Glass.INK) }
    private val nightStart = text("Night start (HH:MM)", initial.nightStart)
    private val nightEnd = text("Night end (HH:MM)", initial.nightEnd)
    private val nightBrightness = decimal("Night brightness (0..1)", initial.nightBrightness.toString())
    private val haptics = Switch(context).apply { text = "Touch haptics"; isChecked = initial.hapticsEnabled; setTextColor(Glass.INK) }
    private val speak = Switch(context).apply { text = "Speak Jarvis replies by default"; isChecked = initial.speakReplies; setTextColor(Glass.INK) }

    init {
        val body = g.col(context).apply {
            background = g.ground()
            setPadding(g.dp(context, 18f), g.dp(context, 16f), g.dp(context, 18f), g.dp(context, 24f))
            addView(TextView(context).apply { text = "‹  SYNAPSE SETTINGS"; textSize = 12f; setTextColor(Glass.BLUE); tap { onSaved() } })
            addView(g.spacer(context, h = 14))
            addView(TextView(context).apply { text = "PANEL SETTINGS"; textSize = 22f; typeface = g.disp(context); setTextColor(Glass.INK) })
            addView(g.spacer(context, h = 6))
            addView(TextView(context).apply { text = "Admin-only local preferences · applied immediately"; textSize = 12f; setTextColor(Glass.INK_DIM) })
            addView(g.spacer(context, h = 16))
            addView(idle); addView(ambient); addView(nightEnabled); addView(nightStart); addView(nightEnd); addView(nightBrightness)
            addView(haptics); addView(speak)
            addView(g.spacer(context, h = 12))
            addView(TextView(context).apply {
                text = "SAVE PANEL SETTINGS"; textSize = 14f; setTextColor(Glass.INK); background = g.tile(context, Glass.BLUE, true, 14f)
                setPadding(g.dp(context, 14f), g.dp(context, 15f), g.dp(context, 14f), g.dp(context, 15f)); tap { save() }
            })
        }
        addView(body)
    }

    private fun text(label: String, value: String): EditText = EditText(context).apply {
        hint = label; setText(value); setTextColor(Glass.INK); setHintTextColor(Glass.INK_FAINT); textSize = 14f
        setSingleLine(true)
        setPadding(g.dp(context, 12f), g.dp(context, 5f), g.dp(context, 12f), g.dp(context, 5f))
        background = g.tile(context, Glass.VIOLET, false, 10f)
        layoutParams = LinearLayout.LayoutParams(-1, g.dp(context, 48f)).apply { bottomMargin = g.dp(context, 7f) }
    }

    private fun number(label: String, value: String) = text(label, value).apply { inputType = InputType.TYPE_CLASS_NUMBER }
    private fun decimal(label: String, value: String) = text(label, value).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }

    private fun save() {
        val settings = PanelSettings(
            idleSeconds = idle.text.toString().trim().toIntOrNull() ?: -1,
            ambientBrightness = ambient.text.toString().trim().toFloatOrNull() ?: -1f,
            nightDimEnabled = nightEnabled.isChecked,
            nightStart = nightStart.text.toString().trim(),
            nightEnd = nightEnd.text.toString().trim(),
            nightBrightness = nightBrightness.text.toString().trim().toFloatOrNull() ?: -1f,
            hapticsEnabled = haptics.isChecked,
            speakReplies = speak.isChecked,
        )
        val errors = settings.validate()
        if (errors.isNotEmpty()) {
            Toast.makeText(context, errors.joinToString("\n"), Toast.LENGTH_LONG).show()
            return
        }
        PanelSettingsStore.save(context, settings)
        NodeBus.send(NodeBus.Command.CONFIG_CHANGED)
        Toast.makeText(context, "Panel settings applied", Toast.LENGTH_SHORT).show()
        onSaved()
    }
}

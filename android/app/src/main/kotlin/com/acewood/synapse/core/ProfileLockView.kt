package com.acewood.synapse.core

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.acewood.synapse.logic.Profile
import com.acewood.synapse.logic.Profiles

/** Full-screen profile picker. A profile with a PIN is unlocked only after local verification. */
class ProfileLockView(
    context: Context,
    private val profiles: Profiles,
    private val onUnlocked: (Profile) -> Unit,
) : FrameLayout(context) {
    private val g = Glass
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val pinInput = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        hint = "PIN"
        textSize = 22f
        gravity = Gravity.CENTER
        setTextColor(Glass.INK)
        setHintTextColor(Glass.INK_FAINT)
        background = g.panel(context, 16f)
        visibility = View.GONE
    }
    private var selected: Profile? = null

    init {
        background = g.ground()
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(g.dp(context, 24f), g.dp(context, 44f), g.dp(context, 24f), g.dp(context, 24f))
        }
        addView(outer, LayoutParams(-1, -1))
        outer.addView(TextView(context).apply {
            text = "SYNAPSE"
            textSize = 28f
            letterSpacing = 0.24f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(Glass.BLUE)
        })
        outer.addView(TextView(context).apply {
            text = "WHO'S USING THE HOUSE REMOTE?"
            textSize = 12f
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setTextColor(Glass.INK_DIM)
            setPadding(0, g.dp(context, 12f), 0, g.dp(context, 26f))
        })
        val cards = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        outer.addView(cards, LinearLayout.LayoutParams(-1, 0, 1f))
        profiles.list.forEach { profile ->
            cards.addView(profileCard(profile))
            cards.addView(g.spacer(context, h = 10))
        }
        outer.addView(pinInput, LinearLayout.LayoutParams(-1, g.dp(context, 58f)).apply {
            topMargin = g.dp(context, 8f); bottomMargin = g.dp(context, 10f)
        })
        outer.addView(TextView(context).apply {
            text = "UNLOCK"
            textSize = 14f
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = g.tile(context, Glass.BLUE, true, 16f)
            setPadding(0, g.dp(context, 15f), 0, g.dp(context, 15f))
            tap { unlock() }
        }, LinearLayout.LayoutParams(-1, g.dp(context, 54f)))
    }

    private fun profileCard(profile: Profile): View = g.row(context).apply {
        background = g.panel(context, 18f)
        setPadding(g.dp(context, 16f), g.dp(context, 14f), g.dp(context, 16f), g.dp(context, 14f))
        addView(TextView(context).apply {
            text = profile.name.trim().ifBlank { "Profile" }.first().uppercaseChar().toString()
            textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = g.tile(context, if (profile.role.canManageProfiles) Glass.BLUE else Glass.VIOLET, true, 999f)
            layoutParams = LinearLayout.LayoutParams(g.dp(context, 44f), g.dp(context, 44f)).apply { rightMargin = g.dp(context, 14f) }
        })
        addView(g.col(context).apply {
            addView(TextView(context).apply { text = profile.name; textSize = 17f; setTextColor(Glass.INK); typeface = g.disp(context) })
            addView(TextView(context).apply {
                text = if (profile.pinHash.isEmpty()) "Guest · tap to continue" else profile.role.name.lowercase().replaceFirstChar { it.uppercaseChar() } + " · PIN required"
                textSize = 11f; setTextColor(Glass.INK_DIM); setPadding(0, g.dp(context, 4f), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        tap { select(profile) }
    }

    private fun select(profile: Profile) {
        selected = profile
        pinInput.setText("")
        pinInput.visibility = if (profile.pinHash.isEmpty()) View.GONE else View.VISIBLE
        if (profile.pinHash.isEmpty()) onUnlocked(profile)
        else { pinInput.requestFocus(); (context as? android.app.Activity)?.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE) }
    }

    private fun unlock() {
        val profile = selected ?: run {
            Toast.makeText(context, "Choose a profile first", Toast.LENGTH_SHORT).show(); return
        }
        if (profiles.resolve(pinInput.text.toString())?.id == profile.id) onUnlocked(profile)
        else { pinInput.setText(""); Toast.makeText(context, "Wrong PIN", Toast.LENGTH_SHORT).show() }
    }
}

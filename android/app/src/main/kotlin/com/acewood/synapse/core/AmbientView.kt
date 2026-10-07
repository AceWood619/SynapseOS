package com.acewood.synapse.core

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import com.acewood.synapse.logic.AmbientData
import com.acewood.synapse.logic.EntityCache
import com.acewood.synapse.logic.NodeConfig
import com.acewood.synapse.logic.Profile

/** Low-power ambient face. MainActivity owns the burn-in-safe pixel shift and brightness ramp. */
class AmbientView(context: Context, private val onSwitchProfile: () -> Unit) : LinearLayout(context) {
    private val g = Glass
    private val date = TextClock(context)
    private val weather = label(15f, Glass.AMBER)
    private val timer = label(15f, Glass.MINT)
    private val nowWrap = g.col(context).apply { visibility = View.GONE }
    private val profileKey = label(11f, Glass.BLUE)
    private val roomKey = label(11f, Glass.INK_FAINT)

    init {
        orientation = VERTICAL; gravity = Gravity.CENTER
        setBackgroundColor(Color.rgb(5, 7, 13))
        addView(TextClock(context).apply {
            format12Hour = "h:mm"; format24Hour = "H:mm"; textSize = 92f; setTextColor(Color.rgb(180, 185, 200))
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL); letterSpacing = -0.02f; gravity = Gravity.CENTER
        })
        date.apply {
            format12Hour = "EEEE, MMMM d"; format24Hour = "EEEE, d MMMM"; textSize = 20f; setTextColor(Color.rgb(100, 108, 125))
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); gravity = Gravity.CENTER; setPadding(0, g.dp(context, 3f), 0, 0)
        }
        addView(date)
        addView(g.row(context).apply {
            gravity = Gravity.CENTER; setPadding(0, g.dp(context, 18f), 0, g.dp(context, 5f))
            addView(weather); addView(g.spacer(context, w = 18)); addView(timer)
        })
        addView(nowWrap)
        addView(profileKey.apply {
            gravity = Gravity.CENTER; background = g.tile(context, Glass.BLUE, false, 999f)
            setPadding(g.dp(context, 15f), g.dp(context, 9f), g.dp(context, 15f), g.dp(context, 9f)); tap { onSwitchProfile() }
        })
        addView(roomKey.apply { setPadding(0, g.dp(context, 12f), 0, 0) })
    }

    fun refresh(cache: EntityCache?, cfg: NodeConfig?, profile: Profile?) {
        val snap = AmbientData.snapshot(cache)
        weather.text = snap.weather ?: "Weather unavailable"
        timer.text = snap.timer?.let { "⏱  ${it.name}: ${it.remaining}" } ?: "No active timers"
        profileKey.text = "PROFILE  ·  ${profile?.name ?: "Choose profile"}"
        roomKey.text = cfg?.let { "●  ${it.room.uppercase()}" } ?: ""
        nowWrap.removeAllViews()
        val p = snap.nowPlaying
        if (p == null) { nowWrap.visibility = View.GONE; return }
        nowWrap.visibility = View.VISIBLE
        nowWrap.background = g.panel(context, 16f)
        nowWrap.setPadding(g.dp(context, 10f), g.dp(context, 8f), g.dp(context, 10f), g.dp(context, 8f))
        val art = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; background = g.tile(context, Glass.VIOLET, false, 10f)
            layoutParams = LayoutParams(g.dp(context, 46f), g.dp(context, 46f)).apply { rightMargin = g.dp(context, 10f) }
        }
        AlbumArtLoader.load(art, p.albumArt)
        nowWrap.addView(g.row(context).apply {
            addView(art)
            addView(g.col(context).apply {
                addView(label(14f, Glass.INK).apply { text = "▶  ${p.title}"; maxLines = 1 })
                addView(label(10.5f, Glass.INK_DIM).apply { text = p.appName ?: "Now playing"; maxLines = 1 })
            })
        })
    }

    private fun label(size: Float, color: Int) = TextView(context).apply {
        textSize = size; setTextColor(color); typeface = g.body(context)
    }
}

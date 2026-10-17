package com.acewood.synapse.core

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import com.acewood.synapse.logic.AmbientData
import com.acewood.synapse.logic.EntityCache
import com.acewood.synapse.logic.NodeConfig
import com.acewood.synapse.logic.Profile
import com.acewood.synapse.logic.WeatherForecast
import com.acewood.synapse.logic.WeatherForecastParser
import kotlin.random.Random

/**
 * Low-power ambient face (redesign 2026-10-16).
 *
 * The root is a full-screen, opaque, true-black frame that never moves, so nothing behind it can show.
 * Burn-in protection moves only [content] inside it by a few dp ([nudge]); every row is width-bounded,
 * so a nudge never pushes text off the screen. MainActivity still owns brightness and when to nudge.
 */
class AmbientView(context: Context, private val onSwitchProfile: () -> Unit) : FrameLayout(context) {
    private val g = Glass
    private val ink = Color.rgb(223, 230, 251)
    private val dim = Color.rgb(141, 155, 196)
    private val faint = Color.rgb(111, 124, 163)

    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
    private val date = TextClock(context)
    private val weather = label(15f, Color.rgb(233, 194, 122))
    private val timer = label(15f, Color.rgb(79, 214, 180))
    private val hourly = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
    private val nowWrap = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE }
    private val nowTitle = label(14f, Color.rgb(195, 205, 234)).apply { maxLines = 1; typeface = g.bodyBold(context) }
    private val nowSub = label(12f, faint).apply { maxLines = 1 }
    private val art = ImageView(context)
    private val profileKey = label(13f, dim)

    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        val side = g.dp(context, 28f)
        content.setPadding(side, 0, side, g.dp(context, 26f))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        content.addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
        content.addView(View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Glass.BLUE) }
            val s = g.dp(context, 10f); layoutParams = LinearLayout.LayoutParams(s, s).apply { bottomMargin = g.dp(context, 24f) }
        })
        content.addView(TextClock(context).apply {
            format12Hour = "h:mm"; format24Hour = "H:mm"; textSize = 96f; setTextColor(ink)
            typeface = g.light(context); letterSpacing = -0.03f; gravity = Gravity.CENTER; includeFontPadding = false
        })
        date.apply {
            format12Hour = "EEEE, MMMM d"; format24Hour = "EEEE, d MMMM"; textSize = 16f; setTextColor(dim)
            typeface = g.body(context); gravity = Gravity.CENTER; setPadding(0, g.dp(context, 10f), 0, 0)
        }
        content.addView(date)
        content.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, g.dp(context, 28f), 0, 0)
            addView(weather); addView(View(context), LinearLayout.LayoutParams(g.dp(context, 22f), 1)); addView(timer)
        })
        content.addView(hourly, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = g.dp(context, 20f) })
        content.addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))

        // Now playing, separated by a hairline. Bounded to the content width.
        nowWrap.setPadding(0, g.dp(context, 12f), 0, g.dp(context, 12f))
        nowWrap.background = GradientDrawable().apply { setColor(Color.BLACK); setStroke(0, 0) }
        art.scaleType = ImageView.ScaleType.CENTER_CROP
        art.background = GradientDrawable().apply { cornerRadius = g.dp(context, 10f).toFloat(); setColor(Color.rgb(21, 13, 43)) }
        art.clipToOutline = true
        nowWrap.addView(art, LinearLayout.LayoutParams(g.dp(context, 40f), g.dp(context, 40f)).apply { rightMargin = g.dp(context, 12f) })
        nowWrap.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; addView(nowTitle); addView(nowSub)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(View(context).apply { setBackgroundColor(Color.rgb(20, 26, 46)) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, g.dp(context, 1f)))
        content.addView(nowWrap, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        content.addView(profileKey.apply {
            gravity = Gravity.CENTER; minHeight = g.dp(context, 44f)
            background = GradientDrawable().apply { cornerRadius = g.dp(context, 22f).toFloat(); setStroke(g.dp(context, 1f), Color.rgb(29, 37, 64)) }
            setPadding(g.dp(context, 20f), g.dp(context, 10f), g.dp(context, 20f), g.dp(context, 10f)); tap { onSwitchProfile() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = g.dp(context, 10f) })
    }

    /** Burn-in shift: move the content (not this view) by up to ±[maxDp]. The black root stays put. */
    fun nudge(maxDp: Float = 14f) {
        val m = g.dp(context, maxDp)
        content.animate().translationX(Random.nextInt(-m, m + 1).toFloat()).translationY(Random.nextInt(-m * 2, m * 2 + 1).toFloat())
            .setDuration(2_000).setInterpolator(DecelerateInterpolator()).start()
    }

    fun refresh(cache: EntityCache?, cfg: NodeConfig?, profile: Profile?, forecast: WeatherForecast? = null) {
        val snap = AmbientData.snapshot(cache)
        weather.text = snap.weather ?: "Weather unavailable"
        timer.text = snap.timer?.let { "${it.name} · ${it.remaining}" } ?: ""
        timer.visibility = if (snap.timer == null) View.GONE else View.VISIBLE
        profileKey.text = (profile?.name ?: "Choose profile") + "  ·  switch profile"

        hourly.removeAllViews()
        val points = forecast?.hourly(5).orEmpty()
        hourly.visibility = if (points.isEmpty()) View.GONE else View.VISIBLE
        points.forEach { p ->
            // Five equal columns: always fits, never runs off the edge.
            hourly.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                addView(label(12f, faint).apply { text = WeatherForecastParser.shortTime(p.time); gravity = Gravity.CENTER })
                addView(label(14f, Color.rgb(195, 205, 234)).apply { text = p.temperature?.let { "${Math.round(it)}°" } ?: "—"; gravity = Gravity.CENTER })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        val p = snap.nowPlaying
        if (p == null) { nowWrap.visibility = View.GONE; return }
        nowWrap.visibility = View.VISIBLE
        nowTitle.text = p.title
        nowSub.text = p.appName ?: "Now playing"
        AlbumArtLoader.load(art, p.albumArt)
    }

    private fun label(size: Float, color: Int) = TextView(context).apply {
        textSize = size; setTextColor(color); typeface = g.body(context)
    }
}

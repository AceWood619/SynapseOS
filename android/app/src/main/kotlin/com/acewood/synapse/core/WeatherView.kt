package com.acewood.synapse.core

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.acewood.synapse.logic.EntityCache
import com.acewood.synapse.logic.WeatherForecast
import com.acewood.synapse.logic.WeatherForecastParser

@SuppressLint("ViewConstructor")
class WeatherView(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
    private val g = Glass
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val subtitle = TextView(context).apply { textSize = 12f; setTextColor(Glass.INK_DIM) }
    private var lastSig: String? = null

    init {
        background = g.ground()
        val pad = g.dp(context, 16f)
        val outer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, g.dp(context, 14f), pad, g.dp(context, 14f)) }
        addView(outer, LayoutParams(-1, -1))
        outer.addView(g.row(context).apply {
            addView(TextView(context).apply { text = "‹  BACK"; textSize = 12f; setTextColor(Glass.INK_DIM); typeface = g.disp(context); background = g.tile(context, Glass.INK_DIM, false, 14f); setPadding(g.dp(context, 16f), g.dp(context, 11f), g.dp(context, 16f), g.dp(context, 11f)); tap { onBack() } })
            addView(g.spacer(context, w = 14))
            addView(g.col(context).apply {
                addView(TextView(context).apply { text = "Weather"; textSize = 22f; setTextColor(Glass.INK); typeface = g.disp(context) })
                addView(subtitle)
            })
        })
        outer.addView(g.spacer(context, h = 12))
        outer.addView(ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun open() { lastSig = null; refresh(); request() }
    fun refresh() {
        if (visibility != View.VISIBLE) return
        post {
            val cache = HaRepository.cache ?: return@post
            val weather = cache.byDomain("weather").firstOrNull()
            val sig = weather?.state + weather?.attributes.toString() + HaRepository.hourlyForecast + HaRepository.dailyForecast
            if (sig == lastSig) return@post
            lastSig = sig
            body.removeAllViews()
            if (weather == null) { subtitle.text = "No weather entity found"; return@post }
            subtitle.text = weather.friendlyName
            addCurrent(weather)
            addForecast("HOURLY", HaRepository.hourlyForecast, 8)
            addForecast("DAILY", HaRepository.dailyForecast, 7)
            if (HaRepository.hourlyForecast == null || HaRepository.dailyForecast == null) body.addView(TextView(context).apply { text = "Forecast loading…"; setTextColor(Glass.INK_FAINT); setPadding(0, g.dp(context, 14f), 0, 0) })
        }
    }

    private fun request() {
        HaRepository.requestForecast("hourly")
        HaRepository.requestForecast("daily")
    }

    private fun addCurrent(e: com.acewood.synapse.logic.Entity) {
        val temp = e.attrDouble("temperature")?.let { "${Math.round(it)}°" } ?: "—"
        body.addView(g.col(context).apply {
            background = g.panel(context, 20f); setPadding(g.dp(context, 18f), g.dp(context, 16f), g.dp(context, 18f), g.dp(context, 16f))
            addView(TextView(context).apply { text = icon(e.state) + "  $temp"; textSize = 38f; setTextColor(Glass.INK); typeface = g.disp(context) })
            addView(TextView(context).apply { text = e.state.replace('-', ' ').replaceFirstChar { it.uppercase() }; textSize = 15f; setTextColor(Glass.AMBER) })
            addView(TextView(context).apply { text = listOfNotNull(e.attrDouble("humidity")?.let { "Humidity ${Math.round(it)}%" }, e.attrDouble("wind_speed")?.let { "Wind ${Math.round(it)}" }).joinToString("  ·  "); textSize = 12f; setTextColor(Glass.INK_DIM) })
        })
        body.addView(g.spacer(context, h = 14))
    }

    private fun addForecast(title: String, forecast: WeatherForecast?, limit: Int) {
        body.addView(g.label(context, title))
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        (forecast?.points?.take(limit) ?: emptyList()).forEach { p ->
            row.addView(g.col(context).apply {
                gravity = Gravity.CENTER; background = g.tile(context, Glass.BLUE, false, 14f); setPadding(g.dp(context, 10f), g.dp(context, 10f), g.dp(context, 10f), g.dp(context, 10f)); layoutParams = LinearLayout.LayoutParams(g.dp(context, 78f), g.dp(context, 76f)).apply { rightMargin = g.dp(context, 8f) }
                addView(TextView(context).apply { text = if (title == "HOURLY") WeatherForecastParser.shortTime(p.time) else p.time.take(10).takeLast(5); textSize = 10f; setTextColor(Glass.INK_DIM) })
                addView(TextView(context).apply { text = icon(p.condition); textSize = 18f; setTextColor(Glass.INK) })
                addView(TextView(context).apply { text = p.temperature?.let { "${Math.round(it)}°" } ?: "—"; textSize = 13f; setTextColor(Glass.AMBER) })
            })
        }
        body.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        body.addView(g.spacer(context, h = 14))
    }

    private fun icon(state: String): String = when (state.lowercase().replace(' ', '-')) {
        "sunny", "clear" -> "☀"; "clear-night" -> "☾"; "partlycloudy", "partly-cloudy" -> "⛅"; "cloudy" -> "☁"; "rainy", "pouring" -> "☂"; "snowy", "snowy-rainy" -> "❄"; "fog" -> "≋"; else -> "◌"
    }
}

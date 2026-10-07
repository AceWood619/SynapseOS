package com.acewood.synapse.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.acewood.synapse.logic.Entity
import com.acewood.synapse.logic.EntityCache
import kotlin.math.abs

/**
 * Every sensor in the house, live, in one Glass screen: this panel's own sensors first, then each
 * room (HA areas), then the rest of the house grouped by kind (network, presence, climate, power,
 * phones, car…). Binary sensors read as words ("Online", "Open", "Motion"), numbers are rounded.
 * Tap a group header to fold it. Rebuilds only when a shown value changes.
 */
@SuppressLint("ViewConstructor")
class SensorsView(context: Context, private val nodeId: String, private val onBack: () -> Unit) : FrameLayout(context) {
    private val g = Glass
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val subtitle = TextView(context).apply { textSize = 12f; setTextColor(Glass.INK_DIM); typeface = g.body(context) }
    private val folded = mutableSetOf("Car", "Phones", "Updates & system", "Other")
    private var lastSig: String? = null

    init {
        background = g.ground()
        val pad = g.dp(context, 16f)
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, g.dp(context, 14f), pad, g.dp(context, 14f))
        }
        addView(outer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        outer.addView(g.row(context).apply {
            addView(backKey())
            addView(g.spacer(context, w = 14))
            addView(g.col(context).apply {
                addView(TextView(context).apply { text = "Sensors"; textSize = 22f; setTextColor(Glass.INK); typeface = g.disp(context) })
                addView(subtitle)
            })
        })
        outer.addView(g.spacer(context, h = 12))
        outer.addView(ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(body) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun backKey(): View = TextView(context).apply {
        text = "‹  BACK"; textSize = 12f; setTextColor(Glass.INK_DIM); typeface = g.disp(context); letterSpacing = 0.08f
        background = g.tile(context, Glass.INK_DIM, false, 14f)
        setPadding(g.dp(context, 16f), g.dp(context, 11f), g.dp(context, 16f), g.dp(context, 11f))
        tap { onBack() }
    }

    fun open() { lastSig = null; refresh() }

    fun refresh() {
        if (visibility != View.VISIBLE) return
        post {
            val cache = HaRepository.cache ?: return@post
            val groups = group(cache)
            val sig = groups.joinToString("#") { (n, l) -> n + l.joinToString(",") { it.entityId + "=" + it.state } } + folded
            if (sig == lastSig) return@post
            lastSig = sig
            val total = groups.sumOf { it.second.size }
            subtitle.text = "$total live sensors · ${groups.size} groups"
            body.removeAllViews()
            groups.forEach { (name, list) -> addGroup(name, list) }
            if (groups.isEmpty()) body.addView(TextView(context).apply {
                text = if (HaRepository.connected) "No sensors found" else "Home Assistant offline"; setTextColor(Glass.INK_FAINT)
            })
        }
    }

    // ---------- grouping ----------

    private fun usable(e: Entity): Boolean {
        if (e.domain != "sensor" && e.domain != "binary_sensor") return false
        if (e.state == "unavailable" || e.state == "unknown" || e.state.isBlank()) return false
        val id = e.entityId
        return !(id.contains("_supports_") || id.endsWith("_last_reset") || id.contains("_firmware"))
    }

    private fun group(cache: EntityCache): List<Pair<String, List<Entity>>> {
        val all = cache.all().filter { usable(it) }
        val used = HashSet<String>()
        val out = ArrayList<Pair<String, List<Entity>>>()
        val prefix = nodeId.replace('-', '_')
        val panel = all.filter { it.entityId.contains("synapse_$prefix") }
        if (panel.isNotEmpty()) { out += "This panel" to panel; used += panel.map { it.entityId } }
        for (r in HaRepository.rooms) {
            val list = r.sensors.mapNotNull { cache.get(it) }.filter { usable(it) && it.entityId !in used }
            if (list.isNotEmpty()) { out += r.name to list; used += list.map { it.entityId } }
        }
        val rest = all.filter { it.entityId !in used }.groupBy { kind(it) }
        val kindOrder = listOf("Network", "Presence", "Climate", "Power & energy", "Security", "Phones", "Car", "Updates & system", "Other")
        kindOrder.forEach { k -> rest[k]?.let { out += k to it.sortedBy { e -> e.friendlyName } } }
        return out
    }

    private fun kind(e: Entity): String {
        val id = e.entityId.lowercase(); val dc = (e.attributes["device_class"] as? String)?.lowercase() ?: ""
        val unit = e.attributes["unit_of_measurement"] as? String ?: ""
        return when {
            id.contains("terrain") || id.contains("gmc") || id.contains("_car_") -> "Car"
            id.contains("iphone") || id.contains("_phone") || id.contains("pixel") -> "Phones"
            dc == "connectivity" || id.contains("router") || id.contains("wan") || id.contains("internet") || id.contains("wifi") ||
                id.contains("speedtest") || unit == "Mbit/s" || unit == "ms" -> "Network"
            dc in setOf("presence", "occupancy", "motion") || id.contains("home") || id.contains("presence") -> "Presence"
            dc in setOf("temperature", "humidity", "pressure", "illuminance", "atmospheric_pressure") || unit in setOf("°F", "°C", "%RH") -> "Climate"
            dc in setOf("power", "energy", "battery", "battery_charging", "voltage", "current", "plug") || unit in setOf("W", "kWh", "V", "A") -> "Power & energy"
            dc in setOf("door", "window", "opening", "lock", "safety", "smoke", "gas", "moisture", "tamper", "problem", "sound") -> "Security"
            id.contains("update") || id.contains("version") || id.contains("cpu") || id.contains("memory") || id.contains("disk") ||
                id.contains("uptime") || id.contains("_load") -> "Updates & system"
            else -> "Other"
        }
    }

    // ---------- rendering ----------

    private fun addGroup(name: String, list: List<Entity>) {
        val isFolded = name in folded
        body.addView(g.row(context).apply {
            setPadding(0, g.dp(context, 6f), 0, g.dp(context, 6f))
            addView(g.label(context, name.uppercase()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = "${list.size}  ${if (isFolded) "▸" else "▾"}"; textSize = 12f; setTextColor(Glass.INK_FAINT); typeface = g.disp(context)
            })
            tap { if (!folded.remove(name)) folded.add(name); lastSig = null; refresh() }
        })
        if (isFolded) { body.addView(g.spacer(context, h = 6)); return }
        body.addView(g.col(context).apply {
            background = g.panel(context, 16f)
            setPadding(g.dp(context, 14f), g.dp(context, 10f), g.dp(context, 14f), g.dp(context, 10f))
            list.forEach { e -> addView(sensorRow(e)) }
        })
        body.addView(g.spacer(context, h = 10))
    }

    private fun sensorRow(e: Entity): View = g.row(context).apply {
        setPadding(0, g.dp(context, 5f), 0, g.dp(context, 5f))
        val (value, accent) = pretty(e)
        addView(TextView(context).apply {
            text = shortName(e); textSize = 13f; setTextColor(Glass.INK_DIM); typeface = g.body(context); maxLines = 2
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = value; textSize = 13.5f; setTextColor(accent); typeface = g.disp(context); gravity = Gravity.END
            setPadding(g.dp(context, 10f), 0, 0, 0)
        })
    }

    private fun shortName(e: Entity): String {
        var n = e.friendlyName.trim()
        // "Synapse livingroom-01 Battery" -> "Battery"; "Living Room 50 onn Roku TV Active app" stays readable.
        n = n.replace(Regex("(?i)^synapse\\s+\\S+\\s+"), "")
        return n.ifBlank { e.entityId }
    }

    /** Human words for binary sensors, rounded numbers with units for the rest. */
    private fun pretty(e: Entity): Pair<String, Int> {
        val dc = (e.attributes["device_class"] as? String) ?: ""
        if (e.domain == "binary_sensor") {
            val on = e.state == "on"
            val words = when (dc) {
                "connectivity" -> "Online" to "Offline"
                "presence" -> "Home" to "Away"
                "occupancy" -> "Occupied" to "Clear"
                "motion" -> "Motion" to "Clear"
                "door", "window", "opening", "garage_door" -> "Open" to "Closed"
                "lock" -> "Unlocked" to "Locked"
                "battery_charging" -> "Charging" to "Not charging"
                "problem" -> "Problem" to "OK"
                "power", "plug", "running" -> "On" to "Off"
                "sound" -> "Sound" to "Quiet"
                "moisture" -> "Wet" to "Dry"
                "smoke", "gas", "safety" -> "ALERT" to "OK"
                else -> "On" to "Off"
            }
            val alert = dc in setOf("problem", "smoke", "gas", "safety", "moisture") && on ||
                dc == "connectivity" && !on || dc == "lock" && on
            return (if (on) words.first else words.second) to when {
                alert -> Glass.RED; on -> Glass.MINT; else -> Glass.INK_FAINT
            }
        }
        val unit = (e.attributes["unit_of_measurement"] as? String)?.let { " $it" } ?: ""
        val num = e.state.toDoubleOrNull()
        val text = if (num != null) {
            val r = if (abs(num) >= 100 || num == Math.floor(num)) "%.0f".format(num) else "%.1f".format(num)
            r + unit
        } else e.state.replace('_', ' ').replaceFirstChar { it.uppercase() } + unit
        val accent = when (dc) {
            "temperature" -> Glass.AMBER; "humidity" -> Glass.BLUE; "battery" -> if ((num ?: 100.0) < 20) Glass.RED else Glass.MINT
            "power", "energy" -> Glass.VIOLET; else -> Glass.INK
        }
        return text.take(28) to accent
    }
}

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

/** Admin diagnostics for explaining why a Home Assistant entity is or is not on a room pad. */
@SuppressLint("ViewConstructor")
class DiagnosticsView(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
    private val g = Glass
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val subtitle = TextView(context).apply { textSize = 12f; setTextColor(Glass.INK_DIM) }
    private val folded = mutableSetOf("Unmapped", "Sensors", "Unavailable")
    private var lastSig: String? = null

    init {
        background = g.ground()
        val pad = g.dp(context, 16f)
        val outer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, g.dp(context, 14f), pad, g.dp(context, 14f)) }
        addView(outer, LayoutParams(-1, -1))
        outer.addView(g.row(context).apply {
            addView(TextView(context).apply {
                text = "‹  BACK"; textSize = 12f; setTextColor(Glass.INK_DIM); typeface = g.disp(context); letterSpacing = .08f
                background = g.tile(context, Glass.INK_DIM, false, 14f); setPadding(g.dp(context, 16f), g.dp(context, 11f), g.dp(context, 16f), g.dp(context, 11f)); tap { onBack() }
            })
            addView(g.spacer(context, w = 14))
            addView(g.col(context).apply {
                addView(TextView(context).apply { text = "Room diagnostics"; textSize = 22f; setTextColor(Glass.INK); typeface = g.disp(context) })
                addView(subtitle)
            })
        })
        outer.addView(g.spacer(context, h = 12))
        outer.addView(ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun open() { lastSig = null; refresh() }

    fun refresh() {
        if (visibility != View.VISIBLE) return
        post {
            val cache = HaRepository.cache
            if (cache == null) { subtitle.text = "Home Assistant offline"; body.removeAllViews(); return@post }
            val groups = classify(cache.all())
            val sig = groups.entries.joinToString("#") { (k, v) -> k + v.joinToString(",") { it.entityId + "=" + it.state } } + folded
            if (sig == lastSig) return@post
            lastSig = sig
            subtitle.text = "${cache.size()} entities · ${HaRepository.rooms.size} rooms · tap a group to expand"
            body.removeAllViews()
            groups.forEach { (name, entities) -> addGroup(name, entities) }
        }
    }

    private fun classify(all: List<Entity>): LinkedHashMap<String, List<Entity>> {
        val roomByEntity = linkedMapOf<String, String>()
        HaRepository.rooms.forEach { room ->
            (room.lights + room.switches + room.media + room.extras + room.remotes + room.fans + room.covers + room.climate + room.locks + room.sensors)
                .forEach { roomByEntity.putIfAbsent(it, room.name) }
        }
        val unavailable = all.filter { it.state == "unavailable" || it.state == "unknown" }
        val unmapped = all.filter { it.entityId !in roomByEntity && it !in unavailable }
        val foldedEntities = all.filter { it.entityId in roomByEntity && isHelper(it) && it !in unavailable }
        val visible = all.filter { it.entityId in roomByEntity && !isHelper(it) && it !in unavailable }
        return linkedMapOf("Visible room controls" to visible, "Folded helpers" to foldedEntities, "Unmapped" to unmapped, "Unavailable" to unavailable,
            "Sensors" to all.filter { it.domain == "sensor" || it.domain == "binary_sensor" })
    }

    private fun isHelper(e: Entity): Boolean {
        val text = (e.entityId + " " + e.friendlyName).lowercase()
        return e.domain == "switch" && listOf("alert", "ding", "motion", "ring", "microphone", "jarvis", "reply", "mute", "listening").any { it in text }
    }

    private fun addGroup(name: String, entities: List<Entity>) {
        val sorted = entities.sortedWith(compareBy<Entity> { it.domain }.thenBy { it.friendlyName.lowercase() })
        val isFolded = name in folded
        body.addView(g.row(context).apply {
            setPadding(0, g.dp(context, 6f), 0, g.dp(context, 6f))
            addView(g.label(context, name.uppercase()), LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(context).apply { text = "${sorted.size}  ${if (isFolded) "▸" else "▾"}"; textSize = 12f; setTextColor(Glass.INK_FAINT); typeface = g.disp(context) })
            tap { if (!folded.remove(name)) folded.add(name); lastSig = null; refresh() }
        })
        if (isFolded) return
        if (sorted.isEmpty()) {
            body.addView(TextView(context).apply { text = "None"; textSize = 12f; setTextColor(Glass.INK_FAINT); setPadding(g.dp(context, 14f), g.dp(context, 8f), 0, g.dp(context, 8f)) })
            return
        }
        body.addView(g.col(context).apply {
            background = g.panel(context, 16f); setPadding(g.dp(context, 12f), g.dp(context, 8f), g.dp(context, 12f), g.dp(context, 8f))
            sorted.forEach { addView(entityRow(it)) }
        })
        body.addView(g.spacer(context, h = 10))
    }

    private fun entityRow(e: Entity): View = g.col(context).apply {
        setPadding(0, g.dp(context, 6f), 0, g.dp(context, 6f))
        addView(g.row(context).apply {
            addView(TextView(context).apply { text = e.friendlyName; textSize = 13f; setTextColor(Glass.INK); typeface = g.body(context); maxLines = 2 }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(context).apply { text = e.state.replace('_', ' '); textSize = 12f; setTextColor(if (e.state == "unavailable" || e.state == "unknown") Glass.RED else Glass.MINT); gravity = Gravity.END })
        })
        addView(TextView(context).apply { text = e.entityId; textSize = 10.5f; setTextColor(Glass.INK_FAINT); typeface = Typeface.MONOSPACE })
    }
}

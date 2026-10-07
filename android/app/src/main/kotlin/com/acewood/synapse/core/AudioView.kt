package com.acewood.synapse.core

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.acewood.synapse.logic.Entity

class AudioView(context: Context, private val onBack: () -> Unit, private val onMusic: () -> Unit) : FrameLayout(context) {
    private val g = Glass
    private val body = g.col(context)

    init {
        background = g.ground()
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(g.dp(context, 16f), g.dp(context, 14f), g.dp(context, 16f), g.dp(context, 14f))
        }
        addView(outer, LayoutParams(-1, -1))
        outer.addView(g.row(context).apply {
            addView(key("‹  BACK", Glass.INK_DIM) { onBack() })
            addView(g.spacer(context, w = 14))
            addView(g.col(context).apply {
                addView(TextView(context).apply { text = "Audio"; textSize = 22f; setTextColor(Glass.INK); typeface = g.disp(context) })
                addView(TextView(context).apply { text = "Speakers, volume, mute, and output"; textSize = 12f; setTextColor(Glass.INK_DIM) })
            })
        })
        outer.addView(g.spacer(context, h = 10))
        outer.addView(key("OPEN HA MUSIC PLAYER", Glass.BLUE) { onMusic() })
        outer.addView(g.spacer(context, h = 10))
        outer.addView(android.widget.ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun open() { render() }

    private fun render() {
        body.removeAllViews()
        val players = HaRepository.cache?.byDomain("media_player").orEmpty()
        if (players.isEmpty()) {
            body.addView(TextView(context).apply { text = "No media players are available from Home Assistant."; textSize = 14f; setTextColor(Glass.INK_DIM) })
            return
        }
        players.forEach { entity -> body.addView(playerCard(entity)); body.addView(g.spacer(context, h = 9)) }
    }

    private fun playerCard(e: Entity): View = g.col(context).apply {
        background = g.panel(context, 17f); setPadding(g.dp(context, 14f), g.dp(context, 12f), g.dp(context, 14f), g.dp(context, 12f))
        addView(g.row(context).apply {
            addView(g.col(context).apply {
                addView(TextView(context).apply { text = e.friendlyName; textSize = 15f; setTextColor(Glass.INK); typeface = g.body(context) })
                addView(TextView(context).apply { text = e.state; textSize = 11f; setTextColor(if (e.state == "playing") Glass.MINT else Glass.INK_DIM) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(button("⏯", Glass.MINT) { service("media_play_pause", e) })
            addView(g.spacer(context, w = 6)); addView(button("MUTE", Glass.INK_DIM) { service("volume_mute", e, mapOf("is_volume_muted" to (e.attributes["is_volume_muted"] != true))) })
        })
        val volume = e.attrDouble("volume_level")?.let { (it * 100).toInt().coerceIn(0, 100) }
        if (volume != null) {
            addView(g.spacer(context, h = 8))
            addView(g.row(context).apply {
                addView(button("−", Glass.BLUE) { service("volume_down", e) })
                addView(SeekBar(context).apply {
                    max = 100; progress = volume; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {}
                        override fun onStartTrackingTouch(bar: SeekBar) {}
                        override fun onStopTrackingTouch(bar: SeekBar) { service("volume_set", e, mapOf("volume_level" to bar.progress / 100.0)) }
                    })
                })
                addView(button("+", Glass.MINT) { service("volume_up", e) })
            })
        }
        @Suppress("UNCHECKED_CAST")
        val sources = ((e.attributes["source_list"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList())
            .filter { it.isNotBlank() }.distinct()
        if (sources.isNotEmpty()) {
            addView(g.spacer(context, h = 8)); addView(TextView(context).apply { text = "OUTPUT  ·  ${e.attributes["source"] ?: "default"}"; textSize = 10f; setTextColor(Glass.INK_FAINT) })
            addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    sources.forEach { source -> addView(button(source, if (source == e.attributes["source"]) Glass.BLUE else Glass.INK_DIM) { service("select_source", e, mapOf("source" to source)) }); addView(g.spacer(context, w = 6)) }
                })
            })
        }
    }

    private fun service(name: String, entity: Entity, data: Map<String, Any?> = emptyMap()) {
        HaRepository.callService("media_player", name, listOf(entity.entityId), data)
        postDelayed({ render() }, 350)
    }
    private fun button(label: String, color: Int, action: () -> Unit): View = TextView(context).apply {
        text = label; textSize = 11f; setTextColor(if (color == Glass.RED) Color.WHITE else color); gravity = Gravity.CENTER
        background = g.tile(context, color, false, 11f); setPadding(g.dp(context, 10f), g.dp(context, 9f), g.dp(context, 10f), g.dp(context, 9f)); tap { action() }
    }
    private fun key(label: String, color: Int, action: () -> Unit): View = button(label, color, action)
}

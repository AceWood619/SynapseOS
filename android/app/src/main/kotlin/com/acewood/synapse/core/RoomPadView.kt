package com.acewood.synapse.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.acewood.synapse.logic.MediaRemote
import com.acewood.synapse.logic.Room
import com.acewood.synapse.logic.RoomControl

/**
 * The room "channel" detail — a Glass overlay that opens when you tap a room on the home screen.
 * Feels like a remote aimed at one room: light keys (tap = toggle, −/+ = dim), a media strip with
 * transport + a Roku D-pad, and extra toggles. Live from HaRepository; rebuilds on each HA change.
 */
@SuppressLint("ViewConstructor")
class RoomPadView(
    context: Context,
    private val onBack: () -> Unit,
) : FrameLayout(context) {

    private val g = Glass
    private var room: Room? = null
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val title = tv(22f, Glass.INK, g.disp(context))
    private val subtitle = tv(12f, Glass.INK_DIM, g.body(context))

    init {
        background = g.ground()
        val pad = g.dp(context, 16f)
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, g.dp(context, 14f), pad, g.dp(context, 14f))
        }
        addView(outer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // header: back + room name
        outer.addView(g.row(context).apply {
            addView(key("‹  BACK", Glass.INK_DIM, wide = true) { onBack() })
            addView(g.spacer(context, w = 14))
            addView(g.col(context).apply { addView(title); addView(subtitle) })
        })
        outer.addView(g.spacer(context, h = 12))
        outer.addView(ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(body)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun tv(size: Float, color: Int, tf: Typeface) = TextView(context).apply {
        textSize = size; setTextColor(color); typeface = tf
    }

    private fun haptic() = try { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } catch (_: Exception) {}

    fun open(r: Room) { room = r; refresh() }

    fun refresh() {
        post {
            val r = room ?: return@post
            val cache = HaRepository.cache ?: return@post
            val m = RoomControl.build(r, cache)
            title.text = r.name
            subtitle.text = buildString {
                append("${m.lightCount} ${if (m.lightCount == 1) "light" else "lights"}")
                if (m.media.isNotEmpty()) append(" · ${m.media.size} media")
                if (m.lightsAmbiguous) append(" · ⚠ map lights")
            }
            body.removeAllViews()
            if (m.lights.isNotEmpty()) { body.addView(g.label(context, "LIGHTS")); body.addView(g.spacer(context, h = 8)) }
            m.lights.forEach { body.addView(lightKey(it)); body.addView(g.spacer(context, h = 9)) }
            m.media.forEach { body.addView(g.spacer(context, h = 4)); body.addView(mediaStrip(it)); body.addView(g.spacer(context, h = 9)) }
            if (m.extras.isNotEmpty()) {
                body.addView(g.spacer(context, h = 4)); body.addView(g.label(context, "MORE")); body.addView(g.spacer(context, h = 8))
                m.extras.forEach { body.addView(toggleChip(it)); body.addView(g.spacer(context, h = 8)) }
            }
            if (m.lights.isEmpty() && m.media.isEmpty() && m.extras.isEmpty())
                body.addView(tv(13f, Glass.INK_FAINT, g.body(context)).apply { text = "No controls in this room yet" })
        }
    }

    // ---- light row: name + dim −/+ + big toggle ----
    private fun lightKey(t: RoomControl.LightTile): View = g.row(context).apply {
        background = g.tile(context, Glass.AMBER, t.isOn, 16f)
        setPadding(g.dp(context, 15f), g.dp(context, 13f), g.dp(context, 13f), g.dp(context, 13f))
        val targets = { HaRepository.cache?.let { t.light.commandTargets(it, both = false) } ?: t.light.entityIds }
        addView(g.col(context).apply {
            addView(tv(15f, Glass.INK, g.body(context)).apply { text = t.light.name; setTypeface(typeface, Typeface.BOLD) })
            addView(tv(11f, if (t.isOn) Glass.AMBER else Glass.INK_FAINT, g.body(context)).apply {
                text = when {
                    !t.available -> "unavailable"
                    t.isOn && t.brightnessPct != null -> "on · ${t.brightnessPct}%"
                    t.isOn -> "on"
                    else -> "off"
                }
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (t.dimmable && t.isOn) {
            addView(roundKey("−") { step(targets(), -15) })
            addView(g.spacer(context, w = 7))
            addView(roundKey("+") { step(targets(), +15) })
            addView(g.spacer(context, w = 10))
        }
        addView(roundKey(if (t.isOn) "⏻" else "○", accent = if (t.isOn) Glass.AMBER else Glass.INK_DIM) {
            HaRepository.toggle(targets(), !t.isOn); refresh()
        })
    }

    private fun step(targets: List<String>, deltaPct: Int) {
        if (targets.isEmpty()) return
        HaRepository.callService("light", "turn_on", targets, mapOf("brightness_step_pct" to deltaPct))
        postDelayed({ refresh() }, 300)
    }

    // ---- media strip: now-playing + transport + optional D-pad ----
    private fun mediaStrip(t: RoomControl.MediaTile): View = g.col(context).apply {
        background = g.panel(context, 18f)
        setPadding(g.dp(context, 15f), g.dp(context, 14f), g.dp(context, 15f), g.dp(context, 14f))
        addView(tv(14f, Glass.INK, g.body(context)).apply { text = t.title; setTypeface(typeface, Typeface.BOLD); maxLines = 1 })
        addView(tv(11f, if (t.isPlaying) Glass.MINT else Glass.INK_FAINT, g.body(context)).apply {
            text = t.state + (t.volumePct?.let { "  ·  vol $it%" } ?: "")
        })
        addView(g.spacer(context, h = 10))
        // transport
        if (t.canTransport) addView(g.row(context).apply {
            gravity = Gravity.CENTER
            addView(roundKey("⏮") { send(MediaRemote.previous(t.entityId)) }); addView(g.spacer(context, w = 10))
            addView(roundKey(if (t.isPlaying) "⏸" else "▶", accent = Glass.MINT) { send(MediaRemote.playPause(t.entityId)) }); addView(g.spacer(context, w = 10))
            addView(roundKey("⏭") { send(MediaRemote.next(t.entityId)) }); addView(g.spacer(context, w = 16))
            addView(roundKey("vol −") { send(MediaRemote.volumeDown(t.entityId)) }); addView(g.spacer(context, w = 7))
            addView(roundKey("vol +") { send(MediaRemote.volumeUp(t.entityId)) })
        })
        if (t.showDpad) { addView(g.spacer(context, h = 12)); addView(dpad(t)) }
    }

    // ---- Roku D-pad: ▲ / ◀ OK ▶ / ▼ with Back·Home·Info·Replay ----
    private fun dpad(t: RoomControl.MediaTile): View = g.col(context).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        fun b(btn: MediaRemote.Button) = { val c = MediaRemote.press(btn, t.remoteEntityId, t.entityId); if (c != null) send(c) }
        addView(dKey("▲", b(MediaRemote.Button.UP)))
        addView(g.row(context).apply {
            gravity = Gravity.CENTER
            addView(dKey("◀", b(MediaRemote.Button.LEFT))); addView(g.spacer(context, w = 8))
            addView(dKey("OK", b(MediaRemote.Button.OK), accent = Glass.BLUE)); addView(g.spacer(context, w = 8))
            addView(dKey("▶", b(MediaRemote.Button.RIGHT)))
        })
        addView(dKey("▼", b(MediaRemote.Button.DOWN)))
        addView(g.spacer(context, h = 10))
        addView(g.row(context).apply {
            gravity = Gravity.CENTER
            addView(roundKey("↩") { b(MediaRemote.Button.BACK)() }); addView(g.spacer(context, w = 8))
            addView(roundKey("⌂") { b(MediaRemote.Button.HOME)() }); addView(g.spacer(context, w = 8))
            addView(roundKey("↺") { b(MediaRemote.Button.REPLAY)() }); addView(g.spacer(context, w = 8))
            addView(roundKey("ⓘ") { b(MediaRemote.Button.INFO)() })
        })
    }

    private fun send(c: com.acewood.synapse.logic.HaCall) {
        HaRepository.callService(c.domain, c.service, listOf(c.entityId), c.data)
        postDelayed({ refresh() }, 300)
    }

    private fun toggleChip(t: RoomControl.ToggleTile): View = g.row(context).apply {
        background = g.tile(context, Glass.VIOLET, t.isOn, 14f)
        setPadding(g.dp(context, 14f), g.dp(context, 11f), g.dp(context, 12f), g.dp(context, 11f))
        addView(tv(13f, Glass.INK, g.body(context)).apply { text = t.name },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(roundKey(if (t.isOn) "⏻" else "○", accent = if (t.isOn) Glass.VIOLET else Glass.INK_DIM) {
            val domain = t.entityId.substringBefore('.')
            HaRepository.callService(domain, if (t.isOn) "turn_off" else "turn_on", listOf(t.entityId)); refresh()
        })
    }

    // small round-ish key
    private fun roundKey(label: String, accent: Int = Glass.INK_DIM, onTap: () -> Unit): View =
        tv(13f, accent, g.disp(context)).apply {
            text = label; gravity = Gravity.CENTER
            background = g.tile(context, accent, false, 13f)
            val h = g.dp(context, 40f)
            setPadding(g.dp(context, 13f), 0, g.dp(context, 13f), 0)
            minHeight = h; minimumHeight = h
            setOnClickListener { haptic(); onTap() }
        }

    // square D-pad key
    private fun dKey(label: String, onTap: () -> Unit, accent: Int = Glass.INK): View =
        tv(16f, accent, g.disp(context)).apply {
            text = label; gravity = Gravity.CENTER
            background = g.tile(context, if (accent == Glass.BLUE) Glass.BLUE else Glass.INK_DIM, accent == Glass.BLUE, 14f)
            val s = g.dp(context, 52f); layoutParams = LinearLayout.LayoutParams(s, s)
            setOnClickListener { haptic(); onTap() }
        }

    private fun key(text: String, color: Int, wide: Boolean = false, onTap: () -> Unit): View =
        tv(12f, color, g.disp(context)).apply {
            setText(text); gravity = Gravity.CENTER; letterSpacing = 0.08f
            background = g.tile(context, color, false, 14f)
            setPadding(g.dp(context, if (wide) 16f else 12f), g.dp(context, 11f), g.dp(context, if (wide) 16f else 12f), g.dp(context, 11f))
            setOnClickListener { haptic(); onTap() }
        }
}

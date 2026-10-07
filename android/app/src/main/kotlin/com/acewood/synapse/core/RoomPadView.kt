package com.acewood.synapse.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.acewood.synapse.logic.MediaRemote
import com.acewood.synapse.logic.Room
import com.acewood.synapse.logic.RoomControl
import kotlin.math.roundToInt

/**
 * The room "channel" detail — a Glass overlay opened by tapping a room on the home screen.
 * A remote aimed at one room: light keys with a brightness slider + auto color controls, a media
 * strip with a volume slider and a big Roku D-pad, plus fans, covers, climate, locks, switches and
 * read-only sensors. Live from HaRepository; rebuilds on each HA change (sliders are left alone
 * while you're dragging them).
 */
@SuppressLint("ViewConstructor")
class RoomPadView(
    context: Context,
    private val onBack: () -> Unit,
) : FrameLayout(context) {

    private val g = Glass
    private var room: Room? = null
    private var dragging = false   // don't rebuild under a finger on a slider
    private val bodyCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val title = tv(22f, Glass.INK, g.disp(context))
    private val subtitle = tv(12f, Glass.INK_DIM, g.body(context))

    // a small, friendly colour palette for colour-capable lights
    private val swatches = listOf(
        "Warm" to intArrayOf(255, 214, 170), "Day" to intArrayOf(255, 255, 255),
        "Red" to intArrayOf(255, 70, 70), "Orange" to intArrayOf(255, 150, 40),
        "Gold" to intArrayOf(255, 210, 60), "Green" to intArrayOf(90, 220, 120),
        "Cyan" to intArrayOf(60, 220, 220), "Blue" to intArrayOf(70, 140, 255),
        "Violet" to intArrayOf(160, 90, 255), "Pink" to intArrayOf(255, 90, 180),
    )

    init {
        background = g.ground()
        val pad = g.dp(context, 16f)
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, g.dp(context, 14f), pad, g.dp(context, 14f))
        }
        addView(outer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        outer.addView(g.row(context).apply {
            addView(key("‹  BACK", Glass.INK_DIM) { onBack() })
            addView(g.spacer(context, w = 14))
            addView(g.col(context).apply { addView(title); addView(subtitle) })
        })
        outer.addView(g.spacer(context, h = 12))
        outer.addView(ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(bodyCol) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun tv(size: Float, color: Int, tf: Typeface) = TextView(context).apply {
        textSize = size; setTextColor(color); typeface = tf
    }
    private fun haptic() = try { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } catch (_: Exception) {}
    private fun cacheTargets(t: RoomControl.LightTile) =
        HaRepository.cache?.let { t.light.commandTargets(it, both = false) } ?: t.light.entityIds

    fun open(r: Room) { room = r; refresh() }

    fun refresh() {
        if (dragging) return
        post {
            val r = room ?: return@post
            val cache = HaRepository.cache ?: return@post
            val m = RoomControl.build(r, cache)
            title.text = r.name
            subtitle.text = buildString {
                val parts = mutableListOf<String>()
                if (m.lightCount > 0) parts += "${m.lightCount} ${if (m.lightCount == 1) "light" else "lights"}"
                if (m.media.isNotEmpty()) parts += "${m.media.size} media"
                if (m.climate.isNotEmpty()) parts += "${m.climate.size} climate"
                if (m.covers.isNotEmpty()) parts += "${m.covers.size} cover"
                append(parts.joinToString(" · "))
                if (m.lightsAmbiguous) append("  ⚠ map lights")
            }
            bodyCol.removeAllViews()
            section("LIGHTS", m.lights.map { lightCard(it) })
            m.media.forEach { addCard(mediaStrip(it)) }
            section("CLIMATE", m.climate.map { climateCard(it) })
            section("FANS", m.fans.map { fanCard(it) })
            section("COVERS", m.covers.map { coverCard(it) })
            section("LOCKS", m.locks.map { lockCard(it) })
            section("SWITCHES", m.extras.map { toggleCard(it) })
            if (m.sensors.isNotEmpty()) { sectionLabel("SENSORS"); addCard(sensorWrap(m.sensors)) }
            if (bodyCol.childCount == 0)
                bodyCol.addView(tv(13f, Glass.INK_FAINT, g.body(context)).apply { text = "No controls in this room yet" })
        }
    }

    private fun sectionLabel(t: String) { bodyCol.addView(g.label(context, t)); bodyCol.addView(g.spacer(context, h = 8)) }
    private fun section(labelText: String, cards: List<View>) {
        if (cards.isEmpty()) return
        sectionLabel(labelText)
        cards.forEach { addCard(it) }
    }
    private fun addCard(v: View) { bodyCol.addView(v); bodyCol.addView(g.spacer(context, h = 9)) }

    // ---------- LIGHT ----------
    private fun lightCard(t: RoomControl.LightTile): View = g.col(context).apply {
        background = g.tile(context, Glass.AMBER, t.isOn, 16f)
        setPadding(g.dp(context, 15f), g.dp(context, 13f), g.dp(context, 14f), g.dp(context, 14f))
        addView(g.row(context).apply {
            addView(g.col(context).apply {
                addView(tv(15f, Glass.INK, g.body(context)).apply { text = t.light.name; setTypeface(typeface, Typeface.BOLD) })
                addView(tv(11f, if (t.isOn) Glass.AMBER else Glass.INK_FAINT, g.body(context)).apply {
                    text = when {
                        !t.available -> "unavailable"
                        t.isOn && t.brightnessPct != null -> "on · ${t.brightnessPct}%"
                        t.isOn -> "on"; else -> "off"
                    }
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(toggleKey(t.isOn, Glass.AMBER) { HaRepository.toggle(cacheTargets(t), !t.isOn); refresh() })
        })
        if (t.isOn && t.dimmable) {
            addView(g.spacer(context, h = 10))
            addView(slider(t.brightnessPct ?: 50, 100, Glass.AMBER) { pct ->
                HaRepository.callService("light", "turn_on", cacheTargets(t), mapOf("brightness_pct" to pct))
            })
        }
        if (t.isOn && t.colorCapable) {
            addView(g.spacer(context, h = 10)); addView(swatchRow(t))
        }
        if (t.isOn && t.colorTempCapable) {
            addView(g.spacer(context, h = 8))
            addView(tv(10f, Glass.INK_FAINT, g.body(context)).apply { text = "WARM  ·—————·  COOL"; letterSpacing = 0.05f })
            addView(slider(50, 100, Glass.BLUE) { pct ->
                val kelvin = (2200 + (pct / 100.0) * (6500 - 2200)).roundToInt()
                HaRepository.callService("light", "turn_on", cacheTargets(t), mapOf("color_temp_kelvin" to kelvin))
            })
        }
    }

    private fun swatchRow(t: RoomControl.LightTile): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        swatches.forEach { (_, rgb) ->
            val c = Color.rgb(rgb[0], rgb[1], rgb[2])
            addView(View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c); setStroke(g.dp(context, 1f), Glass.STROKE) }
                val s = g.dp(context, 30f)
                layoutParams = LinearLayout.LayoutParams(s, s).apply { rightMargin = g.dp(context, 8f) }
                setOnClickListener {
                    haptic(); HaRepository.callService("light", "turn_on", cacheTargets(t), mapOf("rgb_color" to listOf(rgb[0], rgb[1], rgb[2])))
                }
            })
        }
    }

    // ---------- MEDIA ----------
    private fun mediaStrip(t: RoomControl.MediaTile): View = g.col(context).apply {
        background = g.panel(context, 18f)
        setPadding(g.dp(context, 15f), g.dp(context, 14f), g.dp(context, 15f), g.dp(context, 14f))
        addView(tv(14f, Glass.INK, g.body(context)).apply { text = t.title; setTypeface(typeface, Typeface.BOLD); maxLines = 1 })
        addView(tv(11f, if (t.isPlaying) Glass.MINT else Glass.INK_FAINT, g.body(context)).apply { text = t.state })
        if (t.canTransport) {
            addView(g.spacer(context, h = 12))
            addView(g.row(context).apply {
                gravity = Gravity.CENTER
                addView(roundKey("⏮") { send(MediaRemote.previous(t.entityId)) }); addView(g.spacer(context, w = 12))
                addView(roundKey(if (t.isPlaying) "⏸" else "▶", Glass.MINT) { send(MediaRemote.playPause(t.entityId)) }); addView(g.spacer(context, w = 12))
                addView(roundKey("⏭") { send(MediaRemote.next(t.entityId)) })
            })
        }
        val vol = t.volumePct
        if (vol != null) {
            addView(g.spacer(context, h = 10))
            addView(g.row(context).apply {
                addView(tv(11f, Glass.INK_FAINT, g.disp(context)).apply { text = "VOL" })
                addView(g.spacer(context, w = 10))
                addView(slider(vol, 100, Glass.MINT) { pct ->
                    HaRepository.callService("media_player", "volume_set", listOf(t.entityId), mapOf("volume_level" to pct / 100.0))
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
        if (t.showDpad) { addView(g.spacer(context, h = 14)); addView(dpad(t)) }
    }

    // ---------- bigger Roku D-pad ----------
    private fun dpad(t: RoomControl.MediaTile): View = g.col(context).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        fun b(btn: MediaRemote.Button): () -> Unit = { MediaRemote.press(btn, t.remoteEntityId, t.entityId)?.let { send(it) } }
        addView(dKey("▲", b(MediaRemote.Button.UP)))
        addView(g.spacer(context, h = 8))
        addView(g.row(context).apply {
            gravity = Gravity.CENTER
            addView(dKey("◀", b(MediaRemote.Button.LEFT))); addView(g.spacer(context, w = 10))
            addView(dKey("OK", b(MediaRemote.Button.OK), Glass.BLUE)); addView(g.spacer(context, w = 10))
            addView(dKey("▶", b(MediaRemote.Button.RIGHT)))
        })
        addView(g.spacer(context, h = 8))
        addView(dKey("▼", b(MediaRemote.Button.DOWN)))
        addView(g.spacer(context, h = 12))
        addView(g.row(context).apply {
            gravity = Gravity.CENTER
            addView(roundKey("↩ back") { b(MediaRemote.Button.BACK)() }); addView(g.spacer(context, w = 8))
            addView(roundKey("⌂ home") { b(MediaRemote.Button.HOME)() }); addView(g.spacer(context, w = 8))
            addView(roundKey("↺") { b(MediaRemote.Button.REPLAY)() }); addView(g.spacer(context, w = 8))
            addView(roundKey("ⓘ") { b(MediaRemote.Button.INFO)() })
        })
    }

    // ---------- CLIMATE ----------
    private fun climateCard(t: RoomControl.ClimateTile): View = g.col(context).apply {
        background = g.panel(context, 16f)
        setPadding(g.dp(context, 15f), g.dp(context, 13f), g.dp(context, 14f), g.dp(context, 13f))
        addView(g.row(context).apply {
            addView(g.col(context).apply {
                addView(tv(15f, Glass.INK, g.body(context)).apply { text = t.name; setTypeface(typeface, Typeface.BOLD) })
                addView(tv(11f, Glass.INK_DIM, g.body(context)).apply {
                    text = buildString {
                        append(t.mode)
                        t.currentTemp?.let { append("  ·  now ${it.roundToInt()}°") }
                        t.targetTemp?.let { append("  ·  set ${it.roundToInt()}°") }
                    }
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        })
        val target = t.targetTemp
        if (target != null) {
            addView(g.spacer(context, h = 8))
            val span = (t.maxTemp - t.minTemp).coerceAtLeast(1.0)
            val cur = (((target - t.minTemp) / span) * 100).roundToInt().coerceIn(0, 100)
            addView(slider(cur, 100, Glass.RED) { pct ->
                val temp = t.minTemp + (pct / 100.0) * span
                val stepped = (Math.round(temp / t.step) * t.step)
                HaRepository.callService("climate", "set_temperature", listOf(t.entityId), mapOf("temperature" to stepped))
            })
        }
    }

    // ---------- FAN ----------
    private fun fanCard(t: RoomControl.FanTile): View = g.col(context).apply {
        background = g.tile(context, Glass.MINT, t.isOn, 16f)
        setPadding(g.dp(context, 15f), g.dp(context, 13f), g.dp(context, 14f), g.dp(context, 13f))
        addView(g.row(context).apply {
            addView(tv(15f, Glass.INK, g.body(context)).apply { text = t.name; setTypeface(typeface, Typeface.BOLD) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(toggleKey(t.isOn, Glass.MINT) {
                HaRepository.callService("fan", if (t.isOn) "turn_off" else "turn_on", listOf(t.entityId)); refresh()
            })
        })
        if (t.isOn && t.supportsSpeed) {
            addView(g.spacer(context, h = 10))
            addView(slider(t.speedPct ?: 50, 100, Glass.MINT) { pct ->
                HaRepository.callService("fan", "set_percentage", listOf(t.entityId), mapOf("percentage" to pct))
            })
        }
    }

    // ---------- COVER ----------
    private fun coverCard(t: RoomControl.CoverTile): View = g.col(context).apply {
        background = g.panel(context, 16f)
        setPadding(g.dp(context, 15f), g.dp(context, 13f), g.dp(context, 14f), g.dp(context, 13f))
        addView(g.row(context).apply {
            addView(g.col(context).apply {
                addView(tv(15f, Glass.INK, g.body(context)).apply { text = t.name; setTypeface(typeface, Typeface.BOLD) })
                addView(tv(11f, Glass.INK_DIM, g.body(context)).apply { text = t.state + (t.positionPct?.let { "  ·  $it%" } ?: "") })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(roundKey("▲") { HaRepository.callService("cover", "open_cover", listOf(t.entityId)); refresh() }); addView(g.spacer(context, w = 7))
            addView(roundKey("■") { HaRepository.callService("cover", "stop_cover", listOf(t.entityId)); refresh() }); addView(g.spacer(context, w = 7))
            addView(roundKey("▼") { HaRepository.callService("cover", "close_cover", listOf(t.entityId)); refresh() })
        })
        val pos = t.positionPct
        if (t.supportsPosition && pos != null) {
            addView(g.spacer(context, h = 10))
            addView(slider(pos, 100, Glass.BLUE) { pct ->
                HaRepository.callService("cover", "set_cover_position", listOf(t.entityId), mapOf("position" to pct))
            })
        }
    }

    // ---------- LOCK ----------
    private fun lockCard(t: RoomControl.LockTile): View = g.row(context).apply {
        background = g.tile(context, if (t.locked) Glass.MINT else Glass.RED, true, 16f)
        setPadding(g.dp(context, 15f), g.dp(context, 13f), g.dp(context, 14f), g.dp(context, 13f))
        addView(g.col(context).apply {
            addView(tv(15f, Glass.INK, g.body(context)).apply { text = t.name; setTypeface(typeface, Typeface.BOLD) })
            addView(tv(11f, if (t.locked) Glass.MINT else Glass.RED, g.body(context)).apply { text = if (t.locked) "locked" else "UNLOCKED" })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(key(if (t.locked) "UNLOCK" else "LOCK", if (t.locked) Glass.RED else Glass.MINT) {
            HaRepository.callService("lock", if (t.locked) "unlock" else "lock", listOf(t.entityId)); refresh()
        })
    }

    // ---------- SWITCH toggle ----------
    private fun toggleCard(t: RoomControl.ToggleTile): View = g.row(context).apply {
        background = g.tile(context, Glass.VIOLET, t.isOn, 14f)
        setPadding(g.dp(context, 15f), g.dp(context, 12f), g.dp(context, 14f), g.dp(context, 12f))
        addView(tv(14f, Glass.INK, g.body(context)).apply { text = t.name },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(toggleKey(t.isOn, Glass.VIOLET) {
            val domain = t.entityId.substringBefore('.')
            HaRepository.callService(domain, if (t.isOn) "turn_off" else "turn_on", listOf(t.entityId)); refresh()
        })
    }

    // ---------- SENSORS (read-only, compact) ----------
    private fun sensorWrap(sensors: List<RoomControl.SensorTile>): View = g.col(context).apply {
        background = g.panel(context, 16f)
        setPadding(g.dp(context, 14f), g.dp(context, 12f), g.dp(context, 14f), g.dp(context, 12f))
        sensors.forEach { s ->
            addView(g.row(context).apply {
                setPadding(0, g.dp(context, 4f), 0, g.dp(context, 4f))
                addView(tv(12.5f, Glass.INK_DIM, g.body(context)).apply { text = s.name },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(tv(12.5f, Glass.INK, g.disp(context)).apply { text = (s.value + " " + s.unit).trim() })
            })
        }
    }

    // ---------- shared widgets ----------
    private fun slider(value: Int, max: Int, accent: Int, onDone: (Int) -> Unit): SeekBar = SeekBar(context).apply {
        this.max = max; progress = value.coerceIn(0, max)
        progressTintList = ColorStateList.valueOf(accent)
        thumbTintList = ColorStateList.valueOf(accent)
        progressBackgroundTintList = ColorStateList.valueOf(Glass.STROKE)
        val h = g.dp(context, 10f); setPadding(paddingLeft, h, paddingRight, h)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(sb: SeekBar) { dragging = true }
            override fun onStopTrackingTouch(sb: SeekBar) { dragging = false; haptic(); onDone(sb.progress) }
        })
    }

    private fun toggleKey(on: Boolean, accent: Int, onTap: () -> Unit): View =
        tv(16f, if (on) accent else Glass.INK_DIM, g.disp(context)).apply {
            text = if (on) "⏻" else "○"; gravity = Gravity.CENTER
            background = g.tile(context, accent, on, 13f)
            val s = g.dp(context, 46f); layoutParams = LinearLayout.LayoutParams(s, s)
            setOnClickListener { haptic(); onTap() }
        }

    private fun roundKey(label: String, accent: Int = Glass.INK_DIM, onTap: () -> Unit): View =
        tv(13f, accent, g.disp(context)).apply {
            text = label; gravity = Gravity.CENTER
            background = g.tile(context, accent, false, 13f)
            minHeight = g.dp(context, 44f); minimumHeight = g.dp(context, 44f)
            setPadding(g.dp(context, 14f), 0, g.dp(context, 14f), 0)
            setOnClickListener { haptic(); onTap() }
        }

    // bigger square D-pad key
    private fun dKey(label: String, onTap: () -> Unit, accent: Int = Glass.INK): View =
        tv(19f, accent, g.disp(context)).apply {
            text = label; gravity = Gravity.CENTER
            background = g.tile(context, if (accent == Glass.BLUE) Glass.BLUE else Glass.INK_DIM, accent == Glass.BLUE, 16f)
            val s = g.dp(context, 68f); layoutParams = LinearLayout.LayoutParams(s, s)
            setOnClickListener { haptic(); onTap() }
        }

    private fun send(c: com.acewood.synapse.logic.HaCall) {
        HaRepository.callService(c.domain, c.service, listOf(c.entityId), c.data)
        postDelayed({ refresh() }, 300)
    }

    private fun key(text: String, color: Int, onTap: () -> Unit): View =
        tv(12f, color, g.disp(context)).apply {
            setText(text); gravity = Gravity.CENTER; letterSpacing = 0.08f
            background = g.tile(context, color, false, 14f)
            setPadding(g.dp(context, 16f), g.dp(context, 11f), g.dp(context, 16f), g.dp(context, 11f))
            setOnClickListener { haptic(); onTap() }
        }
}

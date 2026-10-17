package com.acewood.synapse.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
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
    private val onCamera: (String, String) -> Unit = { _, _ -> },
    private val cameraAllowed: () -> Boolean = { true },
) : FrameLayout(context) {

    private val g = Glass
    private var room: Room? = null
    private var roomList: List<Room> = emptyList()
    private var roomIndex = 0
    private var downX = 0f
    private var downY = 0f
    private var dragging = false   // don't rebuild under a finger on a slider
    private val bodyCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val title = tv(24f, Glass.INK, g.disp(context)).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
    private val subtitle = tv(11f, Glass.BLUE, g.bodyBold(context)).apply { letterSpacing = 0.14f; maxLines = 1 }

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
            addView(iconKey(GlassIcon.Kind.CHEVRON_LEFT, "Back to remote", 14f) { onBack() }, LinearLayout.LayoutParams(g.dp(context, 44f), g.dp(context, 44f)))
            addView(g.col(context).apply { addView(subtitle); addView(title) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = g.dp(context, 12f); rightMargin = g.dp(context, 8f) })
            addView(iconKey(GlassIcon.Kind.CHEVRON_LEFT, "Previous room", 14f, filled = true) { switchRoom(-1) }, LinearLayout.LayoutParams(g.dp(context, 40f), g.dp(context, 44f)))
            addView(g.spacer(context, w = 6))
            addView(iconKey(GlassIcon.Kind.CHEVRON_RIGHT, "Next room", 14f, filled = true) { switchRoom(1) }, LinearLayout.LayoutParams(g.dp(context, 40f), g.dp(context, 44f)))
        })
        outer.addView(g.spacer(context, h = 12))
        outer.addView(ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(bodyCol) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) return false   // a slider is being dragged: horizontal moves belong to it
                val dx = event.x - downX; val dy = event.y - downY
                if (kotlin.math.abs(dx) > g.dp(context, 55f) && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.25f) return true
            }
        }
        return false
    }

    /** After a horizontal swipe is intercepted, the rest of the gesture lands here: switch on release. */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val dx = event.x - downX
            if (kotlin.math.abs(dx) > g.dp(context, 55f)) switchRoom(if (dx < 0) 1 else -1)
        }
        return true
    }

    private fun switchRoom(delta: Int) {
        if (roomList.isEmpty()) return
        val next = (roomIndex + delta).coerceIn(0, roomList.lastIndex)
        if (next != roomIndex) { roomIndex = next; room = roomList[next]; lastSig = null; refresh(); haptic() }
    }

    private fun tv(size: Float, color: Int, tf: Typeface) = TextView(context).apply {
        textSize = size; setTextColor(color); typeface = tf
    }
    private fun haptic() {
        if (!PanelSettingsStore.load(context).hapticsEnabled) return
        try { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } catch (_: Exception) {}
    }
    private fun cacheTargets(t: RoomControl.LightTile) =
        HaRepository.cache?.let { t.light.commandTargets(it, both = false) } ?: t.light.entityIds

    fun open(r: Room, rooms: List<Room> = listOf(r)) {
        roomList = if (rooms.isEmpty()) listOf(r) else rooms
        roomIndex = roomList.indexOfFirst { it.id == r.id }.coerceAtLeast(0)
        room = roomList[roomIndex]; lastSig = null; refresh()
    }

    private var lastSig: String? = null

    /** Only the entities this room shows; a change anywhere else in the house shouldn't rebuild the pad. */
    private fun signature(r: Room, cache: com.acewood.synapse.logic.EntityCache): String = buildString {
        append(r.id)
        (r.lights + r.switches + r.media + r.extras + r.remotes + r.fans + r.covers + r.climate + r.locks + r.sensors).forEach { id ->
            val e = cache.get(id); append('|').append(id).append('=').append(e?.state)
            if (e != null) {
                append(e.attributes["brightness"]); append(e.attributes["volume_level"]); append(e.attributes["is_volume_muted"]); append(e.attributes["media_title"])
                append(e.attributes["percentage"]); append(e.attributes["current_position"]); append(e.attributes["temperature"])
            }
        }
    }

    fun refresh() {
        if (dragging) return
        post {
            val r = room ?: return@post
            val cache = HaRepository.cache ?: return@post
            val sig = signature(r, cache)
            if (sig == lastSig) return@post
            lastSig = sig
            val m = RoomControl.build(r, cache)
            title.text = r.name
            subtitle.text = buildString {
                append("CH ${roomIndex + 1} OF ${roomList.size.coerceAtLeast(1)}")
                if (m.lightsAmbiguous) append("  ·  MAP LIGHTS")
            }
            bodyCol.removeAllViews()
            if (r.cameras.isNotEmpty() && cameraAllowed()) section("CAMERA", r.cameras.map { cameraCard(it, r.name) })
            section("LIGHTS", m.lights.map { lightCard(it) })
            m.media.forEach { addCard(mediaStrip(it)) }
            section("CLIMATE", m.climate.map { climateCard(it) })
            section("FANS", m.fans.map { fanCard(it) })
            section("COVERS", m.covers.map { coverCard(it) })
            section("LOCKS", m.locks.map { lockCard(it) })
            // Ivy devices are user-facing room controls, not hidden alert settings. Keep only
            // alert/ding/motion helpers folded so Ivy switches remain visible in the room pad.
            val (settings, switches) = m.extras.partition {
                it.name.contains("alert", true) || it.name.contains("ding", true) || it.name.contains("motion", true)
            }
            section("SWITCHES", switches.map { toggleCard(it) })
            if (settings.isNotEmpty()) addCard(settingsFold(settings))
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

    // ---------- CAMERA ----------
    private fun cameraCard(entityId: String, roomName: String): View = g.row(context).apply {
        val label = com.acewood.synapse.logic.CameraFeed.label(entityId, roomName, null)
        background = GradientDrawable().apply { cornerRadius = g.dp(context, 20f).toFloat(); setColor(Color.argb(10, 255, 255, 255)); setStroke(g.dp(context, 1f), Glass.withAlpha(Glass.RED, 110)) }
        setPadding(g.dp(context, 16f), g.dp(context, 14f), g.dp(context, 16f), g.dp(context, 14f))
        addView(GlassIcon(context, GlassIcon.Kind.CAMERA, Glass.RED, 2f), LinearLayout.LayoutParams(g.dp(context, 26f), g.dp(context, 26f)).apply { rightMargin = g.dp(context, 12f) })
        addView(g.col(context).apply {
            addView(tv(16f, Glass.INK, g.bodyBold(context)).apply { text = label })
            addView(tv(13f, Glass.INK_FAINT, g.body(context)).apply { text = HaRepository.cache?.get(entityId)?.state?.let { if (it == "unavailable") "Unavailable" else "Tap to watch live" } ?: "Tap to watch live" })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(tv(13f, Glass.RED, g.bodyBold(context)).apply { text = "WATCH" })
        tap { onCamera(entityId, label) }
    }

    // ---------- LIGHT ----------
    private fun lightCard(t: RoomControl.LightTile): View = g.col(context).apply {
        background = GradientDrawable().apply {
            cornerRadius = g.dp(context, 24f).toFloat()
            setColor(if (t.isOn) Glass.withAlpha(Glass.AMBER, 22) else Color.argb(10, 255, 255, 255))
            setStroke(g.dp(context, 1f), if (t.isOn) Glass.withAlpha(Glass.AMBER, 95) else Color.argb(46, 130, 170, 255))
        }
        setPadding(g.dp(context, 16f), g.dp(context, 15f), g.dp(context, 16f), g.dp(context, 16f))
        addView(g.row(context).apply {
            addView(g.col(context).apply {
                addView(tv(16f, Glass.INK, g.bodyBold(context)).apply { text = t.light.name })
                addView(tv(13f, if (t.isOn) Color.rgb(255, 217, 138) else Glass.INK_FAINT, g.body(context)).apply {
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
            addView(g.spacer(context, h = 12))
            addView(capLabel("BRIGHTNESS"))
            addView(slider(t.brightnessPct ?: 50, 100, Glass.AMBER) { pct ->
                val targets = cacheTargets(t)
                HaRepository.optimisticAttribute(targets, "brightness", (pct * 255 / 100).coerceIn(1, 255))
                HaRepository.callService("light", "turn_on", targets, mapOf("brightness_pct" to pct))
            })
        }
        if (t.isOn && t.colorCapable) {
            addView(g.spacer(context, h = 12)); addView(capLabel("COLOR")); addView(g.spacer(context, h = 6)); addView(swatchRow(t))
        }
        if (t.isOn && t.colorTempCapable) {
            addView(g.spacer(context, h = 12))
            addView(capLabel("WARM  ←  →  COOL"))
            addView(slider(50, 100, Glass.BLUE) { pct ->
                val kelvin = (2200 + (pct / 100.0) * (6500 - 2200)).roundToInt()
                HaRepository.callService("light", "turn_on", cacheTargets(t), mapOf("color_temp_kelvin" to kelvin))
            })
        }
    }

    /** Two rows of five rounded colour chips that fill the card width (big thumb targets). */
    private fun swatchRow(t: RoomControl.LightTile): View = g.col(context).apply {
        swatches.chunked(5).forEachIndexed { row, chunk ->
            if (row > 0) addView(g.spacer(context, h = 8))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                chunk.forEachIndexed { i, (name, rgb) ->
                    if (i > 0) addView(View(context), LinearLayout.LayoutParams(g.dp(context, 8f), 1))
                    addView(View(context).apply {
                        contentDescription = name
                        background = GradientDrawable().apply { cornerRadius = g.dp(context, 12f).toFloat(); setColor(Color.rgb(rgb[0], rgb[1], rgb[2])) }
                        tap { HaRepository.callService("light", "turn_on", cacheTargets(t), mapOf("rgb_color" to listOf(rgb[0], rgb[1], rgb[2]))) }
                    }, LinearLayout.LayoutParams(0, g.dp(context, 40f), 1f))
                }
            })
        }
    }

    private fun capLabel(text: String): View = tv(11.5f, Color.rgb(185, 164, 110), g.bodyBold(context)).apply { this.text = text; letterSpacing = 0.1f }

    // ---------- MEDIA ----------
    private fun mediaStrip(t: RoomControl.MediaTile): View = g.col(context).apply {
        background = g.panel(context, 18f)
        setPadding(g.dp(context, 15f), g.dp(context, 14f), g.dp(context, 15f), g.dp(context, 14f))
        addView(g.row(context).apply {
            val art = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP; background = g.tile(context, Glass.VIOLET, false, 10f)
                layoutParams = LinearLayout.LayoutParams(g.dp(context, 48f), g.dp(context, 48f)).apply { rightMargin = g.dp(context, 10f) }
            }
            AlbumArtLoader.load(art, t.albumArt)
            addView(art)
            addView(g.col(context).apply {
                addView(tv(14f, Glass.INK, g.body(context)).apply { text = t.title; setTypeface(typeface, Typeface.BOLD); maxLines = 1 })
                addView(tv(11f, if (t.isPlaying) Glass.MINT else Glass.INK_FAINT, g.body(context)).apply { text = t.appName ?: t.state })
            })
        })
        if (t.canTransport) {
            addView(g.spacer(context, h = 12))
            addView(g.row(context).apply {
                gravity = Gravity.CENTER
                addView(iconKey(GlassIcon.Kind.PREV, "Previous", 14f, filled = true) { send(MediaRemote.previous(t.entityId)) }, LinearLayout.LayoutParams(g.dp(context, 56f), g.dp(context, 48f))); addView(g.spacer(context, w = 12))
                addView(iconKey(if (t.isPlaying) GlassIcon.Kind.PAUSE else GlassIcon.Kind.PLAY, "Play or pause", 14f, filled = true, accent = Glass.MINT) { send(MediaRemote.playPause(t.entityId)) }, LinearLayout.LayoutParams(g.dp(context, 56f), g.dp(context, 48f))); addView(g.spacer(context, w = 12))
                addView(iconKey(GlassIcon.Kind.NEXT, "Next", 14f, filled = true) { send(MediaRemote.next(t.entityId)) }, LinearLayout.LayoutParams(g.dp(context, 56f), g.dp(context, 48f)))
            })
        }
        val vol = t.volumePct
        if (vol != null) {
            addView(g.spacer(context, h = 10))
            addView(g.row(context).apply {
                addView(tv(11f, Glass.INK_FAINT, g.disp(context)).apply { text = "VOL" })
                addView(g.spacer(context, w = 10))
                addView(slider(vol, 100, Glass.MINT) { pct ->
                    HaRepository.optimisticAttribute(listOf(t.entityId), "volume_level", pct / 100.0)
                    HaRepository.callService("media_player", "volume_set", listOf(t.entityId), mapOf("volume_level" to pct / 100.0))
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
        if (t.canVolumeStep || t.canMute) {
            addView(g.spacer(context, h = 8))
            addView(mediaVolumeKeys(t.entityId, t.caps, t.isMuted))
        }
        if (t.showDpad) {
            addView(g.spacer(context, h = 10))
            addView(tv(11f, Glass.BLUE, g.disp(context)).apply { text = "TV REMOTE"; gravity = Gravity.CENTER })
            addView(g.spacer(context, h = 4)); addView(dpad(t))
        }
    }

    /** VOL − · MUTE · VOL + from the shared [com.acewood.synapse.logic.MediaCaps] gate. */
    private fun mediaVolumeKeys(entityId: String, caps: com.acewood.synapse.logic.MediaCaps, isMuted: Boolean): View =
        g.row(context).apply {
            gravity = Gravity.CENTER
            if (caps.showVolumeKeys) {
                addView(roundKey("VOL −") { caps.volumeDown(entityId)?.let { send(it) } })
                addView(g.spacer(context, w = 7))
            }
            if (caps.showMute) {
                val label = if (!caps.exactMute) "MUTE" else if (isMuted) "UNMUTE" else "MUTE"
                addView(roundKey(label, if (caps.exactMute && isMuted) Glass.RED else Glass.INK_DIM) {
                    if (caps.exactMute) HaRepository.optimisticAttribute(listOf(entityId), "is_volume_muted", !isMuted)
                    caps.mute(entityId, !isMuted)?.let { send(it) }
                })
                if (caps.showVolumeKeys) addView(g.spacer(context, w = 7))
            }
            if (caps.showVolumeKeys) addView(roundKey("VOL +", Glass.MINT) { caps.volumeUp(entityId)?.let { send(it) } })
        }

    // ---------- bigger Roku D-pad ----------
    private fun dpad(t: RoomControl.MediaTile): View = g.col(context).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        fun b(btn: MediaRemote.Button): () -> Unit = { MediaRemote.press(btn, t.remoteEntityId, t.entityId)?.let { send(it) } }
        addView(dIcon(GlassIcon.Kind.CHEVRON_UP, "Up", b(MediaRemote.Button.UP)))
        addView(g.spacer(context, h = 8))
        addView(g.row(context).apply {
            gravity = Gravity.CENTER
            addView(dIcon(GlassIcon.Kind.CHEVRON_LEFT, "Left", b(MediaRemote.Button.LEFT))); addView(g.spacer(context, w = 10))
            addView(dKey("OK", b(MediaRemote.Button.OK), Glass.BLUE)); addView(g.spacer(context, w = 10))
            addView(dIcon(GlassIcon.Kind.CHEVRON_RIGHT, "Right", b(MediaRemote.Button.RIGHT)))
        })
        addView(g.spacer(context, h = 8))
        addView(dIcon(GlassIcon.Kind.CHEVRON_DOWN, "Down", b(MediaRemote.Button.DOWN)))
        addView(g.spacer(context, h = 12))
        addView(g.row(context).apply {
            gravity = Gravity.CENTER
            addView(roundKey("Back") { b(MediaRemote.Button.BACK)() }); addView(g.spacer(context, w = 8))
            addView(roundKey("Home") { b(MediaRemote.Button.HOME)() }); addView(g.spacer(context, w = 8))
            addView(roundKey("Replay") { b(MediaRemote.Button.REPLAY)() }); addView(g.spacer(context, w = 8))
            addView(roundKey("Info") { b(MediaRemote.Button.INFO)() })
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
                HaRepository.optimisticAttribute(listOf(t.entityId), "temperature", stepped)
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
                HaRepository.optimisticAttribute(listOf(t.entityId), "percentage", pct)
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
            addView(roundKey("Open") { HaRepository.callService("cover", "open_cover", listOf(t.entityId)); refresh() }); addView(g.spacer(context, w = 7))
            addView(roundKey("Stop") { HaRepository.callService("cover", "stop_cover", listOf(t.entityId)); refresh() }); addView(g.spacer(context, w = 7))
            addView(roundKey("Close") { HaRepository.callService("cover", "close_cover", listOf(t.entityId)); refresh() })
        })
        val pos = t.positionPct
        if (t.supportsPosition && pos != null) {
            addView(g.spacer(context, h = 10))
            addView(slider(pos, 100, Glass.BLUE) { pct ->
                HaRepository.optimisticAttribute(listOf(t.entityId), "current_position", pct)
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

    private fun settingsFold(items: List<RoomControl.ToggleTile>): View = g.col(context).apply {
        background = g.panel(context, 16f)
        val details = g.col(context).apply { visibility = View.GONE }
        addView(tv(13f, Glass.INK_DIM, g.disp(context)).apply {
            text = "⚙  SETTINGS  ·  Ivy alerts"
            setPadding(g.dp(context, 14f), g.dp(context, 13f), 0, g.dp(context, 13f))
            tap { details.visibility = if (details.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        })
        items.forEach { details.addView(toggleCard(it)); details.addView(g.spacer(context, h = 6)) }
        addView(details)
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

    /** A pill on/off switch (64×36 dp, 48 dp tall touch area). */
    private fun toggleKey(on: Boolean, accent: Int, onTap: () -> Unit): View = FrameLayout(context).apply {
        contentDescription = if (on) "Turn off" else "Turn on"
        layoutParams = LinearLayout.LayoutParams(g.dp(context, 68f), g.dp(context, 48f))
        addView(FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = g.dp(context, 18f).toFloat()
                setColor(if (on) accent else Color.argb(28, 255, 255, 255))
                setStroke(g.dp(context, 1f), if (on) accent else Color.argb(70, 130, 170, 255))
            }
            addView(View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (on) Color.rgb(29, 20, 4) else Glass.INK_DIM) }
            }, LayoutParams(g.dp(context, 28f), g.dp(context, 28f), (if (on) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL).apply {
                leftMargin = g.dp(context, 4f); rightMargin = g.dp(context, 4f)
            })
        }, LayoutParams(g.dp(context, 64f), g.dp(context, 36f), Gravity.CENTER))
        tap { onTap() }
    }

    private fun iconKey(kind: GlassIcon.Kind, label: String, radius: Float, filled: Boolean = false, accent: Int = Glass.INK_DIM, onTap: () -> Unit): View = FrameLayout(context).apply {
        contentDescription = label
        background = GradientDrawable().apply {
            cornerRadius = g.dp(context, radius).toFloat()
            if (filled) setColor(Color.argb(14, 255, 255, 255)) else setStroke(g.dp(context, 1f), Color.argb(64, 130, 170, 255))
        }
        addView(GlassIcon(context, kind, if (accent == Glass.INK_DIM) Color.rgb(201, 212, 242) else accent, 2.2f), LayoutParams(g.dp(context, 18f), g.dp(context, 18f), Gravity.CENTER))
        tap { onTap() }
    }

    private fun dIcon(kind: GlassIcon.Kind, label: String, onTap: () -> Unit): View = FrameLayout(context).apply {
        contentDescription = label
        background = g.tile(context, Glass.INK_DIM, false, 18f)
        addView(GlassIcon(context, kind, Glass.INK, 2.4f), LayoutParams(g.dp(context, 24f), g.dp(context, 24f), Gravity.CENTER))
        val s = g.dp(context, 68f); layoutParams = LinearLayout.LayoutParams(s, s)
        tap { onTap() }
    }

    private fun roundKey(label: String, accent: Int = Glass.INK_DIM, onTap: () -> Unit): View =
        tv(13f, accent, g.disp(context)).apply {
            text = label; gravity = Gravity.CENTER
            background = g.tile(context, accent, false, 13f)
            minHeight = g.dp(context, 44f); minimumHeight = g.dp(context, 44f)
            setPadding(g.dp(context, 14f), 0, g.dp(context, 14f), 0)
            tap { onTap() }
        }

    // bigger square D-pad key
    private fun dKey(label: String, onTap: () -> Unit, accent: Int = Glass.INK): View =
        tv(19f, accent, g.disp(context)).apply {
            text = label; gravity = Gravity.CENTER
            background = g.tile(context, if (accent == Glass.BLUE) Glass.BLUE else Glass.INK_DIM, accent == Glass.BLUE, 16f)
            val s = g.dp(context, 68f); layoutParams = LinearLayout.LayoutParams(s, s)
            tap { onTap() }
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
            tap { onTap() }
        }
}

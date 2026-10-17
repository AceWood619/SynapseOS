package com.acewood.synapse.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import com.acewood.synapse.logic.EntityCache
import com.acewood.synapse.logic.MediaRemote
import com.acewood.synapse.logic.NodeConfig
import com.acewood.synapse.logic.Profile
import com.acewood.synapse.logic.RemoteHome
import com.acewood.synapse.logic.Room
import kotlin.math.abs

/**
 * The "Remote" home screen (redesign option B, Mason 2026-10-16): the phone is a TV remote for the house.
 *
 *   header      SYNAPSE · Jarvis chip · weather/time · profile (notice badge)
 *   channel     ‹  CH 3 / Kids Room / HBO Max playing  ›      (swipe or arrows change room)
 *   remote      VOL rocker · D-pad + OK · mute / play-pause / back   (drive the tuned room's media)
 *   room keys   Lights · TV · Room                           (this room)
 *   bottom      House · mic (Jarvis) · More
 *
 * Built once; [refresh] only updates text, colors and enabled states (cheap on the GE8320).
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class RemoteHomeView(
    context: Context,
    private val cfg: NodeConfig,
    private val onOpenRoom: (Room) -> Unit,
    private val onMic: () -> Unit,
    private val onHouse: () -> Unit,
    private val onMore: () -> Unit,
    private val onSwitchProfile: () -> Unit,
    private val onJarvisSwitch: (Boolean) -> Unit,
    private val onWeather: () -> Unit,
    private val onNotices: () -> Unit,
) : FrameLayout(context) {
    private val g = Glass
    private fun dp(v: Float) = g.dp(context, v)

    private val ink = Glass.INK
    private val dim = Color.rgb(201, 212, 242)
    private val faint = Color.rgb(143, 160, 204)
    private val panelFill = Color.rgb(13, 21, 48)
    private val stroke = Color.argb(56, 130, 170, 255)

    private var profileRooms: Set<String> = emptySet()
    private var channel: RemoteHome.Channel? = null
    private var lastSig: String? = null

    // header
    private val jarvisChip = text(11f, Glass.MINT, g.disp(context)).apply { letterSpacing = 0.06f; gravity = Gravity.CENTER }
    private val weather = text(13f, dim, g.body(context))
    private val avatar = text(14f, ink, g.disp(context)).apply { gravity = Gravity.CENTER }
    private val badge = text(10f, Color.rgb(26, 16, 0), g.bodyBold(context)).apply {
        gravity = Gravity.CENTER; visibility = View.GONE
        background = GradientDrawable().apply { cornerRadius = dp(8f).toFloat(); setColor(Glass.AMBER) }
    }

    // channel card
    private val chLabel = text(11f, Glass.BLUE, g.bodyBold(context)).apply { letterSpacing = 0.16f; gravity = Gravity.CENTER }
    private val roomName = text(26f, ink, g.disp(context)).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val status = text(13f, Glass.MINT, g.body(context)).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }

    // remote
    private val volUp = GlassIcon(context, GlassIcon.Kind.PLUS, ink)
    private val volDown = GlassIcon(context, GlassIcon.Kind.MINUS, ink)
    private val muteIcon = GlassIcon(context, GlassIcon.Kind.MUTE, ink, 2f)
    private val playIcon = GlassIcon(context, GlassIcon.Kind.PAUSE, Glass.MINT, 2.4f)
    private lateinit var rocker: View
    private lateinit var dpad: View
    private lateinit var sideKeys: View

    // house modes (first four that exist in HA)
    private val modesRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

    // room keys
    private val lightsKey = roomKey(GlassIcon.Kind.BULB, "Lights") { lightsTap() }
    private val tvKey = roomKey(GlassIcon.Kind.TV, "TV") { tvTap() }
    private val roomKeyView = roomKey(GlassIcon.Kind.GRID, "Room") { channel?.let { onOpenRoom(it.room) } }

    init {
        background = g.ground()
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16f), 0, dp(16f), dp(16f)) }
        addView(col, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        col.addView(header(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56f)))
        col.addView(channelCard())
        col.addView(remote(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18f) })
        col.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(lightsKey, LinearLayout.LayoutParams(0, dp(72f), 1f))
            addView(View(context), LinearLayout.LayoutParams(dp(10f), 1))
            addView(tvKey, LinearLayout.LayoutParams(0, dp(72f), 1f))
            addView(View(context), LinearLayout.LayoutParams(dp(10f), 1))
            addView(roomKeyView, LinearLayout.LayoutParams(0, dp(72f), 1f))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18f) })
        col.addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
        col.addView(text(12f, faint, g.bodyBold(context)).apply { text = "HOUSE MODES"; letterSpacing = 0.14f; setPadding(dp(4f), 0, 0, dp(8f)) })
        col.addView(modesRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48f)))
        col.addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
        col.addView(bottomBar())
        avatar.text = (cfg.ownerName.trim().firstOrNull() ?: 'S').uppercaseChar().toString()
    }

    // ---------------- public API (mirrors HomeView's) ----------------

    fun setProfile(p: Profile) {
        avatar.text = (p.name.trim().firstOrNull() ?: 'S').uppercaseChar().toString()
        profileRooms = p.layout.rooms.map { it.trim().lowercase().replace(' ', '_') }.filter { it.isNotEmpty() }.toSet()
        lastSig = null; refresh()
    }

    fun setNoticeCount(count: Int) {
        if (count <= 0) { badge.visibility = View.GONE; return }
        badge.text = if (count > 9) "9+" else count.toString(); badge.visibility = View.VISIBLE
    }

    fun refresh() {
        val cache = HaRepository.cache
        val rooms = visibleRooms()
        if (channel == null || rooms.none { it.id == channel?.room?.id }) {
            index = RemoteHome.startIndex(rooms, rememberedRoomId, cfg.room, cache)
        }
        val sig = signature(cache, rooms)
        if (sig == lastSig) return
        lastSig = sig
        bindJarvis(cache)
        bindWeather(cache)
        bindModes(cache)
        channel = cache?.let { RemoteHome.channel(rooms, index, it) }
        bindChannel(channel, rooms.isEmpty())
    }

    // ---------------- state ----------------

    private var index = 0

    private fun visibleRooms(): List<Room> =
        if (profileRooms.isEmpty()) HaRepository.rooms else HaRepository.rooms.filter { it.id.lowercase() in profileRooms }

    private fun step(delta: Int) {
        val rooms = visibleRooms()
        if (rooms.isEmpty()) return
        index = RemoteHome.wrap(index + delta, rooms.size)
        rememberedRoomId = rooms[index].id
        lastSig = null; refresh()
    }

    private fun signature(cache: EntityCache?, rooms: List<Room>): String = buildString {
        append(index).append('|').append(rooms.joinToString(",") { it.id }).append('|').append(HaRepository.connected)
        val r = rooms.getOrNull(RemoteHome.wrap(index, rooms.size)) ?: return@buildString
        if (cache == null) return@buildString
        (r.lights + r.switches + r.media + r.remotes).forEach { id ->
            val e = cache.get(id); append(id).append(e?.state).append(e?.attributes?.get("is_volume_muted")).append(e?.attributes?.get("app_name")).append(e?.attributes?.get("media_title"))
        }
        HomeView.JARVIS_SWITCHES.forEach { append(cache.get(it)?.state) }
        HomeView.MODES.forEach { append(cache.get(it.first)?.state) }
        cache.byDomain("weather").firstOrNull()?.let { append(it.state).append(it.attributes["temperature"]) }
    }

    private fun bindChannel(ch: RemoteHome.Channel?, noRooms: Boolean) {
        if (ch == null) {
            chLabel.text = "SYNAPSE"
            roomName.text = if (noRooms && HaRepository.connected) "Loading rooms…" else "Waiting for Home Assistant"
            status.text = ""; status.setTextColor(faint)
            enable(rocker, false); enable(dpad, false); enable(sideKeys, false)
            bindKey(lightsKey, "Lights", false, Glass.AMBER, false); bindKey(tvKey, "TV", false, Glass.MINT, false); bindKey(roomKeyView, "Room", false, Glass.BLUE, false)
            return
        }
        chLabel.text = "${ch.label}  OF  ${ch.count}"
        roomName.text = ch.room.name
        status.text = ch.status
        status.setTextColor(if (ch.isPlaying) Glass.MINT else if (ch.anyLightOn) Color.rgb(255, 217, 138) else faint)
        enable(rocker, ch.caps.showVolumeKeys)
        enable(dpad, ch.hasDpad)
        enable(sideKeys, ch.hasMedia)
        muteIcon.alpha = if (ch.caps.showMute) 1f else 0.35f
        playIcon.showKind(if (ch.isPlaying) GlassIcon.Kind.PAUSE else GlassIcon.Kind.PLAY)
        bindKey(lightsKey, if (!ch.hasLights) "No lights" else if (ch.anyLightOn) "Lights on" else "Lights off", ch.anyLightOn, Glass.AMBER, ch.hasLights)
        bindKey(tvKey, if (!ch.hasMedia) "No TV" else if (ch.mediaOn) "TV on" else "TV off", ch.mediaOn, Glass.MINT, ch.hasMedia)
        bindKey(roomKeyView, "Room", false, Glass.BLUE, true)
    }

    private fun bindJarvis(cache: EntityCache?) {
        val live = HomeView.jarvisLive(cache)
        jarvisChip.text = if (live) "JARVIS ON" else "JARVIS OFF"
        jarvisChip.setTextColor(if (live) Glass.MINT else Color.rgb(255, 154, 170))
        jarvisChip.background = GradientDrawable().apply {
            cornerRadius = dp(14f).toFloat(); setColor(Glass.withAlpha(if (live) Glass.MINT else Glass.RED, 30))
            setStroke(dp(1f), Glass.withAlpha(if (live) Glass.MINT else Glass.RED, 120))
        }
    }

    private fun bindModes(cache: EntityCache?) {
        modesRow.removeAllViews()
        val present = cache?.let { c -> HomeView.MODES.filter { c.get(it.first) != null }.take(4) }.orEmpty()
        modesRow.visibility = if (present.isEmpty()) View.INVISIBLE else View.VISIBLE
        present.forEachIndexed { i, (id, label, accent) ->
            val on = cache?.get(id)?.state == "on"
            if (i > 0) modesRow.addView(View(context), LinearLayout.LayoutParams(dp(8f), 1))
            modesRow.addView(text(13f, if (on) ink else dim, if (on) g.bodyBold(context) else g.body(context)).apply {
                text = label; gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = dp(16f).toFloat()
                    setColor(if (on) Glass.withAlpha(accent, 40) else Color.argb(8, 255, 255, 255))
                    setStroke(dp(1f), if (on) Glass.withAlpha(accent, 170) else Color.argb(46, 130, 170, 255))
                }
                tap { cache?.let { HomeView.toggleMode(id, on, it) }; postDelayed({ lastSig = null; refresh() }, 150) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun bindWeather(cache: EntityCache?) {
        val t = cache?.byDomain("weather")?.firstOrNull()?.attrDouble("temperature")
        weather.text = t?.let { "${Math.round(it)}°" } ?: ""
        weather.visibility = if (t == null) View.GONE else View.VISIBLE
    }

    // ---------------- actions ----------------

    private fun send(call: com.acewood.synapse.logic.HaCall?) {
        call ?: return
        HaRepository.callService(call.domain, call.service, listOf(call.entityId), call.data)
    }

    private fun press(b: MediaRemote.Button) {
        val ch = channel ?: return
        send(MediaRemote.press(b, ch.remoteId, ch.mediaId))
    }

    private fun lightsTap() {
        val ch = channel ?: return
        val (on, targets) = RemoteHome.lightsCall(ch) ?: return
        // Group by domain: light.* and switch.* need their own turn_on/turn_off.
        targets.groupBy { it.substringBefore('.') }.values.forEach { HaRepository.toggle(it, on) }
        postDelayed({ lastSig = null; refresh() }, 120)
    }

    private fun tvTap() {
        val ch = channel ?: return
        val id = ch.mediaId ?: return
        send(MediaRemote.power(id, !ch.mediaOn))
    }

    // ---------------- building blocks ----------------

    private fun header(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(text(13f, Color.rgb(185, 198, 234), g.disp(context)).apply {
            text = "SYNAPSE"; letterSpacing = 0.18f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(jarvisChip.apply {
            setPadding(dp(10f), 0, dp(10f), 0); tap { onJarvisSwitch(HomeView.jarvisLive(HaRepository.cache)) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(30f)).apply { rightMargin = dp(10f) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), dp(8f), dp(4f), dp(8f)); tap { onWeather() }
            addView(weather.apply { setPadding(0, 0, dp(6f), 0) })
            addView(TextClock(context).apply { format12Hour = "h:mm"; format24Hour = "H:mm"; textSize = 13f; setTextColor(dim); typeface = g.body(context) })
        })
        addView(FrameLayout(context).apply {
            clipChildren = false
            val s = dp(36f)
            addView(avatar.apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Glass.withAlpha(Glass.BLUE, 42)); setStroke(dp(1f), Glass.withAlpha(Glass.BLUE, 110)) }
                tap { onSwitchProfile() }
            }, LayoutParams(s, s, Gravity.CENTER))
            addView(badge.apply { tap { onNotices() } }, LayoutParams(dp(18f), dp(18f), Gravity.TOP or Gravity.END))
        }, LinearLayout.LayoutParams(dp(44f), dp(44f)).apply { leftMargin = dp(8f) })
    }

    /** Row that sees every touch first, so a horizontal fling anywhere on the card changes room. */
    private inner class SwipeRow : LinearLayout(context) {
        private var flung = false
        private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dx = e2.x - (e1?.x ?: e2.x)
                if (abs(dx) > dp(40f) && abs(vx) > abs(vy)) { flung = true; step(if (dx < 0) 1 else -1); return true }
                return false
            }
        })
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) flung = false
            detector.onTouchEvent(ev)
            if (flung && ev.actionMasked == MotionEvent.ACTION_UP) {
                // It was a swipe, not a tap: cancel so the child under the finger doesn't also click.
                val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                super.dispatchTouchEvent(cancel); cancel.recycle(); return true
            }
            return super.dispatchTouchEvent(ev)
        }
    }

    private fun channelCard(): View = SwipeRow().apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.rgb(15, 26, 61), Color.rgb(10, 17, 41))).apply {
            cornerRadius = dp(24f).toFloat(); setStroke(dp(1f), Color.argb(70, 130, 170, 255))
        }
        setPadding(dp(10f), dp(16f), dp(10f), dp(16f))
        addView(circleKey(GlassIcon.Kind.CHEVRON_LEFT, "Previous room") { step(-1) }, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            addView(chLabel); addView(roomName); addView(status)
            tap { channel?.let { onOpenRoom(it.room) } }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6f); rightMargin = dp(6f) })
        addView(circleKey(GlassIcon.Kind.CHEVRON_RIGHT, "Next room") { step(1) }, LinearLayout.LayoutParams(dp(44f), dp(44f)))
    }

    private fun remote(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        rocker = pill(listOf(
            Triple(volUp as View, "Volume up") { channel?.let { c -> c.mediaId?.let { send(c.caps.volumeUp(it)) } } },
            Triple(text(10f, faint, g.bodyBold(context)).apply { text = "VOL"; letterSpacing = 0.12f; gravity = Gravity.CENTER } as View, "", null),
            Triple(volDown as View, "Volume down") { channel?.let { c -> c.mediaId?.let { send(c.caps.volumeDown(it)) } } },
        ))
        addView(rocker, LinearLayout.LayoutParams(dp(62f), dp(196f)))
        addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        dpad = dpad()
        addView(dpad, LinearLayout.LayoutParams(dp(184f), dp(184f)))
        addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        sideKeys = pill(listOf(
            Triple(muteIcon as View, "Mute") { channel?.let { c ->
                val id = c.mediaId ?: return@let
                val muted = HaRepository.cache?.get(id)?.attributes?.get("is_volume_muted") == true
                if (c.caps.exactMute) HaRepository.optimisticAttribute(listOf(id), "is_volume_muted", !muted)
                send(c.caps.mute(id, !muted))
            } },
            Triple(playIcon as View, "Play or pause") { press(MediaRemote.Button.PLAY) },
            Triple(GlassIcon(context, GlassIcon.Kind.BACK, ink, 2f) as View, "Back") { press(MediaRemote.Button.BACK) },
        ))
        addView(sideKeys, LinearLayout.LayoutParams(dp(62f), dp(196f)))
    }

    /** A tall rounded pill with three stacked keys (VOL rocker / side keys). */
    private fun pill(items: List<Triple<View, String, (() -> Unit)?>>): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        background = GradientDrawable().apply { cornerRadius = dp(31f).toFloat(); setColor(panelFill); setStroke(dp(1f), stroke) }
        setPadding(0, dp(6f), 0, dp(6f))
        items.forEachIndexed { i, (v, label, action) ->
            if (i > 0) addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
            if (action == null) { addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)); return@forEachIndexed }
            addView(FrameLayout(context).apply {
                contentDescription = label
                addView(v, LayoutParams(dp(22f), dp(22f), Gravity.CENTER))
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT) }
                tap { action() }
            }, LinearLayout.LayoutParams(dp(52f), dp(52f)))
        }
    }

    private fun dpad(): View = FrameLayout(context).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(panelFill); setStroke(dp(1f), Color.argb(64, 130, 170, 255)) }
        fun arrow(kind: GlassIcon.Kind, label: String, gravity: Int, b: MediaRemote.Button) = addView(FrameLayout(context).apply {
            contentDescription = label
            addView(GlassIcon(context, kind, dim, 2.4f), LayoutParams(dp(22f), dp(22f), Gravity.CENTER))
            tap { press(b) }
        }, LayoutParams(dp(56f), dp(56f), gravity))
        arrow(GlassIcon.Kind.CHEVRON_UP, "Up", Gravity.TOP or Gravity.CENTER_HORIZONTAL, MediaRemote.Button.UP)
        arrow(GlassIcon.Kind.CHEVRON_DOWN, "Down", Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, MediaRemote.Button.DOWN)
        arrow(GlassIcon.Kind.CHEVRON_LEFT, "Left", Gravity.START or Gravity.CENTER_VERTICAL, MediaRemote.Button.LEFT)
        arrow(GlassIcon.Kind.CHEVRON_RIGHT, "Right", Gravity.END or Gravity.CENTER_VERTICAL, MediaRemote.Button.RIGHT)
        addView(text(16f, Color.rgb(4, 16, 31), g.disp(context)).apply {
            text = "OK"; gravity = Gravity.CENTER; contentDescription = "OK"
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Glass.BLUE) }
            tap { press(MediaRemote.Button.OK) }
        }, LayoutParams(dp(70f), dp(70f), Gravity.CENTER))
    }

    private fun roomKey(kind: GlassIcon.Kind, label: String, action: () -> Unit): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        addView(GlassIcon(context, kind, dim, 2f), LinearLayout.LayoutParams(dp(24f), dp(24f)))
        addView(text(12.5f, dim, g.bodyBold(context)).apply { text = label; gravity = Gravity.CENTER; setPadding(0, dp(4f), 0, 0) })
        tap { action() }
    }

    private fun bindKey(key: LinearLayout, label: String, on: Boolean, accent: Int, enabled: Boolean) {
        val icon = key.getChildAt(0) as GlassIcon
        val tv = key.getChildAt(1) as TextView
        tv.text = label
        val c = if (on) ink else dim
        tv.setTextColor(c); icon.setColor(if (on) accent else dim)
        key.background = GradientDrawable().apply {
            cornerRadius = dp(18f).toFloat()
            setColor(if (on) Glass.withAlpha(accent, 28) else Color.argb(10, 255, 255, 255))
            setStroke(dp(1f), if (on) Glass.withAlpha(accent, 130) else Color.argb(46, 130, 170, 255))
        }
        key.alpha = if (enabled) 1f else 0.4f
        key.isEnabled = enabled
    }

    private fun circleKey(kind: GlassIcon.Kind, label: String, action: () -> Unit): View = FrameLayout(context).apply {
        contentDescription = label
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(dp(1f), Color.argb(64, 130, 170, 255)) }
        addView(GlassIcon(context, kind, dim, 2.2f), LayoutParams(dp(18f), dp(18f), Gravity.CENTER))
        tap { action() }
    }

    private fun bottomBar(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(barPill("House") { onHouse() }, LinearLayout.LayoutParams(dp(100f), dp(48f)))
        addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        addView(FrameLayout(context).apply {
            contentDescription = "Talk to Jarvis"
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(143, 212, 255), Glass.BLUE, Glass.INDIGO)).apply { shape = GradientDrawable.OVAL }
            addView(GlassIcon(context, GlassIcon.Kind.MIC, Color.rgb(4, 16, 31), 2.2f), LayoutParams(dp(26f), dp(26f), Gravity.CENTER))
            tap { onMic() }
        }, LinearLayout.LayoutParams(dp(68f), dp(68f)))
        addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        addView(barPill("More") { onMore() }, LinearLayout.LayoutParams(dp(100f), dp(48f)))
    }

    private fun barPill(label: String, action: () -> Unit): View = text(13f, dim, g.bodyBold(context)).apply {
        text = label; gravity = Gravity.CENTER
        background = GradientDrawable().apply { cornerRadius = dp(24f).toFloat(); setStroke(dp(1f), Color.argb(56, 130, 170, 255)) }
        tap { action() }
    }

    private fun enable(v: View, on: Boolean) { v.alpha = if (on) 1f else 0.35f; setTreeEnabled(v, on) }
    private fun setTreeEnabled(v: View, on: Boolean) {
        v.isEnabled = on
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) setTreeEnabled(v.getChildAt(i), on)
    }

    private fun text(size: Float, color: Int, tf: android.graphics.Typeface) = TextView(context).apply {
        textSize = size; setTextColor(color); typeface = tf; includeFontPadding = false
    }

    companion object {
        /** Survives view rebuilds (config reload) within the app process. */
        @Volatile var rememberedRoomId: String? = null
    }
}

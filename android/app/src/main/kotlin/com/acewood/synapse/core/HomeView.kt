package com.acewood.synapse.core

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextClock
import android.widget.TextView
import com.acewood.synapse.logic.Entity
import com.acewood.synapse.logic.EntityCache
import com.acewood.synapse.logic.NodeConfig
import com.acewood.synapse.logic.NowPlaying
import com.acewood.synapse.logic.Profile
import com.acewood.synapse.logic.Room
import com.acewood.synapse.logic.RoomOrder
import com.acewood.synapse.logic.MediaRemote
import com.acewood.synapse.logic.LightingFx
import java.util.Calendar

/**
 * The native Synapse Glass home screen — "a remote for the house". Live from HaRepository:
 * status strip (weather · clock · HA · profile), hero (greeting, summary, now playing), house modes,
 * room channels, scenes, house status + sensors, an app dock, and the thumb console
 * (Home · Apps · mic · Jarvis · All off). Only rebuilds when something it shows actually changed,
 * which keeps taps snappy on the GE8320.
 */
@SuppressLint("ViewConstructor")
class HomeView(
    context: Context,
    private val cfg: NodeConfig,
    private val onOpenRoom: (Room) -> Unit,
    private val onMic: () -> Unit,
    private val onHome: () -> Unit,
    private val onRooms: () -> Unit = {},
    private val onAudio: () -> Unit = {},
    /** Jarvis kill switch tapped: true = Jarvis is currently live (so kill it), false = it's off (ask to restore). */
    private val onJarvisSwitch: (Boolean) -> Unit = {},
    private val onHa: () -> Unit = {},
    private val onApps: () -> Unit = {},
    private val onSensors: () -> Unit = {},
    private val onLaunch: (AppCatalog.App) -> Unit = {},
    private val onSwitchProfile: () -> Unit = {},
    private val onIntercom: () -> Unit = {},
) : FrameLayout(context) {
    private val pillInitial = TextView(context)
    private val pillRole = TextView(context)
    private var profileName: String = cfg.ownerName.trim()
    private var profileRoomIds: Set<String> = emptySet()
    private var activeProfile: Profile? = null

    /** Show who's using the remote (initial + role) and greet them by name. */
    fun setProfile(profile: Profile) {
        activeProfile = profile
        setProfile(profile.name, profile.role.name.lowercase(), profile.layout.rooms, profile.layout.tileScale)
    }

    fun setProfile(name: String, role: String, rooms: List<String> = emptyList(), tileScale: Float = 1f) {
        profileName = name.trim()
        profileRoomIds = rooms.map { it.trim().lowercase().replace(' ', '_') }.filter { it.isNotEmpty() }.toSet()
        pillInitial.text = (profileName.firstOrNull() ?: 'S').uppercaseChar().toString()
        pillRole.text = role.uppercase()
        // tileScale is kept in the profile but not applied yet: scaling the whole HomeView crops the
        // edges on a 360 dp screen. It needs per-tile sizing (follow-up).
        buildDock()
        lastSig = null; refresh()
    }

    private val g = Glass
    private val greeting = tv(23f, Glass.INK, g.disp(context))
    private val summary = tv(12.5f, Glass.INK_DIM, g.light(context))
    private val nowPlaying = tv(12f, Glass.MINT, g.body(context)).apply { maxLines = 1; visibility = View.GONE }
    private val nowPlayingCard = g.col(context).apply { visibility = View.GONE }
    private val timerCard = TimerCardView(context)
    private val weatherChip = tv(12f, Glass.AMBER, g.disp(context)).apply { visibility = View.GONE }
    private val jarvisChip = tv(11f, Glass.MINT, g.disp(context)).apply {
        letterSpacing = 0.1f; gravity = Gravity.CENTER
        setPadding(g.dp(context, 10f), g.dp(context, 5f), g.dp(context, 10f), g.dp(context, 5f))
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { leftMargin = g.dp(context, 10f) }
        tap { onJarvisSwitch(jarvisLive(HaRepository.cache)) }
    }

    private val modesRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val fxWrap = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private lateinit var fxHeader: View
    private val modesWrap = g.col(context)
    private val roomsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private lateinit var roomsHeader: View
    private val scenesWrap = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val houseWrap = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val dockWrap = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private lateinit var homeScroll: ScrollView
    private val hintLabel = tv(11.5f, Glass.INK_FAINT, g.body(context)).apply { gravity = Gravity.CENTER }
    private var activeRoomId: String? = null
    private var lastSig: String? = null

    /** House-wide status lights shown as pills (missing ones are skipped). */
    private val housePills = listOf(
        "binary_sensor.internet" to "Internet", "binary_sensor.xfinity_router" to "Router",
        "binary_sensor.someone_home" to "Someone home", "binary_sensor.dining_pc_online" to "Dining PC",
        "binary_sensor.ps5_power" to "PS5", "binary_sensor.raspberry_pi_power_status" to "Pi power",
    )

    /** House modes (HA input_booleans). Game mode runs its scripts when they exist. */
    private val modes = listOf(
        Triple("input_boolean.away_mode", "Away", Glass.BLUE), Triple("input_boolean.sleep_mode", "Sleep", Glass.VIOLET),
        Triple("input_boolean.movie_mode", "Movie", Glass.INDIGO), Triple("input_boolean.quiet_mode", "Quiet", Glass.MINT),
        Triple("input_boolean.guest_mode", "Guest", Glass.AMBER), Triple("input_boolean.party_mode", "Party", Glass.RED),
        Triple("input_boolean.game_mode", "Game", Glass.MINT),
    )

    init {
        background = g.ground()
        val pad = g.dp(context, 16f)
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, g.dp(context, 14f), pad, g.dp(context, 12f))
        }
        addView(col, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        col.addView(statusStrip())
        col.addView(g.spacer(context, h = 10))

        val body = g.col(context)
        homeScroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(body) }
        col.addView(homeScroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        body.addView(hero())
        body.addView(g.spacer(context, h = 12))
        body.addView(timerCard)
        body.addView(g.spacer(context, h = 12))
        modesWrap.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(modesRow) })
        modesWrap.addView(g.spacer(context, h = 12))
        body.addView(modesWrap)
        fxHeader = g.label(context, "LIGHT FX")
        body.addView(fxHeader)
        body.addView(fxWrap)
        body.addView(g.spacer(context, h = 14))
        roomsHeader = g.label(context, "ROOM CHANNELS")
        body.addView(roomsHeader)
        body.addView(g.spacer(context, h = 8))
        body.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(roomsRow) })
        body.addView(g.spacer(context, h = 14))
        body.addView(g.label(context, "SCENES"))
        body.addView(scenesWrap)
        body.addView(g.spacer(context, h = 14))
        body.addView(sectionHeader("HOUSE", "Sensors ›") { onSensors() })
        body.addView(houseWrap)
        body.addView(g.spacer(context, h = 14))
        body.addView(sectionHeader("APPS", "All apps ›") { onApps() })
        body.addView(g.spacer(context, h = 10))
        body.addView(dockWrap)
        body.addView(g.spacer(context, h = 10))

        col.addView(hintLabel)
        col.addView(g.spacer(context, h = 6))
        col.addView(console())
        buildDock()
    }

    /** The persistent Home key is also useful while already home: return to the dashboard top. */
    fun scrollToTop() { if (::homeScroll.isInitialized) homeScroll.smoothScrollTo(0, 0) }
    /** Navigate to the room-channel section without opening a specific room. */
    fun scrollToRooms() { if (::homeScroll.isInitialized) homeScroll.post { homeScroll.smoothScrollTo(0, roomsHeader.top) } }

    private fun tv(size: Float, color: Int, tf: Typeface) = TextView(context).apply {
        textSize = size; setTextColor(color); typeface = tf
    }

    private fun sectionHeader(label: String, action: String, onTap: () -> Unit): View = g.row(context).apply {
        addView(g.label(context, label), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(tv(12.5f, Glass.BLUE, g.disp(context)).apply {
            text = action; setPadding(g.dp(context, 12f), g.dp(context, 6f), 0, g.dp(context, 6f)); tap { onTap() }
        })
    }

    private fun statusStrip(): View = g.row(context).apply {
        addView(View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Glass.MINT) }
            layoutParams = LinearLayout.LayoutParams(g.dp(context, 7f), g.dp(context, 7f)).apply { rightMargin = g.dp(context, 8f) }
        })
        addView(tv(12f, Glass.INK_DIM, g.disp(context)).apply { text = "SYNAPSE"; letterSpacing = 0.15f })
        addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
        addView(weatherChip.apply { setPadding(0, 0, g.dp(context, 10f), 0) })
        addView(TextClock(context).apply { format12Hour = "h:mm a"; format24Hour = "H:mm"; setTextColor(Glass.INK_DIM); textSize = 12f })
        addView(jarvisChip)
        addView(haChip())
        addView(profilePill())
    }

    /** Jarvis kill switch: the HA helpers that let Jarvis listen and act. */
    companion object {
        val JARVIS_SWITCHES = listOf("input_boolean.jarvis_voice", "input_boolean.dining_pc_jarvis_control", "input_boolean.dining_pc_jarvis_power")
        fun jarvisLive(cache: EntityCache?): Boolean = JARVIS_SWITCHES.any { cache?.get(it)?.state == "on" }
    }

    private fun bindJarvisChip(cache: EntityCache?) {
        val live = jarvisLive(cache)
        jarvisChip.text = if (live) "JARVIS ●" else "JARVIS OFF"
        jarvisChip.setTextColor(if (live) Glass.MINT else Glass.RED)
        jarvisChip.background = g.tile(context, if (live) Glass.MINT else Glass.RED, !live, 999f)
    }

    private fun haChip(): View = tv(11f, Glass.BLUE, g.disp(context)).apply {
        text = "HA"; letterSpacing = 0.12f; gravity = Gravity.CENTER
        background = g.tile(context, Glass.BLUE, false, 999f)
        setPadding(g.dp(context, 12f), g.dp(context, 5f), g.dp(context, 12f), g.dp(context, 5f))
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { leftMargin = g.dp(context, 10f) }
        tap { onHa() }
    }

    private fun profilePill(): View = g.row(context).apply {
        background = g.panel(context, 999f, Color.argb(40, 73, 182, 255))
        setPadding(g.dp(context, 5f), g.dp(context, 4f), g.dp(context, 11f), g.dp(context, 4f))
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { leftMargin = g.dp(context, 10f) }
        val initial = (cfg.ownerName.trim().firstOrNull() ?: 'S').uppercaseChar()
        addView(pillInitial.apply {
            text = initial.toString(); textSize = 10f; setTextColor(Color.parseColor("#04101f")); typeface = g.disp(context)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Glass.BLUE) }
            val s = g.dp(context, 20f); layoutParams = LinearLayout.LayoutParams(s, s).apply { rightMargin = g.dp(context, 7f) }
        })
        addView(pillRole.apply { text = "ADMIN"; textSize = 11f; setTextColor(Glass.INK); typeface = g.body(context); letterSpacing = 0.1f })
        tap { onSwitchProfile() }
    }

    private fun hero(): View = g.row(context).apply {
        background = g.panel(context, 20f)
        setPadding(g.dp(context, 16f), g.dp(context, 15f), g.dp(context, 16f), g.dp(context, 15f))
        addView(TextView(context).apply {
            text = "◉"; textSize = 26f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Glass.BLUE, Glass.INDIGO)).apply { shape = GradientDrawable.OVAL }
            val s = g.dp(context, 56f); layoutParams = LinearLayout.LayoutParams(s, s).apply { rightMargin = g.dp(context, 14f) }
        })
        addView(g.col(context).apply { addView(greeting); addView(summary); addView(nowPlaying); addView(nowPlayingCard) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun console(): View = g.row(context).apply {
        background = g.panel(context, 28f)
        gravity = Gravity.CENTER
        setPadding(g.dp(context, 10f), g.dp(context, 10f), g.dp(context, 10f), g.dp(context, 10f))
        addView(key("HOME", Glass.INK_DIM) { onHome() })
        addView(g.spacer(context, w = 10))
        addView(key("ROOMS", Glass.BLUE) { onRooms() })
        addView(g.spacer(context, w = 10))
        addView(micOrb())
        addView(g.spacer(context, w = 10))
        addView(key("AUDIO", Glass.VIOLET) { onAudio() })
        addView(g.spacer(context, w = 10))
        addView(key("MORE", Glass.AMBER) { onApps() })
    }

    private fun key(text: String, color: Int, onTap: () -> Unit): View = g.col(context).apply {
        gravity = Gravity.CENTER
        background = g.tile(context, color, false, 18f)
        val s = g.dp(context, 54f); layoutParams = LinearLayout.LayoutParams(s, s)
        addView(tv(9.5f, color, g.disp(context)).apply { setText(text); gravity = Gravity.CENTER; letterSpacing = 0.1f })
        tap { onTap() }
    }

    private fun micOrb(): View = TextView(context).apply {
        text = "●"; textSize = 28f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Glass.BLUE, Glass.INDIGO, Color.parseColor("#45227a"))).apply { shape = GradientDrawable.OVAL }
        val s = g.dp(context, 74f); layoutParams = LinearLayout.LayoutParams(s, s)
        tap { hintLabel.text = "Listening…"; onMic() }
    }

    // ---------- live binding ----------

    fun refresh() {
        post {
            val cache = HaRepository.cache
            val sig = signature(cache)
            if (sig == lastSig) return@post
            lastSig = sig
            greeting.text = greetingText()
            summary.text = summaryText(cache)
            bindNowPlaying(cache)
            timerCard.refresh(cache)
            bindWeather(cache)
            bindJarvisChip(cache)
            buildModes(cache)
            buildLightingFx(cache)
            buildRooms(cache)
            buildScenes(cache)
            buildHouse(cache)
            val cur = hintLabel.text?.toString() ?: ""
            if (cur.isBlank() || cur.startsWith("Listening") || cur == "All off") hintLabel.text = "Tap a room, or press the mic for Jarvis"
        }
    }

    /** Everything the home screen shows, as one string. If it didn't change, skip the rebuild. */
    private fun signature(cache: EntityCache?): String = buildString {
        append(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)).append(HaRepository.connected).append(HaRepository.rooms.size)
        if (cache == null) return@buildString
        cache.byDomain("light").forEach { append(it.entityId).append(it.state) }
        cache.byDomain("media_player").forEach { append(it.entityId).append(it.state).append(it.attributes["media_title"]).append(it.attributes["app_name"]).append(it.attributes["entity_picture"]).append(it.attributes["volume_level"]) }
        cache.byDomain("scene").forEach { append(it.entityId) }
        cache.byDomain("weather").firstOrNull()?.let { append(it.state).append(it.attributes["temperature"]) }
        cache.byDomain("timer").forEach { append(it.entityId).append(it.state).append(it.attributes["remaining"]).append(it.attributes["duration"]) }
        modes.forEach { append(cache.get(it.first)?.state) }
        JARVIS_SWITCHES.forEach { append(cache.get(it)?.state) }
        LightingFx.fxCandidates.forEach { (id, _) -> append(cache.get(id)?.friendlyName).append(cache.get(id)?.state) }
        LightingFx.holiday(cache)?.let { (id, options) -> append(cache.get(id)?.state).append(options) }
        append(LightingFx.holidayApplyId(cache))
        housePills.forEach { append(cache.get(it.first)?.state) }
    }

    private fun greetingText(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val part = when { h < 5 -> "Good night"; h < 12 -> "Good morning"; h < 18 -> "Good afternoon"; else -> "Good evening" }
        val who = profileName
        return if (who.isNotEmpty()) "$part, $who" else part
    }

    private fun summaryText(cache: EntityCache?): String {
        if (cache == null || cache.size() == 0) return if (HaRepository.connected) "Connecting…" else "Home Assistant offline"
        val lightsOn = cache.byDomain("light").count { it.on && !it.entityId.endsWith("_listening_light") }
        val media = cache.byDomain("media_player").count { it.state == "playing" }
        return buildString {
            append(if (HaRepository.connected) "Home" else "Offline")
            append(" · $lightsOn ${if (lightsOn == 1) "light" else "lights"} on")
            if (media > 0) append(" · $media playing")
        }
    }

    private fun bindNowPlaying(cache: EntityCache?) {
        val p = NowPlaying.first(cache)
        if (p == null) { nowPlaying.visibility = View.GONE; nowPlayingCard.visibility = View.GONE; return }
        nowPlaying.text = "▶  ${p.title}  ·  ${cache?.get(p.entityId)?.friendlyName ?: "Media"}"
        nowPlaying.visibility = View.VISIBLE
        nowPlayingCard.removeAllViews()
        nowPlayingCard.background = g.panel(context, 16f)
        nowPlayingCard.setPadding(g.dp(context, 10f), g.dp(context, 9f), g.dp(context, 10f), g.dp(context, 9f))
        val art = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = g.tile(context, Glass.VIOLET, false, 12f)
            layoutParams = LinearLayout.LayoutParams(g.dp(context, 58f), g.dp(context, 58f)).apply { rightMargin = g.dp(context, 10f) }
        }
        AlbumArtLoader.load(art, p.albumArt)
        nowPlayingCard.addView(g.row(context).apply {
            addView(art)
            addView(g.col(context).apply {
                addView(tv(13f, Glass.INK, g.body(context)).apply { text = p.title; maxLines = 1 })
                addView(tv(10.5f, Glass.INK_DIM, g.body(context)).apply { text = p.appName ?: cache?.get(p.entityId)?.friendlyName ?: "Media"; maxLines = 1 })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        })
        nowPlayingCard.addView(g.row(context).apply {
            gravity = Gravity.CENTER
            addView(nowKey("⏮") { sendMedia(MediaRemote.previous(p.entityId)) })
            addView(nowKey(if (p.isPlaying) "⏸" else "▶", Glass.MINT) { sendMedia(MediaRemote.playPause(p.entityId)) })
            addView(nowKey("⏭") { sendMedia(MediaRemote.next(p.entityId)) })
        })
        p.volumePct?.let { volume ->
            nowPlayingCard.addView(g.row(context).apply {
                addView(tv(10f, Glass.INK_FAINT, g.disp(context)).apply { text = "VOL" })
                addView(SeekBar(context).apply {
                    max = 100; progress = volume; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(s: SeekBar, value: Int, fromUser: Boolean) {}
                        override fun onStartTrackingTouch(s: SeekBar) {}
                        override fun onStopTrackingTouch(s: SeekBar) {
                            HaRepository.optimisticAttribute(listOf(p.entityId), "volume_level", s.progress / 100.0)
                            HaRepository.callService("media_player", "volume_set", listOf(p.entityId), mapOf("volume_level" to s.progress / 100.0))
                        }
                    })
                })
            })
        }
        HaRepository.rooms.firstOrNull { it.media.contains(p.entityId) }?.let { room ->
            nowPlayingCard.addView(tv(11f, Glass.BLUE, g.disp(context)).apply {
                text = "TV REMOTE  ·  ${room.name}"; gravity = Gravity.CENTER
                setPadding(0, g.dp(context, 7f), 0, g.dp(context, 3f)); tap { onOpenRoom(room) }
            })
        }
        nowPlayingCard.visibility = View.VISIBLE
    }

    private fun nowKey(label: String, accent: Int = Glass.INK_DIM, action: () -> Unit): View =
        tv(13f, accent, g.disp(context)).apply {
            text = label; gravity = Gravity.CENTER; setPadding(g.dp(context, 13f), g.dp(context, 7f), g.dp(context, 13f), g.dp(context, 7f)); tap { action() }
        }

    private fun sendMedia(call: com.acewood.synapse.logic.HaCall) {
        HaRepository.callService(call.domain, call.service, listOf(call.entityId), call.data)
    }

    private fun bindWeather(cache: EntityCache?) {
        val w = cache?.byDomain("weather")?.firstOrNull()
        if (w == null) { weatherChip.visibility = View.GONE; return }
        val icon = when (w.state) {
            "sunny" -> "☀"; "clear-night" -> "☾"; "partlycloudy" -> "⛅"; "cloudy" -> "☁"
            "rainy", "pouring" -> "☂"; "lightning", "lightning-rainy" -> "⚡"; "snowy", "snowy-rainy" -> "❄"; "fog" -> "≋"
            "windy", "windy-variant" -> "≈"; else -> "◌"
        }
        val t = w.attrDouble("temperature")
        weatherChip.text = icon + (t?.let { "  ${Math.round(it)}°" } ?: "")
        weatherChip.visibility = View.VISIBLE
    }

    private fun buildModes(cache: EntityCache?) {
        modesRow.removeAllViews()
        if (cache == null) { modesWrap.visibility = View.GONE; return }
        val present = modes.filter { cache.get(it.first) != null }
        modesWrap.visibility = if (present.isEmpty()) View.GONE else View.VISIBLE
        present.forEach { (id, label, accent) ->
            val on = cache.get(id)?.state == "on"
            modesRow.addView(tv(13f, if (on) Glass.INK else Glass.INK_DIM, g.disp(context)).apply {
                text = (if (on) "● " else "") + label; letterSpacing = 0.05f; gravity = Gravity.CENTER
                background = g.tile(context, accent, on, 999f)
                setPadding(g.dp(context, 16f), g.dp(context, 9f), g.dp(context, 16f), g.dp(context, 9f))
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = g.dp(context, 8f) }
                tap { toggleMode(id, on, cache) }
            })
        }
    }

    private fun toggleMode(id: String, on: Boolean, cache: EntityCache) {
        if (id == "input_boolean.game_mode") {
            val script = if (on) "script.game_mode_off" else "script.game_mode_on"
            if (cache.get(script) != null) { HaRepository.callService("script", "turn_on", listOf(script)); return }
        }
        HaRepository.toggle(listOf(id), !on)
    }

    private fun buildLightingFx(cache: EntityCache?) {
        fxWrap.removeAllViews()
        if (cache == null) { fxWrap.visibility = View.GONE; fxHeader.visibility = View.GONE; return }
        val fx = LightingFx.available(cache)
        val holiday = LightingFx.holiday(cache)
        val apply = LightingFx.holidayApplyId(cache)
        if (fx.isEmpty() && holiday == null) { fxWrap.visibility = View.GONE; fxHeader.visibility = View.GONE; return }
        fxWrap.visibility = View.VISIBLE; fxHeader.visibility = View.VISIBLE
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fx.forEach { (id, fallback) ->
            val entity = cache.get(id)
            val label = prettyName(entity?.friendlyName?.takeIf { it.isNotBlank() } ?: fallback)
            row.addView(fxKey(label, if (id.endsWith("stop")) Glass.RED else Glass.VIOLET) {
                if (cache.get(id) != null) HaRepository.callService("script", "turn_on", listOf(id))
            }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = g.dp(context, 8f) })
        }
        if (row.childCount > 0) fxWrap.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        if (holiday != null) {
            val (selectId, options) = holiday
            val selected = cache.get(selectId)?.state?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Holiday mode"
            fxWrap.addView(g.row(context).apply {
                addView(tv(12f, Glass.INK_DIM, g.body(context)).apply { text = "HOLIDAY  $selected" }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(fxKey("CHOOSE", Glass.AMBER) {
                    if (options.isNotEmpty()) AlertDialog.Builder(context).setTitle("Holiday mode").setItems(options.toTypedArray()) { _, which ->
                        val choice = options[which]
                        HaRepository.callService("input_select", "select_option", listOf(selectId), mapOf("option" to choice))
                        // Let the select land in HA before the apply script reads it (same race as intercom).
                        if (apply != null && cache.get(apply) != null) postDelayed({ HaRepository.callService("script", "turn_on", listOf(apply)) }, 500)
                    }.show()
                })
            })
        }
    }

    private fun fxKey(label: String, color: Int, action: () -> Unit): View = tv(11f, color, g.disp(context)).apply {
        text = label; gravity = Gravity.CENTER; letterSpacing = .04f; background = g.tile(context, color, false, 999f)
        setPadding(g.dp(context, 13f), g.dp(context, 8f), g.dp(context, 13f), g.dp(context, 8f)); tap { action() }
    }

    private fun buildRooms(cache: EntityCache?) {
        roomsRow.removeAllViews()
        val visible = if (profileRoomIds.isEmpty()) HaRepository.rooms else
            HaRepository.rooms.filter { it.id.lowercase() in profileRoomIds }
        val rooms = if (cache != null) RoomOrder.order(visible, cfg.room.lowercase().replace(' ', '_'), cache,
            Calendar.getInstance().get(Calendar.HOUR_OF_DAY), cfg.roomOrder) else visible
        if (activeRoomId == null) activeRoomId = rooms.firstOrNull()?.id
        rooms.forEachIndexed { i, r ->
            val on = cache != null && (r.lights.any { cache.get(it)?.on == true && !it.endsWith("_listening_light") } ||
                r.media.any { cache.get(it)?.state == "playing" })
            roomsRow.addView(g.col(context).apply {
                background = g.tile(context, Glass.BLUE, r.id == activeRoomId || on, 16f)
                setPadding(g.dp(context, 13f), g.dp(context, 11f), g.dp(context, 13f), g.dp(context, 11f))
                layoutParams = LinearLayout.LayoutParams(g.dp(context, 108f), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = g.dp(context, 9f)
                }
                addView(tv(11f, Glass.BLUE, g.disp(context)).apply { text = "CH ${i + 1}"; letterSpacing = 0.1f })
                addView(tv(14f, Glass.INK, g.body(context)).apply { text = r.name; setTypeface(typeface, Typeface.BOLD); maxLines = 1 })
                addView(tv(10.5f, Glass.INK_FAINT, g.body(context)).apply { text = roomSub(r, cache) })
                tap { activeRoomId = r.id; onOpenRoom(r) }
            })
        }
        if (rooms.isEmpty()) roomsRow.addView(tv(12f, Glass.INK_FAINT, g.body(context)).apply {
            text = if (HaRepository.connected) "Loading rooms from Home Assistant…" else "Waiting for Home Assistant…"
        })
    }

    private fun roomSub(r: Room, cache: EntityCache?): String {
        if (cache == null) return "${r.lights.size} lights"
        val on = r.lights.count { cache.get(it)?.on == true && !it.endsWith("_listening_light") }
        val tv = r.media.any { cache.get(it)?.state == "playing" }
        return when { on > 0 && tv -> "$on on · TV"; on > 0 -> "$on on"; tv -> "TV"; else -> "all off" }
    }

    private fun buildScenes(cache: EntityCache?) {
        scenesWrap.removeAllViews()
        val scenes = cache?.byDomain("scene")?.take(8) ?: emptyList()
        val accents = intArrayOf(Glass.AMBER, Glass.VIOLET, Glass.BLUE, Glass.MINT, Glass.INDIGO, Glass.RED)
        var rowView: LinearLayout? = null
        scenes.forEachIndexed { i, s ->
            if (i % 2 == 0) { rowView = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }; scenesWrap.addView(rowView) }
            val accent = accents[i % accents.size]
            val pad = g.col(context).apply {
                background = g.tile(context, accent, false, 15f)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(g.dp(context, 14f), g.dp(context, 10f), g.dp(context, 14f), g.dp(context, 10f))
                layoutParams = LinearLayout.LayoutParams(0, g.dp(context, 56f), 1f).apply {
                    if (i % 2 == 1) leftMargin = g.dp(context, 9f); topMargin = g.dp(context, 9f)
                }
                addView(tv(15f, Glass.INK, g.disp(context)).apply { text = prettyName(s.friendlyName); maxLines = 1 })
            }
            pad.tap { HaRepository.callService("scene", "turn_on", listOf(s.entityId)); flash(pad, accent) }
            rowView?.addView(pad)
        }
        if (scenes.size % 2 == 1) rowView?.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = g.dp(context, 9f) })
        if (scenes.isEmpty()) scenesWrap.addView(tv(12f, Glass.INK_FAINT, g.body(context)).apply { text = "No scenes in Home Assistant yet" })
    }

    private fun buildHouse(cache: EntityCache?) {
        houseWrap.removeAllViews()
        val pills = housePills.mapNotNull { (id, label) -> cache?.get(id)?.let { it to label } }
        if (pills.isEmpty()) {
            houseWrap.addView(tv(12f, Glass.INK_FAINT, g.body(context)).apply { text = "Open Sensors to see everything the house knows" })
            return
        }
        var row: LinearLayout? = null
        pills.forEachIndexed { i, (e, label) ->
            if (i % 3 == 0) { row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }; houseWrap.addView(row) }
            val (word, good) = houseWord(e)
            val accent = if (good) Glass.MINT else Glass.RED
            row?.addView(g.col(context).apply {
                background = g.tile(context, accent, true, 14f)
                setPadding(g.dp(context, 11f), g.dp(context, 8f), g.dp(context, 11f), g.dp(context, 8f))
                addView(tv(10.5f, Glass.INK_DIM, g.body(context)).apply { text = label; maxLines = 1 })
                addView(tv(13f, accent, g.disp(context)).apply { text = word })
                tap { onSensors() }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i % 3 != 0) leftMargin = g.dp(context, 8f); topMargin = g.dp(context, 8f)
            })
        }
        val rem = pills.size % 3
        if (rem != 0) repeat(3 - rem) { row?.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = g.dp(context, 8f) }) }
    }

    /** (word, isGood) for a house status pill. */
    private fun houseWord(e: Entity): Pair<String, Boolean> {
        val on = e.state == "on"
        if (e.state == "unavailable" || e.state == "unknown") return "—" to false
        return when (e.attributes["device_class"] as? String) {
            "connectivity" -> (if (on) "Online" else "Offline") to on
            "presence" -> (if (on) "Yes" else "No") to true
            "problem" -> (if (on) "Problem" else "OK") to !on
            else -> (if (on) "On" else "Off") to true
        }
    }

    private fun buildDock() {
        dockWrap.removeAllViews()
        val apps = AppCatalog.dock(context, activeProfile)
        var row: LinearLayout? = null
        apps.forEachIndexed { i, a ->
            if (i % 3 == 0) { row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }; dockWrap.addView(row) }
            row?.addView(AppDrawerView.appCell(context, a, 54f) { onLaunch(a) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { bottomMargin = g.dp(context, 14f) })
        }
        val rem = apps.size % 3
        if (rem != 0) repeat(3 - rem) { row?.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)) }
    }

    /** "night_hold" -> "Night Hold" when HA only has the raw id as the name. */
    private fun prettyName(n: String): String =
        if (n.contains('_') && !n.contains(' ')) n.split('_').filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) } else n

    private fun flash(v: View, accent: Int) {
        v.background = g.tile(context, accent, true, 15f)
        postDelayed({ v.background = g.tile(context, accent, false, 15f) }, 900)
    }

    fun allOffNow() {
        hintLabel.text = "All off"
        val cache = HaRepository.cache ?: return
        val lights = cache.byDomain("light").filter { it.on && !it.entityId.endsWith("_listening_light") }.map { it.entityId }
        if (lights.isNotEmpty()) HaRepository.callService("light", "turn_off", lights)
    }

    private fun allOff() = allOffNow()
}

package com.acewood.synapse.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import com.acewood.synapse.logic.Entity
import com.acewood.synapse.logic.EntityCache
import com.acewood.synapse.logic.NodeConfig
import com.acewood.synapse.logic.ResilientLight
import com.acewood.synapse.logic.Room
import com.acewood.synapse.logic.RoomOrder
import java.util.Calendar

/**
 * The native Synapse Glass home screen — "a remote for the house". Renders live from HaRepository:
 * greeting hero, room channels (smart-ordered), scene pads, and the thumb console (Home · mic · All off).
 * Built in Views for speed on the GE8320. Call [refresh] on HA updates.
 */
@SuppressLint("ViewConstructor")
class HomeView(
    context: Context,
    private val cfg: NodeConfig,
    private val onOpenRoom: (Room) -> Unit,
    private val onMic: () -> Unit,
    private val onHome: () -> Unit,
) : FrameLayout(context) {

    private val g = Glass
    private val greeting = tv(23f, Glass.INK, g.disp(context))
    private val summary = tv(12.5f, Glass.INK_DIM, g.light(context))
    private val roomsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val scenesWrap = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val hintLabel = tv(11.5f, Glass.INK_FAINT, g.body(context)).apply { gravity = Gravity.CENTER }
    private var activeRoomId: String? = null

    init {
        background = g.ground()
        val pad = g.dp(context, 16f)
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, g.dp(context, 14f), pad, g.dp(context, 14f))
        }
        addView(col, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // status strip
        col.addView(statusStrip())
        col.addView(g.spacer(context, h = 12))
        // hero
        col.addView(hero())
        col.addView(g.spacer(context, h = 14))
        // rooms
        col.addView(g.label(context, "ROOM CHANNELS"))
        col.addView(g.spacer(context, h = 8))
        col.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false; addView(roomsRow)
        })
        col.addView(g.spacer(context, h = 14))
        // scenes
        col.addView(g.label(context, "SCENES"))
        col.addView(g.spacer(context, h = 8))
        col.addView(scenesWrap)
        // push console to bottom
        col.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
        col.addView(hintLabel)
        col.addView(g.spacer(context, h = 6))
        col.addView(console())
    }

    private fun tv(size: Float, color: Int, tf: Typeface) = TextView(context).apply {
        textSize = size; setTextColor(color); typeface = tf
    }

    private fun statusStrip(): View = g.row(context).apply {
        val dot = View(context).apply {
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Glass.MINT) }
            layoutParams = LinearLayout.LayoutParams(g.dp(context, 7f), g.dp(context, 7f)).apply { rightMargin = g.dp(context, 8f) }
        }
        addView(dot)
        addView(tv(12f, Glass.INK_DIM, g.disp(context)).apply { text = "SYNAPSE · HOME"; letterSpacing = 0.15f })
        addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
        addView(TextClock(context).apply { format12Hour = "h:mm a"; format24Hour = "H:mm"; setTextColor(Glass.INK_DIM); textSize = 12f })
        addView(profilePill())
    }

    private fun profilePill(): View = g.row(context).apply {
        background = g.panel(context, 999f, Color.argb(40, 73, 182, 255))
        setPadding(g.dp(context, 5f), g.dp(context, 4f), g.dp(context, 11f), g.dp(context, 4f))
        (layoutParams ?: LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)).also {
            layoutParams = (it as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(-2, -2)).apply { leftMargin = g.dp(context, 10f) }
        }
        val initial = (cfg.ownerName.trim().firstOrNull() ?: 'S').uppercaseChar()
        addView(TextView(context).apply {
            text = initial.toString(); textSize = 10f; setTextColor(Color.parseColor("#04101f")); typeface = g.disp(context)
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Glass.BLUE) }
            val s = g.dp(context, 20f); layoutParams = LinearLayout.LayoutParams(s, s).apply { rightMargin = g.dp(context, 7f) }
        })
        addView(tv(11f, Glass.INK, g.body(context)).apply { text = "ADMIN"; letterSpacing = 0.1f })
    }

    private fun hero(): View = g.row(context).apply {
        background = g.panel(context, 20f)
        setPadding(g.dp(context, 16f), g.dp(context, 15f), g.dp(context, 16f), g.dp(context, 15f))
        val orb = TextView(context).apply {
            text = "◉"; textSize = 26f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Glass.BLUE, Glass.INDIGO)).apply { shape = android.graphics.drawable.GradientDrawable.OVAL }
            val s = g.dp(context, 56f); layoutParams = LinearLayout.LayoutParams(s, s).apply { rightMargin = g.dp(context, 14f) }
        }
        addView(orb)
        addView(g.col(context).apply {
            addView(greeting); addView(summary)
        })
    }

    private fun console(): View = g.row(context).apply {
        background = g.panel(context, 28f)
        gravity = Gravity.CENTER
        setPadding(g.dp(context, 12f), g.dp(context, 12f), g.dp(context, 12f), g.dp(context, 12f))
        addView(key("HOME", Glass.INK_DIM) { onHome() })
        addView(g.spacer(context, w = 14))
        addView(micOrb())
        addView(g.spacer(context, w = 14))
        addView(key("ALL OFF", Glass.RED) { allOff() })
    }

    private fun key(text: String, color: Int, onTap: () -> Unit): View = g.col(context).apply {
        gravity = Gravity.CENTER
        background = g.tile(context, color, false, 18f)
        val s = g.dp(context, 60f); layoutParams = LinearLayout.LayoutParams(s, s)
        addView(tv(10f, color, g.disp(context)).apply { setText(text); gravity = Gravity.CENTER; letterSpacing = 0.12f })
        setOnClickListener { haptic(); onTap() }
    }

    private fun micOrb(): View = TextView(context).apply {
        text = "●"; textSize = 30f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Glass.BLUE, Glass.INDIGO, Color.parseColor("#45227a"))).apply { shape = android.graphics.drawable.GradientDrawable.OVAL }
        val s = g.dp(context, 86f); layoutParams = LinearLayout.LayoutParams(s, s)
        setOnClickListener { haptic(); hintLabel.setText("Listening\u2026"); onMic() }
    }

    private fun haptic() = try { performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY) } catch (_: Exception) {}

    // ---------- live binding ----------

    fun refresh() {
        post {
            greeting.text = greetingText()
            val cache = HaRepository.cache
            summary.text = summaryText(cache)
            buildRooms(cache)
            buildScenes(cache)
            val cur = hintLabel.text?.toString() ?: ""
            if (cur.isBlank() || cur.startsWith("Listening")) hintLabel.setText("Tap a room, or hold the mic for Jarvis")
        }
    }

    private fun greetingText(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val part = when { h < 5 -> "Good night"; h < 12 -> "Good morning"; h < 18 -> "Good afternoon"; else -> "Good evening" }
        val who = cfg.ownerName.trim()
        return if (who.isNotEmpty()) "$part, $who" else part
    }

    private fun summaryText(cache: EntityCache?): String {
        if (cache == null || cache.size() == 0) return if (HaRepository.connected) "Connecting…" else "Home Assistant offline"
        val lightsOn = cache.byDomain("light").count { it.on }
        val media = cache.byDomain("media_player").count { it.state == "playing" }
        return buildString {
            append(if (HaRepository.connected) "Home" else "Offline")
            append(" · $lightsOn ${if (lightsOn == 1) "light" else "lights"} on")
            if (media > 0) append(" · $media playing")
        }
    }

    private fun buildRooms(cache: EntityCache?) {
        roomsRow.removeAllViews()
        val rooms = if (cache != null) RoomOrder.order(HaRepository.rooms, cfg.room.lowercase().replace(' ', '_'), cache,
            Calendar.getInstance().get(Calendar.HOUR_OF_DAY), cfg.roomOrder) else HaRepository.rooms
        if (activeRoomId == null) activeRoomId = rooms.firstOrNull()?.id
        rooms.forEachIndexed { i, r ->
            val on = cache != null && (r.lights.any { cache.get(it)?.on == true } || r.media.any { cache.get(it)?.state == "playing" })
            val card = g.col(context).apply {
                background = g.tile(context, Glass.BLUE, r.id == activeRoomId || on, 16f)
                setPadding(g.dp(context, 13f), g.dp(context, 11f), g.dp(context, 13f), g.dp(context, 11f))
                layoutParams = LinearLayout.LayoutParams(g.dp(context, 104f), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = g.dp(context, 9f)
                }
                addView(tv(11f, Glass.BLUE, g.disp(context)).apply { text = "CH ${i + 1}"; letterSpacing = 0.1f })
                addView(tv(14f, Glass.INK, g.body(context)).apply { text = r.name; setTypeface(typeface, Typeface.BOLD) })
                addView(tv(10.5f, Glass.INK_FAINT, g.body(context)).apply { text = roomSub(r, cache) })
                setOnClickListener { haptic(); activeRoomId = r.id; onOpenRoom(r); refresh() }
            }
            roomsRow.addView(card)
        }
    }

    private fun roomSub(r: Room, cache: EntityCache?): String {
        if (cache == null) return "${r.lights.size} lights"
        val on = r.lights.count { cache.get(it)?.on == true }
        val tv = r.media.any { cache.get(it)?.state == "playing" }
        return when { on > 0 && tv -> "$on on · TV"; on > 0 -> "$on on"; tv -> "TV"; else -> "all off" }
    }

    private fun buildScenes(cache: EntityCache?) {
        scenesWrap.removeAllViews()
        val scenes = cache?.byDomain("scene")?.take(6) ?: emptyList()
        val accents = intArrayOf(Glass.AMBER, Glass.VIOLET, Glass.BLUE, Glass.MINT, Glass.INDIGO, Glass.RED)
        var rowView: LinearLayout? = null
        scenes.forEachIndexed { i, s ->
            if (i % 2 == 0) { rowView = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                scenesWrap.addView(rowView); if (i > 0) {} }
            val accent = accents[i % accents.size]
            val pad = g.col(context).apply {
                background = g.tile(context, accent, false, 15f)
                setPadding(g.dp(context, 14f), g.dp(context, 13f), g.dp(context, 14f), g.dp(context, 13f))
                layoutParams = LinearLayout.LayoutParams(0, g.dp(context, 64f), 1f).apply {
                    if (i % 2 == 1) leftMargin = g.dp(context, 9f); topMargin = g.dp(context, 9f)
                }
                addView(tv(16f, Glass.INK, g.disp(context)).apply { text = s.friendlyName })
                setOnClickListener { haptic(); HaRepository.callService("scene", "turn_on", listOf(s.entityId)); flash(this, accent) }
            }
            rowView?.addView(pad)
        }
        if (scenes.isEmpty()) scenesWrap.addView(tv(12f, Glass.INK_FAINT, g.body(context)).apply { text = "No scenes in Home Assistant yet" })
    }

    private fun flash(v: View, accent: Int) {
        v.background = g.tile(context, accent, true, 15f)
        postDelayed({ v.background = g.tile(context, accent, false, 15f) }, 900)
    }

    private fun allOff() {
        hintLabel.setText("All off")
        val cache = HaRepository.cache ?: return
        val lights = cache.byDomain("light").filter { it.on }.map { it.entityId }
        if (lights.isNotEmpty()) HaRepository.callService("light", "turn_off", lights)
        postDelayed({ refresh() }, 600)
    }
}

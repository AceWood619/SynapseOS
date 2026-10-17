package com.acewood.synapse.core

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.acewood.synapse.logic.EntityCache
import com.acewood.synapse.logic.TimerInfo
import com.acewood.synapse.logic.Timers

/** Home timer card. HA timers are authoritative; quick buttons fall back to a local spoken timer. */
class TimerCardView(context: Context) : LinearLayout(context) {
    private val g = Glass
    private val handler = Handler(Looper.getMainLooper())
    private var localEndMs = 0L
    private var localLabel = ""
    private var speech: Speech? = null
    private var lastTimers: List<TimerInfo> = emptyList()
    private var rendered = false

    init { orientation = VERTICAL }

    fun refresh(cache: EntityCache?) {
        // Only running/paused HA timers. Idle ones are usually automation helpers (e.g. timer.jarvis_tv_restore);
        // a START from here could fire their automation.
        val timers = Timers.snapshot(cache).filter { it.isActive || it.isPaused }
        if (rendered && timers == lastTimers && localEndMs == 0L) return
        lastTimers = timers
        rendered = true
        render(timers)
    }

    private fun render(timers: List<TimerInfo>) {
        removeAllViews()
        if (timers.isEmpty() && localEndMs == 0L) {
            visibility = View.VISIBLE
            background = g.panel(context, 16f)
            setPadding(g.dp(context, 14f), g.dp(context, 11f), g.dp(context, 14f), g.dp(context, 11f))
            addView(TextView(context).apply { text = "TIMERS  ·  quick local fallback"; textSize = 12f; setTextColor(Glass.INK_DIM) })
            addView(quickRow(null))
            return
        }
        visibility = View.VISIBLE
        timers.forEach { timer -> addView(timerRow(timer)); addView(g.spacer(context, h = 7)) }
        if (localEndMs > 0L) { addView(localRow()); addView(g.spacer(context, h = 7)) }
        addView(quickRow(null))
    }

    private fun timerRow(timer: TimerInfo): View = g.col(context).apply {
        background = g.panel(context, 16f); setPadding(g.dp(context, 14f), g.dp(context, 10f), g.dp(context, 14f), g.dp(context, 10f))
        addView(g.row(context).apply {
            addView(g.col(context).apply {
                addView(TextView(context).apply { text = timer.name; textSize = 14f; setTextColor(Glass.INK) })
                addView(TextView(context).apply { text = "${timer.state}  ·  ${timer.remaining}"; textSize = 11f; setTextColor(if (timer.isActive) Glass.MINT else Glass.INK_DIM) })
            }, LayoutParams(0, -2, 1f))
            if (timer.isActive) addView(button("PAUSE", Glass.AMBER) { HaRepository.callService("timer", "pause", listOf(timer.entityId)) })
            else addView(button("START", Glass.MINT) { HaRepository.callService("timer", "start", listOf(timer.entityId)) })
            addView(g.spacer(context, w = 6)); addView(button("CANCEL", Glass.RED) { HaRepository.callService("timer", "cancel", listOf(timer.entityId)) })
        })
    }

    private fun localRow(): View = g.row(context).apply {
        background = g.panel(context, 16f); setPadding(g.dp(context, 14f), g.dp(context, 10f), g.dp(context, 14f), g.dp(context, 10f))
        addView(TextView(context).apply { text = "$localLabel  ·  ${formatRemaining()}"; textSize = 13f; setTextColor(Glass.MINT) }, LayoutParams(0, -2, 1f))
        addView(button("CANCEL", Glass.RED) { cancelLocal() })
    }

    private fun quickRow(entityId: String?): View = g.row(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(context).apply { text = "QUICK"; textSize = 10f; setTextColor(Glass.INK_FAINT) })
        listOf(5, 10, 15, 30).forEach { minutes ->
            addView(g.spacer(context, w = 6)); addView(button("${minutes}M", Glass.BLUE) { startQuick(entityId, minutes) })
        }
    }

    private fun startQuick(entityId: String?, minutes: Int) {
        val duration = "00:${minutes.toString().padStart(2, '0')}:00"
        if (entityId != null) {
            HaRepository.callService("timer", "start", listOf(entityId), mapOf("duration" to duration)); return
        }
        localLabel = "$minutes-minute timer"; localEndMs = SystemClock.elapsedRealtime() + minutes * 60_000L
        handler.removeCallbacksAndMessages(null)   // one tick loop, even if quick buttons are tapped again
        render(lastTimers); tickLocal()
    }

    private fun tickLocal() {
        if (localEndMs <= 0L) return
        if (SystemClock.elapsedRealtime() >= localEndMs) {
            localEndMs = 0L; render(lastTimers)
            try { speech = speech ?: Speech(context.applicationContext); speech?.speak("$localLabel finished") } catch (_: Exception) {}
            return
        }
        render(lastTimers)
        handler.postDelayed({ tickLocal() }, 1_000)
    }

    private fun cancelLocal() { localEndMs = 0L; handler.removeCallbacksAndMessages(null); render(lastTimers) }
    private fun formatRemaining(): String {
        val seconds = ((localEndMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L) / 1000L).toInt()
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }
    private fun button(label: String, color: Int, action: () -> Unit): View = TextView(context).apply {
        text = label; textSize = 10f; setTextColor(if (color == Glass.RED) Color.WHITE else color); gravity = Gravity.CENTER
        background = g.tile(context, color, false, 10f); setPadding(g.dp(context, 8f), g.dp(context, 8f), g.dp(context, 8f), g.dp(context, 8f)); tap { action() }
    }
}

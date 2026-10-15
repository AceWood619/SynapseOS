package com.acewood.synapse.core

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.acewood.synapse.logic.Notice
import com.acewood.synapse.logic.SystemNotices
import com.acewood.synapse.logic.UpdateNotice

/**
 * R-124 Part 4 — admin System notices: HA persistent notifications (with DISMISS) and pending
 * `update.*` entities (count + names + versions, deliberately no install button).
 * Opening it marks everything currently listed as seen, which clears the profile-pill badge.
 */
@SuppressLint("ViewConstructor")
class NoticesView(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
    private val g = Glass
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val subtitle = TextView(context).apply { textSize = 12f; setTextColor(Glass.INK_DIM) }
    private var lastSig: String? = null

    init {
        background = g.ground()
        val pad = g.dp(context, 16f)
        val outer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, g.dp(context, 14f), pad, g.dp(context, 14f)) }
        addView(outer, LayoutParams(-1, -1))
        outer.addView(g.row(context).apply {
            addView(key("‹  BACK", Glass.INK_DIM) { onBack() })
            addView(g.spacer(context, w = 14))
            addView(g.col(context).apply {
                addView(TextView(context).apply { text = "System notices"; textSize = 22f; setTextColor(Glass.INK); typeface = g.disp(context) })
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
            val notices = HaRepository.notices.list()
            val updates = SystemNotices.updates(HaRepository.cache)
            NoticeSeenStore.markSeen(context, SystemNotices.keys(notices, updates))
            val sig = notices.joinToString("|") { it.key } + "#" + updates.joinToString("|") { it.key + it.inProgress } + HaRepository.connected
            if (sig == lastSig) return@post
            lastSig = sig
            subtitle.text = buildString {
                append("${notices.size} notification${if (notices.size == 1) "" else "s"}")
                append(" · ${updates.size} update${if (updates.size == 1) "" else "s"} available")
                if (!HaRepository.connected) append(" · offline")
            }
            body.removeAllViews()
            body.addView(Glass.label(context, "NOTIFICATIONS"))
            body.addView(g.spacer(context, h = 6))
            if (notices.isEmpty()) body.addView(empty("No Home Assistant notifications."))
            notices.forEach { body.addView(noticeCard(it)); body.addView(g.spacer(context, h = 8)) }
            body.addView(g.spacer(context, h = 12))
            body.addView(Glass.label(context, "UPDATES"))
            body.addView(g.spacer(context, h = 6))
            if (updates.isEmpty()) body.addView(empty("Everything is up to date."))
            else body.addView(empty("Install updates from Home Assistant → Settings. Synapse only lists them."))
            updates.forEach { body.addView(g.spacer(context, h = 6)); body.addView(updateRow(it)) }
        }
    }

    private fun noticeCard(n: Notice): View = g.col(context).apply {
        background = g.panel(context, 16f); setPadding(g.dp(context, 14f), g.dp(context, 11f), g.dp(context, 14f), g.dp(context, 11f))
        addView(g.row(context).apply {
            addView(TextView(context).apply {
                text = n.title ?: "Notification"; textSize = 14.5f; setTextColor(Glass.INK); typeface = g.body(context); maxLines = 2
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(key("DISMISS", Glass.AMBER) { HaRepository.dismissNotice(n.id); lastSig = null; refresh() })
        })
        if (n.message.isNotBlank()) addView(TextView(context).apply {
            text = n.message; textSize = 12.5f; setTextColor(Glass.INK_DIM); setPadding(0, g.dp(context, 6f), 0, 0)
        })
        n.createdAt?.let { ts -> addView(TextView(context).apply { text = ts.take(16).replace('T', ' '); textSize = 10f; setTextColor(Glass.INK_FAINT); setPadding(0, g.dp(context, 5f), 0, 0) }) }
    }

    private fun updateRow(u: UpdateNotice): View = g.row(context).apply {
        background = g.panel(context, 14f); setPadding(g.dp(context, 14f), g.dp(context, 10f), g.dp(context, 14f), g.dp(context, 10f))
        addView(TextView(context).apply { text = "⬆"; textSize = 16f; setTextColor(if (u.inProgress) Glass.AMBER else Glass.MINT); setPadding(0, 0, g.dp(context, 10f), 0) })
        addView(g.col(context).apply {
            addView(TextView(context).apply { text = u.name; textSize = 14f; setTextColor(Glass.INK); maxLines = 1 })
            addView(TextView(context).apply {
                text = (u.installed ?: "?") + "  →  " + (u.latest ?: "?") + if (u.inProgress) "  ·  installing" else ""
                textSize = 11f; setTextColor(Glass.INK_DIM)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun empty(text: String): View = TextView(context).apply { this.text = text; textSize = 12.5f; setTextColor(Glass.INK_FAINT) }

    private fun key(label: String, color: Int, action: () -> Unit): View = TextView(context).apply {
        text = label; textSize = 11f; setTextColor(color); typeface = g.disp(context); letterSpacing = .08f; gravity = Gravity.CENTER
        background = g.tile(context, color, false, 12f); setPadding(g.dp(context, 13f), g.dp(context, 9f), g.dp(context, 13f), g.dp(context, 9f)); tap { action() }
    }
}

/** Which notice keys this panel's admin has already seen (app-private prefs; ids/hashes only, no text). */
object NoticeSeenStore {
    private const val PREFS = "synapse_notices"
    private const val KEY = "seen"
    fun seen(ctx: Context): Set<String> = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty()
    fun markSeen(ctx: Context, current: Set<String>) {
        if (seen(ctx) == current) return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, SystemNotices.markSeen(current)).apply()
    }
}

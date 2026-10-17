package com.acewood.synapse.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.acewood.synapse.logic.Profile

/** The Synapse app drawer: every Synapse screen + every allowed Android app, as a Glass icon grid. */
@SuppressLint("ViewConstructor")
class AppDrawerView(
    context: Context,
    private val onBack: () -> Unit,
    private val onLaunch: (AppCatalog.App) -> Unit,
    private val activeProfile: () -> Profile? = { null },
    private val onAllOff: () -> Unit = {},
) : FrameLayout(context) {
    private val g = Glass
    private val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init {
        background = g.ground()
        val pad = g.dp(context, 16f)
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, g.dp(context, 14f), pad, g.dp(context, 14f))
        }
        addView(outer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        outer.addView(g.row(context).apply {
            addView(TextView(context).apply {
                text = "‹  BACK"; textSize = 12f; setTextColor(Glass.INK_DIM); typeface = g.disp(context); letterSpacing = 0.08f
                background = g.tile(context, Glass.INK_DIM, false, 14f)
                setPadding(g.dp(context, 16f), g.dp(context, 11f), g.dp(context, 16f), g.dp(context, 11f))
                tap { onBack() }
            })
            addView(g.spacer(context, w = 14))
            addView(TextView(context).apply { text = "Apps"; textSize = 22f; setTextColor(Glass.INK); typeface = g.disp(context) })
        })
        outer.addView(g.spacer(context, h = 16))
        outer.addView(TextView(context).apply {
            text = "ALL OFF"; textSize = 11f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = g.tile(context, Glass.RED, false, 14f)
            setPadding(g.dp(context, 12f), g.dp(context, 10f), g.dp(context, 12f), g.dp(context, 10f))
            tap { onAllOff() }
        })
        outer.addView(g.spacer(context, h = 10))
        outer.addView(ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(grid) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        outer.addView(TextView(context).apply {
            text = "⚙ items need the admin PIN"; textSize = 11f; setTextColor(Glass.INK_FAINT); gravity = Gravity.CENTER
        })
    }

    fun open() {
        grid.removeAllViews()
        val apps = AppCatalog.all(context, activeProfile())
        var row: LinearLayout? = null
        apps.forEachIndexed { i, a ->
            if (i % 4 == 0) { row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }; grid.addView(row) }
            row?.addView(appCell(context, a, 58f) { onLaunch(a) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                bottomMargin = g.dp(context, 18f)
            })
        }
        // pad the last row so cells keep their width
        val rem = apps.size % 4
        if (rem != 0) repeat(4 - rem) { row?.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)) }
    }

    companion object {
        /** One icon + label cell; real app icons for Android apps, a glowing glyph for Synapse screens. */
        fun appCell(ctx: Context, a: AppCatalog.App, sizeDp: Float, onTap: () -> Unit): View = Glass.col(ctx).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            val s = Glass.dp(ctx, sizeDp)
            val icon = AppCatalog.icon(ctx, a)
            if (icon != null) {
                addView(ImageView(ctx).apply {
                    setImageDrawable(icon)
                    val p = Glass.dp(ctx, sizeDp * 0.14f); setPadding(p, p, p, p)
                    background = Glass.tile(ctx, Glass.BLUE, false, 18f)
                }, LinearLayout.LayoutParams(s, s))
            } else {
                addView(TextView(ctx).apply {
                    text = a.glyph; textSize = sizeDp * 0.36f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                    background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Glass.BLUE, Glass.INDIGO, Glass.VIOLET)).apply {
                        cornerRadius = Glass.dp(ctx, 18f).toFloat(); setStroke(Glass.dp(ctx, 1f), Glass.STROKE_BRIGHT)
                    }
                }, LinearLayout.LayoutParams(s, s))
            }
            addView(TextView(ctx).apply {
                text = (if (a.adminOnly) "⚙ " else "") + a.label; textSize = 11f; setTextColor(Glass.INK_DIM)
                gravity = Gravity.CENTER; maxLines = 2; setPadding(0, Glass.dp(ctx, 6f), 0, 0)
            })
            tap { onTap() }
        }
    }
}

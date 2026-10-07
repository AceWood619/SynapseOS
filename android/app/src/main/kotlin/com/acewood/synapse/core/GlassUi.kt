package com.acewood.synapse.core

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Synapse Glass palette + helpers for building the native UI in Views. */
object Glass {
    val GROUND0 = Color.parseColor("#0b1430"); val GROUND1 = Color.parseColor("#070b1c"); val GROUND2 = Color.parseColor("#03050c")
    val INK = Color.parseColor("#eaf0ff"); val INK_DIM = Color.parseColor("#9fb0dc"); val INK_FAINT = Color.parseColor("#64729b")
    val BLUE = Color.parseColor("#49b6ff"); val INDIGO = Color.parseColor("#3a52ff"); val VIOLET = Color.parseColor("#9b6cff")
    val MINT = Color.parseColor("#45f0c8"); val RED = Color.parseColor("#ff6b81"); val AMBER = Color.parseColor("#ffc24d")
    val STROKE = Color.argb(56, 130, 170, 255)
    val STROKE_BRIGHT = Color.argb(140, 130, 190, 255)

    fun disp(ctx: Context): Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    fun body(ctx: Context): Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    fun light(ctx: Context): Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density).toInt()

    /** The dark radial ground for the whole screen. */
    fun ground(): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(GROUND0, GROUND1, GROUND2)).apply {
        gradientType = GradientDrawable.RADIAL_GRADIENT; gradientRadius = 1400f
        setGradientCenter(0.5f, 0.12f)
    }

    /** A frosted glass panel (faked with layered translucent fills + bright stroke + top highlight). */
    fun panel(ctx: Context, radius: Float = 20f, tint: Int = Color.argb(70, 26, 38, 78)): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(ctx, radius).toFloat()
            colors = intArrayOf(tint, Color.argb(120, 6, 10, 24))
            orientation = GradientDrawable.Orientation.TL_BR
            setStroke(dp(ctx, 1f), STROKE)
        }

    /** A tile that glows in [accent] when "on". */
    fun tile(ctx: Context, accent: Int, on: Boolean, radius: Float = 15f): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(ctx, radius).toFloat()
            if (on) {
                colors = intArrayOf(withAlpha(accent, 60), Color.argb(10, 255, 255, 255))
                orientation = GradientDrawable.Orientation.TL_BR
                setStroke(dp(ctx, 1f), withAlpha(accent, 170))
            } else {
                setColor(Color.argb(10, 255, 255, 255))
                setStroke(dp(ctx, 1f), STROKE)
            }
        }

    fun withAlpha(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

    fun label(ctx: Context, text: String): TextView = TextView(ctx).apply {
        this.text = text; typeface = disp(ctx); setTextColor(INK_FAINT)
        textSize = 12.5f; letterSpacing = 0.18f
    }

    fun row(ctx: Context, gapDp: Float = 9f): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    }
    fun col(ctx: Context): LinearLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    fun spacer(ctx: Context, w: Int = 0, h: Int = 0): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(if (w > 0) dp(ctx, w.toFloat()) else 0, if (h > 0) dp(ctx, h.toFloat()) else 0)
    }
}

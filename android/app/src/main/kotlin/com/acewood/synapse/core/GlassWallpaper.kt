package com.acewood.synapse.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import com.acewood.synapse.logic.Constellation

/**
 * R-124 Part 6 — the Synapse Glass wallpaper: the night radial ground plus a faint, static neuron
 * constellation. Rendered once into a shared bitmap per screen size (all full-screen views share it),
 * so drawing it costs one blit — no per-frame vector work on the GE8320.
 */
class GlassWallpaper : Drawable() {
    private val blit = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val bmp = cached(b.width(), b.height()) ?: run { Glass.plainGround().apply { bounds = b }.draw(canvas); return }
        canvas.drawBitmap(bmp, null, b, blit)
    }

    override fun setAlpha(alpha: Int) { blit.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { blit.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    companion object {
        private var key = 0L
        private var bitmap: Bitmap? = null

        @Synchronized
        private fun cached(w: Int, h: Int): Bitmap? {
            val k = (w.toLong() shl 32) or h.toLong()
            bitmap?.let { if (k == key && !it.isRecycled) return it }
            return runCatching { render(w, h) }.getOrNull()?.also { bitmap = it; key = k }
        }

        private fun render(w: Int, h: Int): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            Glass.plainGround().apply { bounds = Rect(0, 0, w, h) }.draw(c)
            val (pts, edges) = Constellation.build(aspect = h.toFloat() / w)
            val unit = minOf(w, h) / 360f   // ≈ px per dp at the designed 360 dp width
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 0.9f * unit; color = Glass.withAlpha(Glass.BLUE, 26)
            }
            val dot = Paint(Paint.ANTI_ALIAS_FLAG)
            edges.forEach { e ->
                val a = pts[e.a]; val b = pts[e.b]
                c.drawLine(a.x * w, a.y * h, b.x * w, b.y * h, line)
            }
            pts.forEachIndexed { i, p ->
                // Fade with depth so the bottom (where controls sit) stays calm.
                val fade = (1f - p.y * 0.75f)
                dot.color = Glass.withAlpha(if (i % 5 == 0) Glass.MINT else Glass.BLUE, (70 * fade).toInt())
                c.drawCircle(p.x * w, p.y * h, p.size * 1.6f * unit, dot)
            }
            return bmp
        }
    }
}

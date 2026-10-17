package com.acewood.synapse.core

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/**
 * Small stroked line icons drawn in code (24-unit grid, like the mockups), so the remote never depends
 * on emoji/symbol glyphs that render differently on each Android build. Cheap: one path per draw.
 */
class GlassIcon(context: Context, private var kind: Kind, color: Int, private val strokeUnits: Float = 2.2f) : View(context) {
    enum class Kind { CHEVRON_LEFT, CHEVRON_RIGHT, CHEVRON_UP, CHEVRON_DOWN, MUTE, PLAY, PAUSE, BACK, BULB, TV, GRID, MIC, POWER, PLUS, MINUS, HOUSE, DOTS, PREV, NEXT, CAMERA }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; this.color = color
    }
    private val path = Path()
    private val rect = RectF()

    fun setColor(c: Int) { paint.color = c; invalidate() }
    fun showKind(k: Kind) { if (k != kind) { kind = k; invalidate() } }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height) / 24f
        if (s <= 0f) return
        canvas.save()
        canvas.translate((width - 24 * s) / 2f, (height - 24 * s) / 2f)
        canvas.scale(s, s)
        paint.strokeWidth = strokeUnits
        path.reset()
        when (kind) {
            Kind.CHEVRON_LEFT -> { path.moveTo(15f, 18f); path.lineTo(9f, 12f); path.lineTo(15f, 6f) }
            Kind.CHEVRON_RIGHT -> { path.moveTo(9f, 18f); path.lineTo(15f, 12f); path.lineTo(9f, 6f) }
            Kind.CHEVRON_UP -> { path.moveTo(6f, 15f); path.lineTo(12f, 9f); path.lineTo(18f, 15f) }
            Kind.CHEVRON_DOWN -> { path.moveTo(6f, 9f); path.lineTo(12f, 15f); path.lineTo(18f, 9f) }
            Kind.MUTE -> { speaker(); path.moveTo(22f, 9f); path.lineTo(16f, 15f); path.moveTo(16f, 9f); path.lineTo(22f, 15f) }
            Kind.PLAY -> { path.moveTo(7f, 4.5f); path.lineTo(19f, 12f); path.lineTo(7f, 19.5f); path.close() }
            Kind.PAUSE -> { path.moveTo(8f, 5f); path.lineTo(8f, 19f); path.moveTo(16f, 5f); path.lineTo(16f, 19f) }
            Kind.BACK -> { path.moveTo(9f, 14f); path.lineTo(4f, 9f); path.lineTo(9f, 4f); path.moveTo(4f, 9f); path.lineTo(15f, 9f)
                rect.set(10f, 9f, 20f, 19f); path.arcTo(rect, -90f, 180f); path.lineTo(12f, 19f) }
            Kind.BULB -> { path.moveTo(9f, 18f); path.lineTo(15f, 18f); path.moveTo(10f, 21f); path.lineTo(14f, 21f)
                path.moveTo(9f, 15.5f); rect.set(6f, 3f, 18f, 15f); path.arcTo(rect, 135f, 270f); path.lineTo(15f, 15.5f) }
            Kind.TV -> { rect.set(2f, 5f, 22f, 18f); path.addRoundRect(rect, 2f, 2f, Path.Direction.CW); path.moveTo(8f, 21f); path.lineTo(16f, 21f) }
            Kind.GRID -> for ((x, y) in listOf(3f to 3f, 14f to 3f, 3f to 14f, 14f to 14f)) { rect.set(x, y, x + 7f, y + 7f); path.addRoundRect(rect, 2f, 2f, Path.Direction.CW) }
            Kind.MIC -> { rect.set(9f, 3f, 15f, 14f); path.addRoundRect(rect, 3f, 3f, Path.Direction.CW)
                rect.set(5f, 4f, 19f, 18f); path.arcTo(rect, 180f, -180f, true); path.moveTo(12f, 18f); path.lineTo(12f, 21f) }
            Kind.POWER -> { path.moveTo(12f, 3f); path.lineTo(12f, 11f); rect.set(4f, 5f, 20f, 21f); path.arcTo(rect, -60f, 300f, true) }
            Kind.PLUS -> { path.moveTo(12f, 5f); path.lineTo(12f, 19f); path.moveTo(5f, 12f); path.lineTo(19f, 12f) }
            Kind.MINUS -> { path.moveTo(5f, 12f); path.lineTo(19f, 12f) }
            Kind.HOUSE -> { path.moveTo(3f, 11f); path.lineTo(12f, 3f); path.lineTo(21f, 11f); path.moveTo(5f, 10f); path.lineTo(5f, 20f); path.lineTo(19f, 20f); path.lineTo(19f, 10f) }
            Kind.PREV -> { path.moveTo(19f, 20f); path.lineTo(9f, 12f); path.lineTo(19f, 4f); path.close(); path.moveTo(5f, 19f); path.lineTo(5f, 5f) }
            Kind.NEXT -> { path.moveTo(5f, 4f); path.lineTo(15f, 12f); path.lineTo(5f, 20f); path.close(); path.moveTo(19f, 5f); path.lineTo(19f, 19f) }
            Kind.CAMERA -> { rect.set(2f, 6f, 17f, 18f); path.addRoundRect(rect, 2.5f, 2.5f, Path.Direction.CW)
                path.moveTo(17f, 10f); path.lineTo(22f, 7f); path.lineTo(22f, 17f); path.lineTo(17f, 14f)
                path.addCircle(9.5f, 12f, 3f, Path.Direction.CW) }
            Kind.DOTS -> { path.addCircle(5f, 12f, 1.2f, Path.Direction.CW); path.addCircle(12f, 12f, 1.2f, Path.Direction.CW); path.addCircle(19f, 12f, 1.2f, Path.Direction.CW) }
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun speaker() { path.moveTo(11f, 5f); path.lineTo(6f, 9f); path.lineTo(2f, 9f); path.lineTo(2f, 15f); path.lineTo(6f, 15f); path.lineTo(11f, 19f); path.close() }
}

package com.acewood.synapse.core

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.View
import android.view.Window
import com.acewood.synapse.logic.FrameBudget
import com.acewood.synapse.logic.NeuronOrb

/**
 * R-124 Part 6 — the animated neuron hero orb.
 *
 * GE8320 budget rules (CONTRIBUTING.md): no blur, no off-screen loop, no full-screen redraws.
 *  - Redraws ~12 fps, only this 56 dp view (invalidate() is scoped to it).
 *  - Runs only while it is attached, shown, and the panel is not in ambient ([setRunning]).
 *  - Shaders are built once per size; onDraw allocates nothing.
 *  - A window FrameMetrics listener feeds real frame times to [FrameBudget]; if the UI is janky
 *    while the orb animates, the orb freezes on a still frame for the rest of this view's life.
 */
class NeuronOrbView(context: Context) : View(context) {
    private val main = Handler(Looper.getMainLooper())
    private val budget = FrameBudget()
    private val startMs = SystemClock.uptimeMillis()
    private var wantRunning = true
    private var ticking = false

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Glass.MINT }
    private val linkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Glass.withAlpha(Glass.BLUE, 90) }

    private val frame = Runnable { ticking = false; invalidate(); schedule() }

    private var metricsListener: Window.OnFrameMetricsAvailableListener? = null

    /** Pause/resume from the host (ambient, overlays, profile lock). Cheap to call repeatedly. */
    fun setRunning(running: Boolean) {
        wantRunning = running
        if (running) schedule() else { main.removeCallbacks(frame); ticking = false }
    }

    val frozen: Boolean get() = budget.tripped

    private fun active() = wantRunning && !budget.tripped && isAttachedToWindow && isShown && windowVisibility == VISIBLE

    private fun schedule() {
        if (ticking || !active()) return
        ticking = true
        main.postDelayed(frame, FRAME_MS)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val cx = w / 2f; val cy = h / 2f; val r = minOf(w, h) / 2f
        corePaint.shader = RadialGradient(cx - r * 0.18f, cy - r * 0.2f, r * 0.62f,
            intArrayOf(Color.WHITE, Glass.BLUE, Glass.INDIGO), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        haloPaint.shader = RadialGradient(cx, cy, r,
            intArrayOf(Glass.withAlpha(Glass.BLUE, 120), Glass.withAlpha(Glass.INDIGO, 40), Color.TRANSPARENT),
            floatArrayOf(0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
        linkPaint.strokeWidth = maxOf(1f, r * 0.035f)
        ringPaint.strokeWidth = maxOf(1f, r * 0.025f)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f; val cy = h / 2f; val r = minOf(w, h) / 2f
        // Frozen orb = a still frame from t=0, so it never looks broken mid-motion.
        val t = if (budget.tripped) 0.0 else (SystemClock.uptimeMillis() - startMs) / 1000.0
        val pulse = NeuronOrb.pulse(t)

        canvas.drawCircle(cx, cy, r * (0.86f + 0.1f * pulse), haloPaint)
        canvas.drawCircle(cx, cy, r * 0.74f, ringPaint)
        val core = r * (0.36f + 0.05f * pulse)
        for (n in NeuronOrb.nodes(t)) {
            val nx = cx + n.x * r * 0.92f; val ny = cy + n.y * r * 0.92f
            linkPaint.color = Glass.withAlpha(Glass.MINT, (50 + 90 * n.glow).toInt())
            canvas.drawLine(cx, cy, nx, ny, linkPaint)
            nodePaint.alpha = (140 + 115 * n.glow).toInt()
            canvas.drawCircle(nx, ny, r * (0.07f + 0.035f * n.glow), nodePaint)
        }
        canvas.drawCircle(cx, cy, core, corePaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val window = (context as? Activity)?.window
        if (window != null && metricsListener == null) {
            val l = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                // Only judge frames drawn while the orb is animating; ambient/overlay frames don't count.
                if (ticking || active()) {
                    val ns = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
                    if (!budget.record(ns)) main.post { setRunning(false); invalidate() }
                }
            }
            runCatching { window.addOnFrameMetricsAvailableListener(l, main) }.onSuccess { metricsListener = l }
        }
        schedule()
    }

    override fun onDetachedFromWindow() {
        main.removeCallbacks(frame); ticking = false
        metricsListener?.let { l -> runCatching { (context as? Activity)?.window?.removeOnFrameMetricsAvailableListener(l) } }
        metricsListener = null
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) schedule() else { main.removeCallbacks(frame); ticking = false }
    }

    companion object { private const val FRAME_MS = 83L }   // ≈12 fps
}

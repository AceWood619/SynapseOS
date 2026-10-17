package com.acewood.synapse.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.acewood.synapse.logic.CameraFeed
import com.acewood.synapse.logic.MjpegFrameReader
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Full-screen live camera (Mason 2026-10-17: kids' room camera on Synapse).
 *
 * Reads Home Assistant's MJPEG proxy for the camera entity with the panel's token and shows ~10 fps,
 * downscaled to the screen width. If the stream won't open (some camera integrations only do stills),
 * it falls back to refreshing a snapshot every second. Streaming stops whenever this view is closed or
 * hidden, so a camera never runs in the background.
 *
 * Talkback: [talkKey] is the hook for the push-to-talk feature Mason is building; hidden until a
 * transport exists (see coord/REQUESTS.md R-128).
 */
@SuppressLint("ViewConstructor")
class CameraView(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
    private val g = Glass
    private fun dp(v: Float) = g.dp(context, v)

    private val title = TextView(context).apply { textSize = 20f; setTextColor(Glass.INK); typeface = g.disp(context); maxLines = 1 }
    private val status = TextView(context).apply { textSize = 12.5f; setTextColor(Glass.INK_DIM); typeface = g.body(context) }
    private val liveDot = View(context).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Glass.RED) }; visibility = View.INVISIBLE }
    private val image = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(Color.BLACK) }
    val talkKey: TextView = TextView(context).apply {
        text = "Hold to talk"; textSize = 15f; setTextColor(Color.rgb(4, 16, 31)); typeface = g.bodyBold(context); gravity = Gravity.CENTER
        background = GradientDrawable().apply { cornerRadius = dp(26f).toFloat(); setColor(Glass.BLUE) }
        visibility = View.GONE
    }

    @Volatile private var entityId: String? = null
    @Volatile private var running = false
    @Volatile private var call: Call? = null
    private var worker: Thread? = null
    private var shown: Bitmap? = null

    private val http = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    init {
        background = g.ground()
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16f), dp(12f), dp(16f), dp(16f)) }
        addView(col, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        col.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(FrameLayout(context).apply {
                contentDescription = "Back"
                background = GradientDrawable().apply { cornerRadius = dp(14f).toFloat(); setStroke(dp(1f), Color.argb(64, 130, 170, 255)) }
                addView(GlassIcon(context, GlassIcon.Kind.CHEVRON_LEFT, Color.rgb(201, 212, 242)), LayoutParams(dp(18f), dp(18f), Gravity.CENTER))
                tap { onBack() }
            }, LinearLayout.LayoutParams(dp(44f), dp(44f)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    addView(liveDot, LinearLayout.LayoutParams(dp(8f), dp(8f)).apply { rightMargin = dp(6f) })
                    addView(status)
                })
                addView(title)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(12f) })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56f)))
        col.addView(FrameLayout(context).apply {
            background = GradientDrawable().apply { cornerRadius = dp(20f).toFloat(); setColor(Color.BLACK); setStroke(dp(1f), Color.argb(56, 130, 170, 255)) }
            clipToOutline = true
            addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(12f) })
        col.addView(talkKey, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56f)).apply { topMargin = dp(14f) })
    }

    fun open(cameraEntityId: String, label: String) {
        close()
        entityId = cameraEntityId
        title.text = label
        say("Connecting…", live = false)
        running = true
        worker = Thread({ loop(cameraEntityId) }, "synapse-camera").also { it.start() }
    }

    /** Stop streaming and free the frame. Safe to call repeatedly. */
    fun close() {
        running = false
        call?.cancel(); call = null
        worker?.interrupt(); worker = null
        image.setImageDrawable(null)
        shown?.recycle(); shown = null
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!isVisible && running) close()
    }

    override fun onDetachedFromWindow() { close(); super.onDetachedFromWindow() }

    // ---------------- streaming ----------------

    private fun loop(id: String) {
        var failures = 0
        while (running && entityId == id) {
            val cfg = HaRepository.config ?: run { say("Home Assistant isn't set up", false); return }
            val ok = if (failures < 3) stream(cfg.haUrl.trimEnd('/') + CameraFeed.streamPath(id), cfg.haToken)
                     else snapshots(cfg.haUrl.trimEnd('/') + CameraFeed.snapshotPath(id), cfg.haToken)
            if (!running) return
            failures = if (ok) 0 else failures + 1
            say(if (failures >= 3) "Live stream unavailable · showing snapshots" else "Reconnecting…", false)
            try { Thread.sleep((1_000L * failures).coerceIn(1_000L, 5_000L)) } catch (_: InterruptedException) { return }
        }
    }

    /** MJPEG stream; returns true if at least one frame arrived. */
    private fun stream(url: String, token: String): Boolean {
        var got = false
        runCatching {
            val c = http.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build())
            call = c
            c.execute().use { resp ->
                if (!resp.isSuccessful) { say("Camera error (HTTP ${resp.code})", false); return false }
                val reader = MjpegFrameReader(resp.body!!.byteStream())
                var lastShown = 0L
                while (running) {
                    val jpeg = reader.next() ?: break
                    val now = SystemClock.uptimeMillis()
                    if (!CameraFeed.shouldShow(now, lastShown)) continue   // drop frames we can't show in time
                    lastShown = now
                    decode(jpeg)?.let { show(it); got = true }
                }
            }
        }
        return got
    }

    /** Fallback: one JPEG per second from the snapshot endpoint. */
    private fun snapshots(url: String, token: String): Boolean {
        var got = false
        while (running) {
            val ok = runCatching {
                val c = http.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build())
                call = c
                c.execute().use { resp -> if (resp.isSuccessful) resp.body?.bytes()?.let { decode(it) }?.let { show(it); true } ?: false else false }
            }.getOrDefault(false)
            if (!ok) return got
            got = true
            try { Thread.sleep(1_000) } catch (_: InterruptedException) { return got }
        }
        return got
    }

    private var sample = 0
    private fun decode(jpeg: ByteArray): Bitmap? {
        if (sample == 0) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, o)
            val target = resources.displayMetrics.widthPixels.coerceAtLeast(320)
            var s = 1; while (o.outWidth / (s * 2) >= target) s *= 2
            sample = s
        }
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 })
    }

    private fun show(bmp: Bitmap) {
        post {
            if (!running) { bmp.recycle(); return@post }
            val old = shown
            image.setImageBitmap(bmp); shown = bmp
            old?.recycle()
            say("LIVE", live = true)
        }
    }

    private fun say(text: String, live: Boolean) {
        post {
            status.text = text
            status.setTextColor(if (live) Glass.RED else Glass.INK_DIM)
            liveDot.visibility = if (live) View.VISIBLE else View.INVISIBLE
        }
    }
}

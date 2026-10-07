package com.acewood.synapse.core

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One-shot JPEG from the phone camera (Camera2). Streams a few frames first so
 * auto-exposure and focus settle, then returns one. Android shows its green
 * "camera in use" dot while this runs. Works while the Synapse screen is on top
 * (Android blocks camera access for background apps).
 */
class Snapshotter(private val ctx: Context, private val facing: String) {
    private val thread = HandlerThread("synapse-cam").apply { start() }
    private val handler = Handler(thread.looper)
    private val busy = AtomicBoolean(false)
    @Volatile var lastError: String? = null
        private set
    @Volatile var lastSize: String? = null
        private set

    fun permitted() = ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun take(timeoutMs: Long = 10_000, warmupFrames: Int = 6): ByteArray? {
        if (!permitted()) { lastError = "CAMERA permission not granted"; return null }
        if (!busy.compareAndSet(false, true)) { lastError = "busy"; return null }
        var reader: ImageReader? = null
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        try {
            val cm = ctx.getSystemService(CameraManager::class.java)
            val want = if (facing == "front") CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == want }
                ?: run { lastError = "no $facing camera"; return null }
            val chars = cm.getCameraCharacteristics(id)
            val sizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.JPEG)
            if (sizes.isNullOrEmpty()) { lastError = "no JPEG sizes"; return null }
            val size = sizes.filter { it.width <= 1600 && it.height <= 1600 }.maxByOrNull { it.width * it.height }
                ?: sizes.minByOrNull { it.width * it.height }!!
            lastSize = "${size.width}x${size.height}"
            val orientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            val r = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
            reader = r
            val done = CountDownLatch(1)
            var frames = 0
            var result: ByteArray? = null
            r.setOnImageAvailableListener({ ir ->
                val img = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    frames++
                    if (frames > warmupFrames && result == null) {
                        val buf = img.planes[0].buffer
                        result = ByteArray(buf.remaining()).also { buf.get(it) }
                        done.countDown()
                    }
                } finally { img.close() }
            }, handler)

            @Suppress("DEPRECATION")
            cm.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) {
                    device = d
                    try {
                        d.createCaptureSession(listOf(r.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) {
                                session = s
                                val req = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                    addTarget(r.surface)
                                    set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                    set(CaptureRequest.JPEG_ORIENTATION, orientation)
                                    set(CaptureRequest.JPEG_QUALITY, 85.toByte())
                                }.build()
                                try { s.setRepeatingRequest(req, null, handler) } catch (e: Exception) {
                                    lastError = "repeating request: ${e.message}"; done.countDown()
                                }
                            }
                            override fun onConfigureFailed(s: CameraCaptureSession) { lastError = "session config failed"; done.countDown() }
                        }, handler)
                    } catch (e: Exception) { lastError = "session: ${e.message}"; done.countDown() }
                }
                override fun onDisconnected(d: CameraDevice) { lastError = "camera disconnected"; d.close(); done.countDown() }
                override fun onError(d: CameraDevice, error: Int) { lastError = "camera error $error"; d.close(); done.countDown() }
            }, handler)

            if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) lastError = "timeout after $frames frames"
            if (result != null) lastError = null
            return result
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            Log.w(SynapseApp.TAG, "snapshot failed", e)
            return null
        } finally {
            try { session?.close() } catch (_: Exception) {}
            try { device?.close() } catch (_: Exception) {}
            try { reader?.close() } catch (_: Exception) {}
            busy.set(false)
        }
    }

    fun shutdown() = thread.quitSafely()
}

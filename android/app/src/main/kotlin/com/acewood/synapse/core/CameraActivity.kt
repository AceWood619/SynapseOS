package com.acewood.synapse.core

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Minimal native camera: preview, one-tap capture, and private-to-the-phone MediaStore storage. */
class CameraActivity : Activity() {
    private lateinit var preview: SurfaceView
    private lateinit var cameraManager: CameraManager
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var cameraId: String? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val requestCode = 91

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val g = Glass
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = g.ground() }
        root.addView(g.row(this).apply {
            addView(TextView(context).apply {
                text = "‹  HOME"; textSize = 12f; setTextColor(Glass.BLUE); letterSpacing = 0.1f
                background = g.tile(context, Glass.BLUE, false, 14f)
                setPadding(g.dp(context, 14f), g.dp(context, 10f), g.dp(context, 14f), g.dp(context, 10f))
                tap { finish() }
            })
            addView(TextView(context).apply {
                text = "SYNAPSE CAMERA"; textSize = 19f; setTextColor(Glass.INK); typeface = g.disp(context)
                setPadding(g.dp(context, 14f), 0, 0, 0)
            })
        }, LinearLayout.LayoutParams(-1, g.dp(this, 66f)))
        preview = SurfaceView(this)
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "●  CAPTURE"; textSize = 15f; letterSpacing = 0.12f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); background = g.tile(context, Glass.BLUE, true, 18f)
            setPadding(0, g.dp(context, 16f), 0, g.dp(context, 16f))
            setOnClickListener { capture() }
        }, LinearLayout.LayoutParams(-1, g.dp(this, 64f)).apply {
            setMargins(g.dp(this@CameraActivity, 18f), g.dp(this@CameraActivity, 12f), g.dp(this@CameraActivity, 18f), g.dp(this@CameraActivity, 16f))
        })
        setContentView(root)
        cameraManager = getSystemService(CameraManager::class.java)
        preview.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { openCamera() }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
            override fun surfaceDestroyed(holder: SurfaceHolder) { closeCamera() }
        })
    }

    private fun openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), requestCode); return
        }
        startThread()
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) ==
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull()
            val id = cameraId ?: return
            reader = ImageReader.newInstance(1920, 1080, android.graphics.ImageFormat.JPEG, 2).also { r ->
                r.setOnImageAvailableListener({ source ->
                    source.acquireLatestImage()?.use { image -> saveJpeg(image.planes[0].buffer) }
                }, handler)
            }
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) { camera = device; createSession() }
                override fun onDisconnected(device: CameraDevice) { device.close(); camera = null }
                override fun onError(device: CameraDevice, error: Int) { device.close(); camera = null; toast("Camera unavailable") }
            }, handler)
        } catch (_: SecurityException) { toast("Camera permission required") }
        catch (_: Exception) { toast("Camera unavailable") }
    }

    private fun createSession() {
        val device = camera ?: return
        val output = reader ?: return
        runCatching {
            device.createCaptureSession(listOf(preview.holder.surface, output.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(value: CameraCaptureSession) {
                    session = value
                    val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(preview.holder.surface)
                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    }.build()
                    value.setRepeatingRequest(request, null, handler)
                }
                override fun onConfigureFailed(value: CameraCaptureSession) { toast("Camera preview failed") }
            }, handler)
        }
    }

    private fun capture() {
        val device = camera ?: return
        val value = session ?: return
        val output = reader ?: return
        runCatching {
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(output.surface); set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            }.build()
            value.capture(request, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: android.hardware.camera2.TotalCaptureResult) {
                    toast("Saved to Pictures / Synapse")
                }
            }, handler)
        }.onFailure { toast("Capture failed") }
    }

    private fun saveJpeg(buffer: java.nio.ByteBuffer) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "synapse_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Synapse")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        contentResolver.openOutputStream(uri)?.use { out -> out.write(ByteArray(buffer.remaining()).also { buffer.get(it) }) }
    }

    private fun startThread() { thread = HandlerThread("SynapseCamera").also { it.start(); handler = Handler(it.looper) } }
    private fun closeCamera() { session?.close(); session = null; camera?.close(); camera = null; reader?.close(); reader = null; thread?.quitSafely(); thread = null; handler = null }
    private fun toast(message: String) = runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.requestCode && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
        else toast("Camera permission denied")
    }
    override fun onDestroy() { closeCamera(); super.onDestroy() }
}

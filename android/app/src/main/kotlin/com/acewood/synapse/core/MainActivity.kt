package com.acewood.synapse.core

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import com.acewood.synapse.logic.IdleController
import com.acewood.synapse.logic.Json
import com.acewood.synapse.logic.NodeConfig
import kotlin.random.Random

/**
 * The panel screen: Home Assistant dashboard in a WebView, plus a dimmed
 * clock face ("ambient mode") after a period of no activity.
 */
class MainActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    private var webCrashes = 0
    private lateinit var ambient: LinearLayout
    private lateinit var ambientStatus: TextView
    private lateinit var setup: TextView
    private var cfg: NodeConfig? = null
    private val idle = IdleController(120_000)
    @Volatile private var pageOrigin: String? = null
    private var retryPending = false
    private var cornerTaps = 0
    private var firstCornerTapMs = 0L
    private var tickCount = 0L

    private val commands: (NodeBus.Command) -> Unit = { c ->
        when (c) {
            NodeBus.Command.WAKE -> if (idle.activity(now())) applyMode()          // sensor wake: respects grace
            NodeBus.Command.AMBIENT -> if (idle.forceAmbient(now())) applyMode()
            NodeBus.Command.RELOAD, NodeBus.Command.CONFIG_CHANGED -> loadConfigAndPage()
        }
    }

    private fun now() = SystemClock.elapsedRealtime()

    companion object {
        /** Process-wide: companions are opened once per boot (or app restart), not on every resume. */
        @Volatile private var companionsLaunched = false
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        web = WebView(this)
        setupWebView()
        root.addView(web, 0, FrameLayout.LayoutParams(-1, -1))

        ambient = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            addView(TextClock(context).apply {
                format12Hour = "h:mm"; format24Hour = "H:mm"
                textSize = 96f; setTextColor(Color.rgb(170, 170, 170)); typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
                gravity = Gravity.CENTER
            })
            addView(TextClock(context).apply {
                format12Hour = "EEEE, MMMM d"; format24Hour = "EEEE, d MMMM"
                textSize = 22f; setTextColor(Color.rgb(120, 120, 120)); gravity = Gravity.CENTER
            })
            ambientStatus = TextView(context).apply {
                textSize = 14f; setTextColor(Color.rgb(90, 90, 90)); gravity = Gravity.CENTER
                setPadding(0, dp(24), 0, 0)
            }
            addView(ambientStatus)
        }
        root.addView(ambient, FrameLayout.LayoutParams(-1, -1))

        setup = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 18f; setPadding(dp(24), dp(48), dp(24), dp(24))
            setBackgroundColor(Color.rgb(15, 15, 25)); visibility = View.GONE
        }
        root.addView(setup, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        startForegroundService(Intent(this, NodeService::class.java))
        NodeBus.onCommand(commands)
        loadConfigAndPage()
        ui.post(ticker)
    }

    override fun onDestroy() {
        NodeBus.removeCommand(commands)
        ui.removeCallbacksAndMessages(null)
        web.destroy()
        NodeBus.screenMode = "off"
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        Kiosk.applyPolicies(this)
        if (cfg?.kiosk != false) Kiosk.enterLockTask(this)
        idle.activity(now())
        applyMode()
        launchCompanionsOnce()
    }

    /**
     * Companion apps (e.g. Ava, the voice satellite) need to be opened once after boot so their
     * services start: Android 14 only lets a microphone service start from a visible screen.
     * Open each one for a few seconds, then bring the dashboard back. Once per app process.
     */
    private fun launchCompanionsOnce() {
        val apps = cfg?.companionApps.orEmpty()
        if (companionsLaunched || apps.isEmpty()) return
        companionsLaunched = true
        var delay = 3_000L
        for (pkg in apps) {
            val intent = packageManager.getLaunchIntentForPackage(pkg) ?: continue
            ui.postDelayed({
                try { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {
                    android.util.Log.w(SynapseApp.TAG, "can't open companion $pkg", e)
                }
            }, delay)
            delay += 6_000L
        }
        ui.postDelayed({
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }, delay)
    }

    override fun onPause() {
        super.onPause()
        NodeBus.screenMode = "off"
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }

    @Deprecated("Back is handled inside the dashboard")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack()
    }

    // ---------- touch, idle and ambient ----------

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            val wasAmbient = idle.mode == IdleController.Mode.AMBIENT
            if (idle.activity(now(), force = true)) applyMode()
            NodeBus.emit(NodeBus.Event.TOUCH)
            if (ev.x < dp(100) && ev.y < dp(100)) cornerTap()
            if (wasAmbient) return true // the waking tap shouldn't also press a dashboard button
        }
        return super.dispatchTouchEvent(ev)
    }

    private val ticker = object : Runnable {
        override fun run() {
            tickCount++
            if (idle.tick(now())) applyMode()
            // Burn-in protection: nudge the clock a little every minute.
            if (tickCount % 60 == 0L && ambient.visibility == View.VISIBLE) {
                ambient.translationX = Random.nextInt(-dp(30), dp(30)).toFloat()
                ambient.translationY = Random.nextInt(-dp(40), dp(40)).toFloat()
            }
            if (cfg == null && tickCount % 10 == 0L && ConfigStore.importIfPresent(this@MainActivity)) {
                NodeBus.send(NodeBus.Command.CONFIG_CHANGED)
            }
            ui.postDelayed(this, 1_000)
        }
    }

    private fun applyMode() {
        val amb = idle.mode == IdleController.Mode.AMBIENT && cfg != null
        ambient.visibility = if (amb) View.VISIBLE else View.GONE
        val lp = window.attributes
        lp.screenBrightness = if (amb) (cfg?.ambientBrightness ?: 0.02f) else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
        NodeBus.screenMode = if (amb) "ambient" else "active"
        if (amb) {
            val c = cfg
            ambientStatus.text = if (c == null) "" else "● ${c.room}"
        }
    }

    // ---------- hidden settings entry: 5 taps in the top-left corner within 3 s ----------

    private fun cornerTap() {
        val t = now()
        if (t - firstCornerTapMs > 3_000) { firstCornerTapMs = t; cornerTaps = 0 }
        cornerTaps++
        if (cornerTaps >= 5) { cornerTaps = 0; askPin() }
    }

    private fun askPin() {
        val pin = cfg?.pin.orEmpty()
        if (pin.isEmpty()) { openSettings(); return }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN"
        }
        AlertDialog.Builder(this)
            .setTitle("Synapse settings")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                if (input.text.toString() == pin) openSettings()
                else Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    // ---------- dashboard ----------

    private fun loadConfigAndPage() {
        val c = ConfigStore.load(this)
        cfg = c
        if (c == null) {
            setup.visibility = View.VISIBLE
            setup.text = "Synapse node — not configured yet\n\n" +
                "Push a config file to:\n/sdcard/Android/data/$packageName/files/config.json\n\n" +
                "(tools/provision/provision.py in the SynapseOS repo does this.)\n\n" +
                "This screen checks for it every 10 seconds."
            return
        }
        setup.visibility = View.GONE
        idle.idleMs = c.idleSeconds * 1000L
        val url = c.dashboardUrl + (if (c.dashboardUrl.contains('?')) "&" else "?") + "external_auth=1"
        web.loadUrl(url)
        applyMode()
    }

    private fun origin(url: String?): String? = try {
        val u = Uri.parse(url ?: return null)
        val port = if (u.port > 0) ":${u.port}" else ""
        "${u.scheme}://${u.host}$port"
    } catch (e: Exception) { null }

    private fun setupWebView() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        web.setBackgroundColor(Color.BLACK)
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                pageOrigin = origin(url)
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) scheduleRetry()
            }
            /** Without this, a crash of WebView's renderer (likely on a 3 GB phone) kills the whole app. */
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                webCrashes++
                android.util.Log.w(SynapseApp.TAG, "WebView renderer gone (crash=${detail.didCrash()}), rebuilding #$webCrashes")
                ui.post { rebuildWebView() }
                return true
            }
        }
        web.addJavascriptInterface(HaBridge(), "externalApp")
    }

    private fun rebuildWebView() {
        val old = web
        root.removeView(old)
        try { old.destroy() } catch (_: Exception) {}
        web = WebView(this)
        setupWebView()
        root.addView(web, 0, FrameLayout.LayoutParams(-1, -1))
        loadConfigAndPage()
    }

    private fun scheduleRetry() {
        if (retryPending) return
        retryPending = true
        ui.postDelayed({ retryPending = false; web.reload() }, 15_000)
    }

    /**
     * Implements Home Assistant's "external auth" bridge (the same one the official
     * Companion app uses), so the dashboard logs in with the node's token. No typing
     * passwords on the panel. Only answers pages served from the configured HA origin.
     */
    inner class HaBridge {
        private val safeName = Regex("^[A-Za-z0-9_$.]{1,64}$")

        private fun callback(payload: String): String? = try {
            (Json.parseObject(payload)["callback"] as? String)?.takeIf { safeName.matches(it) }
        } catch (e: Exception) { null }

        private fun originOk(): Boolean {
            val c = cfg ?: return false
            return pageOrigin != null && pageOrigin == origin(c.haUrl)
        }

        @JavascriptInterface
        fun getExternalAuth(payload: String) {
            val cb = callback(payload) ?: return
            ui.post {
                val c = cfg
                if (c == null || !originOk()) {
                    web.evaluateJavascript("$cb(false);", null)
                } else {
                    val data = Json.write(mapOf("access_token" to c.haToken, "expires_in" to 1800))
                    web.evaluateJavascript("$cb(true, $data);", null)
                }
            }
        }

        @JavascriptInterface
        fun revokeExternalAuth(payload: String) {
            val cb = callback(payload) ?: return
            ui.post { web.evaluateJavascript("$cb(true);", null) }
        }

        @JavascriptInterface
        fun externalBus(message: String) {
            val msg = try { Json.parseObject(message) } catch (e: Exception) { return }
            val id = msg["id"] ?: return
            val result: Any? = when (msg["type"]) {
                "config/get" -> mapOf(
                    "hasSettingsScreen" to false, "canWriteTag" to false, "hasExoPlayer" to false,
                    "canCommissionMatter" to false, "canImportThreadCredentials" to false,
                    "hasAssist" to false, "hasBarCodeScanner" to 0,
                )
                else -> null
            }
            val reply = Json.write(mapOf("id" to id, "type" to "result", "success" to true, "result" to result))
            ui.post { if (originOk()) web.evaluateJavascript("window.externalBus && window.externalBus($reply);", null) }
        }
    }
}

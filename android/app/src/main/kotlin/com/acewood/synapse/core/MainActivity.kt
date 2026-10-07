package com.acewood.synapse.core

import android.animation.ValueAnimator
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
import android.view.animation.DecelerateInterpolator
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
import com.acewood.synapse.logic.Profile
import com.acewood.synapse.logic.Profiles
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
    private lateinit var splash: LinearLayout
    private var home: HomeView? = null
    private var roomPad: RoomPadView? = null
    private var sensorsView: SensorsView? = null
    private var jarvisView: JarvisView? = null
    private var intercomView: IntercomView? = null
    private var audioView: AudioView? = null
    private var drawerView: AppDrawerView? = null
    private var profileLock: ProfileLockView? = null
    private var profiles: Profiles? = null
    private var activeProfile: Profile? = null
    /** Coalesces HA updates: at most one UI refresh per 350 ms, however chatty the house is. */
    private var refreshQueued = false
    private val haListener: () -> Unit = {
        if (!refreshQueued) {
            refreshQueued = true
            ui.postDelayed({
                refreshQueued = false
                home?.let { if (it.visibility == View.VISIBLE) it.refresh() }
                roomPad?.let { if (it.visibility == View.VISIBLE) it.refresh() }
                sensorsView?.let { if (it.visibility == View.VISIBLE) it.refresh() }
                if (ambient.visibility == View.VISIBLE) ambient.refresh(HaRepository.cache, cfg, activeProfile)
            }, 350)
        }
    }
    private var haHomeChip: TextView? = null
    private var brightnessAnim: ValueAnimator? = null
    private var pageReady = false
    private lateinit var ambient: AmbientView
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

    /** Branded loading screen shown over the WebView until the dashboard finishes painting. */
    private fun buildSplash(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.rgb(8, 10, 18))
        addView(TextView(context).apply {
            text = "SYNAPSE"
            textSize = 34f; setTextColor(Color.rgb(120, 170, 255))
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = 0.34f; gravity = Gravity.CENTER
        })
        addView(TextView(context).apply {
            text = "connecting\u2026"
            textSize = 13f; setTextColor(Color.rgb(90, 96, 115))
            letterSpacing = 0.18f; gravity = Gravity.CENTER; setPadding(0, dp(14), 0, 0)
        })
    }

    private fun showSplash(label: String) {
        pageReady = false
        (splash.getChildAt(1) as? TextView)?.text = label
        splash.animate().cancel()
        splash.alpha = 1f
        splash.visibility = View.VISIBLE
    }

    private fun hideSplash() {
        if (splash.visibility != View.VISIBLE) return
        splash.animate().alpha(0f).setDuration(450).withEndAction { splash.visibility = View.GONE }.start()
    }

    companion object {
        /** Process-wide: companions are opened once per boot (or app restart), not on every resume. */
        @Volatile private var companionsLaunched = false
        /** Companions that must stay lock-task-allowed but never need their activity opened. */
        private val SERVICE_ONLY_COMPANIONS = setOf("org.woheller69.ttsengine")
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
        web.visibility = View.GONE

        ambient = AmbientView(this) { switchProfile() }.apply { visibility = View.GONE; alpha = 0f }
        root.addView(ambient, FrameLayout.LayoutParams(-1, -1))

        setup = TextView(this).apply {
            setTextColor(Color.rgb(210, 214, 228)); textSize = 16f
            setPadding(dp(28), dp(64), dp(28), dp(28))
            gravity = Gravity.CENTER_HORIZONTAL
            typeface = Typeface.MONOSPACE
            setBackgroundColor(Color.rgb(10, 12, 20)); visibility = View.GONE
        }
        root.addView(setup, FrameLayout.LayoutParams(-1, -1))

        splash = buildSplash()
        root.addView(splash, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        startForegroundService(Intent(this, NodeService::class.java))
        NodeBus.onCommand(commands)
        loadConfigAndPage()
        ui.post(ticker)
    }

    override fun onDestroy() {
        NodeBus.removeCommand(commands)
        HaRepository.removeListener(haListener)
        jarvisView?.close()
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
        // TTS engines work as a bound service; opening their activity only steals the screen (R-119).
        val apps = cfg?.companionApps.orEmpty().filterNot { it in SERVICE_ONLY_COMPANIONS }
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
        val overlayOpen = listOf(roomPad, sensorsView, jarvisView, intercomView, audioView, drawerView).any { it?.visibility == View.VISIBLE }
        when {
            overlayOpen -> showHome()
            web.visibility == View.VISIBLE && web.canGoBack() -> web.goBack()
            web.visibility == View.VISIBLE -> showHome()
        }
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
            if (ambient.visibility == View.VISIBLE && tickCount % 5 == 0L) hideSystemBars()
            if (tickCount % 60 == 0L && ambient.visibility == View.VISIBLE) {
                ambient.animate().translationX(Random.nextInt(-dp(30), dp(30)).toFloat())
                    .translationY(Random.nextInt(-dp(40), dp(40)).toFloat())
                    .setDuration(2_000).setInterpolator(DecelerateInterpolator()).start()
            }
            if (cfg == null && tickCount % 10 == 0L && ConfigStore.importIfPresent(this@MainActivity)) {
                NodeBus.send(NodeBus.Command.CONFIG_CHANGED)
            }
            ui.postDelayed(this, 1_000)
        }
    }

    private fun applyMode() {
        val amb = idle.mode == IdleController.Mode.AMBIENT && cfg != null
        NodeBus.screenMode = if (amb) "ambient" else "active"
        if (amb) {
            ambient.refresh(HaRepository.cache, cfg, activeProfile)
            ambient.visibility = View.VISIBLE
            ambient.bringToFront()
            hideSystemBars()                                  // nav bar can reappear on relayout; re-hide it
            ambient.animate().alpha(1f).setDuration(500).setInterpolator(DecelerateInterpolator()).start()
            rampBrightness(cfg?.ambientBrightness ?: 0.02f, clearAfter = false, durationMs = 700)
        } else {
            ambient.animate().alpha(0f).setDuration(350).withEndAction {
                if (idle.mode != IdleController.Mode.AMBIENT) ambient.visibility = View.GONE
            }.start()
            ambient.translationX = 0f; ambient.translationY = 0f
            rampBrightness(1f, clearAfter = true, durationMs = 300)
        }
    }

    /** Smoothly ramp the panel brightness instead of snapping. clearAfter hands control back to the system. */
    private fun rampBrightness(target: Float, clearAfter: Boolean, durationMs: Long) {
        brightnessAnim?.cancel()
        val lp = window.attributes
        val from = if (lp.screenBrightness in 0f..1f) lp.screenBrightness else 1f
        brightnessAnim = ValueAnimator.ofFloat(from, target).apply {
            duration = durationMs
            addUpdateListener {
                val a = window.attributes
                a.screenBrightness = it.animatedValue as Float
                window.attributes = a
            }
            if (clearAfter) addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    val a = window.attributes
                    a.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.attributes = a
                }
            })
            start()
        }
    }

    // ---------- hidden settings entry: 5 taps in the top-left corner within 3 s ----------

    private fun cornerTap() {
        val t = now()
        if (t - firstCornerTapMs > 3_000) { firstCornerTapMs = t; cornerTaps = 0 }
        cornerTaps++
        if (cornerTaps >= 5) { cornerTaps = 0; askPin() }
    }

    private fun askPin(onOk: () -> Unit = { openSettings() }) {
        val pin = cfg?.pin.orEmpty()
        if (pin.isEmpty()) { onOk(); return }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN"
        }
        AlertDialog.Builder(this)
            .setTitle("Synapse settings")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                if (input.text.toString() == pin) onOk()
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
            hideSplash()
            setup.text = "S Y N A P S E\n\n" +
                "node ready — not configured\n\n" +
                "run  tools/provision/provision.py\nfrom the SynapseOS repo on your PC\n\n" +
                "checking for config every 10s…"
            return
        }
        setup.visibility = View.GONE
        profiles = ProfileStore.loadOrCreate(this, c)
        // Mason's remote rule: remember the last profile (no "who are you?" after every reboot/update).
        if (activeProfile == null) {
            val last = ProfileStore.lastProfileId(this)
            activeProfile = profiles?.list?.firstOrNull { it.id == last }
        }
        idle.idleMs = c.idleSeconds * 1000L
        // Native Glass home (default). HA Lovelace stays loaded underneath as the "HA" tab.
        if (home == null) {
            home = HomeView(this, c,
                onOpenRoom = { room -> showRoomPad(room) },
                onMic = { openAssist() },
                onHome = { showHome(); home?.scrollToTop() },
                onRooms = { showHome(); home?.scrollToRooms() },
                onAudio = { showOverlay(audioView) { it.open() } },
                onHa = { showHa() },
                onApps = { showOverlay(drawerView) { it.open() } },
                onSensors = { showOverlay(sensorsView) { it.open() } },
                onLaunch = { app -> launchApp(app) },
                onSwitchProfile = { switchProfile() },
                onIntercom = { showOverlay(intercomView) { it.open() } }).also { h ->
                root.addView(h, FrameLayout.LayoutParams(-1, -1))
            }
            HaRepository.onChange(haListener)
        }
        if (roomPad == null) {
            roomPad = RoomPadView(this, onBack = { showHome() }).also { p ->
                p.visibility = View.GONE
                root.addView(p, FrameLayout.LayoutParams(-1, -1))
            }
        }
        if (sensorsView == null) {
            sensorsView = SensorsView(this, c.nodeId, onBack = { showHome() }).also { v ->
                v.visibility = View.GONE; root.addView(v, FrameLayout.LayoutParams(-1, -1))
            }
        }
        if (jarvisView == null) {
            jarvisView = JarvisView(this, onBack = { showHome() }, onVoice = { openAssist() },
                onIntercom = { showOverlay(intercomView) { it.open() } }).also { v ->
                v.visibility = View.GONE; root.addView(v, FrameLayout.LayoutParams(-1, -1))
            }
        }
        if (intercomView == null) {
            intercomView = IntercomView(this, onBack = { showHome() }).also { v ->
                v.visibility = View.GONE; root.addView(v, FrameLayout.LayoutParams(-1, -1))
            }
        }
        if (audioView == null) {
            audioView = AudioView(this, onBack = { showHome() }, onMusic = { showHa("/media-browser/browser") }).also { v ->
                v.visibility = View.GONE; root.addView(v, FrameLayout.LayoutParams(-1, -1))
            }
        }
        if (drawerView == null) {
            drawerView = AppDrawerView(this, onBack = { showHome() }, onLaunch = { app -> launchApp(app) },
                activeProfile = { activeProfile }, onAllOff = { home?.allOffNow() }).also { v ->
                v.visibility = View.GONE; root.addView(v, FrameLayout.LayoutParams(-1, -1))
            }
        }
        if (haHomeChip == null) {
            haHomeChip = TextView(this).apply {
                text = "⌂  SYNAPSE"; textSize = 12f; setTextColor(Glass.INK); typeface = Glass.disp(this@MainActivity)
                letterSpacing = 0.1f; gravity = Gravity.CENTER
                background = Glass.panel(this@MainActivity, 999f, android.graphics.Color.argb(180, 10, 16, 36))
                val padH = Glass.dp(this@MainActivity, 16f); val padV = Glass.dp(this@MainActivity, 9f)
                setPadding(padH, padV, padH, padV)
                visibility = View.GONE
                tap { showHome() }
                root.addView(this, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
                    topMargin = Glass.dp(this@MainActivity, 14f); rightMargin = Glass.dp(this@MainActivity, 14f)
                })
            }
        }
        applyProfileToHome()
        home?.refresh()
        if (profiles != null && activeProfile == null) showProfileLock() else showHome()
        hideSplash()
        val url = c.dashboardUrl + (if (c.dashboardUrl.contains('?')) "&" else "?") + "external_auth=1"
        haPathLoaded = null
        web.loadUrl(url)
        applyMode()
    }

    /** Tap the profile badge: forget the current profile and show the picker. */
    private fun switchProfile() {
        if (profiles == null) return
        activeProfile = null; ProfileStore.rememberProfile(this, null); showProfileLock()
    }

    private fun applyProfileToHome() {
        val p = activeProfile ?: return
        home?.setProfile(p)
    }

    private fun showProfileLock() {
        val ps = profiles ?: return showHome()
        if (profileLock == null) {
            profileLock = ProfileLockView(this, ps) { profile ->
                activeProfile = profile
                ProfileStore.rememberProfile(this, profile.id)
                applyProfileToHome()
                profileLock?.visibility = View.GONE
                showHome()
            }.also { root.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        }
        hideOverlays()
        web.visibility = View.GONE
        home?.visibility = View.GONE
        profileLock?.visibility = View.VISIBLE
        profileLock?.bringToFront()
    }

    private fun hideOverlays() {
        roomPad?.visibility = View.GONE; sensorsView?.visibility = View.GONE
        jarvisView?.visibility = View.GONE; intercomView?.visibility = View.GONE; audioView?.visibility = View.GONE; drawerView?.visibility = View.GONE
    }
    private fun showHome() {
        if (profiles != null && activeProfile == null) { showProfileLock(); return }
        hideOverlays(); home?.let { it.visibility = View.VISIBLE; it.bringToFront(); it.refresh() }; web.visibility = View.GONE
        haHomeChip?.visibility = View.GONE
        ambient.bringToFront(); splash.bringToFront() }
    /** Show one full-screen Synapse overlay (sensors, Jarvis, app drawer) over the home screen. */
    private fun <T : View> showOverlay(v: T?, prepare: (T) -> Unit = {}) {
        if (v == null) return
        hideOverlays(); home?.visibility = View.GONE; web.visibility = View.GONE; haHomeChip?.visibility = View.GONE
        v.visibility = View.VISIBLE; v.bringToFront(); prepare(v)
        ambient.bringToFront(); splash.bringToFront()
    }
    /** Open a dock/drawer entry: a Synapse screen, a kiosk-allowed Android app, or (admin) a PIN-gated one. */
    private fun launchApp(app: AppCatalog.App) {
        val run = {
            when (val t = app.target) {
                is AppCatalog.Target.Internal -> when (t.id) {
                    "jarvis" -> showOverlay(jarvisView)
                    "audio" -> showOverlay(audioView) { it.open() }
                    "sensors" -> showOverlay(sensorsView) { it.open() }
                    "ha" -> showHa()
                    "music" -> showHa("/media-browser/browser")
                    "browser" -> startActivity(Intent(this, BrowserActivity::class.java))
                    "camera" -> startActivity(Intent(this, CameraActivity::class.java))
                    "settings" -> openSettings()
                }
                is AppCatalog.Target.Pkg -> {
                    if (app.adminOnly) { Kiosk.pauseKiosk(); Kiosk.applyPolicies(this) }
                    if (!AppCatalog.launch(this, t.pkg)) Toast.makeText(this, "${app.label} isn't available", Toast.LENGTH_SHORT).show()
                }
            }
        }
        if (app.adminOnly) askPin { run() } else run()
    }
    private fun showRoomPad(room: com.acewood.synapse.logic.Room) = showOverlay(roomPad) {
        val allowed = activeProfile?.layout?.rooms.orEmpty().map { it.lowercase() }.toSet()
        val rooms = if (allowed.isEmpty()) HaRepository.rooms else HaRepository.rooms.filter { it.id.lowercase() in allowed }
        it.open(room, rooms)
    }
    private var haPathLoaded: String? = null
    private fun showHa(path: String? = null) {
        val c = cfg
        if (c != null && path != haPathLoaded) {
            haPathLoaded = path
            val base = if (path == null) c.dashboardUrl else c.haUrl.trimEnd('/') + path
            web.loadUrl(base + (if (base.contains('?')) "&" else "?") + "external_auth=1")
        }
        hideOverlays(); web.visibility = View.VISIBLE; web.bringToFront(); home?.visibility = View.GONE
        haHomeChip?.let { it.visibility = View.VISIBLE; it.bringToFront() }
        ambient.bringToFront(); splash.bringToFront() }
    private fun openAssist() {
        showOverlay(jarvisView)
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 7001)
        } else jarvisView?.beginVoice()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7001 && grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) jarvisView?.beginVoice()
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
            override fun onPageFinished(view: WebView, url: String?) {
                // Give HA's SPA a moment to paint the dashboard, then fade the splash out.
                if (!pageReady && origin(url) == origin(cfg?.haUrl)) {
                    pageReady = true
                    ui.postDelayed({ hideSplash() }, 700)
                }
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

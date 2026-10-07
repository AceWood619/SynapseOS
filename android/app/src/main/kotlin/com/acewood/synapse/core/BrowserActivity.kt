package com.acewood.synapse.core

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.net.URLEncoder

/** Lightweight in-kiosk browser for HA and local smart-home pages. */
class BrowserActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var address: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val g = Glass
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = g.ground()
            setPadding(g.dp(this@BrowserActivity, 10f), g.dp(this@BrowserActivity, 10f),
                g.dp(this@BrowserActivity, 10f), 0)
        }
        val bar = g.row(this).apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(TextView(this).apply {
            text = "⌂"; textSize = 22f; setTextColor(Glass.BLUE); gravity = Gravity.CENTER
            setPadding(0, 0, g.dp(this@BrowserActivity, 10f), 0)
            tap { loadHome() }
        })
        address = EditText(this).apply {
            hint = "Search or enter address"; textSize = 13f; setSingleLine(true)
            setTextColor(Glass.INK); setHintTextColor(Glass.INK_FAINT)
            setPadding(g.dp(this@BrowserActivity, 12f), 0, g.dp(this@BrowserActivity, 8f), 0)
            background = g.panel(this@BrowserActivity, 999f)
            setOnEditorActionListener { _, _, _ -> navigate(); true }
        }
        bar.addView(address, LinearLayout.LayoutParams(0, g.dp(this, 46f), 1f))
        bar.addView(browserButton("↻") { web.reload() })
        bar.addView(browserButton("‹") { if (web.canGoBack()) web.goBack() })
        bar.addView(browserButton("›") { if (web.canGoForward()) web.goForward() })
        root.addView(bar)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            setBackgroundColor(Color.BLACK)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) { address.setText(url) }
            }
            webChromeClient = WebChromeClient()
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        loadHome()
    }

    private fun browserButton(label: String, action: () -> Unit): View = TextView(this).apply {
        text = label; textSize = 18f; setTextColor(Glass.INK); gravity = Gravity.CENTER
        setPadding(Glass.dp(this@BrowserActivity, 9f), 0, Glass.dp(this@BrowserActivity, 9f), 0)
        tap { action() }
    }

    private fun loadHome() {
        // The HA dashboard needs the node's auth bridge (only MainActivity has it), so start on the web.
        val url = "https://www.google.com"
        address.setText(url); web.loadUrl(url)
    }

    private fun navigate() {
        val raw = address.text.toString().trim()
        if (raw.isEmpty()) return
        val url = when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.contains('.') && !raw.contains(' ') -> "https://$raw"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(raw, "UTF-8")
        }
        web.loadUrl(url)
    }

    @Deprecated("Back is handled by the browser history")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}

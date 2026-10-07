package com.acewood.synapse.core

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Jarvis chat: type (or tap a quick prompt) and Home Assistant's Assist pipeline answers. It's the same
 * brain as the voice satellites, so "turn off the hallway light" actually does it. Replies can be
 * spoken on this panel. The mic button hands off to the voice assistant.
 */
@SuppressLint("ViewConstructor")
class JarvisView(context: Context, private val onBack: () -> Unit, private val onVoice: () -> Unit) : FrameLayout(context) {
    private val g = Glass
    private val log = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(log) }
    private val input = EditText(context)
    private var conversationId: String? = null
    private var speakReplies = true
    private var speech: Speech? = null
    private val speakKey = TextView(context)

    private val prompts = listOf(
        "Turn off all the lights", "Is anyone home?", "What's the weather?", "Turn on the hallway light",
        "Pause the living room TV", "What time is it?",
    )

    init {
        background = g.ground()
        val pad = g.dp(context, 16f)
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, g.dp(context, 14f), pad, g.dp(context, 14f))
        }
        addView(outer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        outer.addView(g.row(context).apply {
            addView(key("‹  BACK", Glass.INK_DIM) { hideKeyboard(); onBack() })
            addView(g.spacer(context, w = 14))
            addView(g.col(context).apply {
                addView(TextView(context).apply { text = "Jarvis"; textSize = 22f; setTextColor(Glass.INK); typeface = g.disp(context) })
                addView(TextView(context).apply { text = "Ask or tell the house anything"; textSize = 12f; setTextColor(Glass.INK_DIM) })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            speakKey.apply {
                textSize = 12f; typeface = g.disp(context); gravity = Gravity.CENTER
                setPadding(g.dp(context, 12f), g.dp(context, 9f), g.dp(context, 12f), g.dp(context, 9f))
                tap { speakReplies = !speakReplies; paintSpeak() }
            }
            paintSpeak(); addView(speakKey)
        })
        outer.addView(g.spacer(context, h = 10))
        outer.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        outer.addView(g.spacer(context, h = 8))
        outer.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                prompts.forEach { p -> addView(chip(p) { send(p) }) }
            })
        })
        outer.addView(g.spacer(context, h = 8))
        outer.addView(g.row(context).apply {
            input.apply {
                hint = "Message Jarvis…"; setHintTextColor(Glass.INK_FAINT); setTextColor(Glass.INK); textSize = 15f
                background = g.panel(context, 16f)
                setPadding(g.dp(context, 14f), g.dp(context, 12f), g.dp(context, 14f), g.dp(context, 12f))
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = EditorInfo.IME_ACTION_SEND
                setOnEditorActionListener { _, action, ev ->
                    if (action == EditorInfo.IME_ACTION_SEND || ev?.keyCode == KeyEvent.KEYCODE_ENTER) { sendInput(); true } else false
                }
            }
            addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(g.spacer(context, w = 8))
            addView(key("SEND", Glass.BLUE) { sendInput() })
            addView(g.spacer(context, w = 8))
            addView(key("🎤", Glass.VIOLET) { hideKeyboard(); onVoice() })
        })
        bubble("Hi, I'm Jarvis. Tap a suggestion or type a command.", mine = false)
    }

    private fun paintSpeak() {
        speakKey.text = if (speakReplies) "🔊 ON" else "🔇 OFF"
        speakKey.setTextColor(if (speakReplies) Glass.MINT else Glass.INK_FAINT)
        speakKey.background = g.tile(context, Glass.MINT, speakReplies, 12f)
    }

    private fun sendInput() {
        val t = input.text?.toString()?.trim().orEmpty()
        if (t.isEmpty()) return
        input.setText(""); send(t)
    }

    private fun send(text: String) {
        bubble(text, mine = true)
        val thinking = bubble("…", mine = false)
        HaRepository.converse(text, conversationId) { reply, conv ->
            post {
                conversationId = conv
                thinking.text = reply
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                if (speakReplies && reply != "(no reply)") {
                    val s = speech ?: Speech(context.applicationContext).also { speech = it }
                    try { s.speak(reply) } catch (_: Exception) {}
                }
            }
        }
    }

    private fun bubble(text: String, mine: Boolean): TextView {
        val tv = TextView(context).apply {
            this.text = text; textSize = 14.5f; setTextColor(if (mine) Glass.INK else Glass.INK)
            background = if (mine) g.tile(context, Glass.BLUE, true, 16f) else g.panel(context, 16f)
            setPadding(g.dp(context, 13f), g.dp(context, 10f), g.dp(context, 13f), g.dp(context, 10f))
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = if (mine) Gravity.END else Gravity.START
            topMargin = g.dp(context, 7f)
            if (mine) leftMargin = g.dp(context, 48f) else rightMargin = g.dp(context, 48f)
        }
        log.addView(tv, lp)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        return tv
    }

    private fun chip(label: String, onTap: () -> Unit): View = TextView(context).apply {
        text = label; textSize = 12.5f; setTextColor(Glass.BLUE); typeface = g.body(context)
        background = g.tile(context, Glass.BLUE, false, 999f)
        setPadding(g.dp(context, 13f), g.dp(context, 8f), g.dp(context, 13f), g.dp(context, 8f))
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = g.dp(context, 8f) }
        tap { onTap() }
    }

    private fun key(text: String, color: Int, onTap: () -> Unit): View = TextView(context).apply {
        setText(text); textSize = 12f; setTextColor(color); typeface = g.disp(context); gravity = Gravity.CENTER; letterSpacing = 0.06f
        background = g.tile(context, color, false, 14f)
        setPadding(g.dp(context, 14f), g.dp(context, 12f), g.dp(context, 14f), g.dp(context, 12f))
        tap { onTap() }
    }

    private fun hideKeyboard() {
        try { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(windowToken, 0) } catch (_: Exception) {}
    }

    fun close() { hideKeyboard(); try { speech?.shutdown() } catch (_: Exception) {}; speech = null }
}

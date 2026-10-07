package com.acewood.synapse.core

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import com.acewood.synapse.logic.AssistPipeline
import com.acewood.synapse.logic.AssistVad

/**
 * Jarvis chat: type (or tap a quick prompt) and Home Assistant's Assist pipeline answers. It's the same
 * brain as the voice satellites, so "turn off the hallway light" actually does it. Replies can be
 * spoken on this panel. The mic button hands off to the voice assistant.
 */
@SuppressLint("ViewConstructor")
class JarvisView(context: Context, private val onBack: () -> Unit, private val onVoice: () -> Unit,
                 private val onIntercom: () -> Unit = {}) : FrameLayout(context) {
    private val g = Glass
    private val log = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(log) }
    private val input = EditText(context)
    private var conversationId: String? = null
    private var speakReplies = true
    private var speech: Speech? = null
    @Volatile private var recording = false
    private var audioRecord: AudioRecord? = null
    private var audioThread: Thread? = null
    private var micKey: TextView? = null
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
                addView(chip("Announce to a room") { onIntercom() })
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
            micKey = key("MIC", Glass.VIOLET) { hideKeyboard(); if (recording) stopVoice() else onVoice() } as TextView
            addView(micKey)
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

    /** Streams the microphone to Home Assistant's Wyoming-backed Assist pipeline. */
    fun beginVoice() {
        if (recording) { stopVoice(); return }
        input.hint = "Listening to HA Assist…"
        micKey?.text = "STOP"
        bubble("Listening…", mine = false)
        HaRepository.startAssist(
            onReady = { handlerId -> post { startAudio(handlerId) } },
            onEvent = { type, data -> post { handleAssistEvent(type, data) } },
            onError = { message -> post { recording = false; micKey?.text = "MIC"; input.hint = "Message Jarvis…"; bubble(message, mine = false) } },
        )
    }

    private fun startAudio(handlerId: Int) {
        if (recording) return
        val min = AudioRecord.getMinBufferSize(AssistPipeline.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) { HaRepository.stopAssist(); input.hint = "Message Jarvis…"; micKey?.text = "MIC"; bubble("The microphone is unavailable.", mine = false); return }
        val record = AudioRecord(MediaRecorder.AudioSource.MIC, AssistPipeline.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, (min * 2).coerceAtLeast(4096))
        audioRecord = record; recording = true
        record.startRecording()
        audioThread = Thread {
            val samples = ShortArray(1024); val vad = AssistVad()
            while (recording) {
                val count = record.read(samples, 0, samples.size)
                if (count <= 0) continue
                val pcm = ByteArray(count * 2)
                for (i in 0 until count) { pcm[i * 2] = (samples[i].toInt() and 0xff).toByte(); pcm[i * 2 + 1] = (samples[i].toInt() shr 8).toByte() }
                HaRepository.sendAssistAudio(handlerId, pcm)
                if (vad.accept(pcm).shouldStop) post { stopVoice() }
            }
        }.apply { name = "synapse-assist-mic"; start() }
    }

    private fun handleAssistEvent(type: String, data: Map<String, Any?>?) {
        when (type) {
            "stt-end" -> input.hint = "Jarvis is thinking…"
            "intent-end" -> {
                val reply = AssistPipeline.speechFromIntent(data) ?: "I didn't get a response from Home Assistant."
                bubble(reply, mine = false)
                if (speakReplies) { val s = speech ?: Speech(context.applicationContext).also { speech = it }; try { s.speak(reply) } catch (_: Exception) {} }
                input.hint = "Message Jarvis…"; micKey?.text = "MIC"
            }
            "run-end", "error" -> { recording = false; input.hint = "Message Jarvis…"; micKey?.text = "MIC" }
        }
    }

    private fun stopVoice() {
        if (!recording) { HaRepository.stopAssist(); return }
        recording = false
        try { audioRecord?.stop() } catch (_: Exception) {}
        audioRecord?.release(); audioRecord = null; audioThread = null
        HaRepository.stopAssist(); input.hint = "Processing…"; micKey?.text = "MIC"
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

    fun close() { hideKeyboard(); stopVoice(); try { speech?.shutdown() } catch (_: Exception) {}; speech = null }
}

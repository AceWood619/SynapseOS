package com.acewood.synapse.core

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.UUID

/** Wraps the system TTS engine (SherpaTTS once installed). */
class Speech(private val appCtx: Context) {
    @Volatile var ready = false
        private set
    @Volatile var lastStatus: Int = -99
        private set
    private var tts: TextToSpeech = newEngine()

    private fun newEngine(): TextToSpeech = TextToSpeech(appCtx.applicationContext) { status ->
        lastStatus = status
        ready = status == TextToSpeech.SUCCESS
        if (ready) android.util.Log.i(SynapseApp.TAG, "TTS ready, engine=" + (try { tts.defaultEngine } catch (e: Exception) { "?" }))
        else android.util.Log.w(SynapseApp.TAG, "TTS init failed status=$status")
    }

    /** Re-init if the engine never came up (e.g. voice installed after boot, or default changed). */
    fun reinitIfNeeded() {
        if (ready) return
        try { tts.shutdown() } catch (_: Exception) {}
        tts = newEngine()
        applyAttrs()
    }

    init { applyAttrs() }

    private fun applyAttrs() {
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
    }

    val engine: String? get() = try { tts.defaultEngine } catch (e: Exception) { null }

    fun speak(text: String): Boolean {
        if (!ready) return false
        return tts.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString()) == TextToSpeech.SUCCESS
    }

    fun shutdown() = tts.shutdown()
}

package com.acewood.synapse.core

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.UUID

/** Wraps the system TTS engine (SherpaTTS once installed). */
class Speech(ctx: Context) {
    @Volatile var ready = false
        private set
    private val tts: TextToSpeech = TextToSpeech(ctx.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    init {
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

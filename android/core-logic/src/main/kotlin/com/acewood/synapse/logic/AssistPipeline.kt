package com.acewood.synapse.logic

import kotlin.math.sqrt

/** Pure helpers for Home Assistant Assist pipeline microphone streaming. */
object AssistPipeline {
    const val SAMPLE_RATE = 16_000
    const val CHANNELS = 1
    const val BYTES_PER_SAMPLE = 2

    /** HA expects each binary websocket frame to start with the one-byte handler id. */
    fun binaryFrame(handlerId: Int, pcm: ByteArray): ByteArray =
        ByteArray(pcm.size + 1).also {
            it[0] = (handlerId and 0xff).toByte()
            pcm.copyInto(it, destinationOffset = 1)
        }

    /** HA's intent-end payload nests speech under response.speech.plain.speech. */
    @Suppress("UNCHECKED_CAST")
    fun speechFromIntent(data: Map<String, Any?>?): String? {
        fun find(value: Any?): String? {
            when (value) {
                is Map<*, *> -> {
                    val speech = value["speech"]
                    if (speech is String && speech.isNotBlank()) return speech
                    value.values.forEach { find(it)?.let { result -> return result } }
                }
                is List<*> -> value.forEach { find(it)?.let { result -> return result } }
            }
            return null
        }
        return find(data)?.trim()
    }
}

/** Small energy-based VAD suitable for a 16 kHz, 16-bit mono AudioRecord stream. */
class AssistVad(
    private val threshold: Double = 650.0,
    private val requiredSilenceMs: Long = 750L,
    private val sampleRate: Int = AssistPipeline.SAMPLE_RATE,
) {
    private var speechSeen = false
    private var silentMs = 0L

    data class Decision(val rms: Double, val speechStarted: Boolean, val shouldStop: Boolean)

    fun accept(pcm: ByteArray): Decision {
        if (pcm.size < 2) return Decision(0.0, false, false)
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < pcm.size) {
            val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xff)).toShort().toInt()
            sum += sample * sample.toDouble(); count++; i += 2
        }
        val rms = if (count == 0) 0.0 else sqrt(sum / count)
        val wasSeen = speechSeen
        val durationMs = count * 1000L / sampleRate
        if (rms >= threshold) { speechSeen = true; silentMs = 0L }
        else if (speechSeen) silentMs += durationMs
        return Decision(rms, !wasSeen && speechSeen, speechSeen && silentMs >= requiredSilenceMs)
    }
}

package com.acewood.synapse.logic

/** Local, non-secret preferences for the physical Synapse panel. */
data class PanelSettings(
    val idleSeconds: Int = 120,
    val ambientBrightness: Float = 0.02f,
    val nightDimEnabled: Boolean = false,
    val nightStart: String = "21:00",
    val nightEnd: String = "06:00",
    val nightBrightness: Float = 0.01f,
    val hapticsEnabled: Boolean = true,
    val speakReplies: Boolean = true,
) {
    fun validate(): List<String> = buildList {
        if (idleSeconds !in 10..86_400) add("idle_seconds must be 10..86400")
        if (ambientBrightness !in 0f..1f) add("ambient_brightness must be 0..1")
        if (nightBrightness !in 0f..1f) add("night_brightness must be 0..1")
        if (!TIME.matches(nightStart)) add("night_start must be HH:MM")
        if (!TIME.matches(nightEnd)) add("night_end must be HH:MM")
    }

    fun effectiveAmbientBrightness(hour: Int, minute: Int): Float {
        if (!nightDimEnabled || !TIME.matches(nightStart) || !TIME.matches(nightEnd)) return ambientBrightness
        val now = hour * 60 + minute
        val start = minutes(nightStart)
        val end = minutes(nightEnd)
        val active = if (start == end) true else if (start < end) now in start until end else now >= start || now < end
        return if (active) nightBrightness else ambientBrightness
    }

    private fun minutes(value: String): Int {
        val p = value.split(':')
        return p[0].toInt() * 60 + p[1].toInt()
    }

    companion object {
        private val TIME = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")
    }
}

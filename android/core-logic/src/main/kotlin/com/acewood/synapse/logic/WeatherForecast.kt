package com.acewood.synapse.logic

/** Forecast bucket normalized from Home Assistant weather.get_forecasts responses. */
data class ForecastPoint(
    val time: String,
    val condition: String,
    val temperature: Double?,
    val precipitation: Double?,
)

data class WeatherForecast(
    val entityId: String,
    val kind: String,
    val points: List<ForecastPoint>,
) {
    fun hourly(limit: Int = 8): List<ForecastPoint> = points.take(limit)
    fun daily(limit: Int = 7): List<ForecastPoint> = points.take(limit)
}

object WeatherForecastParser {
    /** Accepts HA's response map: {"weather.entity": {"forecast": [...]}}. */
    @Suppress("UNCHECKED_CAST")
    fun parse(response: Map<String, Any?>?, kind: String): WeatherForecast? {
        if (response == null) return null
        val envelope = (response["response"] as? Map<String, Any?>) ?: response
        val entry = envelope.entries.firstOrNull { it.value is Map<*, *> } ?: return null
        val body = entry.value as? Map<String, Any?> ?: return null
        val raw = body["forecast"] as? List<*> ?: return null
        val points = raw.mapNotNull { item ->
            val m = item as? Map<String, Any?> ?: return@mapNotNull null
            val time = (m["datetime"] ?: m["period"] ?: "").toString()
            if (time.isBlank()) return@mapNotNull null
            ForecastPoint(
                time = time,
                condition = (m["condition"] as? String ?: "").replace('-', ' '),
                temperature = number(m["temperature"]),
                precipitation = number(m["precipitation_probability"] ?: m["precipitation"]),
            )
        }
        return WeatherForecast(entry.key, kind, points)
    }

    private fun number(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

    fun shortTime(value: String): String = value
        .substringAfter('T', value)
        .substringBefore('+')
        .substringBefore('Z')
        .take(5)
        .ifBlank { value.take(10) }

    fun day(value: String): String = value.substringBefore('T').substringAfterLast('-').let { value.substringBefore('T').takeLast(5) }
}

package com.acewood.synapse.logic

/** Pure discovery helpers for R-124's optional lighting and holiday controls. */
object LightingFx {
    val fxCandidates = listOf(
        "script.ace_fx_ember" to "Ember",
        "script.ace_fx_breathe" to "Breathe",
        "script.ace_fx_sunrise" to "Sunrise",
        "script.ace_fx_drift" to "Drift",
        "script.ace_fx_winddown" to "Wind Down",
        "script.ace_fx_stop" to "Stop FX",
        "script.ivy_apply" to "Apply Ivy",
    )

    fun available(cache: EntityCache?): List<Pair<String, String>> =
        fxCandidates.filter { cache?.get(it.first) != null }

    fun holiday(cache: EntityCache?): Pair<String, List<String>>? {
        val select = cache?.all()?.firstOrNull { it.entityId == "input_select.holiday_mode" } ?: return null
        val options = (select.attributes["options"] as? List<*>)?.mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) }.orEmpty()
        return select.entityId to options
    }

    fun holidayApplyId(cache: EntityCache?): String? =
        cache?.all()?.firstOrNull { it.entityId == "script.holiday_apply" }?.entityId
}

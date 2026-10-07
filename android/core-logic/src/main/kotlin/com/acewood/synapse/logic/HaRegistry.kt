package com.acewood.synapse.logic

/**
 * Turns Home Assistant's three registry lists (areas, entities, devices) into [Area]s for the
 * room channels — so the remote learns the house from HA itself instead of a hand-written map.
 *
 * An entity's area is its own `area_id` if set, otherwise its device's `area_id` (HA's own rule).
 * Disabled/hidden entities and config/diagnostic ones are skipped so the pads show real controls.
 * Pure + testable; the WebSocket client just feeds it the raw result rows.
 */
object HaRegistry {

    fun buildAreas(
        areaRows: List<Map<String, Any?>>,
        entityRows: List<Map<String, Any?>>,
        deviceRows: List<Map<String, Any?>>,
    ): List<Area> {
        // device_id -> area_id (for entities that inherit their area from the device)
        val deviceArea = HashMap<String, String>()
        for (d in deviceRows) {
            val id = d["id"] as? String ?: continue
            (d["area_id"] as? String)?.let { deviceArea[id] = it }
        }

        // area_id -> name (keep HA's order)
        val areaName = LinkedHashMap<String, String>()
        for (a in areaRows) {
            val id = a["area_id"] as? String ?: continue
            areaName[id] = (a["name"] as? String)?.trim()?.ifBlank { null } ?: prettyId(id)
        }

        // area_id -> entity ids, in registry order
        val byArea = LinkedHashMap<String, MutableList<String>>()
        for (e in entityRows) {
            val entityId = e["entity_id"] as? String ?: continue
            if (e["disabled_by"] != null || e["hidden_by"] != null) continue
            val cat = e["entity_category"] as? String
            if (cat == "config" || cat == "diagnostic") continue
            val area = (e["area_id"] as? String)
                ?: (e["device_id"] as? String)?.let { deviceArea[it] }
                ?: continue
            if (area !in areaName) continue
            byArea.getOrPut(area) { mutableListOf() }.add(entityId)
        }

        // Emit areas in HA's area order; include only areas that actually have entities.
        return areaName.entries.mapNotNull { (id, name) ->
            val ents = byArea[id] ?: return@mapNotNull null
            Area(id, name, ents)
        }
    }

    private fun prettyId(id: String): String =
        id.replace('_', ' ').split(' ').filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

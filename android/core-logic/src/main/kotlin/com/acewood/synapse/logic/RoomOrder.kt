package com.acewood.synapse.logic

/**
 * Smart room ordering — "a smart-home remote should be smart." Instead of a fixed list, the room
 * channels reorder by context: the room the panel is in comes first, then rooms with something
 * happening (lights on / media playing), then a time-of-day nudge (bedrooms at night), then the rest.
 * Ordering is stable (no flicker) because ties fall back to the configured/default order.
 */
object RoomOrder {
    fun order(
        rooms: List<Room>,
        hereRoomId: String?,            // the panel's own room (presence / config)
        cache: EntityCache,
        hourOfDay: Int,                 // 0..23, for the night nudge
        baseOrder: List<String> = emptyList(),
    ): List<Room> {
        val baseRank = baseOrder.withIndex().associate { (i, id) -> id to i }
        fun active(r: Room): Boolean =
            r.lights.any { cache.get(it)?.on == true } ||
            r.switches.any { cache.get(it)?.on == true } ||
            r.media.any { cache.get(it)?.state == "playing" }
        val night = hourOfDay >= 21 || hourOfDay < 6
        fun isBedroom(r: Room) = r.id.contains("bedroom") || r.name.contains("bedroom", true) ||
            r.id.contains("kids") || r.name.contains("kids", true)

        // Lower score = earlier. Deterministic tiers; base order breaks ties.
        fun score(r: Room): Int {
            if (hereRoomId != null && r.id == hereRoomId) return 0
            if (active(r)) return 1
            if (night && isBedroom(r)) return 2
            return 3
        }
        return rooms.sortedWith(
            compareBy({ score(it) }, { baseRank[it.id] ?: Int.MAX_VALUE }, { it.name })
        )
    }
}

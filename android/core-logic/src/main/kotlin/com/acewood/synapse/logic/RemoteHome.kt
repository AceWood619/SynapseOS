package com.acewood.synapse.logic

/**
 * Model for the "Remote" home screen (redesign option B, picked by Mason 2026-10-16).
 *
 * The phone acts like a TV remote for the house: one room is "tuned in" (the channel). The VOL
 * rocker, D-pad and side keys drive that room's media; the room keys below toggle its lights and TV.
 * Channel numbers are stable (room order from HA/config), so CH 3 is always the same room.
 */
object RemoteHome {

    data class Channel(
        val index: Int,
        val count: Int,
        val room: Room,
        /** "CH 3". */
        val label: String,
        /** One readable line: "HBO Max playing · 1 light on", "All off". */
        val status: String,
        val mediaId: String?,
        val remoteId: String?,
        val caps: MediaCaps,
        val isPlaying: Boolean,
        val mediaOn: Boolean,
        /** Lights + plug/lamp switches, as command targets (healthy path of each paired light). */
        val lightTargets: List<String>,
        val lightsTotal: Int,
        val lightsOn: Int,
        /** D-pad works: a Roku-style remote, or at least a media player for play/skip. */
        val hasDpad: Boolean,
    ) {
        val anyLightOn: Boolean get() = lightsOn > 0
        val hasLights: Boolean get() = lightsTotal > 0
        val hasMedia: Boolean get() = mediaId != null
    }

    fun wrap(i: Int, n: Int): Int = if (n <= 0) 0 else ((i % n) + n) % n

    /**
     * Which channel to show first: a remembered room if still present, else a room with media
     * playing, else the panel's own room, else channel 1.
     */
    fun startIndex(rooms: List<Room>, rememberedRoomId: String?, panelRoomId: String?, cache: EntityCache?): Int {
        if (rooms.isEmpty()) return 0
        rememberedRoomId?.let { id -> rooms.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { return it } }
        if (cache != null) rooms.indexOfFirst { r -> r.media.any { cache.get(it)?.state == "playing" } }.takeIf { it >= 0 }?.let { return it }
        val here = panelRoomId?.trim()?.lowercase()?.replace(' ', '_')
        if (here != null) rooms.indexOfFirst { it.id.lowercase() == here }.takeIf { it >= 0 }?.let { return it }
        return 0
    }

    fun channel(rooms: List<Room>, index: Int, cache: EntityCache): Channel? {
        if (rooms.isEmpty()) return null
        val i = wrap(index, rooms.size)
        val room = rooms[i]
        val model = RoomControl.build(room, cache)

        // Media: the one playing, else the first that isn't off/unavailable, else the first.
        val media = model.media.firstOrNull { it.isPlaying }
            ?: model.media.firstOrNull { it.state != "off" && it.state != "unavailable" }
            ?: model.media.firstOrNull()

        val switchTiles = room.switches.map { id -> id to (cache.get(id)?.on == true) }
        val lightTargets = model.lights.flatMap { it.light.commandTargets(cache) } + room.switches
        val lightsTotal = model.lights.size + switchTiles.size
        val lightsOn = model.lights.count { it.isOn } + switchTiles.count { it.second }

        return Channel(
            index = i, count = rooms.size, room = room,
            label = "CH ${i + 1}",
            status = status(media, lightsOn, lightsTotal),
            mediaId = media?.entityId,
            remoteId = media?.remoteEntityId ?: room.primaryRemote,
            caps = media?.caps ?: MediaCaps.NONE,
            isPlaying = media?.isPlaying == true,
            mediaOn = media != null && media.state != "off" && media.state != "unavailable" && media.state != "standby",
            lightTargets = lightTargets.distinct(),
            lightsTotal = lightsTotal,
            lightsOn = lightsOn,
            hasDpad = media != null && (media.showDpad || media.canTransport),
        )
    }

    fun status(media: RoomControl.MediaTile?, lightsOn: Int, lightsTotal: Int): String {
        val parts = mutableListOf<String>()
        if (media != null) {
            val what = media.appName?.takeIf { it.isNotBlank() } ?: media.title
            when (media.state) {
                "playing" -> parts += "$what playing"
                "paused" -> parts += "$what paused"
                "unavailable" -> parts += "TV unavailable"
                "off", "standby" -> {}
                else -> if (media.state.isNotBlank()) parts += "TV on"
            }
        }
        if (lightsTotal > 0) parts += when (lightsOn) {
            0 -> if (parts.isEmpty()) "" else "lights off"
            1 -> "1 light on"
            else -> "$lightsOn lights on"
        }
        val line = parts.filter { it.isNotBlank() }.joinToString(" · ")
        return line.ifBlank { "All off" }
    }

    /** The service call for the room's Lights key: any on → all off; all off → all on. */
    fun lightsCall(ch: Channel): Pair<Boolean, List<String>>? =
        if (ch.lightTargets.isEmpty()) null else (!ch.anyLightOn) to ch.lightTargets
}

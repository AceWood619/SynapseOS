package com.acewood.synapse.logic

/**
 * Builds the remote's room "channels" from Home Assistant areas. Rooms come from HA areas
 * (live via the registry/template); this turns raw area→entity lists into tidy room channels:
 * skips empty and excluded areas (kitchen), classifies entities for the remote pad, and hides
 * duplicate cloud-twin lights (zz_cloud_* when a local cync_lan_* exists) to cut clutter.
 */
data class Area(val id: String, val name: String, val entities: List<String>)

data class Room(
    val id: String,
    val name: String,
    val lights: List<String>,       // domain light.*
    val switches: List<String>,     // domain switch.* that look like plugs/lamps
    val media: List<String>,        // domain media_player.* (the D-pad target)
    val extras: List<String>,       // other switches/toggles (jarvis mic, mutes, alerts) — a "more" section
    val remotes: List<String> = emptyList(),  // domain remote.* (Roku etc.) — powers the D-pad send_command
) {
    val hasControls get() = lights.isNotEmpty() || switches.isNotEmpty() || media.isNotEmpty() || extras.isNotEmpty()
    val primaryMedia get() = media.firstOrNull()
    val primaryRemote get() = remotes.firstOrNull()
}

data class RoomsConfig(
    val excludedAreas: Set<String> = setOf("kitchen"),
    val hideCloudTwins: Boolean = false,   // Mason: keep both local+cloud as mutual backup; UI pairs them
    /** switch.* whose name contains any of these is an "extra" (feature toggle), not a primary control. */
    val extraSwitchHints: List<String> = listOf("jarvis", "microphone", "replies", "mute", "mitm", "ring", "motion", "ding", "alert", "windows"),
    /** explicit room order; ids not listed go after, in input order. */
    val order: List<String> = emptyList(),
)

object Rooms {
    fun build(areas: List<Area>, cfg: RoomsConfig = RoomsConfig()): List<Room> {
        val rooms = areas
            .filter { it.id !in cfg.excludedAreas }
            .map { toRoom(it, cfg) }
            .filter { it.hasControls }
        if (cfg.order.isEmpty()) return rooms
        val rank = cfg.order.withIndex().associate { (i, id) -> id to i }
        return rooms.sortedBy { rank[it.id] ?: (cfg.order.size + rooms.indexOf(it)) }
    }

    private fun toRoom(a: Area, cfg: RoomsConfig): Room {
        var lights = a.entities.filter { it.startsWith("light.") }
        if (cfg.hideCloudTwins) {
            val hasLocal = lights.any { !it.contains("zz_cloud") }
            if (hasLocal) lights = lights.filterNot { it.contains("zz_cloud") }
        }
        val switchesAll = a.entities.filter { it.startsWith("switch.") }
        val extras = switchesAll.filter { s -> cfg.extraSwitchHints.any { s.contains(it, true) } }
        val switches = switchesAll - extras.toSet()
        val media = a.entities.filter { it.startsWith("media_player.") }
        val remotes = a.entities.filter { it.startsWith("remote.") }
        return Room(a.id, a.name, lights, switches, media, extras, remotes)
    }
}

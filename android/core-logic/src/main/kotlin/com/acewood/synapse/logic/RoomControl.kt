package com.acewood.synapse.logic

/**
 * View-model for a room "channel" detail pad, built from live HA state. Pure + testable so the
 * Android room-pad view is a thin renderer: light tiles (resilient local+cloud paired, with
 * brightness when dimmable), media tiles (transport + Roku D-pad flag + volume), and extra toggles.
 */
object RoomControl {

    data class LightTile(
        val light: ResilientLight,
        val isOn: Boolean,
        val brightnessPct: Int?,   // 0..100; null when not dimmable or unknown
        val dimmable: Boolean,
        val available: Boolean,    // false = HA reports unavailable/unknown on both paths
        val colorCapable: Boolean = false,      // supports rgb/hs/xy color — show swatches
        val colorTempCapable: Boolean = false,  // supports color_temp — show a warm↔cool slider
    )

    data class MediaTile(
        val entityId: String,
        val remoteEntityId: String?,  // the Roku remote.* to drive the D-pad (null = media_player only)
        val title: String,            // now-playing title, else friendly name
        val state: String,            // playing / paused / idle / off / unavailable
        val isPlaying: Boolean,
        val isMuted: Boolean,
        val volumePct: Int?,          // 0..100; null if volume not settable/known
        val canTransport: Boolean,    // supports play/pause/skip
        val showDpad: Boolean,        // Roku-style: show the directional pad
    )

    data class ToggleTile(val entityId: String, val name: String, val isOn: Boolean, val available: Boolean)

    data class FanTile(val entityId: String, val name: String, val isOn: Boolean,
                       val speedPct: Int?, val supportsSpeed: Boolean, val available: Boolean)

    data class CoverTile(val entityId: String, val name: String, val state: String,
                         val positionPct: Int?, val supportsPosition: Boolean, val available: Boolean) {
        val isOpen get() = state == "open" || (positionPct ?: 0) > 0
    }

    data class ClimateTile(val entityId: String, val name: String, val mode: String,
                           val currentTemp: Double?, val targetTemp: Double?,
                           val minTemp: Double, val maxTemp: Double, val step: Double, val available: Boolean)

    data class LockTile(val entityId: String, val name: String, val locked: Boolean, val available: Boolean)

    data class SensorTile(val entityId: String, val name: String, val value: String, val unit: String)

    data class Model(
        val roomId: String,
        val roomName: String,
        val lights: List<LightTile>,
        val lightsAmbiguous: Boolean,  // local+cloud present but not safely pairable (needs Mason's mapping)
        val media: List<MediaTile>,
        val extras: List<ToggleTile>,
        val fans: List<FanTile> = emptyList(),
        val covers: List<CoverTile> = emptyList(),
        val climate: List<ClimateTile> = emptyList(),
        val locks: List<LockTile> = emptyList(),
        val sensors: List<SensorTile> = emptyList(),
    ) {
        val anyLightOn get() = lights.any { it.isOn }
        val lightCount get() = lights.size
    }

    // HA MediaPlayerEntityFeature bits we care about.
    private const val FEAT_PAUSE = 1
    private const val FEAT_VOLUME_SET = 4
    private const val FEAT_VOLUME_MUTE = 8
    private const val FEAT_NEXT = 32
    private const val FEAT_VOLUME_STEP = 1024
    private const val FEAT_PLAY = 16384

    fun build(room: Room, cache: EntityCache): Model {
        val (paired, ambiguous) = ResilientLight.pairRoomLights(room.lights) { cache.get(it)?.friendlyName }
        val lightTiles = paired.map { rl -> lightTile(rl, cache) }

        val mediaTiles = room.media.map { id -> mediaTile(id, room, cache) }

        val extraTiles = room.extras.map { id ->
            val e = cache.get(id)
            ToggleTile(id, friendly(e?.friendlyName ?: pretty(id), room.name), e?.on ?: false, available(e))
        } + room.switches.map { id ->   // plain switches render as toggles too
            val e = cache.get(id)
            ToggleTile(id, friendly(e?.friendlyName ?: pretty(id), room.name), e?.on ?: false, available(e))
        }

        return Model(
            room.id, room.name, lightTiles, ambiguous, mediaTiles, extraTiles,
            fans = room.fans.map { fanTile(it, cache, room.name) },
            covers = room.covers.map { coverTile(it, cache, room.name) },
            climate = room.climate.map { climateTile(it, cache, room.name) },
            locks = room.locks.map { lockTile(it, cache, room.name) },
            sensors = room.sensors.mapNotNull { sensorTile(it, cache, room.name) },
        )
    }

    private fun fanTile(id: String, cache: EntityCache, roomName: String): FanTile {
        val e = cache.get(id)
        val feat = (e?.attrDouble("supported_features") ?: 0.0).toInt()
        val pct = e?.attrDouble("percentage")?.toInt()?.coerceIn(0, 100)
        return FanTile(id, friendly(e?.friendlyName ?: pretty(id), roomName), e?.on ?: false, pct, has(feat, 1 /* SET_SPEED */), available(e))
    }

    private fun coverTile(id: String, cache: EntityCache, roomName: String): CoverTile {
        val e = cache.get(id)
        val feat = (e?.attrDouble("supported_features") ?: 0.0).toInt()
        val pos = e?.attrDouble("current_position")?.toInt()?.coerceIn(0, 100)
        return CoverTile(id, friendly(e?.friendlyName ?: pretty(id), roomName), e?.state ?: "unavailable", pos, has(feat, 4 /* SET_POSITION */), available(e))
    }

    private fun climateTile(id: String, cache: EntityCache, roomName: String): ClimateTile {
        val e = cache.get(id)
        return ClimateTile(
            id, friendly(e?.friendlyName ?: pretty(id), roomName), e?.state ?: "unavailable",
            currentTemp = e?.attrDouble("current_temperature"),
            targetTemp = e?.attrDouble("temperature"),
            minTemp = e?.attrDouble("min_temp") ?: 50.0,
            maxTemp = e?.attrDouble("max_temp") ?: 90.0,
            step = e?.attrDouble("target_temp_step") ?: 1.0,
            available = available(e),
        )
    }

    private fun lockTile(id: String, cache: EntityCache, roomName: String): LockTile {
        val e = cache.get(id)
        return LockTile(id, friendly(e?.friendlyName ?: pretty(id), roomName), e?.state == "locked", available(e))
    }

    private fun sensorTile(id: String, cache: EntityCache, roomName: String): SensorTile? {
        val e = cache.get(id) ?: return SensorTile(id, friendly(pretty(id), roomName), "—", "")
        val unit = e.attributes["unit_of_measurement"] as? String ?: ""
        return SensorTile(id, friendly(e.friendlyName, roomName), e.state, unit)
    }

    private fun lightTile(rl: ResilientLight, cache: EntityCache): LightTile {
        val src = rl.stateSource(cache)
        val e = src?.let { cache.get(it) }
        val dimmable = isDimmable(e)
        val bri = e?.attrDouble("brightness")?.let { ((it / 255.0) * 100).toInt().coerceIn(0, 100) }
        val modes = colorModes(e)
        return LightTile(
            light = rl,
            isOn = rl.isOn(cache),
            brightnessPct = if (dimmable) bri else null,
            dimmable = dimmable,
            available = available(e),
            colorCapable = modes.any { it in setOf("hs", "rgb", "rgbw", "rgbww", "xy") },
            colorTempCapable = "color_temp" in modes,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun colorModes(e: Entity?): Set<String> =
        (e?.attributes?.get("supported_color_modes") as? List<Any?>)
            ?.mapNotNull { (it as? String)?.lowercase() }?.toSet() ?: emptySet()

    private fun mediaTile(id: String, room: Room, cache: EntityCache): MediaTile {
        val e = cache.get(id)
        val feat = (e?.attrDouble("supported_features") ?: 0.0).toInt()
        val vol = e?.attrDouble("volume_level")?.let { (it * 100).toInt().coerceIn(0, 100) }
        val title = (e?.attributes?.get("media_title") as? String)?.takeIf { it.isNotBlank() }
            ?: friendly(e?.friendlyName ?: pretty(id), room.name)
        val roku = isRoku(id, e)
        return MediaTile(
            entityId = id,
            remoteEntityId = pickRemote(id, room, roku),
            title = title,
            state = e?.state ?: "unavailable",
            isPlaying = e?.state == "playing",
            isMuted = e?.attributes?.get("is_volume_muted") == true,
            volumePct = if (has(feat, FEAT_VOLUME_SET) || has(feat, FEAT_VOLUME_STEP)) vol else null,
            canTransport = has(feat, FEAT_PLAY) || has(feat, FEAT_PAUSE) || has(feat, FEAT_NEXT),
            showDpad = roku || room.remotes.isNotEmpty(),
        )
    }

    /** Choose the remote.* that drives this media player's D-pad: a roku-matching remote, else the room's first. */
    private fun pickRemote(mediaId: String, room: Room, roku: Boolean): String? {
        if (room.remotes.isEmpty()) return null
        val stem = mediaId.substringAfter('.').substringBefore("_roku")
        return room.remotes.firstOrNull { it.substringAfter('.').contains(stem) && stem.isNotBlank() }
            ?: room.remotes.firstOrNull { it.contains("roku", true) }
            ?: room.primaryRemote
    }

    private fun isRoku(id: String, e: Entity?): Boolean =
        id.contains("roku", true) ||
            (e?.friendlyName?.contains("roku", true) == true) ||
            e?.attributes?.containsKey("app_name") == true

    @Suppress("UNCHECKED_CAST")
    private fun isDimmable(e: Entity?): Boolean {
        if (e == null) return false
        val modes = e.attributes["supported_color_modes"] as? List<Any?>
        if (modes != null) return modes.any { (it as? String)?.lowercase() !in setOf("onoff", null) }
        // Fallback: a brightness attribute means it dims.
        return e.attributes.containsKey("brightness")
    }

    private fun available(e: Entity?): Boolean =
        e != null && e.state != "unavailable" && e.state != "unknown"

    private fun has(features: Int, bit: Int): Boolean = (features and bit) == bit

    private fun pretty(entityId: String): String =
        entityId.substringAfter('.').replace('_', ' ').split(' ')
            .filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    private fun friendly(name: String, roomName: String): String {
        val clean = name.trim().ifEmpty { "Device" }
        val room = roomName.trim()
        val initials = room.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }.joinToString("") { it.first().lowercase() }
        val prefixes = listOf(room, initials).filter { it.length > 1 }
        return prefixes.firstOrNull { clean.startsWith("$it ", ignoreCase = true) }
            ?.let { clean.substring(it.length).trim().replaceFirstChar { c -> c.uppercase() } }
            ?: clean
    }
}

package com.acewood.synapse.logic

/** Node configuration, provisioned as JSON (see tools/provision/config.example.json). */
data class NodeConfig(
    val nodeId: String,
    val room: String,
    val haUrl: String,
    val haToken: String,
    val dashboardPath: String = "/lovelace/0",
    val idleSeconds: Int = 120,
    val ambientBrightness: Float = 0.02f,
    val pin: String = "",
    val apiPort: Int = 8765,
    val apiKey: String = "",
    val heartbeatSeconds: Int = 60,
    val kiosk: Boolean = true,
    /** Other apps allowed through kiosk mode and opened once after boot so their services start (e.g. Ava voice). */
    val companionApps: List<String> = emptyList(),
    /** Known BLE devices: MAC or iBeacon "uuid:major:minor" -> name. Empty = BLE scanning off. */
    val bleKnown: Map<String, String> = emptyMap(),
    /** Camera snapshots over the control API. Off by default (privacy). "back" or "front". */
    val camera: String = "",
    /** preferred base room order (ids); the smart ordering uses this to break ties */
    val roomOrder: List<String> = emptyList(),
) {
    val slug: String get() = EntityIds.slug(nodeId)
    val dashboardUrl: String get() = haUrl.trimEnd('/') + "/" + dashboardPath.trimStart('/')

    /** Problems that would stop the node from working; empty list = OK. */
    fun validate(): List<String> = buildList {
        if (slug.isEmpty()) add("node_id is empty")
        if (!(haUrl.startsWith("http://") || haUrl.startsWith("https://"))) add("ha_url must start with http:// or https://")
        if (haToken.length < 20) add("ha_token missing or too short")
        if (idleSeconds < 10) add("idle_seconds must be >= 10")
        if (apiPort !in 1024..65535) add("api_port must be 1024-65535")
        if (apiKey.isNotEmpty() && apiKey.length < 16) add("api_key must be >= 16 chars (or empty to disable the control API)")
        if (pin.isNotEmpty() && (pin.length < 4 || !pin.all { it.isDigit() })) add("pin must be 4+ digits")
        if (ambientBrightness !in 0f..1f) add("ambient_brightness must be 0..1")
        if (camera !in setOf("", "back", "front")) add("camera must be \"\", \"back\" or \"front\"")
        companionApps.filterNot { PACKAGE.matches(it) }.forEach { add("companion_apps: bad package name '$it'") }
    }

    /** JSON for persistence; the token is included, so never log this. */
    fun toJson(): String = Json.write(
        linkedMapOf(
            "node_id" to nodeId, "room" to room, "ha_url" to haUrl, "ha_token" to haToken,
            "dashboard_path" to dashboardPath, "idle_seconds" to idleSeconds,
            "ambient_brightness" to ambientBrightness.toDouble(), "pin" to pin, "api_port" to apiPort,
            "api_key" to apiKey, "heartbeat_seconds" to heartbeatSeconds, "kiosk" to kiosk,
            "companion_apps" to companionApps, "ble_known" to bleKnown, "camera" to camera,
            "room_order" to roomOrder,
        )
    )

    /** Safe-to-show summary (secrets masked). */
    fun redacted(): Map<String, Any?> = linkedMapOf(
        "node_id" to nodeId, "room" to room, "ha_url" to haUrl,
        "ha_token" to if (haToken.isEmpty()) "(unset)" else "(set)",
        "dashboard_path" to dashboardPath, "idle_seconds" to idleSeconds, "api_port" to apiPort,
        "api_key_set" to apiKey.isNotEmpty(), "pin_set" to pin.isNotEmpty(), "kiosk" to kiosk,
        "companion_apps" to companionApps, "ble_known" to bleKnown.size, "camera" to camera,
    )

    companion object {
        val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

        fun fromJson(text: String): NodeConfig {
            val m = Json.parseObject(text)
            fun str(k: String, d: String = "") = (m[k] as? String)?.trim() ?: d
            fun num(k: String, d: Double) = (m[k] as? Double) ?: d
            fun bool(k: String, d: Boolean) = (m[k] as? Boolean) ?: d
            return NodeConfig(
                nodeId = str("node_id"),
                room = str("room"),
                haUrl = str("ha_url").trimEnd('/'),
                haToken = str("ha_token"),
                dashboardPath = str("dashboard_path", "/lovelace/0"),
                idleSeconds = num("idle_seconds", 120.0).toInt(),
                ambientBrightness = num("ambient_brightness", 0.02).toFloat(),
                pin = str("pin"),
                apiPort = num("api_port", 8765.0).toInt(),
                apiKey = str("api_key"),
                heartbeatSeconds = num("heartbeat_seconds", 60.0).toInt().coerceIn(15, 3600),
                kiosk = bool("kiosk", true),
                companionApps = (m["companion_apps"] as? List<*>)?.mapNotNull { (it as? String)?.trim() }
                    ?.filter { it.isNotEmpty() }?.distinct() ?: emptyList(),
                bleKnown = (m["ble_known"] as? Map<*, *>)?.entries
                    ?.mapNotNull { (k, v) -> (k as? String)?.let { it.trim().lowercase() to (v as? String ?: it) } }
                    ?.toMap() ?: emptyMap(),
                camera = str("camera").lowercase(),
                roomOrder = (m["room_order"] as? List<*>)?.mapNotNull { (it as? String)?.trim()?.ifEmpty { null } } ?: emptyList(),
            )
        }
    }
}

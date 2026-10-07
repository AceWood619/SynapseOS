package com.acewood.synapse.logic

/** Home Assistant entity naming for a node: sensor.synapse_<node>_<key>. */
object EntityIds {
    fun slug(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    fun entity(domain: String, nodeId: String, key: String): String =
        "$domain.synapse_${slug(nodeId)}_${slug(key)}"
}

/** One thing the node reports to HA. */
data class SensorSpec(
    val key: String,
    val domain: String = "sensor",
    val name: String,
    val unit: String? = null,
    val deviceClass: String? = null,
    val stateClass: String? = null,
    /** Minimum change that counts as "changed" for numeric values. */
    val deadband: Double = 0.0,
    val icon: String? = null,
)

object NodeSensors {
    val ALL = listOf(
        SensorSpec("battery", name = "Battery", unit = "%", deviceClass = "battery", stateClass = "measurement", deadband = 1.0),
        SensorSpec("battery_temp", name = "Battery temperature", unit = "°C", deviceClass = "temperature", stateClass = "measurement", deadband = 0.5),
        SensorSpec("battery_voltage", name = "Battery voltage", unit = "mV", deviceClass = "voltage", stateClass = "measurement", deadband = 25.0),
        SensorSpec("charging", domain = "binary_sensor", name = "Charging", deviceClass = "battery_charging"),
        SensorSpec("power_source", name = "Power source", icon = "mdi:power-plug"),
        SensorSpec("illuminance", name = "Illuminance", unit = "lx", deviceClass = "illuminance", stateClass = "measurement", deadband = 5.0),
        SensorSpec("pressure", name = "Pressure", unit = "hPa", deviceClass = "atmospheric_pressure", stateClass = "measurement", deadband = 0.3),
        SensorSpec("proximity", domain = "binary_sensor", name = "Proximity", deviceClass = "occupancy"),
        SensorSpec("occupancy", domain = "binary_sensor", name = "Presence", deviceClass = "occupancy"),
        SensorSpec("wifi_rssi", name = "Wi-Fi signal", unit = "dBm", deviceClass = "signal_strength", stateClass = "measurement", deadband = 4.0),
        SensorSpec("screen", name = "Screen mode", icon = "mdi:tablet-dashboard"),
        SensorSpec("uptime", name = "App uptime", unit = "min", stateClass = "measurement", deadband = 5.0, icon = "mdi:timer-outline"),
        SensorSpec("mem_free", name = "Free memory", unit = "MB", stateClass = "measurement", deadband = 50.0, icon = "mdi:memory"),
        SensorSpec("cpu_temp", name = "CPU temperature", unit = "°C", deviceClass = "temperature", stateClass = "measurement", deadband = 1.0),
        SensorSpec("status", name = "Node status", icon = "mdi:access-point-network"),
    ).associateBy { it.key }

    /** Builds the JSON body for POST /api/states/<entity_id>. */
    fun statePayload(spec: SensorSpec, nodeId: String, room: String, value: Any?, extra: Map<String, Any?> = emptyMap()): String {
        val state: String = when (value) {
            null -> "unknown"
            is Boolean -> if (value) "on" else "off"
            is Double -> if (value == Math.rint(value)) value.toLong().toString() else String.format(java.util.Locale.US, "%.2f", value)
            is Float -> String.format(java.util.Locale.US, "%.2f", value)
            else -> value.toString()
        }
        val attrs = linkedMapOf<String, Any?>(
            "friendly_name" to "Synapse ${nodeId} ${spec.name}",
            "synapse_node" to nodeId,
            "synapse_room" to room,
        )
        spec.unit?.let { attrs["unit_of_measurement"] = it }
        spec.deviceClass?.let { attrs["device_class"] = it }
        spec.stateClass?.let { attrs["state_class"] = it }
        spec.icon?.let { attrs["icon"] = it }
        attrs.putAll(extra)
        return Json.write(linkedMapOf("state" to state, "attributes" to attrs))
    }
}

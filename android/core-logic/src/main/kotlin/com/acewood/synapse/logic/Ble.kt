package com.acewood.synapse.logic

/**
 * BLE presence: turns raw advertisements into "who's near this node".
 * Known devices are matched by MAC (watches, ESP tags, fixed-address beacons)
 * or by iBeacon id "uuid:major:minor" (e.g. the HA Companion app's BLE transmitter,
 * which survives phone MAC randomisation).
 */
object BleParser {
    /** Parses Apple iBeacon manufacturer data (company 0x004C, after the company id). Returns "uuid:major:minor". */
    fun iBeaconId(appleData: ByteArray?): String? {
        if (appleData == null || appleData.size < 23) return null
        if (appleData[0] != 0x02.toByte() || appleData[1] != 0x15.toByte()) return null
        val hex = StringBuilder()
        for (i in 2 until 18) hex.append(String.format("%02x", appleData[i].toInt() and 0xff))
        val u = hex.toString()
        val uuid = "${u.substring(0, 8)}-${u.substring(8, 12)}-${u.substring(12, 16)}-${u.substring(16, 20)}-${u.substring(20)}"
        val major = ((appleData[18].toInt() and 0xff) shl 8) or (appleData[19].toInt() and 0xff)
        val minor = ((appleData[20].toInt() and 0xff) shl 8) or (appleData[21].toInt() and 0xff)
        return "$uuid:$major:$minor"
    }

    fun normMac(mac: String) = mac.trim().lowercase()
}

class BleTracker(
    /** key = MAC ("aa:bb:..") or iBeacon id ("uuid:major:minor"), value = friendly name. Keys are case-insensitive. */
    known: Map<String, String>,
    private val windowMs: Long = 60_000,
    /** Weaker than this counts as "seen but not near" (other room). */
    private val nearRssi: Int = -80,
) {
    private val known = known.mapKeys { it.key.trim().lowercase() }
    private data class Sighting(val rssi: Int, val atMs: Long)
    private val seen = HashMap<String, Sighting>() // key: mac or beacon id
    private val knownSeen = HashMap<String, Sighting>() // key: known key

    @Synchronized
    fun onAdvert(mac: String, rssi: Int, beaconId: String?, nowMs: Long) {
        val m = BleParser.normMac(mac)
        seen[m] = Sighting(rssi, nowMs)
        val key = when {
            known.containsKey(m) -> m
            beaconId != null && known.containsKey(beaconId.lowercase()) -> beaconId.lowercase()
            else -> null
        } ?: return
        // Smooth RSSI a little: one packet is noise (research report: never decide on a single packet).
        val prev = knownSeen[key]
        val r = if (prev != null && nowMs - prev.atMs < windowMs) (prev.rssi * 2 + rssi) / 3 else rssi
        knownSeen[key] = Sighting(r, nowMs)
    }

    data class Present(val name: String, val key: String, val rssi: Int, val near: Boolean, val ageS: Long)
    data class Snapshot(val devicesSeen: Int, val known: List<Present>) {
        val anyKnownNear get() = known.any { it.near }
    }

    @Synchronized
    fun snapshot(nowMs: Long): Snapshot {
        seen.entries.removeAll { nowMs - it.value.atMs > windowMs }
        knownSeen.entries.removeAll { nowMs - it.value.atMs > windowMs }
        val present = knownSeen.map { (k, s) ->
            Present(known[k] ?: k, k, s.rssi, s.rssi >= nearRssi, (nowMs - s.atMs) / 1000)
        }.sortedByDescending { it.rssi }
        return Snapshot(seen.size, present)
    }
}

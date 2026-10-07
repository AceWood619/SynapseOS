package com.acewood.synapse.logic

/**
 * One switch in the dashboard that auto-decides between a light's LOCAL (cync_lan) and CLOUD
 * (zz_cloud) entity — whichever is reachable. Mason keeps both in HA as mutual backups; the
 * remote shows a single control and routes to the healthy one (prefer local; fall back to cloud,
 * and vice versa). A command can also hit both so the bulb changes no matter which path is up.
 */
data class ResilientLight(
    val name: String,
    val local: String?,     // light.cync_lan_*  (fast, on-LAN)
    val cloud: String?,     // light.zz_cloud_*  (cloud backup)
) {
    val entityIds: List<String> get() = listOfNotNull(local, cloud)

    private fun healthy(id: String?, cache: EntityCache): Boolean {
        val e = id?.let { cache.get(it) } ?: return false
        return e.state != "unavailable" && e.state != "unknown"
    }

    /** The entity to READ state from: a healthy local wins, else a healthy cloud, else whichever exists. */
    fun stateSource(cache: EntityCache): String? = when {
        healthy(local, cache) -> local
        healthy(cloud, cache) -> cloud
        else -> local ?: cloud
    }

    fun isOn(cache: EntityCache): Boolean = stateSource(cache)?.let { cache.get(it)?.on } ?: false

    /**
     * Entities to send a command to. Default: the healthy one only (clean). If the preferred path
     * is unhealthy we use the backup. [both]=true fires both paths for max reliability.
     */
    fun commandTargets(cache: EntityCache, both: Boolean = false): List<String> {
        if (both) return entityIds
        return listOfNotNull(stateSource(cache))
    }

    companion object {
        /**
         * Pair a room's lights into resilient controls. SAFE auto-pairing only: when a room has
         * exactly one local and one cloud light they're clearly the same bulb, so pair them.
         * Rooms with several of each are left unpaired (listed individually) and flagged, so we
         * never bind the wrong two lights together.
         */
        fun pairRoomLights(lights: List<String>): Pair<List<ResilientLight>, Boolean> {
            val locals = lights.filter { !it.contains("zz_cloud") }
            val clouds = lights.filter { it.contains("zz_cloud") }
            if (locals.size == 1 && clouds.size == 1) {
                // Name from the cloud entity — its name is human-readable; the local one is numeric.
                return listOf(ResilientLight(prettyName(clouds[0]), locals[0], clouds[0])) to false
            }
            // Can't safely auto-pair: present each on its own; ambiguous=true if both kinds exist.
            val singles = lights.map { ResilientLight(prettyName(it), if (it.contains("zz_cloud")) null else it, if (it.contains("zz_cloud")) it else null) }
            val ambiguous = locals.isNotEmpty() && clouds.isNotEmpty()
            return singles to ambiguous
        }

        fun prettyName(entityId: String): String {
            val n = entityId.substringAfter('.')
                .removePrefix("zz_cloud_").removePrefix("cync_lan_")
                .replace(Regex("\\d{5,}"), "").replace('_', ' ').trim()
            return n.ifBlank { entityId.substringAfter('.') }.split(' ').filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
    }
}

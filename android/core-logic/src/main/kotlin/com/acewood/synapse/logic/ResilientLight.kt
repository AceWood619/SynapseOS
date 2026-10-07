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
         * Pair a room's lights (local cync_lan ⇄ cloud zz_cloud twins) into resilient controls.
         * SAFE: only pairs when confident, never binds the wrong two bulbs together.
         *  1. exactly one local + one cloud → clearly the same bulb, pair them.
         *  2. otherwise, pair a cloud to a local whose **friendly name matches** (via [nameOf]), since
         *     the local cync_lan entity id is just a number but its HA friendly name is the real
         *     device name. Unmatched lights are listed on their own.
         * [ambiguous] = some local and some cloud are left unpaired, so Mason (or a config map) may
         * still need to say which is which.
         */
        fun pairRoomLights(lights: List<String>, nameOf: (String) -> String? = { null }): Pair<List<ResilientLight>, Boolean> {
            val locals = lights.filter { !it.contains("zz_cloud") }
            val clouds = lights.filter { it.contains("zz_cloud") }
            fun name(id: String) = nameOf(id)?.trim()?.ifBlank { null } ?: prettyName(id)
            fun norm(id: String) = name(id).lowercase()
                .replace(Regex("\\b(light|lights|lamp)\\b"), "").replace(Regex("[^a-z0-9]+"), " ").trim()

            if (locals.size == 1 && clouds.size == 1) {
                return listOf(ResilientLight(name(clouds[0]), locals[0], clouds[0])) to false
            }

            // name-based matching (only pairs on an exact normalized-name match)
            val usedLocal = HashSet<String>()
            val pairs = ArrayList<ResilientLight>()
            for (cloud in clouds) {
                val key = norm(cloud)
                val match = locals.firstOrNull { it !in usedLocal && key.isNotBlank() && norm(it) == key }
                if (match != null) { usedLocal.add(match); pairs.add(ResilientLight(name(cloud), match, cloud)) }
                else pairs.add(ResilientLight(name(cloud), null, cloud))
            }
            locals.filterNot { it in usedLocal }.forEach { pairs.add(ResilientLight(name(it), it, null)) }
            val unpairedCloud = pairs.any { it.local == null && it.cloud != null }
            val unpairedLocal = pairs.any { it.cloud == null && it.local != null }
            return pairs to (unpairedCloud && unpairedLocal)
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

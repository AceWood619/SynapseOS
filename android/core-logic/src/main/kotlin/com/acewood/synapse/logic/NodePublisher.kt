package com.acewood.synapse.logic

/**
 * The publish decision, pulled out of the Android service so it can be simulated and unit-tested.
 * Given the latest readings and the clock, it decides which HA entities to POST this tick,
 * applying the per-key dead-band / heartbeat policy. The Android NodeService only supplies
 * raw readings and performs the actual HTTP; all the "when to send" logic is here.
 */
class NodePublisher(
    private val nodeId: String,
    private val room: String,
    private val policy: PublishPolicy,
) {
    data class Post(val entityId: String, val key: String, val body: String)

    /** readings: key -> (value, extra attributes). null values are skipped. */
    fun decide(readings: Map<String, Pair<Any?, Map<String, Any?>>>, nowMs: Long): List<Post> {
        val out = ArrayList<Post>()
        for ((key, pair) in readings) {
            val (value, extra) = pair
            if (value == null) continue
            val spec = NodeSensors.ALL[key] ?: continue
            if (!policy.shouldSend(key, value, spec.deadband, nowMs)) continue
            policy.markSent(key, value, nowMs)
            out += Post(
                EntityIds.entity(spec.domain, nodeId, key),
                key,
                NodeSensors.statePayload(spec, nodeId, room, value, extra),
            )
        }
        return out
    }

    /** HA came back after being unreachable: forget what we "sent" so everything re-publishes. */
    fun resetAfterReconnect() = policy.reset()
}

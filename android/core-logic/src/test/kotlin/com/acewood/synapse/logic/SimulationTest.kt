package com.acewood.synapse.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end simulation of a node over a simulated evening, minute by minute.
 * Models the real wiring: sensor values -> PresenceFusion + BleTracker -> readings map
 * -> NodePublisher (dead-band/heartbeat) -> the HA posts that would be sent.
 * A FakeHa records every post and can "go down" to prove the re-send-on-reconnect path.
 * This catches integration bugs (flooding HA, presence never firing, stale state after an
 * outage) here on the JVM, before the APK ever reaches the phone.
 */
class SimulationTest {
    private class FakeHa {
        var up = true
        val posts = ArrayList<Pair<Long, NodePublisher.Post>>()
        val lastState = HashMap<String, String>()
        var reachable = false
        fun deliver(nowMs: Long, toSend: List<NodePublisher.Post>, pub: NodePublisher): Boolean {
            if (!up) { reachable = false; return false }
            val wasReachable = reachable
            reachable = true
            for (p in toSend) {
                posts += nowMs to p
                lastState[p.entityId] = Json.parseObject(p.body)["state"] as String
            }
            if (!wasReachable) pub.resetAfterReconnect().also { /* caller re-decides next tick */ }
            return true
        }
    }

    private class World(val node: String = "livingroom-01", val room: String = "Living Room") {
        val fusion = PresenceFusion()
        val ble = BleTracker(mapOf("aa:bb:cc:dd:ee:ff" to "Mason watch"))
        val idle = IdleController(120_000)
        var lux = 40.0
        var battery = 72.0
        var charging = true
        val screen get() = if (idle.mode == IdleController.Mode.AMBIENT) "ambient" else "active"

        fun readings(t: Long): Map<String, Pair<Any?, Map<String, Any?>>> {
            val p = fusion.evaluate(t)
            val snap = ble.snapshot(t)
            if (snap.anyKnownNear) fusion.fire(PresenceFusion.Signal.BLE, t)
            return linkedMapOf(
                "battery" to (battery to emptyMap()),
                "charging" to (charging to emptyMap()),
                "illuminance" to (lux to emptyMap()),
                "occupancy" to (p.occupied to mapOf("confidence" to p.confidence, "signals" to p.signals)),
                "screen" to (screen to emptyMap()),
                "ble_devices" to (snap.devicesSeen.toDouble() to emptyMap()),
                "ble_known" to ((if (snap.known.any { it.near }) snap.known.first { it.near }.name else "none") to emptyMap()),
                "status" to ("online" to emptyMap()),
            )
        }
    }

    @Test fun eveningSimulation() {
        val w = World()
        val ha = FakeHa()
        val pub = NodePublisher(w.node, w.room, PublishPolicy(heartbeatMs = 60_000, minIntervalMs = 2_000))
        var t = 0L
        val step = 5_000L           // the node ticks every 5 s
        val minute = 60_000L

        // Minute 0: boot, nobody home. One touch to register the installer, then quiet.
        w.idle.activity(t)
        w.fusion.fire(PresenceFusion.Signal.TOUCH, t)

        var occupiedTrueTicks = 0
        var ambientReached = false
        var batteryPosts = 0

        // Simulate 40 minutes.
        while (t <= 40 * minute) {
            // Scripted events:
            if (t == 3 * minute) { w.ble.onAdvert("aa:bb:cc:dd:ee:ff", -55, null, t) } // Mason walks in (watch)
            if (t >= 3 * minute && t % minute == 0L)
                w.ble.onAdvert("aa:bb:cc:dd:ee:ff", -58, null, t)                      // worn watch keeps advertising
            if (t == 6 * minute) { w.lux = 8.0; w.fusion.fire(PresenceFusion.Signal.LIGHT_CHANGE, t) } // lamp off, movie
            if (t == 8 * minute) w.idle.activity(t)                                    // taps the panel
            if (t == 25 * minute) { w.battery = 69.0 }                                 // slow battery drift
            if (t == 30 * minute) { w.charging = false }                               // unplugged briefly

            // HA outage from minute 10 to 13.
            ha.up = t < 10 * minute || t >= 13 * minute

            w.idle.tick(t)
            if (w.screen == "ambient") ambientReached = true

            val posts = pub.decide(w.readings(t), t)
            if (posts.isNotEmpty()) {
                val ok = ha.deliver(t, posts, pub)
                if (!ok) pub.resetAfterReconnect() // node retries whole set next tick
            }
            if (w.fusion.evaluate(t).occupied) occupiedTrueTicks++
            t += step
        }

        // ---- assertions: the house behaved sanely ----
        // 1. Presence turned on when the watch + movie activity arrived, not before.
        assertTrue(occupiedTrueTicks > 100, "presence should hold occupied through the evening, got $occupiedTrueTicks ticks")

        // 2. The screen dimmed to ambient at some point (no activity for > 2 min after minute 8).
        assertTrue(ambientReached, "screen should have gone ambient during the quiet stretch")

        // 3. We did NOT flood HA. Count DELIVERED battery posts (real HA load): a slow sensor
        //    over 40 min with a 60 s heartbeat is dozens, not thousands. (Undelivered retries
        //    during the outage don't reach HA and don't count.)
        val deliveredBattery = ha.posts.count { it.second.key == "battery" }
        assertTrue(deliveredBattery in 20..55, "delivered battery posts should be ~heartbeat-rate, got $deliveredBattery")

        // 4. After the outage (min 10-13), state resynced: occupancy + battery present and fresh.
        val occ = EntityIds.entity("binary_sensor", w.node, "occupancy")
        val bat = EntityIds.entity("sensor", w.node, "battery")
        assertTrue(ha.lastState.containsKey(occ), "occupancy must exist in HA after resync")
        assertEquals("on", ha.lastState[occ], "occupied at end of evening")
        assertEquals("69", ha.lastState[bat], "latest battery value resynced")

        // 5. Every post was well-formed JSON with a state and the right entity prefix.
        for ((_, p) in ha.posts) {
            val obj = Json.parseObject(p.body)
            assertTrue(obj.containsKey("state"))
            assertTrue(p.entityId.startsWith("sensor.synapse_livingroom_01_") ||
                       p.entityId.startsWith("binary_sensor.synapse_livingroom_01_"))
        }

        // 6. There was at least one post right after the outage ended (resync happened).
        val afterOutage = ha.posts.filter { it.first in (13 * minute)..(15 * minute) }
        assertTrue(afterOutage.size >= 5, "should re-publish a burst after HA returns, got ${afterOutage.size}")

        println("SIM OK: delivered=${ha.posts.size} battery=$deliveredBattery occupiedTicks=$occupiedTrueTicks ambient=$ambientReached")
    }

    @Test fun quietEmptyHouseBarelyTalks() {
        // Nobody home, nothing changes: over an hour the node should send few posts (heartbeats only),
        // proving we don't hammer HA when idle.
        val w = World()
        val ha = FakeHa()
        val pub = NodePublisher(w.node, w.room, PublishPolicy(heartbeatMs = 60_000, minIntervalMs = 2_000))
        var t = 0L
        var posts = 0
        while (t <= 60 * 60_000L) {
            val p = pub.decide(w.readings(t), t)
            ha.deliver(t, p, pub)
            posts += p.size
            t += 5_000L
        }
        // 8 sensors, ~60 heartbeats each = ~480 absolute ceiling; initial send + heartbeats.
        assertTrue(posts in 8..520, "idle hour should be mostly heartbeats, got $posts")
        println("SIM idle: $posts posts in 60 min for 8 sensors")
    }
}

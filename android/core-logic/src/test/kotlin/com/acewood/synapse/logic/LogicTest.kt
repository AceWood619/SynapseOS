package com.acewood.synapse.logic

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonTest {
    @Test fun roundTrip() {
        val src = """{"a":1,"b":[true,false,null],"c":"x\"y\n","d":{"e":-2.5e1},"u":"é"}"""
        val m = Json.parseObject(src)
        assertEquals(1.0, m["a"])
        assertEquals(listOf(true, false, null), m["b"])
        assertEquals("x\"y\n", m["c"])
        assertEquals(-25.0, (m["d"] as Map<*, *>)["e"])
        assertEquals("é", m["u"])
        assertEquals(m, Json.parseObject(Json.write(m)))
    }
    @Test fun writesIntegersWithoutDecimals() {
        assertEquals("""{"x":3,"y":2.5,"z":null}""", Json.write(linkedMapOf("x" to 3.0, "y" to 2.5, "z" to Double.NaN)))
    }
    @Test fun rejectsGarbage() {
        assertFailsWith<IllegalArgumentException> { Json.parse("{\"a\":}") }
        assertFailsWith<IllegalArgumentException> { Json.parse("[1,2") }
        assertFailsWith<IllegalArgumentException> { Json.parse("{} x") }
        assertFailsWith<IllegalArgumentException> { Json.parseObject("[1]") }
    }
}

class NodeConfigTest {
    private val good = """{"node_id":"Living Room 01","room":"Living Room","ha_url":"http://homeassistant.local:8123/",
        "ha_token":"abcdefghijklmnopqrstuvwxyz0123","api_key":"0123456789abcdef","pin":"2468"}"""

    @Test fun parsesAndDefaults() {
        val c = NodeConfig.fromJson(good)
        assertEquals("living_room_01", c.slug)
        assertEquals("http://homeassistant.local:8123", c.haUrl)
        assertEquals("http://homeassistant.local:8123/lovelace/0", c.dashboardUrl)
        assertEquals(120, c.idleSeconds)
        assertEquals(8765, c.apiPort)
        assertTrue(c.validate().isEmpty(), c.validate().toString())
        assertEquals(c, NodeConfig.fromJson(c.toJson()))
    }
    @Test fun validationCatchesProblems() {
        val c = NodeConfig.fromJson("""{"node_id":"","ha_url":"homeassistant.local","ha_token":"x","pin":"12a","api_key":"short"}""")
        val errs = c.validate()
        assertEquals(5, errs.size, errs.toString())
    }
    @Test fun companionApps() {
        val c = NodeConfig.fromJson(good.replace("}", ",\"companion_apps\":[\"com.example.ava\",\" com.example.ava \",\"\"]}"))
        assertEquals(listOf("com.example.ava"), c.companionApps)
        assertTrue(c.validate().isEmpty())
        assertEquals(c, NodeConfig.fromJson(c.toJson()))
        val bad = NodeConfig.fromJson(good.replace("}", ",\"companion_apps\":[\"not a package\"]}"))
        assertEquals(1, bad.validate().size)
    }
    @Test fun redactedHidesSecrets() {
        val r = NodeConfig.fromJson(good).redacted().toString()
        assertFalse(r.contains("abcdefghijklmnop"))
        assertFalse(r.contains("0123456789abcdef"))
        assertFalse(r.contains("2468"))
    }
}

class EntityTest {
    @Test fun ids() {
        assertEquals("sensor.synapse_kitchen_01_battery_temp", EntityIds.entity("sensor", "Kitchen-01", "battery_temp"))
    }
    @Test fun payload() {
        val spec = NodeSensors.ALL.getValue("battery")
        val p = Json.parseObject(NodeSensors.statePayload(spec, "kitchen", "Kitchen", 87.0))
        assertEquals("87", p["state"])
        val a = p["attributes"] as Map<*, *>
        assertEquals("%", a["unit_of_measurement"]); assertEquals("battery", a["device_class"])
        val b = Json.parseObject(NodeSensors.statePayload(NodeSensors.ALL.getValue("charging"), "k", "K", true))
        assertEquals("on", b["state"])
        val f = Json.parseObject(NodeSensors.statePayload(NodeSensors.ALL.getValue("pressure"), "k", "K", 1013.256))
        assertEquals("1013.26", f["state"])
    }
}

class PublishPolicyTest {
    @Test fun deadbandHeartbeatAndRateLimit() {
        val p = PublishPolicy(heartbeatMs = 60_000, minIntervalMs = 2_000)
        assertTrue(p.shouldSend("lux", 100.0, 5.0, 0)); p.markSent("lux", 100.0, 0)
        assertFalse(p.shouldSend("lux", 300.0, 5.0, 1_000))   // rate limited
        assertFalse(p.shouldSend("lux", 103.0, 5.0, 3_000))   // inside deadband
        assertTrue(p.shouldSend("lux", 106.0, 5.0, 3_000))    // real change
        assertTrue(p.shouldSend("lux", 100.0, 5.0, 60_000))   // heartbeat
        assertTrue(p.shouldSend("state", "on", 0.0, 0))
        p.markSent("state", "on", 0)
        assertFalse(p.shouldSend("state", "on", 0.0, 5_000))
        assertTrue(p.shouldSend("state", "off", 0.0, 5_000))
        p.reset()
        assertTrue(p.shouldSend("state", "on", 0.0, 5_001))
    }
}

class PresenceTest {
    @Test fun touchMakesOccupiedThenDecaysWithHysteresis() {
        val f = PresenceFusion()
        assertFalse(f.evaluate(0).occupied)
        f.fire(PresenceFusion.Signal.TOUCH, 1_000)
        val r = f.evaluate(1_000)
        assertTrue(r.occupied); assertTrue(r.confidence > 0.8); assertEquals(listOf("touch"), r.signals)
        // after ~8 min TOUCH (half-life 180 s) contributes 2*2^-2.7 ≈ 0.31 → conf ≈ 0.27, still ON (hysteresis)
        assertTrue(f.evaluate(1_000 + 480_000).occupied)
        assertFalse(f.evaluate(1_000 + 900_000).occupied)
    }
    @Test fun singleWeakSignalDoesNotTriggerOccupancy() {
        val f = PresenceFusion()
        f.fire(PresenceFusion.Signal.LIGHT_CHANGE, 0)
        assertFalse(f.evaluate(0).occupied)    // 1-e^-0.5 = 0.39 < 0.6
        f.fire(PresenceFusion.Signal.MOTION, 0)
        assertTrue(f.evaluate(0).occupied)     // 1-e^-1.1 = 0.67
    }
}

class IdleTest {
    @Test fun goesAmbientAndWakes() {
        val c = IdleController(10_000)
        assertFalse(c.tick(0))
        assertFalse(c.tick(9_999))
        assertTrue(c.tick(10_000)); assertEquals(IdleController.Mode.AMBIENT, c.mode)
        assertTrue(c.activity(11_000)); assertEquals(IdleController.Mode.ACTIVE, c.mode)
        assertFalse(c.tick(15_000))
        assertTrue(c.forceAmbient()); assertFalse(c.forceAmbient())
    }
}

class HttpApiTest {
    private class FakeActions : NodeActions {
        val calls = ArrayList<String>()
        override fun status() = mapOf<String, Any?>("node" to "x")
        override fun wake() { calls += "wake" }
        override fun ambient() { calls += "ambient" }
        override fun speak(text: String): Boolean { calls += "speak:$text"; return true }
        override fun reload() { calls += "reload" }
        override fun presence(source: String) { calls += "presence:$source" }
    }
    private fun req(raw: String) = HttpParser.read(ByteArrayInputStream(raw.toByteArray()))

    @Test fun parsesRequest() {
        val r = req("POST /api/speak?x=a%20b HTTP/1.1\r\nHost: h\r\nContent-Length: 15\r\nX-Synapse-Key: k\r\n\r\n{\"text\":\"hi\"}  ")
        assertEquals("POST", r.method); assertEquals("/api/speak", r.path)
        assertEquals("a b", r.query["x"]); assertEquals("k", r.headers["x-synapse-key"])
        assertEquals("{\"text\":\"hi\"}  ", r.body)
    }
    @Test fun rejectsHugeBody() {
        val e = assertFailsWith<HttpParser.BadRequest> { req("POST / HTTP/1.1\r\nContent-Length: 999999\r\n\r\n") }
        assertEquals(413, e.status)
    }
    @Test fun authAndRouting() {
        val a = FakeActions()
        val router = ApiRouter("0123456789abcdef", a)
        assertEquals(200, router.handle(req("GET /api/ping HTTP/1.1\r\n\r\n")).status)
        assertEquals(401, router.handle(req("GET /api/status HTTP/1.1\r\n\r\n")).status)
        assertEquals(401, router.handle(req("GET /api/status HTTP/1.1\r\nX-Synapse-Key: wrong\r\n\r\n")).status)
        assertEquals(200, router.handle(req("GET /api/status HTTP/1.1\r\nAuthorization: Bearer 0123456789abcdef\r\n\r\n")).status)
        val k = "X-Synapse-Key: 0123456789abcdef\r\n"
        assertEquals(405, router.handle(req("GET /api/wake HTTP/1.1\r\n$k\r\n")).status)
        assertEquals(200, router.handle(req("POST /api/wake HTTP/1.1\r\n$k\r\n")).status)
        val body = "{\"text\":\"Dinner is ready\"}"
        assertEquals(200, router.handle(req("POST /api/speak HTTP/1.1\r\n${k}Content-Length: ${body.length}\r\n\r\n$body")).status)
        assertEquals(400, router.handle(req("POST /api/speak HTTP/1.1\r\n${k}Content-Length: 2\r\n\r\n{}")).status)
        assertEquals(400, router.handle(req("POST /api/speak HTTP/1.1\r\n${k}Content-Length: 3\r\n\r\n{x}")).status)
        assertEquals(404, router.handle(req("GET /nope HTTP/1.1\r\n$k\r\n")).status)
        assertEquals(listOf("wake", "speak:Dinner is ready"), a.calls)
    }
    @Test fun disabledWithoutKey() {
        assertEquals(401, ApiRouter("", FakeActions()).handle(req("GET /api/status HTTP/1.1\r\n\r\n")).status)
    }
    @Test fun responseBytes() {
        val s = String(HttpResponse.json(200, mapOf("ok" to true)).toBytes())
        assertTrue(s.startsWith("HTTP/1.1 200 OK\r\n")); assertTrue(s.endsWith("{\"ok\":true}"))
    }
}

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
        assertFalse(r.contains("0123"))              // no token suffix at all
        assertTrue(r.contains("(set)"))
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
        val c = IdleController(10_000, graceMs = 4_000)
        assertFalse(c.tick(0))
        assertFalse(c.tick(9_999))
        assertTrue(c.tick(10_000)); assertEquals(IdleController.Mode.AMBIENT, c.mode)
        // Within the grace window, a sensor wake is ignored (prevents the dim->settle bounce).
        assertFalse(c.activity(11_000)); assertEquals(IdleController.Mode.AMBIENT, c.mode)
        // A real touch (force) wakes even inside grace.
        assertTrue(c.activity(11_500, force = true)); assertEquals(IdleController.Mode.ACTIVE, c.mode)
        assertFalse(c.tick(15_000))
        // Force ambient, then a sensor wake after the grace window wakes normally.
        assertTrue(c.forceAmbient(20_000)); assertFalse(c.forceAmbient(20_000))
        assertFalse(c.activity(22_000))                       // still in grace
        assertTrue(c.activity(24_001)); assertEquals(IdleController.Mode.ACTIVE, c.mode)  // past grace
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
        var jpeg: ByteArray? = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2)
        override fun snapshot(): ByteArray? = jpeg
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

class SnapshotApiTest {
    private fun req(raw: String) = HttpParser.read(ByteArrayInputStream(raw.toByteArray()))
    private class A : NodeActions {
        var jpeg: ByteArray? = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 9)
        override fun status() = emptyMap<String, Any?>()
        override fun wake() {}
        override fun ambient() {}
        override fun speak(text: String) = true
        override fun reload() {}
        override fun presence(source: String) {}
        override fun snapshot() = jpeg
    }
    @Test fun snapshotWithQueryKeyReturnsJpeg() {
        val a = A()
        val r = ApiRouter("0123456789abcdef", a)
        val resp = r.handle(req("GET /api/snapshot?key=0123456789abcdef HTTP/1.1\r\n\r\n"))
        assertEquals(200, resp.status)
        val bytes = resp.toBytes()
        val head = String(bytes, 0, bytes.size - 3, Charsets.ISO_8859_1)
        assertTrue(head.contains("Content-Type: image/jpeg\r\n"))
        assertTrue(head.contains("Content-Length: 3\r\n"))
        assertEquals(0xD8.toByte(), bytes[bytes.size - 2])
        a.jpeg = null
        assertEquals(503, r.handle(req("GET /api/snapshot?key=0123456789abcdef HTTP/1.1\r\n\r\n")).status)
    }
    @Test fun queryKeyOnlyAcceptedForSnapshot() {
        val r = ApiRouter("0123456789abcdef", A())
        assertEquals(401, r.handle(req("GET /api/status?key=0123456789abcdef HTTP/1.1\r\n\r\n")).status)
        assertEquals(401, r.handle(req("GET /api/snapshot?key=wrongwrongwrong1 HTTP/1.1\r\n\r\n")).status)
    }
}

class BleTest {
    @Test fun parsesIBeacon() {
        // 02 15 | uuid(16) | major 0x0001 | minor 0x0102 | tx
        val uuid = "e2c56db5dffb48d2b060d0f5a71096e0"
        val bytes = ByteArray(23)
        bytes[0] = 0x02; bytes[1] = 0x15
        for (i in 0 until 16) bytes[2 + i] = uuid.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        bytes[18] = 0; bytes[19] = 1; bytes[20] = 1; bytes[21] = 2; bytes[22] = (-59).toByte()
        assertEquals("e2c56db5-dffb-48d2-b060-d0f5a71096e0:1:258", BleParser.iBeaconId(bytes))
        assertEquals(null, BleParser.iBeaconId(byteArrayOf(0x02, 0x15)))
        assertEquals(null, BleParser.iBeaconId(ByteArray(23)))
    }
    @Test fun tracksKnownDevicesWithSmoothingAndExpiry() {
        val t = BleTracker(mapOf("AA:BB:CC:DD:EE:FF" to "Watch", "e2c56db5-dffb-48d2-b060-d0f5a71096e0:1:258" to "Mason phone"))
        t.onAdvert("aa:bb:cc:dd:ee:ff", -60, null, 0)
        t.onAdvert("11:22:33:44:55:66", -70, "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0:1:258", 0)
        t.onAdvert("99:99:99:99:99:99", -90, null, 0)
        var s = t.snapshot(1_000)
        assertEquals(3, s.devicesSeen)
        assertEquals(listOf("Watch", "Mason phone"), s.known.map { it.name })
        assertTrue(s.anyKnownNear)
        t.onAdvert("aa:bb:cc:dd:ee:ff", -96, null, 2_000) // one weak packet doesn't knock it out of "near"
        s = t.snapshot(2_000)
        assertEquals(-72, s.known.first { it.name == "Watch" }.rssi)
        assertTrue(s.known.first { it.name == "Watch" }.near)
        s = t.snapshot(70_000)
        assertEquals(0, s.devicesSeen)
        assertTrue(s.known.isEmpty())
    }
    @Test fun configParsesBleAndCamera() {
        val c = NodeConfig.fromJson("""{"node_id":"n","ha_url":"http://h:8123","ha_token":"abcdefghijklmnopqrstuvwxyz0123",
            "ble_known":{"AA:BB:CC:DD:EE:FF":"Watch"},"camera":"Back"}""")
        assertEquals(mapOf("aa:bb:cc:dd:ee:ff" to "Watch"), c.bleKnown)
        assertEquals("back", c.camera)
        assertTrue(c.validate().isEmpty(), c.validate().toString())
        assertEquals(c, NodeConfig.fromJson(c.toJson()))
        assertEquals(1, NodeConfig.fromJson("""{"node_id":"n","ha_url":"http://h","ha_token":"abcdefghijklmnopqrstuvwxyz0123","camera":"side"}""").validate().size)
    }
}

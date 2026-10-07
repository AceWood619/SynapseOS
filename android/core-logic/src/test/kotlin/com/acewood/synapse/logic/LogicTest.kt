package com.acewood.synapse.logic

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertNotEquals

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

class HaWsTest {
    @Test fun buildsMessages() {
        assertEquals("""{"type":"auth","access_token":"abc"}""", HaWs.authMessage("abc"))
        assertEquals("""{"id":1,"type":"subscribe_events","event_type":"state_changed"}""", HaWs.subscribeStatesMessage(1))
        assertEquals("""{"id":2,"type":"get_states"}""", HaWs.getStatesMessage(2))
        val call = Json.parseObject(HaWs.callServiceMessage(5, "light", "turn_on", mapOf("brightness" to 200.0), mapOf("entity_id" to "light.kitchen")))
        assertEquals("light", call["domain"]); assertEquals("turn_on", call["service"])
        assertEquals("light.kitchen", ((call["target"] as Map<*,*>)["entity_id"]))
    }
    @Test fun parsesAuthHandshake() {
        assertTrue(HaWs.parse("""{"type":"auth_required","ha_version":"2026.10"}""") is HaWs.Frame.AuthRequired)
        assertTrue(HaWs.parse("""{"type":"auth_ok","ha_version":"2026.10"}""") is HaWs.Frame.AuthOk)
        val inv = HaWs.parse("""{"type":"auth_invalid","message":"bad token"}""")
        assertTrue(inv is HaWs.Frame.AuthInvalid && inv.message == "bad token")
    }
    @Test fun parsesGetStatesResultAndEvents() {
        val result = """{"id":2,"type":"result","success":true,"result":[
            {"entity_id":"light.kitchen","state":"on","attributes":{"friendly_name":"Kitchen","brightness":180}},
            {"entity_id":"sensor.temp","state":"72.5","attributes":{"unit_of_measurement":"°F"}}]}"""
        val f = HaWs.parse(result)
        assertTrue(f is HaWs.Frame.Result)
        val states = (f as HaWs.Frame.Result).states!!
        assertEquals(2, states.size)
        assertEquals("Kitchen", states[0].friendlyName); assertTrue(states[0].on); assertEquals("light", states[0].domain)

        val evt = """{"type":"event","event":{"event_type":"state_changed","data":{"entity_id":"light.kitchen",
            "new_state":{"entity_id":"light.kitchen","state":"off","attributes":{"friendly_name":"Kitchen"}}}}}"""
        val e = HaWs.parse(evt)
        assertTrue(e is HaWs.Frame.StateChanged)
        assertEquals("off", (e as HaWs.Frame.StateChanged).entity!!.state)
        assertTrue(HaWs.parse("""{"type":"pong","id":9}""") is HaWs.Frame.Pong)
    }
    @Test fun entityCacheAppliesAndQueries() {
        val c = EntityCache()
        val r = HaWs.parse("""{"id":2,"type":"result","success":true,"result":[
            {"entity_id":"light.kitchen","state":"on","attributes":{}},
            {"entity_id":"light.lamp","state":"off","attributes":{}},
            {"entity_id":"sensor.temp","state":"72","attributes":{}}]}""") as HaWs.Frame.Result
        c.applyStates(r.states!!)
        assertEquals(3, c.size())
        assertEquals(2, c.byDomain("light").size)
        assertEquals("on", c.get("light.kitchen")!!.state)
        val e = HaWs.parse("""{"type":"event","event":{"event_type":"state_changed","data":{"new_state":
            {"entity_id":"light.kitchen","state":"off","attributes":{}}}}}""") as HaWs.Frame.StateChanged
        c.applyStateChanged(e.entity)
        assertEquals("off", c.get("light.kitchen")!!.state)
        assertFalse(c.get("light.kitchen")!!.on)
    }
}

class ProfilesTest {
    private fun ps(vararg specs: Triple<Role, String, String>): Profiles {
        var p = Profiles(PinHash.newSalt(), emptyList(), null)
        for ((role, name, pin) in specs) p = p.withProfile(name.lowercase(), name, role, pin)
        return p
    }
    @Test fun roleCapabilities() {
        assertTrue(Role.ADMIN.canDoAction(2) && Role.ADMIN.canManageProfiles)
        assertTrue(Role.USER.canDoAction(2) && !Role.USER.canEditSettings && !Role.USER.canManageProfiles)
        assertFalse(Role.CHILD.canDoAction(1)); assertTrue(Role.CHILD.canDoAction(0) && Role.CHILD.simplified)
        assertFalse(Role.GUEST.canDoAction(2)); assertTrue(Role.GUEST.canDoAction(0))
        assertEquals(Role.GUEST, Role.from("nonsense")); assertEquals(Role.ADMIN, Role.from("Admin"))
    }
    @Test fun pinHashingSaltedAndStable() {
        val salt = PinHash.newSalt()
        val h = PinHash.hash("2468", salt)
        assertTrue(PinHash.equal(h, PinHash.hash("2468", salt)))         // same salt+pin -> same hash
        assertFalse(PinHash.equal(h, PinHash.hash("0000", salt)))
        assertNotEquals(PinHash.hash("2468", PinHash.newSalt()), h)       // different salt -> different hash
    }
    @Test fun resolvesActiveUserByPin() {
        val salt = PinHash.newSalt()
        val p = Profiles(salt, emptyList(), null)
            .withProfile("admin", "Mason", Role.ADMIN, "1111")
            .withProfile("user", "Riah", Role.USER, "2222")
            .withProfile("child", "Kid", Role.CHILD, "3333", Layout(orientation = "landscape", tileScale = 1.4f))
            .withProfile("guest", "Guest", Role.GUEST, "4444")
        assertEquals("Mason", p.resolve("1111")!!.name)
        assertEquals(Role.CHILD, p.resolve("3333")!!.role)
        assertEquals("landscape", p.resolve("3333")!!.layout.orientation)
        assertNull(p.resolve("9999")); assertNull(p.resolve(""))
        assertEquals("Mason", p.admin!!.name)
        assertTrue(p.validate().isEmpty(), p.validate().toString())
    }
    @Test fun validationCatchesProblems() {
        val noAdmin = Profiles(PinHash.newSalt(), emptyList(), null).withProfile("u", "U", Role.USER, "1")
        assertTrue(noAdmin.validate().any { "admin" in it })
        val salt = PinHash.newSalt()
        val dup = Profiles(salt, emptyList(), null)
            .withProfile("admin", "A", Role.ADMIN, "1234")
            .withProfile("user", "B", Role.USER, "1234")
        assertTrue(dup.validate().any { "share a PIN" in it }, dup.validate().toString())
    }
    @Test fun jsonRoundTrip() {
        val ps = Profiles.starter("Mason", "2468", "0000")
        val back = Profiles.fromJson(ps.toJson())
        assertEquals(2, back.list.size)
        assertEquals("Mason", back.admin!!.name)
        assertEquals(Role.ADMIN, back.resolve("2468")!!.role)
        assertEquals("guest", back.defaultProfile!!.id)
        assertEquals(1.25f, back.byId("guest")!!.layout.tileScale)
    }
}

class RoomsTest {
    // Mason's real HA areas (from HANDS' map).
    private val areas = listOf(
        Area("living_room", "Living Room", listOf(
            "light.lr_lamp", "switch.lr_lamp", "media_player.living_room_50_onn_roku_tv",
            "switch.ring_alert", "switch.motion_alert", "switch.hour_ding")),
        Area("master_bedroom", "Master bedroom", listOf(
            "light.bedroom", "light.cync_lan_694243630_22", "light.zz_cloud_bedroom_led_strip",
            "light.zz_cloud_mb_lamp_top", "switch.master_bedroom_jarvis_microphone",
            "switch.led_strip_led_strip_mitm_mode", "media_player.riahs_room_50_onn_roku_tv")),
        Area("kids_room", "Kids Room", listOf(
            "light.cync_lan_694243630_188", "light.zz_cloud_kids_bedroom_light",
            "switch.kids_bedroom_jarvis_microphone", "media_player.kids_room_juniors_roku")),
        Area("dining_room", "Dining Room", listOf(
            "light.cync_lan_694243630_102", "light.zz_cloud_dining_room_light",
            "switch.dining_room_dining_room_windows_mute")),
        Area("hallway", "Hallway", listOf("light.cync_lan_694243630_239", "light.zz_cloud_hallway_light")),
        Area("kitchen", "Kitchen", emptyList()),
        Area("front_door", "Front door", emptyList()),
    )

    @Test fun buildsChannelsSkippingKitchenAndEmpty() {
        val rooms = Rooms.build(areas)
        assertEquals(listOf("Living Room", "Master bedroom", "Kids Room", "Dining Room", "Hallway"), rooms.map { it.name })
        assertTrue(rooms.none { it.id == "kitchen" || it.id == "front_door" })
    }
    @Test fun hidesCloudTwinLightsWhenAsked() {
        val cfg = RoomsConfig(hideCloudTwins = true)   // opt-in; default now keeps both as mutual backup
        val mb = Rooms.build(areas, cfg).first { it.id == "master_bedroom" }
        assertTrue(mb.lights.none { it.contains("zz_cloud") }, mb.lights.toString())
        assertTrue(mb.lights.contains("light.bedroom") && mb.lights.contains("light.cync_lan_694243630_22"))
        val hall = Rooms.build(areas, cfg).first { it.id == "hallway" }
        assertEquals(listOf("light.cync_lan_694243630_239"), hall.lights)
        // default keeps both:
        val hallBoth = Rooms.build(areas).first { it.id == "hallway" }
        assertEquals(2, hallBoth.lights.size)
    }
    @Test fun classifiesMediaAndExtras() {
        val lr = Rooms.build(areas).first { it.id == "living_room" }
        assertEquals("media_player.living_room_50_onn_roku_tv", lr.primaryMedia)
        assertTrue(lr.switches.contains("switch.lr_lamp"))                 // a real lamp = primary
        assertTrue(lr.extras.containsAll(listOf("switch.ring_alert", "switch.motion_alert", "switch.hour_ding")))
        val mb = Rooms.build(areas).first { it.id == "master_bedroom" }
        assertTrue(mb.extras.contains("switch.master_bedroom_jarvis_microphone"))
    }
    @Test fun respectsExplicitOrderAndCanKeepTwins() {
        val ordered = Rooms.build(areas, RoomsConfig(order = listOf("hallway", "living_room")))
        assertEquals("Hallway", ordered.first().name)
        val keepTwins = Rooms.build(areas, RoomsConfig(hideCloudTwins = false)).first { it.id == "hallway" }
        assertEquals(2, keepTwins.lights.size)
    }
}

class ResilientLightTest {
    @Test fun pairsSingleLocalAndCloud() {
        val (pair, ambiguous) = ResilientLight.pairRoomLights(listOf("light.cync_lan_694243630_239", "light.zz_cloud_hallway_light"))
        assertEquals(1, pair.size); assertFalse(ambiguous)
        assertEquals("light.cync_lan_694243630_239", pair[0].local)
        assertEquals("light.zz_cloud_hallway_light", pair[0].cloud)
        assertEquals("Hallway Light", pair[0].name)
    }
    @Test fun doesNotGuessWhenMany() {
        val (pair, ambiguous) = ResilientLight.pairRoomLights(listOf(
            "light.bedroom", "light.cync_lan_694243630_22", "light.zz_cloud_bedroom_led_strip", "light.zz_cloud_mb_lamp_top"))
        assertEquals(4, pair.size)       // left individual, not mis-paired
        assertTrue(ambiguous)             // flagged so Mason can map them
    }
    @Test fun failsOverLocalToCloud() {
        val c = EntityCache()
        c.applyStates(listOf(
            Entity("light.cync_lan_1", "unavailable", emptyMap()),
            Entity("light.zz_cloud_hallway_light", "on", emptyMap())))
        val rl = ResilientLight("Hallway", "light.cync_lan_1", "light.zz_cloud_hallway_light")
        assertEquals("light.zz_cloud_hallway_light", rl.stateSource(c))   // local dead -> cloud
        assertTrue(rl.isOn(c))
        assertEquals(listOf("light.zz_cloud_hallway_light"), rl.commandTargets(c))
        // local back online -> prefer it
        c.applyStates(listOf(Entity("light.cync_lan_1", "off", emptyMap())))
        assertEquals("light.cync_lan_1", rl.stateSource(c))
        assertEquals(listOf("light.cync_lan_1", "light.zz_cloud_hallway_light"), rl.commandTargets(c, both = true))
    }
}

class RoomOrderTest {
    private fun room(id: String, name: String, lights: List<String> = emptyList(), media: List<String> = emptyList()) =
        Room(id, name, lights, emptyList(), media, emptyList())
    @Test fun hereThenActiveThenRest() {
        val rooms = listOf(
            room("living_room", "Living Room", lights = listOf("light.lr")),
            room("master_bedroom", "Master bedroom", lights = listOf("light.mb")),
            room("office", "Office"),
            room("hallway", "Hallway"))
        val c = EntityCache()
        c.applyStates(listOf(Entity("light.lr", "off", emptyMap()), Entity("light.mb", "on", emptyMap())))
        // Panel is in the office; master bedroom is active (light on); daytime.
        val ordered = RoomOrder.order(rooms, hereRoomId = "office", cache = c, hourOfDay = 14,
            baseOrder = listOf("living_room", "master_bedroom", "office", "hallway"))
        assertEquals(listOf("Office", "Master bedroom", "Living Room", "Hallway"), ordered.map { it.name })
    }
    @Test fun nightNudgesBedroomsUp() {
        val rooms = listOf(room("living_room", "Living Room"), room("kids_room", "Kids Room"), room("master_bedroom", "Master bedroom"))
        val ordered = RoomOrder.order(rooms, hereRoomId = null, cache = EntityCache(), hourOfDay = 23,
            baseOrder = listOf("living_room", "kids_room", "master_bedroom"))
        assertEquals(listOf("Kids Room", "Master bedroom", "Living Room"), ordered.map { it.name })
    }
}

class MediaRemoteTest {
    @Test fun dpadGoesToRemoteAsRokuKeys() {
        val c = MediaRemote.press(MediaRemote.Button.OK, remoteEntityId = "remote.living_roku", mediaEntityId = "media_player.living_roku")!!
        assertEquals("remote", c.domain)
        assertEquals("send_command", c.service)
        assertEquals("remote.living_roku", c.entityId)
        assertEquals("Select", c.data["command"])
        assertEquals("Up", MediaRemote.press(MediaRemote.Button.UP, "remote.r", null)!!.data["command"])
        assertEquals("InstantReplay", MediaRemote.press(MediaRemote.Button.REPLAY, "remote.r", null)!!.data["command"])
    }
    @Test fun withoutRemoteOnlyTransportFallsBackToMediaPlayer() {
        // navigation keys have no media_player equivalent
        assertNull(MediaRemote.press(MediaRemote.Button.UP, null, "media_player.x"))
        assertNull(MediaRemote.press(MediaRemote.Button.OK, null, "media_player.x"))
        // transport keys map onto the media_player
        assertEquals("media_play_pause", MediaRemote.press(MediaRemote.Button.PLAY, null, "media_player.x")!!.service)
        assertEquals("media_next_track", MediaRemote.press(MediaRemote.Button.FWD, null, "media_player.x")!!.service)
        // nothing to drive at all
        assertNull(MediaRemote.press(MediaRemote.Button.PLAY, null, null))
    }
    @Test fun transportHelpers() {
        assertEquals("volume_mute", MediaRemote.mute("media_player.x", true).service)
        assertEquals(true, MediaRemote.mute("media_player.x", true).data["is_volume_muted"])
        assertEquals("turn_off", MediaRemote.power("media_player.x", on = false).service)
    }
}

class RoomControlTest {
    private fun cache(vararg e: Entity) = EntityCache().apply { applyStates(e.toList()) }

    @Test fun pairsLocalCloudAndReadsBrightness() {
        val room = Rooms.build(listOf(Area("living_room", "Living Room",
            listOf("light.cync_lan_123456_1", "light.zz_cloud_living_lamp")))).first()
        val c = cache(
            Entity("light.cync_lan_123456_1", "on", mapOf("brightness" to 128.0, "supported_color_modes" to listOf("brightness"))),
            Entity("light.zz_cloud_living_lamp", "on", emptyMap()))
        val m = RoomControl.build(room, c)
        assertEquals(1, m.lights.size)              // local+cloud fused into one tile
        assertFalse(m.lightsAmbiguous)
        assertTrue(m.lights[0].isOn)
        assertTrue(m.lights[0].dimmable)
        assertEquals(50, m.lights[0].brightnessPct) // 128/255 ≈ 50%
        assertTrue(m.anyLightOn)
    }
    @Test fun onoffLightIsNotDimmable() {
        val room = Rooms.build(listOf(Area("hall", "Hall", listOf("light.porch")))).first()
        val c = cache(Entity("light.porch", "off", mapOf("supported_color_modes" to listOf("onoff"))))
        val t = RoomControl.build(room, c).lights.single()
        assertFalse(t.dimmable)
        assertNull(t.brightnessPct)
        assertFalse(t.isOn)
    }
    @Test fun rokuMediaGetsDpadAndRemote() {
        val room = Rooms.build(listOf(Area("den", "Den",
            listOf("media_player.den_roku", "remote.den_roku")))).first()
        val c = cache(Entity("media_player.den_roku", "playing",
            mapOf("supported_features" to 16384.0 + 32 + 4, "media_title" to "The Mandalorian", "volume_level" to 0.4, "app_name" to "Disney+")))
        val mt = RoomControl.build(room, c).media.single()
        assertTrue(mt.showDpad)
        assertEquals("remote.den_roku", mt.remoteEntityId)
        assertTrue(mt.isPlaying)
        assertTrue(mt.canTransport)
        assertEquals("The Mandalorian", mt.title)
        assertEquals(40, mt.volumePct)
    }
    @Test fun plainMediaNoDpadNoRemote() {
        val room = Rooms.build(listOf(Area("office", "Office", listOf("media_player.sonos")))).first()
        val c = cache(Entity("media_player.sonos", "paused", mapOf("supported_features" to 1.0 /* PAUSE only */)))
        val mt = RoomControl.build(room, c).media.single()
        assertFalse(mt.showDpad)
        assertNull(mt.remoteEntityId)
        assertNull(mt.volumePct)            // no VOLUME_SET/STEP bit
        assertTrue(mt.canTransport)         // PAUSE counts
        assertFalse(mt.isPlaying)
    }
    @Test fun unavailableLightFlagged() {
        val room = Rooms.build(listOf(Area("bed", "Bed", listOf("light.bedside")))).first()
        val c = cache(Entity("light.bedside", "unavailable", emptyMap()))
        val t = RoomControl.build(room, c).lights.single()
        assertFalse(t.available)
        assertFalse(t.isOn)
    }
    @Test fun switchesAndExtrasBecomeToggles() {
        val room = Rooms.build(listOf(Area("living_room", "Living Room",
            listOf("switch.lamp", "switch.jarvis_microphone")))).first()
        val c = cache(Entity("switch.lamp", "on", emptyMap()), Entity("switch.jarvis_microphone", "off", emptyMap()))
        val m = RoomControl.build(room, c)
        assertEquals(2, m.extras.size)
        assertTrue(m.extras.any { it.name.contains("lamp", ignoreCase = true) && it.isOn })
    }
}

class HaRegistryTest {
    private fun area(id: String, name: String) = mapOf("area_id" to id, "name" to name)
    private fun ent(id: String, areaId: String? = null, deviceId: String? = null,
                    disabled: Boolean = false, hidden: Boolean = false, cat: String? = null) =
        buildMap<String, Any?> {
            put("entity_id", id)
            areaId?.let { put("area_id", it) }
            deviceId?.let { put("device_id", it) }
            if (disabled) put("disabled_by", "user")
            if (hidden) put("hidden_by", "user")
            cat?.let { put("entity_category", it) }
        }
    private fun dev(id: String, areaId: String?) = mapOf("id" to id, "area_id" to areaId)

    @Test fun groupsByDirectAreaAndDeviceInheritance() {
        val areas = listOf(area("living_room", "Living Room"), area("hallway", "Hallway"))
        val entities = listOf(
            ent("light.lr_lamp", areaId = "living_room"),              // direct
            ent("media_player.lr_roku", deviceId = "dev1"),            // inherits from device
            ent("light.hallway", areaId = "hallway"))
        val devices = listOf(dev("dev1", "living_room"))
        val built = HaRegistry.buildAreas(areas, entities, devices)
        assertEquals(listOf("Living Room", "Hallway"), built.map { it.name })   // HA order kept
        assertEquals(listOf("light.lr_lamp", "media_player.lr_roku"), built[0].entities)
    }
    @Test fun skipsDisabledHiddenConfigAndEmptyAreas() {
        val areas = listOf(area("living_room", "Living Room"), area("kitchen", "Kitchen"), area("ghost", "Ghost"))
        val entities = listOf(
            ent("light.lr", areaId = "living_room"),
            ent("light.disabled", areaId = "living_room", disabled = true),
            ent("sensor.hidden", areaId = "living_room", hidden = true),
            ent("switch.cfg", areaId = "living_room", cat = "config"),
            ent("sensor.diag", areaId = "kitchen", cat = "diagnostic"))   // kitchen ends up empty
        val built = HaRegistry.buildAreas(areas, entities, emptyList())
        assertEquals(listOf("Living Room"), built.map { it.name })       // kitchen+ghost have no real entities
        assertEquals(listOf("light.lr"), built.single().entities)
    }
    @Test fun feedsRoomsBuilderEndToEnd() {
        val areas = listOf(area("living_room", "Living Room"), area("kitchen", "Kitchen"))
        val entities = listOf(
            ent("light.lr_lamp", areaId = "living_room"),
            ent("media_player.lr_roku", areaId = "living_room"),
            ent("remote.lr_roku", areaId = "living_room"),
            ent("light.kitchen_dummy", areaId = "kitchen"))
        val rooms = Rooms.build(HaRegistry.buildAreas(areas, entities, emptyList()))  // default excludes kitchen
        assertEquals(listOf("Living Room"), rooms.map { it.name })
        assertEquals(listOf("light.lr_lamp"), rooms[0].lights)
        assertEquals(listOf("media_player.lr_roku"), rooms[0].media)
        assertEquals(listOf("remote.lr_roku"), rooms[0].remotes)
    }
}

class OwnerNameTest {
    @Test fun roundTripsOwnerName() {
        val c = NodeConfig.fromJson("""{"node_id":"livingroom-01","room":"Living Room","owner_name":"Mason",
            "ha_url":"http://ha.local:8123","ha_token":"abcdefghijklmnopqrstuvwxyz0123"}""")
        assertEquals("Mason", c.ownerName)
        assertEquals("Mason", NodeConfig.fromJson(c.toJson()).ownerName)
    }
    @Test fun defaultsToEmptyWhenAbsent() {
        val c = NodeConfig.fromJson("""{"node_id":"n","room":"r","ha_url":"http://ha.local:8123","ha_token":"abcdefghijklmnopqrstuvwxyz0123"}""")
        assertEquals("", c.ownerName)
    }
}

class RoomControlExpandedTest {
    private fun cache(vararg e: Entity) = EntityCache().apply { applyStates(e.toList()) }
    private fun room(vararg ents: String) =
        Rooms.build(listOf(Area("living_room", "Living Room", ents.toList()))).first()

    @Test fun detectsColorAndColorTempCapability() {
        val c = cache(
            Entity("light.rgb", "on", mapOf("supported_color_modes" to listOf("rgb", "color_temp"), "brightness" to 255.0)),
            Entity("light.ct", "on", mapOf("supported_color_modes" to listOf("color_temp"))),
            Entity("light.plain", "on", mapOf("supported_color_modes" to listOf("onoff"))))
        val rgb = RoomControl.build(room("light.rgb"), c).lights.single()
        assertTrue(rgb.colorCapable); assertTrue(rgb.colorTempCapable)
        val ct = RoomControl.build(room("light.ct"), c).lights.single()
        assertFalse(ct.colorCapable); assertTrue(ct.colorTempCapable)
        val plain = RoomControl.build(room("light.plain"), c).lights.single()
        assertFalse(plain.colorCapable); assertFalse(plain.colorTempCapable)
    }
    @Test fun buildsFanCoverClimateLockSensor() {
        val c = cache(
            Entity("fan.ceiling", "on", mapOf("percentage" to 66.0, "supported_features" to 1.0)),
            Entity("cover.blinds", "open", mapOf("current_position" to 40.0, "supported_features" to 4.0)),
            Entity("climate.nest", "heat", mapOf("current_temperature" to 68.0, "temperature" to 71.0,
                "min_temp" to 50.0, "max_temp" to 90.0, "target_temp_step" to 1.0)),
            Entity("lock.front", "locked", emptyMap()),
            Entity("sensor.temp", "72.4", mapOf("unit_of_measurement" to "°F", "friendly_name" to "Living Temp")))
        val m = RoomControl.build(room("fan.ceiling", "cover.blinds", "climate.nest", "lock.front", "sensor.temp"), c)
        assertEquals(66, m.fans.single().speedPct); assertTrue(m.fans.single().supportsSpeed)
        assertEquals(40, m.covers.single().positionPct); assertTrue(m.covers.single().isOpen)
        assertEquals(71.0, m.climate.single().targetTemp); assertEquals(68.0, m.climate.single().currentTemp)
        assertTrue(m.locks.single().locked)
        assertEquals("72.4", m.sensors.single().value); assertEquals("°F", m.sensors.single().unit)
    }
    @Test fun roomsBuilderClassifiesNewDomains() {
        val r = room("light.a", "fan.b", "cover.c", "climate.d", "lock.e", "sensor.f", "binary_sensor.g")
        assertEquals(listOf("light.a"), r.lights)
        assertEquals(listOf("fan.b"), r.fans)
        assertEquals(listOf("cover.c"), r.covers)
        assertEquals(listOf("climate.d"), r.climate)
        assertEquals(listOf("lock.e"), r.locks)
        assertEquals(listOf("sensor.f", "binary_sensor.g"), r.sensors)
    }
    @Test fun roomWithOnlySensorsStillAppears() {
        val rooms = Rooms.build(listOf(Area("front_door", "Front Door", listOf("binary_sensor.door", "lock.front"))))
        assertEquals(listOf("Front Door"), rooms.map { it.name })   // no longer dropped for lacking lights/media
    }
}

class ResilientPairingByNameTest {
    @Test fun pairsMultiByFriendlyName() {
        // master-bedroom-like: 3 local (numeric ids) + 2 cloud; names reveal the twins
        val lights = listOf(
            "light.cync_lan_694243630_22", "light.cync_lan_694243630_185", "light.cync_lan_694243630_245",
            "light.zz_cloud_bedroom_led_strip", "light.zz_cloud_mb_lamp_top")
        val names = mapOf(
            "light.cync_lan_694243630_22" to "Bedroom LED Strip",
            "light.cync_lan_694243630_185" to "MB Lamp Top",
            "light.cync_lan_694243630_245" to "Nightstand",
            "light.zz_cloud_bedroom_led_strip" to "Bedroom LED Strip",
            "light.zz_cloud_mb_lamp_top" to "MB Lamp Top")
        val (pairs, ambiguous) = ResilientLight.pairRoomLights(lights) { names[it] }
        val strip = pairs.first { it.name.contains("Strip", true) }
        assertEquals("light.cync_lan_694243630_22", strip.local)
        assertEquals("light.zz_cloud_bedroom_led_strip", strip.cloud)
        val lamp = pairs.first { it.name.contains("Lamp", true) }
        assertEquals("light.cync_lan_694243630_185", lamp.local)
        assertEquals("light.zz_cloud_mb_lamp_top", lamp.cloud)
        // the third local (Nightstand) has no cloud twin → stays local-only, not mis-paired
        val ns = pairs.first { it.name.contains("Nightstand", true) }
        assertEquals("light.cync_lan_694243630_245", ns.local); assertNull(ns.cloud)
        assertFalse(ambiguous)   // every cloud matched a local
    }
    @Test fun withoutNamesFallsBackToSinglesAndFlags() {
        val lights = listOf("light.cync_lan_1", "light.cync_lan_2", "light.zz_cloud_strip", "light.zz_cloud_lamp")
        val (pairs, ambiguous) = ResilientLight.pairRoomLights(lights)   // no name resolver
        assertEquals(4, pairs.size)      // nothing mis-paired
        assertTrue(ambiguous)            // flagged for mapping
    }
    @Test fun stillPairsOneLocalOneCloud() {
        val (pairs, ambiguous) = ResilientLight.pairRoomLights(listOf("light.cync_lan_9", "light.zz_cloud_hallway"))
        assertEquals(1, pairs.size); assertFalse(ambiguous)
        assertEquals("light.cync_lan_9", pairs[0].local); assertEquals("light.zz_cloud_hallway", pairs[0].cloud)
    }
}

class ConversationTest {
    @Test fun buildsConversationMessage() {
        val m = Json.parseObject(HaWs.conversationMessage(9, "turn off the lights", "abc"))
        assertEquals("conversation/process", m["type"]); assertEquals("turn off the lights", m["text"])
        assertEquals("abc", m["conversation_id"]); assertEquals("en", m["language"])
    }
    @Test fun parsesReplyAndObjectResult() {
        val raw = """{"id":9,"type":"result","success":true,"result":{"response":{"speech":{"plain":{"speech":"Turned off 3 lights"}}},"conversation_id":"c1"}}"""
        val f = HaWs.parse(raw) as HaWs.Frame.Result
        assertTrue(f.success)
        val (speech, conv) = HaWs.conversationReply(f.obj)
        assertEquals("Turned off 3 lights", speech); assertEquals("c1", conv)
    }
    @Test fun emptyReplyFallsBack() {
        assertEquals("(no reply)", HaWs.conversationReply(null).first)
    }
}

class SensorOnlyAreaTest {
    @Test fun carAreaIsNotAChannel() {
        val rooms = Rooms.build(listOf(
            Area("2018_gmc_terrain", "2018 GMC Terrain", listOf("sensor.terrain_fuel", "binary_sensor.terrain_locked")),
            Area("living_room", "Living Room", listOf("light.lr_lamp", "sensor.lr_temp")),
        ))
        assertEquals(listOf("living_room"), rooms.map { it.id })
    }
}

class RoomPadV3Test {
    private fun cache(vararg e: Entity) = EntityCache().apply { applyStates(e.toList()) }

    @Test fun listeningLightsAreNotRoomControls() {
        val rooms = Rooms.build(listOf(Area("living_room", "Living Room", listOf(
            "light.living_lamp", "light.living_listening_light"))))
        assertEquals(listOf("light.living_lamp"), rooms.single().lights)
    }

    @Test fun friendlyNamesStripRoomPrefixAndInitials() {
        val room = Rooms.build(listOf(Area("living_room", "Living Room", listOf(
            "switch.living_lamp", "switch.lr_ceiling")))).single()
        val model = RoomControl.build(room, cache(
            Entity("switch.living_lamp", "off", mapOf("friendly_name" to "Living Room Lamp")),
            Entity("switch.lr_ceiling", "off", mapOf("friendly_name" to "Lr Ceiling"))))
        assertTrue(model.extras.any { it.name == "Lamp" })
        assertTrue(model.extras.any { it.name == "Ceiling" })
    }
}

class NowPlayingTest {
    @Test fun extractsAlbumArtTransportAndVolume() {
        val state = NowPlaying.from(Entity("media_player.tv", "playing", mapOf(
            "friendly_name" to "Living Room TV",
            "media_title" to "The Matrix",
            "app_name" to "Netflix",
            "entity_picture" to "/api/media_player_proxy/media_player.tv",
            "volume_level" to 0.42,
            "supported_features" to (16384.0 + 32 + 4))))
        assertEquals("The Matrix", state.title)
        assertEquals("Netflix", state.appName)
        assertEquals("/api/media_player_proxy/media_player.tv", state.albumArt)
        assertEquals(42, state.volumePct)
        assertTrue(state.canTransport)
        assertTrue(state.isActive)
    }
}

class IntercomTest {
    @Test fun discoversConfiguredHelpersAndAvoidsMasterByDefault() {
        val cache = EntityCache().apply { applyStates(listOf(
            Entity("input_select.intercom_room", "Living Room", mapOf("options" to listOf("Master bedroom", "Living Room", "Kids Room"))),
            Entity("input_text.intercom_message", "", mapOf("friendly_name" to "Intercom message")),
            Entity("script.intercom_send", "off", emptyMap()),
        )) }
        val target = Intercom.resolve(cache)!!
        assertEquals("input_text.intercom_message", target.messageInputId)
        assertEquals("Living Room", target.defaultRoom)
        assertTrue(Intercom.isMasterBedroom("Master bedroom"))
    }

    @Test fun doesNotGuessAnInputTextHelper() {
        val cache = EntityCache().apply { applyStates(listOf(
            Entity("input_select.intercom_room", "Living Room", mapOf("options" to listOf("Living Room"))),
            Entity("input_text.random_note", "", mapOf("friendly_name" to "Shopping list")),
            Entity("script.intercom_send", "off", emptyMap()),
        )) }
        assertNull(Intercom.resolve(cache)!!.messageInputId)
    }
}

class AmbientDataTest {
    @Test fun snapshotIncludesWeatherTimerAndPlayingMedia() {
        val cache = EntityCache().apply { applyStates(listOf(
            Entity("weather.home", "sunny", mapOf("temperature" to 72.0)),
            Entity("timer.dinner", "active", mapOf("friendly_name" to "Dinner", "remaining" to "00:15:00")),
            Entity("media_player.tv", "playing", mapOf("media_title" to "News", "supported_features" to 16384.0)),
        )) }
        val s = AmbientData.snapshot(cache)
        assertEquals("Sunny  72°", s.weather)
        assertEquals("00:15:00", s.timer?.remaining)
        assertEquals("News", s.nowPlaying?.title)
    }
}

class ProfileAdminTest {
    @Test fun editWithBlankPinPreservesHashAndAssignments() {
        val base = Profiles.starter("Admin", "2468")
        val next = base.upsert("admin", "Mason", Role.ADMIN, null, Layout(rooms = listOf("living_room"), homeApps = listOf("jarvis")))
        assertEquals("Mason", "2468".let { next.resolve(it)?.name })
        assertEquals(listOf("living_room"), next.admin!!.layout.rooms)
        assertEquals(listOf("jarvis"), next.admin!!.layout.homeApps)
        assertFalse(next.toJson().contains("2468"))
    }
}

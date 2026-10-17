package com.acewood.synapse.logic

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CameraFeedTest {
    private fun jpeg(tag: Int, size: Int = 50): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(size) { ((it + tag) % 200).toByte() } + byteArrayOf(0xFF.toByte(), 0xD9.toByte())

    private fun part(j: ByteArray) = "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${j.size}\r\n\r\n".toByteArray() + j + "\r\n".toByteArray()

    /** Delivers the stream in tiny reads so markers get split across chunks. */
    private class Trickle(private val data: ByteArray, private val step: Int) : InputStream() {
        private var i = 0
        override fun read(): Int = if (i < data.size) data[i++].toInt() and 0xFF else -1
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (i >= data.size) return -1
            val n = minOf(step, len, data.size - i); System.arraycopy(data, i, b, off, n); i += n; return n
        }
    }

    @Test fun extractsFramesFromMultipartStream() {
        val a = jpeg(1); val b = jpeg(2, 3000); val c = jpeg(3)
        val r = MjpegFrameReader(ByteArrayInputStream(part(a) + part(b) + part(c)))
        assertContentEquals(a, r.next()); assertContentEquals(b, r.next()); assertContentEquals(c, r.next())
        assertNull(r.next())
    }

    @Test fun survivesMarkersSplitAcrossReads() {
        val a = jpeg(7, 400); val b = jpeg(9, 10)
        for (step in listOf(1, 2, 3, 7)) {
            val r = MjpegFrameReader(Trickle(part(a) + part(b), step))
            assertContentEquals(a, r.next(), "step $step"); assertContentEquals(b, r.next(), "step $step")
        }
    }

    @Test fun dropsOversizeGarbageAndResyncs() {
        val garbage = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(5000) { 1 }   // never ends
        val good = jpeg(4)
        val r = MjpegFrameReader(ByteArrayInputStream(garbage + part(good)), maxFrameBytes = 1000)
        assertContentEquals(good, r.next())
    }

    @Test fun accessFollowsProfileLevels() {
        val p = { role: Role -> Profile("x", "X", role, "") }
        assertTrue(CameraFeed.allowed(null)); assertTrue(CameraFeed.allowed(p(Role.ADMIN))); assertTrue(CameraFeed.allowed(p(Role.USER)))
        assertFalse(CameraFeed.allowed(p(Role.CHILD))); assertFalse(CameraFeed.allowed(p(Role.GUEST)))
    }

    @Test fun pathsLabelsAndRate() {
        assertEquals("/api/camera_proxy_stream/camera.kids_room", CameraFeed.streamPath("camera.kids_room"))
        assertEquals("/api/camera_proxy/camera.kids_room", CameraFeed.snapshotPath("camera.kids_room"))
        assertEquals("Kids Room camera", CameraFeed.label("camera.vm64", "Kids Room", "VM64"))
        assertEquals("vm64 main", CameraFeed.label("camera.vm64_main", null, null))
        assertTrue(CameraFeed.shouldShow(1000, 899)); assertFalse(CameraFeed.shouldShow(1000, 950))
    }

    @Test fun roomsCollectCameras() {
        val r = Rooms.build(listOf(Area("kids_room", "Kids Room", listOf("camera.vm64", "light.kids_lamp")),
            Area("porch", "Porch", listOf("camera.porch")))).associateBy { it.id }
        assertEquals(listOf("camera.vm64"), r["kids_room"]!!.cameras)
        assertEquals(listOf("camera.porch"), r["porch"]!!.cameras)   // camera-only area still becomes a channel
    }
}

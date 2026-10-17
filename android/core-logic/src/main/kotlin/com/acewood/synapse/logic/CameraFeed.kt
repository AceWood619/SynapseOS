package com.acewood.synapse.logic

import java.io.InputStream

/**
 * Live camera support for Synapse (kids' room VM64 camera, or any HA `camera.*`).
 *
 * Home Assistant re-serves every camera as MJPEG at `/api/camera_proxy_stream/<entity_id>` (auth with
 * the panel's Bearer token) and as a single JPEG at `/api/camera_proxy/<entity_id>`. MJPEG is just
 * JPEG frames back to back with small multipart headers, so instead of a video decoder we cut whole
 * JPEGs out of the byte stream by their start (FF D8) and end (FF D9) markers. Like reading a roll
 * of film frame by frame: no codec, cheap on the GE8320.
 */
object CameraFeed {
    fun streamPath(entityId: String) = "/api/camera_proxy_stream/$entityId"
    fun snapshotPath(entityId: String) = "/api/camera_proxy/$entityId"

    /** Level-1 action per the profile model: Admin/User yes, Child/Guest no. No profile = owner panel. */
    fun allowed(profile: Profile?): Boolean = profile == null || profile.role.maxLevel >= 1

    /** Friendly label: "Kids Room camera" from the room, else the entity's name. */
    fun label(entityId: String, roomName: String?, friendlyName: String?): String =
        roomName?.let { "$it camera" } ?: friendlyName ?: entityId.substringAfter('.').replace('_', ' ')

    /** Pick a display rate: never faster than [maxFps], and drop frames when decoding falls behind. */
    fun shouldShow(nowMs: Long, lastShownMs: Long, maxFps: Int = 10): Boolean =
        nowMs - lastShownMs >= 1000L / maxFps.coerceIn(1, 30)
}

/**
 * Pulls complete JPEG frames out of an MJPEG byte stream. Pure Java I/O so it is unit-testable.
 * [maxFrameBytes] guards memory: a frame that never ends (corrupt stream) is dropped, not buffered forever.
 */
class MjpegFrameReader(private val input: InputStream, private val maxFrameBytes: Int = 2 * 1024 * 1024) {
    private val chunk = ByteArray(16 * 1024)
    private var buf = ByteArray(64 * 1024)
    private var len = 0
    private var scanFrom = 0

    /** The next whole JPEG (FF D8 … FF D9), or null at end of stream. */
    fun next(): ByteArray? {
        while (true) {
            frameIn()?.let { return it }
            val n = input.read(chunk)
            if (n < 0) return null
            append(chunk, n)
        }
    }

    private fun append(src: ByteArray, n: Int) {
        if (len + n > buf.size) {
            if (len + n > maxFrameBytes * 2) { len = 0; scanFrom = 0 }   // runaway: drop and resync
            else buf = buf.copyOf(maxOf(buf.size * 2, len + n))
        }
        System.arraycopy(src, 0, buf, len, n); len += n
    }

    private fun frameIn(): ByteArray? {
        val start = indexOf(0xD8, 0)
        if (start < 0) { keepTail(); return null }
        val end = indexOf(0xD9, maxOf(start + 2, scanFrom))
        if (end < 0) {
            scanFrom = maxOf(start + 2, len - 1)
            if (len - start > maxFrameBytes) { len = 0; scanFrom = 0 }        // oversize: drop
            return null
        }
        var from = start
        if (end + 2 - start > maxFrameBytes) {
            // A broken frame never ended and swallowed the next one: restart at the last frame start
            // before this end marker. (Normal-size frames keep any embedded FF D8, e.g. EXIF thumbnails.)
            var i = end - 1
            while (i > start && !(buf[i] == 0xFF.toByte() && buf[i + 1] == 0xD8.toByte())) i--
            from = i
            if (from == start || end + 2 - from > maxFrameBytes) {   // nothing usable: drop it all
                val rest = len - (end + 2); System.arraycopy(buf, end + 2, buf, 0, rest); len = rest; scanFrom = 0
                return frameIn()
            }
        }
        val frame = buf.copyOfRange(from, end + 2)
        val rest = len - (end + 2)
        System.arraycopy(buf, end + 2, buf, 0, rest)
        len = rest; scanFrom = 0
        return frame
    }

    /** Index of the 0xFF,[marker] pair at or after [from], or -1. */
    private fun indexOf(marker: Int, from: Int): Int {
        var i = maxOf(from, 0)
        while (i < len - 1) {
            if (buf[i] == 0xFF.toByte() && buf[i + 1] == marker.toByte()) return i
            i++
        }
        return -1
    }

    /** No frame start yet: keep only the last byte (it might be the 0xFF of a split marker). */
    private fun keepTail() {
        if (len > 1) { buf[0] = buf[len - 1]; len = 1 }
        scanFrom = 0
    }
}

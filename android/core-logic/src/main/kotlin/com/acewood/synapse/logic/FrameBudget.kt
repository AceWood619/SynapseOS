package com.acewood.synapse.logic

/**
 * Frame-time gate for decorative animation on the PowerVR GE8320 (R-124 Part 6).
 *
 * Like a breaker for eye candy: the orb reports how long each of its draws took; if the recent
 * draws are consistently slow, the gate trips and the orb freezes on a static frame for good
 * (until the view is recreated). One slow frame (GC, a list rebuild) never trips it.
 */
class FrameBudget(
    private val limitNanos: Long = 20_000_000L,   // 20 ms per R-124
    private val window: Int = 30,
    private val slowAllowed: Int = 6,              // trip if >20% of the last 30 draws are slow
    private val warmup: Int = 10,                  // ignore the first draws (shader/JIT warmup)
) {
    private val samples = BooleanArray(window)
    private var seen = 0
    private var index = 0
    var tripped = false; private set

    /** Record one draw; returns true while animation should keep running. */
    fun record(drawNanos: Long): Boolean {
        if (tripped) return false
        seen++
        if (seen <= warmup) return true
        samples[index] = drawNanos > limitNanos
        index = (index + 1) % window
        val filled = minOf(seen - warmup, window)
        if (filled >= window / 2 && samples.take(filled).count { it } > slowAllowed) tripped = true
        return !tripped
    }
}

/** Pure geometry for the neuron orb: where each satellite node sits at time t (seconds). */
object NeuronOrb {
    data class Node(val x: Float, val y: Float, val glow: Float)

    /**
     * [count] nodes on slightly different slow orbits around the center, positions in -1..1.
     * Speeds are tiny (one lap ≈ 20–40 s) so a 12 fps redraw still looks smooth.
     */
    fun nodes(tSeconds: Double, count: Int = 6): List<Node> = (0 until count).map { i ->
        val phase = i * (2 * Math.PI / count)
        val speed = 0.16 + 0.05 * (i % 3)                      // rad/s
        val r = 0.62 + 0.14 * Math.sin(tSeconds * 0.35 + i)    // breathing radius
        val a = phase + tSeconds * speed * (if (i % 2 == 0) 1 else -1)
        val glow = (0.55 + 0.45 * Math.sin(tSeconds * 1.1 + i * 1.7)).toFloat()
        Node((r * Math.cos(a)).toFloat(), (r * Math.sin(a) * 0.86).toFloat(), glow)
    }

    /** Core breathing 0..1 (slow, ~6 s period). */
    fun pulse(tSeconds: Double): Float = (0.5 + 0.5 * Math.sin(tSeconds * 1.05)).toFloat()
}

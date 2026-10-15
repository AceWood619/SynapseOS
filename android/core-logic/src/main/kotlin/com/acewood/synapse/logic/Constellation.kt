package com.acewood.synapse.logic

/**
 * The static "neural net" pattern in the Synapse Glass wallpaper (R-124 Part 6). Pure + deterministic
 * so every screen and every boot draws the identical ground, and the app caches one bitmap of it.
 * Coordinates are normalized 0..1 (x across, y down).
 */
object Constellation {
    data class Point(val x: Float, val y: Float, val size: Float)
    data class Edge(val a: Int, val b: Int)

    /** Small deterministic PRNG (no java.util.Random so JVM/Android agree byte-for-byte). */
    private class Lcg(seed: Long) {
        private var s = seed xor 0x5DEECE66DL
        fun next(): Float { s = (s * 6364136223846793005L + 1442695040888963407L); return ((s ushr 40) and 0xFFFFFF).toFloat() / 0x1000000 }
    }

    /**
     * [count] points, denser toward the top glow (y biased by squaring), each linked to up to two
     * nearest neighbours closer than [linkDist] (normalized, measured with y scaled by [aspect]).
     */
    fun build(seed: Long = 0x5A_7A_95EL, count: Int = 46, aspect: Float = 2.2f, linkDist: Float = 0.2f): Pair<List<Point>, List<Edge>> {
        val r = Lcg(seed)
        val pts = List(count) {
            val x = 0.04f + 0.92f * r.next()
            val y = 0.03f + 0.94f * r.next().let { it * it }
            Point(x, y, 0.6f + 1.4f * r.next())
        }
        val edges = LinkedHashSet<Edge>()
        pts.forEachIndexed { i, p ->
            pts.indices.filter { it != i }
                .map { j -> j to dist(p, pts[j], aspect) }
                .filter { it.second < linkDist }
                .sortedBy { it.second }
                .take(2)
                .forEach { (j, _) -> edges += Edge(minOf(i, j), maxOf(i, j)) }
        }
        return pts to edges.toList()
    }

    private fun dist(a: Point, b: Point, aspect: Float): Float {
        val dx = a.x - b.x; val dy = (a.y - b.y) * aspect
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

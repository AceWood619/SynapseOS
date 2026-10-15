package com.acewood.synapse.logic

/**
 * Per-profile tile size (R-124 Part 6). Profiles store `layout.tile_scale`; the guest default is 1.25
 * for bigger thumb targets. Scaling the whole HomeView crops a 360 dp screen, so instead each tile
 * family asks this for its own size, within limits that keep rows fitting at 360 × 800 dp.
 */
data class TileScale(val raw: Float) {
    /** Clamped scale actually applied (0.85..1.4; non-finite values fall back to 1). */
    val scale: Float = if (raw.isFinite()) raw.coerceIn(MIN, MAX) else 1f

    /** Text grows a bit less than boxes so labels don't truncate on bigger tiles. */
    val textScale: Float get() = 1f + (scale - 1f) * 0.6f

    fun box(dp: Float, maxDp: Float = Float.MAX_VALUE): Float = (dp * scale).coerceAtMost(maxDp)
    fun text(sp: Float): Float = sp * textScale

    companion object {
        const val MIN = 0.85f
        const val MAX = 1.4f
        val DEFAULT = TileScale(1f)

        /**
         * Largest square cell that still fits [perRow] per row across [screenDp] with [gutterDp]
         * side padding and [gapDp] between cells — the hard ceiling for dock icons.
         */
        fun maxCellDp(screenDp: Float, perRow: Int, gutterDp: Float = 16f, gapDp: Float = 8f): Float =
            ((screenDp - 2 * gutterDp - (perRow - 1) * gapDp) / perRow).coerceAtLeast(0f)
    }
}

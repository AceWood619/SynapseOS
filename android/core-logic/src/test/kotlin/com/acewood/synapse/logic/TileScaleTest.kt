package com.acewood.synapse.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TileScaleTest {
    @Test fun clampsAndFallsBack() {
        assertEquals(1.4f, TileScale(3f).scale); assertEquals(0.85f, TileScale(0.1f).scale)
        assertEquals(1f, TileScale(Float.NaN).scale); assertEquals(1.25f, TileScale(1.25f).scale)
    }
    @Test fun textGrowsLessThanBoxes() {
        val s = TileScale(1.25f)
        assertEquals(135f, s.box(108f)); assertEquals(11.5f, s.text(10f), 0.001f)
        assertTrue(s.textScale < s.scale)
        assertEquals(120f, s.box(108f, maxDp = 120f))
    }
    @Test fun guestDockStillFitsThreeAcross() {
        val cap = TileScale.maxCellDp(360f, 3)
        assertTrue(TileScale(TileScale.MAX).box(54f, cap) <= cap)
        assertTrue(cap >= 54f)
    }
}

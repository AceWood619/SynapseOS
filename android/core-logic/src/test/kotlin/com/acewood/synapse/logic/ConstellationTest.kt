package com.acewood.synapse.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConstellationTest {
    @Test fun deterministicBoundedAndLinked() {
        val (p1, e1) = Constellation.build()
        val (p2, e2) = Constellation.build()
        assertEquals(p1, p2); assertEquals(e1, e2)
        assertEquals(46, p1.size)
        p1.forEach { assertTrue(it.x in 0f..1f && it.y in 0f..1f && it.size > 0f) }
        assertTrue(e1.isNotEmpty())
        e1.forEach { assertTrue(it.a < it.b && it.b < p1.size) }
        assertTrue(e1.size <= p1.size * 2)
        // Denser at the top (where the ground glow is).
        assertTrue(p1.count { it.y < 0.5f } > p1.count { it.y >= 0.5f })
    }
}

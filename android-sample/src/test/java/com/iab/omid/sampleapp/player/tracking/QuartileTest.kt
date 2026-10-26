package com.iab.omid.sampleapp.player.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class QuartileTest {

    private val duration = 10_000L

    @Test
    fun `GIVEN zero duration WHEN computing quartile THEN UNKNOWN`() {
        assertEquals(Quartile.UNKNOWN, Quartile.from(500, 0))
    }

    @Test
    fun `GIVEN negative duration WHEN computing quartile THEN UNKNOWN`() {
        assertEquals(Quartile.UNKNOWN, Quartile.from(500, -1))
    }

    @Test
    fun `GIVEN position below 1 percent WHEN computing quartile THEN UNKNOWN`() {
        assertEquals(Quartile.UNKNOWN, Quartile.from(0, duration))
        assertEquals(Quartile.UNKNOWN, Quartile.from(99, duration))
    }

    @Test
    fun `GIVEN position at 1 percent WHEN computing quartile THEN START`() {
        assertEquals(Quartile.START, Quartile.from(100, duration))
        assertEquals(Quartile.START, Quartile.from(2_499, duration))
    }

    @Test
    fun `GIVEN position at 25 percent WHEN computing quartile THEN FIRST`() {
        assertEquals(Quartile.FIRST, Quartile.from(2_500, duration))
        assertEquals(Quartile.FIRST, Quartile.from(4_999, duration))
    }

    @Test
    fun `GIVEN position at 50 percent WHEN computing quartile THEN SECOND`() {
        assertEquals(Quartile.SECOND, Quartile.from(5_000, duration))
        assertEquals(Quartile.SECOND, Quartile.from(7_499, duration))
    }

    @Test
    fun `GIVEN position at 75 percent WHEN computing quartile THEN THIRD`() {
        assertEquals(Quartile.THIRD, Quartile.from(7_500, duration))
        assertEquals(Quartile.THIRD, Quartile.from(9_999, duration))
    }

    @Test
    fun `GIVEN position at or beyond duration WHEN computing quartile THEN COMPLETE`() {
        assertEquals(Quartile.COMPLETE, Quartile.from(10_000, duration))
        assertEquals(Quartile.COMPLETE, Quartile.from(12_000, duration))
    }

    @Test
    fun `GIVEN enum WHEN comparing ordinals THEN progression order is preserved`() {
        val order = listOf(Quartile.UNKNOWN, Quartile.START, Quartile.FIRST, Quartile.SECOND, Quartile.THIRD, Quartile.COMPLETE)
        assertEquals(order, Quartile.values().toList())
    }
}

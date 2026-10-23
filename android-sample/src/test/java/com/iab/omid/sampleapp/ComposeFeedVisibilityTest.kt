package com.iab.omid.sampleapp

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposeFeedVisibilityTest {

    @Test
    fun fullyVisible_is100() {
        assertEquals(100f, calculateVisiblePercent(100, 300, 0, 1000), 0.01f)
    }

    @Test
    fun partiallyObscuredAtTop_usesViewportNotZero() {
        // Item spans -150..50 → only 50 of 200 px visible
        assertEquals(25f, calculateVisiblePercent(-150, 50, 0, 1000), 0.01f)
    }

    @Test
    fun partiallyObscuredAtBottom_over50_isCounted() {
        // Item spans 900..1100, viewport ends at 1000 → 50%
        assertEquals(50f, calculateVisiblePercent(900, 1100, 0, 1000), 0.01f)
        assertEquals(75f, calculateVisiblePercent(850, 1050, 0, 1000), 0.01f)
    }

    @Test
    fun offscreen_is0() {
        assertEquals(0f, calculateVisiblePercent(1000, 1200, 0, 1000), 0.01f)
        assertEquals(0f, calculateVisiblePercent(-300, -100, 0, 1000), 0.01f)
    }

    @Test
    fun zeroSize_is0() {
        assertEquals(0f, calculateVisiblePercent(100, 100, 0, 1000), 0.01f)
    }
}

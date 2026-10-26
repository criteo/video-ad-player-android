package com.iab.omid.sampleapp.scenario

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Spec: quartile tracking beacons and indefinite repeat. */
@RunWith(AndroidJUnit4::class)
class QuartileScenarioTest : ScenarioBase() {

    @Test
    fun quartile_beacons_fire_once_each_in_order_and_video_keeps_looping() {
        val activity = launchBasic()
        awaitPlaying(activity)

        assertTrue("complete; beacons=${server.names()}", server.awaitBeacon("complete", timeoutMs = 120_000))

        val order = server.names().filter { it in listOf("start", "firstQuartile", "midpoint", "thirdQuartile", "complete") }
        assertEquals(listOf("start", "firstQuartile", "midpoint", "thirdQuartile", "complete"), order)

        // Repeat indefinitely: still playing after reaching the end.
        assertTrue("keeps playing after complete", waitUntil(10_000) { PlayerRobot.isPlaying(activity) })
        assertEquals("impression fired once", 1, server.count("impression"))
    }
}

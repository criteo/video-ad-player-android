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

        assertTrue("complete; beacons=${server.names()}", server.hasReceivedBeacon(BeaconEvent.COMPLETE, timeoutMs = ScenarioTimeouts.VIDEO_COMPLETE_MS))

        val order = server.names().filter { it in listOf(BeaconEvent.START, BeaconEvent.FIRST_QUARTILE, BeaconEvent.MIDPOINT, BeaconEvent.THIRD_QUARTILE, BeaconEvent.COMPLETE) }
        assertEquals("quartile beacons must arrive exactly once in playback order", listOf(BeaconEvent.START, BeaconEvent.FIRST_QUARTILE, BeaconEvent.MIDPOINT, BeaconEvent.THIRD_QUARTILE, BeaconEvent.COMPLETE), order)

        // Repeat indefinitely: still playing after reaching the end.
        assertTrue("keeps playing after complete", waitUntil(ScenarioTimeouts.STATE_CHANGE_MS) { PlayerRobot.isPlaying(activity) })
        assertEquals("impression fired once", 1, server.countReceivedBeacon(BeaconEvent.IMPRESSION))
    }
}

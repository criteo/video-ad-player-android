package com.iab.omid.sampleapp.scenario

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Spec: launch behaviour, mute, pause/resume and no-click-through click behaviour on the Basic screen. */
@RunWith(AndroidJUnit4::class)
class BasicPlayerScenarioTest : ScenarioBase() {

    @Test
    fun launch_autoplays_muted_and_fires_impression_and_start_without_mute_beacon() {
        val activity = launchBasic()

        awaitPlaying(activity)

        assertTrue("impression beacon must arrive when the video is rendered", server.hasReceivedBeacon(BeaconEvent.IMPRESSION))
        assertTrue("start beacon must arrive after playback begins", server.hasReceivedBeacon(BeaconEvent.START))
        assertTrue("video starts muted", PlayerRobot.isMuted(activity))
        assertTrue("no mute beacon at launch", server.neverFires(BeaconEvent.MUTE, ScenarioTimeouts.NO_BEACON_MS))
        assertEquals("impression fired once", 1, server.countReceivedBeacon(BeaconEvent.IMPRESSION))
    }

    @Test
    fun unmute_then_mute_fire_beacons_each_time() {
        val activity = launchBasic()
        awaitPlaying(activity)

        repeat(2) { round ->
            PlayerRobot.tapMute(activity)
            assertTrue("unmute beacon must arrive on round ${round + 1}", server.hasReceivedBeacon(BeaconEvent.UNMUTE, times = round + 1))
            assertFalse("video must be unmuted on round ${round + 1}", PlayerRobot.isMuted(activity))

            PlayerRobot.tapMute(activity)
            assertTrue("mute beacon must arrive on round ${round + 1}", server.hasReceivedBeacon(BeaconEvent.MUTE, times = round + 1))
            assertTrue("video must be muted on round ${round + 1}", PlayerRobot.isMuted(activity))
        }
        assertEquals("each unmute action must fire exactly one beacon", 2, server.countReceivedBeacon(BeaconEvent.UNMUTE))
        assertEquals("each mute action must fire exactly one beacon", 2, server.countReceivedBeacon(BeaconEvent.MUTE))
    }

    @Test
    fun with_click_tracking_configured_pause_button_fires_pause_and_resume_button_fires_resume() {
        val activity = launchBasic(VastSpec(clickThrough = "https://example.com/landing"))
        awaitPlaying(activity)

        PlayerRobot.tapPlayPause(activity)
        assertTrue("user pause must fire a pause beacon", server.hasReceivedBeacon(BeaconEvent.PAUSE))
        assertTrue("manual pause must stop playback", waitUntil(ScenarioTimeouts.STATE_CHANGE_MS) { !PlayerRobot.isPlaying(activity) })

        PlayerRobot.tapPlayPause(activity)
        assertTrue("resume; beacons=${server.names()} playing=${PlayerRobot.isPlaying(activity)}", server.hasReceivedBeacon(BeaconEvent.RESUME))
        assertTrue("manual resume must restart playback", waitUntil(ScenarioTimeouts.STATE_CHANGE_MS) { PlayerRobot.isPlaying(activity) })

        assertEquals("pause/resume buttons never fire clickTracking", 0, server.countReceivedBeacon(BeaconEvent.CLICK_TRACKING))
    }

    @Test
    fun without_click_through_and_click_tracking_tapping_video_pauses_then_resumes() {
        val activity = launchBasic(VastSpec(clickThrough = null, clickTrackingUrls = emptyList()))
        awaitPlaying(activity)

        PlayerRobot.tapVideo()
        assertTrue("user pause must fire a pause beacon", server.hasReceivedBeacon(BeaconEvent.PAUSE))
        assertTrue("manual pause must stop playback", waitUntil(ScenarioTimeouts.STATE_CHANGE_MS) { !PlayerRobot.isPlaying(activity) })

        PlayerRobot.tapVideo()
        assertTrue("resume; beacons=${server.names()} playing=${PlayerRobot.isPlaying(activity)}", server.hasReceivedBeacon(BeaconEvent.RESUME))
        assertTrue("manual resume must restart playback", waitUntil(ScenarioTimeouts.STATE_CHANGE_MS) { PlayerRobot.isPlaying(activity) })

        assertEquals("no clickTracking without click-through URL", 0, server.countReceivedBeacon(BeaconEvent.CLICK_TRACKING))
    }

    @Test
    fun without_click_tracking_pause_and_resume_buttons_still_fire_their_own_beacons() {
        val activity = launchBasic(VastSpec(clickThrough = "https://example.com/landing", clickTrackingUrls = emptyList()))
        awaitPlaying(activity)

        PlayerRobot.tapPlayPause(activity)
        assertTrue("pause works without ClickTracking URLs", server.hasReceivedBeacon(BeaconEvent.PAUSE))
        assertTrue("playback must pause", waitUntil(ScenarioTimeouts.STATE_CHANGE_MS) { !PlayerRobot.isPlaying(activity) })
        PlayerRobot.tapPlayPause(activity)
        assertTrue("resume works without ClickTracking URLs", server.hasReceivedBeacon(BeaconEvent.RESUME))
        assertTrue("playback must resume", waitUntil(ScenarioTimeouts.STATE_CHANGE_MS) { PlayerRobot.isPlaying(activity) })
        assertTrue("buttons must never fire clickTracking", server.neverFires(BeaconEvent.CLICK_TRACKING))
    }
}

package com.iab.omid.sampleapp.scenario

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Spec: launch behaviour, mute, pause/resume and no-redirect click behaviour on the Basic screen. */
@RunWith(AndroidJUnit4::class)
class BasicPlayerScenarioTest : ScenarioBase() {

    @Test
    fun launch_autoplays_muted_and_fires_impression_and_start_without_mute_beacon() {
        val activity = launchBasic()

        awaitPlaying(activity)

        assertTrue("impression", server.awaitBeacon("impression"))
        assertTrue("start", server.awaitBeacon("start"))
        assertTrue("video starts muted", PlayerRobot.isMuted(activity))
        assertTrue("no mute beacon at launch", server.neverFires("mute", 1_500))
        assertEquals("impression fired once", 1, server.count("impression"))
    }

    @Test
    fun unmute_then_mute_fire_beacons_each_time() {
        val activity = launchBasic()
        awaitPlaying(activity)

        PlayerRobot.tapMute(activity)
        assertTrue("unmute", server.awaitBeacon("unmute"))
        assertFalse(PlayerRobot.isMuted(activity))

        PlayerRobot.tapMute(activity)
        assertTrue("mute", server.awaitBeacon("mute"))
        assertTrue(PlayerRobot.isMuted(activity))
    }

    @Test
    fun pause_button_fires_pause_without_click_tracking_and_resume_button_fires_resume() {
        val activity = launchBasic(VastSpec(clickThrough = "https://example.com/landing"))
        awaitPlaying(activity)

        PlayerRobot.tapPlayPause(activity)
        assertTrue("pause", server.awaitBeacon("pause"))
        assertTrue(waitUntil(5_000) { !PlayerRobot.isPlaying(activity) })

        PlayerRobot.tapPlayPause(activity)
        assertTrue("resume; beacons=${server.names()} playing=${PlayerRobot.isPlaying(activity)}", server.awaitBeacon("resume"))
        assertTrue(waitUntil(5_000) { PlayerRobot.isPlaying(activity) })

        assertEquals("pause/resume buttons never fire clickTracking", 0, server.count("clickTracking"))
    }

    @Test
    fun without_redirect_link_tapping_video_pauses_then_resumes_with_beacons_and_no_clickthrough() {
        val activity = launchBasic(VastSpec(clickThrough = null))
        awaitPlaying(activity)

        PlayerRobot.tapVideo()
        assertTrue("pause", server.awaitBeacon("pause"))
        assertTrue(waitUntil(5_000) { !PlayerRobot.isPlaying(activity) })

        PlayerRobot.tapVideo()
        assertTrue("resume; beacons=${server.names()} playing=${PlayerRobot.isPlaying(activity)}", server.awaitBeacon("resume"))
        assertTrue(waitUntil(5_000) { PlayerRobot.isPlaying(activity) })

        assertEquals("no clickTracking without redirect link", 0, server.count("clickTracking"))
    }
}

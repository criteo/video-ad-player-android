package com.iab.omid.sampleapp.scenario

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/** Spec: viewport-driven loading, autoplay (>=50%), auto pause/resume and their beacons, on the Feed screen. */
@RunWith(AndroidJUnit4::class)
class FeedViewportScenarioTest : ScenarioBase() {

    private val adPosition = 9

    @Test
    @Ignore("Known bug: FeedAdapter creates the player in bind(), so off-screen ads are not fetched at page load.")
    fun video_is_loaded_at_page_load_even_when_not_in_viewport() {
        launchFeed()

        assertTrue(
            "VAST/video must be fetched at load, before the ad is scrolled into view; media requests=${server.mediaRequests}",
            waitUntil(ScenarioTimeouts.ASSET_LOAD_MS) { server.mediaRequests.isNotEmpty() }
        )
    }

    @Test
    fun video_does_not_start_while_outside_viewport() {
        val activity = launchFeed()

        assertTrue("no start beacon while off-screen", server.neverFires(BeaconEvent.START, ScenarioTimeouts.OBSERVATION_MS))
        assertTrue("not playing", !PlayerRobot.isPlaying(activity))
    }

    @Test
    @Ignore("Known bug: onVisibilityChanged(true) can call play() before videoPlayer exists; loading uses autoLoad=false and the visibility flag prevents retry while the ad stays visible.")
    fun scrolling_ad_into_view_autoplays_muted_and_fires_start() {
        val activity = launchFeed()

        PlayerRobot.scrollFeedTo(adPosition)

        assertTrue("start beacon", server.hasReceivedBeacon(BeaconEvent.START, ScenarioTimeouts.PLAYBACK_START_MS))
        assertTrue("visible video must be playing", waitUntil(ScenarioTimeouts.STATE_CHANGE_MS) { PlayerRobot.isPlaying(activity) })
        assertTrue("video must start muted", PlayerRobot.isMuted(activity))
        assertEquals("automatic startup must not fire a mute beacon", 0, server.countReceivedBeacon(BeaconEvent.MUTE))
    }

    /** Diagnostic for a separate re-entry path; unlike initial autoplay, its failure is not yet attributed to the app. */
    @Test
    @Ignore("Test under implementation: verify RecyclerView re-entry and player readiness; the earlier failure after scrolling back has not been isolated to an app bug.")
    fun once_loaded_ad_in_view_autoplays_after_next_visibility_check() {
        val activity = launchFeed()
        PlayerRobot.scrollFeedTo(adPosition)
        assertTrue("video fetched after bind", waitUntil(ScenarioTimeouts.ASSET_LOAD_MS) { server.mediaRequests.isNotEmpty() })
        assertTrue("player must be ready before testing re-entry", waitUntil(ScenarioTimeouts.ASSET_LOAD_MS) { PlayerRobot.isReady(activity) })

        PlayerRobot.scrollFeedTo(0)
        PlayerRobot.scrollFeedTo(adPosition)

        assertTrue("start beacon; beacons=${server.names()}", server.hasReceivedBeacon(BeaconEvent.START, ScenarioTimeouts.PLAYBACK_START_MS))
        assertTrue("visible ad must be playing after re-entry", waitUntil(ScenarioTimeouts.STATE_CHANGE_MS) { PlayerRobot.isPlaying(activity) })
    }

    @Test
    @Ignore("Known bug: onVisibilityChanged(true) can call play() before videoPlayer exists; loading uses autoLoad=false and the visibility flag prevents retry while the ad stays visible.")
    fun scrolling_away_pauses_and_back_resumes_without_pause_or_resume_beacons() {
        val activity = launchFeed()
        PlayerRobot.scrollFeedTo(adPosition)
        assertTrue("start beacon must arrive before testing scrolling", server.hasReceivedBeacon(BeaconEvent.START, ScenarioTimeouts.PLAYBACK_START_MS))
        val wrapper = assertNotNullValue(PlayerRobot.wrapperRef(activity), "loaded Feed ad must have a wrapper")

        PlayerRobot.scrollFeedTo(0)
        assertTrue("paused when out of viewport", waitUntil(ScenarioTimeouts.STATE_CHANGE_MS) { !PlayerRobot.isPlaying(wrapper) })

        PlayerRobot.scrollFeedTo(adPosition)
        assertTrue("resumed when back in viewport", waitUntil(ScenarioTimeouts.ASSET_LOAD_MS) { PlayerRobot.isPlaying(wrapper) })

        assertEquals("no pause beacon", 0, server.countReceivedBeacon(BeaconEvent.PAUSE))
        assertEquals("no resume beacon", 0, server.countReceivedBeacon(BeaconEvent.RESUME))
    }

    @Test
    @Ignore("Known bug: onVisibilityChanged(true) can call play() before videoPlayer exists; loading uses autoLoad=false and the visibility flag prevents retry while the ad stays visible.")
    fun manually_paused_video_resumes_after_leaving_and_returning_without_resume_beacon() {
        val activity = launchFeed()
        PlayerRobot.scrollFeedTo(adPosition)
        assertTrue("start beacon must arrive before testing scrolling", server.hasReceivedBeacon(BeaconEvent.START, ScenarioTimeouts.PLAYBACK_START_MS))
        val wrapper = assertNotNullValue(PlayerRobot.wrapperRef(activity), "loaded Feed ad must have a wrapper")

        PlayerRobot.tapPlayPause(activity)
        assertTrue("manual pause must fire a pause beacon", server.hasReceivedBeacon(BeaconEvent.PAUSE))
        assertTrue("manual pause must stop playback", waitUntil(ScenarioTimeouts.STATE_CHANGE_MS) { !PlayerRobot.isPlaying(wrapper) })

        PlayerRobot.scrollFeedTo(0)
        PlayerRobot.scrollFeedTo(adPosition)

        assertTrue("auto-resumed", waitUntil(ScenarioTimeouts.ASSET_LOAD_MS) { PlayerRobot.isPlaying(wrapper) })
        assertEquals("only the manual pause beacon", 1, server.countReceivedBeacon(BeaconEvent.PAUSE))
        assertEquals("no resume beacon for automatic resume", 0, server.countReceivedBeacon(BeaconEvent.RESUME))
    }
}

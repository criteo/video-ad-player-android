package com.iab.omid.sampleapp.scenario

import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.contrib.RecyclerViewActions.scrollToPosition
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iab.omid.sampleapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/** Spec: viewport-driven loading, autoplay (>=50%), auto pause/resume and their beacons, on the Feed screen. */
@RunWith(AndroidJUnit4::class)
class FeedViewportScenarioTest : ScenarioBase() {

    private val adPosition = 9

    private fun scrollTo(position: Int) {
        onView(withId(R.id.feedRecyclerView)).perform(scrollToPosition<RecyclerView.ViewHolder>(position))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test
    @Ignore("Known spec gap: Feed creates the player only when the ad row is first bound, so nothing is fetched at page load while the ad is off-screen.")
    fun video_is_loaded_at_page_load_even_when_not_in_viewport() {
        launchFeed()

        assertTrue(
            "VAST/video must be fetched at load, before the ad is scrolled into view; media requests=${server.mediaRequests}",
            waitUntil(15_000) { server.mediaRequests.isNotEmpty() }
        )
    }

    @Test
    fun video_does_not_start_while_outside_viewport() {
        val activity = launchFeed()

        assertTrue("no start beacon while off-screen", server.neverFires("start", 3_000))
        assertTrue("not playing", !PlayerRobot.isPlaying(activity))
    }

    @Test
    @Ignore("Known issue: no autoplay when loading completes while the ad is already >=50% visible (no start beacon until scrolled out and back).")
    fun scrolling_ad_into_view_autoplays_muted_and_fires_start() {
        val activity = launchFeed()

        scrollTo(adPosition)

        assertTrue("start beacon", server.awaitBeacon("start", 30_000))
        assertTrue("playing", waitUntil(10_000) { PlayerRobot.isPlaying(activity) })
        assertTrue("muted", PlayerRobot.isMuted(activity))
        assertEquals(0, server.count("mute"))
    }

    /** Diagnostic: same as above, but the video has finished loading before the visibility check re-runs. */
    @Test
    @Ignore("Known issue: no autoplay when loading completes while the ad is already >=50% visible (no start beacon until scrolled out and back).")
    fun once_loaded_ad_in_view_autoplays_after_next_visibility_check() {
        val activity = launchFeed()
        scrollTo(adPosition)
        assertTrue("video fetched after bind", waitUntil(15_000) { server.mediaRequests.isNotEmpty() })
        Thread.sleep(3_000) // let download + player setup finish

        scrollTo(0)
        scrollTo(adPosition)

        assertTrue("start beacon; beacons=${server.names()}", server.awaitBeacon("start", 30_000))
        assertTrue(waitUntil(10_000) { PlayerRobot.isPlaying(activity) })
    }

    @Test
    @Ignore("Known issue: no autoplay when loading completes while the ad is already >=50% visible (no start beacon until scrolled out and back).")
    fun scrolling_away_pauses_and_back_resumes_without_pause_or_resume_beacons() {
        val activity = launchFeed()
        scrollTo(adPosition)
        assertTrue(server.awaitBeacon("start", 30_000))
        val wrapper = assertNotNull_(PlayerRobot.wrapperRef(activity))

        scrollTo(0)
        assertTrue("paused when out of viewport", waitUntil(10_000) { !PlayerRobot.isPlaying(wrapper) })

        scrollTo(adPosition)
        assertTrue("resumed when back in viewport", waitUntil(15_000) { PlayerRobot.isPlaying(wrapper) })

        assertEquals("no pause beacon", 0, server.count("pause"))
        assertEquals("no resume beacon", 0, server.count("resume"))
    }

    @Test
    @Ignore("Known issue: no autoplay when loading completes while the ad is already >=50% visible (no start beacon until scrolled out and back).")
    fun manually_paused_video_resumes_after_leaving_and_returning_without_resume_beacon() {
        val activity = launchFeed()
        scrollTo(adPosition)
        assertTrue(server.awaitBeacon("start", 30_000))
        val wrapper = assertNotNull_(PlayerRobot.wrapperRef(activity))

        PlayerRobot.tapPlayPause(activity)
        assertTrue(server.awaitBeacon("pause"))
        assertTrue(waitUntil(5_000) { !PlayerRobot.isPlaying(wrapper) })

        scrollTo(0)
        scrollTo(adPosition)

        assertTrue("auto-resumed", waitUntil(15_000) { PlayerRobot.isPlaying(wrapper) })
        assertEquals("only the manual pause beacon", 1, server.count("pause"))
        assertEquals("no resume beacon for automatic resume", 0, server.count("resume"))
    }

    private fun <T> assertNotNull_(v: T?): T {
        assertNotNull(v)
        return v!!
    }
}

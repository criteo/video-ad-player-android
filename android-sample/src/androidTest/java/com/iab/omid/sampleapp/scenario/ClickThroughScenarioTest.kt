package com.iab.omid.sampleapp.scenario

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.VerificationModes.times
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Spec: click-through URL behaviour. */
@RunWith(AndroidJUnit4::class)
class ClickThroughScenarioTest : ScenarioBase() {

    private val landing = "https://example.com/landing"

    @Before
    fun initIntents() {
        Intents.init()
        intending(hasAction(Intent.ACTION_VIEW)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun releaseIntents() = Intents.release()

    @Test
    fun with_click_through_tap_opens_clickthrough_fires_click_tracking_and_not_pause() {
        val activity = launchBasic(VastSpec(clickThrough = landing))
        awaitPlaying(activity)

        PlayerRobot.tapVideo()

        assertTrue("video tap must fire clickTracking", server.hasReceivedBeacon(BeaconEvent.CLICK_TRACKING))
        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(landing)))
        assertTrue("pause must NOT fire", server.neverFires(BeaconEvent.PAUSE, ScenarioTimeouts.NO_BEACON_MS))
    }

    @Test
    fun with_click_through_every_tap_fires_click_tracking_again() {
        val activity = launchBasic(VastSpec(clickThrough = landing))
        awaitPlaying(activity)

        PlayerRobot.tapVideo()
        assertTrue("first tap must fire clickTracking", server.hasReceivedBeacon(BeaconEvent.CLICK_TRACKING, times = 1))
        PlayerRobot.tapVideo()

        assertTrue("second click tracked", server.hasReceivedBeacon(BeaconEvent.CLICK_TRACKING, times = 2))
        assertEquals("video taps with a click-through URL must not fire pause", 0, server.countReceivedBeacon(BeaconEvent.PAUSE))
    }

    @Test
    fun without_click_through_tap_never_opens_a_page() {
        val activity = launchBasic(VastSpec(clickThrough = null))
        awaitPlaying(activity)

        PlayerRobot.tapVideo()
        assertTrue("tap without click-through must fire pause", server.hasReceivedBeacon(BeaconEvent.PAUSE))

        assertEquals("clickTracking must not fire without a click-through URL", 0, server.countReceivedBeacon(BeaconEvent.CLICK_TRACKING))
        intended(hasAction(Intent.ACTION_VIEW), times(0))
    }

    @Test
    fun with_click_through_without_click_tracking_tap_still_opens_landing_page() {
        val activity = launchBasic(VastSpec(clickThrough = landing, clickTrackingUrls = emptyList()))
        awaitPlaying(activity)

        PlayerRobot.tapVideo()

        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(landing)))
        assertTrue("no clickTracking is sent when none is configured", server.neverFires(BeaconEvent.CLICK_TRACKING))
        assertTrue("click-through must not fire pause", server.neverFires(BeaconEvent.PAUSE))
    }
}

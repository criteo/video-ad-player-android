package com.iab.omid.sampleapp.scenario

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Spec: redirection link behaviour. */
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
    fun with_redirect_link_tap_opens_clickthrough_fires_click_tracking_and_not_pause() {
        val activity = launchBasic(VastSpec(clickThrough = landing))
        awaitPlaying(activity)

        PlayerRobot.tapVideo()

        assertTrue("clickTracking", server.awaitBeacon("clickTracking"))
        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(landing)))
        assertTrue("pause must NOT fire", server.neverFires("pause", 1_500))
    }

    @Test
    fun with_redirect_link_every_tap_fires_click_tracking_again() {
        val activity = launchBasic(VastSpec(clickThrough = landing))
        awaitPlaying(activity)

        PlayerRobot.tapVideo()
        assertTrue(server.awaitBeacon("clickTracking", times = 1))
        PlayerRobot.tapVideo()

        assertTrue("second click tracked", server.awaitBeacon("clickTracking", times = 2))
        assertEquals(0, server.count("pause"))
    }

    @Test
    fun without_redirect_link_tap_never_opens_a_page() {
        val activity = launchBasic(VastSpec(clickThrough = null))
        awaitPlaying(activity)

        PlayerRobot.tapVideo()
        assertTrue(server.awaitBeacon("pause"))

        assertEquals(0, server.count("clickTracking"))
        // No VIEW intent was launched
        try {
            intended(hasAction(Intent.ACTION_VIEW))
            throw AssertionError("a VIEW intent was fired without a redirect link")
        } catch (expected: AssertionError) {
            if (expected.message?.startsWith("a VIEW") == true) throw expected
        }
    }
}

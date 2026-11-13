package com.iab.omid.sampleapp.scenario

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Spec: app switching (pause when backgrounded, resume with state preserved, no extra beacons). */
@RunWith(AndroidJUnit4::class)
class AppSwitchingScenarioTest : ScenarioBase() {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun backgroundApp() {
        UiDevice.getInstance(instrumentation).pressHome()
    }

    private fun foregroundApp() {
        val ctx = instrumentation.targetContext
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        ctx.startActivity(intent)
    }

    @Test
    fun going_to_background_pauses_without_pause_beacon_and_return_resumes_without_resume_beacon() {
        val activity = launchBasic()
        awaitPlaying(activity)

        backgroundApp()
        assertTrue("paused in background", waitUntil(ScenarioTimeouts.STATE_CHANGE_MS) { !PlayerRobot.isPlaying(activity) })
        assertTrue("no pause beacon for system pause", server.neverFires(BeaconEvent.PAUSE, ScenarioTimeouts.NO_BEACON_MS))

        foregroundApp()
        assertTrue("resumed on return", waitUntil(ScenarioTimeouts.APP_SWITCH_MS) { PlayerRobot.isPlaying(activity) })
        assertEquals("no resume beacon for automatic resume", 0, server.countReceivedBeacon(BeaconEvent.RESUME))
    }

    @Test
    fun mute_state_and_position_are_preserved_across_app_switch() {
        val activity = launchBasic()
        awaitPlaying(activity)
        PlayerRobot.tapMute(activity)
        assertTrue("user unmute must fire an unmute beacon", server.hasReceivedBeacon(BeaconEvent.UNMUTE))
        assertFalse("video must be unmuted before switching apps", PlayerRobot.isMuted(activity))
        val before = PlayerRobot.positionMs(activity)

        backgroundApp()
        assertTrue("video must be paused", waitUntil(ScenarioTimeouts.STATE_CHANGE_MS) { !PlayerRobot.isPlaying(activity) })
        val whilePaused = PlayerRobot.positionMs(activity)
        foregroundApp()
        assertTrue("video must resume after returning to the app", waitUntil(ScenarioTimeouts.APP_SWITCH_MS) { PlayerRobot.isPlaying(activity) })

        assertFalse("still unmuted after return", PlayerRobot.isMuted(activity))
        assertTrue("position kept while paused (was $before, paused at $whilePaused)", whilePaused >= before)
    }

    /** PRODUCT-34910: a manual pause must survive leaving the app and coming back. */
    @Test
    fun manually_paused_video_stays_paused_after_app_switch() {
        val activity = launchBasic()
        awaitPlaying(activity)
        PlayerRobot.tapPlayPause(activity)
        assertTrue("user pause must fire a pause beacon", server.hasReceivedBeacon(BeaconEvent.PAUSE))
        assertTrue("video must be paused", waitUntil(ScenarioTimeouts.STATE_CHANGE_MS) { !PlayerRobot.isPlaying(activity) })

        backgroundApp()
        foregroundApp()

        assertTrue("must stay paused after returning", waitUntil(ScenarioTimeouts.OBSERVATION_MS) { PlayerRobot.isPlaying(activity) }.not())
        assertEquals("no resume beacon", 0, server.countReceivedBeacon(BeaconEvent.RESUME))
        assertEquals("only the manual pause beacon", 1, server.countReceivedBeacon(BeaconEvent.PAUSE))
    }
}

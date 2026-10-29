package com.iab.omid.sampleapp.scenario

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.iab.omid.sampleapp.MainActivity
import org.junit.After
import org.junit.Before

abstract class ScenarioBase {

    protected lateinit var server: ScenarioServer
    protected var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun startServer() {
        server = ScenarioServer().also { it.start() }
    }

    @After
    fun tearDown() {
        scenario?.close()
        server.shutdown()
    }

    protected fun launchBasic(spec: VastSpec = VastSpec()): Activity = launch(spec) { it.showBasicVideoPlayerScreen(server.vastUrl) }

    protected fun launchFeed(spec: VastSpec = VastSpec()): Activity = launch(spec) { it.showFeedVideoPlayerScreen(server.vastUrl) }

    private fun launch(spec: VastSpec, show: (MainActivity) -> Unit): Activity {
        server.vast = spec
        val s = ActivityScenario.launch(MainActivity::class.java)
        scenario = s
        var activity: Activity? = null
        s.onActivity { act ->
            show(act)
            activity = act
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        return activity!!
    }

    /** Waits until playback is running and the `start` beacon (>=1% played) has fired. */
    protected fun awaitPlaying(activity: Activity, timeoutMs: Long = 30_000) {
        check(waitUntil(timeoutMs) { PlayerRobot.isPlaying(activity) }) { "Video never started playing. Beacons: ${server.names()}" }
        check(server.awaitBeacon("start", timeoutMs)) { "start beacon never fired. Beacons: ${server.names()}" }
    }
}

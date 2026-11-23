package com.iab.omid.sampleapp

import android.widget.RadioButton
import android.view.View
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class ExampleSelectorFragmentTest {

    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var activity: MainActivity
    private lateinit var root: View

    private fun start() {
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activity = controller.get()
        idleMainLooper()
        root = activity.currentFragment().requireView()
    }

    @After
    fun tearDown() {
        runCatching { controller.pause().stop().destroy() }
    }

    private fun select(id: Int) {
        root.findViewById<RadioButton>(id).isChecked = true
    }

    private fun tapBasicAndGetUrl(): String? {
        root.findViewById<View>(R.id.basicVideoPlayerItem).performClick()
        idleMainLooper()
        return activity.currentFragment().arguments?.getString("vast_url")
    }

    @Test
    fun `GIVEN selector shown WHEN view created THEN toolbar title is set`() {
        start()
        assertEquals("Video Ad Examples", activity.supportActionBar?.title)
    }

    @Test
    fun `GIVEN single media selected WHEN tapping basic item THEN basic player opens with that url`() {
        start()
        select(R.id.vastUrlSingleMedia)
        val url = tapBasicAndGetUrl()
        assertTrue(activity.currentFragment() is BasicVideoPlayerFragment)
        assertEquals(MainActivity.VAST_DEMO_URL_SINGLE_MEDIA, url)
    }

    @Test
    fun `GIVEN multiple media selected WHEN tapping basic item THEN url matches`() {
        start()
        select(R.id.vastUrlMultipleMedia)
        assertEquals(MainActivity.VAST_DEMO_URL_MULTIPLE_MEDIA, tapBasicAndGetUrl())
    }

    @Test
    fun `GIVEN no captions selected WHEN tapping basic item THEN url matches`() {
        start()
        select(R.id.vastUrlNoClosedCaptions)
        assertEquals(MainActivity.VAST_DEMO_URL_NO_CAPTIONS, tapBasicAndGetUrl())
    }

    @Test
    fun `GIVEN click through selected WHEN tapping basic item THEN url matches`() {
        start()
        select(R.id.vastUrlWithClickThrough)
        assertEquals(MainActivity.VAST_DEMO_URL_CLICK_THROUGH, tapBasicAndGetUrl())
    }

    @Test
    fun `GIVEN no selection WHEN tapping basic item THEN falls back to single media url`() {
        start()
        root.findViewById<android.widget.RadioGroup>(R.id.vastUrlRadioGroup).clearCheck()
        assertEquals(MainActivity.VAST_DEMO_URL_SINGLE_MEDIA, tapBasicAndGetUrl())
    }

    @Test
    fun `GIVEN click through selected WHEN tapping feed item THEN feed player opens with that url`() {
        start()
        select(R.id.vastUrlWithClickThrough)
        root.findViewById<View>(R.id.feedVideoPlayerItem).performClick()
        idleMainLooper()
        assertTrue(activity.currentFragment() is FeedVideoPlayerFragment)
        assertEquals(
            MainActivity.VAST_DEMO_URL_CLICK_THROUGH,
            activity.currentFragment().arguments?.getString("vast_url")
        )
    }
}

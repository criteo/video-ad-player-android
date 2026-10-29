package com.iab.omid.sampleapp

import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

// Unroutable local address: keeps the embedded video wrappers from reaching the internet.
internal const val UNREACHABLE_VAST_URL = "http://127.0.0.1:1/vast.xml"

internal fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

internal fun AppCompatActivity.currentFragment(): Fragment =
    supportFragmentManager.findFragmentById(R.id.mainFragmentContainer)!!

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    private var controller: ActivityController<MainActivity>? = null

    private fun launch(savedState: Bundle? = null): MainActivity {
        val c = Robolectric.buildActivity(MainActivity::class.java)
        controller = c
        return if (savedState == null) c.setup().get() else c.create(savedState).start().resume().get()
    }

    @After
    fun tearDown() {
        controller?.let { runCatching { it.pause().stop().destroy() } }
        controller = null
    }

    @Test
    fun `GIVEN fresh start WHEN activity created THEN selector fragment is shown`() {
        val activity = launch()
        idleMainLooper()
        assertTrue(activity.currentFragment() is ExampleSelectorFragment)
    }

    @Test
    fun `GIVEN fresh start WHEN activity created THEN toolbar is set as action bar`() {
        val activity = launch()
        assertNotNull(activity.supportActionBar)
    }

    @Test
    fun `GIVEN saved state WHEN activity created THEN no fragment is added by the activity`() {
        val activity = launch(savedState = Bundle())
        idleMainLooper()
        assertEquals(0, activity.supportFragmentManager.fragments.size)
    }

    @Test
    fun `GIVEN vast url WHEN showing basic player THEN basic fragment is shown with url argument`() {
        val activity = launch()
        activity.showBasicVideoPlayerScreen(UNREACHABLE_VAST_URL)
        idleMainLooper()
        val fragment = activity.currentFragment()
        assertTrue(fragment is BasicVideoPlayerFragment)
        assertEquals(UNREACHABLE_VAST_URL, fragment.arguments?.getString("vast_url"))
        assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
    }

    @Test
    fun `GIVEN vast url WHEN showing feed player THEN feed fragment is shown with url argument`() {
        val activity = launch()
        activity.showFeedVideoPlayerScreen(UNREACHABLE_VAST_URL)
        idleMainLooper()
        val fragment = activity.currentFragment()
        assertTrue(fragment is FeedVideoPlayerFragment)
        assertEquals(UNREACHABLE_VAST_URL, fragment.arguments?.getString("vast_url"))
        assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
    }

    @Test
    fun `GIVEN back stack entry WHEN fragment is pushed THEN up navigation is enabled`() {
        val activity = launch()
        assertFalse(activity.supportActionBar?.displayOptions?.and(androidx.appcompat.app.ActionBar.DISPLAY_HOME_AS_UP) != 0)
        activity.showBasicVideoPlayerScreen(UNREACHABLE_VAST_URL)
        idleMainLooper()
        val opts = activity.supportActionBar!!.displayOptions
        assertTrue(opts and androidx.appcompat.app.ActionBar.DISPLAY_HOME_AS_UP != 0)
    }

    @Test
    fun `GIVEN pushed fragment WHEN navigating up THEN back stack is popped and up is hidden`() {
        val activity = launch()
        activity.showBasicVideoPlayerScreen(UNREACHABLE_VAST_URL)
        idleMainLooper()

        assertTrue(activity.onSupportNavigateUp())
        idleMainLooper()

        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
        assertTrue(activity.currentFragment() is ExampleSelectorFragment)
        val opts = activity.supportActionBar!!.displayOptions
        assertTrue(opts and androidx.appcompat.app.ActionBar.DISPLAY_HOME_AS_UP == 0)
    }

    @Test
    fun `GIVEN demo urls WHEN inspected THEN they are distinct https urls`() {
        val urls = listOf(
            MainActivity.VAST_DEMO_URL_SINGLE_MEDIA,
            MainActivity.VAST_DEMO_URL_MULTIPLE_MEDIA,
            MainActivity.VAST_DEMO_URL_NO_CAPTIONS,
            MainActivity.VAST_DEMO_URL_CLICK_THROUGH
        )
        assertEquals(4, urls.toSet().size)
        assertTrue(urls.all { it.startsWith("https://") })
    }
}

package com.iab.omid.sampleapp

import android.widget.FrameLayout
import com.iab.omid.sampleapp.player.CriteoVideoAdWrapper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class BasicVideoPlayerFragmentTest {

    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var activity: MainActivity

    private fun open(url: String = UNREACHABLE_VAST_URL): BasicVideoPlayerFragment {
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activity = controller.get()
        activity.showBasicVideoPlayerScreen(url)
        idleMainLooper()
        return activity.currentFragment() as BasicVideoPlayerFragment
    }

    @After
    fun tearDown() {
        runCatching { controller.pause().stop().destroy() }
    }

    private fun wrapperOf(fragment: BasicVideoPlayerFragment): CriteoVideoAdWrapper? {
        val container = fragment.requireView().findViewById<FrameLayout>(R.id.videoAdContainer)
        return (0 until container.childCount).map { container.getChildAt(it) }
            .filterIsInstance<CriteoVideoAdWrapper>().firstOrNull()
    }

    private fun fragmentField(fragment: BasicVideoPlayerFragment, name: String): Any? =
        BasicVideoPlayerFragment::class.java.getDeclaredField(name).apply { isAccessible = true }.get(fragment)

    @Test
    fun `GIVEN fragment opened WHEN view created THEN title is set and wrapper is added to container`() {
        val fragment = open()
        assertEquals("Basic Video Ad Example", activity.supportActionBar?.title)
        assertNotNull(wrapperOf(fragment))
    }

    @Test
    fun `GIVEN wrapper WHEN created THEN it is configured with match parent width and all callbacks`() {
        val fragment = open()
        val wrapper = wrapperOf(fragment)!!
        assertEquals(FrameLayout.LayoutParams.MATCH_PARENT, wrapper.layoutParams.width)
        assertEquals(FrameLayout.LayoutParams.WRAP_CONTENT, wrapper.layoutParams.height)
        assertEquals(6, wrapper.enableLogs.size)
        assertNotNull(wrapper.onVideoLoaded)
        assertNotNull(wrapper.onVideoStarted)
        assertNotNull(wrapper.onVideoPaused)
        assertNotNull(wrapper.onVideoTapped)
        assertNotNull(wrapper.onVideoError)
        // Invoking the logging callbacks must not throw
        wrapper.onVideoLoaded?.invoke()
        wrapper.onVideoStarted?.invoke()
        wrapper.onVideoPaused?.invoke()
        wrapper.onVideoTapped?.invoke()
        wrapper.onVideoError?.invoke(RuntimeException("boom"))
    }

    @Test
    fun `GIVEN no url argument WHEN fragment is created THEN wrapper is still created`() {
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activity = controller.get()
        // Add the fragment directly, without a vast_url argument (falls back to the demo url)
        val fragment = BasicVideoPlayerFragment()
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.mainFragmentContainer, fragment).commitNow()
        assertNotNull(wrapperOf(fragment))
    }

    @Test
    fun `GIVEN playing state not set WHEN paused and resumed THEN no auto resume happens`() {
        val fragment = open()
        controller.pause()
        assertEquals(false, fragmentField(fragment, "shouldAutoResumePlayback"))
        controller.resume()
        assertNotNull(wrapperOf(fragment))
    }

    @Test
    fun `GIVEN auto resume flag set WHEN resumed THEN fragment resumes without error`() {
        val fragment = open()
        BasicVideoPlayerFragment::class.java.getDeclaredField("shouldAutoResumePlayback")
            .apply { isAccessible = true }.setBoolean(fragment, true)
        controller.pause().resume()
        // onPause recomputes the flag from wrapper.isPlaying (false: no player ready)
        controller.pause()
        assertEquals(false, fragmentField(fragment, "shouldAutoResumePlayback"))
    }

    @Test
    fun `GIVEN fragment shown WHEN view destroyed THEN wrapper is released and cleared`() {
        val fragment = open()
        assertNotNull(fragmentField(fragment, "videoAdWrapper"))
        activity.onSupportNavigateUp()
        idleMainLooper()
        assertNull(fragmentField(fragment, "videoAdWrapper"))
        assertTrue(activity.currentFragment() is ExampleSelectorFragment)
    }
}

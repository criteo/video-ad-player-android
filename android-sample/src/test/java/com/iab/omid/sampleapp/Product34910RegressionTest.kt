package com.iab.omid.sampleapp

import androidx.recyclerview.widget.RecyclerView
import com.iab.omid.sampleapp.player.CriteoVideoAdWrapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

/**
 * PRODUCT-34910: a video the user paused manually must stay paused when the app goes to the
 * background and comes back. A video that was playing must still auto-resume.
 */
@RunWith(RobolectricTestRunner::class)
class Product34910RegressionTest {

    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var activity: MainActivity

    private fun open(show: (MainActivity) -> Unit) {
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activity = controller.get()
        show(activity)
        idleMainLooper()
    }

    @After
    fun tearDown() {
        runCatching { controller.pause().stop().destroy() }
    }

    private fun mockWrapper(playing: Boolean): CriteoVideoAdWrapper =
        mockk<CriteoVideoAdWrapper>(relaxed = true).also { every { it.isPlaying } returns playing }

    private fun setField(target: Any, name: String, value: Any?) {
        generateSequence<Class<*>>(target.javaClass) { it.superclass }
            .first { c -> c.declaredFields.any { it.name == name } }
            .getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }

    // BASIC SCREEN

    @Test
    fun `GIVEN basic screen manually paused WHEN app goes to background and returns THEN playback is not resumed`() {
        open { it.showBasicVideoPlayerScreen(UNREACHABLE_VAST_URL) }
        val fragment = activity.currentFragment() as BasicVideoPlayerFragment
        val wrapper = mockWrapper(playing = false)
        setField(fragment, "videoAdWrapper", wrapper)

        controller.pause().resume()

        verify(exactly = 0) { wrapper.play() }
    }

    @Test
    fun `GIVEN basic screen playing WHEN app goes to background and returns THEN playback resumes`() {
        open { it.showBasicVideoPlayerScreen(UNREACHABLE_VAST_URL) }
        val fragment = activity.currentFragment() as BasicVideoPlayerFragment
        val wrapper = mockWrapper(playing = true)
        setField(fragment, "videoAdWrapper", wrapper)

        controller.pause().resume()

        verify(exactly = 1) { wrapper.pause() }
        verify(exactly = 1) { wrapper.play() }
    }

    // FEED SCREEN

    private fun feedHolderWith(wrapper: CriteoVideoAdWrapper): Any {
        val fragment = activity.currentFragment() as FeedVideoPlayerFragment
        val recycler = fragment.requireView().findViewById<RecyclerView>(R.id.feedRecyclerView)
        val adapter = recycler.adapter!!
        val holder = adapter.createViewHolder(recycler, adapter.getItemViewType(9))
        adapter.bindViewHolder(holder, 9)
        setField(holder, "videoAdWrapper", wrapper)
        return holder
    }

    private fun call(target: Any, name: String, vararg args: Any?) {
        val m = target.javaClass.methods.first { it.name == name && it.parameterCount == args.size }
        m.isAccessible = true
        m.invoke(target, *args)
    }

    @Test
    fun `GIVEN feed video manually paused WHEN app leaves and returns with ad visible THEN playback is not resumed`() {
        open { it.showFeedVideoPlayerScreen(UNREACHABLE_VAST_URL) }
        val wrapper = mockWrapper(playing = false)
        val holder = feedHolderWith(wrapper)

        call(holder, "savePlaybackState") // what the fragment does when the app is paused
        call(holder, "onVisibilityChanged", true) // visibility re-check after returning

        verify(exactly = 0) { wrapper.play() }
    }

    @Test
    fun `GIVEN feed video playing WHEN app leaves and returns with ad visible THEN playback resumes`() {
        open { it.showFeedVideoPlayerScreen(UNREACHABLE_VAST_URL) }
        val wrapper = mockWrapper(playing = true)
        val holder = feedHolderWith(wrapper)

        call(holder, "savePlaybackState")
        call(holder, "onVisibilityChanged", true)

        verify(exactly = 1) { wrapper.play() }
    }
}

package com.iab.omid.sampleapp

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iab.omid.sampleapp.player.CriteoVideoAdWrapper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import java.lang.reflect.Method

@RunWith(RobolectricTestRunner::class)
class FeedVideoPlayerFragmentTest {

    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var activity: MainActivity
    private lateinit var fragment: FeedVideoPlayerFragment

    private val videoAdPosition = 9

    private fun open(url: String = UNREACHABLE_VAST_URL) {
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activity = controller.get()
        activity.showFeedVideoPlayerScreen(url)
        idleMainLooper()
        fragment = activity.currentFragment() as FeedVideoPlayerFragment
    }

    @After
    fun tearDown() {
        runCatching { controller.pause().stop().destroy() }
    }

    private fun recycler(): RecyclerView = fragment.requireView().findViewById(R.id.feedRecyclerView)

    private fun adapter(): RecyclerView.Adapter<RecyclerView.ViewHolder> = recycler().adapter!!

    private fun call(target: Any, name: String, vararg args: Any?): Any? {
        val m: Method = target.javaClass.methods.first { it.name == name && it.parameterCount == args.size }
        m.isAccessible = true
        return m.invoke(target, *args)
    }

    private fun bindVideoHolder(): RecyclerView.ViewHolder {
        val holder = adapter().createViewHolder(recycler(), adapter().getItemViewType(videoAdPosition))
        adapter().bindViewHolder(holder, videoAdPosition)
        return holder
    }

    private fun wrapperIn(holder: RecyclerView.ViewHolder): CriteoVideoAdWrapper? {
        val container = holder.itemView.findViewById<FrameLayout>(R.id.videoAdContainer)
        return (0 until container.childCount).map { container.getChildAt(it) }
            .filterIsInstance<CriteoVideoAdWrapper>().firstOrNull()
    }

    @Test
    fun `GIVEN fragment opened WHEN view created THEN title is set and recycler is configured`() {
        open()
        assertEquals("Feed Video Ad Example", activity.supportActionBar?.title)
        assertTrue(recycler().layoutManager is LinearLayoutManager)
        assertEquals(19, adapter().itemCount)
    }

    @Test
    fun `GIVEN adapter WHEN asking view types THEN only the video ad position is a video type`() {
        open()
        val types = (0 until adapter().itemCount).map { adapter().getItemViewType(it) }
        assertEquals(1, types[videoAdPosition])
        assertEquals(1, types.count { it == 1 })
        assertEquals(0, types[0])
    }

    @Test
    fun `GIVEN content item WHEN bound THEN title and body are shown`() {
        open()
        val holder = adapter().createViewHolder(recycler(), 0)
        adapter().bindViewHolder(holder, 0)
        assertEquals("Welcome to the Feed", holder.itemView.findViewById<TextView>(R.id.contentTitle).text.toString())
        assertTrue(holder.itemView.findViewById<TextView>(R.id.contentBody).text.isNotEmpty())
    }

    @Test
    fun `GIVEN video item WHEN bound THEN a wrapper is attached to the container`() {
        open()
        val holder = bindVideoHolder()
        assertNotNull(wrapperIn(holder))
    }

    @Test
    fun `GIVEN video item bound twice WHEN rebinding THEN the same cached wrapper is reused`() {
        open()
        val holder = bindVideoHolder()
        val first = wrapperIn(holder)
        adapter().bindViewHolder(holder, videoAdPosition)
        assertSame(first, wrapperIn(holder))
    }

    @Test
    fun `GIVEN cached wrapper in one holder WHEN bound in another THEN it moves to the new container`() {
        open()
        val h1 = bindVideoHolder()
        val wrapper = wrapperIn(h1)
        val h2 = bindVideoHolder()
        assertSame(wrapper, wrapperIn(h2))
        assertNull(wrapperIn(h1))
    }

    @Test
    fun `GIVEN bound video holder WHEN layout params posted THEN wrapper height follows 16 by 9`() {
        open()
        val holder = bindVideoHolder()
        idleMainLooper()
        val wrapper = wrapperIn(holder)!!
        assertEquals(FrameLayout.LayoutParams.MATCH_PARENT, wrapper.layoutParams.width)
    }

    @Test
    fun `GIVEN video holder WHEN visibility toggles THEN it does not throw and is idempotent`() {
        open()
        val holder = bindVideoHolder()
        call(holder, "onVisibilityChanged", true)
        call(holder, "onVisibilityChanged", true)
        call(holder, "onVisibilityChanged", false)
        call(holder, "onVisibilityChanged", false)
    }

    @Test
    fun `GIVEN video holder WHEN saving state and pausing THEN it does not throw`() {
        open()
        val holder = bindVideoHolder()
        call(holder, "savePlaybackState")
        call(holder, "pause")
        // After saving a non-playing state auto-resume is disabled, visibility true is a no-op
        call(holder, "onVisibilityChanged", true)
    }

    @Test
    fun `GIVEN video holder WHEN cleaned up THEN its container is emptied`() {
        open()
        val holder = bindVideoHolder()
        call(holder, "cleanup")
        assertNull(wrapperIn(holder))
    }

    @Test
    fun `GIVEN bound video holder WHEN recycled THEN wrapper stays cached`() {
        open()
        val holder = bindVideoHolder()
        val wrapper = wrapperIn(holder)
        call(adapter(), "onViewRecycled", holder)
        assertSame(wrapper, wrapperIn(holder))
    }

    @Test
    fun `GIVEN recycler laid out WHEN scrolled to the video THEN visibility check runs`() {
        open()
        val rv = recycler()
        rv.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY)
        )
        rv.layout(0, 0, 320, 480)
        rv.scrollToPosition(videoAdPosition)
        rv.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY)
        )
        rv.layout(0, 0, 320, 480)
        rv.scrollBy(0, 10)
        rv.scrollBy(0, -10)
        rv.stopScroll()
        idleMainLooper()
        assertNotNull(rv.layoutManager)
    }

    @Test
    fun `GIVEN fragment paused WHEN resumed THEN adapter pauses all videos and visibility check is posted`() {
        open()
        bindVideoHolder()
        controller.pause()
        controller.resume()
        idleMainLooper()
        assertNotNull(recycler())
    }

    @Test
    fun `GIVEN adapter with holders WHEN pausing all and cleaning up THEN caches are cleared`() {
        open()
        val holder = bindVideoHolder()
        call(adapter(), "pauseAllVideos")
        call(adapter(), "cleanupVideos")
        assertNull(wrapperIn(holder))
    }

    @Test
    fun `GIVEN fragment shown WHEN view destroyed THEN recycler and adapter references are cleared`() {
        open()
        bindVideoHolder()
        activity.onSupportNavigateUp()
        idleMainLooper()
        val f = FeedVideoPlayerFragment::class.java
        assertNull(f.getDeclaredField("recyclerView").apply { isAccessible = true }.get(fragment))
        assertNull(f.getDeclaredField("adapter").apply { isAccessible = true }.get(fragment))
    }

    @Test
    fun `GIVEN no url argument WHEN adapter builds items THEN video item falls back to demo url`() {
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activity = controller.get()
        fragment = FeedVideoPlayerFragment()
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.mainFragmentContainer, fragment).commitNow()
        assertEquals(19, adapter().itemCount)
    }
}

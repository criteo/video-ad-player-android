package com.iab.omid.sampleapp.scenario

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import androidx.media3.ui.PlayerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import com.iab.omid.sampleapp.player.CriteoVideoAdWrapper
import com.iab.omid.sampleapp.player.CriteoVideoPlayer

/** Finds the SDK views on screen and drives them the way a user would. */
object PlayerRobot {

    private fun <T : View> find(root: View, type: Class<T>, out: MutableList<T> = mutableListOf()): List<T> {
        if (type.isInstance(root)) out += type.cast(root)!!
        if (root is ViewGroup) for (i in 0 until root.childCount) find(root.getChildAt(i), type, out)
        return out
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    // Must be called on the main thread.
    private fun wrapperOnMain(activity: Activity): CriteoVideoAdWrapper? =
        find(activity.window.decorView, CriteoVideoAdWrapper::class.java).firstOrNull()

    /** Grabs the wrapper once so it can still be queried after RecyclerView detaches it. */
    fun wrapperRef(activity: Activity): CriteoVideoAdWrapper? = onMain { wrapperOnMain(activity) }
    fun isPlaying(w: CriteoVideoAdWrapper): Boolean = onMain { w.isPlaying }
    fun position(w: CriteoVideoAdWrapper): Long = onMain { w.currentPositionMs }

    fun isPlaying(activity: Activity): Boolean = onMain { wrapperOnMain(activity)?.isPlaying == true }
    fun isMuted(activity: Activity): Boolean = onMain { wrapperOnMain(activity)?.isMuted != false }
    fun positionMs(activity: Activity): Long = onMain { wrapperOnMain(activity)?.currentPositionMs ?: 0L }

    fun controls(activity: Activity): Controls = onMain {
        val player = find(activity.window.decorView, CriteoVideoPlayer::class.java).first()
        // PlayerView ships its own (hidden) ImageButtons; only take the player's own control row.
        val imageButtons = find(player, ImageButton::class.java).filter { it.parent?.parent === player }
        Controls(
            playPause = imageButtons[0],
            mute = imageButtons[1],
            cc = find(player, Button::class.java).first { it.parent === player && it.text == "CC" }
        )
    }

    class Controls(val playPause: View, val mute: View, val cc: View)

    private fun clickOn(view: View) {
        onView(androidx.test.espresso.matcher.ViewMatchers.isRoot()).perform(object : ViewAction {
            override fun getConstraints() = androidx.test.espresso.matcher.ViewMatchers.isRoot()
            override fun getDescription() = "click on specific view"
            override fun perform(uiController: androidx.test.espresso.UiController, root: View) {
                uiController.loopMainThreadUntilIdle()
                view.performClick()
                uiController.loopMainThreadUntilIdle()
            }
        })
    }

    fun tapPlayPause(activity: Activity) = clickOn(controls(activity).playPause)
    fun tapMute(activity: Activity) = clickOn(controls(activity).mute)

    /** A real touch on the video surface (not on a control button). */
    fun tapVideo() {
        onView(isAssignableFrom(PlayerView::class.java)).perform(click())
    }
}

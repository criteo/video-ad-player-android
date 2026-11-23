package com.iab.omid.sampleapp.scenario

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Spec: closed captioning and multiple media files. */
@RunWith(AndroidJUnit4::class)
class ClosedCaptionAndMediaScenarioTest : ScenarioBase() {

    @Test
    fun with_caption_file_cc_button_is_shown_enabled_and_file_is_downloaded() {
        val activity = launchBasic(VastSpec(captions = true))
        awaitPlaying(activity)

        assertEquals("CC visible", View.VISIBLE, PlayerRobot.ccVisibility(activity))
        assertTrue("CC enabled at launch", PlayerRobot.ccSelected(activity))
    }

    @Test
    fun without_caption_file_cc_button_is_hidden() {
        val activity = launchBasic(VastSpec(captions = false))
        awaitPlaying(activity)

        assertEquals("CC hidden", View.GONE, PlayerRobot.ccVisibility(activity))
    }

    @Test
    fun single_media_file_is_selected_by_default() {
        val activity = launchBasic(VastSpec(media = listOf(MediaSpec("only", 640, 360))))
        awaitPlaying(activity)

        assertEquals("the sole media rendition must be downloaded", listOf("/media/only.mp4"), server.mediaRequests.toList())
    }

    @Test
    fun multiple_media_files_only_one_is_downloaded_closest_to_placement_ratio() {
        val activity = launchBasic(
            VastSpec(
                media = listOf(
                    MediaSpec("portrait", 360, 640),
                    MediaSpec("square", 500, 500),
                    MediaSpec("landscape", 640, 360)
                )
            )
        )
        awaitPlaying(activity)

        assertEquals("exactly one rendition is fetched", 1, server.mediaRequests.size)
        // Basic's placement uses 16:9. Landscape (640/360) is an exact match;
        // square (1:1) and portrait (9:16) are farther from its target ratio.
        assertEquals("16:9 landscape must be selected for the Basic placement", "/media/landscape.mp4", server.mediaRequests.first())
    }

    @Test
    fun without_landscape_rendition_square_is_closer_to_16_9_than_portrait() {
        val activity = launchBasic(
            VastSpec(media = listOf(MediaSpec("portrait", 360, 640), MediaSpec("square", 500, 500)))
        )
        awaitPlaying(activity)

        // For target 16:9 (~1.778), square (1.0) differs by ~0.778,
        // while portrait (0.5625) differs by ~1.215, so square is closer.
        assertEquals("square must be the closest available rendition to 16:9", listOf("/media/square.mp4"), server.mediaRequests.toList())
    }
}

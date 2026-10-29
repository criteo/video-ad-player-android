package com.iab.omid.sampleapp.manager.vast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

class VastModelsTest {

    @Test
    fun `GIVEN default VastAd WHEN created THEN collections are empty and nullables are null`() {
        val ad = VastAd()
        assertNull(ad.videoUrl)
        assertNull(ad.duration)
        assertNull(ad.clickThroughUrl)
        assertNull(ad.closedCaptionUrl)
        assertNull(ad.verificationScriptUrl)
        assertNull(ad.verificationParameters)
        assertNull(ad.vendorKey)
        assertTrue(ad.mediaFiles.isEmpty())
        assertTrue(ad.impressionUrls.isEmpty())
        assertTrue(ad.errorUrls.isEmpty())
        assertTrue(ad.clickTrackingUrls.isEmpty())
        assertTrue(ad.trackingEvents.isEmpty())
        assertTrue(ad.verificationTracking.isEmpty())
    }

    @Test
    fun `GIVEN equal fields WHEN comparing VastAd THEN equal and copy changes one field`() {
        val url = URL("https://example.com/v.mp4")
        val a = VastAd(videoUrl = url, duration = "00:00:10", vendorKey = "k")
        val b = VastAd(videoUrl = url, duration = "00:00:10", vendorKey = "k")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        val c = a.copy(duration = "00:00:20")
        assertNotEquals(a, c)
        assertEquals("00:00:20", c.duration)
        assertTrue(a.toString().contains("VastAd"))
    }

    @Test
    fun `GIVEN VastMediaFile defaults WHEN created THEN optional fields are null`() {
        val file = VastMediaFile(URL("https://example.com/v.mp4"))
        assertNull(file.width)
        assertNull(file.height)
        assertNull(file.type)
        assertNull(file.captionUrl)
    }

    @Test
    fun `GIVEN VastMediaFile WHEN destructured and copied THEN values are preserved`() {
        val url = URL("https://example.com/v.mp4")
        val cc = URL("https://example.com/c.vtt")
        val file = VastMediaFile(url, 640, 360, "video/mp4", cc)
        val (u, w, h, t, c) = file
        assertEquals(url, u)
        assertEquals(640, w)
        assertEquals(360, h)
        assertEquals("video/mp4", t)
        assertEquals(cc, c)
        assertEquals(file, file.copy())
        assertEquals(720, file.copy(height = 720).height)
    }
}

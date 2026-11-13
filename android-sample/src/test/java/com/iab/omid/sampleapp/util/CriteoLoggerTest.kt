package com.iab.omid.sampleapp.util

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class CriteoLoggerTest {

    @After
    fun tearDown() {
        CriteoLogger.minimumLevel = CriteoLogger.Level.DEBUG
        CriteoLogger.enableAllCategories()
        ShadowLog.clear()
    }

    private fun messages() = ShadowLog.getLogs().map { it.msg }

    @Test
    fun `GIVEN all categories enabled by default WHEN checked THEN every category is enabled`() {
        CriteoLogger.enableAllCategories()
        CriteoLogger.Category.values().forEach { assertTrue(CriteoLogger.isCategoryEnabled(it)) }
    }

    @Test
    fun `GIVEN specific categories WHEN set THEN only those are enabled`() {
        CriteoLogger.setEnabledCategories(CriteoLogger.Category.VAST)
        assertTrue(CriteoLogger.isCategoryEnabled(CriteoLogger.Category.VAST))
        assertFalse(CriteoLogger.isCategoryEnabled(CriteoLogger.Category.NETWORK))
    }

    @Test
    fun `GIVEN minimum level ERROR WHEN logging debug and error THEN only error is logged`() {
        CriteoLogger.minimumLevel = CriteoLogger.Level.ERROR
        CriteoLogger.debug("dbg")
        CriteoLogger.info("inf")
        CriteoLogger.warning("wrn")
        CriteoLogger.error("err")
        assertTrue(messages().contains("err"))
        assertFalse(messages().contains("dbg"))
        assertFalse(messages().contains("inf"))
        assertFalse(messages().contains("wrn"))
    }

    @Test
    fun `GIVEN debug level WHEN logging at every level THEN all are logged`() {
        CriteoLogger.minimumLevel = CriteoLogger.Level.DEBUG
        CriteoLogger.debug("d")
        CriteoLogger.info("i")
        CriteoLogger.warning("w")
        CriteoLogger.error("e")
        CriteoLogger.critical("c")
        assertTrue(messages().containsAll(listOf("d", "i", "w", "e", "c")))
    }

    @Test
    fun `GIVEN disabled category WHEN logging in it THEN message is dropped`() {
        CriteoLogger.setEnabledCategories(CriteoLogger.Category.UI)
        CriteoLogger.info("hidden", CriteoLogger.Category.NETWORK)
        CriteoLogger.info("shown", CriteoLogger.Category.UI)
        assertFalse(messages().contains("hidden"))
        assertTrue(messages().contains("shown"))
    }

    @Test
    fun `GIVEN url and status WHEN logging network THEN message includes both`() {
        CriteoLogger.network("req", URL("https://example.com/a"), 200)
        assertTrue(messages().any { it == "req | URL: https://example.com/a | Status: 200" })
    }

    @Test
    fun `GIVEN network log without extras WHEN logged THEN message is plain`() {
        CriteoLogger.network("plain")
        assertTrue(messages().contains("plain"))
    }

    @Test
    fun `GIVEN beacon failure WHEN logging beacon THEN logged as warning with success flag`() {
        CriteoLogger.beacon("b", URL("https://example.com/b"), success = false)
        val entry = ShadowLog.getLogs().first { it.msg.startsWith("b") }
        assertTrue(entry.msg == "b | https://example.com/b | success=false")
        assertTrue(entry.type == android.util.Log.WARN)
    }

    @Test
    fun `GIVEN beacon success WHEN logging beacon THEN logged as info`() {
        CriteoLogger.beacon("ok", success = true)
        val entry = ShadowLog.getLogs().first { it.msg.startsWith("ok") }
        assertTrue(entry.type == android.util.Log.INFO)
        assertTrue(entry.msg == "ok | success=true")
    }

    @Test
    fun `GIVEN times WHEN logging video THEN formatted with one decimal`() {
        CriteoLogger.video("v", currentTime = 1.25, duration = 10.0)
        assertTrue(messages().any { it.startsWith("v | ") && it.endsWith("/10.0s") })
    }

    @Test
    fun `GIVEN only one time WHEN logging video THEN no progress suffix`() {
        CriteoLogger.video("v2", currentTime = 1.0)
        assertTrue(messages().contains("v2"))
    }
}

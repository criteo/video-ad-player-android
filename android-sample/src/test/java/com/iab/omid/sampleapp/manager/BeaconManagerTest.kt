package com.iab.omid.sampleapp.manager

import com.iab.omid.sampleapp.manager.vast.VastAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URL
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class BeaconManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var manager: BeaconManager

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        manager = BeaconManager(OkHttpClient(), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun next(timeoutSec: Long = 5) = server.takeRequest(timeoutSec, TimeUnit.SECONDS)

    @Test
    fun `GIVEN 200 response WHEN firing beacon THEN single GET with user agent is sent`() {
        server.enqueue(MockResponse().setResponseCode(200))

        manager.fireBeacon(server.url("/b").toUrl(), "start")

        val request = next()
        assertNotNull(request)
        assertEquals("GET", request!!.method)
        assertEquals("/b", request.path)
        assertEquals("OM-Demo/1.0", request.getHeader("User-Agent"))
        assertNull(next(1))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `GIVEN 404 response WHEN firing beacon THEN it is not retried`() {
        server.enqueue(MockResponse().setResponseCode(404))

        manager.fireBeacon(server.url("/missing").toUrl(), "start")

        assertNotNull(next())
        assertNull(next(2))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `GIVEN 500 then 200 WHEN firing beacon THEN it is retried once`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200))

        manager.fireBeacon(server.url("/retry").toUrl(), "midpoint")

        assertNotNull(next())
        assertNotNull(next(5)) // retry after ~1s backoff
        assertNull(next(1))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `GIVEN 429 then 200 WHEN firing beacon THEN it is retried`() {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(200))

        manager.fireBeacon(server.url("/rate").toUrl(), "complete")

        assertNotNull(next())
        assertNotNull(next(5))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `GIVEN 408 then 200 WHEN firing beacon THEN it is retried`() {
        server.enqueue(MockResponse().setResponseCode(408))
        server.enqueue(MockResponse().setResponseCode(200))

        manager.fireBeacon(server.url("/timeout").toUrl(), "complete")

        assertNotNull(next())
        assertNotNull(next(5))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `GIVEN connection drop then 200 WHEN firing beacon THEN IO failure is retried`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setResponseCode(200))

        manager.fireBeacon(server.url("/io").toUrl(), "start")

        assertNotNull(next())
        assertNotNull(next(5))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `GIVEN persistent 500 WHEN firing beacon THEN retries are capped after max attempts`() {
        repeat(6) { server.enqueue(MockResponse().setResponseCode(500)) }

        manager.fireBeacon(server.url("/fail").toUrl(), "start")

        // initial + 3 retries (1s, 2s, 4s backoff)
        repeat(4) { assertNotNull("request #$it", next(8)) }
        assertNull(next(1))
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `GIVEN persistent IO failure WHEN firing beacon THEN retries are capped`() {
        repeat(6) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }

        manager.fireBeacon(server.url("/iofail").toUrl(), "start")

        repeat(4) { assertNotNull("request #$it", next(8)) }
        assertNull(next(1))
    }

    @Test
    fun `GIVEN scope cancelled during backoff WHEN waiting THEN no retry is sent`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200))

        manager.fireBeacon(server.url("/cancel").toUrl(), "start")
        assertNotNull(next())
        scope.cancel()

        assertNull(next(2))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `GIVEN ad with impression urls WHEN firing impressions THEN each url is hit`() {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(200)) }
        val ad = VastAd(impressionUrls = listOf(server.url("/i1").toUrl(), server.url("/i2").toUrl()))

        manager.fireImpressionBeacons(ad)

        val paths = listOf(next()!!.path!!, next()!!.path!!).sorted()
        assertEquals(listOf("/i1", "/i2"), paths)
    }

    @Test
    fun `GIVEN ad with click tracking urls WHEN firing click tracking THEN each url is hit`() {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(200)) }
        val ad = VastAd(clickTrackingUrls = listOf(server.url("/c1").toUrl(), server.url("/c2").toUrl()))

        manager.fireClickTrackingBeacons(ad)

        val paths = listOf(next()!!.path!!, next()!!.path!!).sorted()
        assertEquals(listOf("/c1", "/c2"), paths)
    }

    @Test
    fun `GIVEN ad without urls WHEN firing beacons THEN nothing is sent`() {
        manager.fireImpressionBeacons(VastAd())
        manager.fireClickTrackingBeacons(VastAd())

        assertNull(next(1))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `GIVEN default scope constructor WHEN firing beacon THEN it still works`() {
        server.enqueue(MockResponse().setResponseCode(200))
        BeaconManager(OkHttpClient()).fireBeacon(URL(server.url("/default").toString()), "start")
        assertNotNull(next())
    }
}

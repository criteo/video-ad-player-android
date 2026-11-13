package com.iab.omid.sampleapp.scenario

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import java.util.concurrent.CopyOnWriteArrayList

/** One <MediaFile> rendition advertised by the fake VAST. All renditions serve the same sample.mp4. */
data class MediaSpec(val name: String, val width: Int, val height: Int)

/** Shape of the VAST document served by [ScenarioServer]. */
data class VastSpec(
    val media: List<MediaSpec> = listOf(MediaSpec("video", 640, 360)),
    val clickThrough: String? = null,
    // Independent of clickThrough: an empty list omits ClickTracking; relative URLs use this server.
    val clickTrackingUrls: List<String> = listOf("/beacon/${BeaconEvent.CLICK_TRACKING.xmlName}"),
    val captions: Boolean = false,
    val broken: Boolean = false
)

data class Beacon(val event: BeaconEvent, val atMs: Long)

/**
 * Local fake ad server: serves a VAST document, the sample video, an optional WebVTT file and records
 * every beacon the player fires (`/beacon/<name>`) together with every media request.
 */
class ScenarioServer(var vast: VastSpec = VastSpec()) {

    private val server = MockWebServer()
    val beacons = CopyOnWriteArrayList<Beacon>()
    val mediaRequests = CopyOnWriteArrayList<String>()

    private val videoBytes: ByteArray by lazy {
        InstrumentationRegistry.getInstrumentation().context.assets.open("sample.mp4").use { it.readBytes() }
    }
    private val vttBytes: ByteArray by lazy {
        InstrumentationRegistry.getInstrumentation().context.assets.open("sample.vtt").use { it.readBytes() }
    }

    val vastUrl: String get() = server.url("/vast.xml").toString()

    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/vast.xml") ->
                        if (vast.broken) MockResponse().setResponseCode(500)
                        else MockResponse().setBody(buildVast()).setHeader("Content-Type", "text/xml")
                    path.startsWith("/media/") -> {
                        mediaRequests += path
                        MockResponse().setBody(Buffer().write(videoBytes)).setHeader("Content-Type", "video/mp4")
                    }
                    path.startsWith("/cc.vtt") ->
                        MockResponse().setBody(Buffer().write(vttBytes)).setHeader("Content-Type", "text/vtt")
                    path.startsWith("/beacon/") -> {
                        val name = path.removePrefix("/beacon/").substringBefore('?')
                        val event = BeaconEvent.values().firstOrNull { it.xmlName == name }
                            ?: return MockResponse().setResponseCode(404)
                        beacons += Beacon(event, SystemClock.elapsedRealtime())
                        MockResponse().setResponseCode(200)
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    fun shutdown() = server.shutdown()

    fun countReceivedBeacon(event: BeaconEvent): Int = beacons.count { it.event == event }

    fun names(): List<BeaconEvent> = beacons.map { it.event }

    /** Waits up to [timeoutMs] for at least [times] requests to the endpoint for [event]. */
    fun hasReceivedBeacon(event: BeaconEvent, timeoutMs: Long = ScenarioTimeouts.BEACON_MS, times: Int = 1): Boolean =
        waitUntil(timeoutMs) { countReceivedBeacon(event) >= times }

    /** Observes the full [forMs] window and returns true only if [event] has never arrived. */
    fun neverFires(event: BeaconEvent, forMs: Long = ScenarioTimeouts.NO_BEACON_MS): Boolean =
        !waitUntil(forMs) { countReceivedBeacon(event) > 0 }

    private fun buildVast(): String {
        val base = server.url("/").toString().trimEnd('/')
        fun tracking(event: BeaconEvent) = "<Tracking event=\"${event.xmlName}\">$base/beacon/${event.xmlName}</Tracking>"
        val events = BeaconEvent.values()
            .filter { it != BeaconEvent.IMPRESSION && it != BeaconEvent.CLICK_TRACKING }
            .joinToString("\n") { tracking(it) }
        val clickThrough = vast.clickThrough?.let {
            """<ClickThrough id="Criteo"><![CDATA[$it]]></ClickThrough>"""
        }.orEmpty()
        val clickTracking = vast.clickTrackingUrls.joinToString("\n") {
            """<ClickTracking><![CDATA[${server.url(it)}]]></ClickTracking>"""
        }
        val clicks = if (clickThrough.isNotEmpty() || clickTracking.isNotEmpty()) {
            "<VideoClicks>$clickThrough$clickTracking</VideoClicks>"
        } else ""
        val media = vast.media.joinToString("\n") {
            """<MediaFile delivery="progressive" type="video/mp4" width="${it.width}" height="${it.height}">$base/media/${it.name}.mp4</MediaFile>"""
        }
        val cc = if (vast.captions) "<ClosedCaptionFiles><ClosedCaptionFile>$base/cc.vtt</ClosedCaptionFile></ClosedCaptionFiles>" else ""
        return """<?xml version="1.0" encoding="UTF-8"?>
            <VAST version="3.0"><Ad id="scenario"><InLine>
              <Impression>$base/beacon/${BeaconEvent.IMPRESSION.xmlName}</Impression>
              <Creatives><Creative><Linear>
                <Duration>00:00:10</Duration>
                <TrackingEvents>$events</TrackingEvents>
                $clicks
                <MediaFiles>$media</MediaFiles>
                $cc
              </Linear></Creative></Creatives>
            </InLine></Ad></VAST>""".trimIndent()
    }
}

fun waitUntil(timeoutMs: Long, pollMs: Long = ScenarioTimeouts.POLL_INTERVAL_MS, condition: () -> Boolean): Boolean {
    val end = SystemClock.elapsedRealtime() + timeoutMs
    while (SystemClock.elapsedRealtime() < end) {
        if (condition()) return true
        Thread.sleep(pollMs)
    }
    return condition()
}

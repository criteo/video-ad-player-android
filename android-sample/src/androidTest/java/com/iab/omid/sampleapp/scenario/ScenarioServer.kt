package com.iab.omid.sampleapp.scenario

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
    val captions: Boolean = false,
    val broken: Boolean = false
)

data class Beacon(val name: String, val atMs: Long)

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
                        beacons += Beacon(path.removePrefix("/beacon/").substringBefore('?'), System.currentTimeMillis())
                        MockResponse().setResponseCode(200)
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    fun shutdown() = server.shutdown()

    fun count(name: String): Int = beacons.count { it.name == name }

    fun names(): List<String> = beacons.map { it.name }

    /** Waits until beacon [name] has been fired at least [times] times. */
    fun awaitBeacon(name: String, timeoutMs: Long = 20_000, times: Int = 1): Boolean =
        waitUntil(timeoutMs) { count(name) >= times }

    /** True if beacon [name] stays unfired for [forMs]. */
    fun neverFires(name: String, forMs: Long = 2_000): Boolean = !waitUntil(forMs) { count(name) > 0 }

    private fun buildVast(): String {
        val base = server.url("/").toString().trimEnd('/')
        fun tracking(event: String) = "<Tracking event=\"$event\">$base/beacon/$event</Tracking>"
        val events = listOf("start", "firstQuartile", "midpoint", "thirdQuartile", "complete", "mute", "unmute", "pause", "resume")
            .joinToString("\n") { tracking(it) }
        val clicks = vast.clickThrough?.let {
            """<VideoClicks>
                 <ClickThrough id="Criteo"><![CDATA[$it]]></ClickThrough>
                 <ClickTracking>$base/beacon/clickTracking</ClickTracking>
               </VideoClicks>"""
        }.orEmpty()
        val media = vast.media.joinToString("\n") {
            """<MediaFile delivery="progressive" type="video/mp4" width="${it.width}" height="${it.height}">$base/media/${it.name}.mp4</MediaFile>"""
        }
        val cc = if (vast.captions) "<ClosedCaptionFiles><ClosedCaptionFile>$base/cc.vtt</ClosedCaptionFile></ClosedCaptionFiles>" else ""
        return """<?xml version="1.0" encoding="UTF-8"?>
            <VAST version="3.0"><Ad id="scenario"><InLine>
              <Impression>$base/beacon/impression</Impression>
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

fun waitUntil(timeoutMs: Long, pollMs: Long = 100, condition: () -> Boolean): Boolean {
    val end = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < end) {
        if (condition()) return true
        Thread.sleep(pollMs)
    }
    return condition()
}

package com.iab.omid.sampleapp.player

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.iab.omid.sampleapp.manager.vast.VastAd
import com.iab.omid.sampleapp.manager.vast.VastMediaFile
import com.iab.omid.sampleapp.player.CriteoVideoAdError.AssetDownloadFailed
import com.iab.omid.sampleapp.player.CriteoVideoAdError.InvalidURL
import com.iab.omid.sampleapp.player.CriteoVideoAdError.PlayerError
import com.iab.omid.sampleapp.player.CriteoVideoAdError.VastParsingFailed
import com.iab.omid.sampleapp.util.CriteoLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class CriteoVideoAdWrapperTest {

    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private var wrapper: CriteoVideoAdWrapper? = null

    /** path -> response; unknown paths return 404 */
    private val routes = mutableMapOf<String, MockResponse>()

    private val emptyVast = "<VAST><Ad><InLine><Creatives></Creatives></InLine></Ad></VAST>"

    @Before
    fun setUp() {
        CriteoLogger.minimumLevel = CriteoLogger.Level.CRITICAL
        context = ApplicationProvider.getApplicationContext()
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    routes[request.path?.substringBefore('?')] ?: MockResponse().setResponseCode(404)
            }
            start()
        }
    }

    @After
    fun tearDown() {
        wrapper?.release()
        server.shutdown()
        unmockkAll()
        CriteoLogger.enableAllCategories()
        CriteoLogger.minimumLevel = CriteoLogger.Level.DEBUG
    }

    // region helpers
    private fun vast(
        media: String = """<MediaFile width="640" height="360" type="video/mp4">${server.url("/video.mp4")}</MediaFile>""",
        extra: String = ""
    ) = """
        <VAST><Ad><InLine>
          <Impression>${server.url("/imp")}</Impression>
          <Creatives><Creative><Linear>
            <Duration>00:00:10</Duration>
            <TrackingEvents><Tracking event="start">${server.url("/start")}</Tracking></TrackingEvents>
            <MediaFiles>$media</MediaFiles>
            $extra
          </Linear></Creative></Creatives>
        </InLine></Ad></VAST>
    """.trimIndent()

    private fun create(config: CriteoVideoAdConfiguration = CriteoVideoAdConfiguration.DEFAULT) =
        CriteoVideoAdWrapper(context, configuration = config).also { wrapper = it }

    private fun <T> CriteoVideoAdWrapper.field(name: String): T {
        val f = CriteoVideoAdWrapper::class.java.getDeclaredField(name)
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return f.get(this) as T
    }

    private fun CriteoVideoAdWrapper.setField(name: String, value: Any?) {
        val f = CriteoVideoAdWrapper::class.java.getDeclaredField(name)
        f.isAccessible = true
        f.set(this, value)
    }

    /** Pumps the main looper until [condition] holds or the timeout elapses (background IO resumes on main). */
    private fun awaitUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper(20, TimeUnit.MILLISECONDS)
            if (condition()) return
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Condition not met within ${timeoutMs}ms", condition())
    }

    /** View.post only runs once attached, so UI-state tests host the wrapper in an activity. */
    private fun attach(w: CriteoVideoAdWrapper) {
        Robolectric.buildActivity(Activity::class.java).setup().get().setContentView(w)
    }

    private fun CriteoVideoAdWrapper.forceState(state: CriteoVideoAdState) {
        val m = CriteoVideoAdWrapper::class.java.getDeclaredMethod("setCurrentState", CriteoVideoAdState::class.java)
        m.isAccessible = true
        m.invoke(this, state)
    }

    private fun selectBest(w: CriteoVideoAdWrapper, ad: VastAd): URL? {
        val m = CriteoVideoAdWrapper::class.java.getDeclaredMethod("selectBestMediaURL", VastAd::class.java)
        m.isAccessible = true
        return m.invoke(w, ad) as URL?
    }

    private fun mediaFile(path: String, w: Int?, h: Int?, type: String? = "video/mp4") =
        VastMediaFile(URL("https://example.com$path"), w, h, type)
    // endregion

    // region simple types
    @Test
    fun `GIVEN default configuration WHEN inspected THEN has expected defaults`() {
        val c = CriteoVideoAdConfiguration.DEFAULT
        assertTrue(c.autoLoad)
        assertTrue(c.startsMuted)
        assertEquals(Color.WHITE, c.backgroundColor)
        assertEquals("Loading video ad...", c.loadingText)
        assertEquals("Retry", c.retryButtonText)
        assertEquals(Color.RED, c.errorTextColor)
        assertEquals(CriteoVideoAdConfiguration(), c)
    }

    @Test
    fun `GIVEN configuration WHEN copied THEN overrides only given fields`() {
        val c = CriteoVideoAdConfiguration().copy(autoLoad = false, loadingText = "Wait")
        assertFalse(c.autoLoad)
        assertEquals("Wait", c.loadingText)
        assertTrue(c.startsMuted)
    }

    @Test
    fun `GIVEN every log category WHEN mapped THEN maps to logger category`() {
        val expected = mapOf(
            CriteoVideoAdLogCategory.VAST to CriteoLogger.Category.VAST,
            CriteoVideoAdLogCategory.NETWORK to CriteoLogger.Category.NETWORK,
            CriteoVideoAdLogCategory.VIDEO to CriteoLogger.Category.VIDEO,
            CriteoVideoAdLogCategory.BEACON to CriteoLogger.Category.BEACON,
            CriteoVideoAdLogCategory.OMID to CriteoLogger.Category.OMID,
            CriteoVideoAdLogCategory.UI to CriteoLogger.Category.UI
        )
        assertEquals(CriteoVideoAdLogCategory.values().size, expected.size)
        expected.forEach { (k, v) -> assertEquals(v, k.toCategory()) }
    }

    @Test
    fun `GIVEN errors WHEN created THEN messages are prefixed`() {
        assertEquals("Invalid VAST URL: u", InvalidURL("u").message)
        assertEquals("VAST parsing failed: m", VastParsingFailed("m").message)
        assertEquals("Asset download failed: m", AssetDownloadFailed("m").message)
        assertEquals("Player error: m", PlayerError("m").message)
    }

    @Test
    fun `GIVEN sources and states WHEN compared THEN data semantics hold`() {
        assertEquals(VASTSource.Url("a"), VASTSource.Url("a"))
        assertEquals("x", VASTSource.Xml("x").xml)
        val e = RuntimeException("boom")
        assertEquals(CriteoVideoAdState.Error(e), CriteoVideoAdState.Error(e))
        assertSame(e, CriteoVideoAdState.Error(e).error)
        assertTrue(CriteoVideoAdState.Loading != CriteoVideoAdState.Ready)
    }
    // endregion

    // region construction / defaults
    @Test
    fun `GIVEN new wrapper WHEN reading public state THEN not loaded and defaults returned`() {
        val w = create()
        assertEquals(CriteoVideoAdState.NotLoaded, w.state)
        assertFalse(w.isPlaying)
        assertTrue(w.isMuted)
        assertEquals(0L, w.currentPositionMs)
        assertSame(CriteoVideoAdConfiguration.DEFAULT, w.configuration)
        assertEquals(emptySet<CriteoVideoAdLogCategory>(), w.enableLogs)
    }

    @Test
    fun `GIVEN custom configuration WHEN created THEN labels use configured text`() {
        val w = create(CriteoVideoAdConfiguration(loadingText = "Hold on", retryButtonText = "Again", backgroundColor = Color.BLACK))
        assertEquals("Hold on", w.field<TextView>("loadingLabel").text.toString())
        assertEquals("Again", w.field<Button>("retryButton").text.toString())
    }

    @Test
    fun `GIVEN new wrapper WHEN created THEN loading and error views hidden`() {
        val w = create()
        assertEquals(View.GONE, w.field<FrameLayout>("loadingContainerView").visibility)
        assertEquals(View.GONE, w.field<FrameLayout>("errorContainerView").visibility)
    }

    @Test
    fun `GIVEN log categories WHEN enableLogs set THEN logger categories updated`() {
        val w = create()
        w.enableLogs = setOf(CriteoVideoAdLogCategory.VAST, CriteoVideoAdLogCategory.UI)
        assertEquals(setOf(CriteoVideoAdLogCategory.VAST, CriteoVideoAdLogCategory.UI), w.enableLogs)
        assertTrue(CriteoLogger.isCategoryEnabled(CriteoLogger.Category.VAST))
        assertTrue(CriteoLogger.isCategoryEnabled(CriteoLogger.Category.UI))
        assertFalse(CriteoLogger.isCategoryEnabled(CriteoLogger.Category.NETWORK))
    }

    @Test
    fun `GIVEN no source WHEN retry THEN nothing happens`() {
        val w = create()
        w.retry()
        assertEquals(CriteoVideoAdState.NotLoaded, w.state)
    }
    // endregion

    // region selectBestMediaURL
    @Test
    fun `GIVEN no media files WHEN selecting THEN null`() {
        assertNull(selectBest(create(), VastAd()))
    }

    @Test
    fun `GIVEN only non mp4 files WHEN selecting THEN null`() {
        val ad = VastAd(mediaFiles = listOf(mediaFile("/a.webm", 640, 360, "video/webm"), mediaFile("/b", 1, 1, null)))
        assertNull(selectBest(create(), ad))
    }

    @Test
    fun `GIVEN zero sized wrapper WHEN selecting THEN closest to 16 9 chosen`() {
        val ad = VastAd(mediaFiles = listOf(mediaFile("/square.mp4", 500, 500), mediaFile("/wide.mp4", 1280, 720)))
        assertEquals("https://example.com/wide.mp4", selectBest(create(), ad).toString())
    }

    @Test
    fun `GIVEN square wrapper WHEN selecting THEN closest to 1 1 chosen`() {
        val w = create()
        w.layout(0, 0, 200, 200)
        val ad = VastAd(mediaFiles = listOf(mediaFile("/square.mp4", 500, 500), mediaFile("/wide.mp4", 1280, 720)))
        assertEquals("https://example.com/square.mp4", selectBest(w, ad).toString())
    }

    @Test
    fun `GIVEN media without dimensions WHEN selecting THEN sized rendition preferred`() {
        val ad = VastAd(mediaFiles = listOf(mediaFile("/nosize.mp4", null, null), mediaFile("/sized.mp4", 100, 100)))
        assertEquals("https://example.com/sized.mp4", selectBest(create(), ad).toString())
    }

    @Test
    fun `GIVEN media with zero height WHEN selecting THEN still returns a candidate`() {
        val ad = VastAd(mediaFiles = listOf(mediaFile("/bad.mp4", 100, 0)))
        assertEquals("https://example.com/bad.mp4", selectBest(create(), ad).toString())
    }

    @Test
    fun `GIVEN uppercase mp4 type WHEN selecting THEN matched case insensitively`() {
        val ad = VastAd(mediaFiles = listOf(mediaFile("/up.mp4", 16, 9, "VIDEO/MP4")))
        assertEquals("https://example.com/up.mp4", selectBest(create(), ad).toString())
    }
    // endregion

    // region loading flow
    @Test
    fun `GIVEN xml source WHEN loadVideoAd THEN state is loading immediately`() {
        val w = create()
        w.loadVideoAd(VASTSource.Xml(emptyVast))
        assertEquals(CriteoVideoAdState.Loading, w.state)
    }

    @Test
    fun `GIVEN loading state WHEN ui updated THEN only loading view visible`() {
        val w = create()
        attach(w)
        w.forceState(CriteoVideoAdState.Loading)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.VISIBLE, w.field<FrameLayout>("loadingContainerView").visibility)
        assertEquals(View.GONE, w.field<FrameLayout>("errorContainerView").visibility)
    }

    @Test
    fun `GIVEN error without message WHEN ui updated THEN default text shown`() {
        val w = create()
        attach(w)
        w.forceState(CriteoVideoAdState.Error(RuntimeException()))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("An error occurred", w.field<TextView>("errorLabel").text.toString())
    }

    @Test
    fun `GIVEN loading WHEN loadVideoAd called again THEN second call ignored`() {
        val w = create()
        var errors = 0
        w.onVideoError = { errors++ }
        w.loadVideoAd(VASTSource.Xml(emptyVast))
        w.loadVideoAd(VASTSource.Xml(emptyVast))
        awaitUntil { w.state is CriteoVideoAdState.Error }
        assertEquals(1, errors)
    }

    @Test
    fun `GIVEN fromXml WHEN created THEN loading started`() {
        val w = CriteoVideoAdWrapper.fromXml(context, emptyVast).also { wrapper = it }
        assertEquals(CriteoVideoAdState.Loading, w.state)
    }

    @Test
    fun `GIVEN fromUrl WHEN created THEN loading started with configuration`() {
        val config = CriteoVideoAdConfiguration(startsMuted = false)
        val w = CriteoVideoAdWrapper.fromUrl(context, server.url("/vast.xml").toString(), config).also { wrapper = it }
        assertEquals(CriteoVideoAdState.Loading, w.state)
        assertSame(config, w.configuration)
    }

    @Test
    fun `GIVEN vast without media WHEN loading THEN invalid url error shown`() {
        val w = create()
        attach(w)
        var error: Throwable? = null
        w.onVideoError = { error = it }
        w.loadVideoAd(VASTSource.Xml(emptyVast))
        awaitUntil { w.state is CriteoVideoAdState.Error }

        assertTrue(error is InvalidURL)
        assertEquals(View.VISIBLE, w.field<FrameLayout>("errorContainerView").visibility)
        assertEquals(View.GONE, w.field<FrameLayout>("loadingContainerView").visibility)
        assertTrue(w.field<TextView>("errorLabel").text.contains("No valid media file urls"))
    }

    @Test
    fun `GIVEN vast url returning 404 WHEN loading THEN vast parsing error`() {
        val w = create()
        var error: Throwable? = null
        w.onVideoError = { error = it }
        w.loadVideoAd(VASTSource.Url(server.url("/missing.xml").toString()))
        awaitUntil { w.state is CriteoVideoAdState.Error }
        assertTrue(error is VastParsingFailed)
    }

    @Test
    fun `GIVEN video returning 404 WHEN loading THEN asset download error`() {
        routes["/vast.xml"] = MockResponse().setBody(vast())
        val w = create()
        var error: Throwable? = null
        w.onVideoError = { error = it }
        w.loadVideoAd(VASTSource.Url(server.url("/vast.xml").toString()))
        awaitUntil { w.state is CriteoVideoAdState.Error }
        assertTrue(error is AssetDownloadFailed)
    }

    @Test
    fun `GIVEN non mp4 media WHEN loading THEN falls back to first media url`() {
        routes["/video.webm"] = MockResponse().setBody("data")
        val xml = vast(media = """<MediaFile width="640" height="360" type="video/webm">${server.url("/video.webm")}</MediaFile>""")
        val w = create()
        var loaded = false
        w.onVideoLoaded = { loaded = true }
        w.loadVideoAd(VASTSource.Xml(xml))
        awaitUntil { loaded }
        assertEquals(CriteoVideoAdState.Ready, w.state)
    }

    @Test
    fun `GIVEN error WHEN retry button tapped THEN loading restarts with same source`() {
        val w = create()
        var errors = 0
        w.onVideoError = { errors++ }
        w.loadVideoAd(VASTSource.Xml(emptyVast))
        awaitUntil { errors == 1 }

        w.field<Button>("retryButton").performClick()
        assertEquals(CriteoVideoAdState.Loading, w.state)
        awaitUntil { errors == 2 }
        assertTrue(w.state is CriteoVideoAdState.Error)
    }

    @Test
    fun `GIVEN valid assets WHEN loading THEN ready player added and callbacks invoked`() {
        routes["/video.mp4"] = MockResponse().setBody("not really a video")
        val w = create()
        var loaded = false
        w.onVideoLoaded = { loaded = true }
        w.loadVideoAd(VASTSource.Xml(vast()))
        awaitUntil { loaded && w.field<CriteoVideoPlayer?>("videoPlayer") != null }

        assertEquals(CriteoVideoAdState.Ready, w.state)
        val player = w.field<CriteoVideoPlayer>("videoPlayer")
        assertSame(player, w.getChildAt(0))
        assertNotNull(w.field<Any?>("videoAssetFile"))
        assertNull(w.field<Any?>("closedCaptionsAssetFile"))
        assertEquals(View.GONE, w.field<FrameLayout>("loadingContainerView").visibility)
    }

    @Test
    fun `GIVEN closed caption file WHEN loading THEN caption downloaded`() {
        routes["/video.mp4"] = MockResponse().setBody("video")
        routes["/cc.vtt"] = MockResponse().setBody("WEBVTT")
        val xml = vast(extra = "<ClosedCaptionFile>${server.url("/cc.vtt")}</ClosedCaptionFile>")
        val w = create()
        w.loadVideoAd(VASTSource.Xml(xml))
        awaitUntil { w.field<Any?>("closedCaptionsAssetFile") != null && w.field<Any?>("videoPlayer") != null }
        assertEquals(CriteoVideoAdState.Ready, w.state)
    }

    @Test
    fun `GIVEN closed caption 404 WHEN loading THEN still ready without captions`() {
        routes["/video.mp4"] = MockResponse().setBody("video")
        val xml = vast(extra = "<ClosedCaptionFile>${server.url("/cc.vtt")}</ClosedCaptionFile>")
        val w = create()
        w.loadVideoAd(VASTSource.Xml(xml))
        awaitUntil { w.field<Any?>("videoPlayer") != null }
        assertEquals(CriteoVideoAdState.Ready, w.state)
        assertNull(w.field<Any?>("closedCaptionsAssetFile"))
    }

    @Test
    fun `GIVEN ready player WHEN tapped with click through THEN callback and browser`() {
        routes["/video.mp4"] = MockResponse().setBody("video")
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val xml = vast(extra = "<VideoClicks><ClickThrough>https://example.com/landing</ClickThrough></VideoClicks>")
        val w = CriteoVideoAdWrapper(activity).also { wrapper = it }
        var tapped = 0
        w.onVideoTapped = { tapped++ }
        w.loadVideoAd(VASTSource.Xml(xml))
        awaitUntil { w.field<Any?>("videoPlayer") != null }

        w.field<CriteoVideoPlayer>("videoPlayer").performClick()

        assertEquals(1, tapped)
        assertEquals("https://example.com/landing", shadowOf(activity).nextStartedActivity.data.toString())
    }

    @Test
    fun `GIVEN ready player WHEN tapped without click through THEN playback toggled`() {
        routes["/video.mp4"] = MockResponse().setBody("video")
        val w = create()
        w.loadVideoAd(VASTSource.Xml(vast()))
        awaitUntil { w.field<Any?>("videoPlayer") != null }
        val mockPlayer = mockk<CriteoVideoPlayer>(relaxed = true)
        w.setField("videoPlayer", mockPlayer)

        // trigger the real player's click listener installed by the wrapper
        val real = w.getChildAt(0) as CriteoVideoPlayer
        real.performClick()

        verify { mockPlayer.togglePlayPause(true) }
        real.release()
    }

    @Test
    fun `GIVEN click through launch failure WHEN tapped THEN error swallowed`() {
        routes["/video.mp4"] = MockResponse().setBody("video")
        val throwing = object : android.content.ContextWrapper(context) {
            override fun startActivity(intent: android.content.Intent?) = throw IllegalStateException("boom")
        }
        val xml = vast(extra = "<VideoClicks><ClickThrough>https://example.com/landing</ClickThrough></VideoClicks>")
        val w = CriteoVideoAdWrapper(throwing).also { wrapper = it }
        var tapped = 0
        w.onVideoTapped = { tapped++ }
        w.loadVideoAd(VASTSource.Xml(xml))
        awaitUntil { w.field<Any?>("videoPlayer") != null }
        w.field<CriteoVideoPlayer>("videoPlayer").performClick()
        assertEquals(1, tapped)
    }

    @Test
    fun `GIVEN player errors WHEN state observed THEN wrapper moves to error state`() {
        routes["/video.mp4"] = MockResponse().setBody("video")
        val w = create()
        var error: Throwable? = null
        w.onVideoError = { error = it }
        w.loadVideoAd(VASTSource.Xml(vast()))
        awaitUntil { w.field<Any?>("videoPlayer") != null }

        w.field<CriteoVideoPlayer>("videoPlayer").onPlayerError(
            androidx.media3.common.PlaybackException("x", null, androidx.media3.common.PlaybackException.ERROR_CODE_UNSPECIFIED)
        )
        awaitUntil { error != null }
        assertTrue(error is PlayerError)
        assertTrue(w.state is CriteoVideoAdState.Error)
    }

    @Test
    fun `GIVEN player playing WHEN state observed THEN started callback and progress reported`() {
        routes["/video.mp4"] = MockResponse().setBody("video")
        val w = create()
        var started = 0
        var paused = 0
        var progress: Pair<Long, Long>? = null
        w.onVideoStarted = { started++ }
        w.onVideoPaused = { paused++ }
        w.onPlaybackProgress = { c, d -> progress = c to d }
        w.loadVideoAd(VASTSource.Xml(vast()))
        awaitUntil { w.field<Any?>("videoPlayer") != null }
        val player = w.field<CriteoVideoPlayer>("videoPlayer")

        player.onIsPlayingChanged(true)
        awaitUntil { started >= 1 }
        assertTrue(w.isPlaying)
        awaitUntil { progress != null }

        player.onIsPlayingChanged(false)
        awaitUntil { paused >= 1 }
    }
    // endregion

    // region playback controls with injected player
    private fun injectMockPlayer(w: CriteoVideoAdWrapper): CriteoVideoPlayer {
        val p = mockk<CriteoVideoPlayer>(relaxed = true)
        every { p.state } returns MutableStateFlow(
            CriteoVideoPlayer.PlayerState(CriteoVideoPlayer.PlaybackState.PLAYING, isMuted = false)
        )
        every { p.currentPositionMs } returns 777L
        w.setField("videoPlayer", p)
        return p
    }

    @Test
    fun `GIVEN no player WHEN controlling THEN callbacks not invoked`() {
        val w = create()
        var calls = 0
        w.onVideoStarted = { calls++ }
        w.onVideoPaused = { calls++ }
        w.play()
        w.pause()
        w.seekTo(10)
        w.toggleMute()
        assertEquals(0, calls)
    }

    @Test
    fun `GIVEN player WHEN play THEN delegated and callback invoked`() {
        val w = create()
        val p = injectMockPlayer(w)
        var started = 0
        w.onVideoStarted = { started++ }
        w.play()
        verify { p.play(false) }
        assertEquals(1, started)
    }

    @Test
    fun `GIVEN player WHEN pause THEN position saved and callback invoked`() {
        val w = create()
        val p = injectMockPlayer(w)
        var paused = 0
        w.onVideoPaused = { paused++ }
        w.pause()
        verify { p.pause(false) }
        assertEquals(1, paused)
        assertEquals(777L, w.field<Long>("lastPlaybackPosition"))
        assertTrue(w.field<Boolean>("isUserPaused"))
    }

    @Test
    fun `GIVEN player WHEN seek and toggle mute THEN delegated`() {
        val w = create()
        val p = injectMockPlayer(w)
        w.seekTo(55)
        w.toggleMute()
        verify { p.seekTo(55L) }
        verify { p.toggleMute() }
    }

    @Test
    fun `GIVEN player WHEN reading public properties THEN delegated to player`() {
        val w = create()
        injectMockPlayer(w)
        assertTrue(w.isPlaying)
        assertFalse(w.isMuted)
        assertEquals(777L, w.currentPositionMs)
    }

    @Test
    fun `GIVEN player WHEN wrapper detached THEN player paused`() {
        val w = create()
        val p = injectMockPlayer(w)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(w)
        (w.parent as android.view.ViewGroup).removeView(w)
        verify { p.pause(false) }
    }

    @Test
    fun `GIVEN player WHEN released THEN player released and state reset`() {
        val w = create()
        val p = injectMockPlayer(w)
        w.release()
        verify { p.release() }
        assertEquals(CriteoVideoAdState.NotLoaded, w.state)
        assertNull(w.field<Any?>("videoPlayer"))
        assertNull(w.field<Any?>("vastAd"))
    }

    @Test
    fun `GIVEN loading WHEN released THEN load cancelled and state reset`() {
        val w = create()
        w.loadVideoAd(VASTSource.Xml(emptyVast))
        w.release()
        assertEquals(CriteoVideoAdState.NotLoaded, w.state)
        assertNull(w.field<Any?>("preloadJob"))
    }
    // endregion
}

package com.iab.omid.sampleapp.player

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import com.iab.omid.sampleapp.manager.BeaconManager
import com.iab.omid.sampleapp.manager.omid.IOMIDSessionInteractor
import com.iab.omid.sampleapp.manager.omid.OMIDSessionInteractorFactory
import com.iab.omid.sampleapp.manager.vast.VastAd
import com.iab.omid.sampleapp.player.CriteoVideoPlayer.PlaybackState
import com.iab.omid.sampleapp.player.tracking.Quartile
import com.iab.omid.sampleapp.util.CriteoLogger
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.net.URL
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class CriteoVideoPlayerTest {

    private lateinit var context: Context
    private lateinit var view: CriteoVideoPlayer
    private lateinit var exo: ExoPlayer
    private lateinit var beacons: BeaconManager
    private lateinit var omid: IOMIDSessionInteractor

    private val trackingEvents = listOf(
        "start", "firstQuartile", "midpoint", "thirdQuartile", "complete",
        "mute", "unmute", "pause", "resume"
    ).associateWith { URL("https://example.com/tracking/$it") }

    private val ad = VastAd(
        impressionUrls = listOf(URL("https://example.com/imp")),
        trackingEvents = trackingEvents,
        clickTrackingUrls = listOf(URL("https://example.com/click-track"))
    )

    @Before
    fun setUp() {
        CriteoLogger.minimumLevel = CriteoLogger.Level.CRITICAL
        context = ApplicationProvider.getApplicationContext()
        view = CriteoVideoPlayer(context)
        exo = mockk(relaxed = true)
        beacons = mockk(relaxed = true)
        omid = mockk(relaxed = true)
        mockkObject(OMIDSessionInteractorFactory)
        every { OMIDSessionInteractorFactory.create(any(), any(), any(), any(), any()) } returns omid
    }

    @After
    fun tearDown() {
        view.release()
        unmockkAll()
        CriteoLogger.minimumLevel = CriteoLogger.Level.DEBUG
    }

    // region helpers
    private fun <T> CriteoVideoPlayer.field(name: String): T {
        val f = CriteoVideoPlayer::class.java.getDeclaredField(name)
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return f.get(this) as T
    }

    private fun CriteoVideoPlayer.setField(name: String, value: Any?) {
        val f = CriteoVideoPlayer::class.java.getDeclaredField(name)
        f.isAccessible = true
        f.set(this, value)
    }

    private fun injectMockPlayer() = view.setField("player", exo)

    private fun configureWithAd() {
        view.setBeaconManager(beacons)
        view.setVastAd(ad)
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)
    // endregion

    // region initial state / simple accessors
    @Test
    fun `GIVEN new player WHEN reading state THEN idle muted and unknown quartile`() {
        val s = view.state.value
        assertEquals(PlaybackState.IDLE, s.playbackState)
        assertTrue(s.isMuted)
        assertEquals(Quartile.UNKNOWN, s.quartile)
    }

    @Test
    fun `GIVEN no player WHEN reading position and duration THEN both zero`() {
        assertEquals(0L, view.currentPositionMs)
        assertEquals(0L, view.durationMs)
    }

    @Test
    fun `GIVEN player WHEN reading position and duration THEN delegated to exoplayer`() {
        injectMockPlayer()
        every { exo.currentPosition } returns 1234L
        every { exo.duration } returns 9999L
        assertEquals(1234L, view.currentPositionMs)
        assertEquals(9999L, view.durationMs)
    }

    @Test
    fun `GIVEN player state defaults WHEN copying THEN data class semantics hold`() {
        val s = CriteoVideoPlayer.PlayerState()
        assertEquals(s, CriteoVideoPlayer.PlayerState())
        assertEquals(PlaybackState.ERROR, s.copy(playbackState = PlaybackState.ERROR).playbackState)
        assertEquals(6, PlaybackState.values().size)
        assertEquals(PlaybackState.LOADING, PlaybackState.valueOf("LOADING"))
    }

    @Test
    fun `GIVEN new view WHEN inspecting children THEN contains player view and controls`() {
        assertEquals(3, view.childCount)
        assertTrue(view.getChildAt(0) is PlayerView)
    }
    // endregion

    // region listener callbacks
    @Test
    fun `GIVEN volume zero WHEN onVolumeChanged THEN muted`() {
        view.onVolumeChanged(1f)
        assertFalse(view.state.value.isMuted)
        view.onVolumeChanged(0f)
        assertTrue(view.state.value.isMuted)
    }

    @Test
    fun `GIVEN playback exception WHEN onPlayerError THEN state is error`() {
        view.onPlayerError(PlaybackException("x", null, PlaybackException.ERROR_CODE_UNSPECIFIED))
        assertEquals(PlaybackState.ERROR, view.state.value.playbackState)
    }

    @Test
    fun `GIVEN playing WHEN onIsPlayingChanged true THEN state playing`() {
        view.onIsPlayingChanged(true)
        assertEquals(PlaybackState.PLAYING, view.state.value.playbackState)
    }

    @Test
    fun `GIVEN idle WHEN onIsPlayingChanged false THEN state paused`() {
        view.onIsPlayingChanged(false)
        assertEquals(PlaybackState.PAUSED, view.state.value.playbackState)
    }

    @Test
    fun `GIVEN loading WHEN onIsPlayingChanged false THEN stays loading`() {
        injectMockPlayer()
        view.onPlaybackStateChanged(Player.STATE_BUFFERING)
        view.onIsPlayingChanged(false)
        assertEquals(PlaybackState.LOADING, view.state.value.playbackState)
    }

    @Test
    fun `GIVEN finished WHEN onIsPlayingChanged false THEN stays finished`() {
        injectMockPlayer()
        view.onPlaybackStateChanged(Player.STATE_ENDED)
        view.onIsPlayingChanged(false)
        assertEquals(PlaybackState.FINISHED, view.state.value.playbackState)
    }

    @Test
    fun `GIVEN no player WHEN onPlaybackStateChanged THEN state unchanged`() {
        view.onPlaybackStateChanged(Player.STATE_BUFFERING)
        assertEquals(PlaybackState.IDLE, view.state.value.playbackState)
    }

    @Test
    fun `GIVEN player WHEN buffering THEN state loading`() {
        injectMockPlayer()
        view.onPlaybackStateChanged(Player.STATE_BUFFERING)
        assertEquals(PlaybackState.LOADING, view.state.value.playbackState)
    }

    @Test
    fun `GIVEN player WHEN idle callback THEN state idle`() {
        injectMockPlayer()
        view.onPlaybackStateChanged(Player.STATE_BUFFERING)
        view.onPlaybackStateChanged(Player.STATE_IDLE)
        assertEquals(PlaybackState.IDLE, view.state.value.playbackState)
    }

    @Test
    fun `GIVEN ready and playing WHEN onPlaybackStateChanged THEN state playing`() {
        injectMockPlayer()
        every { exo.isPlaying } returns true
        view.onPlaybackStateChanged(Player.STATE_READY)
        assertEquals(PlaybackState.PLAYING, view.state.value.playbackState)
    }

    @Test
    fun `GIVEN ready and not playing WHEN onPlaybackStateChanged THEN state paused`() {
        injectMockPlayer()
        every { exo.isPlaying } returns false
        view.onPlaybackStateChanged(Player.STATE_READY)
        assertEquals(PlaybackState.PAUSED, view.state.value.playbackState)
    }

    @Test
    fun `GIVEN ad and player WHEN playback ends THEN complete beacon and omid complete fired once`() {
        configureWithAd()
        injectMockPlayer()
        view.onPlaybackStateChanged(Player.STATE_ENDED)

        assertEquals(PlaybackState.FINISHED, view.state.value.playbackState)
        assertEquals(Quartile.COMPLETE, view.state.value.quartile)
        verify(exactly = 1) { omid.fireComplete() }
        verify(exactly = 1) { beacons.fireBeacon(trackingEvents.getValue("complete"), "complete") }

        view.onPlaybackStateChanged(Player.STATE_ENDED)
        verify(exactly = 1) { omid.fireComplete() }
    }

    @Test
    fun `GIVEN looped media item WHEN repeat transition THEN complete fired once`() {
        configureWithAd()
        view.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
        view.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)

        assertEquals(Quartile.COMPLETE, view.state.value.quartile)
        verify(exactly = 1) { omid.fireComplete() }
        verify(exactly = 1) { beacons.fireBeacon(trackingEvents.getValue("complete"), "complete") }
    }

    @Test
    fun `GIVEN non repeat transition WHEN onMediaItemTransition THEN nothing fired`() {
        configureWithAd()
        view.onMediaItemTransition(MediaItem.EMPTY, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        assertEquals(Quartile.UNKNOWN, view.state.value.quartile)
        verify(exactly = 0) { omid.fireComplete() }
    }

    @Test
    fun `GIVEN ad without complete url WHEN playback ends THEN no beacon fired`() {
        view.setBeaconManager(beacons)
        view.setVastAd(VastAd())
        view.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
        verify(exactly = 0) { beacons.fireBeacon(any(), any()) }
        verify(exactly = 1) { omid.fireComplete() }
    }
    // endregion

    // region omid session
    @Test
    fun `GIVEN vast ad WHEN setVastAd THEN session started and obstructions registered`() {
        view.setVastAd(ad.copy(vendorKey = "v", verificationParameters = "p", verificationScriptUrl = URL("https://example.com/s.js")))

        verify { OMIDSessionInteractorFactory.create(any(), view, "v", "https://example.com/s.js", "p") }
        verify(exactly = 3) { omid.addMediaControlsObstruction(any()) }
        verify(exactly = 1) { omid.startSession() }
    }

    @Test
    fun `GIVEN ad without verification WHEN setVastAd THEN empty strings passed`() {
        view.setVastAd(VastAd())
        verify { OMIDSessionInteractorFactory.create(any(), view, "", "", "") }
    }

    @Test
    fun `GIVEN existing session WHEN setVastAd again THEN previous session stopped`() {
        view.setVastAd(ad)
        view.setVastAd(ad)
        verify(exactly = 1) { omid.stopSession() }
        verify(exactly = 2) { omid.startSession() }
    }

    @Test
    fun `GIVEN session WHEN release THEN session stopped`() {
        view.setVastAd(ad)
        view.release()
        verify(exactly = 1) { omid.stopSession() }
    }

    @Test
    fun `GIVEN player WHEN release THEN exoplayer released and state reset`() {
        injectMockPlayer()
        view.onIsPlayingChanged(true)
        view.release()

        verify { exo.removeListener(view) }
        verify { exo.release() }
        assertEquals(PlaybackState.IDLE, view.state.value.playbackState)
        assertEquals(Quartile.UNKNOWN, view.state.value.quartile)
    }
    // endregion

    // region play / pause / mute / seek
    @Test
    fun `GIVEN no player WHEN controlling playback THEN no crash`() {
        view.play(true)
        view.pause(true)
        view.togglePlayPause(true)
        view.toggleMute()
        view.seekTo(100)
    }

    @Test
    fun `GIVEN unknown quartile WHEN play THEN resume not reported`() {
        configureWithAd()
        injectMockPlayer()
        view.play(fromUserInteraction = true)
        verify { exo.play() }
        verify(exactly = 0) { omid.fireResume() }
        verify(exactly = 0) { beacons.fireBeacon(any(), any()) }
    }

    @Test
    fun `GIVEN started playback WHEN user plays THEN omid and resume beacon fired`() {
        configureWithAd()
        injectMockPlayer()
        view.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
        view.play(fromUserInteraction = true)
        verify { omid.fireResume() }
        verify { beacons.fireBeacon(trackingEvents.getValue("resume"), "resume") }
    }

    @Test
    fun `GIVEN started playback WHEN programmatic play THEN no resume beacon`() {
        configureWithAd()
        injectMockPlayer()
        view.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
        view.play(fromUserInteraction = false)
        verify { omid.fireResume() }
        verify(exactly = 0) { beacons.fireBeacon(trackingEvents.getValue("resume"), "resume") }
    }

    @Test
    fun `GIVEN not playing WHEN pause THEN ignored`() {
        configureWithAd()
        injectMockPlayer()
        every { exo.isPlaying } returns false
        view.pause(true)
        verify(exactly = 0) { exo.pause() }
        verify(exactly = 0) { omid.firePause() }
    }

    @Test
    fun `GIVEN playing WHEN user pauses THEN pause beacon fired`() {
        configureWithAd()
        injectMockPlayer()
        every { exo.isPlaying } returns true
        view.pause(fromUserInteraction = true)
        verify { exo.pause() }
        verify { omid.firePause() }
        verify { beacons.fireBeacon(trackingEvents.getValue("pause"), "pause") }
    }

    @Test
    fun `GIVEN playing WHEN programmatic pause THEN no pause beacon`() {
        configureWithAd()
        injectMockPlayer()
        every { exo.isPlaying } returns true
        view.pause(fromUserInteraction = false)
        verify { omid.firePause() }
        verify(exactly = 0) { beacons.fireBeacon(any(), any()) }
    }

    @Test
    fun `GIVEN playing WHEN togglePlayPause THEN pauses`() {
        injectMockPlayer()
        every { exo.isPlaying } returns true
        view.togglePlayPause(false)
        verify { exo.pause() }
    }

    @Test
    fun `GIVEN paused WHEN togglePlayPause THEN plays`() {
        injectMockPlayer()
        every { exo.isPlaying } returns false
        view.togglePlayPause(false)
        verify { exo.play() }
    }

    @Test
    fun `GIVEN muted WHEN toggleMute THEN unmutes and fires unmute beacon`() {
        configureWithAd()
        injectMockPlayer()
        every { exo.volume } returns 0f andThen 1f
        view.toggleMute()
        verify { exo.volume = 1f }
        verify { omid.fireVolumeChange(any()) }
        verify { beacons.fireBeacon(trackingEvents.getValue("unmute"), "unmute") }
    }

    @Test
    fun `GIVEN unmuted WHEN toggleMute THEN mutes and fires mute beacon`() {
        configureWithAd()
        injectMockPlayer()
        every { exo.volume } returns 1f andThen 0f
        view.toggleMute()
        verify { exo.volume = 0f }
        verify { beacons.fireBeacon(trackingEvents.getValue("mute"), "mute") }
    }

    @Test
    fun `GIVEN ad without mute url WHEN toggleMute THEN no beacon`() {
        view.setBeaconManager(beacons)
        view.setVastAd(VastAd())
        injectMockPlayer()
        every { exo.volume } returns 0f
        view.toggleMute()
        verify(exactly = 0) { beacons.fireBeacon(any(), any()) }
    }

    @Test
    fun `GIVEN player WHEN seekTo THEN delegated`() {
        injectMockPlayer()
        view.seekTo(4200)
        verify { exo.seekTo(4200L) }
    }
    // endregion

    // region controls
    @Test
    fun `GIVEN player WHEN play pause button clicked THEN toggles playback with user beacon`() {
        configureWithAd()
        injectMockPlayer()
        every { exo.isPlaying } returns true
        val button: ImageButton = view.field("playPauseButton")
        button.performClick()
        verify { exo.pause() }
        verify { beacons.fireBeacon(trackingEvents.getValue("pause"), "pause") }
    }

    @Test
    fun `GIVEN player WHEN mute button clicked THEN volume toggled`() {
        configureWithAd()
        injectMockPlayer()
        every { exo.volume } returns 0f
        val button: ImageButton = view.field("muteButton")
        button.performClick()
        verify { exo.volume = 1f }
    }

    @Test
    fun `GIVEN player WHEN cc button clicked THEN text tracks toggled`() {
        injectMockPlayer()
        every { exo.trackSelectionParameters } returns TrackSelectionParameters.Builder(context).build()
        every { exo.trackSelectionParameters = any() } just Runs
        val cc: Button = view.field("closedCaptionButton")
        cc.performClick()
        assertTrue(cc.isSelected)
        cc.performClick()
        assertFalse(cc.isSelected)
        verify(exactly = 2) { exo.trackSelectionParameters = any() }
    }

    @Test
    fun `GIVEN no player WHEN cc button clicked THEN selection still toggles`() {
        val cc: Button = view.field("closedCaptionButton")
        cc.performClick()
        assertTrue(cc.isSelected)
    }
    // endregion

    // region quartile tracking
    @Test
    fun `GIVEN playing media WHEN position advances THEN quartile events fire in order`() {
        configureWithAd()
        injectMockPlayer()
        every { exo.isPlaying } returns true
        every { exo.duration } returns 1000L
        every { exo.currentPosition } returns 100L

        view.onPlaybackStateChanged(Player.STATE_READY)
        idle(150)
        assertEquals(Quartile.START, view.state.value.quartile)
        verify { omid.fireStart(1000L, 0f) }
        verify { beacons.fireBeacon(trackingEvents.getValue("start"), "start") }

        every { exo.currentPosition } returns 300L
        idle(150)
        assertEquals(Quartile.FIRST, view.state.value.quartile)
        verify { omid.fireFirstQuartile() }

        every { exo.currentPosition } returns 600L
        idle(150)
        assertEquals(Quartile.SECOND, view.state.value.quartile)
        verify { omid.fireMidpoint() }

        every { exo.currentPosition } returns 800L
        idle(150)
        assertEquals(Quartile.THIRD, view.state.value.quartile)
        verify { omid.fireThirdQuartile() }
        verify { beacons.fireBeacon(trackingEvents.getValue("thirdQuartile"), "thirdQuartile") }
    }

    @Test
    fun `GIVEN quartile already reached WHEN position goes back THEN event not re fired`() {
        configureWithAd()
        injectMockPlayer()
        every { exo.isPlaying } returns true
        every { exo.duration } returns 1000L
        every { exo.currentPosition } returns 600L

        view.onPlaybackStateChanged(Player.STATE_READY)
        idle(150)
        every { exo.currentPosition } returns 300L
        idle(150)

        assertEquals(Quartile.SECOND, view.state.value.quartile)
        verify(exactly = 0) { omid.fireFirstQuartile() }
    }

    @Test
    fun `GIVEN unknown duration WHEN tracking loop runs THEN no quartile reported`() {
        configureWithAd()
        injectMockPlayer()
        every { exo.isPlaying } returns true
        every { exo.duration } returns 0L
        every { exo.currentPosition } returns 100L
        view.onPlaybackStateChanged(Player.STATE_READY)
        idle(250)
        assertEquals(Quartile.UNKNOWN, view.state.value.quartile)
    }

    @Test
    fun `GIVEN ready twice WHEN tracking started THEN loop only started once`() {
        configureWithAd()
        injectMockPlayer()
        every { exo.isPlaying } returns true
        every { exo.duration } returns 1000L
        every { exo.currentPosition } returns 100L
        view.onPlaybackStateChanged(Player.STATE_READY)
        view.onPlaybackStateChanged(Player.STATE_READY)
        idle(350)
        verify(exactly = 1) { omid.fireStart(any(), any()) }
    }
    // endregion

    // region load + click
    private fun loadReal(subtitle: Uri? = null, playWhenReady: Boolean = false, muted: Boolean = true) {
        configureWithAd()
        view.load(Uri.parse("file:///nonexistent/video.mp4"), subtitle, playWhenReady, muted)
    }

    @Test
    fun `GIVEN uri WHEN load THEN state loading and impressions fired`() {
        loadReal()
        assertEquals(PlaybackState.LOADING, view.state.value.playbackState)
        assertNotNull(view.field<ExoPlayer?>("player"))
        verify { omid.fireAdLoaded() }
        verify { omid.fireImpression() }
        verify { beacons.fireImpressionBeacons(ad) }
        val cc: Button = view.field("closedCaptionButton")
        assertEquals(View.GONE, cc.visibility)
    }

    @Test
    fun `GIVEN muted flag WHEN load THEN exoplayer volume follows flag`() {
        loadReal(muted = false)
        assertFalse(view.state.value.isMuted)
        assertEquals(1f, view.field<ExoPlayer>("player").volume, 0f)
    }

    @Test
    fun `GIVEN subtitle WHEN load THEN cc button visible and selected`() {
        loadReal(subtitle = Uri.parse("file:///nonexistent/cc.vtt"))
        val cc: Button = view.field("closedCaptionButton")
        assertEquals(View.VISIBLE, cc.visibility)
        assertTrue(cc.isSelected)
    }

    @Test
    fun `GIVEN loaded player WHEN load again THEN previous player released`() {
        loadReal()
        val first = view.field<ExoPlayer>("player")
        view.load(Uri.parse("file:///nonexistent/other.mp4"))
        val second = view.field<ExoPlayer>("player")
        assertTrue(first !== second)
        assertEquals(PlaybackState.LOADING, view.state.value.playbackState)
    }

    @Test
    fun `GIVEN click through url WHEN video tapped THEN click beacons fired and browser opened`() {
        val withClick = ad.copy(clickThroughUrl = URL("https://example.com/landing"))
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val local = CriteoVideoPlayer(activity)
        local.setBeaconManager(beacons)
        local.setVastAd(withClick)
        local.load(Uri.parse("file:///nonexistent/video.mp4"), playWhenReady = false)

        local.field<PlayerView>("playerView").performClick()

        verify { omid.fireClickInteraction() }
        verify { beacons.fireClickTrackingBeacons(withClick) }
        val started = shadowOf(activity).nextStartedActivity
        local.release()
        assertEquals("https://example.com/landing", started.data.toString())
    }

    @Test
    fun `GIVEN no click through url WHEN video tapped THEN playback toggled`() {
        loadReal()
        val real = view.field<ExoPlayer>("player")
        assertFalse(real.isPlaying)
        view.field<PlayerView>("playerView").performClick()
        verify { omid.fireClickInteraction() }
        verify(exactly = 0) { beacons.fireClickTrackingBeacons(any()) }
        assertTrue(real.playWhenReady)
    }

    @Test
    fun `GIVEN no ad WHEN video tapped THEN only omid click reported`() {
        view.load(Uri.parse("file:///nonexistent/video.mp4"), playWhenReady = false)
        view.field<PlayerView>("playerView").performClick()
        assertNull(shadowOf(context as android.app.Application).nextStartedActivity)
    }

    @Test
    fun `GIVEN activity launch failure WHEN video tapped THEN error swallowed`() {
        val withClick = ad.copy(clickThroughUrl = URL("https://example.com/landing"))
        val throwing = object : android.content.ContextWrapper(context) {
            override fun startActivity(intent: android.content.Intent?) = throw IllegalStateException("boom")
        }
        val local = CriteoVideoPlayer(throwing)
        local.setBeaconManager(beacons)
        local.setVastAd(withClick)
        local.load(Uri.parse("file:///nonexistent/video.mp4"), playWhenReady = false)
        local.field<PlayerView>("playerView").performClick()
        verify { beacons.fireClickTrackingBeacons(withClick) }
        local.release()
    }
    // endregion

    // region lifecycle
    @Test
    fun `GIVEN playing player WHEN detached from window THEN paused without beacon`() {
        configureWithAd()
        injectMockPlayer()
        every { exo.isPlaying } returns true
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(view)
        shadowOf(Looper.getMainLooper()).idle()

        (view.parent as android.view.ViewGroup).removeView(view)

        verify { exo.pause() }
        verify { omid.firePause() }
        verify(exactly = 0) { beacons.fireBeacon(any(), any()) }
    }
    // endregion
}

package com.iab.omid.sampleapp;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.PlayerView;


import com.iab.omid.library.criteo.adsession.AdEvents;
import com.iab.omid.library.criteo.adsession.AdSession;
import com.iab.omid.library.criteo.adsession.CreativeType;
import com.iab.omid.library.criteo.adsession.media.MediaEvents;
import com.iab.omid.library.criteo.adsession.media.Position;
import com.iab.omid.library.criteo.adsession.media.VastProperties;
import com.iab.omid.sampleapp.util.AdSessionUtil;
import com.iab.omid.sampleapp.util.Util;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;

/**
 * A fragment representing a single ad detail screen.
 *

 *
 * This sample shows loading a Native ad, passing a url to the Omid js, and marking the impression
 */
public class VideoAdNativeActivity extends Activity implements Player.Listener, View.OnClickListener {

	private static final String CUSTOM_REFERENCE_DATA = "{ \"birthday\":-310957844000, \"user\":\"me\" }";
	private static final String VIDEO_URL = "asset:///video_ad_asset.mp4";
	private static final int PLAYER_VOLUME = 1;

	private AdSession adSession;
	private MediaEvents mediaEvents;
	private AdEvents adEvents;

	private static final int PROGRESS_INTERVAL_MS = 100;
	private final Handler handler = new Handler(Looper.getMainLooper());
	private Quartile lastSentQuartile = Quartile.UNKNOWN;

	private boolean complete;
	private boolean loaded;

	private PlayerView playerView;
	private ExoPlayer player;
//	private DefaultTrackSelector trackSelector;
	private TextView muteTextView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.video_ad_native_detail);

		playerView = findViewById(R.id.videoView);
		TextView userInteractionTextView = findViewById(R.id.userIntercationTextView);
		muteTextView = findViewById(R.id.muteTextView);

		initializePlayer2();

		playerView.requestFocus();

		userInteractionTextView.setOnClickListener(this);
		muteTextView.setOnClickListener(this);

		try {
			adSession = AdSessionUtil.getNativeAdSession(this, CUSTOM_REFERENCE_DATA, CreativeType.VIDEO);
		} catch (MalformedURLException e) {
			throw new UnsupportedOperationException(e);
		}
		mediaEvents = MediaEvents.createMediaEvents(adSession);
		adEvents = AdEvents.createAdEvents(adSession);
		adSession.registerAdView(playerView);
		adSession.start();
	}

	private void initializePlayer2() {
		// Set up the factory for media sources, passing the ads loader and ad view providers.
//		DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);

//		MediaSource.Factory mediaSourceFactory =
//				new DefaultMediaSourceFactory(dataSourceFactory)
//						.setLocalAdInsertionComponents(unusedAdTagUri -> adsLoader, playerView);

		// Create an ExoPlayer and set it as the player for content and ads.
		player = new ExoPlayer.Builder(this)
//				.setMediaSourceFactory(mediaSourceFactory)
				.build();
//		adsLoader.setPlayer(player);

		// Create the MediaItem to play, specifying the content URI and ad tag URI.
//		Uri contentUri = Uri.parse(getString(R.string.content_url));
//		Uri adTagUri = Uri.parse(getString(R.string.ad_tag_url));
		MediaItem mediaItem =
				new MediaItem.Builder()
						.setUri(Uri.parse(VIDEO_URL))
//						.setAdsConfiguration(new MediaItem.AdsConfiguration.Builder(Uri.parse(VIDEO_URL)).build())
						.build();

		// Prepare the content and ad to be played with the SimpleExoPlayer.
		player.setMediaItem(mediaItem);
		player.addListener(this);
		player.setVolume(PLAYER_VOLUME);
		playerView.setPlayer(player);
		player.setPlayWhenReady(true);
		player.prepare();
	}


//	private void initializePlayer(@NonNull Context context) {
//
//// 1, Setup selector
//		DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
//		TrackSelection.Factory mediaTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
//		trackSelector = new DefaultTrackSelector(mediaTrackSelectionFactory);
//
//// 2. Create the player
//		player = ExoPlayerFactory.newSimpleInstance(context, trackSelector);
//		player.addListener(this);
//
//		player.setVolume(PLAYER_VOLUME);
//
//		playerView.setPlayer(player);
//		player.setPlayWhenReady(true);
//
//		DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(context,
//				com.google.android.exoplayer2.util.Util.getUserAgent(context, "com.iab.omid"), bandwidthMeter);
//		ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
//
//		MediaSource mediaSource = new ExtractorMediaSource(Uri.parse(VIDEO_URL),
//				dataSourceFactory, extractorsFactory, null, null);
//
//// 3. Prepare the player with the source.
//		player.prepare(mediaSource);
//	}

	@Override
	public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
		switch (playbackState) {
			case ExoPlayer.STATE_READY:
				onStateReady();
				break;
			case ExoPlayer.STATE_ENDED:
				onStateEnded();
				break;
			case ExoPlayer.STATE_IDLE:
			case ExoPlayer.STATE_BUFFERING:
				// TODO?
				break;
			default:
				throw new IllegalStateException("Unknown playbackState: " + playbackState);
		}
	}

	private void onStateReady() {
		if (!loaded) {
			VastProperties vastProperties = VastProperties.createVastPropertiesForNonSkippableMedia(false, Position.STANDALONE);
			adEvents.loaded(vastProperties);

			loaded = true;
		}

		postProgress();
	}

	private void onStateEnded() {
		complete = true;
		mediaEvents.complete();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		adSession.finish();
		adSession = null;

		handler.removeCallbacksAndMessages(null);
		releasePlayer();
	}

	private void releasePlayer() {
		if (player != null) {
			player.release();
			player = null;
//			trackSelector = null;
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.userIntercationTextView:
				Util.handleUserInteraction(this, mediaEvents);
				break;
			case R.id.muteTextView:
				Util.toggleMute(mediaEvents,player,muteTextView);
				break;
		}
	}

	private boolean onProgressIsMediaPlaying = true;
	private void updatePlayPause() {
		final boolean playing = player.getPlayWhenReady() && (player.getPlaybackState() == ExoPlayer.STATE_READY);
		if (playing != this.onProgressIsMediaPlaying) {
			if (playing) {
				mediaEvents.resume();
			} else {
				mediaEvents.pause();
			}

			this.onProgressIsMediaPlaying = playing;
		}
	}

	private static class ProgressRunnable implements Runnable {
		private final WeakReference<VideoAdNativeActivity> videoAdNativeFragmentWeakReference;

		ProgressRunnable(VideoAdNativeActivity videoAdNativeActivity) {
			this.videoAdNativeFragmentWeakReference = new WeakReference<>(videoAdNativeActivity);
		}

		@Override
		public void run() {
			VideoAdNativeActivity videoAdNativeActivity = videoAdNativeFragmentWeakReference.get();
			if (videoAdNativeActivity == null) {
				return;
			}

			videoAdNativeActivity.onProgress();
		}
	}

	private void onProgress() {
		if (adSession == null) {
			return;
		}

		if (complete) {
			return;
		}

		updateQuartile();
		updatePlayPause();
		postProgress();
	}

	private void postProgress() {
		handler.removeCallbacks(progressRunnable);
		handler.postDelayed(progressRunnable, PROGRESS_INTERVAL_MS);
	}
	private final ProgressRunnable progressRunnable = new ProgressRunnable(this);

	private void updateQuartile() {
		final long duration = player.getDuration();
		final long currentPosition = player.getCurrentPosition();

		if (duration != 0) {
			final Quartile currentQuartile = getQuartile(currentPosition, duration);

			// Don't send old quartile stats that we have either already sent, or passed.
			if (currentQuartile != lastSentQuartile && currentQuartile.ordinal() > lastSentQuartile.ordinal()) {
				sendQuartile(currentQuartile);
				lastSentQuartile = currentQuartile;
			}
		}
	}

	private void sendQuartile(Quartile quartile) {
		switch (quartile) {
			case START:
				mediaEvents.start(player.getDuration(), PLAYER_VOLUME);
				adEvents.impressionOccurred();
				break;
			case FIRST:
				mediaEvents.firstQuartile();
				break;
			case SECOND:
				mediaEvents.midpoint();
				break;
			case THIRD:
				mediaEvents.thirdQuartile();
				break;
			case UNKNOWN:
			default:
				break;
		}
	}

	// 3. firing beacons
	public enum Quartile {
		UNKNOWN,
		START,
		FIRST,
		SECOND,
		THIRD,
	}

	private static Quartile getQuartile(long position, long duration) {
		final double completionFraction = position / (double) duration;
		if (lessThan(completionFraction, 0)) {
			return Quartile.UNKNOWN;
		}

		if (lessThan(completionFraction, 0.25)) {
			return Quartile.START;
		}

		if (lessThan(completionFraction, 0.5)) {
			return Quartile.FIRST;
		}

		if (lessThan(completionFraction, 0.75)) {
			return Quartile.SECOND;
		}

		// We report Quartile.THIRD when completionFraction > 1 on purpose
		// since track might technically report elapsed time after it's completion
		// and if Quartile.THIRD hasn't been reported already, it will be lost
		return Quartile.THIRD;
	}

	private static boolean lessThan(double a, double b){
		return b - a > .000001;
	}
}

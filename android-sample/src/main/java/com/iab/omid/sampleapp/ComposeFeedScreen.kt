package com.iab.omid.sampleapp

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iab.omid.sampleapp.player.CriteoVideoAd
import com.iab.omid.sampleapp.player.CriteoVideoAdConfiguration
import com.iab.omid.sampleapp.player.CriteoVideoAdLogCategory
import com.iab.omid.sampleapp.player.CriteoVideoAdWrapper

private const val TAG = "ComposeFeedScreen"

// ── Feed item model ────────────────────────────────────────────────

private sealed class FeedItem {
    data class Content(val title: String, val body: String) : FeedItem()
    data class VideoAd(val vastUrl: String) : FeedItem()
}

// ── Public entry-point ─────────────────────────────────────────────

/**
 * A Compose-based feed screen that mirrors [FeedVideoPlayerFragment]'s behaviour
 * using a [LazyColumn] instead of a RecyclerView.
 *
 * @param vastUrl    VAST tag URL for the video ad.
 * @param isActive   `true` when the hosting Fragment/Activity is in the resumed state.
 *                   When `false` the video is paused regardless of scroll position.
 */
@Composable
fun ComposeFeedScreen(
    vastUrl: String,
    isActive: Boolean = true,
) {
    val listState = rememberLazyListState()
    val items = remember { buildFeedItems(vastUrl) }

    // Track whether the video ad item is ≥ 50 % visible.
    var videoVisiblePercent by remember { mutableStateOf(0f) }
    val isVideoVisible = isActive && videoVisiblePercent >= 50f

    // Create the wrapper at *screen* scope so it survives LazyColumn item disposal.
    // The player is only released when the screen itself leaves composition
    // (i.e. when the hosting fragment is destroyed).
    val context = LocalContext.current
    val videoWrapper = remember {
        CriteoVideoAdWrapper.fromUrl(
            context = context,
            vastURL = vastUrl,
            configuration = CriteoVideoAdConfiguration(
                autoLoad = false,
                startsMuted = true
            )
        ).apply {
            enableLogs = setOf(
                CriteoVideoAdLogCategory.VIDEO,
                CriteoVideoAdLogCategory.OMID
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose { videoWrapper.release() }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        itemsIndexed(items, key = { index, _ -> index }) { index, item ->
            when (item) {
                is FeedItem.Content -> ContentCard(item)
                is FeedItem.VideoAd -> VideoAdCard(
                    wrapper = videoWrapper,
                    isVisible = isVideoVisible,
                    onVisibilityChanged = { percent -> videoVisiblePercent = percent }
                )
            }
        }
    }
}

// ── Content card ───────────────────────────────────────────────────

@Composable
private fun ContentCard(item: FeedItem.Content) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = item.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.body,
            fontSize = 18.sp,
            color = Color(0xFF666666),
            minLines = 3,
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
    }
}

// ── Video-ad card ──────────────────────────────────────────────────

@Composable
private fun VideoAdCard(
    wrapper: CriteoVideoAdWrapper,
    isVisible: Boolean,
    onVisibilityChanged: (Float) -> Unit,
) {
    val rootView = LocalView.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp)
    ) {
        // "Sponsored" label
        Text(
            text = "SPONSORED",
            fontSize = 12.sp,
            color = Color(0xFF999999),
            letterSpacing = 1.sp,
        )

        Spacer(Modifier.height(8.dp))

        // Video container — visibility detection happens here
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .onGloballyPositioned { coordinates ->
                    val itemBounds = coordinates.boundsInWindow()
                    val itemHeight = itemBounds.height
                    if (itemHeight <= 0f) {
                        onVisibilityChanged(0f)
                        return@onGloballyPositioned
                    }

                    // Window bounds represent the visible viewport
                    val windowHeight = rootView.height.toFloat()
                    val visibleTop = itemBounds.top.coerceAtLeast(0f)
                    val visibleBottom = itemBounds.bottom.coerceAtMost(windowHeight)
                    val visibleHeight = (visibleBottom - visibleTop).coerceAtLeast(0f)

                    val percent = (visibleHeight / itemHeight) * 100f
                    onVisibilityChanged(percent)
                }
        ) {
            CriteoVideoAd(
                wrapper = wrapper,
                isVisible = isVisible,
                onVideoLoaded = { Log.d(TAG, "Video ad loaded") },
                onVideoStarted = { Log.d(TAG, "Video ad started") },
                onVideoPaused = { Log.d(TAG, "Video ad paused") },
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
    }
}

// ── Feed data ──────────────────────────────────────────────────────

private fun buildFeedItems(vastUrl: String): List<FeedItem> = listOf(
    FeedItem.Content(
        title = "Welcome to the Feed",
        body = "This is a demo of a social media-style feed with an embedded video ad."
    ),
    FeedItem.Content(
        title = "Sample Content",
        body = "This feed contains sample content items to simulate a real-world social media feed experience."
    ),
    FeedItem.Content(
        title = "Scroll Down to See Video Ad",
        body = "Scroll down to see the video ad appear inline with other content in the feed."
    ),
    FeedItem.Content(
        title = "VAST Support",
        body = "The video player supports VAST (Video Ad Serving Template) for standardized video ad delivery."
    ),
    FeedItem.Content(
        title = "What is VAST",
        body = "VAST is an XML-based standard developed for serving video ads across different platforms and players."
    ),
    FeedItem.Content(
        title = "How It Works",
        body = "The video ad below will automatically play when at least 50% of it is visible on screen, and pause when less than 50% is visible."
    ),
    FeedItem.Content(
        title = "Beacon Tracking",
        body = "Impression, quartile, and completion beacons are automatically fired as the user watches the video the first time through. Other VAST tracking events like clicks and pause/play are also supported."
    ),
    FeedItem.Content(
        title = "All beacons should be fired only once",
        body = "Except for the interaction beacons that should be fired every time the user interacts with these actions (click, mute, unmute, resume, pause)."
    ),
    FeedItem.Content(
        title = "OMID Integration",
        body = "The video player includes Open Measurement (OMID) tracking for viewability measurement, ensuring accurate ad metrics."
    ),
    FeedItem.VideoAd(vastUrl = vastUrl),
    FeedItem.Content(
        title = "Customizable Experience",
        body = "The video player can be customized with different configurations, such as auto-load behavior and mute settings."
    ),
    FeedItem.Content(
        title = "Closed Captioning",
        body = "The player supports closed captioning if provided in the VAST response. The Player UI includes controls for enabling/disabling captions."
    ),
    FeedItem.Content(
        title = "Click Through Support",
        body = "Click-through URLs defined in the VAST response are supported, allowing users to interact with the ad and be directed to a web url."
    ),
    FeedItem.Content(
        title = "Performance Optimizations",
        body = "The video player is optimized for smooth playback and minimal impact on scrolling performance in a feed context."
    ),
    FeedItem.Content(
        title = "Logging Support",
        body = "Logging is available for video playback events and OMID interactions to aid in debugging and monitoring."
    ),
    FeedItem.Content(
        title = "Video Ad Integration",
        body = "Integrating video ads into a feed enhances user engagement and provides monetization opportunities within content streams."
    ),
    FeedItem.Content(
        title = "Thank You for Watching",
        body = "We appreciate you taking the time to explore this feed video ad example."
    ),
    FeedItem.Content(
        title = "Demo Purpose",
        body = "We hope it demonstrates some of the capabilities of our video ad player."
    ),
    FeedItem.Content(
        title = "Demo Code",
        body = "This demo app's source code is available for reference and further exploration."
    ),
)

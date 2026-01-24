package com.iab.omid.sampleapp

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iab.omid.sampleapp.MainActivity.Companion.VAST_DEMO_URL
import com.iab.omid.sampleapp.player.CriteoVideoAdConfiguration
import com.iab.omid.sampleapp.player.CriteoVideoAdLogCategory
import com.iab.omid.sampleapp.player.CriteoVideoAdWrapper

/**
 * A fragment demonstrating video ads in a feed/list context.
 *
 * This fragment displays a RecyclerView with video ad items that automatically
 * play when at least 50% visible and pause when less than 50% visible.
 * This simulates a social media feed experience where videos autoplay as
 * the user scrolls through content.
 */
class FeedVideoPlayerFragment : Fragment() {

    private var recyclerView: RecyclerView? = null
    private var adapter: FeedAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_feed_video_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = "Feed Video Ad Example"

        setupRecyclerView(view)
    }

    private fun setupRecyclerView(view: View) {
        recyclerView = view.findViewById(R.id.feedRecyclerView)
        adapter = FeedAdapter()

        recyclerView?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@FeedVideoPlayerFragment.adapter

            // Add scroll listener to handle visibility-based auto-play/pause
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    checkVideoVisibility()
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        checkVideoVisibility()
                    }
                }
            })
        }

        // Initial visibility check after layout
        view.post { checkVideoVisibility() }
    }

    /**
     * Checks visibility of the video ad item and plays/pauses based on 50% visibility threshold.
     */
    private fun checkVideoVisibility() {
        val videoAdViewHolder = adapter?.getVideoAdViewHolder() ?: return

        val visibilityPercentage = calculateVisibilityPercentage(videoAdViewHolder.itemView)
        videoAdViewHolder.onVisibilityChanged(visibilityPercentage >= 50)
    }

    /**
     * Calculates what percentage of a view is visible within the RecyclerView bounds.
     *
     * @param view The view to check visibility for.
     * @return Visibility percentage (0-100).
     */
    private fun calculateVisibilityPercentage(view: View): Int {
        val recyclerBounds = Rect()
        recyclerView?.getGlobalVisibleRect(recyclerBounds) ?: return 0

        val viewBounds = Rect()
        view.getGlobalVisibleRect(viewBounds)

        if (viewBounds.isEmpty || view.height == 0) return 0

        val visibleHeight = minOf(viewBounds.bottom, recyclerBounds.bottom) -
                maxOf(viewBounds.top, recyclerBounds.top)

        return if (visibleHeight > 0) {
            (visibleHeight * 100) / view.height
        } else {
            0
        }
    }

    override fun onPause() {
        super.onPause()
        // Pause all videos when fragment is paused
        adapter?.pauseAllVideos()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter?.cleanupVideos()
        recyclerView = null
        adapter = null
    }

    companion object {
        private const val TAG = "FeedVideoPlayerFragment"

        fun newInstance(): FeedVideoPlayerFragment {
            return FeedVideoPlayerFragment()
        }
    }

    /**
     * Sealed class representing different types of feed items.
     */
    private sealed class FeedItem {
        data class Content(
            val title: String,
            val body: String
        ) : FeedItem()

        data class VideoAd(
            val vastUrl: String
        ) : FeedItem()
    }

    /**
     * RecyclerView adapter for feed items with embedded video ads.
     */
    private inner class FeedAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val VIEW_TYPE_CONTENT = 0
        private val VIEW_TYPE_VIDEO_AD = 1

        private val items: List<FeedItem> = listOf(
            FeedItem.Content(
                title = "Welcome to the Feed",
                body = "This is a demo of a social media-style feed with an embedded video ad. Scroll down to see the video ad appear inline with other content."
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
                title = "How It Works",
                body = "The video ad below will automatically play when at least 50% of it is visible on screen, and pause when less than 50% is visible."
            ),
            FeedItem.Content(
                title = "Beacon Tracking",
                body = "Impression, quartile, and completion beacons are automatically fired as the user watches the video. Other VAST tracking events like clicks and pause/play are also supported."
            ),
            FeedItem.Content(
                title = "OMID Integration",
                body = "The video player includes Open Measurement (OMID) tracking for viewability measurement, ensuring accurate ad metrics."
            ),
            FeedItem.VideoAd(vastUrl = VAST_DEMO_URL),
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
                title = "Thank You",
                body = "Thank you for trying out this feed video ad example! We hope it demonstrates the capabilities of our video ad player."
            )
        )

        private var videoAdViewHolder: VideoAdViewHolder? = null

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is FeedItem.Content -> VIEW_TYPE_CONTENT
                is FeedItem.VideoAd -> VIEW_TYPE_VIDEO_AD
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                VIEW_TYPE_VIDEO_AD -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_feed_video, parent, false)
                    VideoAdViewHolder(view)
                }
                else -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_feed_content, parent, false)
                    ContentViewHolder(view)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is FeedItem.Content -> (holder as ContentViewHolder).bind(item)
                is FeedItem.VideoAd -> {
                    (holder as VideoAdViewHolder).bind(item)
                    videoAdViewHolder = holder
                }
            }
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            super.onViewRecycled(holder)
            if (holder is VideoAdViewHolder) {
                // Don't cleanup - we want to preserve the video state
                // Just pause the video when recycled
                holder.pause()
                videoAdViewHolder = null
            }
        }

        override fun getItemCount(): Int = items.size

        fun pauseAllVideos() {
            videoAdViewHolder?.pause()
        }

        fun cleanupVideos() {
            videoAdViewHolder?.cleanup()
            videoAdViewHolder = null
        }

        fun getVideoAdViewHolder(): VideoAdViewHolder? = videoAdViewHolder

        /**
         * ViewHolder for regular content items.
         */
        inner class ContentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val titleText: TextView = itemView.findViewById(R.id.contentTitle)
            private val bodyText: TextView = itemView.findViewById(R.id.contentBody)

            fun bind(item: FeedItem.Content) {
                titleText.text = item.title
                bodyText.text = item.body
            }
        }

        /**
         * ViewHolder for video ad items.
         */
        inner class VideoAdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val videoContainer: FrameLayout = itemView.findViewById(R.id.videoAdContainer)

            private var videoAdWrapper: CriteoVideoAdWrapper? = null
            private var isVideoVisible = false
            private var currentVastUrl: String? = null

            fun bind(item: FeedItem.VideoAd) {
                // Only create a new wrapper if the VAST URL changed or wrapper doesn't exist
                if (videoAdWrapper != null && currentVastUrl == item.vastUrl) {
                    return
                }

                // Clean up previous video if switching to a different ad
                cleanup()
                currentVastUrl = item.vastUrl

                // Create new video wrapper
                val config = CriteoVideoAdConfiguration(
                    autoLoad = true,
                    startsMuted = true // Muted autoplay for feed context
                )

                videoAdWrapper = CriteoVideoAdWrapper.fromUrl(
                    context = itemView.context,
                    vastURL = item.vastUrl,
                    configuration = config
                )

                // Set 16:9 aspect ratio based on container width
                videoContainer.post {
                    val width = videoContainer.width
                    val height = (width * 9.0) / 16.0
                    videoAdWrapper?.layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        height.toInt()
                    )
                }

                // Enable logging for debugging
                videoAdWrapper?.enableLogs = setOf(
                    CriteoVideoAdLogCategory.VIDEO,
                    CriteoVideoAdLogCategory.OMID
                )

                videoAdWrapper?.onVideoLoaded = {
                    Log.d(TAG, "Video ad loaded")
                    // Check visibility after video is loaded
                    if (isVideoVisible) {
                        videoAdWrapper?.play()
                    }
                }

                videoAdWrapper?.onVideoStarted = {
                    Log.d(TAG, "▶️ Video ad started")
                }

                videoAdWrapper?.onVideoPaused = {
                    Log.d(TAG, "⏸️ Video ad paused")
                }

                videoContainer.addView(videoAdWrapper)
            }

            /**
             * Called when visibility state changes based on scroll position.
             *
             * @param isVisible True if at least 50% of the video is visible.
             */
            fun onVisibilityChanged(isVisible: Boolean) {
                if (isVideoVisible == isVisible) return

                isVideoVisible = isVisible

                if (isVisible) {
                    videoAdWrapper?.play()
                    Log.d(TAG, "▶️ Auto-play triggered")
                } else {
                    videoAdWrapper?.pause()
                    Log.d(TAG, "⏸️ Auto-pause triggered")
                }
            }

            fun pause() {
                videoAdWrapper?.pause()
            }

            fun cleanup() {
                videoContainer.removeAllViews()
                videoAdWrapper = null
                isVideoVisible = false
                currentVastUrl = null
            }
        }
    }
}


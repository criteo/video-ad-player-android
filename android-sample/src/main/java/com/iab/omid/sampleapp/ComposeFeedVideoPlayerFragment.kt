package com.iab.omid.sampleapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.iab.omid.sampleapp.MainActivity.Companion.VAST_DEMO_URL_SINGLE_MEDIA

/**
 * Fragment that hosts the Compose-based feed screen via [ComposeView].
 *
 * This is the Compose equivalent of [FeedVideoPlayerFragment], using a
 * [LazyColumn][androidx.compose.foundation.lazy.LazyColumn] instead of a RecyclerView
 * for the feed layout.
 */
class ComposeFeedVideoPlayerFragment : Fragment() {

    /** Tracks whether the fragment is in the resumed state so the Compose screen can pause video. */
    private var isFragmentResumed by mutableStateOf(false)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // Dispose the composition when the Fragment's view lifecycle is destroyed.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                val vastUrl = arguments?.getString("vast_url") ?: VAST_DEMO_URL_SINGLE_MEDIA
                ComposeFeedScreen(
                    vastUrl = vastUrl,
                    isActive = isFragmentResumed,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title =
            "Compose Feed Video Ad"
    }

    override fun onResume() {
        super.onResume()
        isFragmentResumed = true
    }

    override fun onPause() {
        super.onPause()
        isFragmentResumed = false
    }
}

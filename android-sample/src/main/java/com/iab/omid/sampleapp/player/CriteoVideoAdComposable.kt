package com.iab.omid.sampleapp.player

import android.widget.FrameLayout
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Jetpack Compose wrapper around [CriteoVideoAdWrapper].
 *
 * Hosts the existing View-based video ad player inside Compose via [AndroidView].
 * Visibility-based auto-play/pause is driven by the [isVisible] parameter — the
 * parent composable is responsible for computing whether ≥ 50 % of this item is
 * on-screen and passing the result here.
 *
 * @param vastUrl          VAST ad tag URL to load.
 * @param modifier         Standard Compose modifier (a 16:9 aspect ratio is applied by default).
 * @param isVisible        Whether the video is considered visible (≥ 50 %). Controls play/pause.
 * @param configuration    [CriteoVideoAdConfiguration] forwarded to the underlying wrapper.
 * @param enableLogs       Log categories to enable on the wrapper.
 * @param onVideoLoaded    Called when video assets are downloaded and ready.
 * @param onVideoStarted   Called when playback starts.
 * @param onVideoPaused    Called when playback pauses.
 * @param onVideoError     Called when an error occurs.
 */
@Composable
fun CriteoVideoAd(
    vastUrl: String,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    configuration: CriteoVideoAdConfiguration = CriteoVideoAdConfiguration(
        autoLoad = false,
        startsMuted = true
    ),
    enableLogs: Set<CriteoVideoAdLogCategory> = setOf(
        CriteoVideoAdLogCategory.VIDEO,
        CriteoVideoAdLogCategory.OMID
    ),
    onVideoLoaded: (() -> Unit)? = null,
    onVideoStarted: (() -> Unit)? = null,
    onVideoPaused: (() -> Unit)? = null,
    onVideoError: ((Throwable) -> Unit)? = null,
) {
    // Keep stable references to the latest callback values so the update
    // block always uses the most recent lambdas without triggering recomposition.
    val currentOnLoaded = rememberUpdatedState(onVideoLoaded)
    val currentOnStarted = rememberUpdatedState(onVideoStarted)
    val currentOnPaused = rememberUpdatedState(onVideoPaused)
    val currentOnError = rememberUpdatedState(onVideoError)

    // Track the wrapper for cleanup. Using remember (not mutableStateOf) because
    // we don't want setting this to trigger recomposition — the update block
    // handles all mutations.
    val wrapperHolder = remember { WrapperHolder() }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        factory = { ctx ->
            CriteoVideoAdWrapper.fromUrl(
                context = ctx,
                vastURL = vastUrl,
                configuration = configuration
            ).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                this.enableLogs = enableLogs
                this.onVideoLoaded = { currentOnLoaded.value?.invoke() }
                this.onVideoStarted = { currentOnStarted.value?.invoke() }
                this.onVideoPaused = { currentOnPaused.value?.invoke() }
                this.onVideoError = { currentOnError.value?.invoke(it) }
                wrapperHolder.wrapper = this
            }
        },
        // Called on every recomposition — handles visibility-driven play/pause.
        update = { view ->
            if (isVisible) view.play() else view.pause()
        }
    )

    // Clean up when vastUrl changes or the composable leaves the composition.
    DisposableEffect(vastUrl) {
        onDispose {
            wrapperHolder.wrapper?.release()
            wrapperHolder.wrapper = null
        }
    }
}

/**
 * Overload that uses a caller-managed [CriteoVideoAdWrapper].
 *
 * The caller owns the wrapper's lifecycle — this composable will **not** release
 * it on disposal.  Use this when the wrapper must survive Compose disposal cycles
 * (e.g. inside a [LazyColumn][androidx.compose.foundation.lazy.LazyColumn]).
 */
@Composable
fun CriteoVideoAd(
    wrapper: CriteoVideoAdWrapper,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    onVideoLoaded: (() -> Unit)? = null,
    onVideoStarted: (() -> Unit)? = null,
    onVideoPaused: (() -> Unit)? = null,
    onVideoError: ((Throwable) -> Unit)? = null,
) {
    val currentOnLoaded = rememberUpdatedState(onVideoLoaded)
    val currentOnStarted = rememberUpdatedState(onVideoStarted)
    val currentOnPaused = rememberUpdatedState(onVideoPaused)
    val currentOnError = rememberUpdatedState(onVideoError)

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        factory = {
            // Detach from any previous parent (LazyColumn may have removed the
            // composable and is now re-adding it).
            (wrapper.parent as? android.view.ViewGroup)?.removeView(wrapper)
            wrapper.apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                this.onVideoLoaded = { currentOnLoaded.value?.invoke() }
                this.onVideoStarted = { currentOnStarted.value?.invoke() }
                this.onVideoPaused = { currentOnPaused.value?.invoke() }
                this.onVideoError = { currentOnError.value?.invoke(it) }
            }
        },
        update = { view ->
            if (isVisible) view.play() else view.pause()
        }
    )
    // No DisposableEffect — the caller is responsible for releasing the wrapper.
}

/** Simple holder to avoid using mutableStateOf for the wrapper reference. */
private class WrapperHolder {
    var wrapper: CriteoVideoAdWrapper? = null
}

package com.lycoris.mochitv.presentation.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.lycoris.mochitv.data.download.DownloadManagerHolder
import com.lycoris.mochitv.domain.model.Episode
import kotlinx.coroutines.delay

/**
 * Full-screen video player screen.
 *
 * Orientation is locked to landscape for immersive playback and restored
 * when the user navigates away.  [ExoPlayer] lifecycle is tied to the
 * composable lifecycle via [LaunchedEffect] (prepare/play) and
 * [DisposableEffect] (release).
 *
 * @param onBack Called when the user taps the system back button or a back action.
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val nextEpisode by viewModel.nextEpisode.collectAsStateWithLifecycle()

    // ---- Lock orientation to landscape for video playback ------------------
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        // Capture original orientation before forcing landscape
        val original = activity?.requestedOrientation
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = original
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (val state = uiState) {
            is PlayerUiState.Loading -> {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            is PlayerUiState.Ready -> {
                VideoPlayer(
                    streamUrl = state.streamUrl,
                    nextEpisode = nextEpisode,
                    onNextEpisode = viewModel::goToNextEpisode,
                    onWatched = viewModel::recordWatchProgress,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is PlayerUiState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                ) {
                    Text(
                        text = state.message,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = viewModel::retry) { Text("Retry") }
                }
            }
        }
    }
}

// ---- ExoPlayer composable --------------------------------------------------

@Composable
private fun VideoPlayer(
    streamUrl: String,
    nextEpisode: Episode?,
    onNextEpisode: () -> Unit,
    onWatched: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val controlsVisible = remember { mutableStateOf(true) }

    val insetsController = remember {
        context.findActivity()?.window?.let { w ->
            WindowInsetsControllerCompat(w, w.decorView)
        }
    }

    val exoPlayer = remember {
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(DownloadManagerHolder.getCache(context))
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setCacheWriteDataSinkFactory(null) // don't cache on-demand streams
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()
    }

    LaunchedEffect(streamUrl) {
        exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(10_000L)
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration
            val threshold20min = 20 * 60 * 1000L
            if (pos > 0 && (pos >= threshold20min || (dur > 0 && pos * 5 >= dur * 4))) {
                onWatched()
                break
            }
        }
    }

    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    controllerAutoShow = true
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible.value = visibility == View.VISIBLE
                            if (visibility == View.GONE) {
                                insetsController?.hide(WindowInsetsCompat.Type.systemBars())
                            } else {
                                insetsController?.show(WindowInsetsCompat.Type.systemBars())
                            }
                        }
                    )
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (controlsVisible.value && nextEpisode != null) {
            Button(
                onClick = onNextEpisode,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 72.dp),
            ) {
                Text("Next →")
            }
        }
    }
}

// ---- Private helpers -------------------------------------------------------

/**
 * Traverses the [ContextWrapper] chain to find the host [Activity].
 *
 * Returns `null` when running in Compose previews or test environments where
 * no Activity is available — callers should handle null gracefully.
 */
private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

package com.lycoris.mochitv.presentation.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
 * @param onBack Called when the user taps the back button.
 * @param onHome Called when the user taps the home button to navigate back to the home screen.
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
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
                    savedPositionMs = state.savedPositionMs,
                    nextEpisode = nextEpisode,
                    onNextEpisode = viewModel::goToNextEpisode,
                    onWatched = viewModel::recordWatchProgress,
                    onSavePosition = viewModel::savePosition,
                    onBack = onBack,
                    onHome = onHome,
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
    savedPositionMs: Long,
    nextEpisode: Episode?,
    onNextEpisode: () -> Unit,
    onWatched: () -> Unit,
    onSavePosition: (positionMs: Long, durationMs: Long) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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

    // hasNextState is updated every recomposition so the wrapper always reflects the latest value.
    val hasNextState = remember { mutableStateOf(nextEpisode != null) }
    hasNextState.value = nextEpisode != null

    // Single wrapper for the lifetime of the composable — never recreated.
    val wrappedPlayer = remember {
        NextEpisodePlayer(
            player = exoPlayer,
            hasNext = { hasNextState.value },
            onNext = onNextEpisode,
        )
    }

    // PlayerControlView only re-reads available commands when setPlayer() is called or on player
    // events. When nextEpisode availability changes while the player is already playing (no event
    // fires), we briefly cycle the player reference to force a full updateNavigation() pass.
    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }
    LaunchedEffect(nextEpisode != null) {
        val pv = playerViewRef.value ?: return@LaunchedEffect
        pv.player = null
        pv.player = wrappedPlayer
    }

    // Prepare media and seek to the saved position if one exists.
    LaunchedEffect(streamUrl) {
        exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        if (savedPositionMs > 0L) {
            exoPlayer.seekTo(savedPositionMs)
        }
    }

    // Poll for "watched" threshold every 10 s.
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

    // Immersive mode — disable system bar auto-fit for full-bleed video.
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

    // Lifecycle observer: pause ExoPlayer and save position when the app is
    // backgrounded (ON_PAUSE). This covers the home-button and notification-
    // shade use cases where onDispose does not fire immediately.
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
                onSavePosition(exoPlayer.currentPosition, exoPlayer.duration)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Save position then release the player when the composable leaves
    // composition (back press, next-episode transition, or navigation away).
    DisposableEffect(exoPlayer) {
        onDispose {
            onSavePosition(exoPlayer.currentPosition, exoPlayer.duration)
            exoPlayer.release()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    playerViewRef.value = this
                    player = wrappedPlayer
                    setShowNextButton(false)
                    controllerAutoShow = true
                    // Swap the native previous-track button icon for a circular replay arrow.
                    post {
                        findViewById<android.widget.ImageButton>(
                            androidx.media3.ui.R.id.exo_prev
                        )?.setImageResource(com.lycoris.mochitv.R.drawable.ic_replay)
                    }
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
                playerViewRef.value = playerView
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (controlsVisible.value) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent),
                        )
                    )
                    .align(Alignment.TopStart),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 4.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (nextEpisode != null) {
                        IconButton(onClick = onNextEpisode) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Next episode",
                                tint = Color.White,
                            )
                        }
                    }
                    IconButton(onClick = onHome) {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = "Home",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

// ---- NextEpisodePlayer wrapper --------------------------------------------

/**
 * A [ForwardingPlayer] that makes the native "skip to next" control reflect
 * whether our episode list has a next item, and routes the seek action to
 * [onNext] instead of ExoPlayer's single-item playlist.
 */
private class NextEpisodePlayer(
    player: androidx.media3.common.Player,
    private val hasNext: () -> Boolean,
    private val onNext: () -> Unit,
) : androidx.media3.common.ForwardingPlayer(player) {

    override fun getAvailableCommands(): androidx.media3.common.Player.Commands {
        val base = super.getAvailableCommands()
        return if (hasNext()) {
            base.buildUpon()
                .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT)
                .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .build()
        } else {
            base
        }
    }

    override fun hasNextMediaItem(): Boolean = hasNext() || super.hasNextMediaItem()

    override fun isCommandAvailable(command: Int): Boolean =
        if (command == androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
            command == androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT) hasNext()
        else super.isCommandAvailable(command)

    override fun seekToNext() { if (hasNext()) onNext() else super.seekToNext() }
    override fun seekToNextMediaItem() { if (hasNext()) onNext() else super.seekToNextMediaItem() }
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

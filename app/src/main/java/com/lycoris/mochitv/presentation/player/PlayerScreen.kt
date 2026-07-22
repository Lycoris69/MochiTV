package com.lycoris.mochitv.presentation.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

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

/**
 * Embeds an [ExoPlayer] inside a Media3 [PlayerView] via [AndroidView].
 *
 * The player is created once via [remember], prepared when [streamUrl] first
 * arrives (or changes), and released in [DisposableEffect.onDispose] so there
 * are no memory leaks when the composable leaves the composition.
 */
@Composable
private fun VideoPlayer(
    streamUrl: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // ExoPlayer survives recompositions; released in DisposableEffect below
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    LaunchedEffect(streamUrl) {
        exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                // Keep controls visible until the user taps
                controllerAutoShow = true
            }
        },
        update = { playerView ->
            // Re-attach if the composable is recycled (e.g. after config change)
            playerView.player = exoPlayer
        },
        modifier = modifier,
    )
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

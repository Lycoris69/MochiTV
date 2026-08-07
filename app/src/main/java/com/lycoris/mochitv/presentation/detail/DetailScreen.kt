package com.lycoris.mochitv.presentation.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.model.AnimeStatus
import com.lycoris.mochitv.domain.model.Episode
import com.lycoris.mochitv.domain.model.PlaybackPosition
import com.lycoris.mochitv.domain.model.SourceSite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onEpisodeClick: (episodeId: String, animeId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val episodeDownloadStates by viewModel.episodeDownloadStates.collectAsStateWithLifecycle()
    val watchedEpisodeIds by viewModel.watchedEpisodeIds.collectAsStateWithLifecycle()
    val playbackPositions by viewModel.playbackPositions.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.downloadEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    when (val state = uiState) {
        is DetailUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is DetailUiState.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(24.dp),
                    )
                    Button(onClick = viewModel::retry) { Text("Retry") }
                }
            }
        }

        is DetailUiState.Success -> {
            DetailContent(
                anime = state.anime,
                episodes = state.episodes,
                isBookmarked = isBookmarked,
                onToggleBookmark = viewModel::toggleBookmark,
                onEpisodeClick = onEpisodeClick,
                onDownload = viewModel::downloadEpisode,
                onCancel = viewModel::cancelDownload,
                episodeDownloadStates = episodeDownloadStates,
                watchedEpisodeIds = watchedEpisodeIds,
                playbackPositions = playbackPositions,
                snackbarHostState = snackbarHostState,
                onBack = onBack,
                modifier = modifier,
            )
        }
    }
}

// ---- Main content -----------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    anime: Anime,
    episodes: List<Episode>,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onEpisodeClick: (episodeId: String, animeId: String) -> Unit,
    onDownload: (Episode) -> Unit,
    onCancel: (episodeId: String) -> Unit,
    episodeDownloadStates: Map<String, EpisodeDownloadState>,
    watchedEpisodeIds: Set<String>,
    playbackPositions: Map<String, PlaybackPosition>,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var synopsisExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = anime.title,
                        fontFamily = com.lycoris.mochitv.presentation.theme.NunitoFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                expandedHeight = 30.dp,
                actions = {
                    IconButton(onClick = onToggleBookmark) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Filled.Bookmark
                                          else Icons.Filled.BookmarkBorder,
                            contentDescription = if (isBookmarked) "Remove bookmark"
                                                 else "Add bookmark",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {

            // ---- Backdrop image --------------------------------------------
            item {
                AsyncImage(
                    model = anime.thumbnailUrl.ifBlank { null },
                    contentDescription = "${anime.title} poster",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
            }

            // ---- Title, status badge, rating --------------------------------
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = anime.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SourceBadge(sourceSite = anime.sourceSite)
                            StatusBadge(status = anime.status)
                        }
                    }

                    anime.rating?.let { rating ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Rating: ${"%.1f".format(rating)} / 10",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---- Genre chips -----------------------------------------------
            if (anime.genres.isNotEmpty()) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        anime.genres.forEach { genre ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(genre) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // ---- Collapsible synopsis --------------------------------------
            if (anime.synopsis.isNotBlank()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { synopsisExpanded = !synopsisExpanded }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = anime.synopsis,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (synopsisExpanded) Int.MAX_VALUE else 3,
                            overflow = if (synopsisExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (synopsisExpanded) "Show less" else "Read more",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            // ---- Episode list header ---------------------------------------
            item {
                Text(
                    text = "Episodes (${episodes.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                HorizontalDivider()
            }

            // ---- Episode rows ---------------------------------------------
            if (episodes.isEmpty()) {
                item {
                    Text(
                        text = "No episodes available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                items(episodes, key = { it.id }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        onClick = { onEpisodeClick(episode.id, episode.animeId) },
                        onDownload = { onDownload(episode) },
                        onCancel = { onCancel(episode.id) },
                        downloadState = episodeDownloadStates[episode.id]
                            ?: EpisodeDownloadState.None,
                        isWatched = episode.id in watchedEpisodeIds,
                        playbackPosition = playbackPositions[episode.id],
                    )
                    HorizontalDivider()
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// ---- Sub-composables -------------------------------------------------------

@Composable
private fun StatusBadge(status: AnimeStatus) {
    val (label, color) = when (status) {
        AnimeStatus.ONGOING   -> "Ongoing"   to MaterialTheme.colorScheme.primary
        AnimeStatus.COMPLETED -> "Completed" to MaterialTheme.colorScheme.secondary
        AnimeStatus.UNKNOWN   -> "Unknown"   to MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SourceBadge(sourceSite: SourceSite) {
    val label = when (sourceSite) {
        SourceSite.VOIR_ANIME  -> "Voir Anime"
        SourceSite.ANIME_SAMA  -> "Anime-Sama"
    }
    val color = MaterialTheme.colorScheme.outline
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    downloadState: EpisodeDownloadState,
    modifier: Modifier = Modifier,
    isWatched: Boolean = false,
    playbackPosition: PlaybackPosition? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ep ${episode.number}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(56.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                episode.title?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } ?: Text(
                    text = "Episode ${episode.number}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                episode.durationSeconds?.let { secs ->
                    Text(
                        text = formatDuration(secs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                episode.releaseDate?.let { date ->
                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (isWatched) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = "Seen",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Download status indicator
            when (downloadState) {
                EpisodeDownloadState.Resolving,
                EpisodeDownloadState.Queued,
                EpisodeDownloadState.Downloading -> IconButton(onClick = onCancel) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cancel download",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }

                EpisodeDownloadState.Completed -> Icon(
                    imageVector = Icons.Filled.DownloadDone,
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .size(24.dp),
                )

                EpisodeDownloadState.Failed -> IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = "Download failed — tap to retry",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }

                EpisodeDownloadState.None -> IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Download episode ${episode.number}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Thin progress bar shown when there is a saved resume position.
        if (playbackPosition != null && playbackPosition.durationMs > 0L) {
            LinearProgressIndicator(
                progress = { playbackPosition.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }
    }
}

// ---- Private helpers -------------------------------------------------------

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

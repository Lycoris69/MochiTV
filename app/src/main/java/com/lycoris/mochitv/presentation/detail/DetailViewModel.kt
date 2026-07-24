package com.lycoris.mochitv.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import com.lycoris.mochitv.data.download.EpisodeDownloader
import com.lycoris.mochitv.domain.model.Episode
import com.lycoris.mochitv.domain.usecase.BookmarkAnimeUseCase
import com.lycoris.mochitv.domain.usecase.DownloadEpisodeUseCase
import com.lycoris.mochitv.domain.usecase.GetAnimeDetailUseCase
import com.lycoris.mochitv.domain.usecase.GetEpisodesUseCase
import com.lycoris.mochitv.domain.usecase.GetWatchedEpisodeIdsUseCase
import com.lycoris.mochitv.domain.usecase.IsBookmarkedUseCase
import com.lycoris.mochitv.domain.usecase.UnbookmarkAnimeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAnimeDetailUseCase: GetAnimeDetailUseCase,
    private val getEpisodesUseCase: GetEpisodesUseCase,
    private val bookmarkAnimeUseCase: BookmarkAnimeUseCase,
    private val unbookmarkAnimeUseCase: UnbookmarkAnimeUseCase,
    isBookmarkedUseCase: IsBookmarkedUseCase,
    private val downloadEpisodeUseCase: DownloadEpisodeUseCase,
    private val episodeDownloader: EpisodeDownloader,
    getWatchedEpisodeIdsUseCase: GetWatchedEpisodeIdsUseCase,
) : ViewModel() {

    private val animeId: String = checkNotNull(savedStateHandle["animeId"]) {
        "DetailViewModel requires an 'animeId' navigation argument"
    }

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    val isBookmarked: StateFlow<Boolean> = isBookmarkedUseCase(animeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val watchedEpisodeIds: StateFlow<Set<String>> = getWatchedEpisodeIdsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Episodes whose stream URL is currently being resolved (before Media3 takes over). */
    private val _resolvingEpisodes = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Per-episode download state, combining the "resolving" phase tracked locally
     * with the live state from Media3's DownloadManager.
     *
     * Episodes not present in the map are [EpisodeDownloadState.None].
     */
    val episodeDownloadStates: StateFlow<Map<String, EpisodeDownloadState>> = combine(
        _resolvingEpisodes,
        episodeDownloader.observeStates(),
    ) { resolving, media3States ->
        buildMap {
            media3States.forEach { (id, state) ->
                put(id, when (state) {
                    Download.STATE_QUEUED      -> EpisodeDownloadState.Queued
                    Download.STATE_DOWNLOADING -> EpisodeDownloadState.Downloading
                    Download.STATE_COMPLETED   -> EpisodeDownloadState.Completed
                    Download.STATE_FAILED      -> EpisodeDownloadState.Failed
                    Download.STATE_STOPPED     -> EpisodeDownloadState.Queued
                    else                       -> EpisodeDownloadState.None
                })
            }
            // "Resolving" phase takes precedence over any stale Media3 state
            resolving.forEach { id -> put(id, EpisodeDownloadState.Resolving) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** One-shot messages forwarded to the Snackbar. */
    private val _downloadEvents = Channel<String>(Channel.BUFFERED)
    val downloadEvents = _downloadEvents.receiveAsFlow()

    init {
        loadDetail()
    }

    fun retry() = loadDetail()

    fun toggleBookmark() {
        viewModelScope.launch {
            if (isBookmarked.value) unbookmarkAnimeUseCase(animeId)
            else bookmarkAnimeUseCase(animeId)
        }
    }

    fun downloadEpisode(episode: Episode) {
        if (episode.id in _resolvingEpisodes.value) return
        viewModelScope.launch {
            _resolvingEpisodes.update { it + episode.id }
            val animeName = (_uiState.value as? DetailUiState.Success)?.anime?.title ?: "MochiTV"
            downloadEpisodeUseCase(episode, animeName)
                .onSuccess { _downloadEvents.send("Download started") }
                .onFailure { e ->
                    _downloadEvents.send(e.message ?: "Download failed")
                }
            _resolvingEpisodes.update { it - episode.id }
        }
    }

    private fun loadDetail() {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading

            val anime = getAnimeDetailUseCase(animeId).getOrElse { error ->
                _uiState.value = DetailUiState.Error(
                    error.message ?: "Could not load anime details"
                )
                return@launch
            }

            val episodes = getEpisodesUseCase(animeId).getOrElse { error ->
                _uiState.value = DetailUiState.Error(
                    error.message ?: "Could not load episode list"
                )
                return@launch
            }

            val animeWithSynopsis = getAnimeDetailUseCase(animeId).getOrDefault(anime)
            _uiState.value = DetailUiState.Success(animeWithSynopsis, episodes)
        }
    }
}

package com.lycoris.mochitv.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.mochitv.domain.model.Episode
import com.lycoris.mochitv.domain.usecase.BookmarkAnimeUseCase
import com.lycoris.mochitv.domain.usecase.DownloadEpisodeUseCase
import com.lycoris.mochitv.domain.usecase.GetAnimeDetailUseCase
import com.lycoris.mochitv.domain.usecase.GetEpisodesUseCase
import com.lycoris.mochitv.domain.usecase.IsBookmarkedUseCase
import com.lycoris.mochitv.domain.usecase.UnbookmarkAnimeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {

    private val animeId: String = checkNotNull(savedStateHandle["animeId"]) {
        "DetailViewModel requires an 'animeId' navigation argument"
    }

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    val isBookmarked: StateFlow<Boolean> = isBookmarkedUseCase(animeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Episode IDs currently having their stream URL resolved for download. */
    private val _downloadingEpisodes = MutableStateFlow<Set<String>>(emptySet())
    val downloadingEpisodes: StateFlow<Set<String>> = _downloadingEpisodes.asStateFlow()

    /** One-shot messages for the snackbar (success or error from a download attempt). */
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
        if (episode.id in _downloadingEpisodes.value) return
        viewModelScope.launch {
            _downloadingEpisodes.update { it + episode.id }
            val animeName = (_uiState.value as? DetailUiState.Success)?.anime?.title ?: "MochiTV"
            downloadEpisodeUseCase(episode, animeName)
                .onSuccess { _downloadEvents.send("Download started") }
                .onFailure { e ->
                    val msg = when (e) {
                        is UnsupportedOperationException -> e.message ?: "Cannot download this episode"
                        else -> "Download failed: ${e.message}"
                    }
                    _downloadEvents.send(msg)
                }
            _downloadingEpisodes.update { it - episode.id }
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

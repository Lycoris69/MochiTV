package com.lycoris.mochitv.presentation.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.mochitv.data.download.EpisodeDownloader
import com.lycoris.mochitv.domain.usecase.GetEpisodesUseCase
import com.lycoris.mochitv.domain.usecase.GetStreamUrlUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getEpisodesUseCase: GetEpisodesUseCase,
    private val getStreamUrlUseCase: GetStreamUrlUseCase,
    private val episodeDownloader: EpisodeDownloader,
) : ViewModel() {

    private val episodeId: String = checkNotNull(savedStateHandle["episodeId"])
    private val animeId: String = checkNotNull(savedStateHandle["animeId"])

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        loadStream()
    }

    fun retry() = loadStream()

    private fun loadStream() {
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading

            val episodes = getEpisodesUseCase(animeId).getOrElse { error ->
                _uiState.value = PlayerUiState.Error(
                    error.message ?: "Could not load episode list"
                )
                return@launch
            }

            val episode = episodes.firstOrNull { it.id == episodeId } ?: run {
                _uiState.value = PlayerUiState.Error("Episode not found (id=$episodeId)")
                return@launch
            }

            // If episode is fully downloaded, use the cached URI directly.
            // ExoPlayer's CacheDataSource reads from the Media3 cache for this URI,
            // so no network call is made even when offline.
            val cachedUri = episodeDownloader.getDownloadedUri(episode.id)
            if (cachedUri != null) {
                _uiState.value = PlayerUiState.Ready(cachedUri, episode)
                return@launch
            }

            // Not downloaded — resolve the stream URL from the network as normal.
            getStreamUrlUseCase(episode)
                .onSuccess { url -> _uiState.value = PlayerUiState.Ready(url, episode) }
                .onFailure { error ->
                    _uiState.value = PlayerUiState.Error(
                        error.message ?: "Could not resolve stream URL"
                    )
                }
        }
    }
}

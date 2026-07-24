package com.lycoris.mochitv.presentation.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.mochitv.data.download.EpisodeDownloader
import com.lycoris.mochitv.domain.model.Episode
import com.lycoris.mochitv.domain.usecase.GetEpisodesUseCase
import com.lycoris.mochitv.domain.usecase.GetStreamUrlUseCase
import com.lycoris.mochitv.domain.usecase.RecordWatchProgressUseCase
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
    private val recordWatchProgressUseCase: RecordWatchProgressUseCase,
) : ViewModel() {

    private var currentEpisodeId: String = checkNotNull(savedStateHandle["episodeId"])
    private val animeId: String = checkNotNull(savedStateHandle["animeId"])

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _nextEpisode = MutableStateFlow<Episode?>(null)
    val nextEpisode: StateFlow<Episode?> = _nextEpisode.asStateFlow()

    init {
        loadStream()
    }

    fun retry() = loadStream()

    fun goToNextEpisode() {
        _nextEpisode.value?.let { next ->
            currentEpisodeId = next.id
            loadStream()
        }
    }

    fun recordWatchProgress() {
        val episode = (_uiState.value as? PlayerUiState.Ready)?.episode ?: return
        viewModelScope.launch { recordWatchProgressUseCase(episode.id) }
    }

    private fun loadStream() {
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading

            val episodes = getEpisodesUseCase(animeId).getOrElse { error ->
                _uiState.value = PlayerUiState.Error(
                    error.message ?: "Could not load episode list"
                )
                return@launch
            }

            val idx = episodes.indexOfFirst { it.id == currentEpisodeId }
            _nextEpisode.value = episodes.getOrNull(idx + 1)
            val episode = episodes.getOrNull(idx) ?: run {
                _uiState.value = PlayerUiState.Error("Episode not found")
                return@launch
            }

            val cachedUri = episodeDownloader.getDownloadedUri(episode.id)
            if (cachedUri != null) {
                _uiState.value = PlayerUiState.Ready(cachedUri, episode)
                return@launch
            }

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

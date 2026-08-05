package com.lycoris.mochitv.presentation.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.mochitv.data.download.EpisodeDownloader
import com.lycoris.mochitv.domain.model.Episode
import com.lycoris.mochitv.domain.usecase.GetEpisodesUseCase
import com.lycoris.mochitv.domain.usecase.GetPlaybackPositionUseCase
import com.lycoris.mochitv.domain.usecase.GetStreamUrlUseCase
import com.lycoris.mochitv.domain.usecase.RecordWatchProgressUseCase
import com.lycoris.mochitv.domain.usecase.SavePlaybackPositionUseCase
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
    private val getPlaybackPositionUseCase: GetPlaybackPositionUseCase,
    private val savePlaybackPositionUseCase: SavePlaybackPositionUseCase,
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

    /**
     * Persists the current playback position.
     *
     * Should be called from the UI on [androidx.lifecycle.Lifecycle.Event.ON_PAUSE]
     * and also in the [androidx.compose.runtime.DisposableEffect] cleanup when the
     * player composable leaves composition (back press / next-episode transition).
     *
     * If [positionMs] ≤ 0, or if the episode is nearly finished (within the last
     * 5 s of [durationMs]), [SavePlaybackPositionUseCase] handles the cleanup
     * automatically (no-op or delete).
     */
    fun savePosition(positionMs: Long, durationMs: Long) {
        val episode = (_uiState.value as? PlayerUiState.Ready)?.episode ?: return
        viewModelScope.launch {
            savePlaybackPositionUseCase(
                episodeId = episode.id,
                animeId = animeId,
                positionMs = positionMs,
                durationMs = durationMs,
            )
        }
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

            // Load the saved position before emitting Ready so the player can
            // seek to it immediately without an extra state transition.
            val savedPositionMs = getPlaybackPositionUseCase(episode.id)?.positionMs ?: 0L

            val cachedUri = episodeDownloader.getDownloadedUri(episode.id)
            if (cachedUri != null) {
                _uiState.value = PlayerUiState.Ready(cachedUri, episode, savedPositionMs)
                return@launch
            }

            getStreamUrlUseCase(episode)
                .onSuccess { url ->
                    _uiState.value = PlayerUiState.Ready(url, episode, savedPositionMs)
                }
                .onFailure { error ->
                    _uiState.value = PlayerUiState.Error(
                        error.message ?: "Could not resolve stream URL"
                    )
                }
        }
    }
}

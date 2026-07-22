package com.lycoris.mochitv.presentation.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.mochitv.domain.usecase.GetEpisodesUseCase
import com.lycoris.mochitv.domain.usecase.GetStreamUrlUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the video player screen.
 *
 * ### Data flow
 * 1. [episodeId] and [animeId] are read from [SavedStateHandle].
 * 2. [GetEpisodesUseCase] is called — the result is served from the Room cache
 *    populated by [com.lycoris.mochitv.presentation.detail.DetailViewModel], so
 *    no redundant network call is expected in the normal navigation path.
 *    If the cache is empty (e.g. deep-link), the use case falls back to scraping.
 * 3. The target [com.lycoris.mochitv.domain.model.Episode] is located by id.
 * 4. [GetStreamUrlUseCase] resolves the direct stream URL via the scraper.
 * 5. On success, the state transitions to [PlayerUiState.Ready].
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getEpisodesUseCase: GetEpisodesUseCase,
    private val getStreamUrlUseCase: GetStreamUrlUseCase,
) : ViewModel() {

    private val episodeId: String = checkNotNull(savedStateHandle["episodeId"]) {
        "PlayerViewModel requires an 'episodeId' navigation argument"
    }
    private val animeId: String = checkNotNull(savedStateHandle["animeId"]) {
        "PlayerViewModel requires an 'animeId' navigation argument"
    }

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        loadStream()
    }

    /** Retry resolving the stream URL after an error. */
    fun retry() = loadStream()

    private fun loadStream() {
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading

            // Locate the episode — normally served from the Room cache
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

            // Resolve the direct stream URL
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

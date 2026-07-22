package com.lycoris.mochitv.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.mochitv.domain.usecase.GetAnimeDetailUseCase
import com.lycoris.mochitv.domain.usecase.GetEpisodesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the anime detail screen.
 *
 * ### Data flow
 * 1. [animeId] is read from [SavedStateHandle] (populated by Navigation Compose).
 * 2. [GetAnimeDetailUseCase] reads the anime from the local Room cache — fast,
 *    no network needed because [com.lycoris.mochitv.presentation.home.HomeViewModel]
 *    populated the cache when the user performed the search.
 * 3. [GetEpisodesUseCase] scrapes the episode list from the source site and
 *    caches it in Room for the player screen.
 * 4. Steps 2 and 3 are sequential; the state transitions
 *    Loading → Success (or Error) once both complete.
 *
 * State is exposed as an immutable [StateFlow] collected by the Compose UI via
 * `collectAsStateWithLifecycle`.
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAnimeDetailUseCase: GetAnimeDetailUseCase,
    private val getEpisodesUseCase: GetEpisodesUseCase,
) : ViewModel() {

    private val animeId: String = checkNotNull(savedStateHandle["animeId"]) {
        "DetailViewModel requires an 'animeId' navigation argument"
    }

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
    }

    /** Retry loading after an error. */
    fun retry() = loadDetail()

    private fun loadDetail() {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading

            // 1. Load anime metadata from cache (fast Room read)
            val anime = getAnimeDetailUseCase(animeId).getOrElse { error ->
                _uiState.value = DetailUiState.Error(
                    error.message ?: "Could not load anime details"
                )
                return@launch
            }

            // 2. Fetch episodes from scraper — also updates anime synopsis in Room as a side-effect
            val episodes = getEpisodesUseCase(animeId).getOrElse { error ->
                _uiState.value = DetailUiState.Error(
                    error.message ?: "Could not load episode list"
                )
                return@launch
            }

            // 3. Re-read anime from cache to pick up the synopsis saved in step 2
            val animeWithSynopsis = getAnimeDetailUseCase(animeId).getOrDefault(anime)

            _uiState.value = DetailUiState.Success(animeWithSynopsis, episodes)
        }
    }
}

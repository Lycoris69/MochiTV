package com.lycoris.mochitv.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.usecase.SearchAnimeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

// ---- UI state --------------------------------------------------------------

/**
 * Exhaustive UI state for [HomeScreen].
 *
 * Sealed interface instead of sealed class so implementors don't pay the
 * overhead of a superclass allocation for simple states like [Idle] and [Loading].
 */
sealed interface HomeUiState {
    /** No search has been initiated yet (app just opened or query was cleared). */
    data object Idle : HomeUiState

    /** A search is in-flight. */
    data object Loading : HomeUiState

    /** Search completed with results (may be an empty list). */
    data class Success(val results: List<Anime>) : HomeUiState

    /** Search failed; [message] is user-facing. */
    data class Error(val message: String) : HomeUiState
}

// ---- ViewModel -------------------------------------------------------------

/**
 * ViewModel for [HomeScreen].
 *
 * Search is triggered reactively: [onSearchQueryChange] updates [searchQuery],
 * which is observed with a 500 ms debounce so network requests are not fired
 * on every keystroke.
 *
 * State is exposed as [StateFlow] — the UI collects it with
 * `collectAsStateWithLifecycle` to respect the Lifecycle and avoid wasting
 * work in the background.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val searchAnimeUseCase: SearchAnimeUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        observeSearchQuery()
    }

    /**
     * Called by the UI whenever the text in the search field changes.
     * Clears results immediately when the query is blank so the screen
     * returns to [HomeUiState.Idle] without waiting for the debounce.
     */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _uiState.update { HomeUiState.Idle }
        }
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        _searchQuery
            .debounce(500L)               // wait 500 ms of silence before searching
            .distinctUntilChanged()       // skip if query hasn't changed
            .filter { it.length >= 2 }    // require at least 2 characters
            .onEach { query -> executeSearch(query) }
            .launchIn(viewModelScope)
    }

    private suspend fun executeSearch(query: String) {
        _uiState.update { HomeUiState.Loading }
        searchAnimeUseCase(query)
            .onSuccess { results ->
                _uiState.update { HomeUiState.Success(results) }
            }
            .onFailure { error ->
                _uiState.update { HomeUiState.Error(error.message ?: "Unknown error") }
            }
    }
}

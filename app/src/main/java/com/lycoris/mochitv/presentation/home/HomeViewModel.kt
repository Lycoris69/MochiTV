package com.lycoris.mochitv.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.usecase.ClearSearchHistoryUseCase
import com.lycoris.mochitv.domain.usecase.DeleteSearchQueryUseCase
import com.lycoris.mochitv.domain.usecase.GetLatestReleasesUseCase
import com.lycoris.mochitv.domain.usecase.GetSearchHistoryUseCase
import com.lycoris.mochitv.domain.usecase.GetWatchHistoryUseCase
import com.lycoris.mochitv.domain.usecase.SaveSearchQueryUseCase
import com.lycoris.mochitv.domain.usecase.SearchAnimeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val getLatestReleasesUseCase: GetLatestReleasesUseCase,
    private val saveSearchQueryUseCase: SaveSearchQueryUseCase,
    private val deleteSearchQueryUseCase: DeleteSearchQueryUseCase,
    private val clearSearchHistoryUseCase: ClearSearchHistoryUseCase,
    getSearchHistoryUseCase: GetSearchHistoryUseCase,
    getWatchHistoryUseCase: GetWatchHistoryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchHistory: StateFlow<List<String>> = getSearchHistoryUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Most recently watched episodes, newest-first, limited to 10 items. */
    val watchHistory: StateFlow<List<WatchHistoryItem>> = getWatchHistoryUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Latest releases pagination state ----------------------------------

    private val _latestReleases = MutableStateFlow<List<Anime>>(emptyList())
    val latestReleases: StateFlow<List<Anime>> = _latestReleases.asStateFlow()

    private val _isLatestLoading = MutableStateFlow(false)
    /** True while page 1 is being fetched (initial load or refresh). */
    val isLatestLoading: StateFlow<Boolean> = _isLatestLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    /** True while an additional page is being appended. */
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private var latestPage = 1
    private var hasMorePages = true
    private var latestJob: kotlinx.coroutines.Job? = null

    init {
        observeSearchQuery()
        loadLatestReleases()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) _uiState.update { HomeUiState.Idle }
    }

    /** Reset to page 1 and re-fetch. Safe to call from the refresh button. */
    fun refreshLatest() {
        if (_isLatestLoading.value) return
        latestPage = 1
        hasMorePages = true
        _latestReleases.value = emptyList()
        loadLatestReleases()
    }

    /** Append the next page. Called by the UI when the end of the list is reached. */
    fun loadMoreLatest() {
        if (_isLoadingMore.value || _isLatestLoading.value || !hasMorePages) return
        latestPage++
        viewModelScope.launch {
            _isLoadingMore.value = true
            getLatestReleasesUseCase(latestPage)
                .onSuccess { results ->
                    if (results.isEmpty()) {
                        hasMorePages = false
                    } else {
                        _latestReleases.value = _latestReleases.value + results
                    }
                }
                .onFailure { latestPage-- } // retry same page next time
            _isLoadingMore.value = false
        }
    }

    fun saveSearch(query: String) {
        viewModelScope.launch { saveSearchQueryUseCase(query) }
    }

    fun deleteSearch(query: String) {
        viewModelScope.launch { deleteSearchQueryUseCase(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { clearSearchHistoryUseCase() }
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private fun loadLatestReleases() {
        latestJob?.cancel()
        latestJob = viewModelScope.launch {
            _isLatestLoading.value = true
            getLatestReleasesUseCase(1)
                .onSuccess { results ->
                    _latestReleases.value = results
                    hasMorePages = results.isNotEmpty()
                    latestPage = 1
                }
                .onFailure { /* keep stale data visible */ }
            _isLatestLoading.value = false
        }
    }

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

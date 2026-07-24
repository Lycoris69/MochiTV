package com.lycoris.mochitv.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.mochitv.data.download.EpisodeDownloader
import com.lycoris.mochitv.data.local.dao.EpisodeDao
import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.usecase.GetDownloadedAnimeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    getDownloadedAnimeUseCase: GetDownloadedAnimeUseCase,
    private val episodeDownloader: EpisodeDownloader,
    private val episodeDao: EpisodeDao,
) : ViewModel() {

    val downloadedAnime: StateFlow<List<Anime>> = getDownloadedAnimeUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    fun toggleSelection(animeId: String) {
        _selectedIds.update { if (animeId in it) it - animeId else it + animeId }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    // ---- Delete dialog -------------------------------------------------------

    private val _deleteDialogState = MutableStateFlow<DeleteDialogState?>(null)
    val deleteDialogState: StateFlow<DeleteDialogState?> = _deleteDialogState.asStateFlow()

    fun openDeleteDialog() {
        viewModelScope.launch {
            val selectedAnime = downloadedAnime.value.filter { it.id in _selectedIds.value }
            val episodes = selectedAnime.flatMap { anime ->
                episodeDao.getAllByAnimeId(anime.id)
                    .filter { entity -> episodeDownloader.getDownloadedUri(entity.id) != null }
                    .map { entity ->
                        EpisodeItem(
                            id = entity.id,
                            number = entity.number,
                            title = entity.title,
                            animeTitle = anime.title,
                        )
                    }
            }.sortedWith(compareBy({ it.animeTitle }, { it.number }))

            _deleteDialogState.value = DeleteDialogState(
                episodes = episodes,
                checkedIds = episodes.map { it.id }.toSet(),
            )
        }
    }

    fun toggleEpisodeCheck(episodeId: String) {
        _deleteDialogState.update { state ->
            state?.copy(
                checkedIds = if (episodeId in state.checkedIds)
                    state.checkedIds - episodeId
                else
                    state.checkedIds + episodeId
            )
        }
    }

    fun confirmDelete() {
        val toDelete = _deleteDialogState.value?.checkedIds?.toSet() ?: return
        _deleteDialogState.value = null
        clearSelection()
        viewModelScope.launch {
            toDelete.forEach { episodeId -> episodeDownloader.deleteDownload(episodeId) }
        }
    }

    fun dismissDeleteDialog() {
        _deleteDialogState.value = null
    }
}

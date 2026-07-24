package com.lycoris.mochitv.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.usecase.DeleteDownloadedAnimeUseCase
import com.lycoris.mochitv.domain.usecase.GetDownloadedAnimeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    getDownloadedAnimeUseCase: GetDownloadedAnimeUseCase,
    private val deleteDownloadedAnimeUseCase: DeleteDownloadedAnimeUseCase,
) : ViewModel() {

    val downloadedAnime: Flow<List<Anime>> = getDownloadedAnimeUseCase()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    fun toggleSelection(animeId: String) {
        _selectedIds.update { current ->
            if (animeId in current) current - animeId else current + animeId
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val toDelete = _selectedIds.value.toSet()
        clearSelection()
        viewModelScope.launch {
            toDelete.forEach { animeId -> deleteDownloadedAnimeUseCase(animeId) }
        }
    }
}

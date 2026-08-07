package com.lycoris.mochitv.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lycoris.mochitv.data.local.dao.AnimeDao
import com.lycoris.mochitv.data.local.dao.BookmarkDao
import com.lycoris.mochitv.data.local.dao.FolderWithCount
import com.lycoris.mochitv.data.local.dao.LibraryFolderDao
import com.lycoris.mochitv.data.local.entity.LibraryFolderEntity
import com.lycoris.mochitv.data.local.entity.toDomain
import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.usecase.GetWatchHistoryUseCase
import com.lycoris.mochitv.presentation.home.WatchHistoryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val animeDao: AnimeDao,
    private val libraryFolderDao: LibraryFolderDao,
    getWatchHistoryUseCase: GetWatchHistoryUseCase,
) : ViewModel() {

    /** Most recently watched episodes, newest-first, limited to 10 items. */
    val watchHistory: StateFlow<List<WatchHistoryItem>> = getWatchHistoryUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Navigation ---------------------------------------------------------

    /** null = root (shows folders + uncategorized), non-null = inside a named folder */
    private val _currentFolderId = MutableStateFlow<String?>(null)
    val currentFolderId: StateFlow<String?> = _currentFolderId.asStateFlow()

    private val _currentFolderName = MutableStateFlow<String?>(null)
    val currentFolderName: StateFlow<String?> = _currentFolderName.asStateFlow()

    fun navigateToFolder(folderId: String) {
        viewModelScope.launch {
            _currentFolderName.value = libraryFolderDao.getName(folderId)
            _currentFolderId.value = folderId
            clearSelection()
        }
    }

    fun navigateToRoot() {
        _currentFolderId.value = null
        _currentFolderName.value = null
        clearSelection()
    }

    // ---- Folders list (only relevant at root) --------------------------------

    val folders: StateFlow<List<FolderWithCount>> = libraryFolderDao.getAllWithCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Anime list for the current context ---------------------------------

    val displayedAnime: StateFlow<List<Anime>> = _currentFolderId.flatMapLatest { folderId ->
        val idsFlow = if (folderId == null)
            bookmarkDao.getUncategorizedIds()
        else
            bookmarkDao.getIdsByFolder(folderId)

        idsFlow.map { ids ->
            ids.mapNotNull { animeDao.getById(it)?.toDomain() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Selection ----------------------------------------------------------

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    fun toggleSelection(animeId: String) {
        _selectedIds.update { if (animeId in it) it - animeId else it + animeId }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    // ---- Create folder dialog -----------------------------------------------

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    fun openCreateFolderDialog() { _showCreateDialog.value = true }
    fun dismissCreateDialog() { _showCreateDialog.value = false }

    fun createFolder(name: String) {
        val trimmed = name.trim().ifBlank { return }
        viewModelScope.launch {
            libraryFolderDao.insert(
                LibraryFolderEntity(
                    id = UUID.randomUUID().toString(),
                    name = trimmed,
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
        _showCreateDialog.value = false
    }

    // ---- Move dialog --------------------------------------------------------

    private val _showMoveDialog = MutableStateFlow(false)
    val showMoveDialog: StateFlow<Boolean> = _showMoveDialog.asStateFlow()

    fun openMoveDialog() { _showMoveDialog.value = true }
    fun dismissMoveDialog() { _showMoveDialog.value = false }

    fun moveSelectedTo(folderId: String?) {
        val ids = _selectedIds.value.toList()
        viewModelScope.launch {
            bookmarkDao.moveTo(ids, folderId)
        }
        _showMoveDialog.value = false
        clearSelection()
    }

    // ---- Delete folder ------------------------------------------------------

    fun deleteCurrentFolder() {
        val folderId = _currentFolderId.value ?: return
        viewModelScope.launch {
            // Move all series in this folder back to uncategorized before deleting the folder
            bookmarkDao.clearFolder(folderId)
            libraryFolderDao.delete(folderId)
        }
        navigateToRoot()
    }
}

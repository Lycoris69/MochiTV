package com.lycoris.mochitv.presentation.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lycoris.mochitv.data.local.dao.FolderWithCount
import com.lycoris.mochitv.domain.model.Anime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onAnimeClick: (animeId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val currentFolderId by viewModel.currentFolderId.collectAsStateWithLifecycle()
    val currentFolderName by viewModel.currentFolderName.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val displayedAnime by viewModel.displayedAnime.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val showCreateDialog by viewModel.showCreateDialog.collectAsStateWithLifecycle()
    val showMoveDialog by viewModel.showMoveDialog.collectAsStateWithLifecycle()

    val isSelecting = selectedIds.isNotEmpty()

    // Clear selection on back press when in selection mode; otherwise navigate out of folder
    BackHandler(enabled = isSelecting) { viewModel.clearSelection() }
    BackHandler(enabled = currentFolderId != null && !isSelecting) { viewModel.navigateToRoot() }

    if (showCreateDialog) {
        CreateFolderDialog(
            onConfirm = { viewModel.createFolder(it) },
            onDismiss = { viewModel.dismissCreateDialog() },
        )
    }

    if (showMoveDialog) {
        MoveFolderDialog(
            folders = folders,
            onMove = { viewModel.moveSelectedTo(it) },
            onDismiss = { viewModel.dismissMoveDialog() },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when {
                        isSelecting -> Text("${selectedIds.size} selected")
                        currentFolderId != null -> Text(currentFolderName ?: "Folder")
                        else -> Text("Library")
                    }
                },
                navigationIcon = {
                    when {
                        isSelecting -> IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Cancel selection",
                            )
                        }
                        currentFolderId != null -> IconButton(onClick = { viewModel.navigateToRoot() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = {
                    when {
                        isSelecting -> IconButton(onClick = { viewModel.openMoveDialog() }) {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = "Move to folder",
                            )
                        }
                        currentFolderId != null -> IconButton(onClick = { viewModel.deleteCurrentFolder() }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete folder",
                            )
                        }
                        else -> IconButton(onClick = { viewModel.openCreateFolderDialog() }) {
                            Icon(
                                imageVector = Icons.Filled.CreateNewFolder,
                                contentDescription = "Create folder",
                            )
                        }
                    }
                },
                expandedHeight = 48.dp,
            )
        },
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Folder chips row — only at root when folders exist
            if (currentFolderId == null && folders.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(folders, key = { it.id }) { folder ->
                            FolderCard(
                                folder = folder,
                                onClick = { viewModel.navigateToFolder(folder.id) },
                            )
                        }
                    }
                }
            }

            // Empty state message
            if (displayedAnime.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (currentFolderId == null)
                                "No bookmarks yet.\nOpen an anime and tap the bookmark icon."
                            else
                                "No series in this folder yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                items(displayedAnime, key = { it.id }) { anime ->
                    val isSelected = anime.id in selectedIds
                    AnimeCard(
                        anime = anime,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelecting) viewModel.toggleSelection(anime.id)
                            else onAnimeClick(anime.id)
                        },
                        onLongClick = { viewModel.toggleSelection(anime.id) },
                    )
                }
            }
        }
    }
}

// ----- Private composables ---------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnimeCard(
    anime: Anime,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
            AsyncImage(
                model = anime.thumbnailUrl.ifBlank { null },
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun FolderCard(
    folder: FolderWithCount,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.width(120.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = folder.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${folder.seriesCount} series",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreateFolderDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New folder") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Folder name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun MoveFolderDialog(
    folders: List<FolderWithCount>,
    onMove: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to folder") },
        text = {
            Column {
                // Uncategorized is always the first option
                TextButton(
                    onClick = { onMove(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Uncategorized")
                }
                folders.forEach { folder ->
                    TextButton(
                        onClick = { onMove(folder.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(folder.name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        },
    )
}

package com.lycoris.mochitv.presentation.downloads

data class EpisodeItem(
    val id: String,
    val number: Int,
    val title: String?,
    val animeTitle: String,
)

data class DeleteDialogState(
    val episodes: List<EpisodeItem>,
    val checkedIds: Set<String>,
)

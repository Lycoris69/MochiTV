package com.lycoris.mochitv.presentation.home

import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.model.Episode
import com.lycoris.mochitv.domain.model.PlaybackPosition

/**
 * UI model for a single "Continue Watching" card on [HomeScreen].
 *
 * Aggregates the raw position data with the resolved [Anime] and [Episode]
 * objects so the UI can display the thumbnail, title, episode number, and
 * a progress bar without additional lookups.
 */
data class WatchHistoryItem(
    val position: PlaybackPosition,
    val anime: Anime,
    val episode: Episode,
)

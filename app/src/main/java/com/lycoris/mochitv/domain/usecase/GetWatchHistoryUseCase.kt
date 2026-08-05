package com.lycoris.mochitv.domain.usecase

import com.lycoris.mochitv.data.local.dao.AnimeDao
import com.lycoris.mochitv.data.local.dao.EpisodeDao
import com.lycoris.mochitv.data.local.dao.PlaybackPositionDao
import com.lycoris.mochitv.data.local.entity.toDomain
import com.lycoris.mochitv.presentation.home.WatchHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private const val MAX_HISTORY_ITEMS = 10

/**
 * Returns a reactive stream of the most recently played episodes, up to
 * [MAX_HISTORY_ITEMS] entries, ordered newest-first.
 *
 * Each [WatchHistoryItem] joins the raw position row with the cached [Anime]
 * and [Episode] objects from Room.  Entries whose anime or episode is not yet
 * cached are silently skipped (they will appear once the cache is populated on
 * the next detail-page visit).
 */
class GetWatchHistoryUseCase @Inject constructor(
    private val positionDao: PlaybackPositionDao,
    private val animeDao: AnimeDao,
    private val episodeDao: EpisodeDao,
) {
    operator fun invoke(): Flow<List<WatchHistoryItem>> = positionDao.observeAll()
        .map { positions ->
            positions.take(MAX_HISTORY_ITEMS).mapNotNull { pos ->
                val anime = animeDao.getById(pos.animeId)?.toDomain() ?: return@mapNotNull null
                val episode = episodeDao.getById(pos.episodeId)?.toDomain() ?: return@mapNotNull null
                WatchHistoryItem(
                    position = pos.toDomain(),
                    anime = anime,
                    episode = episode,
                )
            }
        }
}

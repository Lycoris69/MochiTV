package com.lycoris.mochitv.domain.usecase

import com.lycoris.mochitv.data.local.dao.PlaybackPositionDao
import com.lycoris.mochitv.data.local.entity.toDomain
import com.lycoris.mochitv.domain.model.PlaybackPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Observes all saved playback positions belonging to [animeId] as a reactive
 * map keyed by episode id.
 *
 * Used by [com.lycoris.mochitv.presentation.detail.DetailViewModel] to show
 * per-episode resume-progress indicators in the episode list.
 */
class GetPlaybackPositionsForAnimeUseCase @Inject constructor(
    private val dao: PlaybackPositionDao,
) {
    operator fun invoke(animeId: String): Flow<Map<String, PlaybackPosition>> =
        dao.observeByAnimeId(animeId)
            .map { list -> list.associate { it.episodeId to it.toDomain() } }
}

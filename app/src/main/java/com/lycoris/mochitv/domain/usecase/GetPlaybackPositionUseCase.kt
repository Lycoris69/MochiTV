package com.lycoris.mochitv.domain.usecase

import com.lycoris.mochitv.data.local.dao.PlaybackPositionDao
import com.lycoris.mochitv.data.local.entity.toDomain
import com.lycoris.mochitv.domain.model.PlaybackPosition
import javax.inject.Inject

/**
 * Returns the last saved playback position for [episodeId], or `null` if none
 * exists (the episode was never started, or was completed and the row deleted).
 */
class GetPlaybackPositionUseCase @Inject constructor(
    private val dao: PlaybackPositionDao,
) {
    suspend operator fun invoke(episodeId: String): PlaybackPosition? =
        dao.get(episodeId)?.toDomain()
}

package com.lycoris.mochitv.domain.usecase

import com.lycoris.mochitv.data.local.dao.PlaybackPositionDao
import com.lycoris.mochitv.data.local.entity.PlaybackPositionEntity
import javax.inject.Inject

/**
 * Persists the user's current playback position for an episode.
 *
 * If [positionMs] is 0 or negative, the call is a no-op (playback never started).
 * If the position is within the last 5 seconds of [durationMs] the episode is
 * considered finished, so any previously saved row is deleted rather than
 * updated — the episode will start from the beginning on the next open.
 */
class SavePlaybackPositionUseCase @Inject constructor(
    private val dao: PlaybackPositionDao,
) {
    suspend operator fun invoke(
        episodeId: String,
        animeId: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (positionMs <= 0L) return
        val nearEnd = durationMs > 0L && (durationMs - positionMs) <= 5_000L
        if (nearEnd) {
            dao.delete(episodeId)
        } else {
            dao.save(
                PlaybackPositionEntity(
                    episodeId = episodeId,
                    animeId = animeId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    savedAtMs = System.currentTimeMillis(),
                )
            )
        }
    }
}

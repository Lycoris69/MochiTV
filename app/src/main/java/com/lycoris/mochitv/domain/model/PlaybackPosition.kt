package com.lycoris.mochitv.domain.model

/**
 * Domain representation of the user's last known playback position for a single episode.
 *
 * This is a projection of the Room entity — the presentation layer depends on this
 * class rather than on the data-layer entity directly.
 *
 * @param positionMs Last known position in milliseconds.
 * @param durationMs Total episode duration in milliseconds (0 if unknown).
 * @param savedAtMs  Wall-clock time when this record was last updated.
 */
data class PlaybackPosition(
    val episodeId: String,
    val animeId: String,
    val positionMs: Long,
    val durationMs: Long,
    val savedAtMs: Long,
) {
    /**
     * Fractional playback progress in [0, 1].
     *
     * Returns `0f` when [durationMs] is zero to avoid division-by-zero.
     */
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                else 0f
}

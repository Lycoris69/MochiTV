package com.lycoris.mochitv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lycoris.mochitv.domain.model.PlaybackPosition

/**
 * Room entity that stores the user's last known playback position for an episode.
 *
 * Rows are upserted every time the user pauses or exits an episode so playback
 * can resume from where they left off.  When an episode is considered finished
 * (position within the last 5 s of duration), the row is deleted instead so the
 * episode starts from the beginning on the next open.
 */
@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey val episodeId: String,
    val animeId: String,
    /** Last known playback position in milliseconds. */
    val positionMs: Long,
    /** Total episode duration in milliseconds as reported by ExoPlayer. */
    val durationMs: Long,
    /** Wall-clock time when this row was last written. */
    val savedAtMs: Long,
)

// ---- Mapping extension -----------------------------------------------------

fun PlaybackPositionEntity.toDomain(): PlaybackPosition = PlaybackPosition(
    episodeId = episodeId,
    animeId = animeId,
    positionMs = positionMs,
    durationMs = durationMs,
    savedAtMs = savedAtMs,
)

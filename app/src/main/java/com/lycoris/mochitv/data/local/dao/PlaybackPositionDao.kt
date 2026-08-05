package com.lycoris.mochitv.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lycoris.mochitv.data.local.entity.PlaybackPositionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the `playback_positions` table.
 *
 * All write operations are `suspend` so callers stay on
 * [kotlinx.coroutines.Dispatchers.IO] naturally.
 */
@Dao
interface PlaybackPositionDao {

    /** Insert or update a playback-position row keyed on [PlaybackPositionEntity.episodeId]. */
    @Upsert
    suspend fun save(entity: PlaybackPositionEntity)

    /** Returns the saved position for [episodeId], or `null` if none exists. */
    @Query("SELECT * FROM playback_positions WHERE episodeId = :episodeId")
    suspend fun get(episodeId: String): PlaybackPositionEntity?

    /**
     * Observe all saved positions ordered newest-first.
     *
     * Room re-emits whenever the underlying table changes.  Collectors should
     * call [take] to limit the result set for UI purposes.
     */
    @Query("SELECT * FROM playback_positions ORDER BY savedAtMs DESC")
    fun observeAll(): Flow<List<PlaybackPositionEntity>>

    /**
     * Observe all saved positions belonging to [animeId], unordered.
     *
     * Used by [com.lycoris.mochitv.presentation.detail.DetailViewModel] to display
     * per-episode resume indicators in the episode list.
     */
    @Query("SELECT * FROM playback_positions WHERE animeId = :animeId")
    fun observeByAnimeId(animeId: String): Flow<List<PlaybackPositionEntity>>

    /** Remove the saved position for [episodeId] (called when an episode is finished). */
    @Query("DELETE FROM playback_positions WHERE episodeId = :episodeId")
    suspend fun delete(episodeId: String)
}

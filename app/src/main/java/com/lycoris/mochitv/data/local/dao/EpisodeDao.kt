package com.lycoris.mochitv.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lycoris.mochitv.data.local.entity.EpisodeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the `episode` table.
 *
 * Episodes are cached when an anime's episode list is first fetched from the
 * network so that [com.lycoris.mochitv.presentation.player.PlayerViewModel] can
 * look up a single episode by its id without a redundant network round-trip.
 */
@Dao
interface EpisodeDao {

    /**
     * Insert or update a list of episode rows, keyed on [EpisodeEntity.id].
     *
     * Called automatically by the repository after every [AnimeDao.getEpisodes]
     * network fetch so the cache stays in sync with live data.
     */
    @Upsert
    suspend fun upsertAll(entities: List<EpisodeEntity>)

    /**
     * Observe all episodes belonging to [animeId] ordered by episode number,
     * as a reactive stream.  Room re-emits whenever the underlying rows change.
     */
    @Query("SELECT * FROM episode WHERE animeId = :animeId ORDER BY number ASC")
    fun observeByAnimeId(animeId: String): Flow<List<EpisodeEntity>>

    /** Returns the episode row with [id], or `null` if not cached. */
    @Query("SELECT * FROM episode WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): EpisodeEntity?
}

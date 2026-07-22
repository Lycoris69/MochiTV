package com.lycoris.mochitv.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lycoris.mochitv.data.local.entity.AnimeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the `anime` table.
 *
 * All write operations are `suspend` functions so callers stay on
 * [kotlinx.coroutines.Dispatchers.IO] naturally.  [observeAll] returns a
 * [Flow] that Room keeps alive and re-emits whenever the underlying table changes.
 */
@Dao
interface AnimeDao {

    /**
     * Insert or update a list of anime rows.
     *
     * [Upsert] (Room 2.5+) replaces an existing row if the primary key
     * ([AnimeEntity.id]) matches, or inserts a new one if it does not.
     */
    @Upsert
    suspend fun upsertAll(entities: List<AnimeEntity>)

    /**
     * Observe the full cached catalogue as a reactive stream.
     *
     * Room emits a new list whenever any row in the `anime` table is inserted,
     * updated, or deleted.
     */
    @Query("SELECT * FROM anime")
    fun observeAll(): Flow<List<AnimeEntity>>

    /** Returns the anime row with [id], or `null` if not cached. */
    @Query("SELECT * FROM anime WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AnimeEntity?

    /** Removes all rows from the `anime` table. */
    @Query("DELETE FROM anime")
    suspend fun deleteAll()
}

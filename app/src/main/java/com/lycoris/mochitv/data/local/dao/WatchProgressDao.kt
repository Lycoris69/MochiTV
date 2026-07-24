package com.lycoris.mochitv.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lycoris.mochitv.data.local.entity.WatchProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchProgressDao {
    @Upsert
    suspend fun markWatched(entity: WatchProgressEntity)

    @Query("SELECT episodeId FROM watch_progress")
    fun observeWatchedIds(): Flow<List<String>>
}

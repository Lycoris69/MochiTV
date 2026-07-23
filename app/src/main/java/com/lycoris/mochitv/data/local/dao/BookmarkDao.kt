package com.lycoris.mochitv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lycoris.mochitv.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE animeId = :animeId")
    suspend fun delete(animeId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE animeId = :animeId)")
    fun isBookmarked(animeId: String): Flow<Boolean>

    @Query("SELECT animeId FROM bookmarks ORDER BY addedAt DESC")
    fun getAllIds(): Flow<List<String>>
}

package com.lycoris.mochitv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lycoris.mochitv.data.local.entity.LibraryFolderEntity
import kotlinx.coroutines.flow.Flow

/**
 * Plain result class — not a Room entity — for the folder + bookmark-count join query.
 */
data class FolderWithCount(
    val id: String,
    val name: String,
    val createdAt: Long,
    val seriesCount: Int,
)

@Dao
interface LibraryFolderDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: LibraryFolderEntity)

    @Query("DELETE FROM library_folders WHERE id = :id")
    suspend fun delete(id: String)

    @Query("""
        SELECT lf.id, lf.name, lf.createdAt,
               COUNT(b.animeId) AS seriesCount
        FROM library_folders lf
        LEFT JOIN bookmarks b ON b.folderId = lf.id
        GROUP BY lf.id
        ORDER BY lf.name ASC
    """)
    fun getAllWithCount(): Flow<List<FolderWithCount>>

    @Query("SELECT name FROM library_folders WHERE id = :id LIMIT 1")
    suspend fun getName(id: String): String?
}

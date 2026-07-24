package com.lycoris.mochitv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val animeId: String,
    val addedAt: Long = System.currentTimeMillis(),
    /** null = uncategorized (not in any folder) */
    val folderId: String? = null,
)

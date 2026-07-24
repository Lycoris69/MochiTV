package com.lycoris.mochitv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey val episodeId: String,
    val watchedAtMs: Long,
)

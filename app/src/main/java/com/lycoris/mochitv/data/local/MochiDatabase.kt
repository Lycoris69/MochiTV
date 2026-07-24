package com.lycoris.mochitv.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lycoris.mochitv.data.local.converter.Converters
import com.lycoris.mochitv.data.local.dao.AnimeDao
import com.lycoris.mochitv.data.local.dao.BookmarkDao
import com.lycoris.mochitv.data.local.dao.EpisodeDao
import com.lycoris.mochitv.data.local.dao.SearchHistoryDao
import com.lycoris.mochitv.data.local.dao.WatchProgressDao
import com.lycoris.mochitv.data.local.entity.AnimeEntity
import com.lycoris.mochitv.data.local.entity.BookmarkEntity
import com.lycoris.mochitv.data.local.entity.EpisodeEntity
import com.lycoris.mochitv.data.local.entity.SearchHistoryEntity
import com.lycoris.mochitv.data.local.entity.WatchProgressEntity

/**
 * Root Room database for MochiTV.
 *
 * [Converters] is registered here so Room applies the `List<String>` ↔ JSON
 * type conversion wherever it appears in entity field types.
 *
 * ### Migrations
 * `exportSchema = false` disables the compile-time schema export file.
 * Before shipping to production, set it to `true`, add the schema directory
 * to source control, and provide proper [androidx.room.migration.Migration]
 * implementations for each version bump.
 */
@Database(
    entities = [AnimeEntity::class, EpisodeEntity::class, BookmarkEntity::class, SearchHistoryEntity::class, WatchProgressEntity::class],
    version = 5,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MochiDatabase : RoomDatabase() {

    abstract fun animeDao(): AnimeDao

    abstract fun episodeDao(): EpisodeDao

    abstract fun bookmarkDao(): BookmarkDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun watchProgressDao(): WatchProgressDao
}

package com.lycoris.mochitv.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lycoris.mochitv.data.local.converter.Converters
import com.lycoris.mochitv.data.local.dao.AnimeDao
import com.lycoris.mochitv.data.local.dao.EpisodeDao
import com.lycoris.mochitv.data.local.entity.AnimeEntity
import com.lycoris.mochitv.data.local.entity.EpisodeEntity

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
    entities = [AnimeEntity::class, EpisodeEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MochiDatabase : RoomDatabase() {

    abstract fun animeDao(): AnimeDao

    abstract fun episodeDao(): EpisodeDao
}

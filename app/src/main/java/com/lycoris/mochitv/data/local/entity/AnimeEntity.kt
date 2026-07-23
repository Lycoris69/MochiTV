package com.lycoris.mochitv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.model.AnimeStatus
import com.lycoris.mochitv.domain.model.SourceSite

/**
 * Room entity that mirrors [Anime] for local cache storage.
 *
 * [genres] is a `List<String>` serialised to JSON by
 * [com.lycoris.mochitv.data.local.converter.Converters] via the `@TypeConverters`
 * annotation on [com.lycoris.mochitv.data.local.MochiDatabase].
 *
 * Enum fields ([status], [sourceSite]) are stored as their string names so that
 * adding new enum variants later does not require a schema migration.
 */
@Entity(tableName = "anime")
data class AnimeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val thumbnailUrl: String,
    val synopsis: String,
    /** Persisted as a JSON array string via [com.lycoris.mochitv.data.local.converter.Converters]. */
    val genres: List<String>,
    val rating: Float?,
    val episodeCount: Int?,
    /** [AnimeStatus.name] — avoids ordinal fragility across schema versions. */
    val status: String,
    val sourceUrl: String,
    /** [SourceSite.name]. */
    val sourceSite: String,
    /** Date of the most recent episode release as reported by the scraper; null when not available. */
    val lastReleaseDate: String?,
)

// ---- Mapping extensions ----------------------------------------------------

/** Converts this entity to its domain counterpart. */
fun AnimeEntity.toDomain(): Anime = Anime(
    id = id,
    title = title,
    thumbnailUrl = thumbnailUrl,
    synopsis = synopsis,
    genres = genres,
    rating = rating,
    episodeCount = episodeCount,
    status = runCatching { AnimeStatus.valueOf(status) }.getOrDefault(AnimeStatus.UNKNOWN),
    sourceUrl = sourceUrl,
    sourceSite = SourceSite.valueOf(sourceSite),
    lastReleaseDate = lastReleaseDate,
)

/** Converts the domain model to its Room entity counterpart. */
fun Anime.toEntity(): AnimeEntity = AnimeEntity(
    id = id,
    title = title,
    thumbnailUrl = thumbnailUrl,
    synopsis = synopsis,
    genres = genres,
    rating = rating,
    episodeCount = episodeCount,
    status = status.name,
    sourceUrl = sourceUrl,
    sourceSite = sourceSite.name,
    lastReleaseDate = lastReleaseDate,
)

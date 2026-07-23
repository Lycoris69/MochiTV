package com.lycoris.mochitv.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lycoris.mochitv.domain.model.Episode
import com.lycoris.mochitv.domain.model.SourceSite

/**
 * Room entity that mirrors [Episode] for local cache storage.
 *
 * [animeId] is a foreign key referencing [AnimeEntity.id].  The `CASCADE`
 * delete rule means episodes are automatically removed if their parent anime
 * row is deleted (e.g. via [com.lycoris.mochitv.data.local.dao.AnimeDao.deleteAll]).
 *
 * An index is required on [animeId] so Room can satisfy the foreign-key
 * constraint efficiently and so [com.lycoris.mochitv.data.local.dao.EpisodeDao.observeByAnimeId]
 * uses the index instead of a full-table scan.
 */
@Entity(
    tableName = "episode",
    foreignKeys = [
        ForeignKey(
            entity = AnimeEntity::class,
            parentColumns = ["id"],
            childColumns = ["animeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["animeId"])],
)
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val animeId: String,
    val number: Int,
    val title: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
    /** Episode watch-page URL used by scrapers to resolve stream URLs. */
    val pageUrl: String,
    /** [SourceSite.name]. */
    val sourceSite: String,
    /** Human-readable release date as scraped from the source site, or null if unavailable. */
    val releaseDate: String? = null,
)

// ---- Mapping extensions ----------------------------------------------------

/** Converts this entity to its domain counterpart. */
fun EpisodeEntity.toDomain(): Episode = Episode(
    id = id,
    animeId = animeId,
    number = number,
    title = title,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    pageUrl = pageUrl,
    sourceSite = SourceSite.valueOf(sourceSite),
    releaseDate = releaseDate,
)

/** Converts the domain model to its Room entity counterpart. */
fun Episode.toEntity(): EpisodeEntity = EpisodeEntity(
    id = id,
    animeId = animeId,
    number = number,
    title = title,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    pageUrl = pageUrl,
    sourceSite = sourceSite.name,
    releaseDate = releaseDate,
)

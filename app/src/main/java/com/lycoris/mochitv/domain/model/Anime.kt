package com.lycoris.mochitv.domain.model

/**
 * Represents a single anime series sourced from a scraper.
 *
 * The [id] is prefixed with the source name (e.g. `"flixnet_naruto"`) so that
 * results from different scrapers can coexist in the same list without collision.
 */
data class Anime(
    /** Unique identifier, scoped by [sourceSite] prefix. */
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val synopsis: String,
    val genres: List<String>,
    /** Average user rating, 0–10 scale, or null if not provided by the source. */
    val rating: Float?,
    val episodeCount: Int?,
    val status: AnimeStatus,
    /** URL of the anime detail page on the source site. */
    val sourceUrl: String,
    val sourceSite: SourceSite,
)

/** Publication status as reported by the source site. */
enum class AnimeStatus {
    ONGOING,
    COMPLETED,
    UNKNOWN,
}

/** Identifies which scraper / site produced a given piece of data. */
enum class SourceSite {
    FLIXNET,
    VOIR_ANIME,
}

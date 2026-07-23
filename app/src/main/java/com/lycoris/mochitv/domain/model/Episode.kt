package com.lycoris.mochitv.domain.model

/**
 * Represents a single watchable episode of an [Anime].
 *
 * [pageUrl] is the watch-page URL on the source site; it is used by the
 * scraper to extract the actual video stream URL on demand.
 */
data class Episode(
    /** Unique identifier, e.g. `"animesama_ep_naruto_1"`. */
    val id: String,
    /** Foreign key linking this episode to its parent [Anime.id]. */
    val animeId: String,
    val number: Int,
    /** Episode title as shown on the source site, or null if absent. */
    val title: String?,
    val thumbnailUrl: String?,
    /** Total duration in seconds, or null if not provided by the source. */
    val durationSeconds: Int?,
    /** URL of the episode watch page — used by scrapers to resolve stream URLs. */
    val pageUrl: String,
    val sourceSite: SourceSite,
)

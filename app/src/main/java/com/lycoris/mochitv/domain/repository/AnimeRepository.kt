package com.lycoris.mochitv.domain.repository

import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.model.Episode
import com.lycoris.mochitv.domain.model.SourceSite
import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer contract for accessing anime data.
 *
 * Implementations live in the `data` layer and aggregate results from
 * multiple [com.lycoris.mochitv.data.scraper.AnimeScraper] instances plus
 * the local Room cache.
 *
 * All network operations return [Result] so callers can distinguish between
 * "empty list" and "fetch failed" without throwing exceptions into the UI.
 */
interface AnimeRepository {

    /**
     * Search for anime matching [query].
     *
     * @param query  User-supplied search string.
     * @param source Optionally restrict results to a single site; null means all sources.
     * @return [Result.success] with a (possibly empty) list, or [Result.failure] on error.
     */
    suspend fun searchAnime(
        query: String,
        source: SourceSite? = null,
    ): Result<List<Anime>>

    /**
     * Fetch all episodes for the anime identified by [animeId].
     *
     * @param animeId The [Anime.id] of the target series.
     */
    suspend fun getEpisodes(animeId: String): Result<List<Episode>>

    /**
     * Resolve the direct video stream URL for a given [episode].
     *
     * This typically involves a secondary HTTP request to extract the embed
     * or JavaScript variable that holds the actual media URL.
     */
    suspend fun getStreamUrl(episode: Episode): Result<String>

    /**
     * Observe the locally cached anime catalogue as a reactive [Flow].
     *
     * Emits immediately with whatever is in the Room database, then emits
     * again whenever the cache is updated.
     */
    fun observeCachedAnime(): Flow<List<Anime>>
}

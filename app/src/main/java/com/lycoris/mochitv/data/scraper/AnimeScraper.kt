package com.lycoris.mochitv.data.scraper

import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.model.Episode
import com.lycoris.mochitv.domain.model.SourceSite

/**
 * Contract that every site-specific scraper must implement.
 *
 * Implementations perform **blocking** OkHttp + Jsoup work internally;
 * each function switches to [kotlinx.coroutines.Dispatchers.IO] via
 * [kotlinx.coroutines.withContext] before issuing any network request.
 *
 * Hilt collects concrete scrapers into a `Set<AnimeScraper>` multi-binding
 * so that [com.lycoris.mochitv.data.repository.AnimeRepositoryImpl] can
 * fan-out calls to all sources without knowing about individual implementations.
 */
interface AnimeScraper {

    /** Identifies the source site this scraper targets. */
    val site: SourceSite

    /**
     * Search for anime by [query] on the source site.
     *
     * @throws Exception on network errors or unexpected HTML structure.
     */
    suspend fun search(query: String): List<Anime>

    /**
     * Fetch and parse the episode list from [animePageUrl].
     *
     * @param animePageUrl  Full URL of the anime detail/series page.
     * @throws Exception on network errors or unexpected HTML structure.
     */
    suspend fun getEpisodes(animePageUrl: String): List<Episode>

    /**
     * Resolve the direct video stream URL from the episode watch page.
     *
     * Implementations should try multiple extraction strategies in order
     * (iframe src, JS variable, JSON key) and throw if none succeeds.
     *
     * @param episodePageUrl  Full URL of the episode watch page.
     * @throws Exception if no stream URL can be extracted.
     */
    suspend fun getStreamUrl(episodePageUrl: String): String
}

package com.lycoris.mochitv.data.repository

import com.lycoris.mochitv.data.local.dao.AnimeDao
import com.lycoris.mochitv.data.local.dao.EpisodeDao
import com.lycoris.mochitv.data.local.entity.toEntity
import com.lycoris.mochitv.data.local.entity.toDomain
import com.lycoris.mochitv.data.scraper.AnimeScraper
import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.model.Episode
import com.lycoris.mochitv.domain.model.SourceSite
import com.lycoris.mochitv.domain.repository.AnimeRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Production [AnimeRepository] that aggregates results from all registered
 * [AnimeScraper] implementations via Hilt multi-binding and caches data in Room.
 *
 * ### Search fan-out
 * Search requests are dispatched concurrently to every enabled scraper using
 * [coroutineScope] + [async], ensuring structured concurrency: if one scraper
 * throws, the exception is re-thrown after all launched coroutines have
 * completed or cancelled. Results are flat-merged into a single list and then
 * upserted to the local [AnimeDao] cache.
 *
 * ### Episode caching
 * After each [getEpisodes] call, episodes are upserted to [EpisodeDao] so that
 * [getEpisodeById] can look them up without an additional network request from
 * the player screen.
 *
 * ### Local cache
 * [observeCachedAnime] is wired to [AnimeDao.observeAll] and re-emits whenever
 * the database table changes. [getAnimeById] and [getEpisodeById] read from Room.
 */
class AnimeRepositoryImpl @Inject constructor(
    // @JvmSuppressWildcards prevents Kotlin from emitting Set<? extends AnimeScraper>,
    // which Dagger cannot match against the @IntoSet bindings.
    private val scrapers: Set<@JvmSuppressWildcards AnimeScraper>,
    private val animeDao: AnimeDao,
    private val episodeDao: EpisodeDao,
) : AnimeRepository {

    override suspend fun searchAnime(
        query: String,
        source: SourceSite?,
    ): Result<List<Anime>> = runCatching {
        require(query.isNotBlank()) { "Search query must not be blank" }

        val targets = if (source != null) {
            scrapers.filter { it.site == source }
        } else {
            scrapers.toList()
        }

        val results = coroutineScope {
            targets
                .map { scraper -> async { scraper.search(query) } }
                .flatMap { deferred -> deferred.await() }
                // Stable ordering: group by source then by title for deterministic UI
                .sortedWith(compareBy({ it.sourceSite.name }, { it.title }))
        }

        // Upsert results into Room so the catalogue is available offline
        animeDao.upsertAll(results.map { it.toEntity() })

        results
    }

    override suspend fun getEpisodes(animeId: String): Result<List<Episode>> = runCatching {
        val scraper = scrapers.firstOrNull { animeId.startsWith(it.site.idPrefix + "_") }
            ?: error("No scraper registered for animeId=$animeId")

        val slug = animeId.removePrefix(scraper.site.idPrefix + "_")
        val pageUrl = "${scraper.site.baseUrl}$slug/"

        // Fetch episodes and synopsis concurrently from the same page
        val (episodes, synopsis) = coroutineScope {
            val episodesDeferred = async { scraper.getEpisodes(pageUrl).sortedBy { it.number } }
            val synopsisDeferred = async { scraper.getAnimeSynopsis(pageUrl) }
            episodesDeferred.await() to synopsisDeferred.await()
        }

        episodeDao.upsertAll(episodes.map { it.toEntity() })

        // Update the cached anime with the fetched synopsis if we got one
        if (synopsis.isNotBlank()) {
            animeDao.getById(animeId)?.copy(synopsis = synopsis)
                ?.let { animeDao.upsertAll(listOf(it)) }
        }

        episodes
    }

    override suspend fun getStreamUrl(episode: Episode): Result<String> = runCatching {
        val scraper = scrapers.firstOrNull { it.site == episode.sourceSite }
            ?: error("No scraper registered for site=${episode.sourceSite}")
        scraper.getStreamUrl(episode.pageUrl)
    }

    override suspend fun getLatestReleases(page: Int): Result<List<Anime>> = runCatching {
        val voirScraper = scrapers.firstOrNull { it.site == SourceSite.VOIR_ANIME }
            ?: error("VoirAnimeScraper is not registered in the Hilt multi-binding")

        val results = voirScraper.getLatestReleases(page)

        if (results.isNotEmpty()) animeDao.upsertAll(results.map { it.toEntity() })

        results
    }

    override fun observeCachedAnime(): Flow<List<Anime>> =
        animeDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAnimeById(id: String): Result<Anime> = runCatching {
        animeDao.getById(id)?.toDomain()
            ?: error("Anime not in local cache (id=$id); search for it first to populate the cache")
    }

    override suspend fun getEpisodeById(id: String): Result<Episode> = runCatching {
        episodeDao.getById(id)?.toDomain()
            ?: error("Episode not in local cache (id=$id); open the detail screen first")
    }
}

// ---- Private extensions ----------------------------------------------------

/** Short prefix used to encode the source in an [Anime.id]. */
private val SourceSite.idPrefix: String
    get() = when (this) {
        SourceSite.ANIME_SAMA -> "animesama"
        SourceSite.VOIR_ANIME -> "voir"
    }

/** Root URL of the source site (trailing slash included). */
private val SourceSite.baseUrl: String
    get() = when (this) {
        SourceSite.ANIME_SAMA -> "https://anime-sama.to/"
        SourceSite.VOIR_ANIME -> "https://voir-anime.to/"
    }

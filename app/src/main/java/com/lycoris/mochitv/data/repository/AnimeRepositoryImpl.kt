package com.lycoris.mochitv.data.repository

import com.lycoris.mochitv.data.scraper.AnimeScraper
import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.model.Episode
import com.lycoris.mochitv.domain.model.SourceSite
import com.lycoris.mochitv.domain.repository.AnimeRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Production [AnimeRepository] that aggregates results from all registered
 * [AnimeScraper] implementations via Hilt multi-binding.
 *
 * ### Search fan-out
 * Search requests are dispatched concurrently to every enabled scraper using
 * [coroutineScope] + [async], ensuring structured concurrency: if one scraper
 * throws, the exception is re-thrown after all launched coroutines have
 * completed or cancelled. Results are flat-merged into a single list.
 *
 * ### Episode lookup
 * The [animeId] encodes the source prefix (e.g. `"flixnet_naruto"`), so the
 * correct scraper can be selected without additional state.
 *
 * ### Local cache
 * The [observeCachedAnime] stub emits an empty list until Room integration is
 * wired. Replacing the `flow { emit(emptyList()) }` with a Room DAO query is
 * the only change needed to activate offline browsing.
 */
class AnimeRepositoryImpl @Inject constructor(
    // @JvmSuppressWildcards prevents Kotlin from emitting Set<? extends AnimeScraper>,
    // which Dagger cannot match against the @IntoSet bindings.
    private val scrapers: Set<@JvmSuppressWildcards AnimeScraper>,
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

        coroutineScope {
            targets
                .map { scraper -> async { scraper.search(query) } }
                .flatMap { deferred -> deferred.await() }
                // Stable ordering: group by source then by title for deterministic UI
                .sortedWith(compareBy({ it.sourceSite.name }, { it.title }))
        }
    }

    override suspend fun getEpisodes(animeId: String): Result<List<Episode>> = runCatching {
        val scraper = scrapers.firstOrNull { animeId.startsWith(it.site.idPrefix + "_") }
            ?: error("No scraper registered for animeId=$animeId")

        // Reconstruct the anime detail page URL from the id slug.
        // A future iteration should store and pass sourceUrl directly.
        val slug = animeId.removePrefix(scraper.site.idPrefix + "_")
        val pageUrl = "${scraper.site.baseUrl}$slug/"

        scraper.getEpisodes(pageUrl)
            .sortedBy { it.number }
    }

    override suspend fun getStreamUrl(episode: Episode): Result<String> = runCatching {
        val scraper = scrapers.firstOrNull { it.site == episode.sourceSite }
            ?: error("No scraper registered for site=${episode.sourceSite}")
        scraper.getStreamUrl(episode.pageUrl)
    }

    override fun observeCachedAnime(): Flow<List<Anime>> = flow {
        // TODO: replace with Room DAO query once local cache is wired:
        //   animeDao.observeAll().collect { emit(it.map { entity -> entity.toDomain() }) }
        emit(emptyList())
    }
}

// ---- Private extensions ----------------------------------------------------

/** Short prefix used to encode the source in an [Anime.id]. */
private val SourceSite.idPrefix: String
    get() = when (this) {
        SourceSite.FLIXNET    -> "flixnet"
        SourceSite.VOIR_ANIME -> "voir"
    }

/** Root URL of the source site (trailing slash included). */
private val SourceSite.baseUrl: String
    get() = when (this) {
        SourceSite.FLIXNET    -> "https://flixnet.site/animesama/"
        SourceSite.VOIR_ANIME -> "https://voir-anime.to/"
    }

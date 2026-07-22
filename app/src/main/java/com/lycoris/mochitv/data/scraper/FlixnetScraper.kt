package com.lycoris.mochitv.data.scraper

import com.lycoris.mochitv.data.network.UserAgents
import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.model.AnimeStatus
import com.lycoris.mochitv.domain.model.Episode
import com.lycoris.mochitv.domain.model.SourceSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject

private const val BASE_URL = "https://flixnet.site/animesama/"

/**
 * Scraper for [https://flixnet.site/animesama/].
 *
 * ## CSS selectors (update when the site's HTML changes)
 * | Page          | Elements           | Notes                              |
 * |---------------|--------------------|------------------------------------|
 * | Search results| `.anime-card`      | Each card is one series            |
 * | Anime title   | `.anime-title`     | Inside each card                   |
 * | Episode list  | `.episode-item`    | On the anime detail page           |
 * | Stream source | `iframe[src]`      | Strategy 1 (preferred)             |
 * | Stream source | `var videoUrl`     | Strategy 2 (JavaScript fallback)   |
 *
 * All HTTP calls use [UserAgents.random] to rotate the User-Agent header.
 */
class FlixnetScraper @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : AnimeScraper {

    override val site: SourceSite = SourceSite.FLIXNET

    // -------------------------------------------------------------------------
    // AnimeScraper implementation
    // -------------------------------------------------------------------------

    override suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        val url = "${BASE_URL}?s=${query.trim().replace(" ", "+")}"
        val doc = fetchDocument(url)

        doc.select(".anime-card").map { card ->
            val href = card.select("a[href]").attr("abs:href")
            val slug = href.trimEnd('/').substringAfterLast('/')

            Anime(
                id = "flixnet_$slug",
                title = card.select(".anime-title").text().ifBlank { slug },
                thumbnailUrl = card.select("img").attr("abs:src"),
                synopsis = card.select(".anime-synopsis").text(),
                genres = card.select(".genre-tag").map { it.text() },
                rating = card.select(".anime-rating").text().toFloatOrNull(),
                episodeCount = card.select(".ep-count").text().toIntOrNull(),
                status = parseStatus(card.select(".anime-status").text()),
                sourceUrl = href,
                sourceSite = SourceSite.FLIXNET,
            )
        }
    }

    override suspend fun getEpisodes(animePageUrl: String): List<Episode> = withContext(Dispatchers.IO) {
        val doc = fetchDocument(animePageUrl)
        val animeSlug = animePageUrl.trimEnd('/').substringAfterLast('/')

        doc.select(".episode-item").mapIndexed { index, element ->
            val href = element.select("a[href]").attr("abs:href")
            val epNum = element.select(".ep-number").text().toIntOrNull() ?: (index + 1)

            Episode(
                id = "flixnet_ep_${animeSlug}_$epNum",
                animeId = "flixnet_$animeSlug",
                number = epNum,
                title = element.select(".ep-title").text().ifBlank { null },
                thumbnailUrl = element.select("img").attr("abs:src").ifBlank { null },
                durationSeconds = null, // Not typically present in episode list HTML
                pageUrl = href,
                sourceSite = SourceSite.FLIXNET,
            )
        }
    }

    override suspend fun getStreamUrl(episodePageUrl: String): String = withContext(Dispatchers.IO) {
        val doc = fetchDocument(episodePageUrl)

        // Strategy 1: direct <iframe> embed — most common case
        doc.select("iframe[src]").firstOrNull()
            ?.attr("abs:src")
            ?.takeIf { it.isNotBlank() }
            ?.let { return@withContext it }

        // Strategy 2: JavaScript variable  →  var videoUrl = "https://..."
        val scripts = doc.select("script").joinToString("\n") { it.data() }
        Regex("""var\s+videoUrl\s*=\s*["']([^"']+)["']""")
            .find(scripts)
            ?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() }
            ?.let { return@withContext it }

        // Strategy 3: JWPlayer / generic  file: "https://..."
        Regex("""file\s*:\s*["']([^"']+\.(?:mp4|m3u8|mkv)[^"']*)["']""")
            .find(scripts)
            ?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() }
            ?.let { return@withContext it }

        error("FlixnetScraper: could not extract stream URL from $episodePageUrl")
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun fetchDocument(url: String): Document {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UserAgents.random())
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", BASE_URL)
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "FlixnetScraper: HTTP ${response.code} fetching $url"
            }
            Jsoup.parse(response.body!!.string(), BASE_URL)
        }
    }

    private fun parseStatus(raw: String): AnimeStatus = when {
        raw.contains("ongoing", ignoreCase = true)   -> AnimeStatus.ONGOING
        raw.contains("completed", ignoreCase = true) -> AnimeStatus.COMPLETED
        else -> AnimeStatus.UNKNOWN
    }
}

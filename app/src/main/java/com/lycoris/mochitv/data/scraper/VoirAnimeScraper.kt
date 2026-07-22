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

private const val BASE_URL = "https://voir-anime.to/"

/**
 * Scraper for [https://voir-anime.to/].
 *
 * This site is predominantly French; the Accept-Language header is set
 * accordingly to receive French-language metadata.
 *
 * ## CSS selectors (update when the site's HTML changes)
 * | Page          | Elements          | Notes                               |
 * |---------------|-------------------|-------------------------------------|
 * | Search results| `.film-item`      | Each card is one series             |
 * | Anime title   | `.film-name`      | Inside each card                    |
 * | Thumbnail     | `img[data-src]`   | Lazy-loaded; fall back to `img[src]`|
 * | Episode list  | `.ep-item`        | On the anime detail page            |
 * | Stream source | `iframe[src]`     | Strategy 1 (preferred)              |
 * | Stream source | `file:`           | Strategy 2 (JWPlayer JS key)        |
 */
class VoirAnimeScraper @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : AnimeScraper {

    override val site: SourceSite = SourceSite.VOIR_ANIME

    // -------------------------------------------------------------------------
    // AnimeScraper implementation
    // -------------------------------------------------------------------------

    override suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        val url = "${BASE_URL}?s=${query.trim().replace(" ", "+")}"
        val doc = fetchDocument(url)

        doc.select(".film-item").map { card ->
            val href = card.select("a[href]").attr("abs:href")
            val slug = href.trimEnd('/').substringAfterLast('/')

            // Voir-anime lazy-loads thumbnails via data-src attribute
            val thumbnail = card.select("img[data-src]").attr("abs:data-src")
                .ifBlank { card.select("img").attr("abs:src") }

            Anime(
                id = "voir_$slug",
                title = card.select(".film-name").text().ifBlank { slug },
                thumbnailUrl = thumbnail,
                synopsis = card.select(".film-synopsis").text(),
                genres = card.select(".genre-tag").map { it.text() },
                rating = card.select(".film-rating").text().toFloatOrNull(),
                episodeCount = card.select(".ep-count").text().toIntOrNull(),
                status = parseStatus(card.select(".film-status").text()),
                sourceUrl = href,
                sourceSite = SourceSite.VOIR_ANIME,
            )
        }
    }

    override suspend fun getEpisodes(animePageUrl: String): List<Episode> = withContext(Dispatchers.IO) {
        val doc = fetchDocument(animePageUrl)
        val animeSlug = animePageUrl.trimEnd('/').substringAfterLast('/')

        doc.select(".ep-item").mapIndexed { index, element ->
            val href = element.select("a[href]").attr("abs:href")
            val epNum = element.select(".ep-number").text().toIntOrNull() ?: (index + 1)

            Episode(
                id = "voir_ep_${animeSlug}_$epNum",
                animeId = "voir_$animeSlug",
                number = epNum,
                title = element.select(".ep-title").text().ifBlank { null },
                thumbnailUrl = element.select("img[data-src]").attr("abs:data-src")
                    .ifBlank { element.select("img").attr("abs:src").ifBlank { null } },
                durationSeconds = null,
                pageUrl = href,
                sourceSite = SourceSite.VOIR_ANIME,
            )
        }
    }

    override suspend fun getStreamUrl(episodePageUrl: String): String = withContext(Dispatchers.IO) {
        val doc = fetchDocument(episodePageUrl)

        // Strategy 1: <iframe src="..."> embed
        doc.select("iframe[src]").firstOrNull()
            ?.attr("abs:src")
            ?.takeIf { it.isNotBlank() }
            ?.let { return@withContext it }

        // Strategy 2: JWPlayer object  →  file:"https://...m3u8"
        val scripts = doc.select("script").joinToString("\n") { it.data() }
        Regex("""file\s*:\s*["']([^"']+\.(?:mp4|m3u8|mkv)[^"']*)["']""")
            .find(scripts)
            ?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() }
            ?.let { return@withContext it }

        // Strategy 3: plain JS string assignment  →  var src = "https://..."
        Regex("""(?:src|videoUrl|streamUrl)\s*=\s*["']([^"']+)["']""")
            .find(scripts)
            ?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() }
            ?.let { return@withContext it }

        error("VoirAnimeScraper: could not extract stream URL from $episodePageUrl")
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun fetchDocument(url: String): Document {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UserAgents.random())
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            // French Accept-Language to receive proper localised content
            .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8")
            .header("Referer", BASE_URL)
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "VoirAnimeScraper: HTTP ${response.code} fetching $url"
            }
            Jsoup.parse(response.body!!.string(), BASE_URL)
        }
    }

    private fun parseStatus(raw: String): AnimeStatus = when {
        raw.contains("en cours", ignoreCase = true)  -> AnimeStatus.ONGOING
        raw.contains("terminé", ignoreCase = true)   -> AnimeStatus.COMPLETED
        raw.contains("ongoing", ignoreCase = true)   -> AnimeStatus.ONGOING
        raw.contains("completed", ignoreCase = true) -> AnimeStatus.COMPLETED
        else -> AnimeStatus.UNKNOWN
    }
}

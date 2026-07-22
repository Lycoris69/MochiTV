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

/**
 * Scraper for the Anime Sama catalogue.
 *
 * The original domain listed in the project brief, [https://flixnet.site/animesama/], is a
 * thin HTML shell that embeds the real site in a full-page `<iframe>`.  All content lives at
 * [https://animesamatv.su/], which we target directly.
 *
 * The site runs the custom "animestream" WordPress theme.
 *
 * ## Verified CSS selectors (confirmed against live HTML, 2026-07-22)
 * | Page            | Selector                        | Value / notes                         |
 * |-----------------|---------------------------------|---------------------------------------|
 * | Search results  | `article.bs`                    | One card per series in `div.listupd`  |
 * | Card link       | `a.tip[href]`                   | Also carries `title` attribute        |
 * | Card title      | `div.tt`                        | Text node; first line before `<h2>`   |
 * | Card thumbnail  | `img.thumb`                     | Direct `src` (no lazy-load attr)      |
 * | Card status     | `span.epx`                      | "Ongoing" / "Completed"               |
 * | Episode list    | `div.eplister ul li`            | `data-index` attribute on each `<li>` |
 * | Episode number  | `div.epl-num`                   | Plain integer text                    |
 * | Episode title   | `div.epl-title`                 | May repeat the number ("Episode 5")   |
 * | Episode link    | `a[href]`                       | Inside each `<li>`                    |
 *
 * Synopsis, genres, rating, and episode count are **not present** in search-result cards.
 * They are available on individual anime detail pages (not yet scraped here).
 */
class FlixnetScraper @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : AnimeScraper {

    override val site: SourceSite = SourceSite.FLIXNET

    // -------------------------------------------------------------------------
    // AnimeScraper implementation
    // -------------------------------------------------------------------------

    override suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        // Note: flixnet.site/animesama/ is only an iframe wrapper; we query
        // animesamatv.su directly so Jsoup can parse the real search results.
        val url = "${BASE_URL}?s=${query.trim().replace(" ", "+")}"
        val doc = fetchDocument(url)

        doc.select("article.bs").map { card ->
            val anchor = card.select("a.tip").first()
            val href = anchor?.attr("abs:href") ?: ""
            val slug = href.trimEnd('/').substringAfterLast('/')

            // The title appears as a text node inside div.tt before the <h2> child.
            // Jsoup's .text() collapses all text nodes inside the element, giving us
            // the title twice ("ONE PIECE ONE PIECE").  ownText() returns only the
            // direct text node, which is the clean title.
            val title = card.select("div.tt").first()?.ownText()?.trim()
                ?: anchor?.attr("title")?.trim()
                ?: slug

            Anime(
                id = "flixnet_$slug",
                title = title.ifBlank { slug },
                thumbnailUrl = card.select("img.thumb").attr("abs:src"),
                synopsis = "",       // Not available in search cards
                genres = emptyList(), // Not available in search cards
                rating = null,        // Not available in search cards
                episodeCount = null,  // Not available in search cards
                status = parseStatus(card.select("span.epx").text()),
                sourceUrl = href,
                sourceSite = SourceSite.FLIXNET,
            )
        }
    }

    override suspend fun getEpisodes(animePageUrl: String): List<Episode> = withContext(Dispatchers.IO) {
        val doc = fetchDocument(animePageUrl)
        val animeSlug = animePageUrl.trimEnd('/').substringAfterLast('/')

        // Episode list: div.eplister > ul > li[data-index]
        // Each li contains: a[href], div.epl-num, div.epl-title, div.epl-date
        doc.select("div.eplister ul li").mapIndexed { index, element ->
            val href = element.select("a[href]").attr("abs:href")
            val epNum = element.select("div.epl-num").text().trim().toIntOrNull() ?: (index + 1)
            val epTitle = element.select("div.epl-title").text().trim().ifBlank { null }

            Episode(
                id = "flixnet_ep_${animeSlug}_$epNum",
                animeId = "flixnet_$animeSlug",
                number = epNum,
                title = epTitle,
                thumbnailUrl = null, // Not present in episode list
                durationSeconds = null,
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

    /**
     * Flixnet's homepage (flixnet.site/animesama/) is a full-page iframe shell
     * with no anime catalogue content of its own.  Latest-releases are sourced
     * exclusively from [VoirAnimeScraper]; this implementation is intentionally
     * a no-op so the Hilt multi-binding stays consistent.
     */
    override suspend fun getLatestReleases(): List<Anime> = emptyList()

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

    private companion object {
        // The real content domain; flixnet.site/animesama/ is only an iframe wrapper.
        const val BASE_URL = "https://animesamatv.su/"
    }
}

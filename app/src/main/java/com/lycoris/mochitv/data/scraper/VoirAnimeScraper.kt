package com.lycoris.mochitv.data.scraper

import com.lycoris.mochitv.data.network.UserAgents
import com.lycoris.mochitv.data.scraper.extractor.GenericExtractor
import com.lycoris.mochitv.data.scraper.extractor.StreamExtractor
import com.lycoris.mochitv.data.scraper.extractor.StreamtapeExtractor
import com.lycoris.mochitv.data.scraper.extractor.VidmolyExtractor
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
 * The site runs the Madara WordPress theme (wp-manga custom post type).
 * All pages are server-side rendered; no JS execution is required.
 *
 * This site is predominantly French; the Accept-Language header is set
 * accordingly so that French-language metadata is returned.
 *
 * ## Verified CSS selectors (confirmed against live HTML, 2026-07-22)
 * | Page            | Selector                                          | Notes                                  |
 * |-----------------|---------------------------------------------------|----------------------------------------|
 * | Search results  | `div.row.c-tabs-item__content`                    | One row per series; all rows share a single `div.c-tabs-item` parent |
 * | Card link/title | `div.post-title a[href]`                          | Both href and display title            |
 * | Card thumbnail  | `div.tab-thumb img`                               | Standard `src` attribute (no lazy)     |
 * | Card genres     | `div.post-content_item.mg_genres .summary-content a` | Each genre is a link                |
 * | Card status     | `div.post-content_item.mg_status .summary-content`| "TERMINÉ" / "EN COURS" (French)       |
 * | Episode list    | `li.wp-manga-chapter`                             | Madara theme; episodes in static HTML  |
 * | Episode link    | `a[href]`                                         | Inside each `<li>`                     |
 * | Episode number  | Extracted from URL slug via last `\d+` match      | e.g. "naruto-220-vostfr" → 220         |
 * | Episode title   | `a` element text (trimmed)                        | e.g. "Naruto - 220 VOSTFR - 220"       |
 * | Latest releases | `div.page-item-detail`                            | Homepage widget; one card per series   |
 * | Latest thumb    | `div.item-thumb img[src]`                         | Direct `src`, no lazy-load             |
 * | Latest title    | `div.post-title.font-title a[href]`               | Also carries the anime page URL        |
 *
 * Synopsis, rating, and episode count are **not present** in search-result or
 * latest-release cards. The search URL pattern `?s={query}` is correct
 * (WordPress native search). Latest releases are sourced from the site root `/`.
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

        // The Madara theme wraps ALL results in ONE div.c-tabs-item parent.
        // Each individual result is a div.row.c-tabs-item__content inside that parent.
        doc.select("div.row.c-tabs-item__content").map { card ->
            val href = card.select("div.post-title a[href]").attr("abs:href")
            val slug = href.trimEnd('/').substringAfterLast('/')

            val thumbImg = card.select("div.tab-thumb img").firstOrNull()
            val thumbnail = bestThumbnail(thumbImg)

            val genres = card.select("div.post-content_item.mg_genres .summary-content a")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }

            val statusText = card.select("div.post-content_item.mg_status .summary-content")
                .text().trim()

            Anime(
                id = "voir_$slug",
                title = card.select("div.post-title a").text().trim().ifBlank { slug },
                thumbnailUrl = thumbnail,
                synopsis = "",       // Not available in search cards
                genres = genres,
                rating = null,        // Not available in search cards
                episodeCount = null,  // Not available in search cards
                status = parseStatus(statusText),
                sourceUrl = href,
                sourceSite = SourceSite.VOIR_ANIME,
            )
        }
    }

    override suspend fun getEpisodes(animePageUrl: String): List<Episode> = withContext(Dispatchers.IO) {
        val doc = fetchDocument(animePageUrl)
        val animeSlug = animePageUrl.trimEnd('/').substringAfterLast('/')

        // Madara theme episode list: ul.version-chap > li.wp-manga-chapter
        // The episode number is not in a dedicated element; it's embedded in the
        // URL slug (e.g. "naruto-220-vostfr") and in the link text.  We extract
        // it from the slug's last numeric group for reliability.
        doc.select("li.wp-manga-chapter").mapIndexed { index, element ->
            val href = element.select("a[href]").attr("abs:href")
            val epSlug = href.trimEnd('/').substringAfterLast('/')
            val epNum = Regex("""\d+""").findAll(epSlug).lastOrNull()?.value?.toIntOrNull()
                ?: (index + 1)
            val epTitle = element.select("a").text().trim().ifBlank { null }

            // Madara theme: release date is inside .chapter-release-date or .chapter-date,
            // typically wrapped in an <i> element. Try the most specific selector first.
            val releaseDate = element
                .select(".chapter-release-date i, .chapter-date i, .chapter-release-date, .chapter-date")
                .firstOrNull()
                ?.text()
                ?.trim()
                ?.ifBlank { null }

            Episode(
                id = "voir_ep_${animeSlug}_$epNum",
                animeId = "voir_$animeSlug",
                number = epNum,
                title = epTitle,
                thumbnailUrl = null, // Not present in episode list rows
                durationSeconds = null,
                pageUrl = href,
                sourceSite = SourceSite.VOIR_ANIME,
                releaseDate = releaseDate,
            )
        }
    }

    override suspend fun getStreamUrl(episodePageUrl: String): String = withContext(Dispatchers.IO) {
        val episodePage = fetchDocument(episodePageUrl)

        // ---------------------------------------------------------------------------
        // Two-step extraction: episode page → embed page → direct stream URL
        // ---------------------------------------------------------------------------

        // Step 1: resolve the first iframe embed URL from the episode page.
        //
        // voir-anime.to (Madara theme) exposes all available player sources in the
        // `thisChapterSources` JS variable, with the primary/default player rendered
        // as a plain <iframe> element in the DOM.  We take the first iframe.
        val embedUrl = episodePage.select("iframe[src]").firstOrNull()
            ?.attr("abs:src")
            ?.takeIf { it.isNotBlank() }

        if (embedUrl != null) {
            // Step 2: fetch the embed page and run extractors in priority order.
            runCatching {
                val embedHtml = fetchRaw(embedUrl, referer = episodePageUrl)
                val extractors: List<StreamExtractor> = listOf(
                    VidmolyExtractor,    // vidmoly.biz  — JWPlayer sources array
                    StreamtapeExtractor, // streamtape.com — robotlink JS obfuscation
                    GenericExtractor,    // any host      — file:"..." / var fileUrl = "..."
                )
                for (extractor in extractors) {
                    val url = extractor.extract(embedUrl, embedHtml)
                    if (!url.isNullOrBlank()) return@withContext url
                }
            }
            // If the embed page fetch or all extractors failed, fall through to legacy strategies.
        }

        // ---------------------------------------------------------------------------
        // Legacy fallback: scan the episode page scripts directly.
        // Handles sites that embed the stream URL in the page itself rather than in
        // a nested iframe embed page.
        // ---------------------------------------------------------------------------
        val scripts = episodePage.select("script").joinToString("\n") { it.data() }

        // Legacy strategy 2: JWPlayer file:"https://...m3u8"
        Regex("""file\s*:\s*["']([^"']+\.(?:mp4|m3u8|mkv)[^"']*)["']""")
            .find(scripts)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?.let { return@withContext it }

        // Legacy strategy 3: plain JS variable  →  var src = "https://..."
        Regex("""(?:src|videoUrl|streamUrl)\s*=\s*["']([^"']+)["']""")
            .find(scripts)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?.let { return@withContext it }

        error("VoirAnimeScraper: could not extract stream URL from $episodePageUrl")
    }

    override suspend fun getAnimeSynopsis(animePageUrl: String): String = withContext(Dispatchers.IO) {
        val doc = fetchDocument(animePageUrl)
        // Madara theme: synopsis lives in div.summary__content (may contain <p> children)
        doc.select("div.summary__content").text().trim()
            .ifBlank { doc.select("div.description-summary").text().trim() }
    }

    /**
     * Fetches a paginated latest-releases page and returns the anime cards.
     *
     * URL pattern: `https://voir-anime.to/` (page 1) or `https://voir-anime.to/page/{n}/`
     * Selectors (confirmed 2026-07-22):
     *  - Card root : `div.page-item-detail`
     *  - Thumbnail : `div.item-thumb img` → `src` (no lazy-load)
     *  - Title/URL : `div.post-title.font-title a[href]`
     *
     * Returns an empty list when [page] exceeds available pages (end of feed).
     */
    override suspend fun getLatestReleases(page: Int): List<Anime> = withContext(Dispatchers.IO) {
        val url = if (page == 1) BASE_URL else "${BASE_URL}page/$page/"
        val doc = fetchDocument(url)

        doc.select("div.page-item-detail").mapNotNull { card ->
            val anchor = card.select("div.post-title.font-title a[href]").firstOrNull()
                ?: return@mapNotNull null

            val href = anchor.attr("abs:href").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null

            val slug = href.trimEnd('/').substringAfterLast('/')
            val title = anchor.text().trim().ifBlank { slug }

            val imgEl = card.select("div.item-thumb img").firstOrNull()
            val thumbnail = bestThumbnail(imgEl)

            // Best-effort date extraction from Madara theme chapter list widget.
            // The homepage card typically shows the latest chapter + its date in:
            //   div.list-chapter > div.chapter-item > span.post-date
            // Fall back through progressively broader selectors; yield null on failure.
            val dateString =
                card.select("div.chapter-item span.post-date").firstOrNull()?.text()?.trim()?.ifBlank { null }
                    ?: card.select("span.post-date").firstOrNull()?.text()?.trim()?.ifBlank { null }
                    ?: card.select("div.list-chapter .chapter-item span:not(.chapter)").firstOrNull()
                        ?.text()?.trim()?.ifBlank { null }

            Anime(
                id = "voir_$slug",
                title = title,
                thumbnailUrl = thumbnail,
                synopsis = "",        // Not available in homepage cards
                genres = emptyList(), // Not available in homepage cards
                rating = null,        // Not available in homepage cards
                episodeCount = null,  // Not available in homepage cards
                status = AnimeStatus.UNKNOWN,
                sourceUrl = href,
                sourceSite = SourceSite.VOIR_ANIME,
                lastReleaseDate = dateString,
            )
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the highest-resolution URL available for [imgEl].
     *
     * Parses the `srcset` attribute ("url 460w, url 110w, …"), picks the entry
     * with the largest width descriptor, and falls back to plain `src` if
     * `srcset` is absent or unparseable.
     */
    private fun bestThumbnail(imgEl: org.jsoup.nodes.Element?): String {
        imgEl ?: return ""
        val srcset = imgEl.attr("srcset").takeIf { it.isNotBlank() }
        if (srcset != null) {
            val best = srcset.split(",")
                .mapNotNull { entry ->
                    val parts = entry.trim().split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        val w = parts.last().removeSuffix("w").toIntOrNull() ?: 0
                        parts.first() to w
                    } else null
                }
                .maxByOrNull { it.second }
                ?.first
            if (!best.isNullOrBlank()) {
                // srcset URLs are already absolute on this site; make sure
                return if (best.startsWith("http")) best
                else "$BASE_URL${best.trimStart('/')}"
            }
        }
        return (imgEl.attr("abs:data-src").takeIf { it.isNotBlank() }
            ?: imgEl.attr("abs:src"))
    }

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

    /**
     * Fetches [url] and returns the raw response body as a string.
     *
     * Used for embed pages where we only need plain text/HTML for regex matching
     * (no Jsoup DOM parsing required).  The [referer] is passed as the HTTP
     * `Referer` header so that embed hosts that check the origin accept the request.
     *
     * @throws IllegalStateException if the HTTP response is not 2xx.
     */
    private fun fetchRaw(url: String, referer: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UserAgents.random())
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8")
            .header("Referer", referer)
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "VoirAnimeScraper: HTTP ${response.code} fetching embed page $url"
            }
            response.body!!.string()
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

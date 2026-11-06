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
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject

private const val BASE_URL = "https://voiranime.rip/"

/**
 * Scraper for [https://voiranime.rip/].
 *
 * The site was rebuilt (it used to be a Madara WordPress site at voir-anime.to).
 * It is now a server-rendered custom site with an Anime-Sama-like layout.
 *
 * ## Key endpoints
 * | Operation       | Method | URL                                                          |
 * |-----------------|--------|--------------------------------------------------------------|
 * | Search          | POST   | `/template-php/defaut/fetch.php` · body: `query=<term>`      |
 * | Anime page      | GET    | `/{slug}/`                                                   |
 * | Season page     | GET    | `/{slug}/{season}/` (lists every episode)                    |
 * | Episode page    | GET    | `/{slug}/{season}/episode-{n}/{vostfr|vf}/`                  |
 * | Latest releases | GET    | `/` (homepage sliders, only one page)                        |
 *
 * ## Selectors (confirmed against live HTML, 2026-11-06)
 * | Data            | Selector / source                                            |
 * |-----------------|--------------------------------------------------------------|
 * | Search result   | `a.va-search-result` (+ `.va-search-result-title`, `img`)    |
 * | Anime title     | `h1.hero-title`                                              |
 * | Synopsis        | `#synopsisText`                                              |
 * | Genres          | `.hero-genres .genre-link`                                   |
 * | Seasons         | `a.season-card[href]`                                        |
 * | Episodes        | `a.episode-card[href]` (`data-langs="vostfr vf"`)            |
 * | Players         | `const readers = {"vostfr":[...],"vf":[...]}` in episode page|
 * | Latest cards    | `div.xcard` (`a[href]`, `img.xcard-img`, `.xcard-title`)     |
 */
class VoirAnimeScraper @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : AnimeScraper {

    override val site: SourceSite = SourceSite.VOIR_ANIME

    // -------------------------------------------------------------------------
    // AnimeScraper implementation
    // -------------------------------------------------------------------------

    override suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("query", query.trim()).build()
        val request = Request.Builder()
            .url("${BASE_URL}template-php/defaut/fetch.php")
            .post(body)
            .header("User-Agent", UserAgents.random())
            .header("Referer", BASE_URL)
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        val html = okHttpClient.newCall(request).execute().use { resp ->
            check(resp.isSuccessful) { "VoirAnimeScraper: HTTP ${resp.code} on search" }
            resp.body!!.string()
        }

        Jsoup.parseBodyFragment(html, BASE_URL).select("a.va-search-result").mapNotNull { el ->
            val href = el.attr("abs:href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val slug = slugOf(href)
            if (slug.isBlank()) return@mapNotNull null
            Anime(
                id = "voir_$slug",
                title = el.select(".va-search-result-title").text().trim().ifBlank { slug },
                thumbnailUrl = el.select("img").attr("abs:src"),
                synopsis = "",
                genres = emptyList(),
                rating = null,
                episodeCount = null,
                status = AnimeStatus.UNKNOWN,
                sourceUrl = href,
                sourceSite = SourceSite.VOIR_ANIME,
            )
        }
    }

    /**
     * Loads the anime page, picks the first season and lists its episodes.
     * Each episode links to its VOSTFR page (or VF when VOSTFR is not offered).
     */
    override suspend fun getEpisodes(animePageUrl: String): List<Episode> = withContext(Dispatchers.IO) {
        val animePage = fetchDocument(animePageUrl)
        val animeSlug = slugOf(animePageUrl)

        val seasonUrl = animePage.select("a.season-card[href]").firstOrNull()?.attr("abs:href")
            ?: return@withContext emptyList()

        val seasonPage = fetchDocument(seasonUrl)
        seasonPage.select("a.episode-card[href]").mapIndexed { index, card ->
            var href = card.attr("abs:href")
            val langs = card.attr("data-langs").split(" ").filter { it.isNotBlank() }
            if (langs.isNotEmpty() && "vostfr" !in langs && "vf" in langs) {
                href = href.replace("/vostfr/", "/vf/")
            }
            val epNum = Regex("""episode-(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                ?: (index + 1)

            Episode(
                id = "voir_ep_${animeSlug}_$epNum",
                animeId = "voir_$animeSlug",
                number = epNum,
                title = card.select(".episode-title").text().trim().ifBlank { null },
                thumbnailUrl = null,
                durationSeconds = null,
                pageUrl = href,
                sourceSite = SourceSite.VOIR_ANIME,
                releaseDate = null,
            )
        }
    }

    override suspend fun getStreamUrl(episodePageUrl: String): String = withContext(Dispatchers.IO) {
        val episodePage = fetchDocument(episodePageUrl)
        val embedUrls = collectAllEmbedUrls(episodePage, episodePageUrl)

        val extractors: List<StreamExtractor> = listOf(
            VidmolyExtractor,
            StreamtapeExtractor,
            GenericExtractor,
        )

        for (embedUrl in embedUrls) {
            val result = runCatching {
                val embedHtml = fetchRaw(embedUrl, referer = episodePageUrl)
                extractors.firstNotNullOfOrNull { it.extract(embedUrl, embedHtml) }
            }.getOrNull()
            if (!result.isNullOrBlank()) return@withContext result
        }

        error("VoirAnimeScraper: could not extract stream URL from $episodePageUrl")
    }

    /**
     * Collects candidate embed URLs from an episode page, in priority order:
     *
     * 1. **`readers` JS object** — `{"vostfr":[urls],"vf":[urls]}`. The language
     *    matching the page URL comes first, the other language is a fallback.
     * 2. Static `<iframe src>` / `data-src`.
     *
     * Results are deduplicated while preserving order.
     */
    private fun collectAllEmbedUrls(doc: Document, pageUrl: String): List<String> {
        val seen = linkedSetOf<String>()
        fun add(url: String) {
            val u = if (url.startsWith("//")) "https:$url" else url
            if (u.startsWith("http")) seen.add(u)
        }

        val scripts = doc.select("script").joinToString("\n") { it.data() }
        val readersJson = Regex("""readers\s*=\s*\{(.*?)\}\s*;""", RegexOption.DOT_MATCHES_ALL)
            .find(scripts)?.groupValues?.get(1)
        if (readersJson != null) {
            val preferred = if ("/vf/" in pageUrl) "vf" else "vostfr"
            val byLang = Regex(""""(\w+)"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
                .findAll(readersJson)
                .associate { m ->
                    m.groupValues[1] to Regex(""""([^"]+)"""").findAll(m.groupValues[2])
                        .map { it.groupValues[1].replace("\\/", "/") }
                        .toList()
                }
            byLang[preferred]?.forEach { add(it) }
            byLang.filterKeys { it != preferred }.values.flatten().forEach { add(it) }
        }

        doc.select("iframe").forEach { el ->
            add(el.attr("abs:src"))
            add(el.attr("abs:data-src"))
        }

        return seen.toList()
    }

    override suspend fun getAnimeSynopsis(animePageUrl: String): String = withContext(Dispatchers.IO) {
        val doc = fetchDocument(animePageUrl)
        doc.select("#synopsisText").text().trim()
            .ifBlank { doc.select("meta[name=description]").attr("content").trim() }
    }

    /**
     * Latest releases come from the homepage sliders (`div.xcard`). The site
     * offers a single page, so [page] > 1 returns an empty list (end of feed).
     * Cards appear in several sliders, so they are deduplicated by slug.
     */
    override suspend fun getLatestReleases(page: Int): List<Anime> = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext emptyList()
        val doc = fetchDocument(BASE_URL)

        doc.select("div.xcard").mapNotNull { card ->
            val href = card.select("a[href]").firstOrNull()?.attr("abs:href")
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val slug = slugOf(href)
            if (slug.isBlank()) return@mapNotNull null

            val imgEl = card.select("img.xcard-img").firstOrNull()
            Anime(
                id = "voir_$slug",
                title = card.select(".xcard-title").text().trim().ifBlank { slug },
                thumbnailUrl = imgEl?.attr("abs:src").orEmpty(),
                synopsis = "",
                genres = emptyList(),
                rating = null,
                episodeCount = null,
                status = AnimeStatus.UNKNOWN,
                sourceUrl = href,
                sourceSite = SourceSite.VOIR_ANIME,
                lastReleaseDate = null,
            )
        }.distinctBy { it.id }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** First path segment of a site URL: `https://host/naruto/kai/` → `naruto`. */
    private fun slugOf(url: String): String =
        url.removePrefix(BASE_URL).trim('/').substringBefore('/')

    private fun fetchDocument(url: String): Document {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UserAgents.random())
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
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

    /** Fetches an embed page as plain text, sending [referer] for hosts that check origin. */
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
}

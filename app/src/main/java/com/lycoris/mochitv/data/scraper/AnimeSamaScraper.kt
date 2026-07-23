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

private const val BASE_URL = "https://anime-sama.to/"

/**
 * Scraper for [https://anime-sama.to/].
 *
 * A large French-language anime catalogue.  Pages are server-side rendered;
 * episode embed URLs live in a per-season `episodes.js` file.
 *
 * ## Key endpoints
 * | Operation       | Method | URL                                                        |
 * |-----------------|--------|------------------------------------------------------------|
 * | Search          | POST   | `/template-php/defaut/fetch.php` · body: `query=<term>`    |
 * | Anime catalogue | GET    | `/catalogue/{slug}/`                                       |
 * | Episode embeds  | GET    | `/catalogue/{slug}/{season}/{lang}/episodes.js`            |
 * | Latest releases | GET    | `/` (homepage "Derniers ajouts" section)                   |
 *
 * ## Verified selectors (confirmed 2026-07-23)
 * | Data            | Selector / source                                          |
 * |-----------------|------------------------------------------------------------|
 * | Search result   | `a.asn-search-result`                                      |
 * | Search title    | `h3.asn-search-result-title`                               |
 * | Search thumb    | `img.asn-search-result-img[src]`                           |
 * | Anime title     | `<h1>`                                                     |
 * | Anime thumbnail | `<meta property="og:image" content="...">`                 |
 * | Synopsis        | `<meta name="description" content="...">`                  |
 * | Genres          | `span.genre-pill`                                          |
 * | Status          | first `span.info-val` text ("Terminé" / "En cours")        |
 * | Seasons         | `panneauAnime("label", "relativeUrl")` in inline JS        |
 * | Episode embeds  | `var eps1 = ['url', ...]` in `episodes.js`                 |
 * | Latest card     | `div.anime-card-premium` on homepage                       |
 * | Latest title    | `h2.card-title`                                            |
 * | Latest thumb    | `img.card-image[src]`                                      |
 */
class AnimeSamaScraper @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : AnimeScraper {

    override val site: SourceSite = SourceSite.ANIME_SAMA

    // -------------------------------------------------------------------------
    // AnimeScraper implementation
    // -------------------------------------------------------------------------

    override suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("query", query.trim())
            .build()

        val request = Request.Builder()
            .url("${BASE_URL}template-php/defaut/fetch.php")
            .post(body)
            .header("User-Agent", UserAgents.random())
            .header("Referer", BASE_URL)
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        val html = okHttpClient.newCall(request).execute().use { resp ->
            check(resp.isSuccessful) { "AnimeSamaScraper: HTTP ${resp.code} on search" }
            resp.body!!.string()
        }

        Jsoup.parseBodyFragment(html).select("a.asn-search-result").map { el ->
            val href = el.attr("abs:href").takeIf { it.isNotBlank() }
                ?: (BASE_URL.trimEnd('/') + el.attr("href"))
            val slug = href.trimEnd('/').substringAfterLast('/')
            val title = el.select("h3.asn-search-result-title").text().trim().ifBlank { slug }
            val thumb = el.select("img.asn-search-result-img").attr("src")

            Anime(
                id = "animesama_$slug",
                title = title,
                thumbnailUrl = thumb,
                synopsis = "",
                genres = emptyList(),
                rating = null,
                episodeCount = null,
                status = AnimeStatus.UNKNOWN,
                sourceUrl = href,
                sourceSite = SourceSite.ANIME_SAMA,
            )
        }
    }

    /**
     * Fetches the catalogue page for [animePageUrl], picks the first available
     * season, then loads its `episodes.js` to get the embed URL list.
     *
     * Each [Episode.pageUrl] is set to the Vidmoly (or other host) embed URL
     * directly — [getStreamUrl] receives it and extracts the `.m3u8` / `.mp4`.
     */
    override suspend fun getEpisodes(animePageUrl: String): List<Episode> = withContext(Dispatchers.IO) {
        val doc = fetchDocument(animePageUrl)
        val animeSlug = animePageUrl.trimEnd('/').substringAfterLast('/')

        // Parse panneauAnime("label", "relativeUrl") calls from inline JS.
        // Prefer VOSTFR seasons; fall back to the first listed season.
        val seasonUrl = pickSeasonUrl(doc, animePageUrl) ?: return@withContext emptyList()

        // Load the per-season episode embed list.
        val epsJs = fetchRaw("${seasonUrl}episodes.js", referer = animePageUrl)

        // Parse:  var eps1 = ['url1', 'url2', ...];
        val embedUrls = Regex("""var\s+eps1\s*=\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            .find(epsJs)
            ?.groupValues?.get(1)
            ?.let { parseJsStringArray(it) }
            ?: emptyList()

        embedUrls.mapIndexed { index, embedUrl ->
            val epNum = index + 1
            Episode(
                id = "animesama_ep_${animeSlug}_$epNum",
                animeId = "animesama_$animeSlug",
                number = epNum,
                title = null,
                thumbnailUrl = null,
                durationSeconds = null,
                pageUrl = embedUrl,
                sourceSite = SourceSite.ANIME_SAMA,
            )
        }
    }

    /**
     * [episodePageUrl] is the embed URL (e.g. `https://vidmoly.to/embed-xxx.html`)
     * returned directly by [getEpisodes].  We fetch it and run extractors to get
     * the direct `.m3u8` / `.mp4` stream URL.
     */
    override suspend fun getStreamUrl(episodePageUrl: String): String = withContext(Dispatchers.IO) {
        val html = fetchRaw(episodePageUrl, referer = BASE_URL)
        val extractors: List<StreamExtractor> = listOf(
            VidmolyExtractor,
            StreamtapeExtractor,
            GenericExtractor,
        )
        for (extractor in extractors) {
            val url = extractor.extract(episodePageUrl, html)
            if (!url.isNullOrBlank()) return@withContext url
        }
        error("AnimeSamaScraper: could not extract stream URL from $episodePageUrl")
    }

    override suspend fun getAnimeSynopsis(animePageUrl: String): String = withContext(Dispatchers.IO) {
        val doc = fetchDocument(animePageUrl)
        doc.select("meta[name=description]").attr("content").trim()
    }

    override suspend fun getLatestReleases(page: Int): List<Anime> = withContext(Dispatchers.IO) {
        // The homepage loads all recent additions in one page; no pagination.
        if (page > 1) return@withContext emptyList()
        val doc = fetchDocument(BASE_URL)

        // "Derniers ajouts" section — cards directly under the section heading.
        // All homepage cards share the same class; we grab them all.
        doc.select("div.anime-card-premium").mapNotNull { card ->
            val anchor = card.select("a[href]").firstOrNull() ?: return@mapNotNull null
            val href = anchor.attr("abs:href").takeIf { it.isNotBlank() } ?: return@mapNotNull null

            // URL pattern: /catalogue/{slug}/{season}/{lang}/
            // Extract the anime slug (second path segment after /catalogue/)
            val slug = href.trimEnd('/').split("/")
                .let { parts -> parts.getOrNull(parts.indexOf("catalogue") + 1) }
                ?: href.trimEnd('/').substringAfterLast('/')

            val title = card.select("h2.card-title").text().trim().ifBlank { slug }
            val thumb = card.select("img.card-image").attr("src")
            val catalogueUrl = "${BASE_URL}catalogue/$slug/"

            Anime(
                id = "animesama_$slug",
                title = title,
                thumbnailUrl = thumb,
                synopsis = "",
                genres = emptyList(),
                rating = null,
                episodeCount = null,
                status = AnimeStatus.UNKNOWN,
                sourceUrl = catalogueUrl,
                sourceSite = SourceSite.ANIME_SAMA,
            )
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Parses `panneauAnime("label", "relativeUrl")` calls from the catalogue
     * page's inline JS and returns the absolute URL of the preferred season.
     *
     * Preference order: VOSTFR season 1 → any VOSTFR → first available season.
     */
    private fun pickSeasonUrl(doc: Document, baseAnimeUrl: String): String? {
        val panneau = Regex("""panneauAnime\("([^"]+)",\s*"([^"]+)"\)""")
            .findAll(doc.html())
            .map { it.groupValues[1] to it.groupValues[2] }
            .filter { (label, url) -> label != "nom" && url != "url" }
            .toList()

        if (panneau.isEmpty()) return null

        val base = baseAnimeUrl.trimEnd('/') + "/"

        // Prefer saison1/vostfr, then any vostfr, then first entry
        val preferred = panneau.firstOrNull { (_, url) -> url == "saison1/vostfr" }
            ?: panneau.firstOrNull { (_, url) -> url.contains("vostfr") }
            ?: panneau.first()

        return base + preferred.second.trimStart('/') + "/"
    }

    /**
     * Parses a JS array body (the content between `[` and `]`) into a list of
     * strings.  Handles both single- and double-quoted entries.
     */
    private fun parseJsStringArray(body: String): List<String> =
        Regex("""['"]([^'"]+)['"]""")
            .findAll(body)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()

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
                "AnimeSamaScraper: HTTP ${response.code} fetching $url"
            }
            Jsoup.parse(response.body!!.string(), BASE_URL)
        }
    }

    private fun fetchRaw(url: String, referer: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UserAgents.random())
            .header("Accept", "*/*")
            .header("Referer", referer)
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "AnimeSamaScraper: HTTP ${response.code} fetching $url"
            }
            response.body!!.string()
        }
    }
}

package com.lycoris.mochitv.data.scraper

import com.lycoris.mochitv.data.network.UserAgents
import com.lycoris.mochitv.data.scraper.extractor.Embed4meExtractor
import com.lycoris.mochitv.data.scraper.extractor.GenericExtractor
import com.lycoris.mochitv.data.scraper.extractor.SibnetExtractor
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
 * | Episode embeds  | `var epsN = ['url', ...]` in `episodes.js` (eps1..eps5)    |
 * | Latest card     | `div.anime-card-premium` on homepage                       |
 * | Latest title    | `h2.card-title`                                            |
 * | Latest thumb    | `img.card-image[src]`                                      |
 */
class AnimeSamaScraper @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val embed4meExtractor: Embed4meExtractor,
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
     * season, then loads its `episodes.js`.
     *
     * episodes.js can define multiple player arrays (eps1…epsN).  We parse all of
     * them and pick the best URL per episode using [pickPreferredUrl]:
     *   sibnet (eps3) > vidmoly (eps2) > streamtape > anything else > embed4me
     */
    override suspend fun getEpisodes(animePageUrl: String): List<Episode> = withContext(Dispatchers.IO) {
        val doc = fetchDocument(animePageUrl)
        val animeSlug = animePageUrl.trimEnd('/').substringAfterLast('/')

        val seasonUrl = pickSeasonUrl(doc, animePageUrl) ?: return@withContext emptyList()

        val epsJs = fetchRaw("${seasonUrl}episodes.js", referer = animePageUrl)

        // Parse every epsN variable: { playerIndex -> listOf(embedUrls) }
        val allPlayers = Regex("""var\s+eps(\d+)\s*=\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            .findAll(epsJs)
            .associate { m -> m.groupValues[1].toInt() to parseJsStringArray(m.groupValues[2]) }

        if (allPlayers.isEmpty()) return@withContext emptyList()

        val episodeCount = allPlayers.values.maxOf { it.size }

        (0 until episodeCount).mapNotNull { idx ->
            val embedUrl = pickPreferredUrl(allPlayers, idx) ?: return@mapNotNull null
            val epNum = idx + 1
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
     * Resolves the direct stream URL for [episodePageUrl].
     *
     * - **sibnet**: fetch shell.php, extract the signed CDN path, follow the
     *   302 redirect (requires Referer: video.sibnet.ru) to get the final URL.
     * - **embed4me**: handed off to the WebView-based [Embed4meExtractor].
     * - **everything else**: tried through the standard extractor chain.
     */
    override suspend fun getStreamUrl(episodePageUrl: String): String = withContext(Dispatchers.IO) {
        when {
            "sibnet.ru" in episodePageUrl -> {
                val html = fetchRaw(episodePageUrl, referer = BASE_URL)
                val videoUrl = SibnetExtractor.extract(episodePageUrl, html)
                    ?: error("SibnetExtractor: could not find video URL in $episodePageUrl")
                // The video URL redirects to a signed CDN URL; follow it manually
                // because the redirect requires Referer: https://video.sibnet.ru/.
                followSibnetRedirect(videoUrl)
            }

            "embed4me.com" in episodePageUrl ->
                embed4meExtractor.extract(episodePageUrl)

            else -> {
                val html = fetchRaw(episodePageUrl, referer = BASE_URL)
                val extractors: List<StreamExtractor> = listOf(
                    VidmolyExtractor,
                    StreamtapeExtractor,
                    GenericExtractor,
                )
                extractors.firstNotNullOfOrNull { it.extract(episodePageUrl, html) }
                    ?: error("AnimeSamaScraper: could not extract stream URL from $episodePageUrl")
            }
        }
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
     * For a given episode [index], picks the best embed URL across all player
     * arrays.
     *
     * Priority: vidmoly > sibnet > streamtape > other > embed4me.
     *
     * Vidmoly is preferred over Sibnet because Vidmoly produces stable m3u8 URLs
     * with no time-limited expiry, making them reliable for both streaming and
     * offline downloads. Sibnet URLs involve a signed CDN redirect whose token can
     * expire before Media3 DownloadService finishes writing, causing silent download
     * failures. Sibnet remains as a fallback for episodes where no Vidmoly source
     * exists.
     */
    private fun pickPreferredUrl(allPlayers: Map<Int, List<String>>, index: Int): String? {
        val candidates = allPlayers.values
            .mapNotNull { urls -> urls.getOrNull(index)?.takeIf { it.isNotBlank() } }
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { "vidmoly" in it }
            ?: candidates.firstOrNull { "sibnet.ru" in it }
            ?: candidates.firstOrNull { "streamtape" in it }
            ?: candidates.firstOrNull { "embed4me.com" !in it }
            ?: candidates.first()
    }

    /**
     * Makes a HEAD request to [videoUrl] with the Referer sibnet expects, and
     * returns the Location from the 302 redirect — that is the signed CDN URL
     * that ExoPlayer can play directly without any special headers.
     */
    private fun followSibnetRedirect(videoUrl: String): String {
        val noFollowClient = okHttpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        val request = Request.Builder()
            .url(videoUrl)
            .head()
            .header("User-Agent", UserAgents.random())
            .header("Referer", "https://video.sibnet.ru/")
            .build()

        return noFollowClient.newCall(request).execute().use { resp ->
            val location = resp.header("Location")
                ?: error("SibnetExtractor: no redirect from $videoUrl (status ${resp.code})")
            when {
                location.startsWith("//") -> "https:$location"
                location.startsWith("/")  -> "https://video.sibnet.ru$location"
                else -> location
            }
        }
    }

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

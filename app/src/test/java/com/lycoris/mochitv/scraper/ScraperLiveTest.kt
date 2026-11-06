package com.lycoris.mochitv.scraper

import com.lycoris.mochitv.data.scraper.FlixnetScraper
import com.lycoris.mochitv.data.scraper.VoirAnimeScraper
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Live integration tests for [FlixnetScraper] and [VoirAnimeScraper].
 *
 * These tests hit real network endpoints.  They always pass (no assertions) —
 * their purpose is to produce diagnostic println output so CSS selectors can be
 * inspected and validated.  Run with:
 *
 *   ./gradlew test --tests "com.lycoris.mochitv.scraper.ScraperLiveTest" --info 2>&1 | tail -150
 */
class ScraperLiveTest {

    // -------------------------------------------------------------------------
    // Shared HTTP client — 30-second timeout, follows redirects
    // -------------------------------------------------------------------------
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val flixnet = FlixnetScraper(client)
    private val voirAnime = VoirAnimeScraper(client)

    // -------------------------------------------------------------------------
    // Raw HTML fetch — print first 3000 chars to inspect actual page structure
    // -------------------------------------------------------------------------

    @Test
    fun testFetchRawFlixnet() {
        println("\n========== FLIXNET RAW HTML (search: one piece) ==========")
        val html = fetchRaw(
            "https://animesamatv.su/?s=one+piece",
            "en-US,en;q=0.9",
            "https://animesamatv.su/",
        )
        println(html.take(3000))
        println("==========================================================\n")
    }

    @Test
    fun testFetchRawVoirAnime() {
        println("\n========== VOIR-ANIME RAW HTML (search: naruto) ==========")
        val html = fetchRaw(
            "https://voiranime.rip/",
            "fr-FR,fr;q=0.9,en;q=0.8",
            "https://voiranime.rip/",
        )
        println(html.take(3000))
        println("==========================================================\n")
    }

    // -------------------------------------------------------------------------
    // Search tests — print every field of all results
    // -------------------------------------------------------------------------

    @Test
    fun testSearchFlixnet() = runBlocking {
        println("\n========== FLIXNET search(\"one piece\") ==========")
        try {
            val results = flixnet.search("one piece")
            println("Result count: ${results.size}")
            results.take(5).forEachIndexed { i, anime ->
                println("""
  --- Result $i ---
  id:           ${anime.id}
  title:        ${anime.title}
  sourceUrl:    ${anime.sourceUrl}
  thumbnailUrl: ${anime.thumbnailUrl}
  status:       ${anime.status}
  synopsis:     ${anime.synopsis.take(80)}
  genres:       ${anime.genres}
  rating:       ${anime.rating}
  episodeCount: ${anime.episodeCount}
                """.trimIndent())
            }
            println("Validation: title non-blank = ${results.firstOrNull()?.title?.isNotBlank()}")
            println("Validation: sourceUrl non-blank = ${results.firstOrNull()?.sourceUrl?.isNotBlank()}")
        } catch (e: Exception) {
            println("FAILED: ${e.message}")
            e.printStackTrace()
        }
        println("=================================================\n")
    }

    @Test
    fun testSearchVoirAnime() = runBlocking {
        println("\n========== VOIR-ANIME search(\"naruto\") ==========")
        try {
            val results = voirAnime.search("naruto")
            println("Result count: ${results.size}")
            results.take(5).forEachIndexed { i, anime ->
                println("""
  --- Result $i ---
  id:           ${anime.id}
  title:        ${anime.title}
  sourceUrl:    ${anime.sourceUrl}
  thumbnailUrl: ${anime.thumbnailUrl}
  status:       ${anime.status}
  synopsis:     ${anime.synopsis.take(80)}
  genres:       ${anime.genres}
  rating:       ${anime.rating}
  episodeCount: ${anime.episodeCount}
                """.trimIndent())
            }
            println("Validation: title non-blank = ${results.firstOrNull()?.title?.isNotBlank()}")
            println("Validation: sourceUrl non-blank = ${results.firstOrNull()?.sourceUrl?.isNotBlank()}")
        } catch (e: Exception) {
            println("FAILED: ${e.message}")
            e.printStackTrace()
        }
        println("==================================================\n")
    }

    // -------------------------------------------------------------------------
    // Episode list tests — pick first search result, call getEpisodes
    // -------------------------------------------------------------------------

    @Test
    fun testGetEpisodesFlixnet() = runBlocking {
        println("\n========== FLIXNET getEpisodes ==========")
        // Use the known One Piece detail URL directly
        val animeUrl = "https://animesamatv.su/anime/one-piece/"
        println("Anime URL: $animeUrl")
        try {
            val episodes = flixnet.getEpisodes(animeUrl)
            println("Episode count: ${episodes.size}")
            episodes.take(5).forEachIndexed { i, ep ->
                println("""
  --- Episode $i ---
  id:       ${ep.id}
  number:   ${ep.number}
  title:    ${ep.title}
  pageUrl:  ${ep.pageUrl}
                """.trimIndent())
            }
            println("Validation: first ep number > 0 = ${episodes.firstOrNull()?.let { it.number > 0 }}")
            println("Validation: first ep pageUrl non-blank = ${episodes.firstOrNull()?.pageUrl?.isNotBlank()}")
        } catch (e: Exception) {
            println("FAILED: ${e.message}")
            e.printStackTrace()
        }
        println("=========================================\n")
    }

    @Test
    fun testGetEpisodesVoirAnime() = runBlocking {
        println("\n========== VOIR-ANIME getEpisodes ==========")
        // Use the known Naruto detail URL directly
        val animeUrl = "https://voiranime.rip/naruto/"
        println("Anime URL: $animeUrl")
        try {
            val episodes = voirAnime.getEpisodes(animeUrl)
            println("Episode count: ${episodes.size}")
            episodes.take(5).forEachIndexed { i, ep ->
                println("""
  --- Episode $i ---
  id:       ${ep.id}
  number:   ${ep.number}
  title:    ${ep.title}
  pageUrl:  ${ep.pageUrl}
                """.trimIndent())
            }
            println("Validation: first ep number > 0 = ${episodes.firstOrNull()?.let { it.number > 0 }}")
            println("Validation: first ep pageUrl non-blank = ${episodes.firstOrNull()?.pageUrl?.isNotBlank()}")
        } catch (e: Exception) {
            println("FAILED: ${e.message}")
            e.printStackTrace()
        }
        println("============================================\n")
    }

    // -------------------------------------------------------------------------
    // Stream URL extraction test — validates the two-step embed extraction
    // -------------------------------------------------------------------------

    /**
     * End-to-end test for [VoirAnimeScraper.getStreamUrl].
     *
     * Flow:
     *   1. Search "naruto" on voiranime.rip and take the first result.
     *   2. Fetch its episode list and take the last entry (episode 1 on voir-anime
     *      appears at the bottom of the list, newest-first order).
     *   3. Call getStreamUrl and assert the result is a playable URL.
     *
     * Run with:
     *   ./gradlew test --tests "com.lycoris.mochitv.scraper.ScraperLiveTest.testGetStreamUrl" --info 2>&1 | tail -60
     */
    @Test
    fun testGetStreamUrl() = runBlocking {
        println("\n========== VOIR-ANIME getStreamUrl (naruto ep 1) ==========")
        try {
            // Step 1: find Naruto's anime page
            val results = voirAnime.search("naruto")
            val naruto = results.firstOrNull { it.title.contains("naruto", ignoreCase = true) }
                ?: results.firstOrNull()
                ?: error("No search results for 'naruto'")
            println("Anime: ${naruto.title}")
            println("URL:   ${naruto.sourceUrl}")

            // Step 2: get episode list and pick episode 1 (last in list, newest-first)
            val episodes = voirAnime.getEpisodes(naruto.sourceUrl)
            println("Episode count: ${episodes.size}")
            val episode = episodes.minByOrNull { it.number }
                ?: error("No episodes found")
            println("Episode: #${episode.number} — ${episode.pageUrl}")

            // Step 3: extract the stream URL
            val streamUrl = voirAnime.getStreamUrl(episode.pageUrl)
            println("Stream URL: $streamUrl")

            // Assertions
            assert(streamUrl.startsWith("http")) {
                "Stream URL should start with http, got: $streamUrl"
            }
            val hasKnownExtension = streamUrl.contains(".m3u8") ||
                    streamUrl.contains(".mp4") ||
                    streamUrl.contains("googlevideo.com") ||
                    streamUrl.contains("streamtape.com") ||
                    streamUrl.contains("vmeas.cloud")
            assert(hasKnownExtension) {
                "Stream URL does not look like a playable media URL: $streamUrl"
            }
            println("Validation PASSED: stream URL looks playable")
        } catch (e: Exception) {
            println("FAILED: ${e.message}")
            e.printStackTrace()
        }
        println("============================================================\n")
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private fun fetchRaw(url: String, acceptLanguage: String, referer: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", acceptLanguage)
            .header("Referer", referer)
            .build()
        return client.newCall(request).execute().use { response ->
            println("HTTP status: ${response.code}")
            response.body?.string() ?: "(empty body)"
        }
    }
}

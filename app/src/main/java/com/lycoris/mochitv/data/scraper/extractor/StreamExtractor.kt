package com.lycoris.mochitv.data.scraper.extractor

/**
 * Contract for extracting a direct video stream URL from an embed page.
 *
 * The episode page on a source site typically embeds the player in an `<iframe>`.
 * That iframe URL points to a third-party video host (e.g. Vidmoly, Streamtape).
 * Implementations of this interface parse the HTML of *that* embed page and
 * return the underlying `.m3u8` / `.mp4` URL that ExoPlayer can play directly.
 *
 * Extractors are tried in priority order; the first non-null result wins.
 *
 * @see VidmolyExtractor
 * @see StreamtapeExtractor
 * @see GenericExtractor
 */
interface StreamExtractor {

    /**
     * Returns true if this extractor knows how to handle the given [embedUrl].
     *
     * The check is typically a hostname substring match so that
     * specialised extractors run before the generic fallback.
     */
    fun canHandle(embedUrl: String): Boolean

    /**
     * Parses [html] (the raw body of [embedUrl]) and returns a direct
     * stream URL, or null if this extractor cannot find one.
     *
     * Implementations must not throw — return null on any parse failure.
     *
     * @param embedUrl The full URL of the embed page; may be used for
     *                 URL reconstruction (e.g. to derive the host).
     * @param html     The raw HTML/JS body of the embed page.
     * @return A playable stream URL, or null.
     */
    fun extract(embedUrl: String, html: String): String?
}

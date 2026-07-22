package com.lycoris.mochitv.data.scraper.extractor

/**
 * Extractor for [Vidmoly](https://vidmoly.biz/) embed pages.
 *
 * Vidmoly uses JWPlayer 8. The embed page contains a player setup block with
 * a `sources` array whose `file` key holds the direct HLS master playlist URL:
 *
 * ```js
 * sources: [{ file: 'https://box-xxxx.vmeas.cloud/hls2/03/00551/abc_n/master.m3u8?t=...' }]
 * ```
 *
 * Verified against live HTML on 2026-07-22.
 */
object VidmolyExtractor : StreamExtractor {

    /**
     * Matches the first `file` entry inside a JWPlayer `sources` array.
     *
     * Group 1: the raw URL value (may be `.m3u8`, `.mp4`, or any CDN URL).
     */
    private val SOURCES_FILE_RE = Regex(
        """sources\s*:\s*\[\s*\{\s*file\s*:\s*['"]([^'"]+)['"]"""
    )

    override fun canHandle(embedUrl: String): Boolean = "vidmoly" in embedUrl

    override fun extract(embedUrl: String, html: String): String? =
        SOURCES_FILE_RE.find(html)
            ?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() }
}

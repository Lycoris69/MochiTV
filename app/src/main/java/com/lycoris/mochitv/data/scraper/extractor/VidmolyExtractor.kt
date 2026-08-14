package com.lycoris.mochitv.data.scraper.extractor

/**
 * Extractor for [Vidmoly](https://vidmoly.biz/) embed pages.
 *
 * Vidmoly uses JWPlayer 8. The embed page contains a player setup block with
 * a `sources` array whose `file` key holds the direct HLS master playlist URL.
 *
 * Supported HTML formats:
 * ```js
 * // JS object-literal (most common):
 * sources: [{ file: 'https://box-xxxx.vmeas.cloud/hls2/.../master.m3u8?t=...' }]
 *
 * // JSON format (less common):
 * "sources": [{"file": "https://..."}]
 *
 * // JWPlayer playlist alternative:
 * playlist: [{ file: 'https://...' }]
 * ```
 *
 * Verified against live HTML on 2026-07-22; JSON format added 2026-08-14.
 */
object VidmolyExtractor : StreamExtractor {

    /**
     * Matches `file` inside a `sources` or `playlist` array.
     * Handles both JS object-literal keys (`file:`) and JSON keys (`"file":`).
     * The `[\s\S]{0,400}?` allows the key to appear on a different line from the
     * array opener while keeping the match bounded to avoid catastrophic backtracking.
     */
    private val SOURCES_FILE_RE = Regex(
        """"?sources"?\s*:\s*\[[\s\S]{0,400}?"?file"?\s*:\s*['"]([^'"]+)['"]"""
    )

    private val PLAYLIST_FILE_RE = Regex(
        """"?playlist"?\s*:\s*\[[\s\S]{0,400}?"?file"?\s*:\s*['"]([^'"]+)['"]"""
    )

    override fun canHandle(embedUrl: String): Boolean = "vidmoly" in embedUrl

    override fun extract(embedUrl: String, html: String): String? =
        SOURCES_FILE_RE.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?: PLAYLIST_FILE_RE.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
}

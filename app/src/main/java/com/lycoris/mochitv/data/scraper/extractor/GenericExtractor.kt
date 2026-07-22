package com.lycoris.mochitv.data.scraper.extractor

/**
 * Generic fallback extractor that applies broad regex patterns against any
 * embed page HTML.
 *
 * Always reports [canHandle] = true; must be placed **last** in the extractor
 * chain so that specialised extractors get priority.
 *
 * Patterns tried, in order:
 *
 * 1. **JWPlayer / Video.js `file` key** — catches the common pattern
 *    `file: "https://...master.m3u8"` or `file: "https://...video.mp4"`.
 *    Requires an explicit `.m3u8`, `.mp4`, or `.mkv` file extension so that
 *    irrelevant `file:` occurrences (e.g. thumbnail paths) are not matched.
 *
 * 2. **JS variable assignment** — catches pages that expose the URL as a
 *    top-level variable: `var fileUrl = "https://..."`, `var videoUrl = "..."`,
 *    `var streamUrl = "..."`. Restricted to `http`-prefixed values to avoid
 *    matching relative paths or empty strings.
 *
 * This extractor covers:
 * - animesamatv.su inner player pages (Blogger / Google Video sourced `.mp4`)
 * - Any other JWPlayer-powered host not handled by a specific extractor
 */
object GenericExtractor : StreamExtractor {

    /**
     * Matches `file: "https://...m3u8"` and `file: 'https://...mp4'` variants.
     * Group 1: the stream URL.
     */
    private val FILE_EXT_RE = Regex(
        """file\s*:\s*["']([^"']+\.(?:m3u8|mp4|mkv)[^"']*)["']"""
    )

    /**
     * Matches `var fileUrl = "https://..."` and similar variable assignments.
     * Group 1: the stream URL (must start with `http`).
     */
    private val JS_VAR_RE = Regex(
        """var\s+(?:fileUrl|videoUrl|streamUrl|src)\s*=\s*["'](https?://[^"']+)["']"""
    )

    override fun canHandle(embedUrl: String): Boolean = true

    override fun extract(embedUrl: String, html: String): String? {
        FILE_EXT_RE.find(html)
            ?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        JS_VAR_RE.find(html)
            ?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return null
    }
}

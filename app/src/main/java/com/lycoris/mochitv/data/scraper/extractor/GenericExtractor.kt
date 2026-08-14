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
 * 1. **JWPlayer / Video.js `file` key** — `file: "https://...master.m3u8"` or
 *    `file: "https://...video.mp4"`. Requires a media file extension.
 *
 * 2. **HTML5 `<source>` tag** — `<source src="...m3u8" type="video/...">`.
 *    Covers embed hosts that use a plain HTML5 `<video>` element.
 *
 * 3. **HLS.js `loadSource`** — `hls.loadSource("https://...m3u8")`.
 *    Used by embed hosts that initialise HLS.js directly in page JS.
 *
 * 4. **JSON url/stream/hls field** — `"url":"https://...m3u8"` etc.
 *    Covers JSON player-config blobs with a known media-URL key.
 *
 * 5. **JS variable assignment** — `var fileUrl = "https://..."` and similar.
 *    Restricted to `http`-prefixed values to avoid matching relative paths.
 */
object GenericExtractor : StreamExtractor {

    /** `file: "https://...m3u8"` and `file: 'https://...mp4'` variants. */
    private val FILE_EXT_RE = Regex(
        """file\s*:\s*["']([^"']+\.(?:m3u8|mp4|mkv)[^"']*)["']"""
    )

    /** HTML5 `<source src="...m3u8" ...>` */
    private val HTML5_SOURCE_RE = Regex(
        """<source[^>]+src=["']([^"']+\.(?:m3u8|mp4|mkv)[^"']*)["']"""
    )

    /** `hls.loadSource("https://...m3u8")` */
    private val HLS_LOAD_RE = Regex(
        """hls\.loadSource\s*\(\s*["']([^"']+\.m3u8[^"']*)["']"""
    )

    /**
     * JSON field whose name suggests a media URL: `"url":"..."`, `"stream":"..."`,
     * `"hls":"..."`, `"video":"..."`.  Value must contain a media extension.
     */
    private val JSON_MEDIA_RE = Regex(
        """"(?:url|src|stream|hls|video)"\s*:\s*"([^"]+\.(?:m3u8|mp4)[^"]*)"""""
    )

    /** `var fileUrl = "https://..."` and similar variable assignments. */
    private val JS_VAR_RE = Regex(
        """var\s+(?:fileUrl|videoUrl|streamUrl|src)\s*=\s*["'](https?://[^"']+)["']"""
    )

    override fun canHandle(embedUrl: String): Boolean = true

    override fun extract(embedUrl: String, html: String): String? {
        FILE_EXT_RE.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?.let { return it }

        HTML5_SOURCE_RE.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?.let { return it }

        HLS_LOAD_RE.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?.let { return it }

        JSON_MEDIA_RE.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?.let { return it }

        JS_VAR_RE.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return null
    }
}

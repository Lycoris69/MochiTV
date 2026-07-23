package com.lycoris.mochitv.data.scraper.extractor

/**
 * Extracts video URLs from Sibnet (video.sibnet.ru) embed pages.
 *
 * Sibnet's shell.php player embeds the relative video path in:
 *   player.src([{src: "/v/HASH/VIDEOID.mp4", type: "video/mp4"},]);
 *
 * The path must be prefixed with https://video.sibnet.ru before use.
 * That URL then redirects (302) to a signed CDN URL — the caller is
 * responsible for following that redirect.
 */
object SibnetExtractor : StreamExtractor {

    override fun canHandle(embedUrl: String) = "sibnet.ru" in embedUrl

    override fun extract(embedUrl: String, html: String): String? {
        // player.src([{src: "/v/HASH/VIDEOID.ext", type: "..."},]);
        val match = Regex("""player\.src\s*\(\s*\[\s*\{\s*src\s*:\s*"([^"]+)"""").find(html)
            ?: return null
        val src = match.groupValues[1]
        return when {
            src.startsWith("//") -> "https:$src"
            src.startsWith("/")  -> "https://video.sibnet.ru$src"
            src.startsWith("http") -> src
            else -> null
        }
    }
}

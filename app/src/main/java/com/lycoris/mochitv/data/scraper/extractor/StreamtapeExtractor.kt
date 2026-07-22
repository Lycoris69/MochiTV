package com.lycoris.mochitv.data.scraper.extractor

/**
 * Extractor for [Streamtape](https://streamtape.com/) embed pages.
 *
 * Streamtape obfuscates its download URL via a two-part JavaScript string
 * concatenation applied to the `div#robotlink` element:
 *
 * ```js
 * document.getElementById('robotlink').innerHTML =
 *     '//strea' + ('xcdmtape.com/get_video?id=XXX&expires=YYY&ip=ZZZ&token=TTT')
 *         .substring(2)
 *         .substring(1);
 * ```
 *
 * The algorithm:
 * 1. Extract the **prefix** string (`//strea`) and the **obfuscated suffix**
 *    (`xcdmtape.com/get_video?...`) from the JS assignment line.
 * 2. Apply each `.substring(n)` call in order to the obfuscated string.
 * 3. Concatenate prefix + trimmed suffix to get the protocol-relative URL.
 * 4. Prepend `https:` to produce a fully-qualified download URL.
 *
 * The resulting `get_video` URL is an HTTP-redirect link that resolves to the
 * actual `.mp4` file; ExoPlayer follows the redirect automatically.
 *
 * Verified against live HTML on 2026-07-22.
 */
object StreamtapeExtractor : StreamExtractor {

    /**
     * Captures the three components of the robotlink JS obfuscation:
     * - Group 1: prefix (e.g. `//strea`)
     * - Group 2: obfuscated suffix string (e.g. `xcdmtape.com/get_video?...`)
     * - Group 3: all chained `.substring(n)` calls (e.g. `.substring(2).substring(1)`)
     */
    private val ROBOTLINK_RE = Regex(
        """getElementById\s*\(\s*['"]robotlink['"]\s*\)\s*\.innerHTML\s*=\s*['"]([^'"]*)['"]\s*\+\s*\(['"]([^'"]+)['"]\)((?:\s*\.substring\s*\(\s*\d+\s*\))+)"""
    )

    private val DIGIT_RE = Regex("""\d+""")

    override fun canHandle(embedUrl: String): Boolean = "streamtape" in embedUrl

    override fun extract(embedUrl: String, html: String): String? {
        val match = ROBOTLINK_RE.find(html) ?: return null

        val prefix = match.groupValues[1]          // e.g. "//strea"
        val obfuscated = match.groupValues[2]       // e.g. "xcdmtape.com/get_video?..."
        val substringChain = match.groupValues[3]   // e.g. ".substring(2).substring(1)"

        // Apply each .substring(n) in sequence to reconstruct the true suffix
        val suffix = DIGIT_RE.findAll(substringChain)
            .map { it.value.toInt() }
            .fold(obfuscated) { acc, n -> acc.substring(n) }

        val raw = prefix + suffix
        // Convert protocol-relative "//streamtape.com/..." to "https://streamtape.com/..."
        return if (raw.startsWith("//")) "https:$raw" else raw
    }
}

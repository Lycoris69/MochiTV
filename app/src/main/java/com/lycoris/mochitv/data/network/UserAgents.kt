package com.lycoris.mochitv.data.network

/**
 * Pool of common desktop User-Agent strings used to rotate HTTP request
 * headers across scraper calls.
 *
 * Simple bot-detection on the target sites typically checks the UA header;
 * rotating through real browser UAs reduces the likelihood of being blocked.
 * Refresh these strings periodically as browser versions advance.
 */
object UserAgents {

    private val pool: List<String> = listOf(
        // Chrome 124 — Windows 10
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        // Chrome 124 — macOS Sonoma
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        // Firefox 125 — Windows 10
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0",
        // Firefox 125 — Linux
        "Mozilla/5.0 (X11; Linux x86_64; rv:125.0) Gecko/20100101 Firefox/125.0",
        // Safari 17 — macOS Sonoma
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_4_1) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Safari/605.1.15",
        // Edge 124 — Windows 10
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0",
        // Chrome 124 — Android 14 (in case a mobile UA blends in better)
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
    )

    /** Returns a pseudo-random User-Agent string from the pool. */
    fun random(): String = pool.random()
}

package com.lycoris.mochitv.data.scraper.extractor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TIMEOUT_MS = 25_000L

/**
 * Extracts HLS stream URLs from embed4me.com player pages.
 *
 * The embed4me player is a React SPA behind Cloudflare bot detection — plain
 * HTTP clients get 503 from its /api/v1/video endpoint.  Loading the page in a
 * hidden [WebView] with JavaScript enabled lets the player pass the CF challenge
 * and fetch its own stream URL.  We intercept the first outbound .m3u8 request
 * the player makes via [WebViewClient.shouldInterceptRequest] and return that CDN
 * URL to ExoPlayer.
 *
 * The timeout is posted **before** [mainHandler.post] so it always fires even if
 * [WebView] construction throws inside the post.
 */
@Singleton
class Embed4meExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extract(embedUrl: String): String = suspendCancellableCoroutine { cont ->
        var webView: WebView? = null

        fun destroyWebView() {
            val wv = webView
            webView = null
            if (wv != null) mainHandler.post { wv.destroy() }
        }

        cont.invokeOnCancellation { destroyWebView() }

        // Timeout is scheduled before mainHandler.post so it always fires,
        // even if WebView(context) throws inside the post runnable.
        val timeoutRunnable = Runnable {
            if (cont.isActive) {
                destroyWebView()
                cont.resumeWithException(
                    IOException("embed4me: timed out after ${TIMEOUT_MS / 1000}s — stream URL not found")
                )
            }
        }
        mainHandler.postDelayed(timeoutRunnable, TIMEOUT_MS)

        mainHandler.post {
            try {
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            val url = request.url.toString()
                            // Capture the first CDN .m3u8 request (master HLS playlist).
                            // Exclude embed4me.com itself — its own API calls may contain
                            // .m3u8-like paths that are not playable stream URLs.
                            if (url.contains(".m3u8") && "embed4me.com" !in url && cont.isActive) {
                                mainHandler.removeCallbacks(timeoutRunnable)
                                cont.resume(url)
                                destroyWebView()
                            }
                            return null
                        }
                    }

                    loadUrl(embedUrl)
                }
            } catch (e: Exception) {
                mainHandler.removeCallbacks(timeoutRunnable)
                if (cont.isActive) cont.resumeWithException(e)
            }
        }
    }
}

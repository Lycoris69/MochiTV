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

/**
 * Extracts HLS stream URLs from embed4me.com player pages.
 *
 * The embed4me player is a React SPA behind Cloudflare bot detection — plain
 * HTTP requests to /api/v1/video return 503.  Loading the page in a [WebView]
 * with JavaScript enabled passes the CF challenge so the player can reach its
 * API and fetch the actual CDN stream URL.  We intercept the first outbound
 * .m3u8 request (the master HLS playlist) via [WebViewClient.shouldInterceptRequest]
 * and resume the suspended caller with that URL.
 *
 * The WebView is never shown to the user and is destroyed as soon as we capture
 * the URL or the 30-second timeout fires.
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
            mainHandler.post { webView?.destroy(); webView = null }
        }

        cont.invokeOnCancellation { destroyWebView() }

        val timeoutRunnable = Runnable {
            if (cont.isActive) {
                destroyWebView()
                cont.resumeWithException(IOException("embed4me: timed out waiting for stream URL"))
            }
        }

        mainHandler.post {
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
                        // First .m3u8 request that's not to the embed4me domain itself
                        // is the master HLS playlist on the CDN.
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

            mainHandler.postDelayed(timeoutRunnable, 30_000L)
        }
    }
}

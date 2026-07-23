package com.lycoris.mochitv.data.scraper.extractor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TIMEOUT_MS = 25_000L

// Constant AES-128-CBC key/IV baked into the embed4me player JS
private val AES_KEY = "kiemtienmua911ca".toByteArray()
private val AES_IV  = "1234567890oiuytr".toByteArray()

/**
 * Extracts HLS stream URLs from embed4me.com player pages.
 *
 * Strategy A (preferred): load the embed page in a hidden [WebView] with JS
 * enabled — the WebView execution environment passes Cloudflare's bot check.
 * We intercept ALL outbound network requests via [WebViewClient.shouldInterceptRequest]:
 *
 *  • When the page's video player requests a CDN `.m3u8` URL, we capture it
 *    and return it immediately.
 *  • When the page hits `/api/v1/video` (the encrypted JSON endpoint) we
 *    relay the request ourselves (inheriting the WebView's CF cookies), decrypt
 *    the AES-CBC response, and pull the `source` field — avoiding the need to
 *    wait for the video element to initialise.
 *
 * A [TIMEOUT_MS]-millisecond timeout is scheduled **before** the WebView is
 * created so it always fires, even if WebView initialisation throws.
 */
@Singleton
class Embed4meExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
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

        // Timeout is scheduled BEFORE mainHandler.post so it always fires,
        // even if WebView(context) throws inside the post.
        val timeoutRunnable = Runnable {
            if (cont.isActive) {
                destroyWebView()
                cont.resumeWithException(
                    IOException("embed4me: timed out after ${TIMEOUT_MS / 1000}s")
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

                            // Strategy A1: player fetched the HLS master playlist
                            if (url.contains(".m3u8") && "embed4me.com" !in url && cont.isActive) {
                                mainHandler.removeCallbacks(timeoutRunnable)
                                cont.resume(url)
                                destroyWebView()
                                return null
                            }

                            // Strategy A2: intercept the encrypted /api/v1/video response.
                            // We relay the request ourselves (inheriting CF cookies from
                            // the WebView session) and decrypt the JSON to get E.source.
                            if ("/api/v1/video" in url && cont.isActive) {
                                val cookies = CookieManager.getInstance().getCookie(url) ?: ""
                                val streamUrl = runCatching {
                                    fetchAndDecryptVideoApi(url, cookies)
                                }.getOrNull()

                                if (!streamUrl.isNullOrBlank() && cont.isActive) {
                                    mainHandler.removeCallbacks(timeoutRunnable)
                                    cont.resume(streamUrl)
                                    destroyWebView()
                                    // Return null so WebView also processes the response
                                    // (keeps the page alive in case our relay failed)
                                }
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

    // ---- helpers ----------------------------------------------------------------

    private fun fetchAndDecryptVideoApi(url: String, cookies: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookies)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Referer", "https://lplayer.embed4me.com/")
            .header("Accept", "application/json, text/plain, */*")
            .build()

        val hexBody = okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string()?.trim() ?: return null
        }

        if (!hexBody.all { it.isLetterOrDigit() } || hexBody.length < 32) return null

        val json = aesDecrypt(hexBody) ?: return null

        // Extract "source":"<url>" from the JSON (avoid a full JSON parser dependency)
        return Regex(""""source"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
    }

    private fun aesDecrypt(hexData: String): String? = runCatching {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(AES_KEY, "AES"),
            IvParameterSpec(AES_IV),
        )
        val raw = hexData.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        String(cipher.doFinal(raw))
    }.getOrNull()
}

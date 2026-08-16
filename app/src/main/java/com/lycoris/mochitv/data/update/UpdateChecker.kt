package com.lycoris.mochitv.data.update

import com.lycoris.mochitv.BuildConfig
import com.lycoris.mochitv.domain.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val GITHUB_REPO  = "Lycoris69/MochiTV"
private const val RELEASES_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

@Singleton
class UpdateChecker @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    /**
     * Fetches the latest GitHub release and returns [UpdateInfo] if the remote
     * version is strictly newer than [BuildConfig.VERSION_NAME]; null otherwise.
     *
     * Throws on network errors — callers should use [runCatching].
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASES_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "MochiTV/${BuildConfig.VERSION_NAME}")
            .build()

        val body = okHttpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "GitHub API returned HTTP ${response.code}"
            }
            response.body!!.string()
        }

        val json         = JSONObject(body)
        val tagName      = json.optString("tag_name")          // e.g. "v1.5.5"
        val releasePageUrl = json.optString("html_url")
        val releaseNotes = json.optString("body", "")

        // Strip leading 'v' / 'V' from the tag
        val remoteVersion = tagName.trimStart('v', 'V')

        // Find the first .apk asset URL, if any
        val downloadUrl: String? = json.optJSONArray("assets")
            ?.let { assets ->
                (0 until assets.length())
                    .asSequence()
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                    ?.optString("browser_download_url")
                    ?.takeIf { it.isNotBlank() }
            }

        if (isNewer(remoteVersion, BuildConfig.VERSION_NAME)) {
            UpdateInfo(
                latestVersion  = remoteVersion,
                releasePageUrl = releasePageUrl,
                downloadUrl    = downloadUrl,
                releaseNotes   = releaseNotes,
            )
        } else {
            null
        }
    }

    // -------------------------------------------------------------------------
    // Version comparison
    // -------------------------------------------------------------------------

    /**
     * Returns true when [remote] is strictly greater than [current].
     *
     * Compares dot-separated numeric components; non-numeric components are
     * compared as strings.  Extra components in either string are treated as 0.
     *
     * Examples:
     *   "1.5.5" > "1.5.4"  → true
     *   "2.0.0" > "1.9.9"  → true
     *   "1.5.4" > "1.5.4"  → false
     */
    private fun isNewer(remote: String, current: String): Boolean {
        val remoteParts  = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val length = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false // equal
    }
}

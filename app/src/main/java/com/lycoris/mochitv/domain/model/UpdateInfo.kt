package com.lycoris.mochitv.domain.model

/**
 * Describes a GitHub release that is newer than the currently installed app.
 *
 * @param latestVersion  Version string from the tag, e.g. "1.5.5"
 * @param releasePageUrl The GitHub releases HTML page (html_url from the API)
 * @param downloadUrl    Direct APK URL from the release assets, or null when
 *                       no .apk asset is attached (falls back to [releasePageUrl])
 * @param releaseNotes   Markdown body of the release, may be blank
 */
data class UpdateInfo(
    val latestVersion: String,
    val releasePageUrl: String,
    val downloadUrl: String?,
    val releaseNotes: String,
)

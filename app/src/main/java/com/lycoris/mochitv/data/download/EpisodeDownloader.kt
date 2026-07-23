package com.lycoris.mochitv.data.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpisodeDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Enqueues [streamUrl] in the system DownloadManager.
     *
     * The file is saved to Downloads/MochiTV/<animeName>/<episodeTitle>.mp4.
     * Throws [UnsupportedOperationException] for HLS streams (.m3u8) because the
     * system DownloadManager cannot reassemble HLS segments.
     */
    fun enqueue(streamUrl: String, animeName: String, episodeNumber: Int, episodeTitle: String?) {
        if (streamUrl.contains(".m3u8")) {
            throw UnsupportedOperationException("HLS streams cannot be saved for offline playback")
        }

        val safeAnime = animeName.sanitize().take(50)
        val safeTitle = (episodeTitle?.sanitize()?.take(60)) ?: "Episode $episodeNumber"
        val subPath = "MochiTV/$safeAnime/$safeTitle.mp4"

        val request = DownloadManager.Request(Uri.parse(streamUrl))
            .setTitle("$safeTitle — $safeAnime")
            .setDescription("Downloading via MochiTV")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, subPath)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }

    private fun String.sanitize() = replace(Regex("""[/\\:*?"<>|]"""), "_").trim()
}

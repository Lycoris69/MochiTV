package com.lycoris.mochitv.data.download

import android.content.Context
import android.net.Uri
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues episode downloads via Media3's [DownloadService].
 *
 * Both direct MP4 URLs and HLS playlists (.m3u8) are handled correctly:
 * Media3 downloads all HLS segments automatically and caches them for
 * offline playback in ExoPlayer via [DownloadManagerHolder.getCache].
 *
 * Downloaded content lives in `<externalFilesDir>/media3_downloads/`.
 */
@Singleton
class EpisodeDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun enqueue(
        streamUrl: String,
        episodeId: String,
        animeName: String,
        episodeNumber: Int,
        episodeTitle: String?,
    ) {
        val request = DownloadRequest.Builder(episodeId, Uri.parse(streamUrl)).build()

        DownloadService.sendAddDownload(
            context,
            MochiDownloadService::class.java,
            request,
            /* foreground = */ false,
        )
    }
}

package com.lycoris.mochitv.data.download

import android.content.Context
import android.net.Uri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
    /**
     * Emits the raw Media3 download state ([Download.STATE_*]) for every known
     * episode ID whenever any download changes.  Callers map these integers to
     * their own state representation.
     */
    fun observeStates(): Flow<Map<String, Int>> = callbackFlow {
        val dm = DownloadManagerHolder.getDownloadManager(context)

        fun sendCurrent() {
            val map = mutableMapOf<String, Int>()
            val cursor = dm.downloadIndex.getDownloads()
            while (cursor.moveToNext()) {
                val d = cursor.download
                map[d.request.id] = d.state
            }
            cursor.close()
            trySend(map)
        }

        val listener = object : DownloadManager.Listener {
            override fun onDownloadChanged(
                dm: DownloadManager,
                download: Download,
                finalException: Exception?,
            ) = sendCurrent()

            override fun onDownloadRemoved(dm: DownloadManager, download: Download) =
                sendCurrent()

            override fun onInitialized(dm: DownloadManager) = sendCurrent()
        }

        dm.addListener(listener)
        sendCurrent()
        awaitClose { dm.removeListener(listener) }
    }

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

package com.lycoris.mochitv.data.download

import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.lycoris.mochitv.R

/**
 * Foreground [DownloadService] that drives all Media3 downloads.
 *
 * Media3's download infrastructure requires a service to keep the download
 * alive when the app is backgrounded. This class simply delegates state to
 * [DownloadManagerHolder] so there is one shared [DownloadManager] for the
 * whole process.
 */
class MochiDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    /* channelDescriptionResourceId = */ 0,
) {
    override fun getDownloadManager(): DownloadManager =
        DownloadManagerHolder.getDownloadManager(this)

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(
            if (downloads.size == 1) "Downloading 1 episode…"
            else "Downloading ${downloads.size} episodes…"
        )
        .setOngoing(true)
        .build()

    companion object {
        const val CHANNEL_ID = "mochitv_downloads"
        private const val FOREGROUND_NOTIFICATION_ID = 2001
    }
}

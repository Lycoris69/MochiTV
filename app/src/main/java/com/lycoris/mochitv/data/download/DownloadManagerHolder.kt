package com.lycoris.mochitv.data.download

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import java.io.File
import java.util.concurrent.Executor

/**
 * Process-scoped singleton for the Media3 [DownloadManager] and its [SimpleCache].
 *
 * [SimpleCache] must have exactly one live instance per directory, so we use
 * double-checked locking to guarantee that even if the object is first accessed
 * from multiple threads simultaneously, only one instance is ever created.
 *
 * Both the manager and the cache are lazily initialised on first use and live
 * for the lifetime of the process (they are never explicitly closed, which is
 * intentional for a long-running media app).
 */
object DownloadManagerHolder {

    @Volatile private var databaseProvider: StandaloneDatabaseProvider? = null
    @Volatile private var cache: SimpleCache? = null
    @Volatile private var downloadManager: DownloadManager? = null

    fun getDownloadManager(context: Context): DownloadManager =
        downloadManager ?: synchronized(this) {
            downloadManager ?: buildDownloadManager(context.applicationContext)
                .also { downloadManager = it }
        }

    fun getCache(context: Context): SimpleCache =
        cache ?: synchronized(this) {
            cache ?: buildCache(context.applicationContext).also { cache = it }
        }

    private fun getDatabaseProvider(context: Context): StandaloneDatabaseProvider =
        databaseProvider ?: synchronized(this) {
            databaseProvider
                ?: StandaloneDatabaseProvider(context.applicationContext)
                    .also { databaseProvider = it }
        }

    private fun buildCache(context: Context): SimpleCache {
        val dir = File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "media3_downloads",
        )
        return SimpleCache(dir, NoOpCacheEvictor(), getDatabaseProvider(context))
    }

    private fun buildDownloadManager(context: Context): DownloadManager =
        DownloadManager(
            context,
            getDatabaseProvider(context),
            getCache(context),
            DefaultHttpDataSource.Factory(),
            Executor(Runnable::run),
        ).apply { maxParallelDownloads = 3 }
}

package com.lycoris.mochitv.domain.usecase

import com.lycoris.mochitv.data.download.EpisodeDownloader
import com.lycoris.mochitv.domain.model.Episode
import javax.inject.Inject

/**
 * Use case: resolve the stream URL for [episode] and enqueue it in the system
 * DownloadManager under the given [animeName] directory.
 *
 * Returns [Result.failure] if the stream URL cannot be resolved or if the
 * stream format is unsupported for offline download (e.g. HLS).
 */
class DownloadEpisodeUseCase @Inject constructor(
    private val getStreamUrlUseCase: GetStreamUrlUseCase,
    private val downloader: EpisodeDownloader,
) {
    suspend operator fun invoke(episode: Episode, animeName: String): Result<Unit> =
        getStreamUrlUseCase(episode).mapCatching { url ->
            downloader.enqueue(url, animeName, episode.number, episode.title)
        }
}

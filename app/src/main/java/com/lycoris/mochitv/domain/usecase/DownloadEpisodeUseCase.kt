package com.lycoris.mochitv.domain.usecase

import com.lycoris.mochitv.data.download.EpisodeDownloader
import com.lycoris.mochitv.domain.model.Episode
import javax.inject.Inject

/**
 * Use case: resolve the stream URL for [episode] and hand it off to
 * [EpisodeDownloader] which starts a Media3 background download.
 *
 * Both MP4 and HLS streams are supported — Media3 handles segment
 * reassembly for HLS automatically.
 *
 * Returns [Result.failure] if the stream URL cannot be resolved or if
 * the download service cannot be started.
 */
class DownloadEpisodeUseCase @Inject constructor(
    private val getStreamUrlUseCase: GetStreamUrlUseCase,
    private val downloader: EpisodeDownloader,
) {
    suspend operator fun invoke(episode: Episode, animeName: String): Result<Unit> =
        getStreamUrlUseCase(episode).mapCatching { url ->
            downloader.enqueue(
                streamUrl = url,
                episodeId = episode.id,
                animeName = animeName,
                episodeNumber = episode.number,
                episodeTitle = episode.title,
            )
        }
}

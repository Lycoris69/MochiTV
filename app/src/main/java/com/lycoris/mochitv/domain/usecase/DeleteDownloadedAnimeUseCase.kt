package com.lycoris.mochitv.domain.usecase

import com.lycoris.mochitv.data.download.EpisodeDownloader
import com.lycoris.mochitv.data.local.dao.EpisodeDao
import javax.inject.Inject

/**
 * Deletes all downloaded episodes belonging to [animeId] from the Media3 download cache.
 *
 * Iterates every [EpisodeEntity] for the anime and fires a remove command for each episode ID,
 * so that both fully completed downloads and in-progress downloads are cancelled and removed.
 */
class DeleteDownloadedAnimeUseCase @Inject constructor(
    private val episodeDownloader: EpisodeDownloader,
    private val episodeDao: EpisodeDao,
) {
    suspend operator fun invoke(animeId: String) {
        episodeDao.getAllByAnimeId(animeId)
            .forEach { entity -> episodeDownloader.deleteDownload(entity.id) }
    }
}

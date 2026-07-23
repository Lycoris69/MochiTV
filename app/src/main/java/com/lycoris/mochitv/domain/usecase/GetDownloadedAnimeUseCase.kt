package com.lycoris.mochitv.domain.usecase

import androidx.media3.exoplayer.offline.Download
import com.lycoris.mochitv.data.download.EpisodeDownloader
import com.lycoris.mochitv.data.local.dao.AnimeDao
import com.lycoris.mochitv.data.local.dao.EpisodeDao
import com.lycoris.mochitv.data.local.entity.toDomain
import com.lycoris.mochitv.domain.model.Anime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetDownloadedAnimeUseCase @Inject constructor(
    private val episodeDownloader: EpisodeDownloader,
    private val episodeDao: EpisodeDao,
    private val animeDao: AnimeDao,
) {
    operator fun invoke(): Flow<List<Anime>> =
        episodeDownloader.observeStates().map { states ->
            states
                .filterValues { it == Download.STATE_COMPLETED }
                .keys
                .mapNotNull { episodeId -> episodeDao.getById(episodeId)?.animeId }
                .distinct()
                .mapNotNull { animeId -> animeDao.getById(animeId)?.toDomain() }
                .sortedBy { it.title }
        }
}

package com.lycoris.mochitv.domain.usecase

import com.lycoris.mochitv.data.local.dao.WatchProgressDao
import com.lycoris.mochitv.data.local.entity.WatchProgressEntity
import javax.inject.Inject

class RecordWatchProgressUseCase @Inject constructor(
    private val watchProgressDao: WatchProgressDao,
) {
    suspend operator fun invoke(episodeId: String) {
        watchProgressDao.markWatched(WatchProgressEntity(episodeId, System.currentTimeMillis()))
    }
}

package com.lycoris.mochitv.domain.usecase

import com.lycoris.mochitv.data.local.dao.WatchProgressDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetWatchedEpisodeIdsUseCase @Inject constructor(
    private val watchProgressDao: WatchProgressDao,
) {
    operator fun invoke(): Flow<Set<String>> =
        watchProgressDao.observeWatchedIds().map { it.toSet() }
}

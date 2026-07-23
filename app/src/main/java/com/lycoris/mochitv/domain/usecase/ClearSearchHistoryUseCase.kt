package com.lycoris.mochitv.domain.usecase

import com.lycoris.mochitv.domain.repository.SearchHistoryRepository
import javax.inject.Inject

class ClearSearchHistoryUseCase @Inject constructor(
    private val repo: SearchHistoryRepository,
) {
    suspend operator fun invoke() = repo.clearAll()
}

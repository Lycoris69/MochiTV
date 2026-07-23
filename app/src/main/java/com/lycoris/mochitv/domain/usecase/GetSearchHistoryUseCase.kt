package com.lycoris.mochitv.domain.usecase

import com.lycoris.mochitv.domain.repository.SearchHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSearchHistoryUseCase @Inject constructor(
    private val repo: SearchHistoryRepository,
) {
    operator fun invoke(): Flow<List<String>> = repo.getAll()
}

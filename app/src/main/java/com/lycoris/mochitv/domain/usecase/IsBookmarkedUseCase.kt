package com.lycoris.mochitv.domain.usecase

import com.lycoris.mochitv.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class IsBookmarkedUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    operator fun invoke(animeId: String): Flow<Boolean> = repository.isBookmarked(animeId)
}

package com.lycoris.mochitv.domain.usecase

import com.lycoris.mochitv.domain.repository.BookmarkRepository
import javax.inject.Inject

class UnbookmarkAnimeUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    suspend operator fun invoke(animeId: String) = repository.unbookmark(animeId)
}

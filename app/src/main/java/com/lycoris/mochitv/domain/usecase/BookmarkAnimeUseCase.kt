package com.lycoris.mochitv.domain.usecase

import com.lycoris.mochitv.domain.repository.BookmarkRepository
import javax.inject.Inject

class BookmarkAnimeUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    suspend operator fun invoke(animeId: String) = repository.bookmark(animeId)
}

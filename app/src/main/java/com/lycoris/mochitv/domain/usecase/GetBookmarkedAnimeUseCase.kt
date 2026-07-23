package com.lycoris.mochitv.domain.usecase

import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBookmarkedAnimeUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    operator fun invoke(): Flow<List<Anime>> = repository.getBookmarkedAnime()
}

package com.lycoris.mochitv.domain.usecase

import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.repository.AnimeRepository
import javax.inject.Inject

/**
 * Use case: fetch a single [Anime] by id from the local Room cache.
 *
 * The cache is populated whenever [SearchAnimeUseCase] is called; calling this
 * use case before any search results in [Result.failure].  The detail screen
 * is reachable only by tapping a card in the search results, so the anime is
 * always cached by the time this use case is invoked.
 */
class GetAnimeDetailUseCase @Inject constructor(
    private val repository: AnimeRepository,
) {
    suspend operator fun invoke(animeId: String): Result<Anime> =
        repository.getAnimeById(animeId)
}

package com.lycoris.mochitv.domain.usecase

import com.lycoris.mochitv.domain.model.Episode
import com.lycoris.mochitv.domain.repository.AnimeRepository
import javax.inject.Inject

/**
 * Use case: fetch the episode list for a specific anime.
 *
 * Delegates entirely to [AnimeRepository]; exists to maintain a consistent
 * use-case boundary so the domain layer controls business rules
 * (e.g. episode sorting, filtering aired episodes) independently of the UI.
 */
class GetEpisodesUseCase @Inject constructor(
    private val repository: AnimeRepository,
) {
    /**
     * @param animeId  The [com.lycoris.mochitv.domain.model.Anime.id] of the target series.
     */
    suspend operator fun invoke(animeId: String): Result<List<Episode>> =
        repository.getEpisodes(animeId)
}

package com.lycoris.mochitv.domain.usecase

import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.repository.AnimeRepository
import javax.inject.Inject

/**
 * Use case: retrieve the latest-updated anime series from voir-anime.to.
 *
 * Delegates to [AnimeRepository.getLatestReleases] and keeps the ViewModel
 * free of repository concerns.  If the fetch strategy changes (e.g. adding a
 * second source, deduplication, or local-first logic), this class absorbs it.
 */
class GetLatestReleasesUseCase @Inject constructor(
    private val repository: AnimeRepository,
) {
    /**
     * Fetch the latest releases.
     *
     * @return [Result.success] with a (possibly empty) list on success, or
     *         [Result.failure] if the network request or HTML parse failed.
     */
    suspend operator fun invoke(page: Int = 1): Result<List<Anime>> = repository.getLatestReleases(page)
}

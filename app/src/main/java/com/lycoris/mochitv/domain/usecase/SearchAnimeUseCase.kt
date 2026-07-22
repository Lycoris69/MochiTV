package com.lycoris.mochitv.domain.usecase

import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.model.SourceSite
import com.lycoris.mochitv.domain.repository.AnimeRepository
import javax.inject.Inject

/**
 * Use case: search for anime across all configured sources.
 *
 * Keeps the ViewModel free of repository concerns — if the search strategy
 * ever changes (caching, deduplication, ranking), this class absorbs it.
 *
 * Hilt injects [AnimeRepository] automatically because of [@Inject constructor].
 */
class SearchAnimeUseCase @Inject constructor(
    private val repository: AnimeRepository,
) {
    /**
     * Execute the search.
     *
     * @param query   Non-blank search string.
     * @param source  Restrict to a single source, or null for all.
     * @return [Result.success] with merged results, or [Result.failure] on network/parse error.
     */
    suspend operator fun invoke(
        query: String,
        source: SourceSite? = null,
    ): Result<List<Anime>> = repository.searchAnime(query.trim(), source)
}

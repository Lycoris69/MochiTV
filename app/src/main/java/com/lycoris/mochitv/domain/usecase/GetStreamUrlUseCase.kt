package com.lycoris.mochitv.domain.usecase

import com.lycoris.mochitv.domain.model.Episode
import com.lycoris.mochitv.domain.repository.AnimeRepository
import javax.inject.Inject

/**
 * Use case: resolve the direct video stream URL for a given [Episode].
 *
 * Delegates to [AnimeRepository.getStreamUrl], which dispatches a secondary
 * HTTP request to the episode's watch page and applies multi-strategy extraction
 * (iframe, JS variable, JWPlayer).  The actual HTTP work runs on
 * [kotlinx.coroutines.Dispatchers.IO] inside the scraper.
 */
class GetStreamUrlUseCase @Inject constructor(
    private val repository: AnimeRepository,
) {
    suspend operator fun invoke(episode: Episode): Result<String> =
        repository.getStreamUrl(episode)
}

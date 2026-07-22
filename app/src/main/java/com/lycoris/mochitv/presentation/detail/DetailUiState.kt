package com.lycoris.mochitv.presentation.detail

import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.model.Episode

/**
 * Exhaustive UI state for the anime detail screen.
 *
 * [DetailViewModel] transitions through these states as it loads the anime
 * metadata and episode list.  The sealed interface ensures the Compose `when`
 * expression is exhaustive without runtime overhead of a superclass allocation.
 */
sealed interface DetailUiState {

    /** Anime metadata and episode list are being loaded. */
    data object Loading : DetailUiState

    /** Both the anime and its episode list loaded successfully. */
    data class Success(
        val anime: Anime,
        val episodes: List<Episode>,
    ) : DetailUiState

    /** Loading failed; [message] is displayed to the user with a retry option. */
    data class Error(val message: String) : DetailUiState
}

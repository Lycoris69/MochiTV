package com.lycoris.mochitv.presentation.player

import com.lycoris.mochitv.domain.model.Episode

/**
 * Exhaustive UI state for the video player screen.
 *
 * [PlayerViewModel] transitions through these states as it looks up the episode
 * and resolves the stream URL.
 */
sealed interface PlayerUiState {

    /** The episode is being located and/or the stream URL is being resolved. */
    data object Loading : PlayerUiState

    /**
     * The stream URL has been resolved and playback can begin.
     *
     * @param streamUrl       Direct media URL (HLS, DASH, or progressive MP4).
     * @param episode         The episode being played (used for display metadata).
     * @param savedPositionMs Last saved playback position in ms, or 0 if none.
     *                        When > 0 the player should seek here after [prepare].
     */
    data class Ready(
        val streamUrl: String,
        val episode: Episode,
        val savedPositionMs: Long = 0L,
    ) : PlayerUiState

    /** Resolution failed; [message] is displayed with a retry button. */
    data class Error(val message: String) : PlayerUiState
}

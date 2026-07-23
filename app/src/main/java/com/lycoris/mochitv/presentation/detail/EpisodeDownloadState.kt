package com.lycoris.mochitv.presentation.detail

sealed interface EpisodeDownloadState {
    /** No download started. */
    data object None : EpisodeDownloadState
    /** Stream URL is being resolved (before handing off to Media3). */
    data object Resolving : EpisodeDownloadState
    /** Handed to Media3 but not yet actively downloading. */
    data object Queued : EpisodeDownloadState
    /** Media3 is actively fetching segments. */
    data object Downloading : EpisodeDownloadState
    /** All content is cached and ready for offline playback. */
    data object Completed : EpisodeDownloadState
    /** Download failed — user can retry by tapping the icon. */
    data object Failed : EpisodeDownloadState
}

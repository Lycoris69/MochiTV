package com.lycoris.mochitv.presentation.downloads

import androidx.lifecycle.ViewModel
import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.usecase.GetDownloadedAnimeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    getDownloadedAnimeUseCase: GetDownloadedAnimeUseCase,
) : ViewModel() {

    val downloadedAnime: Flow<List<Anime>> = getDownloadedAnimeUseCase()
}

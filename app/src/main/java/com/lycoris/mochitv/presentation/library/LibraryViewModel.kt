package com.lycoris.mochitv.presentation.library

import androidx.lifecycle.ViewModel
import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.usecase.GetBookmarkedAnimeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    getBookmarkedAnimeUseCase: GetBookmarkedAnimeUseCase,
) : ViewModel() {

    val bookmarkedAnime: Flow<List<Anime>> = getBookmarkedAnimeUseCase()
}

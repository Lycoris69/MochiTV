package com.lycoris.mochitv.domain.repository

import com.lycoris.mochitv.domain.model.Anime
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    suspend fun bookmark(animeId: String)
    suspend fun unbookmark(animeId: String)
    fun isBookmarked(animeId: String): Flow<Boolean>
    fun getBookmarkedAnime(): Flow<List<Anime>>
}

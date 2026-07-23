package com.lycoris.mochitv.data.repository

import com.lycoris.mochitv.data.local.dao.AnimeDao
import com.lycoris.mochitv.data.local.dao.BookmarkDao
import com.lycoris.mochitv.data.local.entity.BookmarkEntity
import com.lycoris.mochitv.data.local.entity.toDomain
import com.lycoris.mochitv.domain.model.Anime
import com.lycoris.mochitv.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val animeDao: AnimeDao,
) : BookmarkRepository {

    override suspend fun bookmark(animeId: String) =
        bookmarkDao.insert(BookmarkEntity(animeId))

    override suspend fun unbookmark(animeId: String) =
        bookmarkDao.delete(animeId)

    override fun isBookmarked(animeId: String): Flow<Boolean> =
        bookmarkDao.isBookmarked(animeId)

    override fun getBookmarkedAnime(): Flow<List<Anime>> =
        bookmarkDao.getAllIds().map { ids ->
            ids.mapNotNull { animeDao.getById(it)?.toDomain() }
        }
}

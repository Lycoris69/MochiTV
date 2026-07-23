package com.lycoris.mochitv.data.repository

import com.lycoris.mochitv.data.local.dao.SearchHistoryDao
import com.lycoris.mochitv.data.local.entity.SearchHistoryEntity
import com.lycoris.mochitv.domain.repository.SearchHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SearchHistoryRepositoryImpl @Inject constructor(
    private val dao: SearchHistoryDao,
) : SearchHistoryRepository {

    override suspend fun save(query: String) {
        if (query.isNotBlank()) dao.insert(SearchHistoryEntity(query.trim()))
    }

    override suspend fun delete(query: String) = dao.delete(query)

    override suspend fun clearAll() = dao.deleteAll()

    override fun getAll(): Flow<List<String>> =
        dao.getAll().map { list -> list.map { it.query } }
}

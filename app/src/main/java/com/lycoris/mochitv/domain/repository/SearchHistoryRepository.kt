package com.lycoris.mochitv.domain.repository

import kotlinx.coroutines.flow.Flow

interface SearchHistoryRepository {
    suspend fun save(query: String)
    suspend fun delete(query: String)
    suspend fun clearAll()
    fun getAll(): Flow<List<String>>
}

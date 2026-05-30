package com.ennam.app.data.repository

import com.ennam.app.data.db.EntryDao
import com.ennam.app.data.model.Entry
import kotlinx.coroutines.flow.Flow

class EntryRepository(private val entryDao: EntryDao) {

    fun getAllActive(): Flow<List<Entry>> = entryDao.getAllActive()

    fun getByCategory(category: String): Flow<List<Entry>> = entryDao.getByCategory(category)

    suspend fun getById(id: String): Entry? = entryDao.getById(id)

    suspend fun insert(entry: Entry) = entryDao.insert(entry)

    suspend fun update(entry: Entry) = entryDao.update(entry)

    suspend fun archive(id: String) = entryDao.archive(id)

    suspend fun delete(id: String) = entryDao.delete(id)

    fun search(query: String): Flow<List<Entry>> = entryDao.search(query)
}
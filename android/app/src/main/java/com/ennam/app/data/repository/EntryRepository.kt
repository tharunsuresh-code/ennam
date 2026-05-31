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

    fun search(query: String): Flow<List<Entry>> = entryDao.searchFts(query)

    fun searchLike(query: String): Flow<List<Entry>> = entryDao.searchLike(query)

    fun getByDateRange(startOfDay: Long, endOfDay: Long): Flow<List<Entry>> =
        entryDao.getByDateRange(startOfDay, endOfDay)

    fun getAllSorted(): Flow<List<Entry>> = entryDao.getAllSorted()

    // --- Phase 2 card interactions ---
    suspend fun toggleDone(id: String) = entryDao.toggleDone(id)
    suspend fun togglePinned(id: String) = entryDao.togglePinned(id)
    suspend fun toggleLocked(id: String) = entryDao.toggleLocked(id)
    suspend fun setAnswer(id: String, answer: String) = entryDao.setAnswer(id, answer)

    // --- Phase 2 search/embeddings ---
    suspend fun updateEmbedding(id: String, embedding: ByteArray) =
        entryDao.updateEmbedding(id, embedding)

    suspend fun getEntriesWithoutEmbedding(): List<Entry> =
        entryDao.getEntriesWithoutEmbedding()

    suspend fun updateRawText(id: String, rawText: String) = entryDao.updateRawText(id, rawText)

    suspend fun updateCategory(id: String, category: String) = entryDao.updateCategory(id, category)

    suspend fun getDistinctCategories(): List<String> = entryDao.getDistinctCategories()
}

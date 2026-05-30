package com.ennam.app.data.db

import androidx.room.*
import com.ennam.app.data.model.Entry
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Query("SELECT * FROM entries WHERE isArchived = 0 ORDER BY isPinned DESC, createdAt DESC")
    fun getAllActive(): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE isArchived = 0 AND category = :category ORDER BY isPinned DESC, createdAt DESC")
    fun getByCategory(category: String): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: String): Entry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: Entry)

    @Update
    suspend fun update(entry: Entry)

    @Query("UPDATE entries SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: String)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun delete(id: String)

    // --- Phase 2 Week 1 — card interaction queries ---

    @Query("UPDATE entries SET isDone = NOT isDone WHERE id = :id")
    suspend fun toggleDone(id: String)

    @Query("UPDATE entries SET isPinned = NOT isPinned WHERE id = :id")
    suspend fun togglePinned(id: String)

    @Query("UPDATE entries SET isLocked = NOT isLocked WHERE id = :id")
    suspend fun toggleLocked(id: String)

    @Query("UPDATE entries SET answer = :answer WHERE id = :id")
    suspend fun setAnswer(id: String, answer: String)

    // --- Phase 2 Week 2 — search ---

    /** FTS4 full-text search across rawText, summary, category, tags */
    @Query("""
        SELECT * FROM entries WHERE rowid IN (
            SELECT rowid FROM entries_fts WHERE entries_fts MATCH :query
        ) AND isArchived = 0
        ORDER BY isPinned DESC, createdAt DESC
        LIMIT 50
    """)
    fun searchFts(query: String): Flow<List<Entry>>

    /** Fallback LIKE search (used when FTS query is too short or simple) */
    @Query("""
        SELECT * FROM entries 
        WHERE isArchived = 0 
          AND (rawText LIKE '%' || :query || '%' 
            OR summary LIKE '%' || :query || '%'
            OR category LIKE '%' || :query || '%'
            OR tags LIKE '%' || :query || '%')
        ORDER BY isPinned DESC, createdAt DESC
        LIMIT 50
    """)
    fun searchLike(query: String): Flow<List<Entry>>

    /** "On this day" — entries from a specific date range */
    @Query("""
        SELECT * FROM entries 
        WHERE isArchived = 0
          AND createdAt >= :startOfDay 
          AND createdAt < :endOfDay
        ORDER BY createdAt DESC
    """)
    fun getByDateRange(startOfDay: Long, endOfDay: Long): Flow<List<Entry>>

    /** Update embedding for an entry */
    @Query("UPDATE entries SET embedding = :embedding WHERE id = :id")
    suspend fun updateEmbedding(id: String, embedding: ByteArray)

    /** Get all entries without embeddings (for batch embedding) */
    @Query("SELECT * FROM entries WHERE embedding IS NULL AND isArchived = 0")
    suspend fun getEntriesWithoutEmbedding(): List<Entry>

    /** Get all embeddings for similarity search */
    @Query("SELECT id, embedding FROM entries WHERE embedding IS NOT NULL AND isArchived = 0")
    suspend fun getAllEmbeddings(): List<EntryEmbedding>

    @Query("SELECT * FROM entries WHERE isArchived = 0 ORDER BY isPinned DESC, createdAt DESC")
    fun getAllSorted(): Flow<List<Entry>>
}

data class EntryEmbedding(
    val id: String,
    val embedding: ByteArray?
)

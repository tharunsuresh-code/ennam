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

    // --- Phase 2 interaction queries ---

    @Query("UPDATE entries SET isDone = NOT isDone WHERE id = :id")
    suspend fun toggleDone(id: String)

    @Query("UPDATE entries SET isPinned = NOT isPinned WHERE id = :id")
    suspend fun togglePinned(id: String)

    @Query("UPDATE entries SET isLocked = NOT isLocked WHERE id = :id")
    suspend fun toggleLocked(id: String)

    @Query("UPDATE entries SET answer = :answer WHERE id = :id")
    suspend fun setAnswer(id: String, answer: String)

    // Simple text search (no FTS5 virtual table set up yet)
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
    fun search(query: String): Flow<List<Entry>>

    // "On this day" — entries from exactly N days ago
    @Query("""
        SELECT * FROM entries 
        WHERE isArchived = 0
          AND createdAt >= :startOfDay 
          AND createdAt < :endOfDay
        ORDER BY createdAt DESC
    """)
    fun getByDateRange(startOfDay: Long, endOfDay: Long): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE isArchived = 0 ORDER BY isPinned DESC, createdAt DESC")
    fun getAllSorted(): Flow<List<Entry>>
}

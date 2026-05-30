package com.ennam.app.data.db

import androidx.room.*
import com.ennam.app.data.model.Entry
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Query("SELECT * FROM entries WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun getAllActive(): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE isArchived = 0 AND category = :category ORDER BY createdAt DESC")
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

    // Full-text search via SQLite FTS5
    @Query("""
        SELECT * FROM entries 
        WHERE entries MATCH :query 
        ORDER BY rank
        LIMIT 50
    """)
    fun search(query: String): Flow<List<Entry>>
}
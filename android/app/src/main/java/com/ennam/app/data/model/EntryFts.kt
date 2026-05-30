package com.ennam.app.data.model

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/**
 * FTS4 virtual table mirroring the `entries` table for full-text search.
 * Room auto-syncs this with the content table on insert/update/delete.
 */
@Fts4(contentEntity = Entry::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "entries_fts")
data class EntryFts(
    val rawText: String,
    val summary: String,
    val category: String,
    val tags: String
)

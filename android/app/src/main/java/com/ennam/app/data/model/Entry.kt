package com.ennam.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey val id: String,           // UUID
    val createdAt: Long,                   // Unix ms
    val sourceType: String,                // "text" | "voice" | "image"
    val rawText: String,                   // Original transcript/extracted text
    val summary: String,                   // SLM-condensed version
    val category: String,                  // SLM-chosen category
    val tags: String,                      // JSON array of tags
    val actionable: Boolean = false,
    val priority: String = "medium",       // "low" | "medium" | "high"
    val isArchived: Boolean = false
)

/**
 * Pending entry — created before SLM processing finishes.
 * Shown as shimmer card in feed, replaced once SLM returns.
 */
data class PendingEntry(
    val id: String,
    val createdAt: Long,
    val sourceType: String,
    val rawText: String
)
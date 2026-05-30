package com.ennam.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val slug: String,
    val label: String,
    val emoji: String,
    val layoutType: String,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0
) {
    companion object {
        val DEFAULTS = listOf(
            Category("todo", "Todos", "\u2705", "todo", isDefault = true, sortOrder = 0),
            Category("idea", "Ideas", "\ud83d\udca1", "idea", isDefault = true, sortOrder = 1),
            Category("receipt", "Receipts", "\ud83e\uddfe", "receipt", isDefault = true, sortOrder = 2),
            Category("journal", "Journal", "\ud83d\udcdd", "journal", isDefault = true, sortOrder = 3),
            Category("bookmark", "Bookmarks", "\ud83d\udcd6", "bookmark", isDefault = true, sortOrder = 4),
            Category("question", "Questions", "\u2753", "question", isDefault = true, sortOrder = 5),
            Category("screenshot", "Screenshots", "\ud83d\udcf8", "screenshot", isDefault = true, sortOrder = 6),
        )

        val ALL_LAYOUT_TYPES = setOf("todo", "idea", "receipt", "journal", "bookmark", "question", "screenshot", "generic")
    }
}

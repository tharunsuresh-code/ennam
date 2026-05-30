package com.ennam.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val ALL_CATEGORIES = listOf(
    "All", "Ideas", "Todos", "Receipts", "Journal", "Bookmarks", "Questions"
)

private val categoryToSlug = mapOf(
    "All" to "",
    "Ideas" to "idea",
    "Todos" to "todo",
    "Receipts" to "receipt",
    "Journal" to "journal",
    "Bookmarks" to "bookmark",
    "Questions" to "question"
)

@Composable
fun CategoryTabs(
    selectedLabel: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ALL_CATEGORIES.forEach { label ->
            FilterChip(
                selected = selectedLabel == label,
                onClick = { onCategorySelected(categoryToSlug[label] ?: label.lowercase()) },
                label = { Text(label) }
            )
        }
    }
}
package com.ennam.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Known category slugs → display labels */
val KNOWN_CATEGORIES = mapOf(
    "idea" to "Ideas",
    "todo" to "Todos",
    "receipt" to "Receipts",
    "journal" to "Journal",
    "bookmark" to "Bookmarks",
    "question" to "Questions",
    "screenshot" to "Screenshots"
)

/** Convert a category slug to its display label. Capitalizes unknown slugs. */
fun categoryLabel(slug: String): String {
    return KNOWN_CATEGORIES[slug] ?: slug.replaceFirstChar { it.uppercase() }
}

@Composable
fun CategoryTabs(
    selectedLabel: String,
    categories: List<String>,  // dynamic category slugs from the DB
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
        // "All" is always first
        FilterChip(
            selected = selectedLabel == "All",
            onClick = { onCategorySelected("") },
            label = { Text("All") }
        )
        categories.forEach { slug ->
            val label = categoryLabel(slug)
            FilterChip(
                selected = selectedLabel == label,
                onClick = { onCategorySelected(slug) },
                label = { Text(label) }
            )
        }
    }
}

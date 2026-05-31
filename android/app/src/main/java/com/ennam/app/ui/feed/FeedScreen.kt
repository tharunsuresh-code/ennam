package com.ennam.app.ui.feed

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ennam.app.data.model.Entry
import com.ennam.app.ui.components.CardCallbacks
import com.ennam.app.ui.components.CategoryTabs
import com.ennam.app.ui.components.EntryCard
import com.ennam.app.ui.components.PendingEntryCard
import com.ennam.app.ui.components.categoryEmojis
import com.ennam.app.ui.components.categoryLabel
import com.ennam.app.ui.input.InputSheet
import com.ennam.app.ui.search.SearchBar
import com.ennam.app.ui.search.SearchResults
import com.ennam.app.ui.search.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    searchViewModel: SearchViewModel,
    modelState: ModelState,
    onOpenSettings: () -> Unit = {}
) {
    val entries by viewModel.entries.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val pendingEntries by viewModel.pendingEntries.collectAsState()
    val onThisDayEntries by viewModel.onThisDayEntries.collectAsState()

    val searchQuery by searchViewModel.query.collectAsState()
    val searchResults by searchViewModel.results.collectAsState()
    val isSearchActive by searchViewModel.isActive.collectAsState()
    val isSearching by searchViewModel.isSearching.collectAsState()

    var showInputSheet by remember { mutableStateOf(false) }

    // ── Auto-scroll when new entries appear ──
    val listState = rememberLazyListState()
    val prevEntryCount = remember { mutableIntStateOf(0) }

    LaunchedEffect(entries.size) {
        val newCount = entries.size
        // Only auto-scroll when exactly 1 new entry was added (not category switch)
        if (newCount > 0 && newCount == prevEntryCount.intValue + 1) {
            listState.animateScrollToItem(0)
        }
        prevEntryCount.intValue = newCount
    }

    // Reset prevEntryCount when category changes
    LaunchedEffect(selectedCategory) {
        prevEntryCount.intValue = entries.size
    }

    // ── Selected entry for bottom sheet ──
    var selectedEntry by remember { mutableStateOf<Entry?>(null) }
    var editEntryId by remember { mutableStateOf<String?>(null) }
    var editEntryText by remember { mutableStateOf("") }

    val context = LocalContext.current

    val callbacks = CardCallbacks(
        onArchive = { viewModel.archiveEntry(it) },
        onDelete = { viewModel.deleteEntry(it) },
        onToggleDone = { id ->
            viewModel.toggleDone(id)
            viewModel.archiveEntry(id)
        },
        onTogglePin = { viewModel.togglePinned(it) },
        onAnswer = { id, answer -> viewModel.answerQuestion(id, answer) },
        onToggleLocked = { viewModel.toggleLocked(it) },
        onOpenUrl = { url ->
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        },
        onCardTap = { entry ->
            selectedEntry = entry
            editEntryId = null
            editEntryText = entry.rawText
        }
    )

    // ── Bottom sheet for card actions ──
    if (selectedEntry != null) {
        val dynamicCategories by viewModel.dynamicCategories.collectAsState()

        CardActionSheet(
            entry = selectedEntry!!,
            callbacks = callbacks,
            categories = dynamicCategories,
            isEditing = editEntryId == selectedEntry!!.id,
            editText = editEntryText,
            onEditTextChange = { editEntryText = it },
            onStartEdit = { editEntryId = selectedEntry!!.id },
            onSaveEdit = { newCategory ->
                val entry = selectedEntry ?: return@CardActionSheet
                if (editEntryText.isNotBlank() && editEntryText != entry.rawText) {
                    viewModel.updateEntryText(entry.id, editEntryText, newCategory)
                } else if (newCategory != null) {
                    viewModel.updateEntryCategory(entry.id, newCategory)
                }
                editEntryId = null
                selectedEntry = null
            },
            onDismiss = {
                selectedEntry = null
                editEntryId = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ennam") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (modelState == ModelState.Ready && !isSearchActive) {
                FloatingActionButton(
                    onClick = { showInputSheet = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Dump")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (modelState) {
                ModelState.Downloading -> DownloadScreen(progress = 0f, onProgress = {})
                ModelState.Loading -> LoadingScreen()
                ModelState.Error -> ErrorScreen()
                ModelState.Ready -> {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { searchViewModel.setQuery(it) },
                        onClear = { searchViewModel.clearQuery() },
                        isActive = isSearchActive,
                        onActiveChange = { searchViewModel.setActive(it) }
                    )

                    if (isSearchActive) {
                        SearchResults(
                            results = searchResults,
                            query = searchQuery,
                            isSearching = isSearching,
                            callbacks = callbacks,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val dynamicCategories by viewModel.dynamicCategories.collectAsState()

                        CategoryTabs(
                            selectedLabel = selectedCategory.ifBlank { "All" },
                            categories = dynamicCategories,
                            onCategorySelected = { viewModel.setCategory(it) }
                        )
                        Spacer(Modifier.height(8.dp))

                        if (entries.isEmpty() && pendingEntries.isEmpty() && onThisDayEntries.isEmpty()) {
                            EmptyState()
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(pendingEntries, key = { it.id }) { pending ->
                                    PendingEntryCard(pending)
                                }

                                if (onThisDayEntries.isNotEmpty()) {
                                    item {
                                        Text(
                                            "\uD83D\uDCC5 On this day",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    items(onThisDayEntries, key = { "otd-${it.id}" }) { entry ->
                                        EntryCard(entry = entry, callbacks = callbacks)
                                    }
                                    item {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                }

                                items(entries, key = { it.id }) { entry ->
                                    EntryCard(entry = entry, callbacks = callbacks)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Input bottom sheet
    if (showInputSheet) {
        val dynamicCategories by viewModel.dynamicCategories.collectAsState()
        InputSheet(
            onDismiss = { showInputSheet = false },
            onInput = { result ->
                showInputSheet = false
                viewModel.onInput(result)
            },
            dynamicCategories = dynamicCategories
        )
    }
}

// ────────── CARD ACTION BOTTOM SHEET ──────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardActionSheet(
    entry: Entry,
    callbacks: CardCallbacks,
    categories: List<String>,
    isEditing: Boolean,
    editText: String,
    onEditTextChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onSaveEdit: (String?) -> Unit,  // (newCategory: slug or null if unchanged)
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // ── Edit mode ──
            if (isEditing) {
                var editCategory by remember(entry.id) { mutableStateOf(entry.category) }

                Text("Edit Entry", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = editText,
                    onValueChange = onEditTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 10,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSaveEdit(editCategory) })
                )

                Spacer(Modifier.height(16.dp))
                Text("Category", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                // Category picker — current + available dynamic categories
                val allCategoryOptions = remember(categories) {
                    val slugs = categories.toMutableList()
                    if (entry.category !in slugs) slugs.add(0, entry.category)
                    slugs
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allCategoryOptions.forEach { slug ->
                        FilterChip(
                            selected = editCategory == slug,
                            onClick = { editCategory = slug },
                            label = { Text(categoryLabel(slug)) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        onEditTextChange(entry.rawText)
                        onDismiss()
                    }) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = {
                        val newCat = if (editCategory != entry.category) editCategory else null
                        onSaveEdit(newCat)
                    }) {
                        Text("Save")
                    }
                }
                Spacer(Modifier.height(24.dp))
                return@Column
            }

            // ── Entry info ──
            Text(
                text = entry.rawText,
                fontWeight = FontWeight.Medium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.summary.isNotBlank() && entry.summary != entry.rawText) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${categoryEmojis[entry.category] ?: ""} ${entry.category}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // ── Action buttons ──
            // Edit
            TextButton(
                onClick = onStartEdit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Edit text", modifier = Modifier.weight(1f))
            }

            // Category-specific actions
            when (entry.category) {
                "screenshot" -> {
                    TextButton(
                        onClick = {
                            callbacks.onTogglePin(entry.id)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = if (entry.isPinned) "Unpin" else "Pin",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (entry.isPinned) "Unpin from top" else "Pin to top",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                "bookmark" -> {
                    val url = remember(entry.rawText) {
                        Regex("""https?://[^\s,;!?)]+""").find(entry.rawText)?.value ?: ""
                    }
                    if (url.isNotBlank()) {
                        TextButton(
                            onClick = {
                                callbacks.onOpenUrl(url)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.OpenInBrowser,
                                contentDescription = "Open URL",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Open link", modifier = Modifier.weight(1f))
                        }
                    }
                }
                "todo" -> {
                    TextButton(
                        onClick = {
                            callbacks.onToggleDone(entry.id)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = if (entry.isDone) "Undo" else "Done",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (entry.isDone) "Mark as not done" else "Mark as done (archive)",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Archive
            TextButton(
                onClick = {
                    callbacks.onArchive(entry.id)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Archive, contentDescription = "Archive", modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Archive", modifier = Modifier.weight(1f))
            }

            // Delete
            TextButton(
                onClick = {
                    callbacks.onDelete(entry.id)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Delete", modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ────────── SCREEN STATE COMPOSABLES ──────────

@Composable
private fun DownloadScreen(progress: Float, onProgress: (Float) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Downloading model...", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text("Qwen2.5-1.5B-Instruct (~1 GB)", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text("Keep phone on WiFi", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Loading model...", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ErrorScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Something went wrong", color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Text("Try reinstalling the app", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("\uD83D\uDCE5", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            "Tap + to dump your first thought",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

enum class ModelState {
    Downloading, Loading, Ready, Error
}

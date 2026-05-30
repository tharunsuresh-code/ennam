package com.ennam.app.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ennam.app.ui.components.CardCallbacks
import com.ennam.app.ui.components.CategoryTabs
import com.ennam.app.ui.components.EntryCard
import com.ennam.app.ui.components.PendingEntryCard
import com.ennam.app.ui.input.InputResult
import com.ennam.app.ui.input.InputSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    modelState: ModelState
) {
    val entries by viewModel.entries.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val pendingEntries by viewModel.pendingEntries.collectAsState()
    val scope = rememberCoroutineScope()

    var showInputSheet by remember { mutableStateOf(false) }

    // Bottom sheet state for card actions
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedEntryId by remember { mutableStateOf<String?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }

    // Pull-to-refresh state
    var isRefreshing by remember { mutableStateOf(false) }

    val callbacks = CardCallbacks(
        onArchive = { id ->
            viewModel.archiveEntry(id)
        },
        onDelete = { id ->
            viewModel.deleteEntry(id)
        },
        onToggleDone = { id ->
            viewModel.toggleDone(id)
            // Auto-archive when toggled done
            viewModel.archiveEntry(id)
        },
        onTogglePin = { id ->
            viewModel.togglePinned(id)
        },
        onAnswer = { id, answer ->
            viewModel.answerQuestion(id, answer)
        },
        onToggleLocked = { id ->
            viewModel.toggleLocked(id)
        },
        onOpenUrl = { _ -> }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ennam") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (modelState == ModelState.Ready) {
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
                ModelState.Downloading -> DownloadScreen(
                    progress = 0f,
                    onProgress = {}
                )
                ModelState.Loading -> LoadingScreen()
                ModelState.Error -> ErrorScreen()
                ModelState.Ready -> {
                    CategoryTabs(
                        selectedLabel = selectedCategory.ifBlank { "All" },
                        onCategorySelected = { viewModel.setCategory(it) }
                    )

                    Spacer(Modifier.height(8.dp))

                    if (entries.isEmpty() && pendingEntries.isEmpty()) {
                        EmptyState()
                    } else {
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = {
                                // Trigger refresh by re-querying
                                scope.launch {
                                    isRefreshing = true
                                    // Small delay to show the spinner
                                    kotlinx.coroutines.delay(300)
                                    isRefreshing = false
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                // Pending entries first (shimmer)
                                items(pendingEntries, key = { it.id }) { pending ->
                                    PendingEntryCard(pending)
                                }

                                // Completed entries
                                items(entries, key = { it.id }) { entry ->
                                    EntryCard(
                                        entry = entry,
                                        callbacks = callbacks
                                    )
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
        InputSheet(
            onDismiss = { showInputSheet = false },
            onInput = { result ->
                showInputSheet = false
                viewModel.onInput(result)
            }
        )
    }

    // Card action bottom sheet
    if (showActionSheet && selectedEntryId != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showActionSheet = false
                selectedEntryId = null
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    "Actions",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(16.dp))

                FilledTonalButton(
                    onClick = {
                        selectedEntryId?.let { viewModel.archiveEntry(it) }
                        showActionSheet = false
                        selectedEntryId = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📦 Archive")
                }

                Spacer(Modifier.height(8.dp))

                FilledTonalButton(
                    onClick = {
                        selectedEntryId?.let { viewModel.deleteEntry(it) }
                        showActionSheet = false
                        selectedEntryId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("🗑️ Delete")
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

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
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
        )
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
        Text("📥", style = MaterialTheme.typography.displayLarge)
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

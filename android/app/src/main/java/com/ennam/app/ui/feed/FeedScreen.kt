package com.ennam.app.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ennam.app.ui.components.CategoryTabs
import com.ennam.app.ui.components.EntryCard
import com.ennam.app.ui.components.PendingEntryCard
import com.ennam.app.ui.input.InputResult
import com.ennam.app.ui.input.InputSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    modelState: ModelState
) {
    val entries by viewModel.entries.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val pendingEntries by viewModel.pendingEntries.collectAsState()

    var showInputSheet by remember { mutableStateOf(false) }

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
                    onProgress = {} // handled in VM
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
                                EntryCard(entry)
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
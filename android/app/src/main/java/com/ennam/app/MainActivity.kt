package com.ennam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ennam.app.ui.feed.FeedScreen
import com.ennam.app.ui.feed.FeedViewModel
import com.ennam.app.ui.feed.ModelState
import com.ennam.app.ui.search.SearchViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val feedViewModel: FeedViewModel = viewModel()
                val searchViewModel: SearchViewModel = viewModel()
                val scope = rememberCoroutineScope()
                var modelState by remember { mutableStateOf(ModelState.Downloading) }
                var downloadProgress by remember { mutableFloatStateOf(0f) }

                // Startup: download SLM model → load → ready
                LaunchedEffect(Unit) {
                    if (!feedViewModel.isModelDownloaded()) {
                        modelState = ModelState.Downloading
                        feedViewModel.downloadModel(
                            progress = { downloadProgress = it },
                            onComplete = { ok ->
                                if (ok) {
                                    modelState = ModelState.Loading
                                    scope.launch {
                                        feedViewModel.loadModel { modelState = ModelState.Ready }
                                    }
                                } else {
                                    modelState = ModelState.Error
                                }
                            }
                        )
                    } else {
                        modelState = ModelState.Loading
                        scope.launch {
                            feedViewModel.loadModel { modelState = ModelState.Ready }
                        }
                    }

                    // Start embedding model download in background
                    if (!feedViewModel.isEmbeddingModelDownloaded()) {
                        feedViewModel.downloadEmbeddingModel(
                            progress = {},
                            onComplete = { if (it) feedViewModel.loadEmbeddingModel() }
                        )
                    } else {
                        feedViewModel.loadEmbeddingModel()
                    }

                    // Also load embedding model for search
                    if (!searchViewModel.isModelDownloaded()) {
                        searchViewModel.downloadModel(
                            progress = {},
                            onComplete = { if (it) searchViewModel.loadModel() }
                        )
                    } else {
                        searchViewModel.loadModel()
                    }
                }

                FeedScreen(
                    viewModel = feedViewModel,
                    searchViewModel = searchViewModel,
                    modelState = modelState
                )
            }
        }
    }
}

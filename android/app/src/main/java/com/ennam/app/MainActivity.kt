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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val viewModel: FeedViewModel = viewModel()
                val scope = rememberCoroutineScope()
                var modelState by remember { mutableStateOf(ModelState.Downloading) }
                var downloadProgress by remember { mutableFloatStateOf(0f) }

                // Startup: download → load → ready
                LaunchedEffect(Unit) {
                    if (!viewModel.isModelDownloaded()) {
                        modelState = ModelState.Downloading
                        viewModel.downloadModel(
                            progress = { downloadProgress = it },
                            onComplete = { ok ->
                                if (ok) {
                                    modelState = ModelState.Loading
                                    scope.launch {
                                        viewModel.loadModel { modelState = ModelState.Ready }
                                    }
                                } else {
                                    modelState = ModelState.Error
                                }
                            }
                        )
                    } else {
                        modelState = ModelState.Loading
                        scope.launch {
                            viewModel.loadModel { modelState = ModelState.Ready }
                        }
                    }
                }

                FeedScreen(
                    viewModel = viewModel,
                    modelState = modelState
                )
            }
        }
    }
}
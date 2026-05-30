package com.ennam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ennam.app.ml.Classifier
import com.ennam.app.ml.LlamaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var engine: LlamaEngine
    private lateinit var classifier: Classifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = LlamaEngine(this)
        classifier = Classifier(engine)

        setContent {
            MaterialTheme {
                EnnamApp(engine, classifier)
            }
        }
    }
}

@Composable
fun EnnamApp(engine: LlamaEngine, classifier: Classifier) {
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf(AppState.DOWNLOADING) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("") }
    var resultJson by remember { mutableStateOf("") }
    var inferenceTime by remember { mutableStateOf("") }

    val testInputs = listOf(
        "Pick up milk and eggs from the store",
        "Idea: what if we built a voice-controlled todo list?",
        "Had a great dinner at Pasta Bella. The carbonara was amazing. Paid $32.",
        "Today I felt grateful for the morning walk. The air was crisp and the sun was just rising.",
        "https://github.com/ggml-org/llama.cpp - interesting project for on-device ML",
        "How do I deploy a Kotlin Multiplatform app to the Play Store?",
        "Screenshot of the error message: NullPointerException at line 142",
    )

    // Init: download model if needed, then load
    LaunchedEffect(Unit) {
        if (!engine.isModelDownloaded()) {
            state = AppState.DOWNLOADING
            engine.downloadModel(
                progress = { downloadProgress = it },
                onComplete = { ok ->
                    if (ok) {
                        state = AppState.LOADING
                        scope.launch {
                            val loaded = withContext(Dispatchers.IO) { engine.loadModel() }
                            state = if (loaded) AppState.READY else AppState.ERROR
                        }
                    } else {
                        state = AppState.ERROR
                        statusText = "Download failed"
                    }
                }
            )
        } else {
            state = AppState.LOADING
            val loaded = withContext(Dispatchers.IO) { engine.loadModel() }
            state = if (loaded) AppState.READY else AppState.ERROR
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Ennam Prototype", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))

        when (state) {
            AppState.DOWNLOADING -> {
                Text("Downloading model (1.5 GB)...")
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${(downloadProgress * 100).toInt()}%")
            }
            AppState.LOADING -> {
                Text("Loading model... (this may take a moment)")
                CircularProgressIndicator()
            }
            AppState.ERROR -> {
                Text("Error: $statusText", color = MaterialTheme.colorScheme.error)
            }
            AppState.READY -> {
                Text("✅ Model loaded. Ready for testing.", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))

                Text("Test Inputs:", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                testInputs.forEach { input ->
                    Button(
                        onClick = {
                            scope.launch {
                                resultJson = ""
                                inferenceTime = ""
                                val start = System.currentTimeMillis()
                                val result = withContext(Dispatchers.IO) {
                                    classifier.classify(
                                        Classifier.ClassifyInput(rawText = input, sourceType = "text")
                                    )
                                }
                                val elapsed = System.currentTimeMillis() - start
                                inferenceTime = "${elapsed}ms"
                                resultJson = buildString {
                                    appendLine("Category: ${result.category}")
                                    appendLine("Summary: ${result.summary}")
                                    appendLine("Tags: ${result.tags.joinToString(", ")}")
                                    appendLine("Actionable: ${result.actionable}")
                                    appendLine("Priority: ${result.priority}")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = input.take(50) + if (input.length > 50) "..." else "",
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                }

                if (inferenceTime.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Inference: $inferenceTime", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = resultJson,
                            modifier = Modifier.padding(12.dp),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

enum class AppState {
    DOWNLOADING, LOADING, READY, ERROR
}
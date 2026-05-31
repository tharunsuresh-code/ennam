package com.ennam.app.ui.input

import android.Manifest
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import android.speech.RecognizerIntent
import android.content.Intent
import android.speech.RecognitionListener
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

data class InputResult(
    val rawText: String,
    val sourceType: String,
    val categoryOverride: String? = null  // null = let SLM auto-classify
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputSheet(
    onDismiss: () -> Unit,
    onInput: (InputResult) -> Unit,
    dynamicCategories: List<String> = emptyList()
) {
    val context = LocalContext.current
    var textInput by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRequested = true
        if (granted) {
            isRecording = true
            startVoiceRecognition(context) { result ->
                isRecording = false
                onInput(InputResult(result, "voice"))
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Will use CameraX — for Phase 1, open camera intent
            onInput(InputResult("[Image captured - OCR text pending]", "image"))
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text("Quick Dump", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            // Text input
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                label = { Text("Type or paste...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            Spacer(Modifier.height(12.dp))

            // Category picker
            var selectedCategory by remember { mutableStateOf<String?>(null) }
            Text(
                "Category: ${selectedCategory ?: "Auto (SLM)"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("Auto") },
                    leadingIcon = { Text("✨", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = selectedCategory == "todo",
                    onClick = { selectedCategory = "todo" },
                    label = { Text("Todo") },
                    leadingIcon = { Text("📋", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = selectedCategory == "idea",
                    onClick = { selectedCategory = "idea" },
                    label = { Text("Idea") },
                    leadingIcon = { Text("💡", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = selectedCategory == "receipt",
                    onClick = { selectedCategory = "receipt" },
                    label = { Text("Receipt") },
                    leadingIcon = { Text("🧾", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = selectedCategory == "journal",
                    onClick = { selectedCategory = "journal" },
                    label = { Text("Journal") },
                    leadingIcon = { Text("📝", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = selectedCategory == "bookmark",
                    onClick = { selectedCategory = "bookmark" },
                    label = { Text("Bookmark") },
                    leadingIcon = { Text("🔖", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = selectedCategory == "question",
                    onClick = { selectedCategory = "question" },
                    label = { Text("Question") },
                    leadingIcon = { Text("❓", style = MaterialTheme.typography.labelSmall) }
                )

                // Dynamic categories (extra ones created by the SLM)
                val knownSlugs = setOf("todo", "idea", "receipt", "journal", "bookmark", "question")
                dynamicCategories.filter { it !in knownSlugs }.forEach { slug ->
                    FilterChip(
                        selected = selectedCategory == slug,
                        onClick = { selectedCategory = slug },
                        label = { Text(slug.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Voice button
                FilledTonalIconButton(
                    onClick = {
                        val cat = selectedCategory
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            isRecording = true
                            startVoiceRecognition(context) { result ->
                                isRecording = false
                                onInput(InputResult(result, "voice", cat))
                            }
                        } else {
                            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    enabled = !isRecording
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice")
                }

                // Camera button
                FilledTonalIconButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            onInput(InputResult("[Image captured - OCR pending]", "image", selectedCategory))
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Camera")
                }

                // Send text
                FilledIconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            onInput(InputResult(textInput.trim(), "text", selectedCategory))
                            textInput = ""
                        }
                    },
                    enabled = textInput.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }

            if (isRecording) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "🎤 Listening... tap the mic button again to stop",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun startVoiceRecognition(context: android.content.Context, onResult: (String) -> Unit) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        onResult("[Voice recognition not available]")
        return
    }

    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }

    recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            recognizer.destroy()
            onResult(text)
        }
        override fun onError(error: Int) {
            recognizer.destroy()
            onResult("[Voice error: $error]")
        }
        override fun onReadyForSpeech(p0: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(p0: Float) {}
        override fun onBufferReceived(p0: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(p0: Bundle?) {}
        override fun onEvent(p0: Int, p1: Bundle?) {}
    })

    recognizer.startListening(intent)
}
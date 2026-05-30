package com.ennam.app.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ennam.app.data.db.AppDatabase
import com.ennam.app.data.model.Entry
import com.ennam.app.data.model.PendingEntry
import com.ennam.app.data.repository.EntryRepository
import com.ennam.app.ml.Classifier
import com.ennam.app.ml.LlamaEngine
import com.ennam.app.ui.input.InputResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class FeedViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = EntryRepository(db.entryDao())
    private val engine = LlamaEngine(application)
    private val classifier = Classifier(engine)

    private val _selectedCategory = MutableStateFlow("")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _pendingEntries = MutableStateFlow<List<PendingEntry>>(emptyList())
    val pendingEntries: StateFlow<List<PendingEntry>> = _pendingEntries.asStateFlow()

    private val _slmReady = MutableStateFlow(false)

    /** Feed entries for the selected category */
    val entries: StateFlow<List<Entry>> = _selectedCategory.flatMapLatest { category ->
        if (category.isBlank()) {
            repository.getAllActive()
        } else {
            repository.getByCategory(category)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    /** Called when model finishes loading */
    fun onModelReady() {
        _slmReady.value = true
    }

    fun loadModel(onReady: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (engine.loadModel()) {
                _slmReady.value = true
                onReady()
            }
        }
    }

    fun isModelDownloaded(): Boolean = engine.isModelDownloaded()

    fun downloadModel(progress: (Float) -> Unit, onComplete: (Boolean) -> Unit) {
        engine.downloadModel(progress, onComplete)
    }

    /** Handle user input from the input sheet */
    fun onInput(inputResult: InputResult) {
        val pending = PendingEntry(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            sourceType = inputResult.sourceType,
            rawText = inputResult.rawText
        )

        // Show pending immediately
        _pendingEntries.value = _pendingEntries.value + pending

        // Classify in background
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = classifier.classify(
                    Classifier.ClassifyInput(inputResult.rawText, inputResult.sourceType)
                )

                val entry = Entry(
                    id = pending.id,
                    createdAt = pending.createdAt,
                    sourceType = pending.sourceType,
                    rawText = pending.rawText,
                    summary = result.summary,
                    category = result.category,
                    tags = result.tags.joinToString(",") { "\"$it\"" }.let { "[$it]" },
                    actionable = result.actionable,
                    priority = result.priority
                )

                repository.insert(entry)
            } catch (e: Exception) {
                // Fallback: insert raw as uncategorized
                val fallback = Entry(
                    id = pending.id,
                    createdAt = pending.createdAt,
                    sourceType = pending.sourceType,
                    rawText = pending.rawText,
                    summary = pending.rawText.take(100),
                    category = "idea",
                    tags = "[]",
                    actionable = false,
                    priority = "medium"
                )
                repository.insert(fallback)
            } finally {
                // Remove from pending
                _pendingEntries.value = _pendingEntries.value.filter { it.id != pending.id }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.unload()
    }
}
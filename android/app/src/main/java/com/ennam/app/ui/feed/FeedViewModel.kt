package com.ennam.app.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ennam.app.data.db.AppDatabase
import com.ennam.app.data.model.Entry
import com.ennam.app.data.model.PendingEntry
import com.ennam.app.data.repository.EntryRepository
import com.ennam.app.ml.Classifier
import com.ennam.app.ml.Embedder
import com.ennam.app.ml.LlamaEngine
import com.ennam.app.ui.input.InputResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

class FeedViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val repository = EntryRepository(db.entryDao())
    private val engine = LlamaEngine(application)
    private val classifier = Classifier(engine)
    val embedder = Embedder.getInstance(application)

    private val _selectedCategory = MutableStateFlow("")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _pendingEntries = MutableStateFlow<List<PendingEntry>>(emptyList())
    val pendingEntries: StateFlow<List<PendingEntry>> = _pendingEntries.asStateFlow()

    private val _slmReady = MutableStateFlow(false)

    // Queue for inputs submitted while model is loading
    private data class QueuedInput(
        val pending: PendingEntry,
        val inputResult: InputResult
    )
    private val classificationQueue = mutableListOf<QueuedInput>()

    // "On this day" entries
    private val _onThisDayEntries = MutableStateFlow<List<Entry>>(emptyList())
    val onThisDayEntries: StateFlow<List<Entry>> = _onThisDayEntries.asStateFlow()

    /** Feed entries for the selected category */
    val entries: StateFlow<List<Entry>> = _selectedCategory.flatMapLatest { category ->
        if (category.isBlank()) {
            repository.getAllActive()
        } else {
            repository.getByCategory(category)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Dynamic categories derived from all active entries */
    val dynamicCategories: StateFlow<List<String>> = repository.getAllActive().map { allEntries ->
        allEntries.map { it.category }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadOnThisDay()
    }

    private fun loadOnThisDay() {
        viewModelScope.launch(Dispatchers.IO) {
            val cal = Calendar.getInstance()
            val todayStart = cal.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            // Check: 1 month ago, 1 year ago
            val datesToCheck = listOf(30L, 365L)
            val allOtd = mutableListOf<Entry>()

            for (daysAgo in datesToCheck) {
                val start = todayStart - daysAgo * 86_400_000L
                val end = start + 86_400_000L
                try {
                    repository.getByDateRange(start, end).first().let { entries ->
                        if (entries.isNotEmpty()) {
                            allOtd.addAll(entries)
                        }
                    }
                } catch (_: Exception) {}
            }

            _onThisDayEntries.value = allOtd
        }
    }

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
                processClassificationQueue()
                onReady()
            }
        }
    }

    fun isModelLoaded(): Boolean = engine.isLoaded()

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

        if (engine.isLoaded()) {
            // Model ready — classify now
            classifyAndInsert(pending, inputResult)
        } else {
            // Model loading in background — queue for later
            classificationQueue.add(QueuedInput(pending, inputResult))
        }
    }

    /** Classify a single input and insert the result into the DB */
    private fun classifyAndInsert(pending: PendingEntry, inputResult: InputResult) {
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
                    category = inputResult.categoryOverride ?: result.category,
                    tags = result.tags.joinToString(",") { "\"$it\"" }.let { "[$it]" },
                    actionable = result.actionable,
                    priority = result.priority
                )

                repository.insert(entry)

                // Queue embedding computation in background
                computeAndStoreEmbedding(entry.id, entry.rawText + " " + entry.summary)

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
                computeAndStoreEmbedding(fallback.id, fallback.rawText)
            } finally {
                // Remove from pending
                _pendingEntries.value = _pendingEntries.value.filter { it.id != pending.id }
            }
        }
    }

    /** Process all queued classifications once the model finishes loading */
    private fun processClassificationQueue() {
        val batch = classificationQueue.toList()
        classificationQueue.clear()
        for (queued in batch) {
            classifyAndInsert(queued.pending, queued.inputResult)
        }
    }

    /** Compute embedding and store. Runs on IO, doesn't block UI. */
    private fun computeAndStoreEmbedding(id: String, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!embedder.isModelReady) return@launch
                val vec = embedder.embed(text) ?: return@launch
                val bytes = embedder.floatArrayToBytes(vec)
                repository.updateEmbedding(id, bytes)
            } catch (_: Exception) {
                // Embedding failure is non-critical — skip silently
            }
        }
    }

    // ────────── Embedding model ──────────

    fun isEmbeddingModelDownloaded(): Boolean = embedder.isDownloaded()

    fun downloadEmbeddingModel(progress: (Float) -> Unit, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            embedder.downloadIfNeeded(progress, onComplete)
        }
    }

    fun loadEmbeddingModel() {
        viewModelScope.launch {
            embedder.load()
            // Backfill embeddings for existing entries
            try {
                val missing = repository.getEntriesWithoutEmbedding()
                for (entry in missing) {
                    val text = "${entry.rawText} ${entry.summary}"
                    val vec = embedder.embed(text) ?: continue
                    val bytes = embedder.floatArrayToBytes(vec)
                    repository.updateEmbedding(entry.id, bytes)
                }
            } catch (_: Exception) {}
        }
    }

    // ────────── Card interactions ──────────

    fun archiveEntry(id: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.archive(id) }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.delete(id) }
    }

    fun toggleDone(id: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.toggleDone(id) }
    }

    fun togglePinned(id: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.togglePinned(id) }
    }

    fun toggleLocked(id: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.toggleLocked(id) }
    }

    fun answerQuestion(id: String, answer: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.setAnswer(id, answer) }
    }

    fun updateEntryText(id: String, newText: String, newCategory: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateRawText(id, newText)
            if (newCategory != null) {
                repository.updateCategory(id, newCategory)
            }
            // Recompute embedding for updated text
            val entry = repository.getById(id) ?: return@launch
            computeAndStoreEmbedding(id, newText + " " + entry.summary)
        }
    }

    fun updateEntryCategory(id: String, newCategory: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateCategory(id, newCategory)
        }
    }

    /** Unload both LLM and embedder models from memory */
    fun unloadAll() {
        engine.unload()
        embedder.unload()
        _slmReady.value = false
    }

    override fun onCleared() {
        super.onCleared()
        unloadAll()
    }
}

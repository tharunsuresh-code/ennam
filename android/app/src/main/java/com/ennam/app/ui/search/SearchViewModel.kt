package com.ennam.app.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ennam.app.data.db.AppDatabase
import com.ennam.app.data.model.Entry
import com.ennam.app.data.repository.EntryRepository
import com.ennam.app.ml.Embedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = EntryRepository(db.entryDao())
    val embedder = Embedder(application)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<Entry>>(emptyList())
    val results: StateFlow<List<Entry>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private var searchJob: Job? = null

    fun setQuery(q: String) {
        _query.value = q
        _isActive.value = q.isNotBlank()
        if (q.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch(Dispatchers.Default) {
                _isSearching.value = true
                search(q)
                _isSearching.value = false
            }
        } else {
            _results.value = emptyList()
        }
    }

    fun clearQuery() {
        _query.value = ""
        _isActive.value = false
        _results.value = emptyList()
        searchJob?.cancel()
    }

    fun setActive(active: Boolean) {
        _isActive.value = active
        if (!active && _query.value.isBlank()) {
            _results.value = emptyList()
        }
    }

    private suspend fun search(query: String) {
        // 1. FTS4 keyword search
        val ftsResult = mutableListOf<Entry>()
        try {
            repository.search(query).first().let { ftsResult.addAll(it) }
        } catch (_: Exception) {
            // Fallback to LIKE
            try {
                repository.searchLike(query).first().let { ftsResult.addAll(it) }
            } catch (_: Exception) {}
        }

        // 2. Semantic search (if embedder is ready)
        val semanticResults = if (embedder.isModelReady) {
            try {
                val queryEmbedding = embedder.embed(query)
                if (queryEmbedding != null) {
                    val allEntries = mutableListOf<Entry>()
                    try {
                        repository.getAllSorted().first().let { allEntries.addAll(it) }
                    } catch (_: Exception) {}

                    if (allEntries.isNotEmpty()) {
                        val scored = allEntries.mapNotNull { entry ->
                            val emb = entry.embedding
                            if (emb != null) {
                                val entryVec = embedder.bytesToFloatArray(emb)
                                val score = embedder.cosineSimilarity(queryEmbedding, entryVec)
                                entry to score
                            } else null
                        }
                            .filter { it.second > 0.3f } // similarity threshold
                            .sortedByDescending { it.second }
                            .map { it.first }

                        // Merge: FTS results first, then semantic results not already in FTS
                        val ftsIds = ftsResult.map { it.id }.toSet()
                        (ftsResult + scored.filter { it.id !in ftsIds }).distinctBy { it.id }
                    } else ftsResult
                } else ftsResult
            } catch (_: Exception) { ftsResult }
        } else ftsResult

        _results.value = semanticResults
    }

    /** Download embedding model */
    fun downloadModel(progress: (Float) -> Unit, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            embedder.downloadIfNeeded(progress, onComplete)
        }
    }

    fun loadModel() {
        viewModelScope.launch {
            embedder.load()
        }
    }

    fun isModelDownloaded(): Boolean = embedder.isDownloaded()

    fun unload() {
        embedder.unload()
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
    }
}

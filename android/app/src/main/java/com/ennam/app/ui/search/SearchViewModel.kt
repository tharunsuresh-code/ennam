package com.ennam.app.ui.search

import android.app.Application
import android.util.Log
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
    val embedder = Embedder.getInstance(application)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<Entry>>(emptyList())
    val results: StateFlow<List<Entry>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _searchMode = MutableStateFlow(SearchMode.KEYWORD)
    val searchMode: StateFlow<SearchMode> = _searchMode.asStateFlow()

    private var searchJob: Job? = null

    enum class SearchMode {
        KEYWORD,        // LIKE keyword search only
        KEYWORD_AND_SEMANTIC  // LIKE + semantic merged
    }

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
        // 1. LIKE keyword search — reliable, catches partial matches
        //    Searches rawText, summary, category, tags columns
        val likeResults = mutableListOf<Entry>()
        try {
            repository.searchLike(query).first().let { likeResults.addAll(it) }
        } catch (_: Exception) {}

        // If LIKE finds results, use KEYWORD mode by default
        _searchMode.value = if (embedder.isModelReady && likeResults.isNotEmpty()) {
            SearchMode.KEYWORD_AND_SEMANTIC
        } else {
            SearchMode.KEYWORD
        }

        // 2. Semantic search as enhancement (if model is ready)
        val mergedResults = if (embedder.isModelReady) {
            Log.d("SearchVM", "Model ready, attempting semantic search")
            try {
                val queryEmbedding = embedder.embed(query)
                Log.d("SearchVM", "Query embedding: ${queryEmbedding != null}")
                if (queryEmbedding != null) {
                    // Fetch all active entries with embeddings
                    val allEntries = mutableListOf<Entry>()
                    try {
                        repository.getAllSorted().first().let { allEntries.addAll(it) }
                    } catch (_: Exception) {}

                    if (allEntries.isNotEmpty()) {
                        val likeIds = likeResults.map { it.id }.toSet()

                        // Score each entry: keyword match bonus + semantic score
                        val scored = allEntries.mapNotNull { entry ->
                            val keywordBonus = if (entry.id in likeIds) 1.0f else 0.0f
                            val semanticScore = entry.embedding?.let { emb ->
                                val entryVec = embedder.bytesToFloatArray(emb)
                                embedder.cosineSimilarity(queryEmbedding, entryVec)
                            } ?: 0.0f

                            // Combined score: keyword bonus + semantic directly (no halving)
                            val combined = keywordBonus + semanticScore
                            if (combined > 0.2f) entry to combined else null
                        }
                            .sortedByDescending { it.second }
                            .map { it.first }
                            .distinctBy { it.id }

                        if (scored.isNotEmpty()) {
                            _searchMode.value = SearchMode.KEYWORD_AND_SEMANTIC
                            scored
                        } else likeResults
                    } else likeResults
                } else likeResults
            } catch (_: Exception) { likeResults }
        } else likeResults

        _results.value = mergedResults
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
            // Backfill: compute embeddings for entries that don't have them
            backfillEmbeddings()
        }
    }

    /** Compute embeddings for all entries missing them */
    private suspend fun backfillEmbeddings() {
        try {
            val missing = repository.getEntriesWithoutEmbedding()
            if (missing.isEmpty()) return
            for (entry in missing) {
                val text = "${entry.rawText} ${entry.summary}"
                val vec = embedder.embed(text) ?: continue
                val bytes = embedder.floatArrayToBytes(vec)
                repository.updateEmbedding(entry.id, bytes)
            }
        } catch (_: Exception) {
            // Backfill failure is non-critical
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

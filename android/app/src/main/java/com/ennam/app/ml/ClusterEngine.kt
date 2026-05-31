package com.ennam.app.ml

import com.ennam.app.data.model.Entry

/**
 * Threshold-based clustering using stored 384-dim sentence embeddings.
 *
 * Algorithm:
 * 1. Compute pairwise cosine similarity for all entries with embeddings
 * 2. Build a similarity graph: edges exist where similarity > threshold
 * 3. Find connected components → clusters
 * 4. Generate short human-readable labels from top keywords
 */
class ClusterEngine {

    data class Cluster(
        val id: Int,
        val label: String,
        val entries: List<Entry>
    )

    /**
     * Run clustering on a list of entries.
     *
     * @param entries All active entries (with or without embeddings)
     * @param similarityThreshold Cosine similarity threshold [0..1] for grouping (default 0.55)
     * @return List of clusters (non-empty). Entries without embeddings or that don't
     *         match any cluster are grouped into a "Misc" cluster.
     */
    fun compute(entries: List<Entry>, similarityThreshold: Float = 0.55f): List<Cluster> {
        // Filter entries that have embeddings
        val embedEntries = entries.filter { it.embedding != null && it.embedding!!.isNotEmpty() }

        if (embedEntries.size < 2) {
            // Not enough entries to cluster — all go to "Misc" or a single cluster
            return if (entries.isEmpty()) emptyList()
            else listOf(Cluster(0, "All", entries))
        }

        // Convert embeddings to float arrays
        val floatEmbs = embedEntries.map { bytesToFloatArray(it.embedding!!) }

        // Build adjacency: n x n similarity matrix
        val n = embedEntries.size
        val adj = Array(n) { BooleanArray(n) }

        for (i in 0 until n) {
            adj[i][i] = true
            for (j in i + 1 until n) {
                val sim = cosineSimilarity(floatEmbs[i], floatEmbs[j])
                if (sim >= similarityThreshold) {
                    adj[i][j] = true
                    adj[j][i] = true
                }
            }
        }

        // Find connected components (BFS)
        val visited = BooleanArray(n)
        val rawClusters = mutableListOf<Set<Int>>()

        for (i in 0 until n) {
            if (visited[i]) continue
            val component = mutableSetOf<Int>()
            val queue = ArrayDeque<Int>()
            queue.addLast(i)
            visited[i] = true
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                component.add(node)
                for (neighbor in 0 until n) {
                    if (!visited[neighbor] && adj[node][neighbor]) {
                        visited[neighbor] = true
                        queue.addLast(neighbor)
                    }
                }
            }
            if (component.size > 1) {
                rawClusters.add(component)
            }
        }

        // Collect unclustered entries (singletons that didn't match any cluster)
        val clusteredIndices = rawClusters.flatten().toSet()
        val unclusteredEntries = embedEntries.filterIndexed { idx, _ ->
            idx !in clusteredIndices
        }

        // Build final Cluster objects
        val result = mutableListOf<Cluster>()
        var clusterId = 0

        for (component in rawClusters) {
            val clusterEntries = component.map { embedEntries[it] }
            val label = generateLabel(clusterEntries)
            result.add(Cluster(clusterId++, label, clusterEntries))
        }

        // Add a "Misc" bucket for entries that don't fit any cluster
        if (unclusteredEntries.isNotEmpty()) {
            val label = if (unclusteredEntries.size == entries.size - result.sumOf { it.entries.size }) {
                "Uncategorized"
            } else {
                "Other"
            }
            result.add(Cluster(clusterId++, label, unclusteredEntries))
        }

        // Add entries without any embedding to Misc
        val noEmbedEntries = entries.filter { it.embedding == null || it.embedding!!.isEmpty() }
        if (noEmbedEntries.isNotEmpty()) {
            val existingMisc = result.find { it.label == "Misc" || it.label == "Other" || it.label == "Uncategorized" }
            if (existingMisc != null) {
                val idx = result.indexOf(existingMisc)
                result[idx] = existingMisc.copy(entries = existingMisc.entries + noEmbedEntries)
            } else {
                result.add(Cluster(clusterId++, "Misc", noEmbedEntries))
            }
        }

        // Sort clusters by size (largest first)
        return result.sortedByDescending { it.entries.size }
    }

    /** Cosine similarity between two normalized embeddings */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            dot += a[i] * b[i]
        }
        return dot.coerceIn(-1f, 1f)
    }

    /** Convert ByteArray of 384 float32s back to FloatArray */
    private fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.nativeOrder())
        val result = FloatArray(bytes.size / 4)
        buffer.asFloatBuffer().get(result)
        return result
    }

    /** Generate a short human-readable label from a cluster's entries */
    private fun generateLabel(entries: List<Entry>): String {
        if (entries.isEmpty()) return "Empty"
        if (entries.size == 1) {
            // Single entry: use first meaningful words
            val text = entries[0].rawText.trim().take(30)
            return text.ifBlank { "Untitled" }
        }

        // Count word frequency across all entries in the cluster
        val stopWords = setOf(
            "the", "a", "an", "is", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would",
            "can", "could", "shall", "should", "may", "might", "must",
            "to", "of", "in", "for", "on", "with", "at", "by", "from",
            "as", "into", "through", "during", "before", "after",
            "above", "below", "between", "out", "off", "over", "under",
            "again", "further", "then", "once", "here", "there",
            "i", "me", "my", "myself", "we", "our", "ours", "ourselves",
            "you", "your", "yours", "yourself", "he", "him", "his",
            "she", "her", "hers", "it", "its", "they", "them", "their",
            "not", "this", "that", "these", "those", "and", "but", "or",
            "because", "if", "when", "where", "how", "what", "which",
            "who", "whom", "whose", "just", "about", "up", "more",
            "some", "any", "each", "every", "all", "both", "few",
            "no", "nor", "so", "very", "too", "quite", "still",
            "get", "got", "go", "going", "went", "see", "need",
            "like", "also", "even", "much", "many"
        )

        val wordCounts = mutableMapOf<String, Int>()

        for (entry in entries) {
            val text = entry.rawText.lowercase()
                .replace(Regex("""[^a-z0-9\s']"""), " ")
                .trim()
            val words = text.split(Regex("""\s+"""))
                .filter { it.length > 2 && it !in stopWords }

            for (word in words) {
                wordCounts[word] = (wordCounts[word] ?: 0) + 1
            }
        }

        if (wordCounts.isEmpty()) {
            return "Cluster"
        }

        // Sort by frequency, take top words
        val topWords = wordCounts.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key.replaceFirstChar { c -> c.uppercase() } }

        return topWords.joinToString(" ")
    }
}

package com.ennam.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * ONNX Runtime wrapper for all-MiniLM-L6-v2 sentence embeddings.
 * Produces 384-dim float32 embeddings for semantic search.
 * Model + vocab downloaded to app's files dir on first use (~90MB).
 */
class Embedder(private val context: Context) {

    private val modelDir: File get() = File(context.filesDir, "embeddings")
    private val modelFile: File get() = File(modelDir, "model.onnx")
    private val vocabFile: File get() = File(modelDir, "vocab.txt")

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var vocab: Map<String, Int>? = null

    val isModelReady: Boolean get() = ortSession != null

    /** Download model + vocab from Hugging Face */
    suspend fun downloadIfNeeded(
        onProgress: (Float) -> Unit = {},
        onComplete: (Boolean) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            modelDir.mkdirs()
            if (!modelFile.exists()) {
                downloadFile(
                    "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx",
                    modelFile, onProgress
                )
            }
            if (!vocabFile.exists()) {
                downloadFile(
                    "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/vocab.txt",
                    vocabFile, onProgress
                )
            }
            onComplete(true)
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(false)
        }
    }

    /** Load the ONNX model + vocab into memory */
    suspend fun load(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!modelFile.exists() || !vocabFile.exists()) return@withContext false

            val vocabMap = mutableMapOf<String, Int>()
            vocabFile.readLines().forEachIndexed { idx, token ->
                vocabMap[token.trim()] = idx
            }
            vocab = vocabMap

            val env = OrtEnvironment.getEnvironment()
            val session = env.createSession(
                modelFile.absolutePath,
                OrtSession.SessionOptions()
            )
            ortEnv = env
            ortSession = session
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun isDownloaded(): Boolean = modelFile.exists() && vocabFile.exists()

    /** Generate a 384-dim embedding for text. Returns null if model not loaded. */
    suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.IO) {
        val env = ortEnv ?: return@withContext null
        val session = ortSession ?: return@withContext null
        val vcb = vocab ?: return@withContext null

        try {
            val maxLen = 256
            val tokens = tokenize(text, vcb, maxLen)

            val inputIdsBuf = LongBuffer.allocate(maxLen)
            val attentionMaskBuf = LongBuffer.allocate(maxLen)
            val tokenTypeIdsBuf = LongBuffer.allocate(maxLen)

            for (i in 0 until maxLen) {
                inputIdsBuf.put(if (i < tokens.size) tokens[i].toLong() else 0L)
                attentionMaskBuf.put(if (i < tokens.size) 1L else 0L)
                tokenTypeIdsBuf.put(0L)
            }
            inputIdsBuf.rewind()
            attentionMaskBuf.rewind()
            tokenTypeIdsBuf.rewind()

            val shape = longArrayOf(1L, maxLen.toLong())

            val inputIdsTensor = OnnxTensor.createTensor(env, inputIdsBuf, shape)
            val attentionMaskTensor = OnnxTensor.createTensor(env, attentionMaskBuf, shape)
            val tokenTypeIdsTensor = OnnxTensor.createTensor(env, tokenTypeIdsBuf, shape)

            val inputs: Map<String, OnnxTensor> = mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to attentionMaskTensor,
                "token_type_ids" to tokenTypeIdsTensor
            )

            val results = session.run(inputs)

            val outputTensor = (results.get("last_hidden_state") ?: results.get(0)) as? OnnxTensor
                ?: return@withContext null

            val outputBuffer = outputTensor.floatBuffer
            outputBuffer.rewind()

            val tokenCount = tokens.size.coerceAtMost(maxLen)
            val dim = 384
            val embedding = FloatArray(dim)

            for (i in 0 until dim) {
                var sum = 0f
                for (j in 0 until tokenCount) {
                    sum += outputBuffer.get(j * dim + i)
                }
                embedding[i] = sum / tokenCount.coerceAtLeast(1)
            }

            val norm = sqrt(embedding.fold(0.0) { acc, v -> acc + v * v }).toFloat()
            if (norm > 1e-8f) {
                for (i in embedding.indices) embedding[i] /= norm
            }

            results.close()
            embedding
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Compute cosine similarity between two normalized embeddings */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot.coerceIn(-1f, 1f)
    }

    /** Convert FloatArray to ByteArray for DB storage */
    fun floatArrayToBytes(arr: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.nativeOrder())
        buffer.asFloatBuffer().put(arr)
        return buffer.array()
    }

    /** Convert ByteArray back to FloatArray */
    fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        val result = FloatArray(bytes.size / 4)
        buffer.asFloatBuffer().get(result)
        return result
    }

    /** Unload model to free memory */
    fun unload() {
        try { ortSession?.close() } catch (_: Exception) {}
        ortEnv = null
        ortSession = null
        vocab = null
    }

    // ── BERT Tokenizer ──

    companion object {
        private const val CLS_ID = 101
        private const val SEP_ID = 102
        private const val UNK_ID = 100

        fun tokenize(text: String, vocab: Map<String, Int>, maxLen: Int): List<Int> {
            val tokens = mutableListOf(CLS_ID)

            val cleaned = text.lowercase()
                .replace(Regex("""[^\w\s']"""), " ")
                .trim()
                .split(Regex("""\s+"""))
                .filter { it.isNotBlank() }

            for (word in cleaned) {
                if (tokens.size >= maxLen - 1) break
                tokens.addAll(wordpiece(word, vocab))
            }

            if (tokens.size > maxLen - 1) {
                tokens.clear()
                tokens.add(CLS_ID)
                val truncated = cleaned.flatMap { wordpiece(it, vocab) }
                tokens.addAll(truncated.take(maxLen - 2))
            }

            tokens.add(SEP_ID)
            return tokens
        }

        private fun wordpiece(word: String, vocab: Map<String, Int>): List<Int> {
            if (word.isEmpty()) return emptyList()
            vocab[word]?.let { return listOf(it) }

            val pieces = mutableListOf<Int>()
            var remaining = word

            while (remaining.isNotEmpty()) {
                var found = false
                for (end in remaining.length downTo 1) {
                    val sub = if (pieces.isEmpty()) remaining.substring(0, end)
                    else "##${remaining.substring(0, end)}"
                    val id = vocab[sub]
                    if (id != null) {
                        pieces.add(id)
                        remaining = remaining.substring(end)
                        found = true
                        break
                    }
                }
                if (!found) {
                    pieces.add(UNK_ID)
                    break
                }
            }
            return pieces
        }

        private suspend fun downloadFile(url: String, dest: File, onProgress: (Float) -> Unit) {
            val connection = java.net.URL(url).openConnection()
            connection.connect()
            val contentLen = connection.contentLength
            val input = connection.getInputStream()
            val output = dest.outputStream()
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead = 0L

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                if (contentLen > 0) {
                    onProgress(totalRead.toFloat() / contentLen)
                }
            }
            output.close()
            input.close()
        }
    }
}

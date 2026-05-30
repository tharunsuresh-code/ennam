package com.ennam.app.ml

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.util.concurrent.Executors

class LlamaEngine(context: Context) {

    companion object {
        private const val TAG = "LlamaEngine"
        private const val MODEL_FILENAME = "gemma-2-2b-it-Q4_K_M.gguf"
        private const val MODEL_URL =
            "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf"
        private const val MODEL_SIZE_MB = 1630L // ~1.7 GB

        init {
            System.loadLibrary("ennam_jni")
        }
    }

    private val modelDir: File = File(context.filesDir, "models")
    private val modelFile: File = File(modelDir, MODEL_FILENAME)
    private val executor = Executors.newSingleThreadExecutor()
    private var isLoaded = false

    /** Returns true if model file exists on disk */
    fun isModelDownloaded(): Boolean = modelFile.exists()

    /** Returns model file size or 0 */
    fun modelFileSize(): Long = if (modelFile.exists()) modelFile.length() else 0

    /** Downloads model from Hugging Face with progress callback [0f..1f] */
    fun downloadModel(progress: (Float) -> Unit, onComplete: (Boolean) -> Unit) {
        executor.execute {
            try {
                modelDir.mkdirs()
                val url = URL(MODEL_URL)
                val connection = url.openConnection()
                val totalBytes = connection.contentLengthLong
                val inputStream = connection.getInputStream()
                val outputStream = FileOutputStream(modelFile)
                val buf = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                while (inputStream.read(buf).also { bytesRead = it } != -1) {
                    outputStream.write(buf, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0) {
                        progress(totalRead.toFloat() / totalBytes.toFloat())
                    }
                }
                outputStream.close()
                inputStream.close()
                onComplete(true)
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                modelFile.delete()
                onComplete(false)
            }
        }
    }

    /** Load model into memory. Must be called on background thread. */
    fun loadModel(): Boolean {
        if (isLoaded) return true
        if (!modelFile.exists()) return false
        val result = nativeLoadModel(modelFile.absolutePath)
        isLoaded = result
        return result
    }

    /** Run inference. Must be called on background thread. */
    fun infer(prompt: String): String {
        if (!isLoaded) return """{"error": "Model not loaded"}"""
        return nativeInference(prompt)
    }

    /** Unload model from memory */
    fun unload() {
        if (isLoaded) {
            nativeUnloadModel()
            isLoaded = false
        }
    }

    // JNI native methods
    private external fun nativeLoadModel(path: String): Boolean
    private external fun nativeInference(prompt: String): String
    private external fun nativeUnloadModel()
}
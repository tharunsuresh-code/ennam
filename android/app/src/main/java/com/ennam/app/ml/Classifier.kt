package com.ennam.app.ml

import org.json.JSONObject

data class ClassificationResult(
    val category: String,
    val summary: String,
    val tags: List<String>,
    val actionable: Boolean,
    val priority: String
)

/**
 * Wraps SLM inference with the categorization prompt template.
 * Uses Qwen2.5 ChatML format.
 */
class Classifier(private val engine: LlamaEngine) {

    /**
     * Build the full ChatML prompt.
     * Qwen2.5 uses: <|im_start|>system...<|im_end|> <|im_start|>user...<|im_end|> <|im_start|>assistant
     */
    private fun buildPrompt(input: ClassifyInput): String {
        return buildString {
            append("<|im_start|>system\n")
            append("Categorize into: idea/todo/receipt/journal/bookmark/question/screenshot. ")
            append("Return JSON: {\"category\":\"\",\"summary\":\"1-2 line\",\"tags\":[],\"actionable\":false,\"priority\":\"low/med/high\"}\n")
            append("<|im_end|>\n<|im_start|>user\n")
            append("${input.rawText}\n")
            append("<|im_end|>\n<|im_start|>assistant\n")
        }
    }

    data class ClassifyInput(
        val rawText: String,
        val sourceType: String  // "text", "voice", "image"
    )

    /** Classify a single input. Runs on the calling thread (should be background). */
    fun classify(input: ClassifyInput): ClassificationResult {
        val prompt = buildPrompt(input)
        val rawOutput = engine.infer(prompt)
        return parseOutput(rawOutput)
    }

    private fun parseOutput(raw: String): ClassificationResult {
        return try {
            // Find JSON in output (handle leading/trailing text)
            val jsonStart = raw.indexOf('{')
            val jsonEnd = raw.lastIndexOf('}') + 1
            val jsonStr = if (jsonStart >= 0 && jsonEnd > jsonStart) {
                raw.substring(jsonStart, jsonEnd)
            } else {
                raw
            }
            val obj = JSONObject(jsonStr)
            ClassificationResult(
                category = obj.optString("category", "idea"),
                summary = obj.optString("summary", raw.take(100)),
                tags = obj.optJSONArray("tags")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it, "") }
                } ?: emptyList(),
                actionable = obj.optBoolean("actionable", false),
                priority = obj.optString("priority", "medium")
            )
        } catch (e: Exception) {
            // Fallback: if parsing fails, treat as generic idea
            ClassificationResult(
                category = "idea",
                summary = raw.take(100).replace("\n", " "),
                tags = emptyList(),
                actionable = false,
                priority = "medium"
            )
        }
    }
}
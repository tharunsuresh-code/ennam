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
 * Runs the Gemma 2B model to classify and summarize free-form input.
 */
class Classifier(private val engine: LlamaEngine) {

    private val systemPrompt = """
You are a categorization engine. Given user input, identify the type and condense to 1-2 lines.

Rules:
- category must be exactly one of: idea, todo, receipt, journal, bookmark, question, screenshot
- summary must be 1-2 line condensation
- tags must be 2-5 relevant keywords
- actionable must be true if the input describes something to do
- priority must be low, medium, or high

Respond ONLY with valid JSON. No other text.
""".trimIndent()

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

    private fun buildPrompt(input: ClassifyInput): String {
        return """
$systemPrompt

Input (${input.sourceType}): ${input.rawText}
Output:
""".trimIndent()
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
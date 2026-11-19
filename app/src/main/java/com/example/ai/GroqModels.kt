package com.example.ai

/**
 * Qalam AI (v2.6) — Groq model catalog.
 * Kept in its own tiny file so new models are a one-line addition.
 * Model ids verified against console.groq.com/docs/models.
 */
data class GroqModel(
    val id: String,
    val displayName: String,
    val supportsVision: Boolean,
    val note: String
)

object GroqModels {

    /** Vision-capable model used for Image → Word document digitizing. */
    const val VISION_MODEL = "qwen/qwen3.8-27b"

    /** Default text model — fast, generous free-tier rate limits (250K TPM / 1K RPM). */
    const val DEFAULT_MODEL = "openai/gpt-oss-20b"

    /** Sentinel id for the "type any model id" option. */
    const val CUSTOM = "custom"

    val ALL: List<GroqModel> = listOf(
        GroqModel(
            id = "openai/gpt-oss-20b",
            displayName = "GPT-OSS 20B (fast)",
            supportsVision = false,
            note = "Default • very fast • free tier friendly"
        ),
        GroqModel(
            id = "openai/gpt-oss-120b",
            displayName = "GPT-OSS 120B (flagship)",
            supportsVision = false,
            note = "Best quality • 500 t/s • free tier"
        ),
        GroqModel(
            id = VISION_MODEL,
            displayName = "Qwen3.8 27B (vision)",
            supportsVision = true,
            note = "Reads images • used for Image to Word • up to 3 photos"
        ),
        GroqModel(
            id = "llama-3.3-70b-versatile",
            displayName = "Llama 3.3 70B",
            supportsVision = false,
            note = "Reliable all-rounder • 131K context"
        ),
        GroqModel(
            id = "llama-3.1-8b-instant",
            displayName = "Llama 3.1 8B Instant",
            supportsVision = false,
            note = "Lowest latency • 560 t/s"
        ),
        GroqModel(
            id = CUSTOM,
            displayName = "Custom model id...",
            supportsVision = false,
            note = "Paste any id from console.groq.com/docs/models"
        )
    )

    fun findById(id: String): GroqModel = ALL.firstOrNull { it.id == id } ?: ALL.first()
}

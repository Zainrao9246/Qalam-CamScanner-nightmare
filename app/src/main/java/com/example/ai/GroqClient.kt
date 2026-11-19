package com.example.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Qalam AI (v2.6) — minimal Groq REST client.
 * Uses only HttpsURLConnection + org.json, so zero new Gradle dependencies.
 * OpenAI-compatible endpoint: https://api.groq.com/openai/v1/chat/completions
 */
data class GroqMessage(
    val role: String,
    val text: String,
    /** Raw JPEG base64 (no data: prefix). When set, the message is sent as multimodal content. */
    val imageBase64: String? = null
)

data class GroqResult(
    val ok: Boolean,
    val content: String = "",
    val error: String = "",
    val tokensUsed: Int = 0
)

class GroqClient(private val apiKey: String) {

    suspend fun chat(
        model: String,
        messages: List<GroqMessage>,
        maxTokens: Int = 4096
    ): GroqResult = withContext(Dispatchers.IO) {
        runCatching { post(model, messages, maxTokens) }
            .getOrElse { GroqResult(false, error = it.message ?: "Network error — check connection") }
    }

    private fun post(model: String, messages: List<GroqMessage>, maxTokens: Int): GroqResult {
        val payload = JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("temperature", 0.3)
            .put("messages", JSONArray().apply {
                messages.forEach { message ->
                    put(
                        if (message.imageBase64 != null) {
                            JSONObject()
                                .put("role", message.role)
                                .put(
                                    "content",
                                    JSONArray()
                                        .put(JSONObject().put("type", "text").put("text", message.text))
                                        .put(
                                            JSONObject()
                                                .put("type", "image_url")
                                                .put(
                                                    "image_url",
                                                    JSONObject().put("url", "data:image/jpeg;base64," + message.imageBase64)
                                                )
                                        )
                                )
                        } else {
                            JSONObject()
                                .put("role", message.role)
                                .put("content", message.text)
                        }
                    )
                }
            })

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }

        try {
            connection.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""

            if (status !in 200..299) {
                val message = runCatching {
                    JSONObject(body).getJSONObject("error").getString("message")
                }.getOrNull() ?: "HTTP $status — request failed"
                val friendly = when (status) {
                    401 -> "Invalid API key ($message)"
                    429 -> "Rate limit reached — free tier wait karke try again ($message)"
                    else -> message
                }
                return GroqResult(false, error = friendly)
            }

            val json = JSONObject(body)
            val content = json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            val tokens = json.optJSONObject("usage")?.optInt("total_tokens") ?: 0
            return GroqResult(true, content = content, tokensUsed = tokens)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    }
}

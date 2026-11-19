package com.example.data

import android.content.Context
import com.example.ai.GroqModels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Qalam AI (v2.6) — stores the user's Groq API key and chosen model.
 * Plain SharedPreferences so reads are synchronous and cheap; the key never
 * leaves the device except inside requests to api.groq.com itself.
 */
class AiPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _apiKey = MutableStateFlow(prefs.getString(KEY_API_KEY, "").orEmpty())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _modelId = MutableStateFlow(
        prefs.getString(KEY_MODEL_ID, null) ?: GroqModels.DEFAULT_MODEL
    )
    val modelId: StateFlow<String> = _modelId.asStateFlow()

    fun setApiKey(key: String) {
        val clean = key.trim()
        prefs.edit().putString(KEY_API_KEY, clean).apply()
        _apiKey.value = clean
    }

    fun setModel(modelId: String) {
        val clean = modelId.trim().ifBlank { GroqModels.DEFAULT_MODEL }
        prefs.edit().putString(KEY_MODEL_ID, clean).apply()
        _modelId.value = clean
    }

    private companion object {
        const val FILE_NAME = "qalam_ai_prefs"
        const val KEY_API_KEY = "groq_api_key"
        const val KEY_MODEL_ID = "groq_model_id"
    }
}

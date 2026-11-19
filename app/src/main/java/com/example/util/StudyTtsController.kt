package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Offline Android Text-to-Speech controller for hands-free listening to
 * Word/EPUB/HTML notes, Slide decks, and OCR-extracted text.
 */
class StudyTtsController(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private fun ensureInit() {
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(_speechRate.value)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _isSpeaking.value = false
                    }
                })
                pendingText?.let { queued ->
                    pendingText = null
                    speakInternal(queued)
                }
            }
        }
    }

    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        ensureInit()
        if (!isInitialized) {
            pendingText = clean
            _isSpeaking.value = true
            return
        }
        speakInternal(clean)
    }

    private fun speakInternal(text: String) {
        val engine = tts ?: return
        engine.stop()
        engine.setSpeechRate(_speechRate.value)
        // Split very long documents into safe <= 3500-char chunks so Android TTS never drops text
        val chunks = chunkText(text, 3500)
        chunks.forEachIndexed { index, chunk ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = if (index == chunks.lastIndex) "camscan_tts_last" else "camscan_tts_$index"
            engine.speak(chunk, mode, null, utteranceId)
        }
        _isSpeaking.value = true
    }

    fun setRate(rate: Float) {
        val clamped = rate.coerceIn(0.6f, 2.0f)
        _speechRate.value = clamped
        tts?.setSpeechRate(clamped)
    }

    fun stop() {
        pendingText = null
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        pendingText = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _isSpeaking.value = false
    }

    private fun chunkText(text: String, maxLen: Int): List<String> {
        if (text.length <= maxLen) return listOf(text)
        val result = mutableListOf<String>()
        var cursor = 0
        while (cursor < text.length) {
            var end = (cursor + maxLen).coerceAtMost(text.length)
            if (end < text.length) {
                val breakAt = text.lastIndexOf('\n', end).takeIf { it > cursor + 200 }
                    ?: text.lastIndexOf(". ", end).takeIf { it > cursor + 200 }
                    ?: text.lastIndexOf(' ', end).takeIf { it > cursor + 200 }
                if (breakAt != null) end = breakAt + 1
            }
            result.add(text.substring(cursor, end))
            cursor = end
        }
        return result
    }
}

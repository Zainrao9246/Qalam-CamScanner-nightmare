package com.example.util

import android.graphics.Bitmap
import com.example.model.WordBlock
import com.example.model.WordBlockType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OcrEngine {

    data class OcrResult(
        val fullText: String,
        val wordCount: Int,
        val lineCount: Int,
        val detectedKeyValues: Map<String, String> = emptyMap()
    )

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Runs real on-device ML Kit OCR on [bitmap].
     */
    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): Text {
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (cont.isActive) cont.resume(visionText)
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
        }
    }

    suspend fun extractText(bitmap: Bitmap): OcrResult {
        val visionText = recognizeTextFromBitmap(bitmap)
        val full = visionText.text.trim()
        val lines = if (full.isEmpty()) emptyList() else full.lines()
        val words = if (full.isEmpty()) emptyList() else full.split(Regex("\\s+")).filter { it.isNotBlank() }

        return OcrResult(
            fullText = full,
            wordCount = words.size,
            lineCount = lines.size,
            detectedKeyValues = extractKeyValues(full)
        )
    }

    /**
     * Converts ML Kit visual text blocks into structured [WordBlock] paragraphs/headings/bullets
     * using bounding-box height heuristics and bullet markers.
     */
    suspend fun extractStructuredBlocks(bitmap: Bitmap): List<WordBlock> {
        val visionText = recognizeTextFromBitmap(bitmap)
        if (visionText.textBlocks.isEmpty()) return emptyList()

        // Compute median line height to detect headings
        val lineHeights = visionText.textBlocks
            .flatMap { it.lines }
            .mapNotNull { it.boundingBox?.height() }
            .sorted()

        val medianHeight = if (lineHeights.isNotEmpty()) {
            lineHeights[lineHeights.size / 2].toFloat()
        } else {
            24f
        }

        val blocks = mutableListOf<WordBlock>()
        for (textBlock in visionText.textBlocks) {
            val rawLines = textBlock.lines.map { it.text.trim() }.filter { it.isNotEmpty() }
            if (rawLines.isEmpty()) continue

            val avgBlockLineHeight = textBlock.lines
                .mapNotNull { it.boundingBox?.height()?.toFloat() }
                .average()
                .toFloat()

            val combined = rawLines.joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .trim()

            if (combined.isEmpty()) continue

            val firstLine = rawLines.first()
            val isBullet = firstLine.startsWith("•") ||
                    firstLine.startsWith("-") ||
                    firstLine.startsWith("*") ||
                    firstLine.matches(Regex("^[0-9]+[.)]\\s+.*"))

            val blockType = when {
                avgBlockLineHeight > medianHeight * 1.45f && combined.length <= 90 -> WordBlockType.HEADING_1
                avgBlockLineHeight > medianHeight * 1.2f && combined.length <= 100 -> WordBlockType.HEADING_2
                isBullet -> WordBlockType.BULLET
                else -> WordBlockType.PARAGRAPH
            }

            val cleanText = if (blockType == WordBlockType.BULLET) {
                combined.replaceFirst(Regex("^[•\\-*]\\s*"), "").trim()
            } else {
                combined
            }

            blocks.add(
                WordBlock(
                    type = blockType,
                    text = cleanText,
                    isBold = blockType == WordBlockType.HEADING_1 || blockType == WordBlockType.HEADING_2
                )
            )
        }
        return blocks
    }

    private fun extractKeyValues(text: String): Map<String, String> {
        if (text.isBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()

        val emailRegex = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        emailRegex.find(text)?.let { map["Email"] = it.value }

        val dateRegex = Regex("\\b(?:\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2},?\\s+\\d{4})\\b", RegexOption.IGNORE_CASE)
        dateRegex.find(text)?.let { map["Date"] = it.value }

        val totalRegex = Regex("(?:Total|Amount|Fee|Price|Marks)[:\\s]+([\\$\\€\\£]?\\s?[0-9,]+(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)
        totalRegex.find(text)?.let { map["Amount / Value"] = it.groupValues[1].trim() }

        val courseRegex = Regex("\\b([A-Z]{2,4}[-\\s]?\\d{3,4})\\b")
        courseRegex.find(text)?.let { map["Course / Code"] = it.groupValues[1].trim() }

        return map
    }
}

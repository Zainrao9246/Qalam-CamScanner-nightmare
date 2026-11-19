package com.example.ai

import com.example.model.WordBlock
import com.example.model.WordBlockType

/**
 * Qalam AI (v2.6) — task prompts + output parsers.
 * Image → Word uses a strict marker protocol ([H1]/[H2]/[P]/[B]) so the
 * response maps 1:1 onto WordBlock and lands in the real .docx writer.
 */
object AiTasks {

    // ---------- Prompts ----------

    const val IMAGE_TO_WORD =
        "You are a document digitizer. Read the image and reconstruct it as a clean document. " +
        "Output ONLY lines using these markers, nothing else: " +
        "[H1] for the main title, [H2] for section headings, [B] for bullet points, " +
        "[P] for normal paragraphs. " +
        "Rules: keep the original language (Urdu/English/mixed), preserve reading order, " +
        "clean up scan noise, never invent content, no markdown, no commentary."

    const val GRAMMAR =
        "You are an editor. Fix grammar, spelling and punctuation of the user's text. " +
        "Keep the language and meaning exactly. Reply ONLY with the corrected text, no explanations."

    const val BUGS =
        "You are a senior code reviewer. Review the user's code. " +
        "First list bugs and problems as short numbered lines. " +
        "Then give the FULL corrected code in one triple-backtick block. " +
        "Be specific and practical; do not change the program's purpose."

    const val EXPLAIN =
        "You are a patient teacher. Explain the user's content clearly for a student. " +
        "Use short paragraphs and '- ' bullet points. No markdown headers, no code fences unless code."

    const val SUMMARIZE =
        "Summarize the user's content. Reply ONLY lines in this format: " +
        "one line '[H2] Summary' then '[B] key point' lines (5-9 bullets). No other markdown."

    const val EXCEL_FORMULA =
        "You are an Excel formula expert. The user describes a calculation. " +
        "Reply with ONLY the formula (starting with =) on the first line, " +
        "then 2-3 short explanation lines. " +
        "Use standard Excel functions (SUM, AVERAGE, IF, COUNT, COUNTA, MIN, MAX, SUMIF, " +
        "VLOOKUP, INDEX, MATCH, ROUND, TODAY). Cell refs are normal A1 style."

    // ---------- Parsers ----------

    /**
     * Parses [H1]/[H2]/[P]/[B] marker lines (or plain fallback lines) into WordBlocks.
     * Strips stray markdown so nothing leaks into the .docx.
     */
    fun parseAiDocument(response: String): List<WordBlock> {
        val blocks = mutableListOf<WordBlock>()
        response.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val pair = when {
                line.startsWith("[H1]") -> WordBlockType.HEADING_1 to line.substringAfter("[H1]").trim()
                line.startsWith("[H2]") -> WordBlockType.HEADING_2 to line.substringAfter("[H2]").trim()
                line.startsWith("[B]") -> WordBlockType.BULLET to line.substringAfter("[B]").trim()
                line.startsWith("[P]") -> WordBlockType.PARAGRAPH to line.substringAfter("[P]").trim()
                else -> WordBlockType.PARAGRAPH to line
            }
            val text = stripMarkdown(pair.second)
            if (text.isNotBlank()) {
                blocks.add(WordBlock(type = pair.first, text = text))
            }
        }
        return blocks
    }

    /** Extracts the first ``` code fence, falling back to the whole response. */
    fun extractCode(response: String): String {
        val fenced = Regex("```[a-zA-Z0-9+#_-]*\\s*\\n([\\s\\S]*?)```").find(response)
        val code = fenced?.groupValues?.get(1)?.trim().orEmpty()
        return code.ifBlank { response.trim() }
    }

    /** Removes common markdown decoration from a single text line. */
    fun stripMarkdown(line: String): String {
        var text = line
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("^#{1,6}\\s+"), "")
            .replace(Regex("^[*•\\u2013\\u2014-]\\s+"), "")
            .trim()
        if (text.startsWith("\"") && text.endsWith("\"") && text.length > 1) {
            text = text.substring(1, text.length - 1)
        }
        return text
    }
}

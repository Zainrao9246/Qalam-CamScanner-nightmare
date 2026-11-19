package com.example.util

import android.content.Context
import android.net.Uri
import com.example.model.WordBlock
import com.example.model.WordBlockType
import java.io.File
import java.io.FileOutputStream

/**
 * Handles source code, structured data, and log files (.cpp, .java, .kt, .py, .json, .xml, .sql, .log, etc.).
 */
object CodeReaderEngine {

    val SUPPORTED_EXTENSIONS = setOf(
        "cpp", "cc", "cxx", "c", "h", "hpp",
        "java", "kt", "kts", "py", "js", "ts", "tsx", "jsx",
        "json", "xml", "sql", "css", "yaml", "yml", "ini", "cfg", "conf", "log", "sh", "bat", "gradle",
        "html", "htm", "md", "markdown", "txt", "text", "ini", "toml", "env", "properties"
    )

    fun isCodeOrLogExtension(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in SUPPORTED_EXTENSIONS
    }

    fun importCodeFileFromUri(context: Context, uri: Uri, filenameHint: String): File? {
        val dir = File(context.filesDir, "code_files")
        if (!dir.exists()) dir.mkdirs()
        val safeName = filenameHint.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "snippet.txt" }
        val dest = File(dir, "${System.currentTimeMillis()}_$safeName")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            dest
        }.getOrNull()
    }

    fun readLines(file: File): List<String> {
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readText(Charsets.UTF_8).lines()
        }.getOrDefault(emptyList())
    }

    fun toWordBlocks(title: String, lines: List<String>): List<WordBlock> {
        val blocks = mutableListOf<WordBlock>()
        blocks.add(WordBlock(type = WordBlockType.HEADING_1, text = title, isBold = true))
        lines.forEach { line ->
            if (line.isNotBlank()) {
                blocks.add(WordBlock(type = WordBlockType.PARAGRAPH, text = line))
            }
        }
        return blocks
    }
}

package com.example.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Xml
import com.example.model.WordAlign
import com.example.model.WordBlock
import com.example.model.WordBlockType
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Real OOXML (.docx) reader, writer, and smart document cleaner.
 * Zero external heavyweight dependencies; writes valid Word documents
 * compatible with Microsoft Word, Google Docs, WPS, and LibreOffice.
 */
object DocxEngine {

    /**
     * Parses a .docx, .doc, .rtf, .txt, or .md file from a Uri into editable/readable [WordBlock]s.
     */
    fun readDocumentFromUri(context: Context, uri: Uri, displayName: String): List<WordBlock> {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            return readDocumentBytes(bytes, displayName)
        }
        return emptyList()
    }

    fun readDocxFile(file: File): List<WordBlock> {
        if (!file.exists()) return emptyList()
        val bytes = file.readBytes()
        // Media extracted next to the docx so pictures survive the round trip.
        val mediaDir = File(file.parentFile, file.nameWithoutExtension + "_media")
        return readDocumentBytes(bytes, file.name, mediaDir)
    }

    private fun readDocumentBytes(bytes: ByteArray, fileName: String, mediaDir: File? = null): List<WordBlock> {
        if (bytes.isEmpty()) return emptyList()
        val isZip = bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (isZip) {
            if (fileName.endsWith(".odt", ignoreCase = true)) {
                val odtBlocks = runCatching { readOdtStream(bytes.inputStream()) }.getOrDefault(emptyList())
                if (odtBlocks.isNotEmpty()) return odtBlocks
            }
            if (fileName.endsWith(".epub", ignoreCase = true)) {
                val epubBlocks = runCatching { readEpubStream(bytes.inputStream()) }.getOrDefault(emptyList())
                if (epubBlocks.isNotEmpty()) return epubBlocks
            }
            val docxBlocks = runCatching { readDocxStream(bytes.inputStream(), mediaDir) }.getOrDefault(emptyList())
            if (docxBlocks.isNotEmpty()) return docxBlocks
            // Fallback: try ODT or EPUB in case extension wasn't preserved
            val odtFallback = runCatching { readOdtStream(bytes.inputStream()) }.getOrDefault(emptyList())
            if (odtFallback.isNotEmpty()) return odtFallback
            val epubFallback = runCatching { readEpubStream(bytes.inputStream()) }.getOrDefault(emptyList())
            if (epubFallback.isNotEmpty()) return epubFallback
        }
        val head = bytes.copyOfRange(0, minOf(bytes.size, 128)).toString(Charsets.ISO_8859_1)
        if (head.trimStart().startsWith("{\\rtf")) {
            return readRtfText(bytes.toString(Charsets.UTF_8))
        }
        if (fileName.endsWith(".html", ignoreCase = true) ||
            fileName.endsWith(".htm", ignoreCase = true) ||
            head.contains("<html", ignoreCase = true)
        ) {
            return readHtmlText(bytes.toString(Charsets.UTF_8))
        }
        if (fileName.endsWith(".doc", ignoreCase = true) && !isZip) {
            return extractLegacyDocText(bytes)
        }
        return readPlainTextStream(bytes.inputStream())
    }

    private fun readOdtStream(stream: InputStream): List<WordBlock> {
        var contentXml: ByteArray? = null
        ZipInputStream(stream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (entry.name == "content.xml") {
                    contentXml = zis.readBytes()
                    break
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        val bytes = contentXml ?: return emptyList()
        val blocks = mutableListOf<WordBlock>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(bytes.inputStream(), "UTF-8")

        var currentTag = ""
        val sb = StringBuilder()
        var inTextBlock = false
        var isList = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val name = parser.name ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "text:list" -> isList = true
                        "text:h", "text:p" -> {
                            inTextBlock = true
                            currentTag = name
                            sb.setLength(0)
                        }
                        "text:s" -> if (inTextBlock) sb.append(" ")
                        "text:tab" -> if (inTextBlock) sb.append("    ")
                    }
                }
                XmlPullParser.TEXT -> if (inTextBlock) sb.append(parser.text ?: "")
                XmlPullParser.END_TAG -> {
                    when (name) {
                        "text:list" -> isList = false
                        "text:h", "text:p" -> {
                            val txt = sb.toString().trim()
                            if (txt.isNotEmpty()) {
                                val blockType = when {
                                    currentTag == "text:h" -> WordBlockType.HEADING_1
                                    isList -> WordBlockType.BULLET
                                    else -> WordBlockType.PARAGRAPH
                                }
                                blocks.add(
                                    WordBlock(
                                        type = blockType,
                                        text = txt,
                                        isBold = blockType == WordBlockType.HEADING_1
                                    )
                                )
                            }
                            inTextBlock = false
                        }
                    }
                }
            }
            event = parser.next()
        }
        return blocks
    }

    private fun readEpubStream(stream: InputStream): List<WordBlock> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(stream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory &&
                    (entry.name.endsWith(".xhtml", ignoreCase = true) ||
                        entry.name.endsWith(".html", ignoreCase = true) ||
                        entry.name.endsWith(".htm", ignoreCase = true))
                ) {
                    entries[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        if (entries.isEmpty()) return emptyList()
        val blocks = mutableListOf<WordBlock>()
        entries.keys.sorted().forEach { path ->
            val html = entries[path]?.toString(Charsets.UTF_8) ?: return@forEach
            blocks.addAll(readHtmlText(html))
        }
        return blocks
    }

    private fun readHtmlText(rawHtml: String): List<WordBlock> {
        val withoutScripts = rawHtml
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
        val normalized = withoutScripts
            .replace(Regex("<h1[^>]*>", RegexOption.IGNORE_CASE), "\n# ")
            .replace(Regex("<h[2-4][^>]*>", RegexOption.IGNORE_CASE), "\n## ")
            .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "\n• ")
            .replace(Regex("<(p|div|br|tr|h[1-6]|li)[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|h[1-6]|li|tr)>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        return readPlainTextStream(normalized.byteInputStream())
    }

    private fun readRtfText(raw: String): List<WordBlock> {
        val stripped = raw
            .replace(Regex("\\\\par\\b"), "\n")
            .replace(Regex("\\\\line\\b"), "\n")
            .replace(Regex("\\\\tab\\b"), "    ")
            .replace(Regex("\\{\\\\[*a-zA-Z0-9\\s;]+\\}"), "")
            .replace(Regex("\\\\[a-zA-Z]+-?\\d*\\s?"), "")
            .replace("{", "")
            .replace("}", "")
        return readPlainTextStream(stripped.byteInputStream())
    }

    private fun extractLegacyDocText(bytes: ByteArray): List<WordBlock> {
        val sb = StringBuilder()
        var currentRun = StringBuilder()
        for (b in bytes) {
            val code = b.toInt() and 0xFF
            if (code in 32..126 || code == 10 || code == 13 || code == 9) {
                currentRun.append(code.toChar())
            } else {
                if (currentRun.length >= 6) {
                    sb.append(currentRun).append("\n")
                }
                currentRun.setLength(0)
            }
        }
        if (currentRun.length >= 6) sb.append(currentRun)
        val lines = sb.toString().lines()
            .map { it.trim() }
            .filter { line -> line.length >= 3 && line.count { it.isLetterOrDigit() || it == ' ' } > line.length * 0.65 }
            .distinct()
        return lines.map { WordBlock(type = WordBlockType.PARAGRAPH, text = it) }
    }

    private fun readPlainTextStream(stream: InputStream): List<WordBlock> {
        val text = stream.bufferedReader().readText()
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                when {
                    line.startsWith("# ") -> WordBlock(type = WordBlockType.HEADING_1, text = line.removePrefix("# ").trim(), isBold = true)
                    line.startsWith("## ") -> WordBlock(type = WordBlockType.HEADING_2, text = line.removePrefix("## ").trim(), isBold = true)
                    line.startsWith("- ") || line.startsWith("• ") || line.startsWith("* ") ->
                        WordBlock(type = WordBlockType.BULLET, text = line.drop(2).trim())
                    else -> WordBlock(type = WordBlockType.PARAGRAPH, text = line)
                }
            }
    }

    private fun readDocxStream(stream: InputStream, mediaDir: File? = null): List<WordBlock> {
        var documentXmlBytes: ByteArray? = null
        var relsXml: String? = null
        val mediaEntries = linkedMapOf<String, ByteArray>()
        ZipInputStream(stream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                when {
                    entry.name == "word/document.xml" -> documentXmlBytes = zis.readBytes()
                    entry.name == "word/_rels/document.xml.rels" -> relsXml = zis.readBytes().toString(Charsets.UTF_8)
                    entry.name.startsWith("word/media/") -> mediaEntries[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val xmlBytes = documentXmlBytes ?: return emptyList()

        // Resolve relationship ids -> extracted image files (only when a media dir is provided).
        var imageResolver: ((String) -> String?)? = null
        if (mediaDir != null && relsXml != null && mediaEntries.isNotEmpty()) {
            val relMap = Regex("<Relationship\\b[^>]*>").findAll(relsXml!!).mapNotNull { tag ->
                val id = Regex("Id=\"([^\"]+)\"").find(tag.value)?.groupValues?.get(1)
                val target = Regex("Target=\"([^\"]+)\"").find(tag.value)?.groupValues?.get(1)
                if (id != null && target != null) id to target.removePrefix("/").removePrefix("word/") else null
            }.toMap()
            if (mediaDir.exists()) mediaDir.listFiles()?.forEach { it.delete() }
            mediaDir.mkdirs()
            val extracted = mutableMapOf<String, String>()
            mediaEntries.forEach { (entryName, bytes) ->
                val entryKey = entryName.removePrefix("word/")
                val outFile = File(mediaDir, "${System.currentTimeMillis()}_${entryName.substringAfterLast('/')}")
                runCatching { outFile.writeBytes(bytes) }
                relMap.filter { it.value == entryKey }.keys.forEach { relId ->
                    extracted[relId] = outFile.absolutePath
                }
                extracted[entryKey] = outFile.absolutePath
            }
            imageResolver = { rid -> extracted[rid] }
        }

        return parseDocumentXml(xmlBytes.inputStream(), imageResolver)
    }

    private fun parseDocumentXml(
        xmlStream: InputStream,
        imageResolver: ((String) -> String?)? = null
    ): List<WordBlock> {
        val blocks = mutableListOf<WordBlock>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(xmlStream, "UTF-8")

        var inParagraph = false
        var inParagraphProps = false
        var inRunProps = false
        var inText = false

        var currentText = StringBuilder()
        var currentStyle = ""
        var isBullet = false
        var hasBold = false
        var hasItalic = false
        var hasUnderline = false
        var hasStrike = false
        var paraAlign = WordAlign.LEFT
        var runSizeSp: Int? = null
        var runColorHex: String? = null

        // Table & picture state (v2.7)
        var inTable = false
        var tableRows = mutableListOf<MutableList<String>>()
        var currentRow: MutableList<String>? = null
        var currentCell = StringBuilder()
        var inCell = false
        var pendingImagePath: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tagName) {
                        "w:p" -> {
                            inParagraph = true
                            currentText = StringBuilder()
                            currentStyle = ""
                            isBullet = false
                            hasBold = false
                            hasItalic = false
                            hasUnderline = false
                            hasStrike = false
                            paraAlign = WordAlign.LEFT
                            runSizeSp = null
                            runColorHex = null
                        }
                        "w:pPr" -> if (inParagraph) inParagraphProps = true
                        "w:rPr" -> if (inParagraph) inRunProps = true
                        "w:pStyle" -> if (inParagraphProps) {
                            currentStyle = parser.getAttributeValue(null, "w:val") ?: ""
                        }
                        "w:numPr" -> if (inParagraphProps) {
                            isBullet = true
                        }
                        "w:b" -> if (inRunProps) {
                            val valAttr = parser.getAttributeValue(null, "w:val")
                            if (valAttr != "0" && valAttr != "false") hasBold = true
                        }
                        "w:i" -> if (inRunProps) {
                            val valAttr = parser.getAttributeValue(null, "w:val")
                            if (valAttr != "0" && valAttr != "false") hasItalic = true
                        }
                        "w:u" -> if (inRunProps) {
                            val valAttr = parser.getAttributeValue(null, "w:val")
                            if (valAttr != "none") hasUnderline = true
                        }
                        "w:strike" -> if (inRunProps) {
                            val valAttr = parser.getAttributeValue(null, "w:val")
                            if (valAttr != "0" && valAttr != "false") hasStrike = true
                        }
                        "w:color" -> if (inRunProps) {
                            val valAttr = parser.getAttributeValue(null, "w:val")
                            if (!valAttr.isNullOrBlank() && valAttr != "auto" && valAttr.length >= 6) {
                                runColorHex = valAttr.takeLast(6).uppercase()
                            }
                        }
                        "w:sz" -> if (inRunProps) {
                            val valAttr = parser.getAttributeValue(null, "w:val")?.toIntOrNull()
                            // OOXML stores half-points; keep only body-text sane sizes
                            if (valAttr != null && valAttr in 16..96) runSizeSp = valAttr / 2
                        }
                        "w:jc" -> if (inParagraphProps) {
                            when (parser.getAttributeValue(null, "w:val")) {
                                "center" -> paraAlign = WordAlign.CENTER
                                "right" -> paraAlign = WordAlign.RIGHT
                                "both" -> paraAlign = WordAlign.JUSTIFY
                            }
                        }
                        "w:t" -> if (inParagraph) inText = true
                        "w:tab" -> if (inParagraph) currentText.append("    ")
                        "w:br" -> if (inParagraph) currentText.append("\n")
                        "w:tbl" -> {
                            inTable = true
                            tableRows = mutableListOf()
                            currentRow = null
                        }
                        "w:tr" -> if (inTable) {
                            currentRow = mutableListOf()
                            tableRows.add(currentRow!!)
                        }
                        "w:tc" -> if (inTable) {
                            inCell = true
                            currentCell = StringBuilder()
                        }
                        "a:blip" -> if (inParagraph && pendingImagePath == null) {
                            val rid = parser.getAttributeValue(null, "r:embed")
                                ?: parser.getAttributeValue(null, "embed")
                            if (rid != null) {
                                pendingImagePath = imageResolver?.invoke(rid)
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inText) {
                        currentText.append(parser.text ?: "")
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tagName) {
                        "w:t" -> inText = false
                        "w:rPr" -> inRunProps = false
                        "w:pPr" -> inParagraphProps = false
                        "w:tc" -> if (inTable) {
                            inCell = false
                            currentRow?.add(currentCell.toString().trim())
                            currentCell = StringBuilder()
                        }
                        "w:tr" -> if (inTable) currentRow = null
                        "w:tbl" -> {
                            inTable = false
                            val finishedRows = tableRows.filter { it.isNotEmpty() }
                            if (finishedRows.isNotEmpty()) {
                                blocks.add(
                                    WordBlock(
                                        type = WordBlockType.TABLE,
                                        text = "",
                                        tableRows = finishedRows.map { it.toList() }
                                    )
                                )
                            }
                            currentRow = null
                        }
                        "w:p" -> {
                            inParagraph = false
                            val text = currentText.toString().trim()
                            when {
                                pendingImagePath != null -> {
                                    blocks.add(
                                        WordBlock(
                                            type = WordBlockType.PICTURE,
                                            text = text,
                                            imagePath = pendingImagePath
                                        )
                                    )
                                    pendingImagePath = null
                                }
                                inTable && inCell -> {
                                    if (text.isNotEmpty()) {
                                        if (currentCell.isNotEmpty()) currentCell.append("\n")
                                        currentCell.append(text)
                                    }
                                }
                                inTable -> {
                                    // paragraph directly in table but not in a cell — keep text as body
                                    if (text.isNotEmpty()) {
                                        blocks.add(WordBlock(type = WordBlockType.PARAGRAPH, text = text))
                                    }
                                }
                                text.isNotEmpty() -> {
                                val type = when {
                                    currentStyle.contains("Heading1", ignoreCase = true) ||
                                            currentStyle.equals("Title", ignoreCase = true) -> WordBlockType.HEADING_1
                                    currentStyle.contains("Heading2", ignoreCase = true) ||
                                            currentStyle.contains("Heading3", ignoreCase = true) ||
                                            currentStyle.equals("Subtitle", ignoreCase = true) -> WordBlockType.HEADING_2
                                    isBullet || currentStyle.contains("List", ignoreCase = true) -> WordBlockType.BULLET
                                    else -> WordBlockType.PARAGRAPH
                                }
                                blocks.add(
                                    WordBlock(
                                        type = type,
                                        text = text,
                                        isBold = hasBold || type == WordBlockType.HEADING_1 || type == WordBlockType.HEADING_2,
                                        isItalic = hasItalic,
                                        isUnderline = hasUnderline,
                                        isStrikethrough = hasStrike,
                                        fontSizeSp = runSizeSp,
                                        colorHex = runColorHex,
                                        alignment = paraAlign
                                    )
                                )
                                }
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return blocks
    }

    /**
     * Writes a real, standards-compliant OOXML .docx archive to [destFile].
     */
    fun writeDocxFile(blocks: List<WordBlock>, destFile: File): File {
        destFile.parentFile?.mkdirs()

        // Collect existing pictures in block order; each gets rId10, rId11, ...
        val pictures = blocks.mapIndexedNotNull { index, block ->
            if (block.type == WordBlockType.PICTURE && block.imagePath != null) {
                val file = File(block.imagePath)
                if (file.exists()) index to file else null
            } else {
                null
            }
        }
        val relIdByBlockIndex = pictures
            .mapIndexed { i, (index, _) -> index to "rId${10 + i}" }
            .toMap()

        ZipOutputStream(FileOutputStream(destFile)).use { zos ->
            // 1. [Content_Types].xml
            writeZipEntry(zos, "[Content_Types].xml", CONTENT_TYPES_XML)
            // 2. _rels/.rels
            writeZipEntry(zos, "_rels/.rels", ROOT_RELS_XML)
            // 3. word/_rels/document.xml.rels (includes image relationships when present)
            writeZipEntry(zos, "word/_rels/document.xml.rels", buildDocRelsXml(pictures))
            // 4. word/styles.xml
            writeZipEntry(zos, "word/styles.xml", STYLES_XML)
            // 5. word/numbering.xml (real Word numbered lists)
            writeZipEntry(zos, "word/numbering.xml", NUMBERING_XML)
            // 6. word/media/* binary image entries
            pictures.forEachIndexed { i, (_, file) ->
                val ext = file.extension.lowercase().ifBlank { "png" }
                zos.putNextEntry(ZipEntry("word/media/image${i + 1}.$ext"))
                file.inputStream().use { input -> input.copyTo(zos) }
                zos.closeEntry()
            }
            // 7. word/document.xml
            writeZipEntry(zos, "word/document.xml", buildDocumentXml(blocks, relIdByBlockIndex))
        }
        return destFile
    }

    private fun buildDocRelsXml(pictures: List<Pair<Int, File>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>""")
        pictures.forEachIndexed { i, _ ->
            val ext = pictures[i].second.extension.lowercase().ifBlank { "png" }
            sb.append("\n  <Relationship Id=\"rId${10 + i}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image${i + 1}.$ext\"/>")
        }
        sb.append("\n</Relationships>")
        return sb.toString()
    }

    /** Inline centered image paragraph — standard Word w:drawing markup. */
    private fun buildPictureParagraphXml(block: WordBlock, relId: String, blockIndex: Int): String {
        val path = block.imagePath ?: return ""
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val widthPx = if (bounds.outWidth > 0) bounds.outWidth else 800
        val heightPx = if (bounds.outHeight > 0) bounds.outHeight else 600
        // EMU = px * 9525 (96dpi); cap display width at ~5.9 inches (page text width)
        var cx = widthPx * 9525L
        var cy = heightPx * 9525L
        val maxCx = 5_400_000L
        if (cx > maxCx) {
            cy = cy * maxCx / cx
            cx = maxCx
        }
        val docPrId = 100 + blockIndex
        val sb = StringBuilder()
        sb.append("    <w:p>\n")
        sb.append("      <w:pPr><w:jc w:val=\"center\"/></w:pPr>\n")
        sb.append("""      <w:r>
        <w:drawing>
          <wp:inline distT="0" distB="0" distL="0" distR="0">
            <wp:extent cx="$cx" cy="$cy"/>
            <wp:docPr id="$docPrId" name="Picture${docPrId}"/>
            <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                <pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                  <pic:nvPicPr><pic:cNvPr id="$docPrId" name="Picture${docPrId}"/><pic:cNvPicPr/></pic:nvPicPr>
                  <pic:blipFill>
                    <a:blip r:embed="$relId"/>
                    <a:stretch><a:fillRect/></a:stretch>
                  </pic:blipFill>
                  <pic:spPr>
                    <a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>
                    <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                  </pic:spPr>
                </pic:pic>
              </a:graphicData>
            </a:graphic>
          </wp:inline>
        </w:drawing>
      </w:r>
""")
        if (block.text.isNotBlank()) {
            sb.append("      <w:r>\n")
            sb.append("        <w:t xml:space=\"preserve\">${escapeXml(block.text)}</w:t>\n")
            sb.append("      </w:r>\n")
        }
        sb.append("    </w:p>\n")
        return sb.toString()
    }

    /** Real Word table with full borders and proportional column widths. */
    private fun buildTableXml(rows: List<List<String>>): String {
        if (rows.isEmpty()) return ""
        val colCount = rows.maxOf { it.size }.coerceAtLeast(1)
        val contentWidthTwips = 9026 // A4 minus 1440-twip margins on both sides
        val colWidth = contentWidthTwips / colCount
        val sb = StringBuilder()
        sb.append("    <w:tbl>\n")
        sb.append("      <w:tblPr><w:tblW w:w=\"$contentWidthTwips\" w:type=\"dxa\"/><w:tblBorders>")
        listOf("top", "left", "bottom", "right", "insideH", "insideV").forEach { border ->
            sb.append("<w:$border w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"999999\"/>")
        }
        sb.append("</w:tblBorders></w:tblPr>\n")
        sb.append("      <w:tblGrid>")
        repeat(colCount) { sb.append("<w:gridCol w:w=\"$colWidth\"/>") }
        sb.append("</w:tblGrid>\n")
        rows.forEach { row ->
            sb.append("      <w:tr>\n")
            repeat(colCount) { col ->
                val cellText = row.getOrNull(col) ?: ""
                sb.append("        <w:tc><w:tcPr><w:tcW w:w=\"$colWidth\" w:type=\"dxa\"/></w:tcPr>")
                sb.append("<w:p><w:r><w:t xml:space=\"preserve\">${escapeXml(cellText)}</w:t></w:r></w:p></w:tc>\n")
            }
            sb.append("      </w:tr>\n")
        }
        sb.append("    </w:tbl>\n")
        // Word requires a paragraph directly after every table
        sb.append("    <w:p/>\n")
        return sb.toString()
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun buildDocumentXml(blocks: List<WordBlock>, relIdByBlockIndex: Map<Int, String> = emptyMap()): String {
        val sb = StringBuilder()
        sb.append(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing">
  <w:body>
"""
        )

        val safeBlocks = if (blocks.isEmpty()) {
            listOf(WordBlock(type = WordBlockType.PARAGRAPH, text = ""))
        } else {
            blocks
        }

        for ((blockIndex, block) in safeBlocks.withIndex()) {
            when (block.type) {
                WordBlockType.PICTURE -> {
                    val relId = relIdByBlockIndex[blockIndex]
                    if (relId != null) {
                        sb.append(buildPictureParagraphXml(block, relId, blockIndex))
                    } else {
                        // Missing image file: keep a visible placeholder paragraph
                        sb.append("    <w:p><w:r><w:t xml:space=\"preserve\">[Image]</w:t></w:r></w:p>\n")
                    }
                }
                WordBlockType.TABLE -> sb.append(buildTableXml(block.tableRows))
                else -> {
                    sb.append("    <w:p>\n")

                    // --- Paragraph properties: style, numbering, alignment ---
                    val pPr = StringBuilder("<w:pPr>")
                    when (block.type) {
                        WordBlockType.HEADING_1 -> pPr.append("<w:pStyle w:val=\"Heading1\"/>")
                        WordBlockType.HEADING_2 -> pPr.append("<w:pStyle w:val=\"Heading2\"/>")
                        WordBlockType.BULLET -> pPr.append("<w:pStyle w:val=\"ListParagraph\"/><w:ind w:left=\"480\"/>")
                        WordBlockType.NUMBERED -> pPr.append(
                            "<w:pStyle w:val=\"ListParagraph\"/><w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr>"
                        )
                        WordBlockType.PARAGRAPH -> pPr.append("<w:spacing w:after=\"120\" w:line=\"276\" w:lineRule=\"auto\"/>")
                        else -> pPr.append("<w:spacing w:after=\"120\"/>")
                    }
            when (block.alignment) {
                WordAlign.CENTER -> pPr.append("<w:jc w:val=\"center\"/>")
                WordAlign.RIGHT -> pPr.append("<w:jc w:val=\"right\"/>")
                WordAlign.JUSTIFY -> pPr.append("<w:jc w:val=\"both\"/>")
                WordAlign.LEFT -> { /* default, omit */ }
            }
            pPr.append("</w:pPr>")
            sb.append("      $pPr\n")

            val displayText = if (block.type == WordBlockType.BULLET && !block.text.startsWith("•")) {
                "• ${block.text}"
            } else {
                block.text
            }

            sb.append("      <w:r>\n")
            val needsRpr = block.isBold || block.isItalic || block.isUnderline || block.isStrikethrough ||
                    block.fontSizeSp != null || block.colorHex != null ||
                    block.type == WordBlockType.HEADING_1 || block.type == WordBlockType.HEADING_2
            if (needsRpr) {
                sb.append("        <w:rPr>")
                if (block.isBold || block.type == WordBlockType.HEADING_1 || block.type == WordBlockType.HEADING_2) {
                    sb.append("<w:b/>")
                }
                if (block.isItalic) {
                    sb.append("<w:i/>")
                }
                if (block.isUnderline) {
                    sb.append("<w:u w:val=\"single\"/>")
                }
                if (block.isStrikethrough) {
                    sb.append("<w:strike/>")
                }
                if (block.colorHex != null) {
                    sb.append("<w:color w:val=\"${block.colorHex}\"/>")
                }
                if (block.fontSizeSp != null) {
                    sb.append("<w:sz w:val=\"${block.fontSizeSp * 2}\"/><w:szCs w:val=\"${block.fontSizeSp * 2}\"/>")
                }
                if (block.type == WordBlockType.HEADING_1) {
                    sb.append("<w:sz w:val=\"36\"/><w:color w:val=\"00695C\"/>")
                } else if (block.type == WordBlockType.HEADING_2) {
                    sb.append("<w:sz w:val=\"28\"/><w:color w:val=\"004D40\"/>")
                }
                sb.append("</w:rPr>\n")
            }
            sb.append("        <w:t xml:space=\"preserve\">${escapeXml(displayText)}</w:t>\n")
            sb.append("      </w:r>\n")
            sb.append("    </w:p>\n")
                }
            }
        }

        sb.append(
            """    <w:sectPr>
      <w:pgSz w:w="11906" w:h="16838"/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/>
    </w:sectPr>
  </w:body>
</w:document>"""
        )
        return sb.toString()
    }

    // ==================== SMART DOCUMENT CLEANER ====================

    /**
     * 1. Removes empty blocks, collapses double/triple spaces, trims messy edges.
     */
    fun cleanWhitespace(blocks: List<WordBlock>): List<WordBlock> {
        return blocks.mapNotNull { block ->
            if (block.type == WordBlockType.PICTURE || block.type == WordBlockType.TABLE) {
                return@mapNotNull block
            }
            val cleaned = block.text
                .replace(Regex("[ \\t]+"), " ")
                .replace(Regex("\\n{2,}"), "\n")
                .trim()
            if (cleaned.isEmpty()) null else block.copy(text = cleaned)
        }
    }

    /**
     * 2. Merges broken line wraps from PDF-to-Word conversions into smooth flowing paragraphs.
     */
    fun mergeBrokenLineBreaks(blocks: List<WordBlock>): List<WordBlock> {
        if (blocks.size <= 1) return blocks
        val result = mutableListOf<WordBlock>()

        for (block in blocks) {
            val cleanedText = block.text.replace(Regex("\\s*\\n\\s*"), " ").trim()
            val current = block.copy(text = cleanedText)

            val prev = result.lastOrNull()
            val canMergeWithPrev = prev != null &&
                    prev.type == WordBlockType.PARAGRAPH &&
                    current.type == WordBlockType.PARAGRAPH &&
                    !prev.text.endsWith(".") &&
                    !prev.text.endsWith(":") &&
                    !prev.text.endsWith("?") &&
                    !prev.text.endsWith("!") &&
                    current.text.isNotEmpty() &&
                    current.text.first().isLowerCase()

            if (canMergeWithPrev && prev != null) {
                result[result.lastIndex] = prev.copy(text = "${prev.text} ${current.text}")
            } else {
                result.add(current)
            }
        }
        return result
    }

    /**
     * 3. Auto-detects lecture/chapter/section titles and promotes them to Heading 1 / Heading 2.
     */
    fun autoFormatHeadings(blocks: List<WordBlock>): List<WordBlock> {
        return blocks.map { block ->
            val t = block.text.trim()
            val isH1Pattern = t.matches(Regex("^(?:Chapter|Unit|Module|Lecture|Lesson|Week)\\s+\\d+.*", RegexOption.IGNORE_CASE)) ||
                    (t.length in 4..60 && t == t.uppercase() && t.any { it.isLetter() })
            val isH2Pattern = t.matches(Regex("^\\d+(\\.\\d+)+\\s+.*")) ||
                    (t.length in 4..55 && t.endsWith(":") && !t.contains("."))
            val isBulletPattern = t.startsWith("•") || t.startsWith("- ") || t.startsWith("* ")

            when {
                isH1Pattern -> block.copy(type = WordBlockType.HEADING_1, isBold = true)
                isH2Pattern -> block.copy(type = WordBlockType.HEADING_2, isBold = true)
                isBulletPattern -> block.copy(
                    type = WordBlockType.BULLET,
                    text = t.replaceFirst(Regex("^[•\\-*]\\s*"), "").trim()
                )
                else -> block
            }
        }
    }

    /**
     * 4. Strips page numbers, repeated PDF headers/footers, and optional custom watermark string.
     */
    fun stripNoiseAndWatermarks(blocks: List<WordBlock>, customNoise: String = ""): List<WordBlock> {
        val pageNumberRegex = Regex("^(?:Page\\s+\\d+(?:\\s+of\\s+\\d+)?|\\d+\\s*/\\s*\\d+|-\\s*\\d+\\s*-|\\d{1,3})$", RegexOption.IGNORE_CASE)
        val trimmedNoise = customNoise.trim()

        // Also detect lines repeated 3+ times identically across document (common header/footer)
        val freq = blocks.groupingBy { it.text.trim() }.eachCount()

        return blocks.filter { block ->
            val t = block.text.trim()
            if (block.type == WordBlockType.PICTURE || block.type == WordBlockType.TABLE) return@filter true
            if (t.isEmpty()) return@filter false
            if (pageNumberRegex.matches(t)) return@filter false
            if (trimmedNoise.isNotEmpty() && t.equals(trimmedNoise, ignoreCase = true)) return@filter false
            if (t.length < 70 && (freq[t] ?: 0) >= 3) return@filter false
            true
        }.map { block ->
            if (trimmedNoise.isNotEmpty() && block.text.contains(trimmedNoise, ignoreCase = true)) {
                block.copy(text = block.text.replace(trimmedNoise, "", ignoreCase = true).trim())
            } else {
                block
            }
        }.filter { it.text.isNotEmpty() || it.type == WordBlockType.PICTURE || it.type == WordBlockType.TABLE }
    }

    /**
     * 5. Find and Replace across all blocks. Returns Pair(updatedBlocks, matchCount).
     */
    fun findAndReplace(
        blocks: List<WordBlock>,
        findQuery: String,
        replaceWith: String,
        ignoreCase: Boolean = true
    ): Pair<List<WordBlock>, Int> {
        if (findQuery.isEmpty()) return blocks to 0
        var totalMatches = 0
        val regex = Regex(Regex.escape(findQuery), if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())

        val updated = blocks.map { block ->
            val count = regex.findAll(block.text).count()
            if (count > 0) {
                totalMatches += count
                block.copy(text = regex.replace(block.text, Regex.escapeReplacement(replaceWith)))
            } else {
                block
            }
        }
        return updated to totalMatches
    }

    private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="png" ContentType="image/png"/>
  <Default Extension="jpg" ContentType="image/jpeg"/>
  <Default Extension="jpeg" ContentType="image/jpeg"/>
  <Default Extension="gif" ContentType="image/gif"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>
</Types>"""

    private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private const val DOC_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>
</Relationships>"""

    private const val NUMBERING_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:abstractNum w:abstractNumId="0">
    <w:lvl w:ilvl="0">
      <w:start w:val="1"/>
      <w:numFmt w:val="decimal"/>
      <w:lvlText w:val="%1."/>
      <w:lvlJc w:val="left"/>
      <w:pPr><w:ind w:left="480" w:hanging="360"/></w:pPr>
    </w:lvl>
  </w:abstractNum>
  <w:num w:numId="1">
    <w:abstractNumId w:val="0"/>
  </w:num>
</w:numbering>"""

    private const val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:rPr><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr><w:spacing w:before="240" w:after="120"/></w:pPr>
    <w:rPr><w:b/><w:sz w:val="36"/><w:color w:val="00695C"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading2">
    <w:name w:val="heading 2"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr><w:spacing w:before="180" w:after="80"/></w:pPr>
    <w:rPr><w:b/><w:sz w:val="28"/><w:color w:val="004D40"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="ListParagraph">
    <w:name w:val="List Paragraph"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr><w:ind w:left="480"/></w:pPr>
  </w:style>
</w:styles>"""
}

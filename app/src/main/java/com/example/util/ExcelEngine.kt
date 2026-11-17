package com.example.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Xml
import com.example.model.ExcelSheetData
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Real OOXML (.xlsx) and CSV/TSV Spreadsheet reader, writer, and PDF exporter.
 * Compatible with Microsoft Excel, Google Sheets, and LibreOffice Calc.
 */
object ExcelEngine {

    fun importSpreadsheetFromUri(context: Context, uri: Uri, filenameHint: String): File? {
        val dir = File(context.filesDir, "spreadsheets")
        if (!dir.exists()) dir.mkdirs()
        val safeName = filenameHint.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "Sheet.xlsx" }
        val destFile = File(dir, "${System.currentTimeMillis()}_$safeName")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile
        }.getOrNull()
    }

    fun readSpreadsheetFile(file: File): List<ExcelSheetData> {
        if (!file.exists()) return emptyList()
        val ext = file.extension.lowercase()
        return when (ext) {
            "csv" -> FileInputStream(file).use { listOf(parseDelimitedStream(it, ',', file.nameWithoutExtension.ifBlank { "Sheet1" })) }
            "tsv" -> FileInputStream(file).use { listOf(parseDelimitedStream(it, '\t', file.nameWithoutExtension.ifBlank { "Sheet1" })) }
            "ods" -> FileInputStream(file).use { readOdsStream(it) }
            "xls" -> {
                val bytes = file.readBytes()
                val isZip = bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
                if (isZip) readXlsxStream(bytes.inputStream()) else readLegacyXlsBytes(bytes, file.nameWithoutExtension)
            }
            else -> FileInputStream(file).use { readXlsxStream(it) }
        }
    }

    fun saveSpreadsheetFile(file: File, sheets: List<ExcelSheetData>): File {
        val ext = file.extension.lowercase()
        return when (ext) {
            "csv", "tsv" -> {
                val delim = if (ext == "tsv") '\t' else ','
                val rows = sheets.firstOrNull()?.rows ?: emptyList()
                val text = rows.joinToString("\n") { row ->
                    row.joinToString(delim.toString()) { cell ->
                        if (cell.contains(delim) || cell.contains('"') || cell.contains('\n')) {
                            "\"${cell.replace("\"", "\"\"")}\""
                        } else cell
                    }
                }
                file.parentFile?.mkdirs()
                file.writeText(text)
                file
            }
            else -> writeXlsxFile(sheets, file)
        }
    }

    private fun readOdsStream(stream: InputStream): List<ExcelSheetData> {
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
        val sheets = mutableListOf<ExcelSheetData>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(bytes.inputStream(), "UTF-8")

        var sheetName = "Sheet1"
        var currentRows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        var inCell = false
        var inTextP = false
        var repeatCols = 1
        val cellText = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "table:table" -> {
                            sheetName = parser.getAttributeValue(null, "table:name") ?: "Sheet${sheets.size + 1}"
                            currentRows = mutableListOf()
                        }
                        "table:table-row" -> currentRow = mutableListOf()
                        "table:table-cell" -> {
                            inCell = true
                            cellText.setLength(0)
                            repeatCols = (parser.getAttributeValue(null, "table:number-columns-repeated")?.toIntOrNull() ?: 1)
                                .coerceIn(1, 20)
                        }
                        "text:p" -> if (inCell) inTextP = true
                    }
                }
                XmlPullParser.TEXT -> if (inCell && inTextP) cellText.append(parser.text ?: "")
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "text:p" -> {
                            if (inCell && cellText.isNotEmpty()) cellText.append(" ")
                            inTextP = false
                        }
                        "table:table-cell" -> {
                            val value = cellText.toString().trim()
                            repeat(repeatCols) {
                                if (currentRow.size < 40) currentRow.add(value)
                            }
                            inCell = false
                        }
                        "table:table-row" -> {
                            if (currentRow.any { it.isNotBlank() }) {
                                currentRows.add(currentRow.toList())
                            }
                        }
                        "table:table" -> {
                            if (currentRows.isNotEmpty()) {
                                val maxCols = currentRows.maxOfOrNull { it.size } ?: 0
                                val normalized = currentRows.map { r ->
                                    if (r.size < maxCols) r + List(maxCols - r.size) { "" } else r
                                }
                                sheets.add(ExcelSheetData(name = sheetName, rows = normalized))
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        return sheets
    }

    private fun readLegacyXlsBytes(bytes: ByteArray, sheetName: String): List<ExcelSheetData> {
        val strings = mutableListOf<String>()
        val run = StringBuilder()
        for (b in bytes) {
            val code = b.toInt() and 0xFF
            if (code in 32..126) {
                run.append(code.toChar())
            } else {
                if (run.length >= 2) {
                    val s = run.toString().trim()
                    if (s.any { it.isLetterOrDigit() }) strings.add(s)
                }
                run.setLength(0)
            }
        }
        val clean = strings.distinct().take(200)
        val rows = clean.chunked(4).map { chunk ->
            if (chunk.size < 4) chunk + List(4 - chunk.size) { "" } else chunk
        }
        return if (rows.isEmpty()) emptyList() else listOf(ExcelSheetData(sheetName.ifBlank { "Sheet1" }, rows))
    }

    private fun parseDelimitedStream(stream: InputStream, delimiter: Char, sheetName: String): ExcelSheetData {
        val content = stream.bufferedReader().readText()
        val rows = mutableListOf<List<String>>()
        for (rawLine in content.lines()) {
            if (rawLine.isBlank()) continue
            rows.add(parseCsvLine(rawLine, delimiter))
        }
        val maxCols = rows.maxOfOrNull { it.size } ?: 0
        val normalized = rows.map { row ->
            if (row.size < maxCols) row + List(maxCols - row.size) { "" } else row
        }
        return ExcelSheetData(name = sheetName, rows = normalized)
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    cells.add(current.toString().trim())
                    current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        cells.add(current.toString().trim())
        return cells
    }

    private fun readXlsxStream(stream: InputStream): List<ExcelSheetData> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(stream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (entries.isEmpty()) return emptyList()

        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it.inputStream()) } ?: emptyList()
        val relsMap = entries["xl/_rels/workbook.xml.rels"]?.let { parseWorkbookRels(it.inputStream()) } ?: emptyMap()
        val sheetInfos = entries["xl/workbook.xml"]?.let { parseWorkbookSheets(it.inputStream(), relsMap) }

        val resolvedSheets = if (!sheetInfos.isNullOrEmpty()) {
            sheetInfos
        } else {
            entries.keys
                .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
                .sortedBy { Regex("\\d+").find(it)?.value?.toIntOrNull() ?: 999 }
                .mapIndexed { idx, path -> "Sheet${idx + 1}" to path }
        }

        val result = mutableListOf<ExcelSheetData>()
        for ((name, rawPath) in resolvedSheets) {
            val normalizedPath = rawPath.removePrefix("/").let {
                if (it.startsWith("xl/")) it else "xl/$it"
            }
            val bytes = entries[normalizedPath] ?: entries[rawPath.removePrefix("/")] ?: continue
            val rows = parseWorksheetXml(bytes.inputStream(), sharedStrings)
            result.add(ExcelSheetData(name = name, rows = rows))
        }
        return result
    }

    private fun parseSharedStrings(stream: InputStream): List<String> {
        val list = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, "UTF-8")

        var inSi = false
        var inText = false
        val sb = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "si" -> {
                            inSi = true
                            sb.setLength(0)
                        }
                        "t" -> if (inSi) inText = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inSi && inText) sb.append(parser.text ?: "")
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "t" -> inText = false
                        "si" -> {
                            list.add(sb.toString())
                            inSi = false
                        }
                    }
                }
            }
            event = parser.next()
        }
        return list
    }

    private fun parseWorkbookRels(stream: InputStream): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, "UTF-8")

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id")
                val target = parser.getAttributeValue(null, "Target")
                if (!id.isNullOrBlank() && !target.isNullOrBlank()) {
                    map[id] = target
                }
            }
            event = parser.next()
        }
        return map
    }

    private fun parseWorkbookSheets(stream: InputStream, relsMap: Map<String, String>): List<Pair<String, String>> {
        val sheets = mutableListOf<Pair<String, String>>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, "UTF-8")

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name") ?: "Sheet"
                val rId = parser.getAttributeValue(null, "r:id")
                    ?: parser.getAttributeValue(null, "id")
                val sheetId = parser.getAttributeValue(null, "sheetId") ?: "${sheets.size + 1}"
                val target = if (rId != null && relsMap.containsKey(rId)) {
                    relsMap[rId]!!
                } else {
                    "worksheets/sheet$sheetId.xml"
                }
                sheets.add(name to target)
            }
            event = parser.next()
        }
        return sheets
    }

    private fun parseWorksheetXml(stream: InputStream, sharedStrings: List<String>): List<List<String>> {
        val parsedRows = mutableListOf<Pair<Int, Map<Int, String>>>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, "UTF-8")

        var currentRowIndex = 0
        var currentRowCells = mutableMapOf<Int, String>()
        var currentColIndex = 0
        var currentCellType = ""
        var inCell = false
        var inValue = false
        var inInlineText = false
        val valueBuilder = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "row" -> {
                            val rAttr = parser.getAttributeValue(null, "r")?.toIntOrNull()
                            currentRowIndex = if (rAttr != null) rAttr - 1 else parsedRows.size
                            currentRowCells = mutableMapOf()
                            currentColIndex = 0
                        }
                        "c" -> {
                            inCell = true
                            val ref = parser.getAttributeValue(null, "r")
                            currentColIndex = if (!ref.isNullOrBlank()) colRefToIndex(ref) else currentColIndex
                            currentCellType = parser.getAttributeValue(null, "t") ?: ""
                            valueBuilder.setLength(0)
                        }
                        "v" -> if (inCell) {
                            inValue = true
                            valueBuilder.setLength(0)
                        }
                        "t" -> if (inCell && currentCellType == "inlineStr") {
                            inInlineText = true
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inValue || inInlineText) {
                        valueBuilder.append(parser.text ?: "")
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "v" -> inValue = false
                        "t" -> inInlineText = false
                        "c" -> {
                            val raw = valueBuilder.toString().trim()
                            val decoded = when (currentCellType) {
                                "s" -> raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: raw
                                "b" -> if (raw == "1") "TRUE" else "FALSE"
                                else -> raw
                            }
                            if (decoded.isNotEmpty()) {
                                currentRowCells[currentColIndex] = decoded
                            }
                            currentColIndex++
                            inCell = false
                        }
                        "row" -> {
                            if (currentRowCells.isNotEmpty()) {
                                parsedRows.add(currentRowIndex to currentRowCells)
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }

        if (parsedRows.isEmpty()) return emptyList()
        val maxCol = parsedRows.maxOfOrNull { (_, map) -> map.keys.maxOrNull() ?: 0 } ?: 0
        val colCount = (maxCol + 1).coerceAtLeast(1)
        val maxRow = parsedRows.maxOfOrNull { it.first } ?: 0

        val rowMap = parsedRows.toMap()
        return (0..maxRow).map { rIdx ->
            val cells = rowMap[rIdx] ?: emptyMap()
            (0 until colCount).map { cIdx -> cells[cIdx] ?: "" }
        }.filter { row -> row.any { it.isNotBlank() } }
    }

    private fun colRefToIndex(ref: String): Int {
        var result = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') {
                result = result * 26 + (ch - 'A' + 1)
            } else if (ch in 'a'..'z') {
                result = result * 26 + (ch - 'a' + 1)
            } else {
                break
            }
        }
        return (result - 1).coerceAtLeast(0)
    }

    fun indexToColName(index: Int): String {
        var idx = index
        val sb = StringBuilder()
        while (idx >= 0) {
            sb.insert(0, ('A' + (idx % 26)))
            idx = idx / 26 - 1
        }
        return sb.toString()
    }

    /**
     * Writes a valid multi-sheet OOXML (.xlsx) workbook.
     */
    fun writeXlsxFile(sheets: List<ExcelSheetData>, outputFile: File): File {
        outputFile.parentFile?.mkdirs()
        val safeSheets = if (sheets.isEmpty()) listOf(ExcelSheetData("Sheet1", listOf(listOf("")))) else sheets

        val sharedStrings = mutableListOf<String>()
        val stringIndexMap = mutableMapOf<String, Int>()
        fun intern(text: String): Int {
            return stringIndexMap.getOrPut(text) {
                sharedStrings.add(text)
                sharedStrings.size - 1
            }
        }

        safeSheets.forEach { sheet ->
            sheet.rows.forEach { row ->
                row.forEach { cell ->
                    if (cell.isNotEmpty() && cell.toDoubleOrNull() == null) {
                        intern(cell)
                    }
                }
            }
        }

        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            writeZipEntry(zos, "[Content_Types].xml", buildContentTypesXml(safeSheets.size))
            writeZipEntry(zos, "_rels/.rels", ROOT_RELS_XML)
            writeZipEntry(zos, "xl/workbook.xml", buildWorkbookXml(safeSheets))
            writeZipEntry(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml(safeSheets.size))
            writeZipEntry(zos, "xl/sharedStrings.xml", buildSharedStringsXml(sharedStrings))
            safeSheets.forEachIndexed { idx, sheet ->
                writeZipEntry(zos, "xl/worksheets/sheet${idx + 1}.xml", buildSheetXml(sheet, ::intern))
            }
        }
        return outputFile
    }

    /**
     * Exports a spreadsheet tab to a clean landscape A4 PDF table.
     */
    fun exportSheetToPdf(title: String, sheet: ExcelSheetData, outputPdfFile: File): File {
        val pageW = 842 // A4 Landscape
        val pageH = 595
        val margin = 32f
        val colCount = (sheet.rows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
        val availW = pageW - margin * 2
        val colW = availW / colCount
        val rowH = 22f

        val pdfDoc = PdfDocument()
        try {
            var pageNum = 1
            var page = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            var canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0, 105, 92)
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val headerBgPaint = Paint().apply { color = Color.rgb(224, 242, 241) }
            val altRowPaint = Paint().apply { color = Color.rgb(248, 250, 249) }
            val borderPaint = Paint().apply {
                color = Color.rgb(200, 212, 208)
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }
            val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(28, 34, 33)
                textSize = 9.5f
            }
            val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0, 77, 64)
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            fun drawHeaderBanner() {
                canvas.drawText("$title — ${sheet.name}", margin, margin + 12f, titlePaint)
            }

            drawHeaderBanner()
            var cursorY = margin + 26f

            sheet.rows.forEachIndexed { rIdx, row ->
                if (cursorY + rowH > pageH - margin) {
                    pdfDoc.finishPage(page)
                    pageNum++
                    page = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                    canvas = page.canvas
                    canvas.drawColor(Color.WHITE)
                    drawHeaderBanner()
                    cursorY = margin + 26f
                }

                val isHeader = rIdx == 0
                if (isHeader) {
                    canvas.drawRect(margin, cursorY, margin + availW, cursorY + rowH, headerBgPaint)
                } else if (rIdx % 2 == 0) {
                    canvas.drawRect(margin, cursorY, margin + availW, cursorY + rowH, altRowPaint)
                }

                for (cIdx in 0 until colCount) {
                    val left = margin + cIdx * colW
                    canvas.drawRect(left, cursorY, left + colW, cursorY + rowH, borderPaint)
                    val text = row.getOrElse(cIdx) { "" }
                    val maxChars = ((colW - 8f) / 5.5f).toInt().coerceAtLeast(3)
                    val clipped = if (text.length > maxChars) text.take(maxChars - 1) + "…" else text
                    canvas.drawText(
                        clipped,
                        left + 4f,
                        cursorY + 14.5f,
                        if (isHeader) headerTextPaint else cellPaint
                    )
                }
                cursorY += rowH
            }

            pdfDoc.finishPage(page)
            outputPdfFile.parentFile?.mkdirs()
            FileOutputStream(outputPdfFile).use { pdfDoc.writeTo(it) }
        } finally {
            pdfDoc.close()
        }
        return outputPdfFile
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun buildContentTypesXml(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("""</Types>""")
    }

    private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun buildWorkbookXml(sheets: List<ExcelSheetData>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { idx, sheet ->
            append("""<sheet name="${escapeXml(sheet.name)}" sheetId="${idx + 1}" r:id="rId${idx + 1}"/>""")
        }
        append("""</sheets></workbook>""")
    }

    private fun buildWorkbookRelsXml(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""")
        append("""</Relationships>""")
    }

    private fun buildSharedStringsXml(strings: List<String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${strings.size}" uniqueCount="${strings.size}">""")
        strings.forEach { s ->
            append("""<si><t xml:space="preserve">${escapeXml(s)}</t></si>""")
        }
        append("""</sst>""")
    }

    private fun buildSheetXml(sheet: ExcelSheetData, intern: (String) -> Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        sheet.rows.forEachIndexed { rIdx, row ->
            append("""<row r="${rIdx + 1}">""")
            row.forEachIndexed { cIdx, value ->
                if (value.isNotEmpty()) {
                    val ref = "${indexToColName(cIdx)}${rIdx + 1}"
                    val num = value.toDoubleOrNull()
                    if (num != null) {
                        append("""<c r="$ref"><v>$value</v></c>""")
                    } else {
                        val sIdx = intern(value)
                        append("""<c r="$ref" t="s"><v>$sIdx</v></c>""")
                    }
                }
            }
            append("""</row>""")
        }
        append("""</sheetData></worksheet>""")
    }
}

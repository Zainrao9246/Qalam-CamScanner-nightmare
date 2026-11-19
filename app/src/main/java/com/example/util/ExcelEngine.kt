package com.example.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Xml
import com.example.model.ExcelCellStyle
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
        val xfStyles = entries["xl/styles.xml"]?.let { parseStylesXml(it.inputStream()) } ?: emptyMap()
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
            val parsed = parseWorksheetXml(bytes.inputStream(), sharedStrings, xfStyles)
            result.add(
                ExcelSheetData(
                    name = name,
                    rows = parsed.rows,
                    formulas = parsed.formulas,
                    styles = parsed.styles
                )
            )
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

    private class ParsedWorksheet(
        val rows: List<List<String>>,
        val formulas: Map<String, String>,
        val styles: Map<String, ExcelCellStyle>
    )

    /** Parses xl/styles.xml into a map of cellXfs index -> resolved cell style. */
    private fun parseStylesXml(stream: InputStream): Map<Int, ExcelCellStyle> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, "UTF-8")

        val fonts = mutableListOf<Triple<Boolean, Boolean, String?>>()  // bold, italic, color(6 hex)
        val fills = mutableListOf<String?>()                            // fill rgb(6 hex) or null
        val xfs = mutableListOf<Pair<Int, Int>>()                       // fontId, fillId

        var inFonts = false
        var inFills = false
        var inCellXfs = false
        var curBold = false
        var curItalic = false
        var curColor: String? = null
        var curFill: String? = null
        var curPatternSolid = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "fonts" -> inFonts = true
                        "fills" -> inFills = true
                        "cellXfs" -> inCellXfs = true
                        "font" -> if (inFonts) {
                            curBold = false
                            curItalic = false
                            curColor = null
                        }
                        "b" -> if (inFonts) curBold = true
                        "i" -> if (inFonts) curItalic = true
                        "color" -> if (inFonts) {
                            val rgb = parser.getAttributeValue(null, "rgb")
                            if (!rgb.isNullOrBlank() && rgb.length >= 6) curColor = rgb.takeLast(6).uppercase()
                        }
                        "fill" -> if (inFills) {
                            curFill = null
                            curPatternSolid = false
                        }
                        "patternFill" -> if (inFills) {
                            curPatternSolid = parser.getAttributeValue(null, "patternType") == "solid"
                        }
                        "fgColor" -> if (inFills && curPatternSolid) {
                            val rgb = parser.getAttributeValue(null, "rgb")
                            if (!rgb.isNullOrBlank() && rgb.length >= 6) curFill = rgb.takeLast(6).uppercase()
                        }
                        "xf" -> if (inCellXfs) {
                            val fontId = parser.getAttributeValue(null, "fontId")?.toIntOrNull() ?: 0
                            val fillId = parser.getAttributeValue(null, "fillId")?.toIntOrNull() ?: 0
                            xfs.add(fontId to fillId)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "font" -> if (inFonts) fonts.add(Triple(curBold, curItalic, curColor))
                        "fill" -> if (inFills) fills.add(if (curPatternSolid) curFill else null)
                        "fonts" -> inFonts = false
                        "fills" -> inFills = false
                        "cellXfs" -> inCellXfs = false
                    }
                }
            }
            event = parser.next()
        }

        val out = mutableMapOf<Int, ExcelCellStyle>()
        xfs.forEachIndexed { idx, (fontId, fillId) ->
            val font = fonts.getOrNull(fontId) ?: Triple(false, false, null)
            val fill = fills.getOrNull(fillId)
            val style = ExcelCellStyle(
                bold = font.first,
                italic = font.second,
                colorHex = font.third,
                fillHex = fill
            )
            if (!style.isDefault) out[idx] = style
        }
        return out
    }

    private fun parseWorksheetXml(
        stream: InputStream,
        sharedStrings: List<String>,
        xfStyles: Map<Int, ExcelCellStyle> = emptyMap()
    ): ParsedWorksheet {
        val parsedRows = mutableListOf<Pair<Int, Map<Int, String>>>()
        val formulas = mutableMapOf<String, String>()
        val styles = mutableMapOf<String, ExcelCellStyle>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, "UTF-8")

        var currentRowIndex = 0
        var currentRowCells = mutableMapOf<Int, String>()
        var currentColIndex = 0
        var currentCellType = ""
        var currentCellRef = ""
        var currentStyleIdx = -1
        var inCell = false
        var inValue = false
        var inInlineText = false
        var inFormula = false
        val valueBuilder = StringBuilder()
        val formulaBuilder = StringBuilder()

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
                            currentCellRef = ref ?: ""
                            currentColIndex = if (!ref.isNullOrBlank()) colRefToIndex(ref) else currentColIndex
                            currentCellType = parser.getAttributeValue(null, "t") ?: ""
                            currentStyleIdx = parser.getAttributeValue(null, "s")?.toIntOrNull() ?: -1
                            valueBuilder.setLength(0)
                            formulaBuilder.setLength(0)
                        }
                        "f" -> if (inCell) {
                            inFormula = true
                            formulaBuilder.setLength(0)
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
                    if (inFormula) {
                        formulaBuilder.append(parser.text ?: "")
                    } else if (inValue || inInlineText) {
                        valueBuilder.append(parser.text ?: "")
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "v" -> inValue = false
                        "t" -> inInlineText = false
                        "f" -> inFormula = false
                        "c" -> {
                            val raw = valueBuilder.toString().trim()
                            val decoded = when (currentCellType) {
                                "s" -> raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: raw
                                "b" -> if (raw == "1") "TRUE" else "FALSE"
                                else -> raw
                            }
                            if (formulaBuilder.isNotBlank()) {
                                val fText = formulaBuilder.toString().trim()
                                if (fText.isNotEmpty() && currentCellRef.isNotBlank()) {
                                    formulas[currentCellRef.uppercase()] = "=$fText"
                                }
                            }
                            if (decoded.isNotEmpty()) {
                                currentRowCells[currentColIndex] = decoded
                            }
                            if (currentStyleIdx >= 0 && currentCellRef.isNotBlank()) {
                                xfStyles[currentStyleIdx]?.let { styles[currentCellRef.uppercase()] = it }
                            }
                            currentColIndex++
                            inCell = false
                            inFormula = false
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

        if (parsedRows.isEmpty()) {
            return ParsedWorksheet(emptyList(), formulas, styles)
        }
        val maxCol = parsedRows.maxOfOrNull { (_, map) -> map.keys.maxOrNull() ?: 0 } ?: 0
        val colCount = (maxCol + 1).coerceAtLeast(1)
        val maxRow = parsedRows.maxOfOrNull { it.first } ?: 0

        val rowMap = parsedRows.toMap()
        val rows = (0..maxRow).map { rIdx ->
            val cells = rowMap[rIdx] ?: emptyMap()
            (0 until colCount).map { cIdx -> cells[cIdx] ?: "" }
        }.filter { row -> row.any { it.isNotBlank() } }
        return ParsedWorksheet(rows, formulas, styles)
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
     * Writes a valid multi-sheet OOXML (.xlsx) workbook with formulas (cached values)
     * and per-cell styles (bold/italic/font color/fill).
     */
    fun writeXlsxFile(sheets: List<ExcelSheetData>, outputFile: File): File {
        outputFile.parentFile?.mkdirs()
        val safeSheets = if (sheets.isEmpty()) listOf(ExcelSheetData("Sheet1", listOf(listOf("")))) else sheets

        // Recalculate formulas so cached <v> values written to the file are fresh
        val evalSheets = safeSheets.map { evaluateSheetFormulas(it) }

        // --- Build dynamic style tables (fonts / fills / cellXfs) ---
        val usedStyles = evalSheets
            .flatMap { sheet -> sheet.styles.values }
            .filter { !it.isDefault }
            .distinct()
        val fonts = mutableListOf<Triple<Boolean, Boolean, String?>>()   // bold, italic, color(6 hex)
        fonts.add(Triple(false, false, null))                            // font 0: default
        val fills = mutableListOf<String?>()                             // fill rgb(6 hex) or null
        fills.add(null)                                                  // fill 0: none
        fills.add("gray125")                                             // fill 1: built-in
        val xfSpecs = mutableListOf<Pair<Int, Int>>()                    // xf -> (fontId, fillId)
        xfSpecs.add(0 to 0)                                              // xf 0: default
        val styleIndexMap = HashMap<ExcelCellStyle, Int>()
        for (style in usedStyles) {
            val fontKey = Triple(style.bold, style.italic, style.colorHex)
            var fontId = fonts.indexOf(fontKey)
            if (fontId < 0) {
                fonts.add(fontKey)
                fontId = fonts.size - 1
            }
            var fillId = 0
            if (style.fillHex != null) {
                fillId = fills.indexOf(style.fillHex)
                if (fillId < 0) {
                    fills.add(style.fillHex)
                    fillId = fills.size - 1
                }
            }
            styleIndexMap[style] = xfSpecs.size
            xfSpecs.add(fontId to fillId)
        }

        val sharedStrings = mutableListOf<String>()
        val stringIndexMap = mutableMapOf<String, Int>()
        fun intern(text: String): Int {
            return stringIndexMap.getOrPut(text) {
                sharedStrings.add(text)
                sharedStrings.size - 1
            }
        }

        evalSheets.forEach { sheet ->
            sheet.rows.forEach { row ->
                row.forEach { cell ->
                    if (cell.isNotEmpty() && cell.toDoubleOrNull() == null) {
                        intern(cell)
                    }
                }
            }
        }

        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            writeZipEntry(zos, "[Content_Types].xml", buildContentTypesXml(evalSheets.size))
            writeZipEntry(zos, "_rels/.rels", ROOT_RELS_XML)
            writeZipEntry(zos, "xl/workbook.xml", buildWorkbookXml(evalSheets))
            writeZipEntry(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml(evalSheets.size))
            writeZipEntry(zos, "xl/styles.xml", buildStylesXml(fonts, fills, xfSpecs))
            writeZipEntry(zos, "xl/sharedStrings.xml", buildSharedStringsXml(sharedStrings))
            evalSheets.forEachIndexed { idx, sheet ->
                writeZipEntry(zos, "xl/worksheets/sheet${idx + 1}.xml", buildSheetXml(sheet, ::intern, styleIndexMap))
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
                    val ref = "${indexToColName(cIdx)}${rIdx + 1}"
                    val cellStyle = sheet.styles[ref]
                    if (cellStyle?.fillHex != null) {
                        try {
                            val fillPaint = Paint().apply {
                                color = Color.parseColor("#${cellStyle.fillHex}")
                            }
                            canvas.drawRect(left + 1f, cursorY + 1f, left + colW - 1f, cursorY + rowH - 1f, fillPaint)
                        } catch (_: IllegalArgumentException) {
                            // invalid color, skip fill
                        }
                    }
                    val text = row.getOrElse(cIdx) { "" }
                    val maxChars = ((colW - 8f) / 5.5f).toInt().coerceAtLeast(3)
                    val clipped = if (text.length > maxChars) text.take(maxChars - 1) + "…" else text
                    val styleFlags = when {
                        isHeader || (cellStyle?.bold == true && (cellStyle.italic)) -> Typeface.BOLD_ITALIC
                        isHeader || cellStyle?.bold == true -> Typeface.BOLD
                        cellStyle?.italic == true -> Typeface.ITALIC
                        else -> Typeface.NORMAL
                    }
                    val cellPaint = if (isHeader) {
                        headerTextPaint
                    } else {
                        Paint(cellPaint).apply {
                            typeface = Typeface.create(Typeface.DEFAULT, styleFlags)
                            cellStyle?.colorHex?.let {
                                try {
                                    color = Color.parseColor("#$it")
                                } catch (_: IllegalArgumentException) {
                                    // keep default
                                }
                            }
                        }
                    }
                    canvas.drawText(clipped, left + 4f, cursorY + 14.5f, cellPaint)
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

    // ==================== FORMULA ENGINE ====================
    // Supports: SUM, AVG/AVERAGE, MIN, MAX, COUNT, IF, ABS, ROUND,
    // cell refs (B3), ranges (B1:B9), + - * / parentheses, comparisons (= <> < > <= >=)

    private data class FormulaTok(val type: String, val text: String)

    private fun tokenizeFormula(src: String): List<FormulaTok> {
        val tokens = mutableListOf<FormulaTok>()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    var j = i
                    while (j < src.length && (src[j].isDigit() || src[j] == '.')) j++
                    tokens.add(FormulaTok("num", src.substring(i, j)))
                    i = j
                }
                c.isLetter() -> {
                    var j = i
                    while (j < src.length && src[j].isLetterOrDigit()) j++
                    val text = src.substring(i, j)
                    val type = if (text.matches(Regex("[A-Za-z]+[0-9]+"))) "ref" else "name"
                    tokens.add(FormulaTok(type, text))
                    i = j
                }
                c == '"' -> {
                    var j = i + 1
                    val sb = StringBuilder()
                    while (j < src.length && src[j] != '"') {
                        sb.append(src[j])
                        j++
                    }
                    tokens.add(FormulaTok("str", sb.toString()))
                    i = j + 1
                }
                src.startsWith("<=", i) -> { tokens.add(FormulaTok("op", "<=")); i += 2 }
                src.startsWith(">=", i) -> { tokens.add(FormulaTok("op", ">=")); i += 2 }
                src.startsWith("<>", i) -> { tokens.add(FormulaTok("op", "<>")); i += 2 }
                c in "+-*/(),:<>=" -> { tokens.add(FormulaTok("op", c.toString())); i++ }
                else -> i++
            }
        }
        return tokens
    }

    private class FormulaParser(
        private val tokens: List<FormulaTok>,
        private val sheet: ExcelSheetData
    ) {
        private var pos = 0

        private fun peek(): FormulaTok? = tokens.getOrNull(pos)
        private fun next(): FormulaTok? = tokens.getOrNull(pos++)

        fun eval(): Any {
            val v = parseComparison()
            return v
        }

        private fun parseComparison(): Any {
            var left: Any = parseAdd()
            while (true) {
                val t = peek() ?: break
                if (t.type == "op" && t.text in listOf("=", "<", ">", "<=", ">=", "<>")) {
                    next()
                    val right = parseAdd()
                    val l = numOf(left)
                    val r = numOf(right)
                    left = when (t.text) {
                        "=" -> l == r
                        "<" -> l < r
                        ">" -> l > r
                        "<=" -> l <= r
                        ">=" -> l >= r
                        "<>" -> l != r
                        else -> false
                    }
                } else break
            }
            return left
        }

        private fun parseAdd(): Any {
            var left: Any = parseMul()
            while (true) {
                val t = peek() ?: break
                if (t.type == "op" && (t.text == "+" || t.text == "-")) {
                    next()
                    val right = parseMul()
                    left = if (t.text == "+") numOf(left) + numOf(right) else numOf(left) - numOf(right)
                } else break
            }
            return left
        }

        private fun parseMul(): Any {
            var left: Any = parseUnary()
            while (true) {
                val t = peek() ?: break
                if (t.type == "op" && (t.text == "*" || t.text == "/")) {
                    next()
                    val right = parseUnary()
                    left = if (t.text == "*") {
                        numOf(left) * numOf(right)
                    } else {
                        val d = numOf(right)
                        if (d == 0.0) 0.0 else numOf(left) / d
                    }
                } else break
            }
            return left
        }

        private fun parseUnary(): Any {
            val t = peek()
            if (t != null && t.type == "op" && t.text == "-") {
                next()
                return -numOf(parseUnary())
            }
            if (t != null && t.type == "op" && t.text == "+") {
                next()
                return parseUnary()
            }
            return parseAtom()
        }

        private fun parseAtom(): Any {
            val t = next() ?: return ""
            return when (t.type) {
                "num" -> t.text.toDoubleOrNull() ?: 0.0
                "str" -> t.text
                "ref" -> cellValue(t.text.uppercase())
                "name" -> {
                    val func = t.text.uppercase()
                    if (peek()?.text == "(") {
                        next()
                        val args = mutableListOf<Any>()
                        if (peek()?.text != ")") {
                            while (true) {
                                args.add(parseArg())
                                if (peek()?.text == ",") {
                                    next()
                                    continue
                                } else break
                            }
                        }
                        if (peek()?.text == ")") next()
                        evalFunction(func, args)
                    } else {
                        ""
                    }
                }
                "op" -> if (t.text == "(") {
                    val v = parseComparison()
                    if (peek()?.text == ")") next()
                    v
                } else ""
                else -> ""
            }
        }

        /** arg := scalar expression OR range like B1:B9 */
        private fun parseArg(): Any {
            val t = peek()
            if (t != null && t.type == "ref") {
                if (tokens.getOrNull(pos + 1)?.text == ":") {
                    next()
                    next()
                    val endTok = next()
                    return rangeValues(t.text.uppercase(), endTok?.text?.uppercase() ?: t.text.uppercase())
                }
            }
            return parseComparison()
        }

        private fun evalFunction(func: String, args: List<Any>): Any {
            val nums = mutableListOf<Double>()
            for (a in args) {
                when (a) {
                    is List<*> -> a.forEach { nums.add(numOf(it)) }
                    else -> nums.add(numOf(a))
                }
            }
            return when (func) {
                "SUM" -> nums.sum()
                "AVG", "AVERAGE" -> if (nums.isEmpty()) 0.0 else nums.sum() / nums.size
                "MIN" -> nums.minOrNull() ?: 0.0
                "MAX" -> nums.maxOrNull() ?: 0.0
                "COUNT" -> nums.size.toDouble()
                "IF" -> {
                    val cond = args.getOrNull(0)
                    val condBool = if (cond is Boolean) cond else numOf(cond) != 0.0
                    if (condBool) args.getOrNull(1) ?: "" else args.getOrNull(2) ?: ""
                }
                "ABS" -> kotlin.math.abs(nums.firstOrNull() ?: 0.0)
                "ROUND" -> {
                    val v = nums.getOrNull(0) ?: 0.0
                    val d = (nums.getOrNull(1) ?: 0.0).toInt()
                    val f = Math.pow(10.0, d.toDouble())
                    Math.round(v * f) / f
                }
                else -> "#NAME?"
            }
        }

        private fun cellValue(ref: String): Any {
            val (r, c) = refToCoords(ref)
            val raw = sheet.rows.getOrNull(r)?.getOrNull(c) ?: ""
            if (raw.isBlank()) return 0.0
            raw.toDoubleOrNull()?.let { return it }
            return raw
        }

        private fun rangeValues(startRef: String, endRef: String): List<Any> {
            val (r1, c1) = refToCoords(startRef)
            val (r2, c2) = refToCoords(endRef)
            val out = mutableListOf<Any>()
            for (r in minOf(r1, r2)..maxOf(r1, r2)) {
                for (c in minOf(c1, c2)..maxOf(c1, c2)) {
                    val raw = sheet.rows.getOrNull(r)?.getOrNull(c) ?: ""
                    raw.toDoubleOrNull()?.let { out.add(it) }
                }
            }
            return out
        }

        private fun numOf(v: Any?): Double = when (v) {
            is Double -> v
            is Int -> v.toDouble()
            is Boolean -> if (v) 1.0 else 0.0
            is String -> v.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    /** "B12" -> (rowIndex 11, colIndex 1) */
    fun refToCoords(ref: String): Pair<Int, Int> {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        val digits = ref.dropWhile { it.isLetter() }
        var col = 0
        for (ch in letters) col = col * 26 + (ch - 'A' + 1)
        return (digits.toIntOrNull() ?: 1) - 1 to (col - 1).coerceAtLeast(0)
    }

    /** Evaluates a single formula ("=SUM(A1:A4)" or "SUM(A1:A4)") against [sheet]'s current display values. */
    fun evaluateFormula(formula: String, sheet: ExcelSheetData): String {
        val src = formula.removePrefix("=").trim()
        if (src.isEmpty()) return ""
        // Unbalanced parentheses or quotes cannot parse cleanly — report #ERR instead of a silent wrong value
        var parenBalance = 0
        var inQuote = false
        for (ch in src) {
            when {
                ch == '"' -> inQuote = !inQuote
                ch == '(' && !inQuote -> parenBalance++
                ch == ')' && !inQuote -> parenBalance--
            }
        }
        if (inQuote || parenBalance != 0) return "#ERR"
        return try {
            val tokens = tokenizeFormula(src)
            if (tokens.isEmpty()) return ""
            val result = FormulaParser(tokens, sheet).eval()
            when (result) {
                is Boolean -> if (result) "TRUE" else "FALSE"
                is Double -> formatFormulaNumber(result)
                else -> result?.toString() ?: ""
            }
        } catch (_: Exception) {
            "#ERR"
        }
    }

    /**
     * Recalculates every formula cell of the sheet and returns a copy with
     * fresh display values in [rows]. Two passes handle simple formula chains.
     */
    fun evaluateSheetFormulas(sheet: ExcelSheetData): ExcelSheetData {
        if (sheet.formulas.isEmpty()) return sheet
        var result = sheet
        repeat(2) {
            val newRows = result.rows.mapIndexed { rIdx, row ->
                row.mapIndexed { cIdx, value ->
                    val ref = "${indexToColName(cIdx)}${rIdx + 1}"
                    val formula = result.formulas[ref]
                    if (formula != null) evaluateFormula(formula, result) else value
                }
            }
            result = result.copy(rows = newRows)
        }
        return result
    }

    private fun formatFormulaNumber(v: Double): String {
        return if (v == v.toLong().toDouble()) {
            v.toLong().toString()
        } else {
            "%.4f".format(v).trimEnd('0').trimEnd('.')
        }
    }

    private fun buildStylesXml(
        fonts: List<Triple<Boolean, Boolean, String?>>,
        fills: List<String?>,
        xfSpecs: List<Pair<Int, Int>>
    ): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<fonts count="${fonts.size}">""")
        fonts.forEach { (bold, italic, colorHex) ->
            append("""<font><sz val="11"/><name val="Calibri"/>""")
            if (bold) append("""<b/>""")
            if (italic) append("""<i/>""")
            if (colorHex != null) append("""<color rgb="FF${colorHex.uppercase()}"/>""")
            append("""</font>""")
        }
        append("""</fonts>""")
        append("""<fills count="${fills.size}">""")
        fills.forEach { rgb ->
            if (rgb == null) {
                append("""<fill><patternFill patternType="none"/></fill>""")
            } else if (rgb == "gray125") {
                append("""<fill><patternFill patternType="gray125"/></fill>""")
            } else {
                append("""<fill><patternFill patternType="solid"><fgColor rgb="FF${rgb.uppercase()}"/><bgColor indexed="64"/></patternFill></fill>""")
            }
        }
        append("""</fills>""")
        append("""<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""")
        append("""<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0"/></cellStyleXfs>""")
        append("""<cellXfs count="${xfSpecs.size}">""")
        xfSpecs.forEach { (fontId, fillId) ->
            val applyFont = if (fontId > 0) " applyFont=\"1\"" else ""
            val applyFill = if (fillId > 1) " applyFill=\"1\"" else ""
            append("""<xf numFmtId="0" fontId="$fontId" fillId="$fillId" xfId="0"$applyFont$applyFill/>""")
        }
        append("""</cellXfs>""")
        append("""</styleSheet>""")
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
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
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
        append("""<Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("""<Relationship Id="rId${sheetCount + 2}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""")
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

    private fun buildSheetXml(
        sheet: ExcelSheetData,
        intern: (String) -> Int,
        styleIndexMap: Map<ExcelCellStyle, Int>
    ): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        sheet.rows.forEachIndexed { rIdx, row ->
            append("""<row r="${rIdx + 1}">""")
            row.forEachIndexed { cIdx, value ->
                val ref = "${indexToColName(cIdx)}${rIdx + 1}"
                val style = sheet.styles[ref]
                val sAttr = if (style != null && styleIndexMap.containsKey(style)) {
                    " s=\"${styleIndexMap[style]}\""
                } else ""
                val formula = sheet.formulas[ref]
                when {
                    formula != null -> {
                        val num = value.toDoubleOrNull()
                        val fXml = "<f>${escapeXml(formula.removePrefix("="))}</f>"
                        if (value.isEmpty()) {
                            append("""<c r="$ref"$sAttr>$fXml</c>""")
                        } else if (num != null) {
                            append("""<c r="$ref"$sAttr>$fXml<v>${formatFormulaNumber(num)}</v></c>""")
                        } else {
                            append("""<c r="$ref"$sAttr t="str">$fXml<v>${escapeXml(value)}</v></c>""")
                        }
                    }
                    value.isNotEmpty() -> {
                        val num = value.toDoubleOrNull()
                        if (num != null) {
                            append("""<c r="$ref"$sAttr><v>$value</v></c>""")
                        } else {
                            val sIdx = intern(value)
                            append("""<c r="$ref"$sAttr t="s"><v>$sIdx</v></c>""")
                        }
                    }
                }
            }
            append("""</row>""")
        }
        append("""</sheetData></worksheet>""")
    }
}

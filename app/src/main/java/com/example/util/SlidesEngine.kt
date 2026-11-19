package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.example.model.SlideItem
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
 * Real OOXML (.pptx) PowerPoint slide deck reader & writer.
 */
object SlidesEngine {

    fun importPptxFromUri(context: Context, uri: Uri, filenameHint: String): File? {
        val dir = File(context.filesDir, "slides")
        if (!dir.exists()) dir.mkdirs()
        val safeName = filenameHint.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "Slides.pptx" }
        val destFile = File(dir, "${System.currentTimeMillis()}_$safeName")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            destFile
        }.getOrNull()
    }

    fun readPptxFile(file: File): List<SlideItem> {
        if (!file.exists()) return emptyList()
        if (file.extension.equals("odp", ignoreCase = true)) {
            val odpSlides = FileInputStream(file).use { readOdpStream(it) }
            if (odpSlides.isNotEmpty()) return odpSlides
        }
        FileInputStream(file).use { return readPptxStream(it) }
    }

    private fun readOdpStream(stream: InputStream): List<SlideItem> {
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
        val slides = mutableListOf<SlideItem>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(bytes.inputStream(), "UTF-8")

        var inPage = false
        var inNotes = false
        var inParagraph = false
        var slideIndex = 0
        val currentLines = mutableListOf<String>()
        val notesLines = mutableListOf<String>()
        val sb = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "draw:page" -> {
                            inPage = true
                            slideIndex++
                            currentLines.clear()
                            notesLines.clear()
                        }
                        "presentation:notes" -> if (inPage) inNotes = true
                        "text:p", "text:h" -> if (inPage) {
                            inParagraph = true
                            sb.setLength(0)
                        }
                    }
                }
                XmlPullParser.TEXT -> if (inPage && inParagraph) sb.append(parser.text ?: "")
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "text:p", "text:h" -> {
                            val line = sb.toString().trim()
                            if (line.isNotEmpty()) {
                                if (inNotes) notesLines.add(line) else currentLines.add(line)
                            }
                            inParagraph = false
                        }
                        "presentation:notes" -> inNotes = false
                        "draw:page" -> {
                            val title = currentLines.firstOrNull() ?: "Slide $slideIndex"
                            val body = if (currentLines.size > 1) currentLines.drop(1) else emptyList()
                            slides.add(
                                SlideItem(
                                    number = slideIndex,
                                    title = title,
                                    bodyLines = body,
                                    notes = notesLines.joinToString("\n")
                                )
                            )
                            inPage = false
                        }
                    }
                }
            }
            event = parser.next()
        }
        return slides
    }

    private fun readPptxStream(stream: InputStream): List<SlideItem> {
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

        val slidePaths = entries.keys
            .filter { Regex("^ppt/slides/slide\\d+\\.xml$").matches(it) }
            .sortedBy { Regex("\\d+").find(it)?.value?.toIntOrNull() ?: 999 }

        return slidePaths.mapIndexed { idx, path ->
            val slideNum = Regex("\\d+").find(path)?.value?.toIntOrNull() ?: (idx + 1)
            val parsed = parseSlideXml(entries[path]!!.inputStream())
            val notesPath = "ppt/notesSlides/notesSlide$slideNum.xml"
            val notesLines = entries[notesPath]?.let { parseTextParagraphs(it.inputStream()) } ?: emptyList()

            val title = parsed.paragraphs.firstOrNull()?.text?.takeIf { it.isNotBlank() } ?: "Slide $slideNum"
            val body = parsed.paragraphs.drop(1).map { it.text }
            val notes = notesLines
                .filterNot { it == slideNum.toString() }
                .joinToString("\n")
                .trim()

            SlideItem(
                number = slideNum,
                title = title,
                bodyLines = body,
                notes = notes,
                titleColorHex = parsed.paragraphs.firstOrNull()?.colorHex,
                titleSizePt = parsed.paragraphs.firstOrNull()?.sizePt,
                bodyColorHex = parsed.paragraphs.getOrNull(1)?.colorHex,
                bodySizePt = parsed.paragraphs.getOrNull(1)?.sizePt,
                backgroundHex = parsed.backgroundHex
            )
        }
    }

    private class SlideParagraph(val text: String, val colorHex: String?, val sizePt: Int?)

    private class ParsedSlide(val paragraphs: List<SlideParagraph>, val backgroundHex: String?)

    /** Parses one slide XML: paragraphs (with first-run color/size) + background color. */
    private fun parseSlideXml(stream: InputStream): ParsedSlide {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, "UTF-8")

        val paragraphs = mutableListOf<SlideParagraph>()
        var backgroundHex: String? = null

        var inBg = false
        var inRunProps = false
        var inParagraph = false
        var inText = false
        var inRun = false
        val sb = StringBuilder()
        var runColor: String? = null
        var runSizePt: Int? = null
        var pColor: String? = null
        var pSizePt: Int? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "p:bg" -> inBg = true
                        "a:rPr", "a:defRPr" -> {
                            inRunProps = true
                            runColor = null
                            runSizePt = parser.getAttributeValue(null, "sz")?.toIntOrNull()?.div(100)
                        }
                        "a:srgbClr" -> {
                            val hex = parser.getAttributeValue(null, "val")
                            if (!hex.isNullOrBlank() && hex.length >= 6) {
                                if (inBg) {
                                    backgroundHex = hex.takeLast(6).uppercase()
                                } else if (inRunProps) {
                                    runColor = hex.takeLast(6).uppercase()
                                }
                            }
                        }
                        "a:p" -> {
                            inParagraph = true
                            inRun = false
                            pColor = null
                            pSizePt = null
                            sb.setLength(0)
                        }
                        "a:r" -> if (inParagraph) {
                            inRun = true
                            runColor = null
                            runSizePt = null
                        }
                        "a:t" -> if (inParagraph && inRun) inText = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inText) sb.append(parser.text ?: "")
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "p:bg" -> inBg = false
                        "a:rPr", "a:defRPr" -> inRunProps = false
                        "a:t" -> inText = false
                        "a:r" -> if (inParagraph) {
                            inRun = false
                            if (pColor == null && sb.isNotBlank()) {
                                pColor = runColor
                                pSizePt = runSizePt
                            }
                        }
                        "a:p" -> {
                            val text = sb.toString().trim()
                            if (text.isNotEmpty()) {
                                paragraphs.add(SlideParagraph(text, pColor, pSizePt))
                            }
                            inParagraph = false
                            inText = false
                        }
                    }
                }
            }
            event = parser.next()
        }
        return ParsedSlide(paragraphs, backgroundHex)
    }

    private fun parseTextParagraphs(stream: InputStream): List<String> {
        val paragraphs = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, "UTF-8")

        var inParagraph = false
        var inText = false
        val sb = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "a:p" -> {
                            inParagraph = true
                            sb.setLength(0)
                        }
                        "a:t" -> if (inParagraph) inText = true
                        "a:br" -> if (inParagraph) sb.append(" ")
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inParagraph && inText) sb.append(parser.text ?: "")
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "a:t" -> inText = false
                        "a:p" -> {
                            val line = sb.toString().trim()
                            if (line.isNotEmpty()) paragraphs.add(line)
                            inParagraph = false
                        }
                    }
                }
            }
            event = parser.next()
        }
        return paragraphs
    }

    fun slidesToWordBlocks(deckTitle: String, slides: List<SlideItem>): List<WordBlock> {
        val blocks = mutableListOf<WordBlock>()
        blocks.add(WordBlock(type = WordBlockType.HEADING_1, text = deckTitle, isBold = true))
        for (slide in slides) {
            blocks.add(WordBlock(type = WordBlockType.HEADING_2, text = "Slide ${slide.number}: ${slide.title}", isBold = true))
            for (line in slide.bodyLines) {
                blocks.add(WordBlock(type = WordBlockType.BULLET, text = line))
            }
            if (slide.notes.isNotBlank()) {
                blocks.add(WordBlock(type = WordBlockType.PARAGRAPH, text = "Notes: ${slide.notes}", isItalic = true))
            }
        }
        return blocks
    }

    fun writePptxFile(slides: List<SlideItem>, outputFile: File): File {
        outputFile.parentFile?.mkdirs()
        val safeSlides = if (slides.isEmpty()) listOf(SlideItem(1, "Slide 1", emptyList())) else slides

        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            writeZipEntry(zos, "[Content_Types].xml", buildContentTypesXml(safeSlides.size))
            writeZipEntry(zos, "_rels/.rels", ROOT_RELS_XML)
            writeZipEntry(zos, "ppt/presentation.xml", buildPresentationXml(safeSlides.size))
            writeZipEntry(zos, "ppt/_rels/presentation.xml.rels", buildPresentationRelsXml(safeSlides.size))
            safeSlides.forEachIndexed { idx, slide ->
                val num = idx + 1
                writeZipEntry(zos, "ppt/slides/slide$num.xml", buildSlideXml(slide))
                if (slide.notes.isNotBlank()) {
                    writeZipEntry(zos, "ppt/notesSlides/notesSlide$num.xml", buildNotesXml(slide.notes))
                }
            }
        }
        return outputFile
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

    private fun buildContentTypesXml(slideCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>""")
        for (i in 1..slideCount) {
            append("""<Override PartName="/ppt/slides/slide$i.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
        }
        append("""</Types>""")
    }

    private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""

    private fun buildPresentationXml(slideCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:sldIdLst>""")
        for (i in 1..slideCount) {
            append("""<p:sldId id="${255 + i}" r:id="rId$i"/>""")
        }
        append("""</p:sldIdLst></p:presentation>""")
    }

    private fun buildPresentationRelsXml(slideCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..slideCount) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide$i.xml"/>""")
        }
        append("""</Relationships>""")
    }

    private fun buildSlideXml(slide: SlideItem): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld>""")
        slide.backgroundHex?.let { bg ->
            append("""<p:bg><p:bgPr><a:solidFill><a:srgbClr val="${bg.uppercase()}"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>""")
        }
        append("""<p:spTree>""")
        append("""<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>""")

        // --- Title text box ---
        append("""<p:sp><p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>""")
        append("""<p:spPr><a:xfrm><a:off x="838200" y="365125"/><a:ext cx="7524750" cy="1325563"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr>""")
        append("""<p:txBody><a:bodyPr wrap="square"/><a:lstStyle/>""")
        append("""<a:p><a:r>${runProps(slide.titleSizePt ?: 32, slide.titleColorHex, bold = true)}<a:t>${escapeXml(slide.title)}</a:t></a:r></a:p>""")
        append("""</p:txBody></p:sp>""")

        // --- Body text box ---
        append("""<p:sp><p:nvSpPr><p:cNvPr id="3" name="Content"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>""")
        append("""<p:spPr><a:xfrm><a:off x="838200" y="1825625"/><a:ext cx="7524750" cy="4351338"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr>""")
        append("""<p:txBody><a:bodyPr wrap="square"/><a:lstStyle/>""")
        slide.bodyLines.forEach { line ->
            append("""<a:p><a:r>${runProps(slide.bodySizePt ?: 18, slide.bodyColorHex, bold = false)}<a:t>${escapeXml(line)}</a:t></a:r></a:p>""")
        }
        append("""</p:txBody></p:sp>""")

        append("""</p:spTree></p:cSld></p:sld>""")
    }

    /** DrawingML run properties: sz is in hundredths of a point. */
    private fun runProps(sizePt: Int, colorHex: String?, bold: Boolean): String = buildString {
        append("<a:rPr lang=\"en-US\"")
        append(" sz=\"")
        append(sizePt * 100)
        append("\"")
        if (bold) append(" b=\"1\"")
        append(">")
        if (colorHex != null) {
            append("<a:solidFill><a:srgbClr val=\"")
            append(colorHex.uppercase())
            append("\"/></a:solidFill>")
        }
        append("</a:rPr>")
    }

    private fun buildNotesXml(notes: String): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<p:notes xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:sp><p:txBody><a:bodyPr/><a:lstStyle/>""")
        notes.lines().filter { it.isNotBlank() }.forEach { line ->
            append("""<a:p><a:r><a:t>${escapeXml(line)}</a:t></a:r></a:p>""")
        }
        append("""</p:txBody></p:sp></p:spTree></p:cSld></p:notes>""")
    }
}

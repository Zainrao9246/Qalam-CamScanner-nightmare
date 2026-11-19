package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.example.data.DocumentRepository
import com.example.data.PdfAnnotationStore
import com.example.model.DocKind
import com.example.model.ExcelSheetData
import com.example.model.NormPoint
import com.example.model.PdfCommentPin
import com.example.model.PdfDocumentAnnotations
import com.example.model.PdfStroke
import com.example.model.PdfStrokeType
import com.example.model.WordBlockType
import kotlinx.coroutines.runBlocking
import com.example.util.CodeReaderEngine
import com.example.util.DocxEngine
import com.example.util.ExcelEngine
import com.example.util.ImageProcessor
import com.example.util.PdfEngine
import com.example.util.PdfSuiteEngine
import com.example.util.SlidesEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Implements(PdfRenderer::class)
class SuiteShadowPdfRenderer {
    @Implementation
    fun __constructor__(input: ParcelFileDescriptor) {
        // Accept ParcelFileDescriptor on JVM
    }

    @Implementation
    fun getPageCount(): Int = TestShadowPdfDocument.lastWrittenPageCount.coerceAtLeast(1)

    @Implementation
    fun openPage(index: Int): PdfRenderer.Page = Shadow.newInstanceOf(PdfRenderer.Page::class.java)

    @Implementation
    fun close() {
        // No-op
    }
}

@Implements(PdfRenderer.Page::class)
class SuiteShadowPdfRendererPage {
    @Implementation
    fun getWidth(): Int = 595

    @Implementation
    fun getHeight(): Int = 842

    @Implementation
    fun render(destination: Bitmap, destClip: Rect?, transform: Matrix?, renderMode: Int) {
        val c = Canvas(destination)
        c.drawColor(Color.WHITE)
        val p = Paint().apply { color = Color.DKGRAY }
        c.drawRect(20f, 20f, (destination.width - 20).toFloat(), 60f, p)
    }

    @Implementation
    fun close() {
        // No-op
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    shadows = [TestShadowPdfDocument::class, SuiteShadowPdfRenderer::class, SuiteShadowPdfRendererPage::class]
)
class PdfSuiteAndFormatsTest {

    @Test
    fun pdfAnnotationStoreSaveLoadAndDeleteRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val docId = "doc_unit_test_123"
        val annotations = PdfDocumentAnnotations(
            docId = docId,
            bookmarkedPages = setOf(0, 3),
            pageNotes = mapOf(0 to "Focus on Big-O table", 3 to "Exam question candidate"),
            strokes = listOf(
                PdfStroke(
                    id = "s1",
                    pageIndex = 0,
                    type = PdfStrokeType.HIGHLIGHT,
                    colorArgb = 0xFFFFEB3BL,
                    widthRatio = 0.03f,
                    points = listOf(NormPoint(0.1f, 0.2f), NormPoint(0.8f, 0.2f))
                )
            ),
            comments = listOf(
                PdfCommentPin(
                    id = "c1",
                    pageIndex = 1,
                    xRatio = 0.45f,
                    yRatio = 0.35f,
                    title = "Important Formula",
                    body = "Memorize recurrence relation",
                    colorArgb = 0xFFFFB300L
                )
            )
        )

        PdfAnnotationStore.save(context, annotations)
        val loaded = PdfAnnotationStore.load(context, docId)

        assertEquals(setOf(0, 3), loaded.bookmarkedPages)
        assertEquals("Focus on Big-O table", loaded.pageNotes[0])
        assertEquals(1, loaded.strokes.size)
        assertEquals(PdfStrokeType.HIGHLIGHT, loaded.strokes.first().type)
        assertEquals(1, loaded.comments.size)
        assertEquals("Important Formula", loaded.comments.first().title)

        PdfAnnotationStore.delete(context, docId)
        val afterDelete = PdfAnnotationStore.load(context, docId)
        assertTrue(afterDelete.strokes.isEmpty())
        assertTrue(afterDelete.comments.isEmpty())
        assertTrue(afterDelete.bookmarkedPages.isEmpty())
    }

    @Test
    fun pdfSuiteMergeSplitCompressWatermarkAndAnnotatedExportWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val src1 = File(context.cacheDir, "suite_src1.pdf")
        val src2 = File(context.cacheDir, "suite_src2.pdf")
        PdfEngine.convertWordToPdf(
            "Part 1",
            listOf(com.example.model.WordBlock(type = WordBlockType.HEADING_1, text = "Lecture 1", isBold = true)),
            src1
        )
        PdfEngine.convertWordToPdf(
            "Part 2",
            listOf(com.example.model.WordBlock(type = WordBlockType.PARAGRAPH, text = "Lecture 2 content")),
            src2
        )

        // Merge
        val merged = File(context.cacheDir, "suite_merged.pdf")
        PdfSuiteEngine.mergePdfs(listOf(src1, src2), merged)
        assertTrue(merged.exists() && merged.length() > 500)
        assertEquals(2, PdfEngine.getPdfPageCount(merged))

        // Compress (2-page input)
        val compressed = File(context.cacheDir, "suite_compressed.pdf")
        val compResult = PdfSuiteEngine.compressPdf(
            sourcePdf = merged,
            level = PdfSuiteEngine.CompressionLevel.BALANCED,
            outputFile = compressed
        )
        assertTrue(compResult.outputFile.exists() && compResult.compressedBytes > 0)

        // Watermark (2-page input)
        val watermarked = File(context.cacheDir, "suite_watermarked.pdf")
        PdfSuiteEngine.watermarkPdf(merged, "VU CS301", diagonal = true, outputFile = watermarked)
        assertTrue(watermarked.exists() && watermarked.length() > 500)

        // Annotated Export (2 original pages + 1 summary page = 3)
        val annotated = File(context.cacheDir, "suite_annotated.pdf")
        val annData = PdfDocumentAnnotations(
            docId = "doc_ann",
            bookmarkedPages = setOf(0),
            pageNotes = mapOf(0 to "Review before quiz"),
            strokes = listOf(
                PdfStroke(
                    id = "st1",
                    pageIndex = 0,
                    type = PdfStrokeType.PEN,
                    colorArgb = 0xFFE53935L,
                    widthRatio = 0.01f,
                    points = listOf(NormPoint(0.2f, 0.2f), NormPoint(0.6f, 0.5f))
                )
            ),
            comments = listOf(
                PdfCommentPin(id = "cm1", pageIndex = 0, xRatio = 0.5f, yRatio = 0.5f, title = "Note", body = "Check formula")
            )
        )
        PdfSuiteEngine.exportAnnotatedPdf(merged, "CS301 Lecture", annData, annotated, includeNotesSummaryPage = true)
        assertTrue(annotated.exists() && annotated.length() > 500)
        assertEquals(3, PdfEngine.getPdfPageCount(annotated))

        // Split / Reorder / Rotate (1-page extracted output)
        val split = File(context.cacheDir, "suite_split.pdf")
        PdfSuiteEngine.splitOrReorderPdf(
            sourcePdf = merged,
            pageSpecs = listOf(
                PdfSuiteEngine.PageTransformSpec(sourcePageIndex = 1, rotationDegrees = 90)
            ),
            outputFile = split
        )
        assertTrue(split.exists() && split.length() > 500)
        assertEquals(1, PdfEngine.getPdfPageCount(split))

        // Page selection parser
        assertEquals(listOf(0, 1, 2, 4), PdfSuiteEngine.parsePageSelection("1-3, 5, 99", 6))

        listOf(src1, src2, merged, split, compressed, watermarked, annotated).forEach { it.delete() }
    }

    @Test
    fun htmlOdtAndEpubParsingWorkInDocxEngine() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // HTML
        val htmlFile = File(context.cacheDir, "notes.html")
        htmlFile.writeText("<html><body><h1>Data Structures</h1><p>Stacks use LIFO order.</p><li>Push and Pop</li></body></html>")
        val htmlBlocks = DocxEngine.readDocxFile(htmlFile)
        assertTrue(htmlBlocks.any { it.type == WordBlockType.HEADING_1 && it.text == "Data Structures" })
        assertTrue(htmlBlocks.any { it.text.contains("Stacks use LIFO order.") })
        htmlFile.delete()

        // ODT
        val odtFile = File(context.cacheDir, "notes.odt")
        ZipOutputStream(FileOutputStream(odtFile)).use { zos ->
            zos.putNextEntry(ZipEntry("content.xml"))
            zos.write(
                """
                <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                    xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                  <office:body><office:text>
                    <text:h>Operating Systems</text:h>
                    <text:p>Virtual memory maps logical addresses.</text:p>
                  </office:text></office:body>
                </office:document-content>
                """.trimIndent().toByteArray()
            )
            zos.closeEntry()
        }
        val odtBlocks = DocxEngine.readDocxFile(odtFile)
        assertEquals(2, odtBlocks.size)
        assertEquals(WordBlockType.HEADING_1, odtBlocks[0].type)
        assertEquals("Operating Systems", odtBlocks[0].text)
        odtFile.delete()

        // EPUB
        val epubFile = File(context.cacheDir, "book.epub")
        ZipOutputStream(FileOutputStream(epubFile)).use { zos ->
            zos.putNextEntry(ZipEntry("OEBPS/ch1.xhtml"))
            zos.write("<html><body><h2>Chapter 1</h2><p>Algorithms and Complexity.</p></body></html>".toByteArray())
            zos.closeEntry()
        }
        val epubBlocks = DocxEngine.readDocxFile(epubFile)
        assertTrue(epubBlocks.any { it.text == "Chapter 1" })
        assertTrue(epubBlocks.any { it.text.contains("Algorithms and Complexity.") })
        epubFile.delete()
    }

    @Test
    fun odsAndEditedCsvSaveWorkInExcelEngine() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val odsFile = File(context.cacheDir, "marks.ods")
        ZipOutputStream(FileOutputStream(odsFile)).use { zos ->
            zos.putNextEntry(ZipEntry("content.xml"))
            zos.write(
                """
                <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                    xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
                    xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                  <office:body><office:spreadsheet>
                    <table:table table:name="Quiz">
                      <table:table-row>
                        <table:table-cell><text:p>Student</text:p></table:table-cell>
                        <table:table-cell><text:p>Score</text:p></table:table-cell>
                      </table:table-row>
                      <table:table-row>
                        <table:table-cell><text:p>Shaheer</text:p></table:table-cell>
                        <table:table-cell><text:p>98</text:p></table:table-cell>
                      </table:table-row>
                    </table:table>
                  </office:spreadsheet></office:body>
                </office:document-content>
                """.trimIndent().toByteArray()
            )
            zos.closeEntry()
        }
        val parsedOds = ExcelEngine.readSpreadsheetFile(odsFile)
        assertEquals(1, parsedOds.size)
        assertEquals("Quiz", parsedOds[0].name)
        assertEquals("Shaheer", parsedOds[0].rows[1][0])
        assertEquals("98", parsedOds[0].rows[1][1])
        odsFile.delete()

        val csvFile = File(context.cacheDir, "edited.csv")
        ExcelEngine.saveSpreadsheetFile(
            csvFile,
            listOf(ExcelSheetData("Sheet1", listOf(listOf("Course", "GPA"), listOf("CS401", "4.0"))))
        )
        val reloaded = ExcelEngine.readSpreadsheetFile(csvFile)
        assertEquals("4.0", reloaded[0].rows[1][1])
        csvFile.delete()
    }

    @Test
    fun odpSlidesAndCodeReaderWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val odpFile = File(context.cacheDir, "deck.odp")
        ZipOutputStream(FileOutputStream(odpFile)).use { zos ->
            zos.putNextEntry(ZipEntry("content.xml"))
            zos.write(
                """
                <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                    xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
                    xmlns:presentation="urn:oasis:names:tc:opendocument:xmlns:presentation:1.0"
                    xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                  <office:body><office:presentation>
                    <draw:page>
                      <text:p>Compiler Construction</text:p>
                      <text:p>Lexical Analysis</text:p>
                      <presentation:notes><text:p>Remember DFA vs NFA</text:p></presentation:notes>
                    </draw:page>
                  </office:presentation></office:body>
                </office:document-content>
                """.trimIndent().toByteArray()
            )
            zos.closeEntry()
        }
        val slides = SlidesEngine.readPptxFile(odpFile)
        assertEquals(1, slides.size)
        assertEquals("Compiler Construction", slides[0].title)
        assertEquals(listOf("Lexical Analysis"), slides[0].bodyLines)
        assertEquals("Remember DFA vs NFA", slides[0].notes)
        odpFile.delete()

        assertTrue(CodeReaderEngine.isCodeOrLogExtension("main.cpp"))
        assertTrue(CodeReaderEngine.isCodeOrLogExtension("schema.sql"))
        assertFalse(CodeReaderEngine.isCodeOrLogExtension("handout.pdf"))

        val codeFile = File(context.cacheDir, "algo.py")
        codeFile.writeText("def bfs(graph, start):\n    return [start]\n")
        val lines = CodeReaderEngine.readLines(codeFile)
        assertTrue(lines.first().contains("def bfs"))
        val wordBlocks = CodeReaderEngine.toWordBlocks("algo.py", lines)
        assertTrue(wordBlocks.any { it.text.contains("return [start]") })
        codeFile.delete()
    }

    @Test
    fun imageProcessorAutoEdgeIdCardAndBookSplitWork() {
        // Bright centered sheet on dark background -> auto-edge detects inner bounds
        val w = 200
        val h = 200
        val docBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h) { Color.BLACK }
        for (y in 30 until 170) {
            for (x in 30 until 170) {
                pixels[y * w + x] = Color.WHITE
            }
        }
        docBmp.setPixels(pixels, 0, w, 0, 0, w, h)

        val quad = ImageProcessor.detectDocumentQuad(docBmp)
        assertTrue(quad.topLeft.x in 0.08f..0.25f)
        assertTrue(quad.topRight.x in 0.75f..0.92f)

        // ID card combiner outputs A4 composite
        val front = Bitmap.createBitmap(400, 250, Bitmap.Config.ARGB_8888)
        val back = Bitmap.createBitmap(400, 250, Bitmap.Config.ARGB_8888)
        val combined = ImageProcessor.combineIdCardSides(front, back)
        assertEquals(1240, combined.width)
        assertEquals(1754, combined.height)

        // Book spread splitter splits width in half
        val spread = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val (left, right) = ImageProcessor.splitBookSpread(spread)
        assertEquals(400, left.width)
        assertEquals(400, right.width)
        assertEquals(600, left.height)
    }

    @Test
    fun pdfToImageFilesAndBlankSpreadsheetAndScanFromFilesWork() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = DocumentRepository(context)

        // Generate 2-page PDF and extract as JPEG scan pages
        val srcPdf = File(context.cacheDir, "extract_test.pdf")
        PdfEngine.convertWordToPdf(
            "Notes",
            listOf(
                com.example.model.WordBlock(type = WordBlockType.HEADING_1, text = "Lecture Summary", isBold = true),
                com.example.model.WordBlock(type = WordBlockType.PARAGRAPH, text = "Important formulas for final exam.")
            ),
            srcPdf
        )
        val outImgDir = File(context.cacheDir, "pdf_img_extract_out")
        val extracted = PdfSuiteEngine.pdfToImageFiles(srcPdf, listOf(0), outImgDir, widthPx = 800)
        assertEquals(1, extracted.size)
        assertTrue(extracted.first().exists() && extracted.first().length() > 100)

        val scanDoc = repo.createScanDocumentFromFiles("Extracted Page", "CS201", extracted)
        assertEquals(DocKind.SCAN, scanDoc.kind)
        assertEquals(1, scanDoc.totalPages)
        assertEquals("CS201", scanDoc.subjectTag)

        // Create blank .xlsx spreadsheet
        val (sheetDoc, sheets) = repo.createBlankSpreadsheetDocument("Semester GPA", "VU")
        assertEquals(DocKind.EXCEL, sheetDoc.kind)
        assertTrue(sheetDoc.filePath != null && File(sheetDoc.filePath!!).exists())
        assertTrue(sheets.first().rows.size >= 3)

        repo.deleteDocument(scanDoc.id)
        repo.deleteDocument(sheetDoc.id)
        srcPdf.delete()
        outImgDir.deleteRecursively()
        Unit
    }
}

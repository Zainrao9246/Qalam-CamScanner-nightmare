package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.example.data.DocumentRepository
import com.example.data.StarterDocumentSeeder
import com.example.model.CropQuad
import com.example.model.DocKind
import com.example.model.ExcelSheetData
import com.example.model.FilterType
import com.example.model.PictureDraftItem
import com.example.model.SlideItem
import com.example.model.WordBlock
import com.example.model.WordBlockType
import com.example.util.ExcelEngine
import com.example.util.ImageProcessor
import com.example.util.PdfEngine
import com.example.util.SlidesEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import java.io.File
import java.io.OutputStream

@Implements(PdfDocument::class)
class TestShadowPdfDocument {
    private var pagesCount = 0
    private var closed = false
    private var currentPage: PdfDocument.Page? = null

    @Implementation
    fun __constructor__() {
        pagesCount = 0
        closed = false
        currentPage = null
    }

    @Implementation
    fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        check(!closed) { "document is closed!" }
        val page = Shadow.newInstanceOf(PdfDocument.Page::class.java)
        val bmp = Bitmap.createBitmap(
            pageInfo.pageWidth.coerceAtLeast(1),
            pageInfo.pageHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        PdfDocument.Page::class.java.declaredFields.forEach { field ->
            field.isAccessible = true
            when (field.type) {
                Canvas::class.java -> field.set(page, canvas)
                PdfDocument.PageInfo::class.java -> field.set(page, pageInfo)
            }
        }
        currentPage = page
        pagesCount++
        return page
    }

    @Implementation
    fun finishPage(page: PdfDocument.Page?) {
        check(!closed) { "document is closed!" }
        if (page != null) {
            PdfDocument.Page::class.java.declaredFields.forEach { field ->
                field.isAccessible = true
                if (field.type == Canvas::class.java) {
                    field.set(page, null)
                }
            }
        }
        currentPage = null
    }

    @Implementation
    fun writeTo(out: OutputStream) {
        check(!closed) { "document is closed!" }
        lastWrittenPageCount = pagesCount.coerceAtLeast(1)
        val body = buildString {
            append("%PDF-1.4\n")
            append("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n")
            append("2 0 obj << /Type /Pages /Count ${pagesCount.coerceAtLeast(1)} >> endobj\n")
            repeat(pagesCount.coerceAtLeast(1)) { idx ->
                append("${idx + 3} 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >> endobj\n")
            }
            append("trailer << /Root 1 0 R >>\n%%EOF\n")
        }.padEnd(600, ' ')
        out.write(body.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    @Implementation
    fun close() {
        closed = true
    }

    companion object {
        @Volatile
        var lastWrittenPageCount: Int = 1
    }
}

@Implements(PdfRenderer::class)
class TestShadowPdfRenderer {
    @Implementation
    fun __constructor__(input: ParcelFileDescriptor) {
        // Accept ParcelFileDescriptor in host Robolectric JVM tests
    }

    @Implementation
    fun getPageCount(): Int = TestShadowPdfDocument.lastWrittenPageCount.coerceAtLeast(1)

    @Implementation
    fun close() {
        // No-op
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    shadows = [TestShadowPdfDocument::class, TestShadowPdfRenderer::class]
)
class PdfAndStarterTest {

    @Test
    fun wordToPdfCreatesValidPdfFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val outFile = File(context.cacheDir, "test_word_to_pdf.pdf")
        if (outFile.exists()) outFile.delete()

        val blocks = listOf(
            WordBlock(type = WordBlockType.HEADING_1, text = "Test Heading", isBold = true),
            WordBlock(type = WordBlockType.PARAGRAPH, text = "Paragraph body text for Virtual University notes."),
            WordBlock(type = WordBlockType.BULLET, text = "Bullet point item")
        )

        PdfEngine.convertWordToPdf("UnitTestDoc", blocks, outFile, PdfEngine.PageSize.A4)
        assertTrue(outFile.exists())
        assertTrue(outFile.length() > 500)
        assertEquals(1, PdfEngine.getPdfPageCount(outFile))
        outFile.delete()
    }

    @Test
    fun picturesToPdfBuildsMultiPagePdfAfterRearrange() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bmp1 = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
        val bmp2 = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
        val p1 = ImageProcessor.saveBitmapToFile(context, bmp1, "p1")
        val p2 = ImageProcessor.saveBitmapToFile(context, bmp2, "p2")

        val items = listOf(
            PictureDraftItem(originalPath = p2, processedPath = p2),
            PictureDraftItem(originalPath = p1, processedPath = p1)
        )
        val outPdf = File(context.cacheDir, "pics_test.pdf")
        PdfEngine.buildPdfFromPictures(items, outPdf, PdfEngine.PageSize.A4, withMargins = true)

        assertTrue(outPdf.exists())
        assertTrue(outPdf.length() > 500)
        assertEquals(2, PdfEngine.getPdfPageCount(outPdf))
        outPdf.delete()
    }

    @Test
    fun imageProcessorFiltersAndPerspectiveCropWorkCleanly() {
        val src = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        val cropped = ImageProcessor.cropPerspective(src, CropQuad())
        assertTrue(cropped.width >= 200 && cropped.height >= 200)

        FilterType.values().forEach { filter ->
            val filtered = ImageProcessor.applyFilter(cropped, filter)
            assertEquals(cropped.width, filtered.width)
            assertEquals(cropped.height, filtered.height)
        }
    }

    @Test
    fun starterSeederCreatesRealPdfWordAndScanInRoom() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = DocumentRepository(context)
        StarterDocumentSeeder.seedRealStarterDocuments(context, repo)
        StarterDocumentSeeder.seedOfficeStarterDocuments(context, repo)

        val docs = repo.documents.first()
        assertEquals(5, docs.size)
        assertTrue(docs.any { it.kind == DocKind.PDF && it.filePath != null && File(it.filePath!!).exists() })
        assertTrue(docs.any { it.kind == DocKind.WORD && it.filePath != null && File(it.filePath!!).exists() })
        assertTrue(docs.any { it.kind == DocKind.EXCEL && it.filePath != null && File(it.filePath!!).exists() })
        assertTrue(docs.any { it.kind == DocKind.SLIDES && it.filePath != null && File(it.filePath!!).exists() })
        assertTrue(docs.any { it.kind == DocKind.SCAN && it.pages.isNotEmpty() })
    }

    @Test
    fun excelXlsxRoundTripCsvAndPdfExportWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val xlsxFile = File(context.cacheDir, "grades_test.xlsx")
        val sheets = listOf(
            ExcelSheetData(
                name = "Grades",
                rows = listOf(
                    listOf("Course", "Score", "Grade"),
                    listOf("CS101", "92", "A"),
                    listOf("CS301", "96", "A+")
                )
            ),
            ExcelSheetData(
                name = "Schedule",
                rows = listOf(
                    listOf("Day", "Topic"),
                    listOf("Monday", "Trees")
                )
            )
        )

        ExcelEngine.writeXlsxFile(sheets, xlsxFile)
        assertTrue(xlsxFile.exists())

        val parsed = ExcelEngine.readSpreadsheetFile(xlsxFile)
        assertEquals(2, parsed.size)
        assertEquals("Grades", parsed[0].name)
        assertEquals("92", parsed[0].rows[1][1])
        assertEquals("Schedule", parsed[1].name)

        val outPdf = File(context.cacheDir, "grades_test.pdf")
        ExcelEngine.exportSheetToPdf("Grades Test", parsed[0], outPdf)
        assertTrue(outPdf.exists() && outPdf.length() > 500)

        val csvFile = File(context.cacheDir, "data.csv")
        csvFile.writeText("Name,Marks\n\"Ali, Khan\",95\nSara,98\n")
        val csvParsed = ExcelEngine.readSpreadsheetFile(csvFile)
        assertEquals(1, csvParsed.size)
        assertEquals("Ali, Khan", csvParsed[0].rows[1][0])

        xlsxFile.delete()
        outPdf.delete()
        csvFile.delete()
    }

    @Test
    fun slidesPptxRoundTripAndWordConversionWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pptxFile = File(context.cacheDir, "lecture_test.pptx")
        val slides = listOf(
            SlideItem(1, "Intro to OOP", listOf("Classes", "Objects"), "Key exam topic"),
            SlideItem(2, "Polymorphism", listOf("Virtual functions"))
        )

        SlidesEngine.writePptxFile(slides, pptxFile)
        assertTrue(pptxFile.exists())

        val parsed = SlidesEngine.readPptxFile(pptxFile)
        assertEquals(2, parsed.size)
        assertEquals("Intro to OOP", parsed[0].title)
        assertEquals("Key exam topic", parsed[0].notes)
        assertEquals("Polymorphism", parsed[1].title)

        val wordBlocks = SlidesEngine.slidesToWordBlocks("CS201", parsed)
        assertTrue(wordBlocks.any { it.type == WordBlockType.HEADING_2 && it.text.contains("Intro to OOP") })
        assertTrue(wordBlocks.any { it.type == WordBlockType.BULLET && it.text == "Virtual functions" })

        pptxFile.delete()
    }
}

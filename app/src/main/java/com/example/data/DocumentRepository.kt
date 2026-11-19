package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.db.AppDatabase
import com.example.data.db.DocumentEntity
import com.example.model.CropQuad
import com.example.model.DocKind
import com.example.model.Document
import com.example.model.DocumentCategory
import com.example.model.ExcelSheetData
import com.example.model.FilterType
import com.example.model.ScanPage
import com.example.model.SlideItem
import com.example.model.WordBlock
import com.example.model.WordBlockType
import com.example.util.CodeReaderEngine
import com.example.util.DocxEngine
import com.example.util.ExcelEngine
import com.example.util.FileTemplates
import com.example.util.ImageProcessor
import com.example.util.PdfEngine
import com.example.util.SlidesEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class DocumentRepository(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).documentDao()

    val documents: Flow<List<Document>> = dao.getAllDocuments().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getDocumentById(id: String): Document? = withContext(Dispatchers.IO) {
        dao.getDocumentById(id)?.toDomain()
    }

    suspend fun saveDocument(doc: Document) = withContext(Dispatchers.IO) {
        dao.insertOrUpdate(DocumentEntity.fromDomain(doc))
    }

    suspend fun updateLastReadPage(docId: String, page: Int) = withContext(Dispatchers.IO) {
        dao.updateLastReadPage(docId, page)
    }

    suspend fun deleteDocument(docId: String) = withContext(Dispatchers.IO) {
        dao.deleteById(docId)
        PdfAnnotationStore.delete(context, docId)
    }

    suspend fun toggleStar(docId: String): Document? = withContext(Dispatchers.IO) {
        val existing = dao.getDocumentById(docId)?.toDomain() ?: return@withContext null
        val updated = existing.copy(isStarred = !existing.isStarred, updatedAt = System.currentTimeMillis())
        dao.insertOrUpdate(DocumentEntity.fromDomain(updated))
        updated
    }

    suspend fun renameDocument(docId: String, newTitle: String, subjectTag: String? = null): Document? =
        withContext(Dispatchers.IO) {
            val existing = dao.getDocumentById(docId)?.toDomain() ?: return@withContext null
            val updated = existing.copy(
                title = newTitle,
                subjectTag = subjectTag ?: existing.subjectTag,
                updatedAt = System.currentTimeMillis()
            )
            dao.insertOrUpdate(DocumentEntity.fromDomain(updated))
            updated
        }

    suspend fun createScanDocument(
        title: String,
        subjectTag: String,
        bitmap: Bitmap,
        category: DocumentCategory = DocumentCategory.SCANS,
        filter: FilterType = FilterType.MAGIC_COLOR,
        cropQuad: CropQuad = CropQuad()
    ): Document = withContext(Dispatchers.IO) {
        val origPath = ImageProcessor.saveBitmapToFile(context, bitmap, "scan_raw")
        val cropped = ImageProcessor.cropPerspective(bitmap, cropQuad)
        val filtered = ImageProcessor.applyFilter(cropped, filter)
        val procPath = ImageProcessor.saveBitmapToFile(context, filtered, "scan_proc")

        val page = ScanPage(
            pageNumber = 1,
            originalImagePath = origPath,
            processedImagePath = procPath,
            filterType = filter,
            cropQuad = cropQuad
        )

        val doc = Document(
            title = title.ifBlank { "Scan ${timestampLabel()}" },
            kind = DocKind.SCAN,
            subjectTag = subjectTag,
            totalPages = 1,
            category = category,
            pages = listOf(page)
        )
        dao.insertOrUpdate(DocumentEntity.fromDomain(doc))
        doc
    }

    suspend fun addPageToScanDocument(
        documentId: String,
        bitmap: Bitmap,
        filter: FilterType = FilterType.MAGIC_COLOR,
        cropQuad: CropQuad = CropQuad()
    ): Document? = withContext(Dispatchers.IO) {
        val existing = dao.getDocumentById(documentId)?.toDomain() ?: return@withContext null
        val origPath = ImageProcessor.saveBitmapToFile(context, bitmap, "scan_raw")
        val cropped = ImageProcessor.cropPerspective(bitmap, cropQuad)
        val filtered = ImageProcessor.applyFilter(cropped, filter)
        val procPath = ImageProcessor.saveBitmapToFile(context, filtered, "scan_proc")

        val newPage = ScanPage(
            pageNumber = existing.pages.size + 1,
            originalImagePath = origPath,
            processedImagePath = procPath,
            filterType = filter,
            cropQuad = cropQuad
        )

        val updated = existing.copy(
            pages = existing.pages + newPage,
            totalPages = existing.pages.size + 1,
            updatedAt = System.currentTimeMillis()
        )
        dao.insertOrUpdate(DocumentEntity.fromDomain(updated))
        updated
    }

    suspend fun updateScanPage(
        documentId: String,
        pageId: String,
        newFilter: FilterType? = null,
        newCropQuad: CropQuad? = null,
        rotateDegrees: Int = 0,
        signatureBitmap: Bitmap? = null,
        watermarkText: String? = null
    ): Document? = withContext(Dispatchers.IO) {
        val doc = dao.getDocumentById(documentId)?.toDomain() ?: return@withContext null
        val updatedPages = doc.pages.map { page ->
            if (page.id == pageId) {
                val origBmp = ImageProcessor.loadBitmap(page.originalImagePath) ?: return@map page
                val finalQuad = newCropQuad ?: page.cropQuad
                val finalFilter = newFilter ?: page.filterType
                val finalRot = (page.rotationDegrees + rotateDegrees) % 360

                var processed = ImageProcessor.cropPerspective(origBmp, finalQuad)
                if (finalRot > 0) processed = ImageProcessor.rotateBitmap(processed, finalRot)
                processed = ImageProcessor.applyFilter(processed, finalFilter)
                if (signatureBitmap != null) processed = ImageProcessor.overlaySignature(processed, signatureBitmap)
                if (!watermarkText.isNullOrBlank()) processed = ImageProcessor.addWatermark(processed, watermarkText)

                val newProcPath = ImageProcessor.saveBitmapToFile(context, processed, "scan_upd")
                page.copy(
                    processedImagePath = newProcPath,
                    filterType = finalFilter,
                    cropQuad = finalQuad,
                    rotationDegrees = finalRot,
                    watermarkText = watermarkText ?: page.watermarkText
                )
            } else page
        }

        val updated = doc.copy(pages = updatedPages, updatedAt = System.currentTimeMillis())
        dao.insertOrUpdate(DocumentEntity.fromDomain(updated))
        updated
    }

    suspend fun deleteScanPage(documentId: String, pageId: String): Document? = withContext(Dispatchers.IO) {
        val doc = dao.getDocumentById(documentId)?.toDomain() ?: return@withContext null
        val remaining = doc.pages.filter { it.id != pageId }.mapIndexed { i, p -> p.copy(pageNumber = i + 1) }
        if (remaining.isEmpty()) {
            dao.deleteById(documentId)
            null
        } else {
            val updated = doc.copy(
                pages = remaining,
                totalPages = remaining.size,
                updatedAt = System.currentTimeMillis()
            )
            dao.insertOrUpdate(DocumentEntity.fromDomain(updated))
            updated
        }
    }

    suspend fun importPdfFromUri(uri: Uri, subjectTag: String = ""): Document? = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: "Imported_Document.pdf"
        val pdfFile = PdfEngine.importPdfFromUri(context, uri, displayName) ?: return@withContext null
        val pageCount = PdfEngine.getPdfPageCount(pdfFile).coerceAtLeast(1)

        // Generate a crisp thumbnail from page 0 for home library preview
        val thumbBmp = PdfEngine.renderPdfPage(pdfFile, 0, targetWidthPx = 480)
        val thumbPath = thumbBmp?.let {
            val p = ImageProcessor.saveBitmapToFile(context, it, "pdf_thumb")
            it.recycle()
            p
        }

        val cleanTitle = displayName.substringBeforeLast(".").ifBlank { "PDF Document" }
        val previewPages = if (thumbPath != null) {
            listOf(
                ScanPage(
                    pageNumber = 1,
                    originalImagePath = thumbPath,
                    processedImagePath = thumbPath,
                    filterType = FilterType.ORIGINAL
                )
            )
        } else emptyList()

        val doc = Document(
            title = cleanTitle,
            kind = DocKind.PDF,
            subjectTag = subjectTag,
            filePath = pdfFile.absolutePath,
            totalPages = pageCount,
            category = DocumentCategory.HANDOUTS,
            pages = previewPages
        )
        dao.insertOrUpdate(DocumentEntity.fromDomain(doc))
        doc
    }

    suspend fun importWordFromUri(uri: Uri, subjectTag: String = ""): Pair<Document, List<WordBlock>>? =
        withContext(Dispatchers.IO) {
            val displayName = queryDisplayName(uri) ?: "Notes.docx"
            val blocks = DocxEngine.readDocumentFromUri(context, uri, displayName)
            val cleanTitle = displayName.substringBeforeLast(".").ifBlank { "Study Notes" }

            val wordDir = File(context.filesDir, "word_docs")
            if (!wordDir.exists()) wordDir.mkdirs()
            val safeFile = File(wordDir, "${UUID.randomUUID()}_${cleanTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.docx")
            val finalBlocks = if (blocks.isEmpty()) {
                listOf(WordBlock(type = WordBlockType.HEADING_1, text = cleanTitle, isBold = true))
            } else blocks
            DocxEngine.writeDocxFile(finalBlocks, safeFile)

            val doc = Document(
                title = cleanTitle,
                kind = DocKind.WORD,
                subjectTag = subjectTag,
                filePath = safeFile.absolutePath,
                totalPages = 1,
                category = DocumentCategory.NOTES
            )
            dao.insertOrUpdate(DocumentEntity.fromDomain(doc))
            doc to finalBlocks
        }

    suspend fun importExcelFromUri(uri: Uri, subjectTag: String = ""): Pair<Document, List<ExcelSheetData>>? =
        withContext(Dispatchers.IO) {
            val displayName = queryDisplayName(uri) ?: "Spreadsheet.xlsx"
            val file = ExcelEngine.importSpreadsheetFromUri(context, uri, displayName) ?: return@withContext null
            val sheets = ExcelEngine.readSpreadsheetFile(file)
            val cleanTitle = displayName.substringBeforeLast(".").ifBlank { "Spreadsheet" }
            val doc = Document(
                title = cleanTitle,
                kind = DocKind.EXCEL,
                subjectTag = subjectTag,
                filePath = file.absolutePath,
                totalPages = sheets.size.coerceAtLeast(1),
                category = DocumentCategory.OTHER
            )
            dao.insertOrUpdate(DocumentEntity.fromDomain(doc))
            doc to sheets
        }

    suspend fun importSlidesFromUri(uri: Uri, subjectTag: String = ""): Pair<Document, List<SlideItem>>? =
        withContext(Dispatchers.IO) {
            val displayName = queryDisplayName(uri) ?: "Presentation.pptx"
            val file = SlidesEngine.importPptxFromUri(context, uri, displayName) ?: return@withContext null
            val slides = SlidesEngine.readPptxFile(file)
            val cleanTitle = displayName.substringBeforeLast(".").ifBlank { "Slides" }
            val doc = Document(
                title = cleanTitle,
                kind = DocKind.SLIDES,
                subjectTag = subjectTag,
                filePath = file.absolutePath,
                totalPages = slides.size.coerceAtLeast(1),
                category = DocumentCategory.HANDOUTS
            )
            dao.insertOrUpdate(DocumentEntity.fromDomain(doc))
            doc to slides
        }

    suspend fun importCodeFromUri(uri: Uri, subjectTag: String = ""): Pair<Document, List<String>>? =
        withContext(Dispatchers.IO) {
            val displayName = queryDisplayName(uri) ?: "snippet.txt"
            val file = CodeReaderEngine.importCodeFileFromUri(context, uri, displayName) ?: return@withContext null
            val lines = CodeReaderEngine.readLines(file)
            val doc = Document(
                title = displayName,
                kind = DocKind.CODE,
                subjectTag = subjectTag,
                filePath = file.absolutePath,
                totalPages = 1,
                category = DocumentCategory.NOTES
            )
            dao.insertOrUpdate(DocumentEntity.fromDomain(doc))
            doc to lines
        }

    suspend fun saveSpreadsheetDocument(doc: Document, sheets: List<ExcelSheetData>): Document =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "spreadsheets")
            if (!dir.exists()) dir.mkdirs()
            val destFile = doc.filePath?.let { File(it) } ?: File(dir, "${UUID.randomUUID()}_sheet.xlsx")
            ExcelEngine.saveSpreadsheetFile(destFile, sheets)
            val updated = doc.copy(
                filePath = destFile.absolutePath,
                totalPages = sheets.size.coerceAtLeast(1),
                updatedAt = System.currentTimeMillis()
            )
            dao.insertOrUpdate(DocumentEntity.fromDomain(updated))
            updated
        }

    suspend fun saveSlidesDocument(doc: Document, slides: List<SlideItem>): Document =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "slides")
            if (!dir.exists()) dir.mkdirs()
            val destFile = doc.filePath?.let { File(it) } ?: File(dir, "${UUID.randomUUID()}_deck.pptx")
            SlidesEngine.writePptxFile(slides, destFile)
            val updated = doc.copy(
                filePath = destFile.absolutePath,
                totalPages = slides.size.coerceAtLeast(1),
                updatedAt = System.currentTimeMillis()
            )
            dao.insertOrUpdate(DocumentEntity.fromDomain(updated))
            updated
        }

    suspend fun createScanDocumentFromFiles(
        title: String,
        subjectTag: String,
        imageFiles: List<File>,
        category: DocumentCategory = DocumentCategory.SCANS
    ): Document = withContext(Dispatchers.IO) {
        val pages = imageFiles.mapIndexed { idx, file ->
            ScanPage(
                pageNumber = idx + 1,
                originalImagePath = file.absolutePath,
                processedImagePath = file.absolutePath,
                filterType = FilterType.ORIGINAL
            )
        }
        val doc = Document(
            title = title.ifBlank { "Pages ${timestampLabel()}" },
            kind = DocKind.SCAN,
            subjectTag = subjectTag,
            totalPages = pages.size.coerceAtLeast(1),
            category = category,
            pages = pages
        )
        dao.insertOrUpdate(DocumentEntity.fromDomain(doc))
        doc
    }

    suspend fun createBlankSpreadsheetDocument(
        title: String,
        subjectTag: String
    ): Pair<Document, List<ExcelSheetData>> = withContext(Dispatchers.IO) {
        val cleanTitle = title.ifBlank { "Sheet ${timestampLabel()}" }
        val initialSheets = listOf(
            ExcelSheetData(
                name = "Sheet1",
                rows = listOf(
                    listOf("Item", "Category", "Value", "Notes"),
                    listOf("Sample 1", "General", "100", "Tap any cell to edit"),
                    listOf("Sample 2", "General", "200", "")
                )
            )
        )
        val dir = File(context.filesDir, "spreadsheets")
        if (!dir.exists()) dir.mkdirs()
        val destFile = File(dir, "${UUID.randomUUID()}_sheet.xlsx")
        ExcelEngine.writeXlsxFile(initialSheets, destFile)

        val doc = Document(
            title = cleanTitle,
            kind = DocKind.EXCEL,
            subjectTag = subjectTag,
            filePath = destFile.absolutePath,
            totalPages = 1,
            category = DocumentCategory.OTHER
        )
        dao.insertOrUpdate(DocumentEntity.fromDomain(doc))
        doc to initialSheets
    }

    suspend fun createBlankWordDocument(title: String, subjectTag: String): Pair<Document, List<WordBlock>> =
        withContext(Dispatchers.IO) {
            val cleanTitle = title.ifBlank { "Study Notes ${timestampLabel()}" }
            val initialBlocks = listOf(
                WordBlock(type = WordBlockType.HEADING_1, text = cleanTitle, isBold = true),
                WordBlock(
                    type = WordBlockType.PARAGRAPH,
                    text = if (subjectTag.isNotBlank()) "Course: $subjectTag — Start writing or paste your lecture notes here."
                    else "Start typing your study notes here. Use Clean Tools to format messy text automatically."
                )
            )

            val wordDir = File(context.filesDir, "word_docs")
            if (!wordDir.exists()) wordDir.mkdirs()
            val destFile = File(wordDir, "${UUID.randomUUID()}_note.docx")
            DocxEngine.writeDocxFile(initialBlocks, destFile)

            val doc = Document(
                title = cleanTitle,
                kind = DocKind.WORD,
                subjectTag = subjectTag,
                filePath = destFile.absolutePath,
                totalPages = 1,
                category = DocumentCategory.NOTES
            )
            dao.insertOrUpdate(DocumentEntity.fromDomain(doc))
            doc to initialBlocks
        }

    suspend fun createBlankCodeFileDocument(
        title: String,
        ext: String,
        subjectTag: String
    ): Pair<Document, String> = withContext(Dispatchers.IO) {
        val cleanExt = ext.trim('.').lowercase().ifBlank { "txt" }
        val cleanTitle = title.ifBlank { "New ${cleanExt.uppercase()} ${timestampLabel()}" }
        val content = FileTemplates.starterContent(cleanExt, cleanTitle, subjectTag)

        val dir = File(context.filesDir, "code_files")
        if (!dir.exists()) dir.mkdirs()
        val safeBase = cleanTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(60).ifBlank { "file" }
        val destFile = File(dir, "${UUID.randomUUID()}_$safeBase.$cleanExt")
        destFile.writeText(content, Charsets.UTF_8)

        val doc = Document(
            title = cleanTitle,
            kind = DocKind.CODE,
            subjectTag = subjectTag,
            filePath = destFile.absolutePath,
            totalPages = 1,
            category = DocumentCategory.OTHER
        )
        dao.insertOrUpdate(DocumentEntity.fromDomain(doc))
        doc to content
    }

    suspend fun saveCodeFileDocument(doc: Document, content: String): Document =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "code_files")
            if (!dir.exists()) dir.mkdirs()
            val file = doc.filePath?.let { File(it) }
                ?: File(dir, "${doc.id}_${System.currentTimeMillis()}.txt")
            file.writeText(content, Charsets.UTF_8)

            val updated = doc.copy(
                filePath = file.absolutePath,
                updatedAt = System.currentTimeMillis()
            )
            dao.insertOrUpdate(DocumentEntity.fromDomain(updated))
            updated
        }

    suspend fun createBlankSlidesDocument(
        title: String,
        subjectTag: String
    ): Pair<Document, List<SlideItem>> = withContext(Dispatchers.IO) {
        val cleanTitle = title.ifBlank { "Presentation ${timestampLabel()}" }
        val initialSlides = listOf(
            SlideItem(
                number = 1,
                title = cleanTitle,
                bodyLines = listOf(
                    "Point one — first idea of your lecture",
                    "Point two — second key idea",
                    "Point three — summary / question"
                ),
                notes = if (subjectTag.isNotBlank()) "Course: $subjectTag — speaker notes go here."
                else "Speaker notes: explain each bullet with a short example."
            )
        )
        val dir = File(context.filesDir, "slides")
        if (!dir.exists()) dir.mkdirs()
        val safeBase = cleanTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(60).ifBlank { "deck" }
        val destFile = File(dir, "${UUID.randomUUID()}_$safeBase.pptx")
        SlidesEngine.writePptxFile(initialSlides, destFile)

        val doc = Document(
            title = cleanTitle,
            kind = DocKind.SLIDES,
            subjectTag = subjectTag,
            filePath = destFile.absolutePath,
            totalPages = initialSlides.size,
            category = DocumentCategory.OTHER
        )
        dao.insertOrUpdate(DocumentEntity.fromDomain(doc))
        doc to initialSlides
    }

    suspend fun saveWordDocument(doc: Document, blocks: List<WordBlock>): Document = withContext(Dispatchers.IO) {
        val wordDir = File(context.filesDir, "word_docs")
        if (!wordDir.exists()) wordDir.mkdirs()
        val destFile = doc.filePath?.let { File(it) } ?: File(wordDir, "${UUID.randomUUID()}_note.docx")
        DocxEngine.writeDocxFile(blocks, destFile)

        val updated = doc.copy(
            filePath = destFile.absolutePath,
            updatedAt = System.currentTimeMillis()
        )
        dao.insertOrUpdate(DocumentEntity.fromDomain(updated))
        updated
    }

    suspend fun registerGeneratedPdf(
        title: String,
        pdfFile: File,
        subjectTag: String,
        category: DocumentCategory
    ): Document = withContext(Dispatchers.IO) {
        val pageCount = PdfEngine.getPdfPageCount(pdfFile).coerceAtLeast(1)
        val thumbBmp = PdfEngine.renderPdfPage(pdfFile, 0, targetWidthPx = 480)
        val thumbPath = thumbBmp?.let {
            val p = ImageProcessor.saveBitmapToFile(context, it, "pdf_thumb")
            it.recycle()
            p
        }
        val previewPages = if (thumbPath != null) {
            listOf(
                ScanPage(
                    pageNumber = 1,
                    originalImagePath = thumbPath,
                    processedImagePath = thumbPath,
                    filterType = FilterType.ORIGINAL
                )
            )
        } else emptyList()

        val doc = Document(
            title = title,
            kind = DocKind.PDF,
            subjectTag = subjectTag,
            filePath = pdfFile.absolutePath,
            totalPages = pageCount,
            category = category,
            pages = previewPages
        )
        dao.insertOrUpdate(DocumentEntity.fromDomain(doc))
        doc
    }

    fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            }
        }.getOrNull()
    }

    private fun timestampLabel(): String {
        return java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.US).format(java.util.Date())
    }
}

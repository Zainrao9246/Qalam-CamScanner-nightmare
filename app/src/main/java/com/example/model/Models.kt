package com.example.model

import androidx.compose.ui.geometry.Offset
import java.util.UUID

enum class DocKind(val badge: String) {
    SCAN("SCAN"),
    PDF("PDF"),
    WORD("DOCX"),
    EXCEL("XLSX"),
    SLIDES("PPTX"),
    CODE("CODE")
}

enum class CameraCaptureMode(val label: String) {
    DOCUMENT("Document"),
    ID_CARD("ID Card"),
    BOOK_SPLIT("Book 2-Page")
}

enum class PdfEyeMode(val label: String) {
    DAY("Day"),
    SEPIA("Sepia"),
    NIGHT("Night")
}

enum class PdfAnnotationTool(val label: String) {
    READ("Read"),
    HIGHLIGHT("Highlight"),
    PEN("Draw"),
    UNDERLINE("Underline"),
    COMMENT("Comment"),
    ERASER("Eraser")
}

enum class PdfStrokeType {
    HIGHLIGHT,
    PEN,
    UNDERLINE
}

data class NormPoint(
    val x: Float,
    val y: Float
)

data class PdfStroke(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val type: PdfStrokeType,
    val colorArgb: Long,
    val widthRatio: Float,
    val points: List<NormPoint>
)

data class PdfCommentPin(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val xRatio: Float,
    val yRatio: Float,
    val title: String,
    val body: String,
    val colorArgb: Long = 0xFFFFB300L,
    val createdAt: Long = System.currentTimeMillis()
)

data class PdfDocumentAnnotations(
    val docId: String,
    val bookmarkedPages: Set<Int> = emptySet(),
    val pageNotes: Map<Int, String> = emptyMap(),
    val strokes: List<PdfStroke> = emptyList(),
    val comments: List<PdfCommentPin> = emptyList()
)

data class ExcelSheetData(
    val name: String,
    val rows: List<List<String>>
)

data class SlideItem(
    val number: Int,
    val title: String,
    val bodyLines: List<String>,
    val notes: String = ""
)

enum class FilterType(val displayName: String, val description: String) {
    ORIGINAL("Original", "Original captured photo"),
    MAGIC_COLOR("Magic Color", "Whitens paper background, sharpens text"),
    BW_HIGH("B&W 1", "High-contrast clean document print"),
    BW_SOFT("B&W 2", "Softer binarization for receipts & pencil"),
    GRAYSCALE("Grayscale", "Smooth monochrome scan"),
    LIGHTEN("Lighten", "Removes shadows and dim lighting")
}

enum class DocumentCategory(val label: String) {
    ALL("All"),
    NOTES("My Notes"),
    HANDOUTS("Handouts"),
    ASSIGNMENTS("Assignments"),
    SCANS("Scans"),
    OTHER("Other")
}

data class CropQuad(
    val topLeft: Offset = Offset(0.05f, 0.05f),
    val topRight: Offset = Offset(0.95f, 0.05f),
    val bottomRight: Offset = Offset(0.95f, 0.95f),
    val bottomLeft: Offset = Offset(0.05f, 0.95f)
)

data class ScanPage(
    val id: String = UUID.randomUUID().toString(),
    val pageNumber: Int = 1,
    val originalImagePath: String,
    val processedImagePath: String,
    val filterType: FilterType = FilterType.MAGIC_COLOR,
    val rotationDegrees: Int = 0,
    val cropQuad: CropQuad = CropQuad(),
    val ocrText: String? = null,
    val watermarkText: String? = null
)

enum class WordBlockType {
    HEADING_1,
    HEADING_2,
    PARAGRAPH,
    BULLET
}

data class WordBlock(
    val id: String = UUID.randomUUID().toString(),
    val type: WordBlockType = WordBlockType.PARAGRAPH,
    val text: String = "",
    val isBold: Boolean = false,
    val isItalic: Boolean = false
)

data class PictureDraftItem(
    val id: String = UUID.randomUUID().toString(),
    val originalPath: String,
    val processedPath: String,
    val cropQuad: CropQuad = CropQuad(),
    val filterType: FilterType = FilterType.ORIGINAL,
    val rotationDegrees: Int = 0
)

data class Document(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val kind: DocKind = DocKind.SCAN,
    val subjectTag: String = "",
    val filePath: String? = null,
    val totalPages: Int = 1,
    val lastReadPage: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val category: DocumentCategory = DocumentCategory.ALL,
    val isStarred: Boolean = false,
    val pages: List<ScanPage> = emptyList()
) {
    val pageCount: Int
        get() = if (pages.isNotEmpty()) pages.size else totalPages

    val thumbnailPath: String?
        get() = pages.firstOrNull()?.processedImagePath ?: pages.firstOrNull()?.originalImagePath
}

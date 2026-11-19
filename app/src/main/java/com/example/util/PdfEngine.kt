package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.model.Document
import com.example.model.PdfEyeMode
import com.example.model.PictureDraftItem
import com.example.model.WordAlign
import com.example.model.WordBlock
import com.example.model.WordBlockType
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object PdfEngine {

    enum class PageSize(val displayName: String, val widthPt: Int, val heightPt: Int) {
        A4("A4", 595, 842),
        LETTER("US Letter", 612, 792),
        AUTO_FIT("Fit Image", 0, 0)
    }

    /**
     * Copies a PDF selected via Android SAF Uri into persistent app files directory.
     */
    fun importPdfFromUri(context: Context, uri: Uri, filenameHint: String): File? {
        val dir = File(context.filesDir, "pdfs")
        if (!dir.exists()) dir.mkdirs()
        val safeName = filenameHint.replace(Regex("[^a-zA-Z0-9._-]"), "_")
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

    /**
     * Returns actual page count of a PDF file using Android PdfRenderer.
     */
    fun getPdfPageCount(pdfFile: File): Int {
        if (!pdfFile.exists()) return 0
        return runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    renderer.pageCount
                }
            }
        }.getOrDefault(0)
    }

    /**
     * Renders a single page from [pdfFile] into a crisp [Bitmap].
     * Supports [PdfEyeMode.DAY], [PdfEyeMode.SEPIA], and [PdfEyeMode.NIGHT].
     */
    fun renderPdfPage(
        pdfFile: File,
        pageIndex: Int,
        targetWidthPx: Int = 1200,
        nightMode: Boolean = false,
        eyeMode: PdfEyeMode = if (nightMode) PdfEyeMode.NIGHT else PdfEyeMode.DAY
    ): Bitmap? {
        if (!pdfFile.exists()) return null
        return runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return null
                    renderer.openPage(pageIndex).use { page ->
                        val ratio = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                        val w = targetWidthPx.coerceIn(400, 2000)
                        val h = (w * ratio).toInt().coerceAtLeast(100)

                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        canvas.drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        when (eyeMode) {
                            PdfEyeMode.DAY -> bmp
                            PdfEyeMode.NIGHT -> applyReadingNightFilter(bmp)
                            PdfEyeMode.SEPIA -> applyReadingSepiaFilter(bmp)
                        }
                    }
                }
            }
        }.getOrNull()
    }

    private fun applyReadingSepiaFilter(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        // Warm paper sepia tint (#FBF2DC) reducing blue light
        val sepiaMatrix = ColorMatrix(
            floatArrayOf(
                0.96f, 0f, 0f, 0f, 4f,
                0f, 0.91f, 0f, 0f, 0f,
                0f, 0f, 0.78f, 0f, -4f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(sepiaMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private fun applyReadingNightFilter(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        // Invert colors + soft warm dark tint (#181C1B background, soft off-white text)
        val invertMatrix = ColorMatrix(
            floatArrayOf(
                -0.88f, 0f, 0f, 0f, 232f,
                0f, -0.88f, 0f, 0f, 234f,
                0f, 0f, -0.88f, 0f, 230f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(invertMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    /**
     * REAL PDF -> WORD (.docx):
     * Renders every page of [pdfFile], extracts structured text blocks via real ML Kit OCR,
     * cleans broken line breaks, and writes a real .docx file to [outputDocxFile].
     */
    suspend fun convertPdfToWord(
        pdfFile: File,
        outputDocxFile: File,
        onProgress: (currentPage: Int, totalPages: Int) -> Unit = { _, _ -> }
    ): Pair<File, List<WordBlock>> {
        val total = getPdfPageCount(pdfFile)
        val allBlocks = mutableListOf<WordBlock>()

        for (i in 0 until total) {
            onProgress(i + 1, total)
            val bmp = renderPdfPage(pdfFile, i, targetWidthPx = 1400, nightMode = false) ?: continue
            val pageBlocks = OcrEngine.extractStructuredBlocks(bmp)
            allBlocks.addAll(pageBlocks)
            bmp.recycle()
        }

        val cleanedBlocks = DocxEngine.mergeBrokenLineBreaks(
            DocxEngine.cleanWhitespace(allBlocks)
        )
        DocxEngine.writeDocxFile(cleanedBlocks, outputDocxFile)
        return outputDocxFile to cleanedBlocks
    }

    /**
     * REAL WORD (.docx) -> PDF:
     * Typesets [blocks] onto clean multi-page A4 or US Letter PDF pages with proper margins,
     * headings, bullet indentation, and automatic page breaks.
     */
    fun convertWordToPdf(
        title: String,
        blocks: List<WordBlock>,
        outputPdfFile: File,
        pageSize: PageSize = PageSize.A4
    ): File {
        val pageW = if (pageSize == PageSize.AUTO_FIT) PageSize.A4.widthPt else pageSize.widthPt
        val pageH = if (pageSize == PageSize.AUTO_FIT) PageSize.A4.heightPt else pageSize.heightPt
        val margin = 48f
        val contentWidth = (pageW - margin * 2).toInt()
        val maxY = pageH - margin - 24f

        val pdfDoc = PdfDocument()
        try {
            var pageNum = 1
            var currentPage = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            var canvas = currentPage.canvas
            canvas.drawColor(Color.WHITE)
            var cursorY = margin

            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                textSize = 9f
                textAlign = Paint.Align.RIGHT
            }

            fun finishAndStartNewPage() {
                canvas.drawText("$title  •  Page $pageNum", pageW - margin, pageH - 22f, footerPaint)
                pdfDoc.finishPage(currentPage)
                pageNum++
                currentPage = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                canvas = currentPage.canvas
                canvas.drawColor(Color.WHITE)
                cursorY = margin
            }

            var numberedCounter = 0

            for (block in blocks) {
                // --- Picture blocks: draw the image scaled to content width ---
                if (block.type == WordBlockType.PICTURE) {
                    val path = block.imagePath
                    if (path != null) {
                        val bmp = BitmapFactory.decodeFile(path)
                        if (bmp != null) {
                            val scale = contentWidth.toFloat() / bmp.width
                            val drawW = contentWidth.toFloat()
                            val drawH = (bmp.height * scale).coerceAtLeast(20f)
                            if (cursorY + drawH > maxY) {
                                finishAndStartNewPage()
                            }
                            canvas.save()
                            canvas.translate(margin, cursorY)
                            canvas.drawBitmap(
                                bmp,
                                null,
                                RectF(0f, 0f, drawW, drawH),
                                Paint(Paint.FILTER_BITMAP_FLAG)
                            )
                            canvas.restore()
                            cursorY += drawH + 10f
                            if (block.text.isNotBlank()) {
                                val caption = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = Color.GRAY
                                    textSize = 9f
                                    textAlign = Paint.Align.CENTER
                                }
                                if (cursorY + 14f > maxY) finishAndStartNewPage()
                                canvas.drawText(block.text, margin + contentWidth / 2f, cursorY + 10f, caption)
                                cursorY += 14f
                            }
                            bmp.recycle()
                        }
                    }
                    continue
                }

                // --- Table blocks: bordered grid with cell text ---
                if (block.type == WordBlockType.TABLE) {
                    val rows = block.tableRows
                    if (rows.isNotEmpty()) {
                        val cols = rows.maxOf { it.size }.coerceAtLeast(1)
                        val colW = contentWidth.toFloat() / cols
                        val rowH = 20f
                        val tableH = rows.size * rowH
                        if (cursorY + tableH > maxY) {
                            finishAndStartNewPage()
                        }
                        val borderPaint = Paint().apply {
                            style = Paint.Style.STROKE
                            strokeWidth = 1f
                            color = Color.rgb(150, 150, 150)
                        }
                        val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.rgb(30, 34, 34)
                            textSize = 10f
                        }
                        rows.forEachIndexed { rIdx, row ->
                            val y = cursorY + rIdx * rowH
                            repeat(cols) { c ->
                                val x = margin + c * colW
                                canvas.drawRect(x, y, x + colW, y + rowH, borderPaint)
                                val cellText = row.getOrNull(c) ?: ""
                                if (cellText.isNotBlank()) {
                                    canvas.save()
                                    canvas.clipRect(x + 2f, y, x + colW - 2f, y + rowH)
                                    canvas.drawText(cellText, x + 4f, y + 14f, cellPaint)
                                    canvas.restore()
                                }
                            }
                        }
                        cursorY += tableH + 10f
                    }
                    continue
                }

                if (block.text.isBlank()) {
                    cursorY += 10f
                    continue
                }

                if (block.type == WordBlockType.NUMBERED) numberedCounter++ else numberedCounter = 0

                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = when (block.type) {
                        WordBlockType.HEADING_1 -> Color.rgb(0, 105, 92)
                        WordBlockType.HEADING_2 -> Color.rgb(25, 50, 48)
                        else -> Color.rgb(30, 34, 34)
                    }
                    if (block.colorHex != null) {
                        try {
                            color = Color.parseColor("#${block.colorHex}")
                        } catch (_: IllegalArgumentException) {
                            // keep default color
                        }
                    }
                    textSize = when (block.type) {
                        WordBlockType.HEADING_1 -> 18f
                        WordBlockType.HEADING_2 -> 14f
                        else -> 11.5f
                    }
                    if (block.fontSizeSp != null) {
                        textSize = block.fontSizeSp * 0.72f
                    }
                    val style = when {
                        (block.isBold || block.type != WordBlockType.PARAGRAPH) && block.isItalic -> Typeface.BOLD_ITALIC
                        block.isBold || block.type == WordBlockType.HEADING_1 || block.type == WordBlockType.HEADING_2 -> Typeface.BOLD
                        block.isItalic -> Typeface.ITALIC
                        else -> Typeface.NORMAL
                    }
                    typeface = Typeface.create(Typeface.SERIF, style)
                    if (block.isUnderline) isUnderlineText = true
                    if (block.isStrikethrough) isStrikeThruText = true
                }

                val indent = if (block.type == WordBlockType.BULLET || block.type == WordBlockType.NUMBERED) 20 else 0
                val textToDraw = when (block.type) {
                    WordBlockType.BULLET -> "•  ${block.text}"
                    WordBlockType.NUMBERED -> "$numberedCounter.  ${block.text}"
                    else -> block.text
                }
                val availWidth = (contentWidth - indent).coerceAtLeast(100)

                val pdfAlignment = when (block.alignment) {
                    WordAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
                    WordAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                    WordAlign.JUSTIFY -> Layout.Alignment.ALIGN_NORMAL
                    WordAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
                }

                val staticLayout = StaticLayout.Builder
                    .obtain(textToDraw, 0, textToDraw.length, textPaint, availWidth)
                    .setAlignment(pdfAlignment)
                    .setLineSpacing(3f, 1.2f)
                    .setIncludePad(false)
                    .build()

                val spacingBefore = when (block.type) {
                    WordBlockType.HEADING_1 -> 10f
                    WordBlockType.HEADING_2 -> 6f
                    else -> 0f
                }
                val spacingAfter = when (block.type) {
                    WordBlockType.HEADING_1 -> 8f
                    WordBlockType.HEADING_2 -> 6f
                    WordBlockType.BULLET -> 5f
                    WordBlockType.NUMBERED -> 5f
                    WordBlockType.PARAGRAPH -> 8f
                    WordBlockType.PICTURE -> 6f
                    WordBlockType.TABLE -> 6f
                }

                cursorY += spacingBefore

                if (cursorY + staticLayout.height > maxY) {
                    finishAndStartNewPage()
                }

                canvas.save()
                canvas.translate(margin + indent, cursorY)
                staticLayout.draw(canvas)
                canvas.restore()

                cursorY += staticLayout.height + spacingAfter
            }

            canvas.drawText("$title  •  Page $pageNum", pageW - margin, pageH - 22f, footerPaint)
            pdfDoc.finishPage(currentPage)

            outputPdfFile.parentFile?.mkdirs()
            FileOutputStream(outputPdfFile).use { out ->
                pdfDoc.writeTo(out)
            }
        } finally {
            pdfDoc.close()
        }
        return outputPdfFile
    }

    /**
     * REAL PICTURES -> PDF (from rearranged [PictureDraftItem] list).
     */
    fun buildPdfFromPictures(
        items: List<PictureDraftItem>,
        outputPdfFile: File,
        pageSize: PageSize = PageSize.A4,
        withMargins: Boolean = true
    ): File {
        val pdfDoc = PdfDocument()
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            items.forEachIndexed { index, item ->
                val bitmap = BitmapFactory.decodeFile(item.processedPath)
                    ?: BitmapFactory.decodeFile(item.originalPath)
                if (bitmap != null) {
                    val pageW = if (pageSize == PageSize.AUTO_FIT) bitmap.width else pageSize.widthPt
                    val pageH = if (pageSize == PageSize.AUTO_FIT) bitmap.height else pageSize.heightPt

                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                    val page = pdfDoc.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)

                    val margin = if (pageSize == PageSize.AUTO_FIT || !withMargins) 0f else 24f
                    val availW = pageW - margin * 2
                    val availH = pageH - margin * 2

                    val imgRatio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
                    val pageRatio = availW / availH.coerceAtLeast(1f)

                    val drawW: Float
                    val drawH: Float
                    if (imgRatio > pageRatio) {
                        drawW = availW
                        drawH = availW / imgRatio
                    } else {
                        drawH = availH
                        drawW = availH * imgRatio
                    }

                    val left = margin + (availW - drawW) / 2f
                    val top = margin + (availH - drawH) / 2f
                    canvas.drawBitmap(bitmap, null, RectF(left, top, left + drawW, top + drawH), paint)

                    pdfDoc.finishPage(page)
                    bitmap.recycle()
                }
            }

            outputPdfFile.parentFile?.mkdirs()
            FileOutputStream(outputPdfFile).use { out ->
                pdfDoc.writeTo(out)
            }
        } finally {
            pdfDoc.close()
        }
        return outputPdfFile
    }

    /**
     * Exports a scanned [Document] to a persistent or shareable PDF file.
     */
    fun generatePdfFromScanDocument(
        context: Context,
        document: Document,
        pageSize: PageSize = PageSize.A4,
        includeWatermark: Boolean = false
    ): File {
        val exportDir = File(context.filesDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val cleanTitle = document.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "Scan" }
        val outputFile = File(exportDir, "${cleanTitle}_${UUID.randomUUID().toString().take(4)}.pdf")

        val pdfDoc = PdfDocument()
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 10f
                textAlign = Paint.Align.CENTER
            }

            document.pages.forEachIndexed { index, page ->
                val bitmap = BitmapFactory.decodeFile(page.processedImagePath)
                    ?: BitmapFactory.decodeFile(page.originalImagePath)

                if (bitmap != null) {
                    val pageW = if (pageSize == PageSize.AUTO_FIT) bitmap.width else pageSize.widthPt
                    val pageH = if (pageSize == PageSize.AUTO_FIT) bitmap.height else pageSize.heightPt

                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                    val pdfPage = pdfDoc.startPage(pageInfo)
                    val canvas = pdfPage.canvas
                    canvas.drawColor(Color.WHITE)

                    val margin = if (pageSize == PageSize.AUTO_FIT) 0f else 28f
                    val footerSpace = if (includeWatermark) 24f else 12f
                    val availW = pageW - (margin * 2)
                    val availH = pageH - (margin * 2) - footerSpace

                    val imgRatio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
                    val pageRatio = availW / availH.coerceAtLeast(1f)

                    val drawW: Float
                    val drawH: Float
                    if (imgRatio > pageRatio) {
                        drawW = availW
                        drawH = availW / imgRatio
                    } else {
                        drawH = availH
                        drawW = availH * imgRatio
                    }

                    val left = margin + (availW - drawW) / 2f
                    val top = margin + (availH - drawH) / 2f
                    canvas.drawBitmap(bitmap, null, RectF(left, top, left + drawW, top + drawH), paint)

                    if (includeWatermark) {
                        textPaint.textSize = 10f
                        textPaint.color = Color.rgb(120, 130, 130)
                        textPaint.textAlign = Paint.Align.CENTER
                        canvas.drawText("Scanned with Qalam", pageW / 2f, pageH - 12f, textPaint)
                    }

                    textPaint.textSize = 8f
                    textPaint.color = Color.GRAY
                    textPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("${index + 1} / ${document.pages.size}", pageW - margin, pageH - 12f, textPaint)

                    pdfDoc.finishPage(pdfPage)
                    bitmap.recycle()
                }
            }

            FileOutputStream(outputFile).use { out ->
                pdfDoc.writeTo(out)
            }
        } finally {
            pdfDoc.close()
        }
        return outputFile
    }

    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share $title"))
    }
}

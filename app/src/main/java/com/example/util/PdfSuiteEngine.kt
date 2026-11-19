package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.model.PdfDocumentAnnotations
import com.example.model.PdfStrokeType
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Advanced PDF Power Suite Engine:
 * - Merge multiple PDFs into one document
 * - Split, extract, reorder, delete, or rotate pages inside an existing PDF
 * - Compress PDF with High / Balanced / Smallest presets (exact before/after sizes)
 * - Add custom diagonal or footer watermarks to every page
 * - Flatten Highlights, Pen ink, Underlines, Sticky Comment Pins, and Study Notes Summary into a new PDF
 */
object PdfSuiteEngine {

    enum class CompressionLevel(
        val label: String,
        val targetWidthPx: Int,
        val jpegQuality: Int,
        val subtitle: String
    ) {
        HIGH_QUALITY("High Quality", 1400, 88, "Sharp text, ~30-45% smaller"),
        BALANCED("Balanced Study", 1050, 72, "Great readability, ~55-70% smaller"),
        SMALLEST("Smallest Size", 760, 52, "Best for quick WhatsApp / LMS upload")
    }

    data class PageTransformSpec(
        val sourcePageIndex: Int,
        val rotationDegrees: Int = 0
    )

    data class CompressionResult(
        val outputFile: File,
        val originalBytes: Long,
        val compressedBytes: Long,
        val pageCount: Int
    )

    fun mergePdfs(
        pdfFiles: List<File>,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): File {
        val totalPages = pdfFiles.sumOf { PdfEngine.getPdfPageCount(it) }.coerceAtLeast(1)
        var writtenPages = 0
        val outDoc = PdfDocument()
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            for (file in pdfFiles) {
                if (!file.exists()) continue
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        for (idx in 0 until renderer.pageCount) {
                            writtenPages++
                            onProgress(writtenPages, totalPages)
                            renderer.openPage(idx).use { srcPage ->
                                val w = srcPage.width.coerceAtLeast(300)
                                val h = srcPage.height.coerceAtLeast(300)
                                val scale = (1300f / w).coerceIn(1f, 2.5f)
                                val rw = (w * scale).toInt()
                                val rh = (h * scale).toInt()
                                val bmp = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                                val bCanvas = Canvas(bmp)
                                bCanvas.drawColor(Color.WHITE)
                                srcPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                val pageInfo = PdfDocument.PageInfo.Builder(w, h, writtenPages).create()
                                val outPage = outDoc.startPage(pageInfo)
                                outPage.canvas.drawColor(Color.WHITE)
                                outPage.canvas.drawBitmap(bmp, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
                                outDoc.finishPage(outPage)
                                bmp.recycle()
                            }
                        }
                    }
                }
            }
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { outDoc.writeTo(it) }
        } finally {
            outDoc.close()
        }
        return outputFile
    }

    fun splitOrReorderPdf(
        sourcePdf: File,
        pageSpecs: List<PageTransformSpec>,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): File {
        val outDoc = PdfDocument()
        val total = pageSpecs.size.coerceAtLeast(1)
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            ParcelFileDescriptor.open(sourcePdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    pageSpecs.forEachIndexed { outIdx, spec ->
                        if (spec.sourcePageIndex in 0 until renderer.pageCount) {
                            onProgress(outIdx + 1, total)
                            renderer.openPage(spec.sourcePageIndex).use { srcPage ->
                                val srcW = srcPage.width.coerceAtLeast(300)
                                val srcH = srcPage.height.coerceAtLeast(300)
                                val scale = (1300f / srcW).coerceIn(1f, 2.5f)
                                val rw = (srcW * scale).toInt()
                                val rh = (srcH * scale).toInt()
                                val rawBmp = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                                Canvas(rawBmp).drawColor(Color.WHITE)
                                srcPage.render(rawBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                val rot = ((spec.rotationDegrees % 360) + 360) % 360
                                val finalBmp = if (rot != 0) {
                                    val m = Matrix().apply { postRotate(rot.toFloat()) }
                                    val rotated = Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, m, true)
                                    rawBmp.recycle()
                                    rotated
                                } else {
                                    rawBmp
                                }

                                val swapDims = rot == 90 || rot == 270
                                val outW = if (swapDims) srcH else srcW
                                val outH = if (swapDims) srcW else srcH
                                val pageInfo = PdfDocument.PageInfo.Builder(outW, outH, outIdx + 1).create()
                                val outPage = outDoc.startPage(pageInfo)
                                outPage.canvas.drawColor(Color.WHITE)
                                outPage.canvas.drawBitmap(finalBmp, null, RectF(0f, 0f, outW.toFloat(), outH.toFloat()), paint)
                                outDoc.finishPage(outPage)
                                finalBmp.recycle()
                            }
                        }
                    }
                }
            }
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { outDoc.writeTo(it) }
        } finally {
            outDoc.close()
        }
        return outputFile
    }

    fun compressPdf(
        sourcePdf: File,
        level: CompressionLevel,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): CompressionResult {
        val originalBytes = sourcePdf.length()
        val outDoc = PdfDocument()
        var total = 0
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            ParcelFileDescriptor.open(sourcePdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    total = renderer.pageCount
                    for (idx in 0 until total) {
                        onProgress(idx + 1, total)
                        renderer.openPage(idx).use { srcPage ->
                            val ratio = srcPage.height.toFloat() / srcPage.width.coerceAtLeast(1)
                            val targetW = level.targetWidthPx.coerceAtLeast(400)
                            val targetH = (targetW * ratio).toInt().coerceAtLeast(200)
                            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.RGB_565)
                            Canvas(bmp).drawColor(Color.WHITE)
                            srcPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val jpegStream = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, level.jpegQuality, jpegStream)
                            bmp.recycle()

                            val compressedBytes = jpegStream.toByteArray()
                            val decoded = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
                            val pageW = srcPage.width.coerceAtLeast(300)
                            val pageH = srcPage.height.coerceAtLeast(300)
                            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
                            val outPage = outDoc.startPage(pageInfo)
                            if (decoded != null) {
                                outPage.canvas.drawBitmap(decoded, null, RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()), paint)
                                decoded.recycle()
                            }
                            outDoc.finishPage(outPage)
                        }
                    }
                }
            }
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { outDoc.writeTo(it) }
        } finally {
            outDoc.close()
        }
        return CompressionResult(
            outputFile = outputFile,
            originalBytes = originalBytes,
            compressedBytes = outputFile.length(),
            pageCount = total
        )
    }

    fun watermarkPdf(
        sourcePdf: File,
        watermarkText: String,
        diagonal: Boolean,
        alpha: Int = 75,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): File {
        val cleanText = watermarkText.trim().ifBlank { "Qalam" }
        val outDoc = PdfDocument()
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(alpha.coerceIn(25, 180), 175, 25, 35)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            ParcelFileDescriptor.open(sourcePdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val total = renderer.pageCount
                    for (idx in 0 until total) {
                        onProgress(idx + 1, total)
                        renderer.openPage(idx).use { srcPage ->
                            val pageW = srcPage.width.coerceAtLeast(300)
                            val pageH = srcPage.height.coerceAtLeast(300)
                            val scale = (1300f / pageW).coerceIn(1f, 2.5f)
                            val rw = (pageW * scale).toInt()
                            val rh = (pageH * scale).toInt()
                            val bmp = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            srcPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
                            val outPage = outDoc.startPage(pageInfo)
                            val canvas = outPage.canvas
                            canvas.drawColor(Color.WHITE)
                            canvas.drawBitmap(bmp, null, RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()), paint)

                            if (diagonal) {
                                wmPaint.textSize = (pageW * 0.085f).coerceIn(24f, 64f)
                                canvas.save()
                                canvas.rotate(-35f, pageW / 2f, pageH / 2f)
                                canvas.drawText(cleanText, pageW / 2f, pageH / 2f, wmPaint)
                                canvas.restore()
                            } else {
                                wmPaint.textSize = (pageW * 0.028f).coerceIn(10f, 22f)
                                canvas.drawText(cleanText, pageW / 2f, pageH - 18f, wmPaint)
                            }

                            outDoc.finishPage(outPage)
                            bmp.recycle()
                        }
                    }
                }
            }
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { outDoc.writeTo(it) }
        } finally {
            outDoc.close()
        }
        return outputFile
    }

    fun exportAnnotatedPdf(
        sourcePdf: File,
        docTitle: String,
        annotations: PdfDocumentAnnotations,
        outputFile: File,
        includeNotesSummaryPage: Boolean = true,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): File {
        val outDoc = PdfDocument()
        try {
            val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            var lastPageNum = 0
            ParcelFileDescriptor.open(sourcePdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val total = renderer.pageCount
                    for (idx in 0 until total) {
                        lastPageNum = idx + 1
                        onProgress(lastPageNum, total)
                        renderer.openPage(idx).use { srcPage ->
                            val pageW = srcPage.width.coerceAtLeast(300)
                            val pageH = srcPage.height.coerceAtLeast(300)
                            val scale = (1300f / pageW).coerceIn(1f, 2.5f)
                            val rw = (pageW * scale).toInt()
                            val rh = (pageH * scale).toInt()
                            val bmp = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            srcPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, lastPageNum).create()
                            val outPage = outDoc.startPage(pageInfo)
                            val canvas = outPage.canvas
                            canvas.drawColor(Color.WHITE)
                            canvas.drawBitmap(bmp, null, RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()), bitmapPaint)

                            // Draw Strokes (Highlights, Pen, Underlines)
                            val pageStrokes = annotations.strokes.filter { it.pageIndex == idx }
                            for (stroke in pageStrokes) {
                                if (stroke.points.isEmpty()) continue
                                val baseColor = stroke.colorArgb.toInt()
                                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    style = Paint.Style.STROKE
                                    strokeCap = if (stroke.type == PdfStrokeType.HIGHLIGHT) Paint.Cap.SQUARE else Paint.Cap.ROUND
                                    strokeJoin = Paint.Join.ROUND
                                    strokeWidth = (stroke.widthRatio * pageW).coerceAtLeast(1.5f)
                                    color = if (stroke.type == PdfStrokeType.HIGHLIGHT) {
                                        Color.argb(105, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
                                    } else {
                                        baseColor
                                    }
                                }
                                if (stroke.type == PdfStrokeType.UNDERLINE && stroke.points.size >= 2) {
                                    val first = stroke.points.first()
                                    val last = stroke.points.last()
                                    val avgY = ((first.y + last.y) * 0.5f) * pageH
                                    canvas.drawLine(first.x * pageW, avgY, last.x * pageW, avgY, p)
                                } else if (stroke.points.size == 1) {
                                    val pt = stroke.points.first()
                                    canvas.drawPoint(pt.x * pageW, pt.y * pageH, p)
                                } else {
                                    val path = Path()
                                    stroke.points.forEachIndexed { pIdx, pt ->
                                        val px = pt.x * pageW
                                        val py = pt.y * pageH
                                        if (pIdx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                    }
                                    canvas.drawPath(path, p)
                                }
                            }

                            // Draw Sticky Comment Pins
                            val pageComments = annotations.comments.filter { it.pageIndex == idx }
                            val pinFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
                            val pinStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                style = Paint.Style.STROKE
                                strokeWidth = 1.5f
                                color = Color.WHITE
                            }
                            val pinText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.WHITE
                                textSize = (pageW * 0.022f).coerceIn(8f, 14f)
                                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                                textAlign = Paint.Align.CENTER
                            }
                            val r = (pageW * 0.02f).coerceIn(8f, 15f)
                            pageComments.forEachIndexed { cIdx, pin ->
                                val cx = pin.xRatio * pageW
                                val cy = pin.yRatio * pageH
                                pinFill.color = pin.colorArgb.toInt()
                                canvas.drawCircle(cx, cy, r, pinFill)
                                canvas.drawCircle(cx, cy, r, pinStroke)
                                canvas.drawText("${cIdx + 1}", cx, cy + pinText.textSize * 0.35f, pinText)
                            }

                            outDoc.finishPage(outPage)
                            bmp.recycle()
                        }
                    }
                }
            }

            // Optional Study Notes & Comments Summary Page at the end
            val hasNotes = annotations.comments.isNotEmpty() || annotations.pageNotes.isNotEmpty() || annotations.bookmarkedPages.isNotEmpty()
            if (includeNotesSummaryPage && hasNotes) {
                val pageW = 595
                val pageH = 842
                val margin = 44f
                val contentW = (pageW - margin * 2).toInt()
                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, lastPageNum + 1).create()
                val summaryPage = outDoc.startPage(pageInfo)
                val canvas = summaryPage.canvas
                canvas.drawColor(Color.WHITE)

                val headingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(0, 105, 92)
                    textSize = 18f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(35, 40, 39)
                    textSize = 11f
                }

                var y = margin
                canvas.drawText("Study Notes & Highlights — $docTitle", margin, y + 16f, headingPaint)
                y += 34f

                val lines = buildList {
                    if (annotations.bookmarkedPages.isNotEmpty()) {
                        add("Bookmarked Pages: ${annotations.bookmarkedPages.sorted().joinToString(", ") { "Page ${it + 1}" }}")
                    }
                    annotations.pageNotes.toSortedMap().forEach { (pIdx, note) ->
                        add("Page ${pIdx + 1} Note: $note")
                    }
                    annotations.comments.sortedBy { it.pageIndex }.forEachIndexed { idx, c ->
                        val title = c.title.ifBlank { "Comment ${idx + 1}" }
                        add("Page ${c.pageIndex + 1} [$title]: ${c.body}")
                    }
                }

                for (entry in lines) {
                    if (y > pageH - margin - 20f) break
                    val layout = StaticLayout.Builder
                        .obtain(entry, 0, entry.length, bodyPaint, contentW)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(2f, 1.15f)
                        .build()
                    canvas.save()
                    canvas.translate(margin, y)
                    layout.draw(canvas)
                    canvas.restore()
                    y += layout.height + 10f
                }

                outDoc.finishPage(summaryPage)
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { outDoc.writeTo(it) }
        } finally {
            outDoc.close()
        }
        return outputFile
    }

    fun pdfToImageFiles(
        sourcePdf: File,
        pageIndices: List<Int>,
        outputDir: File,
        widthPx: Int = 1400,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): List<File> {
        if (!sourcePdf.exists()) return emptyList()
        if (!outputDir.exists()) outputDir.mkdirs()
        val results = mutableListOf<File>()
        ParcelFileDescriptor.open(sourcePdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                val targetIndices = if (pageIndices.isEmpty()) {
                    (0 until renderer.pageCount).toList()
                } else {
                    pageIndices.filter { it in 0 until renderer.pageCount }
                }
                val total = targetIndices.size.coerceAtLeast(1)
                targetIndices.forEachIndexed { step, pageIdx ->
                    onProgress(step + 1, total)
                    renderer.openPage(pageIdx).use { srcPage ->
                        val ratio = srcPage.height.toFloat() / srcPage.width.coerceAtLeast(1)
                        val targetW = widthPx.coerceIn(600, 1800)
                        val targetH = (targetW * ratio).toInt().coerceAtLeast(200)
                        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        srcPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val outFile = File(outputDir, "pdf_page_${pageIdx + 1}_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(outFile).use { fos ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                        }
                        bmp.recycle()
                        results.add(outFile)
                    }
                }
            }
        }
        return results
    }

    fun parsePageSelection(input: String, totalPages: Int): List<Int> {
        if (input.isBlank() || totalPages <= 0) return emptyList()
        val selected = linkedSetOf<Int>()
        for (part in input.split(",", ";")) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.contains("-")) {
                val bounds = trimmed.split("-", limit = 2)
                val start = bounds[0].trim().toIntOrNull()
                val end = bounds[1].trim().toIntOrNull()
                if (start != null && end != null) {
                    val low = minOf(start, end).coerceIn(1, totalPages)
                    val high = maxOf(start, end).coerceIn(1, totalPages)
                    for (p in low..high) selected.add(p - 1)
                }
            } else {
                val single = trimmed.toIntOrNull()
                if (single != null && single in 1..totalPages) {
                    selected.add(single - 1)
                }
            }
        }
        return selected.toList()
    }
}

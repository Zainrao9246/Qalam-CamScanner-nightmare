package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import com.example.model.CropQuad
import com.example.model.FilterType
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageProcessor {

    /**
     * Warps and perspective-crops the 4 corners defined by [quad] into a rectified rectangular bitmap.
     * [quad] has coordinates normalized 0.0f..1.0f relative to [source].
     */
    fun cropPerspective(source: Bitmap, quad: CropQuad): Bitmap {
        val w = source.width.toFloat()
        val h = source.height.toFloat()

        val srcPoints = floatArrayOf(
            quad.topLeft.x * w, quad.topLeft.y * h,         // Top Left
            quad.topRight.x * w, quad.topRight.y * h,       // Top Right
            quad.bottomRight.x * w, quad.bottomRight.y * h, // Bottom Right
            quad.bottomLeft.x * w, quad.bottomLeft.y * h    // Bottom Left
        )

        // Calculate approximate output dimensions based on average width and height of quad
        val topWidth = kotlin.math.hypot(srcPoints[2] - srcPoints[0], srcPoints[3] - srcPoints[1])
        val bottomWidth = kotlin.math.hypot(srcPoints[4] - srcPoints[6], srcPoints[5] - srcPoints[7])
        val leftHeight = kotlin.math.hypot(srcPoints[6] - srcPoints[0], srcPoints[7] - srcPoints[1])
        val rightHeight = kotlin.math.hypot(srcPoints[4] - srcPoints[2], srcPoints[5] - srcPoints[3])

        val outWidth = max(200, max(topWidth, bottomWidth).roundToInt())
        val outHeight = max(200, max(leftHeight, rightHeight).roundToInt())

        val dstPoints = floatArrayOf(
            0f, 0f,
            outWidth.toFloat(), 0f,
            outWidth.toFloat(), outHeight.toFloat(),
            0f, outHeight.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        // Draw into destination bitmap
        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        canvas.drawColor(AndroidColor.WHITE)
        canvas.drawBitmap(source, matrix, paint)
        return output
    }

    /**
     * Applies rotation to [source].
     */
    fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Applies CamScanner's signature document filters.
     */
    fun applyFilter(source: Bitmap, filter: FilterType): Bitmap {
        return when (filter) {
            FilterType.ORIGINAL -> source.copy(Bitmap.Config.ARGB_8888, true)
            FilterType.MAGIC_COLOR -> applyMagicColor(source)
            FilterType.BW_HIGH -> applyBwHigh(source)
            FilterType.BW_SOFT -> applyBwSoft(source)
            FilterType.GRAYSCALE -> applyGrayscale(source)
            FilterType.LIGHTEN -> applyLighten(source)
        }
    }

    /**
     * CamScanner's famous "Magic Color":
     * Whitens the paper background (curves mid-tones up), sharply darkens text/ink,
     * and preserves signature/seal colors.
     */
    private fun applyMagicColor(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Step 1: ColorMatrix that lifts brightness, increases contrast, and slightly enhances saturation
        // contrast = 1.35, brightness = 25
        val contrast = 1.35f
        val brightness = 28f
        val cm = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))

        // Slight saturation boost for colorful stamps/signatures
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(1.15f)
        cm.postConcat(satMatrix)

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)

        // Step 2: Pixel-level paper whitening for background noise removal
        val width = out.width
        val height = out.height
        val pixels = IntArray(width * height)
        out.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = (pixel shr 24) and 0xff
            var r = (pixel shr 16) and 0xff
            var g = (pixel shr 8) and 0xff
            var b = pixel and 0xff

            // If background is nearly white/light grey, push it to pristine white
            val luminance = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
            if (luminance > 165) {
                val boost = ((luminance - 165) * 1.5f).toInt()
                r = min(255, r + boost)
                g = min(255, g + boost)
                b = min(255, b + boost)
            } else if (luminance < 110) {
                // Darken dark text/ink
                r = (r * 0.75f).toInt()
                g = (g * 0.75f).toInt()
                b = (b * 0.75f).toInt()
            }

            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    /**
     * B&W High Contrast (pure document binary threshold).
     */
    private fun applyBwHigh(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val width = out.width
        val height = out.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt()

            val v = if (lum > 140) 255 else 0
            pixels[i] = (0xff shl 24) or (v shl 16) or (v shl 8) or v
        }

        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    /**
     * B&W Soft (gentle contrast curve for thermal receipts and pencil notes).
     */
    private fun applyBwSoft(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cm = ColorMatrix()
        cm.setSaturation(0f) // desaturate

        // High contrast S-curve
        val contrast = 1.6f
        val brightness = 20f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(contrastMatrix)

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    /**
     * Standard Grayscale.
     */
    private fun applyGrayscale(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    /**
     * Lighten: Lifts dark shadows while keeping contrast.
     */
    private fun applyLighten(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cm = ColorMatrix(floatArrayOf(
            1.1f, 0f, 0f, 0f, 40f,
            0f, 1.1f, 0f, 0f, 40f,
            0f, 0f, 1.1f, 0f, 40f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    /**
     * Adds diagonal or footer watermark text onto the document page.
     */
    fun addWatermark(source: Bitmap, text: String, isFooter: Boolean = false): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(80, 180, 20, 20)
            textSize = if (isFooter) out.width * 0.035f else out.width * 0.09f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        if (isFooter) {
            paint.color = AndroidColor.argb(140, 100, 115, 110)
            canvas.drawText(text, out.width / 2f, out.height - (out.width * 0.04f), paint)
        } else {
            canvas.save()
            canvas.rotate(-35f, out.width / 2f, out.height / 2f)
            canvas.drawText(text, out.width / 2f, out.height / 2f, paint)
            canvas.restore()
        }
        return out
    }

    /**
     * Overlays digital signature onto bitmap at given relative position (0..1).
     */
    fun overlaySignature(source: Bitmap, signature: Bitmap, relX: Float = 0.65f, relY: Float = 0.8f): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Target signature size ~ 25% of document width
        val targetWidth = out.width * 0.28f
        val scale = targetWidth / signature.width
        val targetHeight = signature.height * scale

        val left = out.width * relX - (targetWidth / 2f)
        val top = out.height * relY - (targetHeight / 2f)

        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(left, top)
        }
        canvas.drawBitmap(signature, matrix, paint)
        return out
    }

    /**
     * Persists a bitmap to local app files and returns the absolute path.
     */
    fun saveBitmapToFile(context: Context, bitmap: Bitmap, prefix: String = "scan"): String {
        val dir = File(context.filesDir, "documents")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "${prefix}_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        return file.absolutePath
    }

    fun loadBitmap(path: String): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        return loadOrientedBitmapFromFile(file.absolutePath, 2000)
    }

    fun loadOrientedBitmapFromFile(path: String, maxDim: Int = 2000): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, boundsOpts)
        val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxDim)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bmp = BitmapFactory.decodeFile(path, decodeOpts) ?: return null

        val exifRot = runCatching {
            val exif = ExifInterface(path)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)

        return if (exifRot != 0) rotateBitmap(bmp, exifRot) else bmp
    }

    fun loadOrientedBitmapFromUri(context: Context, uri: Uri, maxDim: Int = 2000): Bitmap? {
        return runCatching {
            val tempFile = File.createTempFile("pick_", ".jpg", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }
            val bmp = loadOrientedBitmapFromFile(tempFile.absolutePath, maxDim)
            tempFile.delete()
            bmp
        }.getOrNull()
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val maxSide = max(width, height)
        while (maxSide / inSampleSize > maxDim * 2) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    /**
     * Heuristic Auto-Edge Document Detector:
     * Downscales [source], scans horizontal & vertical edge-gradient/luminance profiles
     * relative to the center document sheet vs background margins, and returns a snapped [CropQuad].
     */
    fun detectDocumentQuad(source: Bitmap): CropQuad {
        return runCatching {
            val w = 160
            val h = ((source.height.toFloat() / source.width.coerceAtLeast(1)) * w)
                .roundToInt()
                .coerceIn(60, 240)
            val small = Bitmap.createScaledBitmap(source, w, h, true)
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            if (small !== source) small.recycle()

            val lum = FloatArray(w * h)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xff
                val g = (p shr 8) and 0xff
                val b = p and 0xff
                lum[i] = 0.299f * r + 0.587f * g + 0.114f * b
            }

            // Sample central document brightness vs outer border brightness
            var centerSum = 0f
            var centerCount = 0
            for (y in (h / 4) until (3 * h / 4)) {
                for (x in (w / 4) until (3 * w / 4)) {
                    centerSum += lum[y * w + x]
                    centerCount++
                }
            }
            val centerLum = if (centerCount > 0) centerSum / centerCount else 200f

            var borderSum = 0f
            var borderCount = 0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (x < w * 0.08 || x > w * 0.92 || y < h * 0.08 || y > h * 0.92) {
                        borderSum += lum[y * w + x]
                        borderCount++
                    }
                }
            }
            val borderLum = if (borderCount > 0) borderSum / borderCount else 100f

            // If scene doesn't have clear document-vs-background contrast, use clean slight inset
            if (abs(centerLum - borderLum) < 22f) {
                return CropQuad()
            }

            val threshold = (centerLum + borderLum) * 0.5f
            val paperIsBrighter = centerLum > borderLum

            fun isPaper(l: Float): Boolean = if (paperIsBrighter) l >= threshold else l <= threshold

            var leftEdge = (w * 0.05f).roundToInt()
            var rightEdge = (w * 0.95f).roundToInt()
            var topEdge = (h * 0.05f).roundToInt()
            var bottomEdge = (h * 0.95f).roundToInt()

            // Scan inward from each side until >=35% of row/col matches paper brightness
            for (x in 2 until w / 2) {
                var hits = 0
                for (y in (h / 5) until (4 * h / 5)) {
                    if (isPaper(lum[y * w + x])) hits++
                }
                if (hits > (3 * h / 5) * 0.35f) {
                    leftEdge = x
                    break
                }
            }
            for (x in (w - 3) downTo w / 2) {
                var hits = 0
                for (y in (h / 5) until (4 * h / 5)) {
                    if (isPaper(lum[y * w + x])) hits++
                }
                if (hits > (3 * h / 5) * 0.35f) {
                    rightEdge = x
                    break
                }
            }
            for (y in 2 until h / 2) {
                var hits = 0
                for (x in (w / 5) until (4 * w / 5)) {
                    if (isPaper(lum[y * w + x])) hits++
                }
                if (hits > (3 * w / 5) * 0.35f) {
                    topEdge = y
                    break
                }
            }
            for (y in (h - 3) downTo h / 2) {
                var hits = 0
                for (x in (w / 5) until (4 * w / 5)) {
                    if (isPaper(lum[y * w + x])) hits++
                }
                if (hits > (3 * w / 5) * 0.35f) {
                    bottomEdge = y
                    break
                }
            }

            val nx1 = (leftEdge.toFloat() / w).coerceIn(0.02f, 0.4f)
            val nx2 = (rightEdge.toFloat() / w).coerceIn(0.6f, 0.98f)
            val ny1 = (topEdge.toFloat() / h).coerceIn(0.02f, 0.4f)
            val ny2 = (bottomEdge.toFloat() / h).coerceIn(0.6f, 0.98f)

            if ((nx2 - nx1) < 0.25f || (ny2 - ny1) < 0.25f) {
                CropQuad()
            } else {
                CropQuad(
                    topLeft = Offset(nx1, ny1),
                    topRight = Offset(nx2, ny1),
                    bottomRight = Offset(nx2, ny2),
                    bottomLeft = Offset(nx1, ny2)
                )
            }
        }.getOrDefault(CropQuad())
    }

    /**
     * Arranges Front & Back captures of an ID card neatly centered on a crisp A4 sheet.
     */
    fun combineIdCardSides(front: Bitmap, back: Bitmap): Bitmap {
        val pageW = 1240
        val pageH = 1754
        val out = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(AndroidColor.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(210, 218, 216)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(90, 105, 102)
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val maxCardW = pageW * 0.72f
        val maxCardH = pageH * 0.34f

        fun drawCard(bmp: Bitmap, centerY: Float, label: String) {
            val ratio = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
            var drawW = maxCardW
            var drawH = drawW / ratio
            if (drawH > maxCardH) {
                drawH = maxCardH
                drawW = drawH * ratio
            }
            val left = (pageW - drawW) / 2f
            val top = centerY - drawH / 2f
            val rect = RectF(left, top, left + drawW, top + drawH)
            canvas.drawText(label, pageW / 2f, top - 18f, labelPaint)
            canvas.drawBitmap(bmp, null, rect, paint)
            canvas.drawRect(rect, borderPaint)
        }

        drawCard(front, pageH * 0.28f, "FRONT SIDE")
        drawCard(back, pageH * 0.72f, "BACK SIDE")
        return out
    }

    /**
     * Splits a landscape open-book spread capture into Left Page and Right Page bitmaps.
     */
    fun splitBookSpread(source: Bitmap): Pair<Bitmap, Bitmap> {
        val w = source.width
        val h = source.height
        val halfW = (w / 2).coerceAtLeast(1)
        val left = Bitmap.createBitmap(source, 0, 0, halfW, h)
        val right = Bitmap.createBitmap(source, halfW, 0, (w - halfW).coerceAtLeast(1), h)
        return left to right
    }
}

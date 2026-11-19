package com.example.util

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Real On-Device QR Code & Barcode Scanner powered by Google ML Kit.
 */
object QrBarcodeEngine {

    data class ScannedCode(
        val rawValue: String,
        val displayValue: String,
        val formatLabel: String,
        val isUrl: Boolean
    )

    suspend fun scanBitmap(bitmap: Bitmap): List<ScannedCode> = suspendCancellableCoroutine { cont ->
        val scanner = BarcodeScanning.getClient()
        val image = InputImage.fromBitmap(bitmap, 0)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val list = barcodes.mapNotNull { code ->
                    val raw = code.rawValue?.trim().orEmpty()
                    if (raw.isEmpty()) return@mapNotNull null
                    ScannedCode(
                        rawValue = raw,
                        displayValue = code.displayValue?.takeIf { it.isNotBlank() } ?: raw,
                        formatLabel = formatName(code.format, code.valueType),
                        isUrl = code.valueType == Barcode.TYPE_URL ||
                            raw.startsWith("http://", ignoreCase = true) ||
                            raw.startsWith("https://", ignoreCase = true)
                    )
                }
                if (cont.isActive) cont.resume(list)
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(emptyList())
            }
    }

    private fun formatName(format: Int, valueType: Int): String {
        val fmt = when (format) {
            Barcode.FORMAT_QR_CODE -> "QR Code"
            Barcode.FORMAT_EAN_13 -> "EAN-13"
            Barcode.FORMAT_EAN_8 -> "EAN-8"
            Barcode.FORMAT_UPC_A -> "UPC-A"
            Barcode.FORMAT_CODE_128 -> "Code 128"
            Barcode.FORMAT_CODE_39 -> "Code 39"
            Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
            Barcode.FORMAT_PDF417 -> "PDF417"
            else -> "Barcode"
        }
        val kind = when (valueType) {
            Barcode.TYPE_URL -> "Link"
            Barcode.TYPE_WIFI -> "Wi-Fi"
            Barcode.TYPE_EMAIL -> "Email"
            Barcode.TYPE_PHONE -> "Phone"
            Barcode.TYPE_ISBN -> "ISBN"
            else -> "Text"
        }
        return "$fmt • $kind"
    }
}

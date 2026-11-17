package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.graphics.Bitmap
import com.example.model.CameraCaptureMode
import com.example.ui.MainViewModel
import com.example.ui.theme.CamScanAccent
import com.example.ui.theme.CamScanPrimary
import com.example.util.ImageProcessor
import java.io.File

@Composable
fun CameraScanScreen(
    viewModel: MainViewModel,
    targetDocId: String? = null,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.onImagePickedForScan(it, targetDocId) }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var isCapturing by remember { mutableStateOf(false) }
    var captureMode by remember { mutableStateOf(CameraCaptureMode.DOCUMENT) }
    var isBatchMode by remember { mutableStateOf(false) }
    val batchPaths = remember { mutableStateListOf<String>() }
    var idFrontBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    LaunchedEffect(flashMode) {
        imageCapture.flashMode = flashMode
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            val previewView = remember { PreviewView(context) }

            DisposableEffect(lifecycleOwner) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                val executor = ContextCompat.getMainExecutor(context)

                cameraProviderFuture.addListener({
                    val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull()
                    if (cameraProvider != null) {
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        runCatching {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture
                            )
                        }
                    }
                }, executor)

                onDispose {
                    runCatching { cameraProviderFuture.get().unbindAll() }
                }
            }

            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Document Framing Bracket Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val marginX = if (captureMode == CameraCaptureMode.ID_CARD) size.width * 0.12f else size.width * 0.1f
                val marginY = if (captureMode == CameraCaptureMode.ID_CARD) size.height * 0.30f else size.height * 0.18f
                val len = 36.dp.toPx()
                val stroke = 3.dp.toPx()
                val col = CamScanAccent.copy(alpha = 0.85f)

                // Top-left
                drawLine(col, Offset(marginX, marginY), Offset(marginX + len, marginY), stroke, StrokeCap.Round)
                drawLine(col, Offset(marginX, marginY), Offset(marginX, marginY + len), stroke, StrokeCap.Round)
                // Top-right
                drawLine(col, Offset(size.width - marginX, marginY), Offset(size.width - marginX - len, marginY), stroke, StrokeCap.Round)
                drawLine(col, Offset(size.width - marginX, marginY), Offset(size.width - marginX, marginY + len), stroke, StrokeCap.Round)
                // Bottom-left
                drawLine(col, Offset(marginX, size.height - marginY), Offset(marginX + len, size.height - marginY), stroke, StrokeCap.Round)
                drawLine(col, Offset(marginX, size.height - marginY), Offset(marginX, size.height - marginY - len), stroke, StrokeCap.Round)
                // Bottom-right
                drawLine(col, Offset(size.width - marginX, size.height - marginY), Offset(size.width - marginX - len, size.height - marginY), stroke, StrokeCap.Round)
                drawLine(col, Offset(size.width - marginX, size.height - marginY), Offset(size.width - marginX, size.height - marginY - len), stroke, StrokeCap.Round)

                if (captureMode == CameraCaptureMode.BOOK_SPLIT) {
                    drawLine(
                        color = CamScanAccent.copy(alpha = 0.7f),
                        start = Offset(size.width / 2f, marginY),
                        end = Offset(size.width / 2f, size.height - marginY),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        } else {
            // Permission Denied / Prompt Fallback
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = CamScanAccent,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Access Needed",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Grant camera permission to scan notes, handouts, and documents, or import an image from your gallery.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary)
                ) {
                    Text("Enable Camera", color = Color.White)
                }
            }
        }

        // Top Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateBack() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .testTag("camera_close_btn")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                val hint = when {
                    captureMode == CameraCaptureMode.ID_CARD && idFrontBitmap == null -> "ID Card: Capture FRONT Side"
                    captureMode == CameraCaptureMode.ID_CARD && idFrontBitmap != null -> "Now Capture BACK Side"
                    captureMode == CameraCaptureMode.BOOK_SPLIT -> "Align 2 Open Book Pages (Left | Right)"
                    isBatchMode -> "Batch Mode: ${batchPaths.size} pages captured"
                    else -> "Align Document Within Frame"
                }
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelMedium.copy(color = Color.White)
                )
            }

            IconButton(
                onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                        else -> ImageCapture.FLASH_MODE_AUTO
                    }
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .testTag("camera_flash_btn")
            ) {
                Icon(
                    imageVector = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff
                        else -> Icons.Default.FlashAuto
                    },
                    contentDescription = "Flash Mode",
                    tint = Color.White
                )
            }
        }

        // Bottom Capture Controls + Scan Mode Strip
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(top = 10.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                CameraCaptureMode.values().forEach { mode ->
                    val selected = captureMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) CamScanPrimary else Color.White.copy(alpha = 0.14f))
                            .clickable {
                                captureMode = mode
                                idFrontBitmap = null
                                if (mode != CameraCaptureMode.DOCUMENT) isBatchMode = false
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("camera_mode_${mode.name.lowercase()}")
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
                if (captureMode == CameraCaptureMode.DOCUMENT) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isBatchMode) CamScanAccent else Color.White.copy(alpha = 0.14f))
                            .clickable { isBatchMode = !isBatchMode }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("camera_batch_toggle")
                    ) {
                        Text(
                            text = if (isBatchMode) "Batch ON" else "Batch",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (isBatchMode) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .testTag("camera_gallery_btn")
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Gallery", style = MaterialTheme.typography.labelSmall.copy(color = Color.White))
                }

                // Shutter Button
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(if (isCapturing) CamScanAccent else CamScanPrimary)
                        .clickable(enabled = hasCameraPermission && !isCapturing) {
                            isCapturing = true
                            val photoFile = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                            imageCapture.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        isCapturing = false
                                        if (captureMode == CameraCaptureMode.DOCUMENT && isBatchMode) {
                                            batchPaths.add(photoFile.absolutePath)
                                            Toast.makeText(context, "Captured page ${batchPaths.size}", Toast.LENGTH_SHORT).show()
                                            return
                                        }
                                        val bmp = ImageProcessor.loadOrientedBitmapFromFile(photoFile.absolutePath, 2000)
                                        photoFile.delete()
                                        if (bmp != null) {
                                            when (captureMode) {
                                                CameraCaptureMode.DOCUMENT -> viewModel.startNewScan(bmp, targetDocId)
                                                CameraCaptureMode.ID_CARD -> {
                                                    val front = idFrontBitmap
                                                    if (front == null) {
                                                        idFrontBitmap = bmp
                                                        Toast.makeText(context, "Front captured! Now scan Back side.", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        idFrontBitmap = null
                                                        viewModel.combineIdCardAndStartScan(front, bmp, targetDocId)
                                                    }
                                                }
                                                CameraCaptureMode.BOOK_SPLIT -> viewModel.splitBookAndSaveDocument(bmp)
                                            }
                                        } else {
                                            Toast.makeText(context, "Could not decode captured photo", Toast.LENGTH_SHORT).show()
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        isCapturing = false
                                        Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        .testTag("camera_shutter_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Capture",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                if (isBatchMode && batchPaths.isNotEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable {
                                val paths = batchPaths.toList()
                                batchPaths.clear()
                                viewModel.finishBatchCameraScan(paths, targetDocId)
                            }
                            .testTag("camera_batch_done_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(CamScanPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Finish Batch", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Done (${batchPaths.size})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = CamScanAccent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }
        }
    }
}

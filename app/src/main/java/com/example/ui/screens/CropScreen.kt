package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CropQuad
import com.example.ui.MainViewModel
import com.example.ui.theme.CamScanAccent
import com.example.ui.theme.CamScanPrimary
import kotlin.math.hypot

enum class CornerHandle {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT
}

@Composable
fun CropScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val bitmap by viewModel.transientBitmap.collectAsState()
    val cropQuad by viewModel.transientCropQuad.collectAsState()

    var activeHandle by remember { mutableStateOf(CornerHandle.NONE) }
    var loupePos by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1413)) // Deep darkroom dark for precise edge alignment
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.navigateBack() },
                    modifier = Modifier.testTag("crop_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Text(
                    text = "Edge Alignment",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )

                IconButton(
                    onClick = { viewModel.rotateTransientBitmap() },
                    modifier = Modifier.testTag("rotate_90_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.RotateRight,
                        contentDescription = "Rotate 90",
                        tint = Color.White
                    )
                }
            }

            // Interactive Canvas Area
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val availableWidth = constraints.maxWidth.toFloat()
                val availableHeight = constraints.maxHeight.toFloat()

                val bmp = bitmap
                if (bmp != null) {
                    val imgW = bmp.width.toFloat()
                    val imgH = bmp.height.toFloat()

                    // Compute scaling to fit within constraints
                    val scale = minOf(availableWidth / imgW, availableHeight / imgH)
                    val drawW = imgW * scale
                    val drawH = imgH * scale

                    val offsetX = (availableWidth - drawW) / 2f
                    val offsetY = (availableHeight - drawH) / 2f

                    // Helper functions to convert between normalized (0..1) and canvas coordinates
                    fun normToCanvas(norm: Offset): Offset {
                        return Offset(offsetX + norm.x * drawW, offsetY + norm.y * drawH)
                    }

                    fun canvasToNorm(canvasPt: Offset): Offset {
                        val nx = ((canvasPt.x - offsetX) / drawW).coerceIn(0f, 1f)
                        val ny = ((canvasPt.y - offsetY) / drawH).coerceIn(0f, 1f)
                        return Offset(nx, ny)
                    }

                    val tl = normToCanvas(cropQuad.topLeft)
                    val tr = normToCanvas(cropQuad.topRight)
                    val br = normToCanvas(cropQuad.bottomRight)
                    val bl = normToCanvas(cropQuad.bottomLeft)

                    val latestQuad by rememberUpdatedState(cropQuad)

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(drawW, drawH, offsetX, offsetY) {
                                detectDragGestures(
                                    onDragStart = { startPt ->
                                        val q = latestQuad
                                        val curTl = normToCanvas(q.topLeft)
                                        val curTr = normToCanvas(q.topRight)
                                        val curBr = normToCanvas(q.bottomRight)
                                        val curBl = normToCanvas(q.bottomLeft)

                                        val distTL = hypot(startPt.x - curTl.x, startPt.y - curTl.y)
                                        val distTR = hypot(startPt.x - curTr.x, startPt.y - curTr.y)
                                        val distBR = hypot(startPt.x - curBr.x, startPt.y - curBr.y)
                                        val distBL = hypot(startPt.x - curBl.x, startPt.y - curBl.y)

                                        val touchThreshold = 55.dp.toPx()
                                        val minDist = minOf(distTL, distTR, distBR, distBL)

                                        activeHandle = when {
                                            minDist > touchThreshold -> CornerHandle.NONE
                                            minDist == distTL -> CornerHandle.TOP_LEFT
                                            minDist == distTR -> CornerHandle.TOP_RIGHT
                                            minDist == distBR -> CornerHandle.BOTTOM_RIGHT
                                            else -> CornerHandle.BOTTOM_LEFT
                                        }
                                        loupePos = startPt
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val newTouch = change.position
                                        loupePos = newTouch
                                        val norm = canvasToNorm(newTouch)
                                        val q = latestQuad

                                        when (activeHandle) {
                                            CornerHandle.TOP_LEFT -> {
                                                viewModel.updateCropQuad(q.copy(topLeft = norm))
                                            }
                                            CornerHandle.TOP_RIGHT -> {
                                                viewModel.updateCropQuad(q.copy(topRight = norm))
                                            }
                                            CornerHandle.BOTTOM_RIGHT -> {
                                                viewModel.updateCropQuad(q.copy(bottomRight = norm))
                                            }
                                            CornerHandle.BOTTOM_LEFT -> {
                                                viewModel.updateCropQuad(q.copy(bottomLeft = norm))
                                            }
                                            CornerHandle.NONE -> {}
                                        }
                                    },
                                    onDragEnd = {
                                        activeHandle = CornerHandle.NONE
                                    },
                                    onDragCancel = {
                                        activeHandle = CornerHandle.NONE
                                    }
                                )
                            }
                    ) {
                        // 1. Draw scaled image
                        drawImage(
                            image = bmp.asImageBitmap(),
                            dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                            dstSize = IntSize(drawW.toInt(), drawH.toInt())
                        )

                        // 2. Draw semi-transparent dim layer over outside regions
                        val quadPath = Path().apply {
                            moveTo(tl.x, tl.y)
                            lineTo(tr.x, tr.y)
                            lineTo(br.x, br.y)
                            lineTo(bl.x, bl.y)
                            close()
                        }

                        // 3. Draw boundary quadrilateral lines
                        drawPath(
                            path = quadPath,
                            color = CamScanAccent,
                            style = Stroke(width = 3.5.dp.toPx())
                        )

                        // 4. Draw midpoint guidelines for CamScanner grid feel
                        val midTop = Offset((tl.x + tr.x) / 2f, (tl.y + tr.y) / 2f)
                        val midBottom = Offset((bl.x + br.x) / 2f, (bl.y + br.y) / 2f)
                        val midLeft = Offset((tl.x + bl.x) / 2f, (tl.y + bl.y) / 2f)
                        val midRight = Offset((tr.x + br.x) / 2f, (tr.y + br.y) / 2f)

                        drawLine(
                            color = CamScanAccent.copy(alpha = 0.45f),
                            start = midTop,
                            end = midBottom,
                            strokeWidth = 1.5.dp.toPx()
                        )
                        drawLine(
                            color = CamScanAccent.copy(alpha = 0.45f),
                            start = midLeft,
                            end = midRight,
                            strokeWidth = 1.5.dp.toPx()
                        )

                        // 5. Draw 4 circular corner pins
                        listOf(tl, tr, br, bl).forEach { pin ->
                            // Outer ring
                            drawCircle(
                                color = CamScanPrimary,
                                radius = 15.dp.toPx(),
                                center = pin
                            )
                            // Inner white core
                            drawCircle(
                                color = Color.White,
                                radius = 7.dp.toPx(),
                                center = pin
                            )
                        }

                        // 6. Draw side mid-point markers
                        listOf(midTop, midRight, midBottom, midLeft).forEach { mid ->
                            drawCircle(
                                color = CamScanAccent,
                                radius = 5.dp.toPx(),
                                center = mid
                            )
                        }
                    }

                    // Floating Magnifier Loupe when dragging a corner pin
                    if (activeHandle != CornerHandle.NONE) {
                        val activePin = when (activeHandle) {
                            CornerHandle.TOP_LEFT -> tl
                            CornerHandle.TOP_RIGHT -> tr
                            CornerHandle.BOTTOM_RIGHT -> br
                            CornerHandle.BOTTOM_LEFT -> bl
                            CornerHandle.NONE -> Offset.Zero
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(Color.Black)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val loupeRadius = size.width / 2f
                                // Draw magnified portion around activePin
                                val normActive = canvasToNorm(activePin)
                                val imgCenterX = normActive.x * bmp.width
                                val imgCenterY = normActive.y * bmp.height

                                val srcWindowSize = 120f
                                val srcLeft = (imgCenterX - srcWindowSize / 2f).coerceIn(0f, (bmp.width - srcWindowSize).coerceAtLeast(0f))
                                val srcTop = (imgCenterY - srcWindowSize / 2f).coerceIn(0f, (bmp.height - srcWindowSize).coerceAtLeast(0f))

                                drawImage(
                                    image = bmp.asImageBitmap(),
                                    srcOffset = IntOffset(srcLeft.toInt(), srcTop.toInt()),
                                    srcSize = IntSize(srcWindowSize.toInt(), srcWindowSize.toInt()),
                                    dstOffset = IntOffset.Zero,
                                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                                )

                                // Crosshairs
                                drawLine(
                                    color = CamScanAccent,
                                    start = Offset(loupeRadius, 0f),
                                    end = Offset(loupeRadius, size.height),
                                    strokeWidth = 2.dp.toPx()
                                )
                                drawLine(
                                    color = CamScanAccent,
                                    start = Offset(0f, loupeRadius),
                                    end = Offset(size.width, loupeRadius),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }
                    }
                }
            }

            // Quick Adjustment Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.reDetectAutoCropQuad()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("auto_detect_btn")
                ) {
                    Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto Detect", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = {
                        // Select all
                        viewModel.updateCropQuad(
                            CropQuad(
                                topLeft = Offset(0.0f, 0.0f),
                                topRight = Offset(1.0f, 0.0f),
                                bottomRight = Offset(1.0f, 1.0f),
                                bottomLeft = Offset(0.0f, 1.0f)
                            )
                        )
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("select_all_btn")
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select All", fontSize = 13.sp)
                }
            }

            // Bottom Navigation & Next Confirmation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.navigateBack() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Retake")
                }

                Button(
                    onClick = { viewModel.confirmCrop() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CamScanPrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_crop_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Next",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next"
                        )
                    }
                }
            }
        }
    }
}

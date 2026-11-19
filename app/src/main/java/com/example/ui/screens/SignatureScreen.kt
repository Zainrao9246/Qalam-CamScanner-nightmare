package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.theme.CamScanPrimary

data class SignaturePath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val paths = remember { mutableStateListOf<SignaturePath>() }
    var currentPoints = remember { mutableStateListOf<Offset>() }
    var inkColor by remember { mutableStateOf(Color(0xFF003366)) } // Classic blue pen
    val strokeWidth = 5.5f

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Draw, contentDescription = null, tint = CamScanPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Digital Signature",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("signature_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        paths.clear()
                        currentPoints.clear()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear")
                }

                Button(
                    onClick = {
                        if (paths.isNotEmpty()) {
                            val allPoints = paths.flatMap { it.points }
                            if (allPoints.isNotEmpty()) {
                                val minX = allPoints.minOf { it.x }
                                val maxX = allPoints.maxOf { it.x }
                                val minY = allPoints.minOf { it.y }
                                val maxY = allPoints.maxOf { it.y }

                                val pad = 24f
                                val rawW = (maxX - minX).coerceAtLeast(40f)
                                val rawH = (maxY - minY).coerceAtLeast(40f)

                                val targetW = 700
                                val scale = (targetW - pad * 2) / rawW
                                val targetH = ((rawH * scale) + pad * 2).toInt().coerceIn(160, 600)

                                val sigBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                                val canvas = AndroidCanvas(sigBitmap)
                                val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                    style = AndroidPaint.Style.STROKE
                                    strokeCap = AndroidPaint.Cap.ROUND
                                    strokeJoin = AndroidPaint.Join.ROUND
                                    setStrokeWidth((8f * scale).coerceIn(5f, 14f))
                                }

                                paths.forEach { p ->
                                    paint.color = android.graphics.Color.argb(
                                        (p.color.alpha * 255).toInt(),
                                        (p.color.red * 255).toInt(),
                                        (p.color.green * 255).toInt(),
                                        (p.color.blue * 255).toInt()
                                    )
                                    val androidPath = android.graphics.Path()
                                    p.points.forEachIndexed { i, pt ->
                                        val sx = (pt.x - minX) * scale + pad
                                        val sy = (pt.y - minY) * scale + pad
                                        if (i == 0) androidPath.moveTo(sx, sy)
                                        else androidPath.lineTo(sx, sy)
                                    }
                                    canvas.drawPath(androidPath, paint)
                                }

                                viewModel.applySignatureToPage(sigBitmap)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("apply_signature_btn")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apply to Page", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Ink color selector & controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ink Color:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )

                    // Blue ink
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF003366))
                            .border(
                                width = if (inkColor == Color(0xFF003366)) 3.dp else 1.dp,
                                color = if (inkColor == Color(0xFF003366)) CamScanPrimary else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { inkColor = Color(0xFF003366) }
                    )

                    // Black ink
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1A1A1A))
                            .border(
                                width = if (inkColor == Color(0xFF1A1A1A)) 3.dp else 1.dp,
                                color = if (inkColor == Color(0xFF1A1A1A)) CamScanPrimary else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { inkColor = Color(0xFF1A1A1A) }
                    )
                }

                IconButton(
                    onClick = {
                        if (paths.isNotEmpty()) paths.removeAt(paths.size - 1)
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
            }

            // Interactive Pad
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFCFCF9))
                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .pointerInput(inkColor) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPoints.clear()
                                currentPoints.add(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentPoints.add(change.position)
                            },
                            onDragEnd = {
                                if (currentPoints.isNotEmpty()) {
                                    paths.add(
                                        SignaturePath(
                                            points = currentPoints.toList(),
                                            color = inkColor,
                                            strokeWidth = strokeWidth
                                        )
                                    )
                                    currentPoints.clear()
                                }
                            },
                            onDragCancel = {
                                currentPoints.clear()
                            }
                        )
                    }
                    .testTag("signature_canvas")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Baseline guide line
                    val baselineY = size.height * 0.75f
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.5f),
                        start = Offset(40f, baselineY),
                        end = Offset(size.width - 40f, baselineY),
                        strokeWidth = 2.dp.toPx()
                    )

                    // Draw completed paths
                    paths.forEach { p ->
                        if (p.points.size > 1) {
                            val path = Path().apply {
                                moveTo(p.points.first().x, p.points.first().y)
                                for (i in 1 until p.points.size) {
                                    lineTo(p.points[i].x, p.points[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = p.color,
                                style = Stroke(
                                    width = p.strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    // Draw current dragging path
                    if (currentPoints.size > 1) {
                        val path = Path().apply {
                            moveTo(currentPoints.first().x, currentPoints.first().y)
                            for (i in 1 until currentPoints.size) {
                                lineTo(currentPoints[i].x, currentPoints[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = inkColor,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                if (paths.isEmpty() && currentPoints.isEmpty()) {
                    Text(
                        text = "Sign here with your finger",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.LightGray,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Signature will be placed on the bottom right of the active document page.",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            )
        }
    }
}

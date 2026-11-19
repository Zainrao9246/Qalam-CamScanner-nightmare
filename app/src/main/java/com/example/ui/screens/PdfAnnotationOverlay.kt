package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NormPoint
import com.example.model.PdfAnnotationTool
import com.example.model.PdfCommentPin
import com.example.model.PdfDocumentAnnotations
import com.example.model.PdfEyeMode
import com.example.model.PdfStroke
import com.example.model.PdfStrokeType
import com.example.ui.theme.CamScanAccent
import com.example.ui.theme.CamScanPrimary
import com.example.util.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

val HIGHLIGHT_COLORS = listOf(
    0xFFFFEB3BL, // Yellow
    0xFF69F0AEL, // Green
    0xFF40C4FFL, // Cyan
    0xFFFF80ABL  // Pink
)

val PEN_COLORS = listOf(
    0xFFE53935L, // Crimson Red
    0xFF1E88E5L, // Study Blue
    0xFF111827L, // Deep Charcoal
    0xFF00897BL  // Teal
)

@Composable
fun PdfPageAnnotationLayer(
    pageIndex: Int,
    strokes: List<PdfStroke>,
    comments: List<PdfCommentPin>,
    activeTool: PdfAnnotationTool,
    selectedColorArgb: Long,
    onAddStroke: (PdfStroke) -> Unit,
    onTapAddComment: (xRatio: Float, yRatio: Float) -> Unit,
    onTapCommentPin: (PdfCommentPin) -> Unit,
    onEraseAt: (xRatio: Float, yRatio: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeDraftPoints = remember { mutableStateListOf<NormPoint>() }
    val currentTool by rememberUpdatedState(activeTool)
    val currentColor by rememberUpdatedState(selectedColorArgb)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val boxW = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val boxH = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        val interactionModifier = when (activeTool) {
            PdfAnnotationTool.READ -> Modifier
            PdfAnnotationTool.COMMENT -> Modifier.pointerInput(pageIndex) {
                detectTapGestures { offset ->
                    val xr = (offset.x / boxW).coerceIn(0.05f, 0.95f)
                    val yr = (offset.y / boxH).coerceIn(0.05f, 0.95f)
                    onTapAddComment(xr, yr)
                }
            }
            PdfAnnotationTool.ERASER -> Modifier
                .pointerInput(pageIndex) {
                    detectTapGestures { offset ->
                        onEraseAt((offset.x / boxW).coerceIn(0f, 1f), (offset.y / boxH).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(pageIndex) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onEraseAt(
                            (change.position.x / boxW).coerceIn(0f, 1f),
                            (change.position.y / boxH).coerceIn(0f, 1f)
                        )
                    }
                }
            PdfAnnotationTool.HIGHLIGHT,
            PdfAnnotationTool.PEN,
            PdfAnnotationTool.UNDERLINE -> Modifier.pointerInput(pageIndex, activeTool, selectedColorArgb) {
                detectDragGestures(
                    onDragStart = { start ->
                        activeDraftPoints.clear()
                        activeDraftPoints.add(
                            NormPoint((start.x / boxW).coerceIn(0f, 1f), (start.y / boxH).coerceIn(0f, 1f))
                        )
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        activeDraftPoints.add(
                            NormPoint(
                                (change.position.x / boxW).coerceIn(0f, 1f),
                                (change.position.y / boxH).coerceIn(0f, 1f)
                            )
                        )
                    },
                    onDragEnd = {
                        if (activeDraftPoints.isNotEmpty()) {
                            val strokeType = when (currentTool) {
                                PdfAnnotationTool.HIGHLIGHT -> PdfStrokeType.HIGHLIGHT
                                PdfAnnotationTool.UNDERLINE -> PdfStrokeType.UNDERLINE
                                else -> PdfStrokeType.PEN
                            }
                            val widthRatio = when (strokeType) {
                                PdfStrokeType.HIGHLIGHT -> 0.032f
                                PdfStrokeType.UNDERLINE -> 0.0055f
                                PdfStrokeType.PEN -> 0.0055f
                            }
                            onAddStroke(
                                PdfStroke(
                                    pageIndex = pageIndex,
                                    type = strokeType,
                                    colorArgb = currentColor,
                                    widthRatio = widthRatio,
                                    points = activeDraftPoints.toList()
                                )
                            )
                            activeDraftPoints.clear()
                        }
                    },
                    onDragCancel = {
                        activeDraftPoints.clear()
                    }
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize().then(interactionModifier)) {
            val w = size.width
            val h = size.height

            fun renderStroke(
                type: PdfStrokeType,
                colorArgb: Long,
                widthRatio: Float,
                points: List<NormPoint>
            ) {
                if (points.isEmpty()) return
                val rawColor = Color(colorArgb)
                val drawColor = if (type == PdfStrokeType.HIGHLIGHT) rawColor.copy(alpha = 0.42f) else rawColor
                val strokeWidthPx = (widthRatio * w).coerceAtLeast(3f)
                val cap = if (type == PdfStrokeType.HIGHLIGHT) StrokeCap.Square else StrokeCap.Round

                if (type == PdfStrokeType.UNDERLINE && points.size >= 2) {
                    val start = points.first()
                    val end = points.last()
                    val yAvg = ((start.y + end.y) * 0.5f) * h
                    drawLine(
                        color = drawColor,
                        start = Offset(start.x * w, yAvg),
                        end = Offset(end.x * w, yAvg),
                        strokeWidth = strokeWidthPx,
                        cap = StrokeCap.Round
                    )
                } else if (points.size == 1) {
                    val p = points.first()
                    drawCircle(
                        color = drawColor,
                        radius = strokeWidthPx / 2f,
                        center = Offset(p.x * w, p.y * h)
                    )
                } else {
                    val path = Path()
                    points.forEachIndexed { idx, pt ->
                        val px = pt.x * w
                        val py = pt.y * h
                        if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    drawPath(
                        path = path,
                        color = drawColor,
                        style = Stroke(width = strokeWidthPx, cap = cap, join = StrokeJoin.Round)
                    )
                }
            }

            strokes.forEach { s ->
                renderStroke(s.type, s.colorArgb, s.widthRatio, s.points)
            }

            if (activeDraftPoints.isNotEmpty()) {
                val draftType = when (currentTool) {
                    PdfAnnotationTool.HIGHLIGHT -> PdfStrokeType.HIGHLIGHT
                    PdfAnnotationTool.UNDERLINE -> PdfStrokeType.UNDERLINE
                    else -> PdfStrokeType.PEN
                }
                val draftW = if (draftType == PdfStrokeType.HIGHLIGHT) 0.032f else 0.0055f
                renderStroke(draftType, currentColor, draftW, activeDraftPoints)
            }
        }

        // Sticky Comment Pins
        comments.forEachIndexed { index, pin ->
            val pinColor = Color(pin.colorArgb)
            val px = (pin.xRatio * boxW).roundToInt() - 16
            val py = (pin.yRatio * boxH).roundToInt() - 16
            Box(
                modifier = Modifier
                    .offset { IntOffset(px.coerceAtLeast(4), py.coerceAtLeast(4)) }
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(pinColor)
                    .border(2.dp, Color.White, CircleShape)
                    .clickable {
                        if (activeTool == PdfAnnotationTool.ERASER) {
                            onEraseAt(pin.xRatio, pin.yRatio)
                        } else {
                            onTapCommentPin(pin)
                        }
                    }
                    .testTag("pdf_comment_pin_${index}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
fun PdfAnnotationToolbar(
    activeTool: PdfAnnotationTool,
    selectedColorArgb: Long,
    onSelectTool: (PdfAnnotationTool) -> Unit,
    onSelectColor: (Long) -> Unit,
    onUndo: () -> Unit,
    onOpenPageNote: () -> Unit,
    onClearPage: () -> Unit,
    onExportAnnotated: () -> Unit
) {
    Surface(
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolChipButton(
                    icon = Icons.Default.Highlight,
                    label = "Highlight",
                    selected = activeTool == PdfAnnotationTool.HIGHLIGHT,
                    onClick = {
                        onSelectTool(PdfAnnotationTool.HIGHLIGHT)
                        if (selectedColorArgb !in HIGHLIGHT_COLORS) onSelectColor(HIGHLIGHT_COLORS.first())
                    }
                )
                ToolChipButton(
                    icon = Icons.Default.BorderColor,
                    label = "Pen",
                    selected = activeTool == PdfAnnotationTool.PEN,
                    onClick = {
                        onSelectTool(PdfAnnotationTool.PEN)
                        if (selectedColorArgb !in PEN_COLORS) onSelectColor(PEN_COLORS.first())
                    }
                )
                ToolChipButton(
                    icon = Icons.Default.FormatUnderlined,
                    label = "Underline",
                    selected = activeTool == PdfAnnotationTool.UNDERLINE,
                    onClick = {
                        onSelectTool(PdfAnnotationTool.UNDERLINE)
                        if (selectedColorArgb !in PEN_COLORS) onSelectColor(PEN_COLORS.first())
                    }
                )
                ToolChipButton(
                    icon = Icons.AutoMirrored.Filled.Comment,
                    label = "Comment",
                    selected = activeTool == PdfAnnotationTool.COMMENT,
                    onClick = { onSelectTool(PdfAnnotationTool.COMMENT) }
                )
                ToolChipButton(
                    icon = Icons.Default.CleaningServices,
                    label = "Eraser",
                    selected = activeTool == PdfAnnotationTool.ERASER,
                    onClick = { onSelectTool(PdfAnnotationTool.ERASER) }
                )
                ToolChipButton(
                    icon = Icons.Default.NoteAdd,
                    label = "Page Note",
                    selected = false,
                    onClick = onOpenPageNote
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val palette = if (activeTool == PdfAnnotationTool.HIGHLIGHT) HIGHLIGHT_COLORS else PEN_COLORS
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    palette.forEach { argb ->
                        val isSelected = selectedColorArgb == argb
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) CamScanPrimary else Color.Gray.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable { onSelectColor(argb) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected Color",
                                    tint = if (argb == 0xFFFFEB3BL) Color.Black else Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onUndo, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = onClearPage, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Page Marks")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = onExportAnnotated,
                        colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("export_annotated_pdf_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save PDF", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolChipButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) CamScanPrimary else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (selected) CamScanAccent else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfNotesAndBookmarksSheet(
    annotations: PdfDocumentAnnotations,
    onJumpToPage: (Int) -> Unit,
    onEditComment: (PdfCommentPin) -> Unit,
    onDeleteComment: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Bookmarks, Comments & Notes",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(10.dp))

            val hasAnything = annotations.bookmarkedPages.isNotEmpty() ||
                annotations.comments.isNotEmpty() ||
                annotations.pageNotes.isNotEmpty()

            if (!hasAnything) {
                Text(
                    text = "No bookmarks, sticky comments, or notes yet. Tap the Bookmark icon or Annotate to highlight & drop sticky notes anywhere on a page.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier.padding(vertical = 18.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(340.dp)
                ) {
                    if (annotations.bookmarkedPages.isNotEmpty()) {
                        item {
                            Text(
                                text = "BOOKMARKED PAGES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = CamScanPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                annotations.bookmarkedPages.sorted().forEach { pIdx ->
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = CamScanPrimary.copy(alpha = 0.12f)
                                        ),
                                        modifier = Modifier.clickable {
                                            onJumpToPage(pIdx)
                                            onDismiss()
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = CamScanPrimary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Page ${pIdx + 1}", fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
                        }
                    }

                    if (annotations.pageNotes.isNotEmpty()) {
                        item {
                            Text(
                                text = "PAGE STUDY NOTES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = CamScanPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        items(annotations.pageNotes.toSortedMap().entries.toList()) { (pIdx, note) ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onJumpToPage(pIdx)
                                        onDismiss()
                                    }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Page ${pIdx + 1}", fontWeight = FontWeight.Bold, color = CamScanPrimary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(note, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    if (annotations.comments.isNotEmpty()) {
                        item {
                            Text(
                                text = "STICKY COMMENTS (${annotations.comments.size})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = CamScanPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        items(annotations.comments.sortedBy { it.pageIndex }) { pin ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onJumpToPage(pin.pageIndex)
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(pin.colorArgb))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Page ${pin.pageIndex + 1} • ${pin.title.ifBlank { "Note" }}",
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (pin.body.isNotBlank()) {
                                            Text(pin.body, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    IconButton(onClick = { onEditComment(pin) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Comment", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { onDeleteComment(pin.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Comment", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfThumbnailsSheet(
    pdfFile: File,
    pageCount: Int,
    currentPage: Int,
    bookmarks: Set<Int>,
    onSelectPage: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "All Pages ($pageCount)",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.height(360.dp)
            ) {
                items(pageCount) { idx ->
                    val thumb by produceState<Bitmap?>(initialValue = null, pdfFile, idx) {
                        value = withContext(Dispatchers.IO) {
                            PdfEngine.renderPdfPage(pdfFile, idx, targetWidthPx = 400, eyeMode = PdfEyeMode.DAY)
                        }
                    }
                    val isCurrent = idx == currentPage
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (isCurrent) 2.5.dp else 1.dp,
                                color = if (isCurrent) CamScanPrimary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                onSelectPage(idx)
                                onDismiss()
                            }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.72f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White)
                        ) {
                            val bmp = thumb
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Page ${idx + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (idx in bookmarks) {
                                Icon(
                                    Icons.Default.Bookmark,
                                    contentDescription = "Bookmarked",
                                    tint = CamScanPrimary,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${idx + 1}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) CamScanPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CommentEditorDialog(
    initialTitle: String,
    initialBody: String,
    isEditing: Boolean,
    onSave: (title: String, body: String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var body by remember { mutableStateOf(initialBody) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Sticky Comment" else "Add Sticky Comment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Label (e.g. Exam Topic)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Comment / Explanation") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title.ifBlank { "Note" }, body) },
                colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary)
            ) {
                Text("Save", color = Color.White)
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = Color(0xFFE53935))
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

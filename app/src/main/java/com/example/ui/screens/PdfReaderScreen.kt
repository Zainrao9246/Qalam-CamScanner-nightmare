package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.PdfAnnotationTool
import com.example.model.PdfCommentPin
import com.example.model.PdfEyeMode
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.theme.CamScanAccent
import com.example.ui.theme.CamScanPrimary
import com.example.util.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val context = LocalContext.current
    val document by viewModel.activeDocument.collectAsState()
    val nightReadingMode by viewModel.isReaderNightMode.collectAsState()
    val annotations by viewModel.activePdfAnnotations.collectAsState()
    val statusMsg by viewModel.pdfStatusMessage.collectAsState()
    val doc = document ?: return

    LaunchedEffect(statusMsg) {
        if (!statusMsg.isNullOrBlank()) {
            Toast.makeText(context, statusMsg, Toast.LENGTH_SHORT).show()
            viewModel.pdfStatusMessage.value = null
        }
    }

    val pdfFile = remember(doc.filePath) { doc.filePath?.let { File(it) } }
    var pageCount by remember(pdfFile) { mutableIntStateOf(doc.totalPages.coerceAtLeast(1)) }

    LaunchedEffect(pdfFile) {
        if (pdfFile != null && pdfFile.exists()) {
            val count = withContext(Dispatchers.IO) { PdfEngine.getPdfPageCount(pdfFile) }
            if (count > 0) pageCount = count
        }
    }

    var eyeMode by remember(nightReadingMode) {
        mutableStateOf(if (nightReadingMode) PdfEyeMode.NIGHT else PdfEyeMode.DAY)
    }
    var isHorizontalPager by remember { mutableStateOf(false) }

    // Annotation State
    var isAnnotateBarVisible by remember { mutableStateOf(false) }
    var activeTool by remember { mutableStateOf(PdfAnnotationTool.READ) }
    var selectedColorArgb by remember { mutableLongStateOf(HIGHLIGHT_COLORS.first()) }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = doc.lastReadPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    )
    val pagerState = rememberPagerState(
        initialPage = doc.lastReadPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
        pageCount = { pageCount }
    )

    val currentVisiblePage by remember(isHorizontalPager) {
        derivedStateOf {
            if (isHorizontalPager) pagerState.currentPage else listState.firstVisibleItemIndex
        }
    }

    LaunchedEffect(currentVisiblePage) {
        viewModel.updateLastReadPage(doc.id, currentVisiblePage)
    }

    // Zoom & Pan State
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newZoom = (zoomScale * zoomChange).coerceIn(1f, 4f)
        zoomScale = newZoom
        panOffset = if (newZoom > 1.02f) {
            Offset(
                x = (panOffset.x + panChange.x).coerceIn(-1200f, 1200f),
                y = (panOffset.y + panChange.y).coerceIn(-1200f, 1200f)
            )
        } else {
            Offset.Zero
        }
    }

    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpInput by remember { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showNotesSheet by remember { mutableStateOf(false) }
    var showThumbnailsSheet by remember { mutableStateOf(false) }

    var pendingCommentDraft by remember { mutableStateOf<Triple<Int, Float, Float>?>(null) }
    var editingCommentPin by remember { mutableStateOf<PdfCommentPin?>(null) }
    var showPageNoteDialog by remember { mutableStateOf(false) }
    var pageNoteInput by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    fun jumpToPage(targetIdx: Int) {
        val safe = targetIdx.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        scope.launch {
            if (isHorizontalPager) pagerState.animateScrollToPage(safe) else listState.scrollToItem(safe)
        }
    }

    val readerBg = when (eyeMode) {
        PdfEyeMode.DAY -> MaterialTheme.colorScheme.background
        PdfEyeMode.SEPIA -> Color(0xFFF3E9D2)
        PdfEyeMode.NIGHT -> Color(0xFF101312)
    }

    val isCurrentBookmarked = currentVisiblePage in annotations.bookmarkedPages
    val notesCount = annotations.bookmarkedPages.size + annotations.comments.size + annotations.pageNotes.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = doc.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                        Text(
                            text = "Page ${currentVisiblePage + 1}/$pageCount • ${(zoomScale * 100).toInt()}% • ${eyeMode.label}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("pdf_reader_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.togglePdfBookmark(currentVisiblePage) },
                        modifier = Modifier.testTag("pdf_bookmark_btn")
                    ) {
                        Icon(
                            imageVector = if (isCurrentBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark Page",
                            tint = if (isCurrentBookmarked) CamScanPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = { showNotesSheet = true },
                        modifier = Modifier.testTag("pdf_notes_sheet_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (notesCount > 0) {
                                    Badge { Text("$notesCount") }
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = "Notes & Bookmarks")
                        }
                    }

                    IconButton(
                        onClick = {
                            eyeMode = when (eyeMode) {
                                PdfEyeMode.DAY -> PdfEyeMode.SEPIA
                                PdfEyeMode.SEPIA -> PdfEyeMode.NIGHT
                                PdfEyeMode.NIGHT -> PdfEyeMode.DAY
                            }
                            viewModel.setReaderNightMode(eyeMode == PdfEyeMode.NIGHT)
                        },
                        modifier = Modifier.testTag("reader_night_mode_btn")
                    ) {
                        Icon(
                            imageVector = when (eyeMode) {
                                PdfEyeMode.DAY -> Icons.Default.WbSunny
                                PdfEyeMode.SEPIA -> Icons.Default.BrightnessHigh
                                PdfEyeMode.NIGHT -> Icons.Default.Brightness4
                            },
                            contentDescription = "Cycle Day / Sepia / Night",
                            tint = if (eyeMode != PdfEyeMode.DAY) CamScanAccent else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.testTag("pdf_more_tools_btn")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More PDF Tools")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Pages Thumbnails") },
                                leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showThumbnailsSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isHorizontalPager) "Switch to Vertical Scroll" else "Switch to Page Flip") },
                                leadingIcon = {
                                    Icon(
                                        if (isHorizontalPager) Icons.Default.SwapVert else Icons.Default.SwapHoriz,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    isHorizontalPager = !isHorizontalPager
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add / Edit Page Note") },
                                leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    pageNoteInput = annotations.pageNotes[currentVisiblePage].orEmpty()
                                    showPageNoteDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save Annotated PDF Copy") },
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.exportAnnotatedPdfDocument(doc, includeNotesSummary = true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("PDF Power Suite (Merge/Split/Compress)") },
                                leadingIcon = { Icon(Icons.Default.Build, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.navigateTo(Screen.PDF_TOOLS)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Convert PDF → Word (.docx)") },
                                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.convertPdfDocumentToWord(doc)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share PDF") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.shareDocumentFile(doc)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                if (isAnnotateBarVisible) {
                    PdfAnnotationToolbar(
                        activeTool = activeTool,
                        selectedColorArgb = selectedColorArgb,
                        onSelectTool = { activeTool = it },
                        onSelectColor = { selectedColorArgb = it },
                        onUndo = { viewModel.undoLastPdfStroke(currentVisiblePage) },
                        onOpenPageNote = {
                            pageNoteInput = annotations.pageNotes[currentVisiblePage].orEmpty()
                            showPageNoteDialog = true
                        },
                        onClearPage = { viewModel.clearPdfPageAnnotations(currentVisiblePage) },
                        onExportAnnotated = { viewModel.exportAnnotatedPdfDocument(doc, includeNotesSummary = true) }
                    )
                }

                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                jumpInput = "${currentVisiblePage + 1}"
                                showJumpDialog = true
                            },
                            modifier = Modifier.testTag("pdf_jump_page_btn")
                        ) {
                            Text(
                                text = "${currentVisiblePage + 1}/$pageCount",
                                fontWeight = FontWeight.Bold,
                                color = CamScanPrimary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    zoomScale = (zoomScale - 0.35f).coerceAtLeast(1f)
                                    if (zoomScale <= 1.02f) {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(20.dp))
                            }
                            Text(
                                text = "${(zoomScale * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                            IconButton(
                                onClick = { zoomScale = (zoomScale + 0.35f).coerceAtMost(4f) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(20.dp))
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    isAnnotateBarVisible = !isAnnotateBarVisible
                                    activeTool = if (isAnnotateBarVisible) PdfAnnotationTool.HIGHLIGHT else PdfAnnotationTool.READ
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isAnnotateBarVisible) CamScanPrimary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = if (isAnnotateBarVisible) Color.White else MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("pdf_toggle_annotate_btn")
                            ) {
                                Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isAnnotateBarVisible) "Done" else "Annotate",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Button(
                                onClick = {
                                    if (pdfFile != null) {
                                        scope.launch {
                                            val bmp = withContext(Dispatchers.IO) {
                                                PdfEngine.renderPdfPage(pdfFile, currentVisiblePage, 1400, false)
                                            }
                                            if (bmp != null) {
                                                viewModel.runOcrOnBitmap(bmp)
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("pdf_ocr_page_btn")
                            ) {
                                Icon(Icons.Default.TextFields, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("OCR", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        val doubleTapModifier = if (activeTool == PdfAnnotationTool.READ) {
            Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (zoomScale > 1.15f) {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        } else {
                            zoomScale = 2.2f
                        }
                    }
                )
            }
        } else Modifier

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(readerBg)
                .padding(padding)
                .then(doubleTapModifier)
                .transformable(state = transformState, enabled = activeTool == PdfAnnotationTool.READ)
        ) {
            if (pdfFile == null || !pdfFile.exists()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("PDF file not found on device.")
                }
            } else if (isHorizontalPager) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = activeTool == PdfAnnotationTool.READ && zoomScale <= 1.05f,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        }
                ) { pageIndex ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PdfPageView(
                            pdfFile = pdfFile,
                            pageIndex = pageIndex,
                            totalPages = pageCount,
                            eyeMode = eyeMode,
                            annotations = annotations,
                            activeTool = activeTool,
                            selectedColorArgb = selectedColorArgb,
                            onAddStroke = { viewModel.addPdfStroke(it) },
                            onTapAddComment = { xr, yr -> pendingCommentDraft = Triple(pageIndex, xr, yr) },
                            onTapCommentPin = { editingCommentPin = it },
                            onEraseAt = { xr, yr -> viewModel.erasePdfAnnotationAt(pageIndex, xr, yr) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    userScrollEnabled = activeTool == PdfAnnotationTool.READ,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        }
                ) {
                    items(pageCount, key = { it }) { pageIndex ->
                        PdfPageView(
                            pdfFile = pdfFile,
                            pageIndex = pageIndex,
                            totalPages = pageCount,
                            eyeMode = eyeMode,
                            annotations = annotations,
                            activeTool = activeTool,
                            selectedColorArgb = selectedColorArgb,
                            onAddStroke = { viewModel.addPdfStroke(it) },
                            onTapAddComment = { xr, yr -> pendingCommentDraft = Triple(pageIndex, xr, yr) },
                            onTapCommentPin = { editingCommentPin = it },
                            onEraseAt = { xr, yr -> viewModel.erasePdfAnnotationAt(pageIndex, xr, yr) }
                        )
                    }
                }
            }
        }
    }

    // Jump to Page Dialog
    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Go to Page") },
            text = {
                OutlinedTextField(
                    value = jumpInput,
                    onValueChange = { jumpInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Page (1 - $pageCount)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = (jumpInput.toIntOrNull() ?: 1).coerceIn(1, pageCount) - 1
                        showJumpDialog = false
                        jumpToPage(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary)
                ) {
                    Text("Jump", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Add Sticky Comment Dialog
    pendingCommentDraft?.let { (pageIdx, xr, yr) ->
        CommentEditorDialog(
            initialTitle = "",
            initialBody = "",
            isEditing = false,
            onSave = { title, body ->
                viewModel.addOrUpdatePdfComment(
                    PdfCommentPin(
                        pageIndex = pageIdx,
                        xRatio = xr,
                        yRatio = yr,
                        title = title,
                        body = body
                    )
                )
                pendingCommentDraft = null
            },
            onDismiss = { pendingCommentDraft = null }
        )
    }

    // Edit Sticky Comment Dialog
    editingCommentPin?.let { pin ->
        CommentEditorDialog(
            initialTitle = pin.title,
            initialBody = pin.body,
            isEditing = true,
            onSave = { title, body ->
                viewModel.addOrUpdatePdfComment(pin.copy(title = title, body = body))
                editingCommentPin = null
            },
            onDelete = {
                viewModel.deletePdfComment(pin.id)
                editingCommentPin = null
            },
            onDismiss = { editingCommentPin = null }
        )
    }

    // Quick Per-Page Study Note Dialog
    if (showPageNoteDialog) {
        AlertDialog(
            onDismissRequest = { showPageNoteDialog = false },
            title = { Text("Study Note — Page ${currentVisiblePage + 1}") },
            text = {
                OutlinedTextField(
                    value = pageNoteInput,
                    onValueChange = { pageNoteInput = it },
                    label = { Text("Summary / Key formulas on this page") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.savePdfPageNote(currentVisiblePage, pageNoteInput)
                        showPageNoteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary)
                ) {
                    Text("Save Note", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPageNoteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showNotesSheet) {
        PdfNotesAndBookmarksSheet(
            annotations = annotations,
            onJumpToPage = { jumpToPage(it) },
            onEditComment = { editingCommentPin = it },
            onDeleteComment = { viewModel.deletePdfComment(it) },
            onDismiss = { showNotesSheet = false }
        )
    }

    if (showThumbnailsSheet && pdfFile != null) {
        PdfThumbnailsSheet(
            pdfFile = pdfFile,
            pageCount = pageCount,
            currentPage = currentVisiblePage,
            bookmarks = annotations.bookmarkedPages,
            onSelectPage = { jumpToPage(it) },
            onDismiss = { showThumbnailsSheet = false }
        )
    }
}

@Composable
private fun PdfPageView(
    pdfFile: File,
    pageIndex: Int,
    totalPages: Int,
    eyeMode: PdfEyeMode,
    annotations: com.example.model.PdfDocumentAnnotations,
    activeTool: PdfAnnotationTool,
    selectedColorArgb: Long,
    onAddStroke: (com.example.model.PdfStroke) -> Unit,
    onTapAddComment: (xRatio: Float, yRatio: Float) -> Unit,
    onTapCommentPin: (PdfCommentPin) -> Unit,
    onEraseAt: (xRatio: Float, yRatio: Float) -> Unit
) {
    val pageBitmap by produceState<Bitmap?>(initialValue = null, pdfFile, pageIndex, eyeMode) {
        value = withContext(Dispatchers.IO) {
            PdfEngine.renderPdfPage(pdfFile, pageIndex, targetWidthPx = 1200, eyeMode = eyeMode)
        }
    }

    val pageStrokes = remember(annotations.strokes, pageIndex) {
        annotations.strokes.filter { it.pageIndex == pageIndex }
    }
    val pageComments = remember(annotations.comments, pageIndex) {
        annotations.comments.filter { it.pageIndex == pageIndex }
    }
    val pageNote = annotations.pageNotes[pageIndex]

    Card(
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (eyeMode) {
                PdfEyeMode.DAY -> Color.White
                PdfEyeMode.SEPIA -> Color(0xFFFBF2DC)
                PdfEyeMode.NIGHT -> Color(0xFF1B1F1E)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (eyeMode == PdfEyeMode.NIGHT) Color(0xFF2D3533) else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(10.dp)
            )
    ) {
        Column {
            val bmp = pageBitmap
            val aspect = if (bmp != null && bmp.height > 0) bmp.width.toFloat() / bmp.height.toFloat() else 0.707f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page ${pageIndex + 1}",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                    PdfPageAnnotationLayer(
                        pageIndex = pageIndex,
                        strokes = pageStrokes,
                        comments = pageComments,
                        activeTool = activeTool,
                        selectedColorArgb = selectedColorArgb,
                        onAddStroke = onAddStroke,
                        onTapAddComment = onTapAddComment,
                        onTapCommentPin = onTapCommentPin,
                        onEraseAt = onEraseAt,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(color = CamScanPrimary, modifier = Modifier.size(28.dp))
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${pageIndex + 1} / $totalPages",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White)
                    )
                }
            }

            if (!pageNote.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CamScanPrimary.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.NoteAdd, contentDescription = null, tint = CamScanPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = pageNote,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
        }
    }
}

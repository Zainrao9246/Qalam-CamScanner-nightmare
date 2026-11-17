package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.DocKind
import com.example.model.Document
import com.example.ui.LibraryFilter
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.SortOrder
import com.example.ui.ViewMode
import com.example.ui.theme.CamScanAccent
import com.example.ui.theme.CamScanPrimary
import com.example.ui.theme.CamScanTertiary
import com.example.util.FileTemplates
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val documents by viewModel.filteredDocuments.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedSubject by viewModel.selectedSubjectFilter.collectAsState()
    val subjectTags by viewModel.availableSubjectTags.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val conversionProgress by viewModel.conversionProgress.collectAsState()

    var isToolsDrawerOpen by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedDocIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionMode = selectedDocIds.isNotEmpty()

    var showBatchTagDialog by remember { mutableStateOf(false) }
    var batchTagInput by remember { mutableStateOf("") }

    var docToRename by remember { mutableStateOf<Document?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var renameSubjectInput by remember { mutableStateOf("") }

    var showNewNoteDialog by remember { mutableStateOf(false) }
    var newNoteTitle by remember { mutableStateOf("") }
    var newNoteSubject by remember { mutableStateOf("") }

    var showNewSheetDialog by remember { mutableStateOf(false) }
    var newSheetTitle by remember { mutableStateOf("") }
    var newSheetSubject by remember { mutableStateOf("") }

    // Create New File Studio (Word, Excel, HTML/CSS/JS/TS, Markdown, code & data files)
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileTitle by remember { mutableStateOf("") }
    var newFileSubject by remember { mutableStateOf("") }
    var selectedNewFileExt by remember { mutableStateOf("html") }

    BackHandler(enabled = isToolsDrawerOpen || isSelectionMode) {
        if (isToolsDrawerOpen) isToolsDrawerOpen = false else selectedDocIds = emptySet()
    }

    // Open any file (PDF, Word DOCX, TXT, Image)
    val openAnyFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importFileFromUri(it) }
    }

    // Pick PDF specifically for PDF -> Word conversion
    val pdfToWordLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.convertUriPdfToWord(it) }
    }

    // Pick Word (.docx / .txt) for Word -> PDF conversion
    val wordToPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importWordUriAndConvertToPdf(it) }
    }

    // Pick Word (.docx / .txt) to Edit & Clean
    val wordEditLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importWordUriAndOpenEditor(it) }
    }

    // Pick Multiple Pictures for Pictures -> PDF (with Rearranging)
    val picsToPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.startPicturesToPdf(uris)
        }
    }

    // Single Image Scan Picker
    val singleScanPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.onImagePickedForScan(it) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    if (isSelectionMode) {
                        val selectedPdfDocs = documents.filter { it.id in selectedDocIds && it.kind == DocKind.PDF }
                        TopAppBar(
                            title = { Text("${selectedDocIds.size} selected", fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(onClick = { selectedDocIds = emptySet() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel Selection")
                                }
                            },
                            actions = {
                                IconButton(onClick = {
                                    selectedDocIds = if (selectedDocIds.size == documents.size) emptySet()
                                    else documents.map { it.id }.toSet()
                                }) {
                                    Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                                }
                                if (selectedPdfDocs.size >= 2) {
                                    IconButton(onClick = {
                                        viewModel.mergePdfDocuments(selectedPdfDocs, "Merged_${selectedPdfDocs.size}_PDFs")
                                        selectedDocIds = emptySet()
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = "Merge Selected PDFs", tint = CamScanPrimary)
                                    }
                                }
                                IconButton(onClick = {
                                    batchTagInput = ""
                                    showBatchTagDialog = true
                                }) {
                                    Icon(Icons.Default.Label, contentDescription = "Tag Subject")
                                }
                                IconButton(onClick = {
                                    viewModel.batchDeleteDocuments(selectedDocIds)
                                    selectedDocIds = emptySet()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                    } else {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_qalam_logo),
                                        contentDescription = "Qalam Logo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Qalam",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = (-0.5).sp
                                            )
                                        )
                                        Text(
                                            text = "${documents.size} documents • Ad-free",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        )
                                    }
                                }
                            },
                            actions = {
                                Box {
                                    IconButton(
                                        onClick = { showSortMenu = true },
                                        modifier = Modifier.testTag("sort_docs_btn")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort Documents")
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        SortOrder.values().forEach { order ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = order.label,
                                                        fontWeight = if (order == sortOrder) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (order == sortOrder) CamScanPrimary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.sortOrder.value = order
                                                    showSortMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        viewModel.viewMode.value =
                                            if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
                                    },
                                    modifier = Modifier.testTag("toggle_view_mode_btn")
                                ) {
                                    Icon(
                                        imageVector = if (viewMode == ViewMode.GRID) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                        contentDescription = "Toggle Grid/List"
                                    )
                                }

                                // Upper-Right Three-Lines Menu Button (☰)
                                IconButton(
                                    onClick = { isToolsDrawerOpen = true },
                                    modifier = Modifier.testTag("top_right_menu_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "All Features & Tools Menu",
                                        tint = CamScanPrimary
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }

                    // Quick Action Strip (Direct 1-tap access for VU student workflow)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickPillButton(
                            icon = Icons.Default.FolderOpen,
                            label = "Open File",
                            testTag = "quick_open_file_btn",
                            onClick = {
                                openAnyFileLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        QuickPillButton(
                            icon = Icons.Default.CameraAlt,
                            label = "Camera",
                            testTag = "quick_camera_btn",
                            onClick = { viewModel.navigateTo(Screen.CAMERA_SCAN) },
                            modifier = Modifier.weight(1f)
                        )
                        QuickPillButton(
                            icon = Icons.Default.Collections,
                            label = "Pics→PDF",
                            testTag = "quick_pics_pdf_btn",
                            onClick = {
                                picsToPdfLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        QuickPillButton(
                            icon = Icons.Default.EditNote,
                            label = "New Note",
                            testTag = "quick_new_note_btn",
                            onClick = {
                                newNoteTitle = ""
                                newNoteSubject = ""
                                showNewNoteDialog = true
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.searchQuery.value = it },
                        placeholder = { Text("Search PDFs, notes, subjects (e.g. CS101)...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("search_bar_input"),
                        shape = RoundedCornerShape(22.dp),
                        singleLine = true
                    )

                    // Library Type Filter Chips
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(LibraryFilter.values()) { filter ->
                            val isSelected = filter == selectedFilter
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) CamScanPrimary else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) CamScanAccent else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { viewModel.selectedFilter.value = filter }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                                    .testTag("filter_chip_${filter.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }

                    // Course / Subject Filter Chips (if any tagged docs exist)
                    if (subjectTags.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                val allSubjSelected = selectedSubject == null
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (allSubjSelected) CamScanPrimary.copy(alpha = 0.16f) else Color.Transparent)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                        .clickable { viewModel.selectedSubjectFilter.value = null }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "All Subjects",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (allSubjSelected) CamScanPrimary else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (allSubjSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                }
                            }
                            items(subjectTags) { tag ->
                                val active = selectedSubject.equals(tag, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (active) CamScanPrimary.copy(alpha = 0.18f) else Color.Transparent)
                                        .border(
                                            1.dp,
                                            if (active) CamScanPrimary else MaterialTheme.colorScheme.outlineVariant,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            viewModel.selectedSubjectFilter.value = if (active) null else tag
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (active) CamScanPrimary else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { viewModel.navigateTo(Screen.CAMERA_SCAN) },
                    containerColor = CamScanPrimary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .testTag("scan_fab_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Scan Document")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scan",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (documents.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(CamScanPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Empty Library",
                                tint = CamScanPrimary,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching documents" else "Your Study Library is Ready",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Open PDF handouts or Word notes from your mobile, scan pages with the camera, or tap the ☰ menu in the top-right for all converters & cleaners.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                                textAlign = TextAlign.Center
                            )
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    openAnyFileLauncher.launch(arrayOf("*/*"))
                                }
                            ) {
                                Text("Open from Phone")
                            }
                            Button(
                                onClick = {
                                    newNoteTitle = ""
                                    newNoteSubject = ""
                                    showNewNoteDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary)
                            ) {
                                Text("Create Note", color = Color.White)
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (viewMode == ViewMode.GRID) 2 else 1),
                        contentPadding = PaddingValues(
                            start = 14.dp,
                            end = 14.dp,
                            top = 10.dp,
                            bottom = 84.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(documents, key = { it.id }) { doc ->
                            val isSelected = doc.id in selectedDocIds
                            DocumentCard(
                                document = doc,
                                isGrid = viewMode == ViewMode.GRID,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedDocIds = if (isSelected) selectedDocIds - doc.id else selectedDocIds + doc.id
                                    } else {
                                        viewModel.openDocument(doc)
                                    }
                                },
                                onLongClick = {
                                    selectedDocIds = selectedDocIds + doc.id
                                },
                                onToggleStar = { viewModel.toggleStar(doc.id) },
                                onRename = {
                                    docToRename = doc
                                    renameInput = doc.title
                                    renameSubjectInput = doc.subjectTag
                                },
                                onDelete = { viewModel.deleteDocument(doc.id) },
                                onShare = { viewModel.shareDocumentFile(doc) },
                                onConvert = {
                                    when (doc.kind) {
                                        DocKind.PDF -> viewModel.convertPdfDocumentToWord(doc)
                                        DocKind.WORD -> viewModel.openDocument(doc, startWordInReadMode = false)
                                        DocKind.EXCEL -> viewModel.openDocument(doc)
                                        DocKind.SLIDES -> viewModel.openDocument(doc)
                                        DocKind.CODE -> viewModel.openDocument(doc)
                                        DocKind.SCAN -> viewModel.preparePdfExport(doc)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Scrim + Right-Side 3-Lines Tools Drawer (Upper-Right Menu)
        AnimatedVisibility(
            visible = isToolsDrawerOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.48f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isToolsDrawerOpen = false }
            )
        }

        AnimatedVisibility(
            visible = isToolsDrawerOpen,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 290.dp, max = 330.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {} // Consume clicks inside drawer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Tools & Settings",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "By Shaheer Rao • Clean & ad-free",
                                style = MaterialTheme.typography.bodySmall.copy(color = CamScanPrimary)
                            )
                        }
                        IconButton(
                            onClick = { isToolsDrawerOpen = false },
                            modifier = Modifier.testTag("close_drawer_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Menu")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dark / Light Theme Switch
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = null,
                                    tint = CamScanPrimary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isDarkTheme) "Dark Theme" else "Light Theme",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Tap to switch appearance",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    )
                                }
                            }
                            Switch(
                                checked = isDarkTheme,
                                onCheckedChange = { viewModel.setDarkTheme(it) },
                                modifier = Modifier.testTag("theme_switch_btn")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "CONVERTERS & PDF BUILDER",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    DrawerToolItem(
                        icon = Icons.Default.Collections,
                        title = "Pictures to PDF",
                        subtitle = "Rearrange photos, crop, filter & combine",
                        testTag = "drawer_pics_to_pdf",
                        onClick = {
                            isToolsDrawerOpen = false
                            picsToPdfLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    DrawerToolItem(
                        icon = Icons.AutoMirrored.Filled.Article,
                        title = "PDF to Word (.docx)",
                        subtitle = "Extract & convert PDF into editable Word",
                        testTag = "drawer_pdf_to_word",
                        onClick = {
                            isToolsDrawerOpen = false
                            pdfToWordLauncher.launch(arrayOf("application/pdf"))
                        }
                    )

                    DrawerToolItem(
                        icon = Icons.Default.PictureAsPdf,
                        title = "Word to PDF (.pdf)",
                        subtitle = "Convert .docx or .txt notes into crisp PDF",
                        testTag = "drawer_word_to_pdf",
                        onClick = {
                            isToolsDrawerOpen = false
                            wordToPdfLauncher.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "text/plain"
                                )
                            )
                        }
                    )

                    DrawerToolItem(
                        icon = Icons.Default.Build,
                        title = "PDF Power Suite",
                        subtitle = "Merge PDFs, Split/Reorder/Rotate, Compress & Watermark",
                        testTag = "drawer_pdf_suite",
                        onClick = {
                            isToolsDrawerOpen = false
                            viewModel.navigateTo(Screen.PDF_TOOLS)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        text = "WORD CLEANER & STUDY NOTES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    DrawerToolItem(
                        icon = Icons.Default.AutoFixHigh,
                        title = "Edit & Clean Word Doc",
                        subtitle = "Open .docx from phone & clean messy text",
                        testTag = "drawer_clean_word",
                        onClick = {
                            isToolsDrawerOpen = false
                            wordEditLauncher.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "text/plain"
                                )
                            )
                        }
                    )

                    DrawerToolItem(
                        icon = Icons.Default.EditNote,
                        title = "Write New Study Note",
                        subtitle = "Create clean formatted .docx for your subject",
                        testTag = "drawer_new_note",
                        onClick = {
                            isToolsDrawerOpen = false
                            newNoteTitle = ""
                            newNoteSubject = ""
                            showNewNoteDialog = true
                        }
                    )

                    DrawerToolItem(
                        icon = Icons.Default.TableChart,
                        title = "New Spreadsheet (.xlsx)",
                        subtitle = "Create editable table for marks, expenses & schedules",
                        testTag = "drawer_new_sheet",
                        onClick = {
                            isToolsDrawerOpen = false
                            newSheetTitle = ""
                            newSheetSubject = ""
                            showNewSheetDialog = true
                        }
                    )

                    DrawerToolItem(
                        icon = Icons.Default.Code,
                        title = "Create New File Studio",
                        subtitle = "Word, Excel, HTML/CSS/JS/TS, Markdown, JSON, Python, Java, Kotlin, C/C++, SQL, YAML & more",
                        testTag = "drawer_new_file",
                        onClick = {
                            isToolsDrawerOpen = false
                            newFileTitle = ""
                            newFileSubject = ""
                            showNewFileDialog = true
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        text = "SCANNER & MOBILE IMPORT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    DrawerToolItem(
                        icon = Icons.Default.CameraAlt,
                        title = "Camera Document Scan",
                        subtitle = "Document, ID Card (Front+Back) & 2-Page Book Split",
                        testTag = "drawer_camera_scan",
                        onClick = {
                            isToolsDrawerOpen = false
                            viewModel.navigateTo(Screen.CAMERA_SCAN)
                        }
                    )

                    DrawerToolItem(
                        icon = Icons.Default.QrCodeScanner,
                        title = "QR & Barcode Scanner",
                        subtitle = "Scan QR codes, links, ISBN & Wi-Fi offline",
                        testTag = "drawer_qr_scan",
                        onClick = {
                            isToolsDrawerOpen = false
                            viewModel.navigateTo(Screen.QR_SCAN)
                        }
                    )

                    DrawerToolItem(
                        icon = Icons.Default.PhotoLibrary,
                        title = "Scan Photo from Gallery",
                        subtitle = "Auto-edge crop & enhance a single photo of notes",
                        testTag = "drawer_gallery_scan",
                        onClick = {
                            isToolsDrawerOpen = false
                            singleScanPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    DrawerToolItem(
                        icon = Icons.Default.FolderOpen,
                        title = "Open Any File from Phone",
                        subtitle = "PDF, Word/ODT/EPUB/HTML, Excel/ODS/CSV, Slides/ODP, Code/Logs & Images",
                        testTag = "drawer_open_file",
                        onClick = {
                            isToolsDrawerOpen = false
                            openAnyFileLauncher.launch(arrayOf("*/*"))
                        }
                    )
                }
            }
        }

        // Conversion / Processing Progress Overlay
        if (!conversionProgress.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = CamScanPrimary)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = conversionProgress!!,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }

    // Rename & Subject Tag Dialog
    docToRename?.let { doc ->
        AlertDialog(
            onDismissRequest = { docToRename = null },
            title = { Text("Edit Document Info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        label = { Text("Document Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = renameSubjectInput,
                        onValueChange = { renameSubjectInput = it },
                        label = { Text("Subject / Course Tag (e.g. CS101)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            viewModel.renameDocument(doc.id, renameInput.trim(), renameSubjectInput.trim())
                        }
                        docToRename = null
                    }
                ) { Text("Save", color = CamScanPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { docToRename = null }) { Text("Cancel") }
            }
        )
    }

    // Create New Study Note (.docx) Dialog
    if (showNewNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNewNoteDialog = false },
            title = { Text("New Study Note (.docx)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newNoteTitle,
                        onValueChange = { newNoteTitle = it },
                        label = { Text("Note Title (e.g. Lecture 14 Trees)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newNoteSubject,
                        onValueChange = { newNoteSubject = it },
                        label = { Text("Subject Code (e.g. CS301)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNewNoteDialog = false
                        viewModel.createNewWordNote(newNoteTitle.trim(), newNoteSubject.trim())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary)
                ) {
                    Text("Create & Edit", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewNoteDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Create New Spreadsheet (.xlsx) Dialog
    if (showNewSheetDialog) {
        AlertDialog(
            onDismissRequest = { showNewSheetDialog = false },
            title = { Text("New Spreadsheet (.xlsx)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newSheetTitle,
                        onValueChange = { newSheetTitle = it },
                        label = { Text("Sheet Title (e.g. Semester GPA)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newSheetSubject,
                        onValueChange = { newSheetSubject = it },
                        label = { Text("Subject Code (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNewSheetDialog = false
                        viewModel.createNewSpreadsheet(newSheetTitle.trim(), newSheetSubject.trim())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary)
                ) {
                    Text("Create Sheet", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewSheetDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Create New File Studio Dialog (Word / Excel / HTML / CSS / JS / TS / code & data files)
    if (showNewFileDialog) {
        val selectedType = FileTemplates.findByExt(selectedNewFileExt)
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Create New File") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Pick a file type — Word & Excel use their editors, everything else opens in the code editor with an offline preview (HTML & Markdown).",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FileTemplates.CREATE_TYPES.forEach { type ->
                            val active = type.ext == selectedNewFileExt
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) CamScanPrimary.copy(alpha = 0.18f) else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (active) CamScanPrimary else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedNewFileExt = type.ext }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = type.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (active) CamScanPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }
                        }
                    }
                    selectedType?.let { type ->
                        Text(
                            text = "${type.label} • ${type.subtitle}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = CamScanPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    OutlinedTextField(
                        value = newFileTitle,
                        onValueChange = { newFileTitle = it },
                        label = { Text("File Name (e.g. Index Page, DSA Notes)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newFileSubject,
                        onValueChange = { newFileSubject = it },
                        label = { Text("Subject Code (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ext = selectedNewFileExt
                        showNewFileDialog = false
                        when (ext) {
                            "docx" -> viewModel.createNewWordNote(newFileTitle.trim(), newFileSubject.trim())
                            "xlsx" -> viewModel.createNewSpreadsheet(newFileTitle.trim(), newFileSubject.trim())
                            "pptx" -> viewModel.createNewPresentation(newFileTitle.trim(), newFileSubject.trim())
                            else -> viewModel.createNewCodeFile(newFileTitle.trim(), ext, newFileSubject.trim())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary)
                ) {
                    Text("Create File", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Batch Subject Tag Dialog
    if (showBatchTagDialog) {
        AlertDialog(
            onDismissRequest = { showBatchTagDialog = false },
            title = { Text("Assign Subject Tag (${selectedDocIds.size} docs)") },
            text = {
                OutlinedTextField(
                    value = batchTagInput,
                    onValueChange = { batchTagInput = it },
                    label = { Text("Course / Tag (e.g. CS301)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.batchTagDocuments(selectedDocIds, batchTagInput)
                        showBatchTagDialog = false
                        selectedDocIds = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary)
                ) { Text("Apply Tag", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showBatchTagDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun QuickPillButton(
    icon: ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = CamScanPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DrawerToolItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CamScanPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = CamScanPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                ),
                maxLines = 2
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentCard(
    document: Document,
    isGrid: Boolean,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onToggleStar: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onConvert: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateStr = remember(document.updatedAt) {
        SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(document.updatedAt))
    }

    val badgeColor = when (document.kind) {
        DocKind.PDF -> Color(0xFFD32F2F)
        DocKind.WORD -> Color(0xFF1565C0)
        DocKind.EXCEL -> Color(0xFF2E7D32)
        DocKind.SLIDES -> Color(0xFFD84315)
        DocKind.CODE -> Color(0xFF6A1B9A)
        DocKind.SCAN -> CamScanPrimary
    }

    val kindIcon = when (document.kind) {
        DocKind.PDF -> Icons.Default.PictureAsPdf
        DocKind.WORD -> Icons.AutoMirrored.Filled.Article
        DocKind.EXCEL -> Icons.Default.TableChart
        DocKind.SLIDES -> Icons.Default.Slideshow
        DocKind.CODE -> Icons.Default.Code
        DocKind.SCAN -> Icons.Default.Description
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CamScanPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 5.dp else 2.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) CamScanPrimary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("doc_card_${document.id}")
    ) {
        if (isGrid) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.82f)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    if (document.thumbnailPath != null) {
                        AsyncImage(
                            model = File(document.thumbnailPath!!),
                            contentDescription = document.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = kindIcon,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = document.kind.badge,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    // Format Badge (PDF / DOCX / XLSX / PPTX / SCAN)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = document.kind.badge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }

                    // Page Count / Last Read Pill
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        val txt = when (document.kind) {
                            DocKind.PDF -> if (document.lastReadPage > 0) "p.${document.lastReadPage + 1}/${document.pageCount}"
                            else "${document.pageCount} ${if (document.pageCount == 1) "Page" else "Pages"}"
                            DocKind.WORD -> "Read / Edit"
                            DocKind.EXCEL -> "${document.pageCount} ${if (document.pageCount == 1) "Sheet" else "Sheets"}"
                            DocKind.SLIDES -> "${document.pageCount} ${if (document.pageCount == 1) "Slide" else "Slides"}"
                            DocKind.CODE -> "${document.pageCount} Lines"
                            DocKind.SCAN -> "${document.pageCount} ${if (document.pageCount == 1) "Page" else "Pages"}"
                        }
                        Text(
                            text = txt,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    // Star or Selection Checkmark button
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = CamScanPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onToggleStar,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                        ) {
                            Icon(
                                imageVector = if (document.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Star",
                                tint = if (document.isStarred) CamScanTertiary else Color.Gray
                            )
                        }
                    }
                }

                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (document.subjectTag.isNotBlank()) "${document.subjectTag} • $dateStr" else dateStr,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Options", modifier = Modifier.size(18.dp))
                            }
                            DocumentOptionsMenu(
                                expanded = menuExpanded,
                                kind = document.kind,
                                onDismiss = { menuExpanded = false },
                                onConvert = onConvert,
                                onRename = onRename,
                                onShare = onShare,
                                onDelete = onDelete
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp, 72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    if (document.thumbnailPath != null) {
                        AsyncImage(
                            model = File(document.thumbnailPath!!),
                            contentDescription = document.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = kindIcon,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(CamScanPrimary.copy(alpha = 0.28f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(badgeColor.copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = document.kind.badge,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        if (document.subjectTag.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(CamScanPrimary.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = document.subjectTag,
                                    style = MaterialTheme.typography.labelSmall.copy(color = CamScanPrimary)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        )
                    }
                }

                IconButton(onClick = onToggleStar) {
                    Icon(
                        imageVector = if (document.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Star",
                        tint = if (document.isStarred) CamScanTertiary else Color.Gray
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DocumentOptionsMenu(
                        expanded = menuExpanded,
                        kind = document.kind,
                        onDismiss = { menuExpanded = false },
                        onConvert = onConvert,
                        onRename = onRename,
                        onShare = onShare,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentOptionsMenu(
    expanded: Boolean,
    kind: DocKind,
    onDismiss: () -> Unit,
    onConvert: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = {
                Text(
                    when (kind) {
                        DocKind.PDF -> "Convert PDF → Word"
                        DocKind.WORD -> "Edit & Clean Doc"
                        DocKind.EXCEL -> "Open Spreadsheet"
                        DocKind.SLIDES -> "Open Slide Deck"
                        DocKind.CODE -> "Open Code / Log"
                        DocKind.SCAN -> "Export as PDF"
                    }
                )
            },
            leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null) },
            onClick = {
                onDismiss()
                onConvert()
            }
        )
        DropdownMenuItem(
            text = { Text("Share") },
            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
            onClick = {
                onDismiss()
                onShare()
            }
        )
        DropdownMenuItem(
            text = { Text("Rename / Subject Tag") },
            onClick = {
                onDismiss()
                onRename()
            }
        )
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            onClick = {
                onDismiss()
                onDelete()
            }
        )
    }
}

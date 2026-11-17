package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.DocKind
import com.example.model.Document
import com.example.ui.MainViewModel
import com.example.ui.theme.CamScanPrimary
import com.example.util.PdfEngine
import com.example.util.PdfSuiteEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private enum class PdfSuiteTab(val title: String) {
    MERGE("Merge"),
    SPLIT("Split / Pages"),
    COMPRESS("Compress"),
    WATERMARK("Watermark"),
    TO_IMAGES("PDF → Images")
}

private data class EditablePdfPage(
    val sourceIndex: Int,
    var selected: Boolean = true,
    var rotationDegrees: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.navigateBack() }

    val allDocs by viewModel.allDocuments.collectAsState()
    val activeDoc by viewModel.activeDocument.collectAsState()
    val pdfDocs = remember(allDocs) { allDocs.filter { it.kind == DocKind.PDF } }

    var activeTab by remember { mutableStateOf(PdfSuiteTab.MERGE) }

    val importPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importFileFromUri(uri)
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        Column {
                            Text("PDF Power Suite", fontWeight = FontWeight.Bold)
                            Text(
                                text = "Merge • Split & Rotate • Compress • Watermark",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                )
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.navigateBack() },
                            modifier = Modifier.testTag("pdf_tools_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { importPdfLauncher.launch(arrayOf("application/pdf")) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Import PDF from Phone")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )

                ScrollableTabRow(
                    selectedTabIndex = activeTab.ordinal,
                    edgePadding = 12.dp
                ) {
                    PdfSuiteTab.values().forEach { tab ->
                        Tab(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            text = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            if (pdfDocs.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = CamScanPrimary, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No PDFs in Library yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Import a PDF from your phone or create one from Pictures to use PDF Power Suite.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { importPdfLauncher.launch(arrayOf("application/pdf")) },
                        colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary)
                    ) {
                        Text("Import PDF", color = Color.White)
                    }
                }
            } else {
                when (activeTab) {
                    PdfSuiteTab.MERGE -> MergePdfsTab(pdfDocs = pdfDocs, onMerge = { list, title ->
                        viewModel.mergePdfDocuments(list, title)
                    })
                    PdfSuiteTab.SPLIT -> SplitReorderTab(
                        pdfDocs = pdfDocs,
                        initialDoc = activeDoc?.takeIf { it.kind == DocKind.PDF } ?: pdfDocs.first(),
                        onBuildPdf = { doc, specs, title ->
                            viewModel.splitOrTransformPdfDocument(doc, specs, title)
                        }
                    )
                    PdfSuiteTab.COMPRESS -> CompressPdfTab(
                        pdfDocs = pdfDocs,
                        initialDoc = activeDoc?.takeIf { it.kind == DocKind.PDF } ?: pdfDocs.first(),
                        onCompress = { doc, level -> viewModel.compressPdfDocument(doc, level) }
                    )
                    PdfSuiteTab.WATERMARK -> WatermarkPdfTab(
                        pdfDocs = pdfDocs,
                        initialDoc = activeDoc?.takeIf { it.kind == DocKind.PDF } ?: pdfDocs.first(),
                        onApplyWatermark = { doc, text, diag -> viewModel.watermarkPdfDocument(doc, text, diag) }
                    )
                    PdfSuiteTab.TO_IMAGES -> PdfToImagesTab(
                        pdfDocs = pdfDocs,
                        initialDoc = activeDoc?.takeIf { it.kind == DocKind.PDF } ?: pdfDocs.first(),
                        onExportImages = { doc, indices -> viewModel.exportPdfAsScanImages(doc, indices) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MergePdfsTab(
    pdfDocs: List<Document>,
    onMerge: (List<Document>, String) -> Unit
) {
    val selectedList = remember { mutableStateListOf<Document>() }
    var outputTitle by remember { mutableStateOf("Combined_Study_Notes") }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        OutlinedTextField(
            value = outputTitle,
            onValueChange = { outputTitle = it },
            label = { Text("Output Merged PDF Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "1. Select 2 or more PDFs below (${selectedList.size} selected):",
            style = MaterialTheme.typography.labelLarge,
            color = CamScanPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (selectedList.isNotEmpty()) {
                item {
                    Text(
                        text = "MERGE ORDER (Top to Bottom):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                itemsIndexed(selectedList, key = { idx, d -> "sel_${d.id}_$idx" }) { idx, doc ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CamScanPrimary.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${idx + 1}.", fontWeight = FontWeight.Bold, color = CamScanPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(doc.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${doc.pageCount} pages", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(
                                onClick = {
                                    if (idx > 0) {
                                        val item = selectedList.removeAt(idx)
                                        selectedList.add(idx - 1, item)
                                    }
                                },
                                enabled = idx > 0
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    if (idx < selectedList.lastIndex) {
                                        val item = selectedList.removeAt(idx)
                                        selectedList.add(idx + 1, item)
                                    }
                                },
                                enabled = idx < selectedList.lastIndex
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { selectedList.removeAt(idx) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "AVAILABLE PDFS IN LIBRARY:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            itemsIndexed(pdfDocs, key = { _, d -> "lib_${d.id}" }) { _, doc ->
                val isSelected = selectedList.any { it.id == doc.id }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isSelected) selectedList.removeAll { it.id == doc.id }
                            else selectedList.add(doc)
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                if (checked && !isSelected) selectedList.add(doc)
                                else if (!checked) selectedList.removeAll { it.id == doc.id }
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(doc.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${doc.pageCount} pages${if (doc.subjectTag.isNotBlank()) " • ${doc.subjectTag}" else ""}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = { onMerge(selectedList.toList(), outputTitle) },
            enabled = selectedList.size >= 2,
            colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("merge_pdfs_action_btn")
        ) {
            Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Merge ${selectedList.size} PDFs into One", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SplitReorderTab(
    pdfDocs: List<Document>,
    initialDoc: Document,
    onBuildPdf: (Document, List<PdfSuiteEngine.PageTransformSpec>, String) -> Unit
) {
    var selectedDoc by remember { mutableStateOf(initialDoc) }
    val pdfFile = remember(selectedDoc.filePath) { selectedDoc.filePath?.let { File(it) } }
    var totalPages by remember(selectedDoc.id) { mutableIntStateOf(selectedDoc.pageCount.coerceAtLeast(1)) }
    val pageItems = remember { mutableStateListOf<EditablePdfPage>() }
    var rangeInput by remember { mutableStateOf("") }
    var outputTitle by remember(selectedDoc.id) { mutableStateOf("${selectedDoc.title} (Split)") }

    LaunchedEffect(selectedDoc.id, pdfFile) {
        val count = if (pdfFile != null && pdfFile.exists()) {
            withContext(Dispatchers.IO) { PdfEngine.getPdfPageCount(pdfFile) }.coerceAtLeast(1)
        } else selectedDoc.pageCount.coerceAtLeast(1)
        totalPages = count
        pageItems.clear()
        for (i in 0 until count) {
            pageItems.add(EditablePdfPage(sourceIndex = i, selected = true, rotationDegrees = 0))
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        PdfPickerRow(pdfDocs = pdfDocs, selectedDoc = selectedDoc, onSelect = { selectedDoc = it })
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = rangeInput,
                onValueChange = { rangeInput = it },
                label = { Text("Pages e.g. 1-3, 5") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = {
                    val chosen = PdfSuiteEngine.parsePageSelection(rangeInput, totalPages).toSet()
                    if (chosen.isNotEmpty()) {
                        for (i in pageItems.indices) {
                            pageItems[i] = pageItems[i].copy(selected = pageItems[i].sourceIndex in chosen)
                        }
                    }
                }
            ) {
                Text("Apply Range")
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = outputTitle,
            onValueChange = { outputTitle = it },
            label = { Text("Output PDF Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(pageItems, key = { idx, item -> "pg_${item.sourceIndex}_$idx" }) { idx, item ->
                val thumb by produceState<Bitmap?>(initialValue = null, pdfFile, item.sourceIndex) {
                    value = if (pdfFile != null && pdfFile.exists()) {
                        withContext(Dispatchers.IO) { PdfEngine.renderPdfPage(pdfFile, item.sourceIndex, 360) }
                    } else null
                }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (item.selected) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .border(
                            width = if (item.selected) 2.dp else 1.dp,
                            color = if (item.selected) CamScanPrimary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            pageItems[idx] = item.copy(selected = !item.selected)
                        }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            val bmp = thumb
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Page ${item.sourceIndex + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { rotationZ = item.rotationDegrees.toFloat() }
                                )
                            }
                            if (item.selected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = CamScanPrimary,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("P.${item.sourceIndex + 1}", fontWeight = FontWeight.Bold)
                            Row {
                                IconButton(
                                    onClick = {
                                        pageItems[idx] = item.copy(rotationDegrees = (item.rotationDegrees + 90) % 360)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate 90", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = {
                                        if (idx > 0) {
                                            val moved = pageItems.removeAt(idx)
                                            pageItems.add(idx - 1, moved)
                                        }
                                    },
                                    enabled = idx > 0,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Earlier", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        val selectedSpecs = pageItems.filter { it.selected }.map {
            PdfSuiteEngine.PageTransformSpec(it.sourceIndex, it.rotationDegrees)
        }
        Button(
            onClick = { onBuildPdf(selectedDoc, selectedSpecs, outputTitle) },
            enabled = selectedSpecs.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("split_pdf_action_btn")
        ) {
            Icon(Icons.Default.ContentCut, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Selected (${selectedSpecs.size} Pages)", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CompressPdfTab(
    pdfDocs: List<Document>,
    initialDoc: Document,
    onCompress: (Document, PdfSuiteEngine.CompressionLevel) -> Unit
) {
    var selectedDoc by remember { mutableStateOf(initialDoc) }
    var chosenLevel by remember { mutableStateOf(PdfSuiteEngine.CompressionLevel.BALANCED) }
    val fileSizeKb = remember(selectedDoc.filePath) {
        val len = selectedDoc.filePath?.let { File(it).length() } ?: 0L
        if (len >= 1024 * 1024) String.format(java.util.Locale.US, "%.2f MB", len / (1024f * 1024f))
        else "${(len / 1024).coerceAtLeast(1)} KB"
    }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        PdfPickerRow(pdfDocs = pdfDocs, selectedDoc = selectedDoc, onSelect = { selectedDoc = it })
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Current Size: $fileSizeKb (${selectedDoc.pageCount} pages)",
            style = MaterialTheme.typography.titleSmall,
            color = CamScanPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        PdfSuiteEngine.CompressionLevel.values().forEach { level ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (chosenLevel == level) CamScanPrimary.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { chosenLevel = level }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = chosenLevel == level, onClick = { chosenLevel = level })
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(level.label, fontWeight = FontWeight.Bold)
                        Text(level.subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = { onCompress(selectedDoc, chosenLevel) },
            colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("compress_pdf_action_btn")
        ) {
            Icon(Icons.Default.Compress, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Compress PDF Now", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WatermarkPdfTab(
    pdfDocs: List<Document>,
    initialDoc: Document,
    onApplyWatermark: (Document, String, Boolean) -> Unit
) {
    var selectedDoc by remember { mutableStateOf(initialDoc) }
    var watermarkText by remember { mutableStateOf("Shaheer Rao • Study Notes") }
    var diagonal by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        PdfPickerRow(pdfDocs = pdfDocs, selectedDoc = selectedDoc, onSelect = { selectedDoc = it })
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = watermarkText,
            onValueChange = { watermarkText = it },
            label = { Text("Watermark Text") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (diagonal) CamScanPrimary.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.weight(1f).clickable { diagonal = true }
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = diagonal, onClick = { diagonal = true })
                    Text("Diagonal Stamp", fontWeight = FontWeight.SemiBold)
                }
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (!diagonal) CamScanPrimary.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.weight(1f).clickable { diagonal = false }
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !diagonal, onClick = { diagonal = false })
                    Text("Page Footer", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = { onApplyWatermark(selectedDoc, watermarkText, diagonal) },
            enabled = watermarkText.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("watermark_pdf_action_btn")
        ) {
            Icon(Icons.Default.BrandingWatermark, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Apply Watermark to All Pages", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PdfToImagesTab(
    pdfDocs: List<Document>,
    initialDoc: Document,
    onExportImages: (Document, List<Int>) -> Unit
) {
    var selectedDoc by remember { mutableStateOf(initialDoc) }
    var pageRangeInput by remember { mutableStateOf("") }
    val totalPages = selectedDoc.totalPages.coerceAtLeast(1)

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        PdfPickerRow(pdfDocs = pdfDocs, selectedDoc = selectedDoc, onSelect = { selectedDoc = it })
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Convert PDF pages into editable high-res scan images so you can crop, filter, sign, or share each page.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = pageRangeInput,
            onValueChange = { pageRangeInput = it },
            label = { Text("Pages (leave blank for all 1-$totalPages, or e.g. 1-3, 5)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                val parsed = if (pageRangeInput.isBlank()) {
                    (0 until totalPages).toList()
                } else {
                    PdfSuiteEngine.parsePageSelection(pageRangeInput, totalPages)
                }
                val finalIndices = if (parsed.isEmpty()) (0 until totalPages).toList() else parsed
                onExportImages(selectedDoc, finalIndices)
            },
            colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("pdf_to_images_action_btn")
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Extract Pages as Scan Images", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PdfPickerRow(
    pdfDocs: List<Document>,
    selectedDoc: Document,
    onSelect: (Document) -> Unit
) {
    Column {
        Text("Select PDF Document:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            pdfDocs.take(3).forEach { doc ->
                val selected = doc.id == selectedDoc.id
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) CamScanPrimary else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onSelect(doc) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = doc.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }
    }
}

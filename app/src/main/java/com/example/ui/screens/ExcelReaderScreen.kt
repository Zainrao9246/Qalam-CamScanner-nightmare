package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Close
import android.widget.Toast
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ExcelCellStyle
import com.example.ui.MainViewModel
import com.example.ui.theme.CamScanAccent
import com.example.ui.theme.CamScanPrimary
import com.example.util.ExcelEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelReaderScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val context = LocalContext.current
    val document by viewModel.activeDocument.collectAsState()
    val sheets by viewModel.excelSheets.collectAsState()
    val nightMode by viewModel.isReaderNightMode.collectAsState()
    val statusMsg by viewModel.excelStatusMessage.collectAsState()

    val doc = document ?: return

    LaunchedEffect(statusMsg) {
        if (!statusMsg.isNullOrBlank()) {
            Toast.makeText(context, statusMsg, Toast.LENGTH_SHORT).show()
            viewModel.excelStatusMessage.value = null
        }
    }

    var activeSheetIndex by remember(doc.id) { mutableIntStateOf(0) }
    var searchQuery by remember(doc.id) { mutableStateOf("") }
    var selectedCellRef by remember(doc.id) { mutableStateOf("") }
    var selectedCellValue by remember(doc.id) { mutableStateOf("") }
    var selectedCellCoords by remember(doc.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    var showEditCellDialog by remember { mutableStateOf(false) }
    var editCellInput by remember { mutableStateOf("") }

    val activeSheet = sheets.getOrNull(activeSheetIndex) ?: sheets.firstOrNull()
    val hScrollState = rememberScrollState()

    val filteredRows = remember(activeSheet, searchQuery) {
        val rows = activeSheet?.rows ?: emptyList()
        if (searchQuery.isBlank() || rows.isEmpty()) {
            rows.mapIndexed { idx, row -> idx to row }
        } else {
            val header = rows.first()
            val matches = rows.drop(1).mapIndexedNotNull { idx, row ->
                if (row.any { it.contains(searchQuery, ignoreCase = true) }) {
                    (idx + 1) to row
                } else null
            }
            listOf(0 to header) + matches
        }
    }

    val colCount = remember(activeSheet) {
        (activeSheet?.rows?.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    }

    val colWidths = remember(activeSheet, colCount) {
        val rows = activeSheet?.rows ?: emptyList()
        (0 until colCount).map { cIdx ->
            val maxLen = rows.maxOfOrNull { it.getOrElse(cIdx) { "" }.length } ?: 6
            (maxLen * 9 + 28).coerceIn(88, 220).dp
        }
    }

    val excelGreen = Color(0xFF2E7D32)
    val sheetBg = if (nightMode) Color(0xFF101513) else MaterialTheme.colorScheme.surface
    val rowEvenBg = if (nightMode) Color(0xFF161D1B) else Color(0xFFF7FAF8)
    val rowOddBg = if (nightMode) Color(0xFF121716) else Color.White
    val headerBg = if (nightMode) Color(0xFF1A3329) else Color(0xFFE6F4EE)
    val gridBorder = if (nightMode) Color(0xFF283632) else Color(0xFFD5E1DC)
    val cellTextColor = if (nightMode) Color(0xFFE5EEEB) else Color(0xFF19211F)

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = doc.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${sheets.size} ${if (sheets.size == 1) "sheet" else "sheets"} • ${activeSheet?.rows?.size ?: 0} rows",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                )
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.navigateBack() },
                            modifier = Modifier.testTag("excel_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.saveActiveSpreadsheet() },
                            modifier = Modifier.testTag("excel_save_btn")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save Spreadsheet", tint = CamScanPrimary)
                        }
                        IconButton(
                            onClick = { viewModel.setReaderNightMode(!nightMode) },
                            modifier = Modifier.testTag("excel_night_mode_btn")
                        ) {
                            Icon(
                                imageVector = if (nightMode) Icons.Default.BrightnessHigh else Icons.Default.Brightness4,
                                contentDescription = "Toggle Night Reading Mode",
                                tint = if (nightMode) CamScanAccent else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { viewModel.shareDocumentFile(doc) },
                            modifier = Modifier.testTag("excel_share_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share Spreadsheet")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Search filter bar inside spreadsheet
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search cells or rows...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )

                // Sheet Tabs
                if (sheets.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(sheets) { idx, sheet ->
                            val selected = idx == activeSheetIndex
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) excelGreen else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    .clickable {
                                        activeSheetIndex = idx
                                        selectedCellRef = ""
                                        selectedCellValue = ""
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                                    .testTag("excel_sheet_tab_$idx")
                            ) {
                                Text(
                                    text = sheet.name,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }

                // Selected Cell Formula/Inspector Strip + Live Column Math
                if (selectedCellRef.isNotEmpty()) {
                    val colStats = remember(activeSheet, selectedCellCoords) {
                        val cIdx = selectedCellCoords?.second ?: return@remember null
                        val nums = (activeSheet?.rows ?: emptyList()).drop(1)
                            .mapNotNull { it.getOrNull(cIdx)?.replace(",", "")?.toDoubleOrNull() }
                        if (nums.isEmpty()) null else Triple(nums.sum(), nums.average(), nums.size)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(excelGreen)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = selectedCellRef,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val activeFormula = activeSheet?.formulas?.get(selectedCellRef)
                            Text(
                                text = if (activeFormula != null) "= $activeFormula" else selectedCellValue.ifEmpty { "(Empty)" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (activeFormula != null) {
                                Text(
                                    text = "fx = ${selectedCellValue.ifEmpty { "(recalculating)" }}",
                                    style = MaterialTheme.typography.labelSmall.copy(color = excelGreen)
                                )
                            } else if (colStats != null) {
                                val (sum, avg, cnt) = colStats
                                Text(
                                    text = "Col Sum: ${"%.2f".format(sum).trimEnd('0').trimEnd('.')} • Avg: ${"%.2f".format(avg)} ($cnt)",
                                    style = MaterialTheme.typography.labelSmall.copy(color = excelGreen)
                                )
                            }
                        }
                        if (selectedCellCoords != null) {
                            if (selectedCellCoords!!.first > 0 && (activeSheet?.rows?.size ?: 0) > 1) {
                                IconButton(
                                    onClick = {
                                        viewModel.deleteExcelRow(activeSheetIndex, selectedCellCoords!!.first)
                                        selectedCellRef = ""
                                        selectedCellCoords = null
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Row", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(
                                onClick = {
                                    editCellInput = activeSheet?.formulas?.get(selectedCellRef) ?: selectedCellValue
                                    showEditCellDialog = true
                                },
                                modifier = Modifier.size(32.dp).testTag("excel_edit_cell_btn")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Cell", tint = CamScanPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Cell Format Toolbar (bold / italic / text color / fill) — only when a cell is selected
                if (selectedCellCoords != null && activeSheet != null) {
                    val (selRow, selCol) = selectedCellCoords!!
                    val selRef = selectedCellRef
                    val selStyle = activeSheet.styles[selRef] ?: ExcelCellStyle()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleExcelBold(activeSheetIndex, selRow, selCol) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.FormatBold,
                                contentDescription = "Bold cell",
                                tint = if (selStyle.bold) excelGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.toggleExcelItalic(activeSheetIndex, selRow, selCol) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.FormatItalic,
                                contentDescription = "Italic cell",
                                tint = if (selStyle.italic) excelGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Text color dots
                        listOf<String?>(null, "1E2422", "C62828", "1565C0", "2E7D32", "6A1B9A").forEach { hex ->
                            val active = selStyle.colorHex?.uppercase() == hex?.uppercase()
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (hex == null) {
                                            Modifier.background(MaterialTheme.colorScheme.surface)
                                        } else {
                                            Modifier.background(parseCellColor(hex))
                                        }
                                    )
                                    .border(
                                        width = if (active) 2.dp else 1.dp,
                                        color = if (active) excelGreen else gridBorder,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        viewModel.setExcelTextColor(activeSheetIndex, selRow, selCol, hex)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (hex == null) {
                                    Icon(
                                        Icons.Default.FormatColorReset,
                                        contentDescription = "Automatic text color",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Fill color dots
                        listOf<String?>(null, "FFF9C4", "C8E6C9", "BBDEFB", "FFE0B2", "E1BEE7").forEach { hex ->
                            val active = selStyle.fillHex?.uppercase() == hex?.uppercase()
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (hex == null) {
                                            Modifier.background(MaterialTheme.colorScheme.surface)
                                        } else {
                                            Modifier.background(parseCellColor(hex))
                                        }
                                    )
                                    .border(
                                        width = if (active) 2.dp else 1.dp,
                                        color = if (active) excelGreen else gridBorder,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        viewModel.setExcelFill(activeSheetIndex, selRow, selCol, hex)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (hex == null) {
                                    Icon(
                                        Icons.Default.FormatColorFill,
                                        contentDescription = "No fill",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = gridBorder)
            }
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.TableChart, contentDescription = null, tint = excelGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = activeSheet?.name ?: "Sheet",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.addExcelRow(activeSheetIndex) },
                            enabled = activeSheet != null,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("excel_add_row_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Row")
                        }
                        OutlinedButton(
                            onClick = { viewModel.addExcelColumn(activeSheetIndex) },
                            enabled = activeSheet != null,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("excel_add_col_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Col")
                        }
                        Button(
                            onClick = {
                                if (activeSheet != null) {
                                    viewModel.exportExcelSheetToPdf(doc, activeSheet)
                                }
                            },
                            enabled = activeSheet != null,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("excel_export_pdf_btn")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        if (activeSheet == null || activeSheet.rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(sheetBg)
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("This spreadsheet has no visible cells.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(sheetBg)
                    .padding(padding)
            ) {
                // Sticky Column Letters Header (A, B, C...)
                item(key = "col_letters_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (nightMode) Color(0xFF1A2220) else Color(0xFFEDF2F0))
                    ) {
                        Box(
                            modifier = Modifier
                                .width(42.dp)
                                .height(32.dp)
                                .border(0.5.dp, gridBorder),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "#",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = cellTextColor.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Row(modifier = Modifier.horizontalScroll(hScrollState)) {
                            for (cIdx in 0 until colCount) {
                                Box(
                                    modifier = Modifier
                                        .width(colWidths[cIdx])
                                        .height(32.dp)
                                        .border(0.5.dp, gridBorder),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ExcelEngine.indexToColName(cIdx),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = cellTextColor.copy(alpha = 0.7f),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Spreadsheet Data Rows
                itemsIndexed(filteredRows, key = { _, pair -> pair.first }) { displayIdx, (origRowIdx, row) ->
                    val isHeaderRow = origRowIdx == 0
                    val bg = when {
                        isHeaderRow -> headerBg
                        displayIdx % 2 == 0 -> rowEvenBg
                        else -> rowOddBg
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                    ) {
                        // Row Number Cell
                        Box(
                            modifier = Modifier
                                .width(42.dp)
                                .height(42.dp)
                                .border(0.5.dp, gridBorder),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${origRowIdx + 1}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = cellTextColor.copy(alpha = 0.65f),
                                    fontWeight = if (isHeaderRow) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }

                        // Horizontal Scrollable Cells (synchronized via hScrollState)
                        Row(modifier = Modifier.horizontalScroll(hScrollState)) {
                            for (cIdx in 0 until colCount) {
                                val cellVal = row.getOrElse(cIdx) { "" }
                                val cellRef = "${ExcelEngine.indexToColName(cIdx)}${origRowIdx + 1}"
                                val isSelected = selectedCellRef == cellRef
                                val cellStyle = activeSheet?.styles?.get(cellRef)
                                val cellFill = cellStyle?.fillHex?.let { parseCellColor(it) }

                                Box(
                                    modifier = Modifier
                                        .width(colWidths[cIdx])
                                        .height(42.dp)
                                        .background(
                                            when {
                                                isSelected -> excelGreen.copy(alpha = 0.16f)
                                                cellFill != null -> cellFill
                                                else -> Color.Transparent
                                            }
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 0.5.dp,
                                            color = if (isSelected) excelGreen else gridBorder
                                        )
                                        .clickable {
                                            selectedCellRef = cellRef
                                            selectedCellValue = cellVal
                                            selectedCellCoords = origRowIdx to cIdx
                                        }
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = cellVal,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 13.sp,
                                            fontWeight = if (isHeaderRow || cellStyle?.bold == true) FontWeight.Bold else FontWeight.Normal,
                                            fontStyle = if (cellStyle?.italic == true) FontStyle.Italic else FontStyle.Normal,
                                            color = cellStyle?.colorHex?.let { parseCellColor(it) }
                                                ?: if (isHeaderRow && !nightMode) Color(0xFF004D40) else cellTextColor
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditCellDialog) {
        val coords = selectedCellCoords
        AlertDialog(
            onDismissRequest = { showEditCellDialog = false },
            title = { Text("Edit Cell $selectedCellRef") },
            text = {
                OutlinedTextField(
                    value = editCellInput,
                    onValueChange = { editCellInput = it },
                    label = { Text("Value or formula, e.g. =SUM(A1:A5)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (coords != null) {
                            viewModel.updateExcelCell(activeSheetIndex, coords.first, coords.second, editCellInput)
                            // Refresh strip with the recalculated display value
                            val updated = viewModel.excelSheets.value.getOrNull(activeSheetIndex)
                            selectedCellValue = updated?.rows?.getOrNull(coords.first)
                                ?.getOrNull(coords.second) ?: editCellInput
                        }
                        showEditCellDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary)
                ) {
                    Text("Update", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditCellDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/** Parses a 6-digit hex color ("C8E6C9") into a Compose Color (transparent fallback). */
private fun parseCellColor(hex: String?): Color {
    if (hex.isNullOrBlank()) return Color.Transparent
    val clean = if (hex.startsWith("#")) hex.drop(1) else hex
    if (clean.length != 6) return Color.Transparent
    return clean.toLongOrNull(16)?.let { Color(0xFF000000L or it) } ?: Color.Transparent
}

package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.WordBlock
import com.example.model.WordBlockType
import com.example.ui.MainViewModel
import com.example.ui.theme.CamScanAccent
import com.example.ui.theme.CamScanPrimary
import com.example.ui.theme.MerriweatherReadingFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordEditorScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.stopTts()
        viewModel.saveActiveWordDocument()
        viewModel.navigateBack()
    }

    val context = LocalContext.current
    val document by viewModel.activeDocument.collectAsState()
    val blocks by viewModel.wordBlocks.collectAsState()
    val statusMsg by viewModel.wordStatusMessage.collectAsState()
    val startInReadMode by viewModel.wordStartInReadMode.collectAsState()
    val nightMode by viewModel.isReaderNightMode.collectAsState()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsState()
    val ttsRate by viewModel.ttsSpeechRate.collectAsState()

    val doc = document ?: return

    var isReadMode by remember(doc.id, startInReadMode) { mutableStateOf(startInReadMode) }
    var readerFontOffset by remember(doc.id) { mutableStateOf(0f) }
    var showCleanSheet by remember { mutableStateOf(false) }
    var showOutlineSheet by remember { mutableStateOf(false) }
    var showFindReplaceDialog by remember { mutableStateOf(false) }
    var showNoiseDialog by remember { mutableStateOf(false) }
    var showPasteBulkDialog by remember { mutableStateOf(false) }

    val readerListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var customNoiseText by remember { mutableStateOf("") }
    var bulkPasteText by remember { mutableStateOf("") }

    LaunchedEffect(statusMsg) {
        if (!statusMsg.isNullOrBlank()) {
            Toast.makeText(context, statusMsg, Toast.LENGTH_SHORT).show()
            viewModel.wordStatusMessage.value = null
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = doc.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            Text(
                                text = "${blocks.size} blocks • ${blocks.sumOf { it.text.split(Regex("\\s+")).count { w -> w.isNotBlank() } }} words",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                viewModel.stopTts()
                                viewModel.saveActiveWordDocument()
                                viewModel.navigateBack()
                            },
                            modifier = Modifier.testTag("word_editor_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Offline Read Aloud (TTS)
                        IconButton(
                            onClick = {
                                if (isTtsSpeaking) {
                                    viewModel.stopTts()
                                } else {
                                    val fullText = blocks.joinToString("\n") { it.text }.trim()
                                    if (fullText.isNotEmpty()) viewModel.speakStudyText(fullText)
                                }
                            },
                            modifier = Modifier.testTag("word_tts_btn")
                        ) {
                            Icon(
                                imageVector = if (isTtsSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                                contentDescription = if (isTtsSpeaking) "Stop Read Aloud" else "Listen Offline",
                                tint = if (isTtsSpeaking) CamScanAccent else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Night Reading Mode toggle
                        IconButton(
                            onClick = { viewModel.setReaderNightMode(!nightMode) },
                            modifier = Modifier.testTag("word_night_mode_btn")
                        ) {
                            Icon(
                                imageVector = if (nightMode) Icons.Default.BrightnessHigh else Icons.Default.Brightness4,
                                contentDescription = "Toggle Night Reading Mode",
                                tint = if (nightMode) CamScanAccent else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Read Mode vs Edit Mode Switch
                        IconButton(
                            onClick = { isReadMode = !isReadMode },
                            modifier = Modifier.testTag("word_mode_toggle_btn")
                        ) {
                            Icon(
                                imageVector = if (isReadMode) Icons.Default.Edit else Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = if (isReadMode) "Switch to Edit Mode" else "Switch to Reader Mode",
                                tint = CamScanPrimary
                            )
                        }

                        // Save .docx
                        IconButton(
                            onClick = { viewModel.saveActiveWordDocument() },
                            modifier = Modifier.testTag("word_save_btn")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save DOCX", tint = CamScanPrimary)
                        }

                        // Share .docx
                        IconButton(
                            onClick = { viewModel.shareActiveWordDocx() },
                            modifier = Modifier.testTag("word_share_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share DOCX")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                if (isTtsSpeaking) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CamScanPrimary.copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reading aloud (${ttsRate}x)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = CamScanPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf(0.85f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                                TextButton(
                                    onClick = { viewModel.setTtsRate(speed) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = "${speed}x",
                                        fontWeight = if (ttsRate == speed) FontWeight.Bold else FontWeight.Normal,
                                        color = if (ttsRate == speed) CamScanPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            TextButton(
                                onClick = { viewModel.stopTts() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Stop", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Sub-toolbar: Clean Reader controls OR Edit & Clean toolbar
                if (isReadMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showOutlineSheet = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("word_outline_btn")
                            ) {
                                Icon(Icons.Default.FormatListNumbered, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Outline", style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = { readerFontOffset = (readerFontOffset - 1.5f).coerceAtLeast(-3f) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("A-", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = { readerFontOffset = (readerFontOffset + 1.5f).coerceAtMost(8f) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("A+", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        OutlinedButton(
                            onClick = { showCleanSheet = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("word_reader_clean_btn")
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clean Doc", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showCleanSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("word_clean_tools_btn")
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clean Doc", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = { showFindReplaceDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("word_find_replace_btn")
                        ) {
                            Icon(Icons.Default.FindReplace, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Find/Replace", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = {
                                bulkPasteText = blocks.joinToString("\n\n") { it.text }
                                showPasteBulkDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("word_bulk_edit_btn")
                        ) {
                            Text("Bulk Text", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = { viewModel.addWordBlock(type = WordBlockType.HEADING_1) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("+ Heading", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = { viewModel.addWordBlock(type = WordBlockType.PARAGRAPH) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("+ Para", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = { viewModel.addWordBlock(type = WordBlockType.BULLET) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("+ Bullet", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
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
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isReadMode) {
                        OutlinedButton(
                            onClick = { isReadMode = false },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("word_switch_edit_btn")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit & Clean")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.saveActiveWordDocument() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save .DOCX")
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.saveActiveWordDocument()
                            viewModel.convertWordBlocksToPdf(doc.title, blocks, doc.subjectTag)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("word_to_pdf_export_btn")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Word → PDF", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) { padding ->
        if (isReadMode) {
            val readerOuterBg = if (nightMode) Color(0xFF101312) else Color(0xFFECEFE9)
            val paperBg = if (nightMode) Color(0xFF1A1F1D) else Color(0xFFFFFEFA)
            val paperText = if (nightMode) Color(0xFFE6EEEA) else Color(0xFF1E2422)
            val paperHeading = if (nightMode) CamScanAccent else CamScanPrimary

            // Fast Paper Document Reader View (section-indexed for Outline jump)
            LazyColumn(
                state = readerListState,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .background(readerOuterBg)
                    .padding(padding)
            ) {
                item(key = "word_paper_header") {
                    Surface(
                        color = paperBg,
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (doc.subjectTag.isNotBlank()) "${doc.subjectTag} • DOCUMENT READER" else "DOCUMENT READER",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = paperHeading,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                )
                                Text(
                                    text = "${blocks.sumOf { it.text.split(Regex("\\s+")).count { w -> w.isNotBlank() } }} words",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = paperText.copy(alpha = 0.55f)
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(
                                color = if (nightMode) Color(0xFF2B3532) else Color(0xFFE5EAE6)
                            )
                        }
                    }
                }

                itemsIndexed(blocks, key = { idx, b -> "${b.id}_$idx" }) { idx, block ->
                    val isLast = idx == blocks.lastIndex
                    Surface(
                        color = paperBg,
                        shape = if (isLast) RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp) else RoundedCornerShape(0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val baseSize = when (block.type) {
                            WordBlockType.HEADING_1 -> 21f
                            WordBlockType.HEADING_2 -> 17.5f
                            else -> 15.5f
                        }
                        val fontSize = (baseSize + readerFontOffset).coerceIn(12f, 28f).sp
                        val fontWeight = when {
                            block.type == WordBlockType.HEADING_1 || block.type == WordBlockType.HEADING_2 -> FontWeight.Bold
                            block.isBold -> FontWeight.Bold
                            else -> FontWeight.Normal
                        }
                        val prefix = if (block.type == WordBlockType.BULLET) "•  " else ""
                        Text(
                            text = "$prefix${block.text}",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = if (block.type == WordBlockType.PARAGRAPH || block.type == WordBlockType.BULLET)
                                    MerriweatherReadingFont else MaterialTheme.typography.titleLarge.fontFamily,
                                fontSize = fontSize,
                                lineHeight = (fontSize.value * 1.55f).sp,
                                fontWeight = fontWeight,
                                fontStyle = if (block.isItalic) FontStyle.Italic else FontStyle.Normal,
                                color = if (block.type == WordBlockType.HEADING_1) paperHeading else paperText
                            ),
                            modifier = Modifier.padding(
                                start = if (block.type == WordBlockType.BULLET) 34.dp else 22.dp,
                                end = 22.dp,
                                top = if (block.type == WordBlockType.HEADING_1) 10.dp else 4.dp,
                                bottom = if (isLast) 24.dp else 6.dp
                            )
                        )
                    }
                }
            }
        } else {
            // Editable Word Blocks Mode
            LazyColumn(
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                itemsIndexed(blocks, key = { _, b -> b.id }) { index, block ->
                    WordBlockEditorCard(
                        block = block,
                        canMoveUp = index > 0,
                        canMoveDown = index < blocks.lastIndex,
                        onTextChange = { newText ->
                            viewModel.updateWordBlock(block.id) { it.copy(text = newText) }
                        },
                        onCycleType = { newType ->
                            viewModel.updateWordBlock(block.id) {
                                it.copy(
                                    type = newType,
                                    isBold = newType == WordBlockType.HEADING_1 || newType == WordBlockType.HEADING_2 || it.isBold
                                )
                            }
                        },
                        onToggleBold = {
                            viewModel.updateWordBlock(block.id) { it.copy(isBold = !it.isBold) }
                        },
                        onToggleItalic = {
                            viewModel.updateWordBlock(block.id) { it.copy(isItalic = !it.isItalic) }
                        },
                        onMoveUp = { viewModel.moveWordBlock(block.id, -1) },
                        onMoveDown = { viewModel.moveWordBlock(block.id, 1) },
                        onAddBelow = { viewModel.addWordBlock(afterBlockId = block.id) },
                        onDelete = { viewModel.removeWordBlock(block.id) }
                    )
                }
            }
        }
    }

    // Smart Document Cleaner Bottom Sheet
    if (showCleanSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCleanSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = CamScanPrimary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Smart Word Document Cleaner",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Text(
                    text = "Quickly clean messy PDF-converted text, fix broken lines, and structure study notes.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                CleanerActionRow(
                    title = "1-Tap Full Auto Clean (Recommended)",
                    subtitle = "Strips page numbers, removes extra spaces, merges broken lines & formats headings",
                    isPrimary = true,
                    onClick = {
                        viewModel.cleanWordFullAuto()
                        showCleanSheet = false
                    }
                )

                CleanerActionRow(
                    title = "Fix Broken Line Breaks",
                    subtitle = "Joins split sentences caused by PDF column/page wraps",
                    onClick = {
                        viewModel.cleanWordMergeLineBreaks()
                        showCleanSheet = false
                    }
                )

                CleanerActionRow(
                    title = "Clean Extra Spaces & Blank Lines",
                    subtitle = "Removes empty paragraphs and collapses irregular spacing",
                    onClick = {
                        viewModel.cleanWordWhitespace()
                        showCleanSheet = false
                    }
                )

                CleanerActionRow(
                    title = "Auto-Detect Headings & Bullets",
                    subtitle = "Promotes Chapter/Lecture titles and bullet points cleanly",
                    onClick = {
                        viewModel.cleanWordAutoHeadings()
                        showCleanSheet = false
                    }
                )

                CleanerActionRow(
                    title = "Remove Watermarks / Repeated Noise",
                    subtitle = "Deletes page numbers, repeated headers, or custom text",
                    onClick = {
                        showCleanSheet = false
                        showNoiseDialog = true
                    }
                )
            }
        }
    }

    // Headings Outline / Table of Contents Sheet
    if (showOutlineSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val headings = remember(blocks) {
            blocks.mapIndexedNotNull { idx, b ->
                if (b.type == WordBlockType.HEADING_1 || b.type == WordBlockType.HEADING_2) idx to b else null
            }
        }
        ModalBottomSheet(
            onDismissRequest = { showOutlineSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Document Outline (${headings.size})",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                if (headings.isEmpty()) {
                    Text(
                        text = "No headings found. Use 'Clean Doc → Auto-Detect Headings' to organize chapters & topics.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        itemsIndexed(headings) { _, (blockIndex, heading) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        showOutlineSheet = false
                                        scope.launch {
                                            readerListState.animateScrollToItem(blockIndex + 1)
                                        }
                                    }
                                    .padding(
                                        start = if (heading.type == WordBlockType.HEADING_2) 20.dp else 8.dp,
                                        end = 8.dp,
                                        top = 10.dp,
                                        bottom = 10.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = heading.text,
                                    style = if (heading.type == WordBlockType.HEADING_1) {
                                        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = CamScanPrimary)
                                    } else {
                                        MaterialTheme.typography.bodyMedium
                                    }
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    // Find & Replace Dialog
    if (showFindReplaceDialog) {
        AlertDialog(
            onDismissRequest = { showFindReplaceDialog = false },
            title = { Text("Find & Replace") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = findText,
                        onValueChange = { findText = it },
                        label = { Text("Find text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        label = { Text("Replace with") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (findText.isNotEmpty()) {
                            viewModel.findAndReplaceWord(findText, replaceText)
                        }
                        showFindReplaceDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary)
                ) {
                    Text("Replace All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFindReplaceDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Strip Custom Watermark / Header Noise Dialog
    if (showNoiseDialog) {
        AlertDialog(
            onDismissRequest = { showNoiseDialog = false },
            title = { Text("Strip Watermark / Header Noise") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Automatically strips page numbers & repeated lines. Optionally specify exact header/watermark text to remove (e.g. 'Virtual University'):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = customNoiseText,
                        onValueChange = { customNoiseText = it },
                        label = { Text("Optional text to remove") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cleanWordStripNoise(customNoiseText)
                        showNoiseDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary)
                ) {
                    Text("Clean Now", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoiseDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Bulk Paste / Fast Raw Text Editor Dialog
    if (showPasteBulkDialog) {
        AlertDialog(
            onDismissRequest = { showPasteBulkDialog = false },
            title = { Text("Bulk Text / Fast Note Input") },
            text = {
                OutlinedTextField(
                    value = bulkPasteText,
                    onValueChange = { bulkPasteText = it },
                    label = { Text("Paste or edit full text") },
                    minLines = 8,
                    maxLines = 14,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newBlocks = bulkPasteText
                            .split(Regex("\\n\\s*\\n|\\n"))
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .map { WordBlock(type = WordBlockType.PARAGRAPH, text = it) }
                        if (newBlocks.isNotEmpty()) {
                            viewModel.updateWordBlocks(newBlocks)
                            viewModel.cleanWordAutoHeadings()
                        }
                        showPasteBulkDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary)
                ) {
                    Text("Apply & Format", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteBulkDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun WordBlockEditorCard(
    block: WordBlock,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onTextChange: (String) -> Unit,
    onCycleType: (WordBlockType) -> Unit,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onAddBelow: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Mini Format Bar per block
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        WordBlockType.HEADING_1 to "H1",
                        WordBlockType.HEADING_2 to "H2",
                        WordBlockType.PARAGRAPH to "Body",
                        WordBlockType.BULLET to "•"
                    ).forEach { (type, label) ->
                        val active = block.type == type
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (active) CamScanPrimary else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .clickable { onCycleType(type) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    // Bold / Italic
                    IconButton(onClick = onToggleBold, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.FormatBold,
                            contentDescription = "Bold",
                            tint = if (block.isBold) CamScanPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onToggleItalic, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.FormatItalic,
                            contentDescription = "Italic",
                            tint = if (block.isItalic) CamScanPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canMoveUp) {
                        IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                        }
                    }
                    if (canMoveDown) {
                        IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                        }
                    }
                    IconButton(onClick = onAddBelow, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Add block below", tint = CamScanPrimary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete block", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)

            val textStyle = when (block.type) {
                WordBlockType.HEADING_1 -> MaterialTheme.typography.titleLarge.copy(
                    color = CamScanPrimary,
                    fontWeight = FontWeight.Bold
                )
                WordBlockType.HEADING_2 -> MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                else -> MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (block.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (block.isItalic) FontStyle.Italic else FontStyle.Normal
                )
            }

            Row(verticalAlignment = Alignment.Top) {
                if (block.type == WordBlockType.BULLET) {
                    Text(
                        text = "• ",
                        style = textStyle,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                BasicTextField(
                    value = block.text,
                    onValueChange = onTextChange,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(CamScanPrimary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (block.text.isEmpty()) {
                            Text(
                                text = "Write here...",
                                style = textStyle.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                            )
                        }
                        inner()
                    }
                )
            }
        }
    }
}

@Composable
private fun CleanerActionRow(
    title: String,
    subtitle: String,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isPrimary) CamScanPrimary.copy(alpha = 0.14f)
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isPrimary) CamScanAccent else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(12.dp)
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isPrimary) CamScanPrimary else MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            )
        }
    }
}

package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.util.FileTemplates
import com.example.util.SyntaxHighlighter
import com.example.ui.theme.CamScanAccent
import com.example.ui.theme.CamScanPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeReaderScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val document by viewModel.activeDocument.collectAsState()
    val lines by viewModel.codeLines.collectAsState()
    val codeContent by viewModel.codeContent.collectAsState()
    val nightMode by viewModel.isReaderNightMode.collectAsState()
    val doc = document ?: return

    var searchQuery by remember(doc.id) { mutableStateOf("") }
    var fontSizeSp by remember { mutableFloatStateOf(13.5f) }
    var wordWrap by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var editBuffer by remember { mutableStateOf("") }
    var showReplaceRow by remember { mutableStateOf(false) }
    var replaceQuery by remember { mutableStateOf("") }

    val canPreview = remember(doc) { FileTemplates.isPreviewable(doc.filePath ?: doc.title) }
    val lang = remember(doc) { SyntaxHighlighter.langFor(doc.filePath ?: doc.title) }
    val palette = if (nightMode) SyntaxHighlighter.nightPalette() else SyntaxHighlighter.dayPalette()

    fun enterEditMode() {
        editBuffer = codeContent
        isEditing = true
    }

    fun exitEditMode() {
        isEditing = false
        editBuffer = ""
    }

    fun saveAndExitEditMode() {
        viewModel.saveActiveCodeFile(editBuffer)
        isEditing = false
        Toast.makeText(context, "Saved ${doc.title}", Toast.LENGTH_SHORT).show()
    }

    // Auto-save on back press while editing; otherwise navigate back.
    BackHandler(enabled = isEditing) {
        saveAndExitEditMode()
    }
    BackHandler(enabled = !isEditing) {
        viewModel.navigateBack()
    }

    fun replaceAll() {
        if (searchQuery.isBlank()) return
        if (!isEditing) enterEditMode()
        val count = Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE)
            .findAll(editBuffer)
            .count()
        editBuffer = Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE)
            .replace(editBuffer, replaceQuery)
        Toast.makeText(context, "Replaced $count occurrence(s) — tap Save to keep", Toast.LENGTH_LONG).show()
    }

    val editorBg = if (nightMode) Color(0xFF0E1413) else Color(0xFFFAFBFB)
    val gutterColor = if (nightMode) Color(0xFF566B67) else Color(0xFF8B9996)
    val codeTextColor = if (nightMode) Color(0xFFE3EFEC) else Color(0xFF1A2422)
    val highlightBg = if (nightMode) Color(0xFF1F3B33) else Color(0xFFFFF59D)

    val displayedLines = remember(lines, searchQuery) {
        lines.mapIndexed { idx, line -> idx + 1 to line }
    }

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
                                text = when {
                                    isEditing -> "Editing — unsaved changes"
                                    canPreview -> "${lines.size} lines • Code Viewer + Offline Preview"
                                    else -> "${lines.size} lines • Code & Text Viewer"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                )
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.navigateBack() },
                            modifier = Modifier.testTag("code_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (canPreview && !isEditing) {
                            IconButton(onClick = { viewModel.openHtmlPreview() }) {
                                Icon(
                                    Icons.Default.Visibility,
                                    contentDescription = "Offline Preview",
                                    tint = CamScanPrimary
                                )
                            }
                        }
                        if (isEditing) {
                            IconButton(onClick = { exitEditMode() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel Editing")
                            }
                        } else {
                            IconButton(onClick = { enterEditMode() }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit File",
                                    tint = CamScanPrimary
                                )
                            }
                        }
                        IconButton(onClick = { showReplaceRow = !showReplaceRow }) {
                            Icon(
                                Icons.Default.FindReplace,
                                contentDescription = "Find & Replace",
                                tint = if (showReplaceRow) CamScanPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { wordWrap = !wordWrap }) {
                            Icon(
                                Icons.Default.WrapText,
                                contentDescription = "Toggle Word Wrap",
                                tint = if (wordWrap) CamScanPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { viewModel.setReaderNightMode(!nightMode) }) {
                            Icon(
                                imageVector = if (nightMode) Icons.Default.BrightnessHigh else Icons.Default.Brightness4,
                                contentDescription = "Toggle Night Mode",
                                tint = if (nightMode) CamScanAccent else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { viewModel.shareDocumentFile(doc) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share File")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Find in file...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )

                if (showReplaceRow) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = replaceQuery,
                            onValueChange = { replaceQuery = it },
                            placeholder = { Text("Replace with...") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { replaceAll() },
                            enabled = searchQuery.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Replace All", fontWeight = FontWeight.Bold, color = Color.White)
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
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEditing) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { exitEditMode() },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) { Text("Discard") }
                        }
                        Button(
                            onClick = { saveAndExitEditMode() },
                            colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { fontSizeSp = (fontSizeSp - 1.5f).coerceAtLeast(10f) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) { Text("A-") }
                            OutlinedButton(
                                onClick = { fontSizeSp = (fontSizeSp + 1.5f).coerceAtMost(24f) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) { Text("A+") }
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(lines.joinToString("\n")))
                                    Toast.makeText(context, "Copied ${lines.size} lines", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy")
                            }
                        }

                        Button(
                            onClick = { viewModel.exportActiveCodeToPdf() },
                            colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Export PDF", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        if (isEditing) {
            // Edit mode: monospace editor bound to the file content.
            OutlinedTextField(
                value = editBuffer,
                onValueChange = { editBuffer = it },
                modifier = Modifier
                    .fillMaxSize()
                    .background(editorBg)
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp,
                    color = codeTextColor
                ),
                placeholder = { Text("Start typing...", color = gutterColor) }
            )
        } else {
            val hScroll = rememberScrollState()
            LazyColumn(
                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .background(editorBg)
                    .padding(padding)
                    .then(if (wordWrap) Modifier else Modifier.horizontalScroll(hScroll))
            ) {
                itemsIndexed(displayedLines, key = { _, pair -> pair.first }) { _, (lineNum, text) ->
                    val isMatch = searchQuery.isNotBlank() && text.contains(searchQuery, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isMatch) highlightBg else Color.Transparent)
                            .padding(vertical = 1.5.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = lineNum.toString().padStart(4, ' '),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSizeSp.sp,
                                color = gutterColor
                            ),
                            modifier = Modifier.width(44.dp)
                        )
                        Text(
                            text = SyntaxHighlighter.highlightLine(text, lang, palette),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSizeSp.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

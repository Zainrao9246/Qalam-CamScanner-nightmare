package com.example.ui.screens

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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

    // Undo / redo stacks (v2.5) — snapshots taken at typing-burst boundaries.
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }
    var lastEditMs by remember { mutableLongStateOf(0L) }

    // Offline JavaScript runner (v2.5) — .js files execute in a sandboxed
    // local WebView; console output is piped back through a JS bridge.
    var showJsConsole by remember { mutableStateOf(false) }
    var jsHarnessHtml by remember { mutableStateOf("") }
    var jsLoadedHarness by remember { mutableStateOf("") }
    var jsFinished by remember { mutableStateOf(true) }
    val jsOutput = remember { mutableStateListOf<String>() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val isJsFile = remember(doc) {
        (doc.filePath ?: doc.title).substringAfterLast('.', "").lowercase() == "js"
    }

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
        undoStack.clear()
        redoStack.clear()
    }

    fun saveAndExitEditMode() {
        viewModel.saveActiveCodeFile(editBuffer)
        isEditing = false
        undoStack.clear()
        redoStack.clear()
        Toast.makeText(context, "Saved ${doc.title}", Toast.LENGTH_SHORT).show()
    }

    fun onEditBufferChange(newValue: String) {
        val now = System.currentTimeMillis()
        if (now - lastEditMs > 600 || undoStack.isEmpty()) {
            undoStack.add(editBuffer)
            if (undoStack.size > 200) undoStack.removeAt(0)
            redoStack.clear()
        }
        lastEditMs = now
        editBuffer = newValue
    }

    fun undoEdit() {
        if (undoStack.isEmpty()) return
        redoStack.add(editBuffer)
        editBuffer = undoStack.removeAt(undoStack.size - 1)
        lastEditMs = 0L
    }

    fun redoEdit() {
        if (redoStack.isEmpty()) return
        undoStack.add(editBuffer)
        editBuffer = redoStack.removeAt(redoStack.size - 1)
        lastEditMs = 0L
    }

    fun runJsFile() {
        jsOutput.clear()
        jsFinished = false
        jsLoadedHarness = ""
        showJsConsole = true
        val safeCode = codeContent.replace("</script>", "<\\/script>", ignoreCase = true)
        jsHarnessHtml = """<!DOCTYPE html>
<html><head><meta charset="utf-8"/></head><body>
<script>
var bridge = window.JsBridge;
function send(tag, msg) { bridge.log(tag + msg); }
console.log = function() {
  var parts = [];
  for (var i = 0; i < arguments.length; i++) {
    var v = arguments[i];
    try { parts.push(typeof v === 'object' && v !== null ? JSON.stringify(v) : String(v)); }
    catch (e) { parts.push(String(v)); }
  }
  send('LOG ', parts.join(' '));
};
console.error = function() { send('ERR ', 'console.error: ' + Array.prototype.join.call(arguments, ' ')); };
console.warn = function() { send('WRN ', Array.prototype.join.call(arguments, ' ')); };
window.onerror = function(message, source, line) { send('ERR ', 'Line ' + line + ': ' + message); return true; };
</script>
<script>
try {
$safeCode
} catch (err) {
  send('ERR ', err && err.message ? err.message : String(err));
}
send('DONE ', 'Program finished.');
</script>
</body></html>"""
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
        undoStack.add(editBuffer)
        redoStack.clear()
        lastEditMs = 0L
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
                        if (isJsFile) {
                            IconButton(
                                onClick = { runJsFile() },
                                modifier = Modifier.testTag("code_run_btn")
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Run JavaScript",
                                    tint = CamScanPrimary
                                )
                            }
                        }
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
                            IconButton(
                                onClick = { undoEdit() },
                                enabled = undoStack.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo",
                                    tint = if (undoStack.isNotEmpty()) CamScanPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            }
                            IconButton(
                                onClick = { redoEdit() },
                                enabled = redoStack.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = "Redo",
                                    tint = if (redoStack.isNotEmpty()) CamScanPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            }
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
            // Edit mode (v2.5): monospace editor with a live line-number gutter.
            // Gutter and editor share one vertical scroll; the editor region has
            // its own horizontal scroll so long lines never wrap out of sync.
            val editVScroll = rememberScrollState()
            val editHScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(editorBg)
                    .padding(padding)
                    .verticalScroll(editVScroll)
            ) {
                Text(
                    text = editBuffer.lines().indices
                        .joinToString("\n") { (it + 1).toString().padStart(3, ' ') },
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * 1.5f).sp,
                        color = gutterColor
                    ),
                    modifier = Modifier
                        .padding(start = 10.dp, end = 6.dp, top = 10.dp)
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(editHScroll)
                ) {
                    BasicTextField(
                        value = editBuffer,
                        onValueChange = { onEditBufferChange(it) },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.5f).sp,
                            color = codeTextColor
                        ),
                        modifier = Modifier
                            .padding(end = 16.dp, top = 10.dp)
                    )
                }
            }
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

    // 10s watchdog for the JS runner.
    LaunchedEffect(showJsConsole) {
        if (showJsConsole) {
            kotlinx.coroutines.delay(10000)
            if (showJsConsole && !jsFinished) {
                jsOutput.add("WRN (no output for 10s — infinite loop or async code)")
            }
        }
    }

    if (showJsConsole) {
        AlertDialog(
            onDismissRequest = { showJsConsole = false },
            title = { Text("JavaScript Console", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Offline run — no network access",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (jsOutput.isEmpty()) {
                        Text(
                            "Running...",
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = gutterColor)
                        )
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(Color(0xFF0E1413), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        items(jsOutput.size) { i ->
                            val line = jsOutput[i]
                            val lineColor = when {
                                line.startsWith("ERR ") -> Color(0xFFEF9A9A)
                                line.startsWith("WRN ") -> Color(0xFFFFD54F)
                                line.startsWith("DONE ") -> Color(0xFFA5D6A7)
                                else -> Color(0xFFE3EFEC)
                            }
                            Text(
                                text = if (line.startsWith("LOG ")) line.removePrefix("LOG ")
                                else if (line.startsWith("ERR ")) line.removePrefix("ERR ")
                                else if (line.startsWith("WRN ")) line.removePrefix("WRN ")
                                else line.removePrefix("DONE "),
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = lineColor)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                    // Hidden 1dp WebView executes the harness; output flows to jsOutput.
                    // Reloads only when the harness actually changes, never per recomposition.
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                webViewClient = WebViewClient()
                                addJavascriptInterface(
                                    JsConsoleBridge { message ->
                                        mainHandler.post {
                                            jsOutput.add(message)
                                            if (message.startsWith("DONE ")) jsFinished = true
                                        }
                                    },
                                    "JsBridge"
                                )
                            }
                        },
                        update = { webView ->
                            if (jsLoadedHarness != jsHarnessHtml) {
                                jsLoadedHarness = jsHarnessHtml
                                webView.loadDataWithBaseURL(null, jsHarnessHtml, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.height(1.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showJsConsole = false }) { Text("Close") }
            },
            dismissButton = {
                TextButton(onClick = { runJsFile() }) { Text("Re-run") }
            }
        )
    }
}

/** Bridges JavaScript console output from the sandboxed WebView into Compose state. */
class JsConsoleBridge(private val onLog: (String) -> Unit) {
    @JavascriptInterface
    fun log(message: String) {
        onLog(message)
    }
}

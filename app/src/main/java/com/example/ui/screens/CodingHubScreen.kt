package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.DocKind
import com.example.model.Document
import com.example.ui.MainViewModel
import com.example.ui.theme.CamScanPrimary
import com.example.util.FileTemplates
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated Coding Hub — one place for every code / web / data file in the library.
 * Quick-create any code file from offline templates, open it in the syntax
 * editor, or jump straight into the offline HTML / Markdown preview.
 * Fully offline: it only reads the existing Room library.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CodingHubScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.navigateBack() }

    val allDocs by viewModel.allDocuments.collectAsState()
    val codeDocs = remember(allDocs) {
        allDocs.filter { it.kind == DocKind.CODE }.sortedByDescending { it.updatedAt }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedExt by remember { mutableStateOf("py") }
    var newFileName by remember { mutableStateOf("") }
    var newFileSubject by remember { mutableStateOf("") }

    val codeTypes = remember {
        FileTemplates.CREATE_TYPES.filter { it.ext !in setOf("docx", "xlsx", "pptx") }
    }
    val selectedType = remember(selectedExt) { FileTemplates.findByExt(selectedExt) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Coding Hub", fontWeight = FontWeight.Bold)
                            Text(
                                "${codeDocs.size} code file${if (codeDocs.size == 1) "" else "s"} • offline editor & preview",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = CamScanPrimary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("New Code File")
                }
            }
        ) { padding ->
            if (codeDocs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        tint = CamScanPrimary.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No code files yet",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap \"New Code File\" to create your first script, web page, Markdown note or data file — Python, Java, Kotlin, C++, HTML, CSS, JS and more, all offline.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(codeDocs, key = { it.id }) { doc ->
                        CodeFileRow(
                            doc = doc,
                            ext = extOf(doc),
                            dateText = dateFormat.format(Date(doc.updatedAt)),
                            onOpen = { viewModel.openDocument(doc) },
                            onPreview = { viewModel.openCodePreview(doc) }
                        )
                    }
                }
            }
        }
    }

    // Quick-create dialog: extension chips with plain-language descriptions
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Code File") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Pick an extension — every file opens in the syntax editor, and HTML & Markdown also get an offline preview.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        codeTypes.forEach { type ->
                            val active = type.ext == selectedExt
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (active) CamScanPrimary.copy(alpha = 0.18f)
                                        else Color.Transparent
                                    )
                                    .border(
                                        1.dp,
                                        if (active) CamScanPrimary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedExt = type.ext }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${type.ext} (${type.label})",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (active) CamScanPrimary
                                        else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }
                        }
                    }
                    selectedType?.let { type ->
                        Text(
                            text = "${type.label} — ${type.subtitle}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = CamScanPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text("File Name (e.g. DSA Notes, Index Page)") },
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
                        if (newFileName.isNotBlank()) {
                            showCreateDialog = false
                            viewModel.createNewCodeFile(newFileName.trim(), selectedExt, newFileSubject.trim())
                            newFileName = ""
                            newFileSubject = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary)
                ) {
                    Text("Create", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CodeFileRow(
    doc: Document,
    ext: String,
    dateText: String,
    onOpen: () -> Unit,
    onPreview: () -> Unit
) {
    val canPreview = remember(doc.id) {
        FileTemplates.isPreviewable(doc.filePath ?: doc.title)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Terminal,
                contentDescription = null,
                tint = CamScanPrimary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    doc.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(if (ext.isNotBlank()) ".$ext" else "code")
                        if (doc.subjectTag.isNotBlank()) append("  •  ${doc.subjectTag}")
                        append("  •  $dateText")
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (canPreview) {
                IconButton(onClick = onPreview) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = "Offline Preview",
                        tint = CamScanPrimary
                    )
                }
            }
        }
    }
}

private fun extOf(doc: Document): String {
    val name = doc.filePath ?: doc.title
    val dot = name.lastIndexOf('.')
    return if (dot >= 0) name.substring(dot + 1).lowercase(Locale.getDefault()) else ""
}

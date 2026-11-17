package com.example.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.MainViewModel
import com.example.util.FileTemplates
import com.example.ui.theme.CamScanAccent
import com.example.ui.theme.CamScanPrimary
import java.io.File

/**
 * Offline rendered preview for .html / .htm / .md files.
 * Renders inside a WebView entirely from local file content — the app has no
 * INTERNET permission, so nothing can ever be fetched from the network here.
 * The "Code" action jumps back to the source editor (CodeReaderScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlPreviewScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.navigateBack() }

    val file by viewModel.htmlPreviewFile.collectAsState()
    val nightMode by viewModel.isReaderNightMode.collectAsState()
    val activeDoc by viewModel.activeDocument.collectAsState()
    val target = file ?: return

    val rawHtml = remember(target.absolutePath, target.lastModified()) {
        HtmlPreviewEngine.buildRenderableHtml(target)
    }
    val injectedHtml = remember(rawHtml, nightMode) {
        if (!nightMode) {
            rawHtml
        } else {
            // Offline dark inversion via CSS filter — no external resources needed.
            val darkStyle = "<style>html { filter: invert(1) hue-rotate(180deg); background: #ffffff; }</style>"
            if (rawHtml.contains("<head>", ignoreCase = true)) {
                rawHtml.replaceFirst("<head>", "<head>$darkStyle", ignoreCase = true)
            } else {
                "<!DOCTYPE html><html><head>$darkStyle</head><body>$rawHtml</body></html>"
            }
        }
    }

    Scaffold(
        containerColor = if (nightMode) Color(0xFF101413) else Color(0xFFF7F8F8),
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = activeDoc?.title ?: target.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Offline Preview • ${target.name.substringAfterLast('.', "html").uppercase()}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                )
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.navigateBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.setReaderNightMode(!nightMode) }) {
                            Icon(
                                imageVector = if (nightMode) Icons.Default.BrightnessHigh else Icons.Default.Brightness4,
                                contentDescription = "Toggle Night Mode",
                                tint = if (nightMode) CamScanAccent else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { viewModel.navigateBack() }) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = "View Source",
                                tint = CamScanPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true      // local scripts only; no INTERNET permission in manifest
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    webViewClient = WebViewClient()
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL(null, injectedHtml, "text/html", "UTF-8", null)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

/** Builds ready-to-render HTML for a local file (raw HTML passthrough or Markdown conversion). */
object HtmlPreviewEngine {

    fun buildRenderableHtml(file: File): String {
        val content = runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
        val ext = file.name.substringAfterLast('.', "").lowercase()
        return if (ext == "md" || ext == "markdown") {
            wrapMarkdownDocument(content, file.name.substringBeforeLast('.'))
        } else {
            content.ifBlank {
                "<!DOCTYPE html><html><body style='font-family:sans-serif;padding:24px;'>" +
                    "<h3>Empty file</h3><p>This file has no content yet. Tap the code icon to edit it.</p></body></html>"
            }
        }
    }

    private fun wrapMarkdownDocument(markdown: String, title: String): String {
        val body = FileTemplates.markdownToHtml(markdown)
        return """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${FileTemplates.escapeHtml(title)}</title>
  <style>
    body { font-family: sans-serif; margin: 20px; line-height: 1.65; color: #1a2422; }
    h1, h2, h3, h4 { color: #2c6e49; margin-top: 1.2em; }
    pre { background: #f0f2f1; padding: 12px; border-radius: 8px; overflow-x: auto; font-size: 13px; }
    code { font-family: monospace; background: #eef1ef; padding: 1px 5px; border-radius: 4px; }
    pre code { background: transparent; padding: 0; }
    blockquote { border-left: 4px solid #2c6e49; margin: 12px 0; padding: 4px 14px; background: #f4f7f5; }
    a { color: #2c6e49; }
    hr { border: none; border-top: 1px solid #d8ddd9; margin: 18px 0; }
    li { margin: 4px 0; }
  </style>
</head>
<body>
$body
</body>
</html>"""
    }
}

package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiTasks
import com.example.ai.GroqClient
import com.example.ai.GroqMessage
import com.example.ai.GroqModels
import com.example.model.WordBlock
import com.example.model.WordBlockType
import com.example.ui.MainViewModel
import com.example.ui.theme.CamScanPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Qalam AI task modes (v2.6). */
enum class AiMode(val label: String, val hint: String) {
    IMAGE_TO_WORD("Image → Word", "Photo of notes/page se formatted Word doc banao"),
    GRAMMAR("Fix Grammar", "Text ki grammar, spelling aur punctuation theek karo"),
    BUGS("Find Bugs", "Code ke bugs dhundo aur fixed code do"),
    EXPLAIN("Explain", "Kisi bhi content ko asaan zubaan mein samjhao"),
    SUMMARIZE("Summarize", "Lambay notes ko key points mein summarize karo"),
    EXCEL_FORMULA("Excel Formula", "Batao calculation kya hai — formula ready milega")
}

/**
 * Qalam AI Hub (v2.6) — Groq-powered assistant in its own self-contained file.
 * Free API key from console.groq.com/keys; the key lives only on the device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.navigateBack() }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val apiKey by viewModel.aiApiKey.collectAsState()
    val savedModel by viewModel.aiModelId.collectAsState()
    val activeDoc by viewModel.activeDocument.collectAsState()
    val codeContent by viewModel.codeContent.collectAsState()

    var showKeyField by remember { mutableStateOf(apiKey.isBlank()) }
    var keyInput by remember { mutableStateOf("") }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var customModelOpen by remember { mutableStateOf(false) }
    var customModelId by remember { mutableStateOf("") }

    var mode by remember { mutableStateOf(AiMode.IMAGE_TO_WORD) }
    var pickedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageBase64 by remember { mutableStateOf<String?>(null) }
    var inputText by remember { mutableStateOf("") }
    var docTitle by remember { mutableStateOf("") }
    var codeExt by remember { mutableStateOf("js") }
    var output by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var tokensUsed by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            errorMsg = ""
            scope.launch(Dispatchers.IO) {
                val bmp = loadBitmapFromUri(context, uri, maxDim = 1600)
                val b64 = bmp?.let { bitmapToBase64(it) }
                withContext(Dispatchers.Main) {
                    isLoading = false
                    pickedBitmap = bmp
                    imageBase64 = b64
                    if (b64 == null) errorMsg = "Could not read the selected image — try another one"
                }
            }
        }
    }

    val modelLabel = if (savedModel == GroqModels.CUSTOM && customModelId.isNotBlank()) {
        "Custom: $customModelId"
    } else {
        GroqModels.findById(savedModel).displayName
    }

    fun resolveModel(): String = when {
        mode == AiMode.IMAGE_TO_WORD -> GroqModels.VISION_MODEL
        savedModel == GroqModels.CUSTOM -> customModelId.trim().ifBlank { GroqModels.DEFAULT_MODEL }
        else -> savedModel
    }

    fun buildMessages(currentMode: AiMode, input: String, imageB64: String?): List<GroqMessage> = when (currentMode) {
        AiMode.IMAGE_TO_WORD -> listOf(
            GroqMessage("system", AiTasks.IMAGE_TO_WORD),
            GroqMessage("user", "Convert this page into document lines.", imageB64)
        )
        AiMode.GRAMMAR -> listOf(GroqMessage("system", AiTasks.GRAMMAR), GroqMessage("user", input))
        AiMode.BUGS -> listOf(GroqMessage("system", AiTasks.BUGS), GroqMessage("user", input))
        AiMode.EXPLAIN -> listOf(GroqMessage("system", AiTasks.EXPLAIN), GroqMessage("user", input))
        AiMode.SUMMARIZE -> listOf(GroqMessage("system", AiTasks.SUMMARIZE), GroqMessage("user", input))
        AiMode.EXCEL_FORMULA -> listOf(GroqMessage("system", AiTasks.EXCEL_FORMULA), GroqMessage("user", input))
    }

    fun runAi() {
        if (apiKey.isBlank()) {
            errorMsg = "Pehle apni free Groq API key add karo"
            showKeyField = true
            return
        }
        if (mode == AiMode.IMAGE_TO_WORD && imageBase64 == null) {
            errorMsg = "Pehle image choose karo"
            return
        }
        val input = inputText.trim()
        if (mode != AiMode.IMAGE_TO_WORD && input.isEmpty()) {
            errorMsg = "Kuch text type karo ya open code file load karo"
            return
        }
        scope.launch {
            isLoading = true
            errorMsg = ""
            output = ""
            val result = GroqClient(apiKey).chat(
                model = resolveModel(),
                messages = buildMessages(mode, input, imageBase64)
            )
            isLoading = false
            if (result.ok) {
                output = result.content
                tokensUsed = result.tokensUsed
            } else {
                errorMsg = result.error
            }
        }
    }

    fun saveOutputAsWord() {
        if (output.isBlank()) return
        val blocks = when (mode) {
            AiMode.IMAGE_TO_WORD, AiMode.SUMMARIZE -> AiTasks.parseAiDocument(output)
            else -> output.lines()
                .filter { it.isNotBlank() }
                .map { WordBlock(type = WordBlockType.PARAGRAPH, text = AiTasks.stripMarkdown(it)) }
        }
        if (blocks.isEmpty()) {
            Toast.makeText(context, "Nothing to save", Toast.LENGTH_SHORT).show()
            return
        }
        val title = docTitle.trim().ifBlank {
            when (mode) {
                AiMode.IMAGE_TO_WORD -> "Scanned Page (AI)"
                AiMode.GRAMMAR -> "Corrected Text (AI)"
                AiMode.EXPLAIN -> "AI Explanation"
                AiMode.SUMMARIZE -> "AI Summary"
                else -> "AI Notes"
            }
        }
        viewModel.createAiWordDocument(title, blocks)
        Toast.makeText(context, "Word document created — $title", Toast.LENGTH_SHORT).show()
    }

    fun saveOutputAsCode() {
        val code = AiTasks.extractCode(output)
        if (code.isBlank()) {
            Toast.makeText(context, "No code found in the reply", Toast.LENGTH_SHORT).show()
            return
        }
        val ext = codeExt.trim('.').lowercase().ifBlank { "js" }
        val title = docTitle.trim().ifBlank { "AI Fixed Code" }
        viewModel.createAiCodeFile("$title.$ext", ext, code)
        Toast.makeText(context, "Code file created — $title.$ext", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Qalam AI", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Groq • $modelLabel • free tier",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------- API key card ----------
            if (showKeyField || apiKey.isBlank()) {
                item(key = "key_card") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "Groq API Key",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Free key banao: console.groq.com/keys → \"Create API Key\". " +
                                    "Key sirf tumhare phone pe store hoti hai.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = keyInput,
                                onValueChange = { keyInput = it },
                                label = { Text("gsk_...") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (keyInput.isNotBlank()) {
                                        viewModel.setAiApiKey(keyInput)
                                        showKeyField = false
                                        keyInput = ""
                                        Toast.makeText(context, "API key saved", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
                                modifier = Modifier.testTag("ai_save_key_btn")
                            ) {
                                Text("Save Key", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                item(key = "key_row") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CamScanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "API key connected (gsk_••••${apiKey.takeLast(4)})",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = { showKeyField = true }) { Text("Change") }
                    }
                }
            }

            // ---------- Model selector ----------
            item(key = "model") {
                Box {
                    OutlinedButton(
                        onClick = { modelMenuOpen = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_model_btn")
                    ) {
                        Text("Model: $modelLabel", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = modelMenuOpen, onDismissRequest = { modelMenuOpen = false }) {
                        GroqModels.ALL.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(model.displayName, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            model.note,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        )
                                    }
                                },
                                onClick = {
                                    modelMenuOpen = false
                                    viewModel.setAiModel(model.id)
                                    if (model.id == GroqModels.CUSTOM) customModelOpen = true
                                }
                            )
                        }
                    }
                }
            }

            // ---------- Mode chips ----------
            item(key = "modes") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AiMode.values().size) { index ->
                        val candidate = AiMode.values()[index]
                        val selected = candidate == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) CamScanPrimary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .clickable {
                                    mode = candidate
                                    errorMsg = ""
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("ai_mode_${candidate.name.lowercase()}")
                        ) {
                            Text(
                                candidate.label,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // ---------- Mode input ----------
            item(key = "input") {
                Column {
                    Text(
                        mode.hint,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = CamScanPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    if (mode == AiMode.IMAGE_TO_WORD) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    imagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .testTag("ai_image_pick"),
                            contentAlignment = Alignment.Center
                        ) {
                            val bmp = pickedBitmap
                            if (bmp != null) {
                                ImagePreview(bitmap = bmp)
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.AddPhotoAlternate,
                                        contentDescription = "Pick Image",
                                        tint = CamScanPrimary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Tap to choose a page photo",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Vision model (${GroqModels.VISION_MODEL}) auto-use hoga — image compressed to 1600px JPEG.",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        )
                    } else {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            label = {
                                Text(
                                    when (mode) {
                                        AiMode.BUGS -> "Paste code here..."
                                        AiMode.EXCEL_FORMULA -> "e.g. Marks column A mein hain, 80 se zyada ka PASS count..."
                                        else -> "Paste or type your text..."
                                    }
                                )
                            },
                            minLines = 4,
                            maxLines = 10,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val canLoadCode = activeDoc?.kind == com.example.model.DocKind.CODE && codeContent.isNotBlank()
                        if (canLoadCode && mode != AiMode.EXCEL_FORMULA) {
                            TextButton(
                                onClick = {
                                    inputText = codeContent
                                    codeExt = (activeDoc?.filePath ?: activeDoc?.title ?: "js")
                                        .substringAfterLast('.', "js")
                                }
                            ) {
                                Text("Load my open code file (${activeDoc?.title ?: ""})")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = docTitle,
                        onValueChange = { docTitle = it },
                        label = { Text("New document title (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (mode == AiMode.BUGS) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = codeExt,
                            onValueChange = { codeExt = it },
                            label = { Text("Code file extension (js, py, kt...)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ---------- Run ----------
            item(key = "run") {
                Button(
                    onClick = { runAi() },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_run_btn")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Thinking...", color = Color.White)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Run AI", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (errorMsg.isNotBlank()) {
                item(key = "error") {
                    Text(
                        errorMsg,
                        color = Color(0xFFC62828),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // ---------- Output ----------
            if (output.isNotBlank()) {
                item(key = "output") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (mode == AiMode.BUGS) Color(0xFF0E1413)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                if (tokensUsed > 0) "AI Response • $tokensUsed tokens" else "AI Response",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                )
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                output,
                                style = if (mode == AiMode.BUGS) {
                                    androidx.compose.ui.text.TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = Color(0xFFE3EFEC)
                                    )
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(output))
                                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy")
                                }
                                if (mode != AiMode.BUGS && mode != AiMode.EXCEL_FORMULA) {
                                    Button(
                                        onClick = { saveOutputAsWord() },
                                        colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
                                        modifier = Modifier.testTag("ai_save_word_btn")
                                    ) {
                                        Icon(Icons.Default.Description, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Save as Word", color = Color.White)
                                    }
                                }
                                if (mode == AiMode.BUGS) {
                                    Button(
                                        onClick = { saveOutputAsCode() },
                                        colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
                                        modifier = Modifier.testTag("ai_save_code_btn")
                                    ) {
                                        Icon(Icons.Default.Code, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Save as Code", color = Color.White)
                                    }
                                }
                            }
                            if (mode == AiMode.EXCEL_FORMULA) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Formula copy karke Excel cell mein paste karo — Qalam spreadsheet usse calculate karega.",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Small non-experimental preview for the picked page photo. */
@Composable
private fun ImagePreview(bitmap: Bitmap) {
    val imageBitmap = bitmap.asImageBitmap()
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val viewW = size.width
        val viewH = size.height
        val scale = minOf(viewW / bitmap.width, viewH / bitmap.height)
        val drawW = bitmap.width * scale
        val drawH = bitmap.height * scale
        drawImage(
            imageBitmap,
            dstOffset = androidx.compose.ui.geometry.IntOffset(
                ((viewW - drawW) / 2f).toInt(),
                ((viewH - drawH) / 2f).toInt()
            ),
            dstSize = androidx.compose.ui.geometry.IntSize(drawW.toInt(), drawH.toInt())
        )
    }
}

/** Decodes a content Uri into a downscaled Bitmap. */
private fun loadBitmapFromUri(context: android.content.Context, uri: Uri, maxDim: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    } ?: return null
    val scale = maxDim.toFloat() / maxOf(decoded.width, decoded.height)
    if (scale < 1f) {
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else {
        decoded
    }
}.getOrNull()

/** Compresses a Bitmap to base64 JPEG for the vision request. */
private fun bitmapToBase64(bitmap: Bitmap): String = runCatching {
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
    Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}.getOrDefault("")

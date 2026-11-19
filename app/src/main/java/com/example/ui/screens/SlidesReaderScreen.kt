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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SlideItem
import com.example.ui.MainViewModel
import com.example.ui.theme.CamScanAccent
import com.example.ui.theme.CamScanPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlidesReaderScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val document by viewModel.activeDocument.collectAsState()
    val slides by viewModel.slidesList.collectAsState()
    val nightMode by viewModel.isReaderNightMode.collectAsState()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsState()

    val doc = document ?: return
    var isCarouselMode by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var editingSlideNumber by remember { mutableStateOf<Int?>(null) }

    val slideBg = if (nightMode) Color(0xFF141918) else MaterialTheme.colorScheme.background
    val cardBg = if (nightMode) Color(0xFF1C2321) else Color.White
    val slideAccent = Color(0xFFD84315)
    val textColor = if (nightMode) Color(0xFFE7EFEC) else Color(0xFF1C2422)

    BackHandler {
        viewModel.stopTts()
        if (isEditMode) {
            viewModel.saveActiveSlides()
        }
        viewModel.navigateBack()
    }

    Scaffold(
        topBar = {
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
                            text = "${slides.size} ${if (slides.size == 1) "slide" else "slides"}${if (doc.subjectTag.isNotBlank()) " • ${doc.subjectTag}" else ""}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.stopTts()
                            viewModel.navigateBack()
                        },
                        modifier = Modifier.testTag("slides_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (isTtsSpeaking) {
                                viewModel.stopTts()
                            } else if (slides.isNotEmpty()) {
                                val text = slides.joinToString("\n") { s ->
                                    buildString {
                                        append("Slide ${s.number}: ${s.title}. ")
                                        if (s.bodyLines.isNotEmpty()) append(s.bodyLines.joinToString(". "))
                                        if (s.notes.isNotBlank()) append(" Notes: ${s.notes}")
                                    }
                                }
                                viewModel.speakStudyText(text)
                            }
                        },
                        modifier = Modifier.testTag("slides_tts_btn")
                    ) {
                        Icon(
                            imageVector = if (isTtsSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = "Listen to Slides",
                            tint = if (isTtsSpeaking) CamScanAccent else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = {
                            isEditMode = !isEditMode
                            if (!isEditMode) viewModel.saveActiveSlides()
                        },
                        modifier = Modifier.testTag("slides_edit_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Save else Icons.Default.Edit,
                            contentDescription = if (isEditMode) "Save deck" else "Edit slides",
                            tint = if (isEditMode) CamScanAccent else CamScanPrimary
                        )
                    }
                    IconButton(
                        onClick = { isCarouselMode = !isCarouselMode },
                        modifier = Modifier.testTag("slides_mode_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (isCarouselMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.ViewCarousel,
                            contentDescription = "Toggle Carousel / List",
                            tint = CamScanPrimary
                        )
                    }
                    IconButton(
                        onClick = { viewModel.setReaderNightMode(!nightMode) },
                        modifier = Modifier.testTag("slides_night_mode_btn")
                    ) {
                        Icon(
                            imageVector = if (nightMode) Icons.Default.BrightnessHigh else Icons.Default.Brightness4,
                            contentDescription = "Toggle Night Reading Mode",
                            tint = if (nightMode) CamScanAccent else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = { viewModel.shareDocumentFile(doc) },
                        modifier = Modifier.testTag("slides_share_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share Slides")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
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
                    OutlinedButton(
                        onClick = { viewModel.convertSlidesToWordNotes(doc, slides) },
                        enabled = slides.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("slides_to_word_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Slides → Word")
                    }

                    Button(
                        onClick = { viewModel.convertSlidesToPdf(doc, slides) },
                        enabled = slides.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("slides_to_pdf_btn")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Slides → PDF", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        if (slides.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(slideBg)
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No slide content found in this presentation.")
            }
        } else if (isCarouselMode && !isEditMode) {
            val pagerState = rememberPagerState(pageCount = { slides.size })
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(16.dp),
                pageSpacing = 12.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .background(slideBg)
                    .padding(padding)
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    SlideDeckCard(
                        slide = slides[page],
                        totalSlides = slides.size,
                        nightMode = nightMode,
                        cardBg = cardBg,
                        slideAccent = slideAccent,
                        textColor = textColor
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .background(slideBg)
                    .padding(padding)
            ) {
                items(slides, key = { it.number }) { slide ->
                    Column {
                        SlideDeckCard(
                            slide = slide,
                            totalSlides = slides.size,
                            nightMode = nightMode,
                            cardBg = cardBg,
                            slideAccent = slideAccent,
                            textColor = textColor
                        )
                        if (isEditMode) {
                            SlideEditToolbar(
                                slide = slide,
                                canMoveUp = slide.number > 1,
                                canMoveDown = slide.number < slides.size,
                                onEdit = { editingSlideNumber = slide.number },
                                onMoveUp = { viewModel.moveSlide(slide.number, -1) },
                                onMoveDown = { viewModel.moveSlide(slide.number, 1) },
                                onDuplicate = { viewModel.duplicateSlide(slide.number) },
                                onDelete = { viewModel.deleteSlide(slide.number) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Slide editing bottom sheet
    val editingSlide = slides.firstOrNull { it.number == editingSlideNumber }
    if (editingSlide != null) {
        SlideEditSheet(
            slide = editingSlide,
            onDismiss = { editingSlideNumber = null },
            onUpdate = { updated ->
                viewModel.updateSlide(editingSlide.number, updated)
            },
            onDone = { editingSlideNumber = null }
        )
    }
}

@Composable
private fun SlideDeckCard(
    slide: SlideItem,
    totalSlides: Int,
    nightMode: Boolean,
    cardBg: Color,
    slideAccent: Color,
    textColor: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = slide.backgroundHex?.let { parseSlideColor(it) } ?: cardBg
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (nightMode) Color(0xFF2D3835) else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(slideAccent.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "SLIDE ${slide.number} / $totalSlides",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = slideAccent,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = slide.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = slide.titleColorHex?.let { parseSlideColor(it) }
                        ?: if (nightMode) CamScanAccent else Color(0xFF004D40),
                    fontSize = (slide.titleSizePt?.coerceIn(12, 60) ?: 22).sp
                )
            )

            if (slide.bodyLines.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    slide.bodyLines.forEach { line ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "•  ",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = slideAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = slide.bodyColorHex?.let { parseSlideColor(it) } ?: textColor,
                                    fontSize = (slide.bodySizePt?.coerceIn(10, 40) ?: 15).sp,
                                    lineHeight = ((slide.bodySizePt?.coerceIn(10, 40) ?: 15) * 1.45f).sp
                                )
                            )
                        }
                    }
                }
            }

            if (slide.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(
                    color = if (nightMode) Color(0xFF2D3835) else MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Speaker Notes: ${slide.notes}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = FontStyle.Italic,
                        color = textColor.copy(alpha = 0.72f)
                    )
                )
            }
        }
    }
}

@Composable
private fun SlideEditToolbar(
    slide: SlideItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onEdit,
            colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Edit", color = Color.White)
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.ArrowUpward, contentDescription = "Move slide up", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.ArrowDownward, contentDescription = "Move slide down", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDuplicate, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate slide", tint = CamScanPrimary, modifier = Modifier.size(17.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete slide", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlideEditSheet(
    slide: SlideItem,
    onDismiss: () -> Unit,
    onUpdate: (SlideItem) -> Unit,
    onDone: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var titleText by remember(slide.number) { mutableStateOf(slide.title) }
    var bodyText by remember(slide.number) { mutableStateOf(slide.bodyLines.joinToString("\n")) }
    var notesText by remember(slide.number) { mutableStateOf(slide.notes) }
    var titleColor by remember(slide.number) { mutableStateOf(slide.titleColorHex) }
    var bodyColor by remember(slide.number) { mutableStateOf(slide.bodyColorHex) }
    var titleSize by remember(slide.number) { mutableStateOf(slide.titleSizePt ?: 32) }
    var bodySize by remember(slide.number) { mutableStateOf(slide.bodySizePt ?: 18) }
    var bgColor by remember(slide.number) { mutableStateOf(slide.backgroundHex) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Edit Slide ${slide.number}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            OutlinedTextField(
                value = titleText,
                onValueChange = {
                    titleText = it
                    onUpdate(slide.copy(title = it))
                },
                label = { Text("Slide title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = bodyText,
                onValueChange = {
                    bodyText = it
                    onUpdate(slide.copy(bodyLines = it.split("\n").map { l -> l.trim() }.filter { l -> l.isNotEmpty() }))
                },
                label = { Text("Bullet points (one per line)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notesText,
                onValueChange = {
                    notesText = it
                    onUpdate(slide.copy(notes = it))
                },
                label = { Text("Speaker notes") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            // Title formatting: color dots + size stepper
            Text(
                text = "Title formatting",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            ColorDotRow(
                presets = listOf(null, "004D40", "1A237C", "C62828", "E65100", "4A148C"),
                activeHex = titleColor,
                onSelect = {
                    titleColor = it
                    onUpdate(slide.copy(titleColorHex = it))
                }
            )
            SizeStepperRow(
                label = "Title size",
                size = titleSize,
                onChange = {
                    titleSize = it
                    onUpdate(slide.copy(titleSizePt = it))
                }
            )

            Text(
                text = "Body formatting",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            ColorDotRow(
                presets = listOf(null, "1C2422", "C62828", "1565C0", "1B5E20", "6A1B9A"),
                activeHex = bodyColor,
                onSelect = {
                    bodyColor = it
                    onUpdate(slide.copy(bodyColorHex = it))
                }
            )
            SizeStepperRow(
                label = "Body size",
                size = bodySize,
                onChange = {
                    bodySize = it
                    onUpdate(slide.copy(bodySizePt = it))
                }
            )

            HorizontalDivider()

            Text(
                text = "Slide background",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            ColorDotRow(
                presets = listOf(null, "FFF8E1", "E8F5E9", "E3F2FD", "FCE4EC", "263238"),
                activeHex = bgColor,
                onSelect = {
                    bgColor = it
                    onUpdate(slide.copy(backgroundHex = it))
                }
            )

            Button(
                onClick = {
                    onUpdate(
                        slide.copy(
                            title = titleText,
                            bodyLines = bodyText.split("\n").map { l -> l.trim() }.filter { l -> l.isNotEmpty() },
                            notes = notesText,
                            titleColorHex = titleColor,
                            bodyColorHex = bodyColor,
                            titleSizePt = titleSize,
                            bodySizePt = bodySize,
                            backgroundHex = bgColor
                        )
                    )
                    onDone()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CamScanPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ColorDotRow(
    presets: List<String?>,
    activeHex: String?,
    onSelect: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { hex ->
            val active = activeHex?.uppercase() == hex?.uppercase()
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .then(
                        if (hex == null) {
                            Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        } else {
                            Modifier.background(parseSlideColor(hex) ?: Color.Gray)
                        }
                    )
                    .border(
                        width = if (active) 2.5.dp else 1.dp,
                        color = if (active) CamScanPrimary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape
                    )
                    .clickable { onSelect(hex) },
                contentAlignment = Alignment.Center
            ) {
                if (hex == null) {
                    Icon(
                        Icons.Default.FormatColorReset,
                        contentDescription = "Automatic",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SizeStepperRow(
    label: String,
    size: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange((size - 2).coerceAtLeast(10)) }, modifier = Modifier.size(32.dp)) {
                Text("A\u2212", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                text = "${size}pt",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = CamScanPrimary,
                modifier = Modifier.width(48.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = { onChange((size + 2).coerceAtMost(60)) }, modifier = Modifier.size(32.dp)) {
                Text("A+", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Parses a 6-digit hex color into a Compose Color (transparent fallback). */
private fun parseSlideColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val clean = if (hex.startsWith("#")) hex.drop(1) else hex
    if (clean.length != 6) return null
    return clean.toLongOrNull(16)?.let { Color(0xFF000000L or it) }
}

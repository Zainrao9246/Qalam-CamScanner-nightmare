package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.PictureAsPdf
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    BackHandler {
        viewModel.stopTts()
        viewModel.navigateBack()
    }

    val document by viewModel.activeDocument.collectAsState()
    val slides by viewModel.slidesList.collectAsState()
    val nightMode by viewModel.isReaderNightMode.collectAsState()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsState()

    val doc = document ?: return
    var isCarouselMode by remember { mutableStateOf(false) }

    val slideBg = if (nightMode) Color(0xFF141918) else MaterialTheme.colorScheme.background
    val cardBg = if (nightMode) Color(0xFF1C2321) else Color.White
    val slideAccent = Color(0xFFD84315)
    val textColor = if (nightMode) Color(0xFFE7EFEC) else Color(0xFF1C2422)

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
        } else if (isCarouselMode) {
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
                    SlideDeckCard(
                        slide = slide,
                        totalSlides = slides.size,
                        nightMode = nightMode,
                        cardBg = cardBg,
                        slideAccent = slideAccent,
                        textColor = textColor
                    )
                }
            }
        }
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
        colors = CardDefaults.cardColors(containerColor = cardBg),
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
                    color = if (nightMode) CamScanAccent else Color(0xFF004D40),
                    fontSize = 19.sp
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
                                    color = textColor,
                                    lineHeight = 22.sp
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

package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.screens.CameraScanScreen
import com.example.ui.screens.CodeReaderScreen
import com.example.ui.screens.CropScreen
import com.example.ui.screens.DocumentDetailScreen
import com.example.ui.screens.ExcelReaderScreen
import com.example.ui.screens.FilterScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.HtmlPreviewScreen
import com.example.ui.screens.OcrScreen
import com.example.ui.screens.PdfPreviewScreen
import com.example.ui.screens.PdfReaderScreen
import com.example.ui.screens.PdfToolsScreen
import com.example.ui.screens.PicturesToPdfScreen
import com.example.ui.screens.QrScanScreen
import com.example.ui.screens.SignatureScreen
import com.example.ui.screens.SlidesReaderScreen
import com.example.ui.screens.WordEditorScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            viewModel.handleIncomingIntent(intent)
        }

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CamScanApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIncomingIntent(intent)
    }
}

@Composable
fun CamScanApp(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val targetDocId by viewModel.transientTargetDocId.collectAsState()

    BackHandler(enabled = currentScreen != Screen.HOME) {
        viewModel.navigateBack()
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "ScreenTransition"
    ) { screen ->
        when (screen) {
            Screen.HOME -> HomeScreen(viewModel = viewModel)
            Screen.CAMERA_SCAN -> CameraScanScreen(viewModel = viewModel, targetDocId = targetDocId)
            Screen.CROP -> CropScreen(viewModel = viewModel)
            Screen.FILTER -> FilterScreen(viewModel = viewModel)
            Screen.DOCUMENT_DETAIL -> DocumentDetailScreen(viewModel = viewModel)
            Screen.PDF_READER -> PdfReaderScreen(viewModel = viewModel)
            Screen.PDF_TOOLS -> PdfToolsScreen(viewModel = viewModel)
            Screen.WORD_EDITOR -> WordEditorScreen(viewModel = viewModel)
            Screen.EXCEL_READER -> ExcelReaderScreen(viewModel = viewModel)
            Screen.SLIDES_READER -> SlidesReaderScreen(viewModel = viewModel)
            Screen.CODE_READER -> CodeReaderScreen(viewModel = viewModel)
            Screen.PICTURES_TO_PDF -> PicturesToPdfScreen(viewModel = viewModel)
            Screen.OCR -> OcrScreen(viewModel = viewModel)
            Screen.QR_SCAN -> QrScanScreen(viewModel = viewModel)
            Screen.SIGNATURE -> SignatureScreen(viewModel = viewModel)
            Screen.PDF_PREVIEW -> PdfPreviewScreen(viewModel = viewModel)
            Screen.HTML_PREVIEW -> HtmlPreviewScreen(viewModel = viewModel)
        }
    }
}

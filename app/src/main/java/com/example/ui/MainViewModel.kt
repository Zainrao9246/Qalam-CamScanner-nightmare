package com.example.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AiPreferences
import com.example.data.DocumentRepository
import com.example.data.PdfAnnotationStore
import com.example.data.StarterDocumentSeeder
import com.example.data.ThemePreferences
import com.example.model.CropQuad
import com.example.model.DocKind
import com.example.model.Document
import com.example.model.DocumentCategory
import com.example.model.ExcelCellStyle
import com.example.model.ExcelSheetData
import com.example.model.FilterType
import com.example.model.PdfCommentPin
import com.example.model.PdfDocumentAnnotations
import com.example.model.PdfStroke
import com.example.model.PictureDraftItem
import com.example.model.ScanPage
import com.example.model.SlideItem
import com.example.model.WordBlock
import com.example.model.WordBlockType
import com.example.util.CodeReaderEngine
import com.example.util.DocxEngine
import com.example.util.ExcelEngine
import com.example.util.ImageProcessor
import com.example.util.OcrEngine
import com.example.util.PdfEngine
import com.example.util.PdfSuiteEngine
import com.example.util.QrBarcodeEngine
import com.example.util.SlidesEngine
import com.example.util.StudyTtsController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.hypot

enum class Screen {
    HOME,
    CODING_HUB,
    CAMERA_SCAN,
    CROP,
    FILTER,
    DOCUMENT_DETAIL,
    PDF_READER,
    PDF_TOOLS,
    WORD_EDITOR,
    EXCEL_READER,
    SLIDES_READER,
    CODE_READER,
    PICTURES_TO_PDF,
    OCR,
    QR_SCAN,
    SIGNATURE,
    PDF_PREVIEW,
    HTML_PREVIEW,
    AI_ASSISTANT
}

enum class LibraryFilter(val label: String) {
    ALL("All"),
    PDF("PDFs"),
    WORD("Word"),
    EXCEL("Excel"),
    SLIDES("Slides"),
    CODE("Code"),
    SCAN("Scans"),
    STARRED("Starred")
}

enum class ViewMode {
    GRID,
    LIST
}

enum class SortOrder(val label: String) {
    NEWEST("Newest First"),
    NAME_AZ("Title A–Z"),
    MOST_PAGES("Most Pages"),
    STARRED("Starred First")
}

data class FilterPreviewItem(
    val filterType: FilterType,
    val previewBitmap: Bitmap
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = DocumentRepository(appContext)
    private val themePreferences = ThemePreferences(appContext)
    private val ttsController = StudyTtsController(appContext)

    val isTtsSpeaking: StateFlow<Boolean> = ttsController.isSpeaking
    val ttsSpeechRate: StateFlow<Float> = ttsController.speechRate

    // Theme Preferences
    val isDarkTheme: StateFlow<Boolean> = themePreferences.isDarkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isReaderNightMode: StateFlow<Boolean> = themePreferences.isReaderNightMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Navigation state
    private val _currentScreen = MutableStateFlow(Screen.HOME)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()
    private val screenStack = mutableListOf<Screen>()

    // Library Filter, Sort & Search
    val searchQuery = MutableStateFlow("")
    val selectedFilter = MutableStateFlow(LibraryFilter.ALL)
    val selectedSubjectFilter = MutableStateFlow<String?>(null)
    val sortOrder = MutableStateFlow(SortOrder.NEWEST)
    val viewMode = MutableStateFlow(ViewMode.GRID)

    val allDocuments: StateFlow<List<Document>> = repository.documents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableSubjectTags: StateFlow<List<String>> = repository.documents
        .map { docs ->
            docs.mapNotNull { it.subjectTag.trim().takeIf { tag -> tag.isNotBlank() } }
                .distinct()
                .sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredDocuments: StateFlow<List<Document>> = combine(
        repository.documents,
        searchQuery,
        selectedFilter,
        selectedSubjectFilter,
        sortOrder
    ) { docs, query, filter, subjectFilter, sort ->
        val filtered = docs.filter { doc ->
            val matchesFilter = when (filter) {
                LibraryFilter.ALL -> true
                LibraryFilter.PDF -> doc.kind == DocKind.PDF
                LibraryFilter.WORD -> doc.kind == DocKind.WORD
                LibraryFilter.EXCEL -> doc.kind == DocKind.EXCEL
                LibraryFilter.SLIDES -> doc.kind == DocKind.SLIDES
                LibraryFilter.CODE -> doc.kind == DocKind.CODE
                LibraryFilter.SCAN -> doc.kind == DocKind.SCAN
                LibraryFilter.STARRED -> doc.isStarred
            }
            val matchesSubject = subjectFilter.isNullOrBlank() ||
                    doc.subjectTag.equals(subjectFilter, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                    doc.title.contains(query, ignoreCase = true) ||
                    doc.subjectTag.contains(query, ignoreCase = true) ||
                    doc.kind.badge.contains(query, ignoreCase = true)
            matchesFilter && matchesSubject && matchesQuery
        }
        when (sort) {
            SortOrder.NEWEST -> filtered.sortedByDescending { it.updatedAt }
            SortOrder.NAME_AZ -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.MOST_PAGES -> filtered.sortedByDescending { it.pageCount }
            SortOrder.STARRED -> filtered.sortedWith(
                compareByDescending<Document> { it.isStarred }.thenByDescending { it.updatedAt }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Document & Page
    private val _activeDocument = MutableStateFlow<Document?>(null)
    val activeDocument: StateFlow<Document?> = _activeDocument.asStateFlow()

    private val _activePage = MutableStateFlow<ScanPage?>(null)
    val activePage: StateFlow<ScanPage?> = _activePage.asStateFlow()

    // Scanner Transient State
    val transientBitmap = MutableStateFlow<Bitmap?>(null)
    val transientCropQuad = MutableStateFlow(CropQuad())
    val transientFilter = MutableStateFlow(FilterType.MAGIC_COLOR)
    val transientRotation = MutableStateFlow(0)
    val transientTargetDocId = MutableStateFlow<String?>(null)
    val transientTargetPageId = MutableStateFlow<String?>(null)
    val transientEditingPictureDraftId = MutableStateFlow<String?>(null)

    private val _filterPreviews = MutableStateFlow<List<FilterPreviewItem>>(emptyList())
    val filterPreviews: StateFlow<List<FilterPreviewItem>> = _filterPreviews.asStateFlow()

    // Pictures to PDF State
    private val _pictureDrafts = MutableStateFlow<List<PictureDraftItem>>(emptyList())
    val pictureDrafts: StateFlow<List<PictureDraftItem>> = _pictureDrafts.asStateFlow()

    // Word Editor / Reader State
    private val _wordBlocks = MutableStateFlow<List<WordBlock>>(emptyList())
    val wordBlocks: StateFlow<List<WordBlock>> = _wordBlocks.asStateFlow()
    val wordStartInReadMode = MutableStateFlow(true)
    val wordStatusMessage = MutableStateFlow<String?>(null)

    // Excel Spreadsheet Reader/Editor State
    private val _excelSheets = MutableStateFlow<List<ExcelSheetData>>(emptyList())
    val excelSheets: StateFlow<List<ExcelSheetData>> = _excelSheets.asStateFlow()
    val excelStatusMessage = MutableStateFlow<String?>(null)

    // Slides Presentation Reader State
    private val _slidesList = MutableStateFlow<List<SlideItem>>(emptyList())
    val slidesList: StateFlow<List<SlideItem>> = _slidesList.asStateFlow()

    // Code & Log Reader State
    private val _codeLines = MutableStateFlow<List<String>>(emptyList())
    val codeLines: StateFlow<List<String>> = _codeLines.asStateFlow()
    private val _codeContent = MutableStateFlow("")
    val codeContent: StateFlow<String> = _codeContent.asStateFlow()

    // HTML / Markdown Offline Preview State
    private val _htmlPreviewFile = MutableStateFlow<File?>(null)
    val htmlPreviewFile: StateFlow<File?> = _htmlPreviewFile.asStateFlow()

    // PDF Annotations & Status State
    private val _activePdfAnnotations = MutableStateFlow(PdfDocumentAnnotations(""))
    val activePdfAnnotations: StateFlow<PdfDocumentAnnotations> = _activePdfAnnotations.asStateFlow()
    val pdfStatusMessage = MutableStateFlow<String?>(null)

    // QR / Barcode Scanner State
    private val _qrResults = MutableStateFlow<List<QrBarcodeEngine.ScannedCode>>(emptyList())
    val qrResults: StateFlow<List<QrBarcodeEngine.ScannedCode>> = _qrResults.asStateFlow()

    // OCR State
    private val _ocrResult = MutableStateFlow<OcrEngine.OcrResult?>(null)
    val ocrResult: StateFlow<OcrEngine.OcrResult?> = _ocrResult.asStateFlow()
    val isOcrLoading = MutableStateFlow(false)

    // Conversion / Progress State
    val conversionProgress = MutableStateFlow<String?>(null)
    val generatedPdfFile = MutableStateFlow<File?>(null)
    val isExportingPdf = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            if (!themePreferences.hasSeededStarterDocs()) {
                withContext(Dispatchers.IO) {
                    StarterDocumentSeeder.seedRealStarterDocuments(appContext, repository)
                }
                themePreferences.markStarterDocsSeeded()
            }
            if (!themePreferences.hasSeededOfficeStarters()) {
                withContext(Dispatchers.IO) {
                    StarterDocumentSeeder.seedOfficeStarterDocuments(appContext, repository)
                }
                themePreferences.markOfficeStartersSeeded()
            }
        }
    }

    // ==================== THEME ====================

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { themePreferences.setDarkTheme(enabled) }
    }

    fun setReaderNightMode(enabled: Boolean) {
        viewModelScope.launch { themePreferences.setReaderNightMode(enabled) }
    }

    // ==================== NAVIGATION ====================

    /** Bottom-bar tab switch between top-level screens (Home <-> Coding Hub). */
    fun selectTopTab(target: Screen) {
        if (_currentScreen.value == target) return
        screenStack.removeAll { it == Screen.HOME || it == Screen.CODING_HUB }
        screenStack.add(_currentScreen.value)
        _currentScreen.value = target
    }

    fun navigateTo(screen: Screen) {
        if (_currentScreen.value != screen) {
            screenStack.add(_currentScreen.value)
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        if (screenStack.isNotEmpty()) {
            _currentScreen.value = screenStack.removeAt(screenStack.size - 1)
            return true
        }
        return false
    }

    fun openDocument(doc: Document, startWordInReadMode: Boolean = true) {
        _activeDocument.value = doc
        when (doc.kind) {
            DocKind.PDF -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val ann = PdfAnnotationStore.load(appContext, doc.id)
                    withContext(Dispatchers.Main) {
                        _activePdfAnnotations.value = ann
                        navigateTo(Screen.PDF_READER)
                    }
                }
            }
            DocKind.WORD -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val file = doc.filePath?.let { File(it) }
                    val blocks = if (file != null && file.exists()) DocxEngine.readDocxFile(file) else emptyList()
                    withContext(Dispatchers.Main) {
                        wordStartInReadMode.value = startWordInReadMode
                        _wordBlocks.value = blocks
                        navigateTo(Screen.WORD_EDITOR)
                    }
                }
            }
            DocKind.EXCEL -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val file = doc.filePath?.let { File(it) }
                    val sheets = if (file != null && file.exists()) ExcelEngine.readSpreadsheetFile(file) else emptyList()
                    withContext(Dispatchers.Main) {
                        _excelSheets.value = sheets
                        navigateTo(Screen.EXCEL_READER)
                    }
                }
            }
            DocKind.SLIDES -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val file = doc.filePath?.let { File(it) }
                    val slides = if (file != null && file.exists()) SlidesEngine.readPptxFile(file) else emptyList()
                    withContext(Dispatchers.Main) {
                        _slidesList.value = slides
                        navigateTo(Screen.SLIDES_READER)
                    }
                }
            }
            DocKind.CODE -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val file = doc.filePath?.let { File(it) }
                    val content = if (file != null && file.exists()) {
                        runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
                    } else ""
                    withContext(Dispatchers.Main) {
                        _codeContent.value = content
                        _codeLines.value = content.lines()
                        navigateTo(Screen.CODE_READER)
                    }
                }
            }
            DocKind.SCAN -> navigateTo(Screen.DOCUMENT_DETAIL)
        }
    }

    fun selectPage(page: ScanPage) {
        _activePage.value = page
    }

    // ==================== LIBRARY ACTIONS ====================

    fun toggleStar(docId: String) {
        viewModelScope.launch {
            val updated = repository.toggleStar(docId)
            if (_activeDocument.value?.id == docId && updated != null) {
                _activeDocument.value = updated
            }
        }
    }

    fun renameDocument(docId: String, newTitle: String, subjectTag: String? = null) {
        viewModelScope.launch {
            val updated = repository.renameDocument(docId, newTitle, subjectTag)
            if (_activeDocument.value?.id == docId && updated != null) {
                _activeDocument.value = updated
            }
        }
    }

    fun deleteDocument(docId: String) {
        viewModelScope.launch {
            repository.deleteDocument(docId)
            if (_activeDocument.value?.id == docId) {
                _activeDocument.value = null
                _currentScreen.value = Screen.HOME
                screenStack.clear()
            }
        }
    }

    fun updateLastReadPage(docId: String, pageIndex: Int) {
        viewModelScope.launch {
            repository.updateLastReadPage(docId, pageIndex)
            _activeDocument.value?.let {
                if (it.id == docId) _activeDocument.value = it.copy(lastReadPage = pageIndex)
            }
        }
    }

    // ==================== IMPORT FROM PHONE ====================

    fun importFileFromUri(uri: Uri, subjectTag: String = "") {
        viewModelScope.launch {
            conversionProgress.value = "Opening document..."
            val name = repository.queryDisplayName(uri) ?: ""
            val mime = appContext.contentResolver.getType(uri) ?: ""
            when {
                mime.contains("pdf", ignoreCase = true) || name.endsWith(".pdf", ignoreCase = true) -> {
                    val doc = repository.importPdfFromUri(uri, subjectTag)
                    conversionProgress.value = null
                    if (doc != null) openDocument(doc)
                }
                mime.contains("spreadsheetml", ignoreCase = true) ||
                        mime.contains("opendocument.spreadsheet", ignoreCase = true) ||
                        mime.contains("ms-excel", ignoreCase = true) ||
                        mime.contains("csv", ignoreCase = true) ||
                        name.endsWith(".xlsx", ignoreCase = true) ||
                        name.endsWith(".ods", ignoreCase = true) ||
                        name.endsWith(".xls", ignoreCase = true) ||
                        name.endsWith(".csv", ignoreCase = true) ||
                        name.endsWith(".tsv", ignoreCase = true) -> {
                    val pair = repository.importExcelFromUri(uri, subjectTag)
                    conversionProgress.value = null
                    if (pair != null) {
                        _activeDocument.value = pair.first
                        _excelSheets.value = pair.second
                        navigateTo(Screen.EXCEL_READER)
                    }
                }
                mime.contains("presentationml", ignoreCase = true) ||
                        mime.contains("opendocument.presentation", ignoreCase = true) ||
                        mime.contains("powerpoint", ignoreCase = true) ||
                        name.endsWith(".pptx", ignoreCase = true) ||
                        name.endsWith(".odp", ignoreCase = true) -> {
                    val pair = repository.importSlidesFromUri(uri, subjectTag)
                    conversionProgress.value = null
                    if (pair != null) {
                        _activeDocument.value = pair.first
                        _slidesList.value = pair.second
                        navigateTo(Screen.SLIDES_READER)
                    }
                }
                CodeReaderEngine.isCodeOrLogExtension(name) -> {
                    val pair = repository.importCodeFromUri(uri, subjectTag)
                    conversionProgress.value = null
                    if (pair != null) {
                        _activeDocument.value = pair.first
                        _codeContent.value = pair.second.joinToString("\n")
                        _codeLines.value = pair.second
                        navigateTo(Screen.CODE_READER)
                    }
                }
                mime.contains("wordprocessingml", ignoreCase = true) ||
                        mime.contains("opendocument.text", ignoreCase = true) ||
                        mime.contains("epub", ignoreCase = true) ||
                        mime.contains("msword", ignoreCase = true) ||
                        mime.contains("rtf", ignoreCase = true) ||
                        mime.contains("html", ignoreCase = true) ||
                        mime.startsWith("text/") ||
                        name.endsWith(".docx", ignoreCase = true) ||
                        name.endsWith(".doc", ignoreCase = true) ||
                        name.endsWith(".odt", ignoreCase = true) ||
                        name.endsWith(".epub", ignoreCase = true) ||
                        name.endsWith(".html", ignoreCase = true) ||
                        name.endsWith(".htm", ignoreCase = true) ||
                        name.endsWith(".rtf", ignoreCase = true) ||
                        name.endsWith(".txt", ignoreCase = true) ||
                        name.endsWith(".md", ignoreCase = true) -> {
                    val pair = repository.importWordFromUri(uri, subjectTag)
                    conversionProgress.value = null
                    if (pair != null) {
                        wordStartInReadMode.value = true
                        _activeDocument.value = pair.first
                        _wordBlocks.value = pair.second
                        navigateTo(Screen.WORD_EDITOR)
                    }
                }
                mime.startsWith("image/") ||
                        name.endsWith(".jpg", ignoreCase = true) ||
                        name.endsWith(".jpeg", ignoreCase = true) ||
                        name.endsWith(".png", ignoreCase = true) ||
                        name.endsWith(".webp", ignoreCase = true) -> {
                    conversionProgress.value = null
                    onImagePickedForScan(uri)
                }
                else -> {
                    val pair = repository.importWordFromUri(uri, subjectTag)
                    conversionProgress.value = null
                    if (pair != null) {
                        wordStartInReadMode.value = true
                        _activeDocument.value = pair.first
                        _wordBlocks.value = pair.second
                        navigateTo(Screen.WORD_EDITOR)
                    }
                }
            }
        }
    }

    // ==================== PDF -> WORD CONVERSION ====================

    fun convertUriPdfToWord(uri: Uri) {
        viewModelScope.launch {
            conversionProgress.value = "Reading PDF..."
            val doc = repository.importPdfFromUri(uri)
            if (doc != null) {
                convertPdfDocumentToWord(doc)
            } else {
                conversionProgress.value = null
            }
        }
    }

    fun convertPdfDocumentToWord(pdfDoc: Document) {
        val pdfPath = pdfDoc.filePath ?: return
        val pdfFile = File(pdfPath)
        if (!pdfFile.exists()) return

        viewModelScope.launch {
            conversionProgress.value = "Converting PDF to Word..."
            val wordDir = File(appContext.filesDir, "word_docs")
            if (!wordDir.exists()) wordDir.mkdirs()
            val cleanBase = pdfDoc.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "Converted" }
            val outDocx = File(wordDir, "${cleanBase}_${UUID.randomUUID().toString().take(4)}.docx")

            val (_, blocks) = withContext(Dispatchers.IO) {
                PdfEngine.convertPdfToWord(pdfFile, outDocx) { cur, total ->
                    conversionProgress.value = "Extracting page $cur of $total..."
                }
            }

            val finalBlocks = if (blocks.isEmpty()) {
                listOf(
                    WordBlock(
                        type = WordBlockType.PARAGRAPH,
                        text = "No selectable/OCR text detected in ${pdfDoc.title}. You can start typing notes here."
                    )
                )
            } else blocks

            DocxEngine.writeDocxFile(finalBlocks, outDocx)

            val wordDoc = Document(
                title = "${pdfDoc.title} (Word)",
                kind = DocKind.WORD,
                subjectTag = pdfDoc.subjectTag,
                filePath = outDocx.absolutePath,
                totalPages = 1,
                category = DocumentCategory.NOTES
            )
            repository.saveDocument(wordDoc)

            conversionProgress.value = null
            wordStartInReadMode.value = false
            _activeDocument.value = wordDoc
            _wordBlocks.value = finalBlocks
            navigateTo(Screen.WORD_EDITOR)
        }
    }

    // ==================== WORD -> PDF & WORD EDITOR / CLEANER ====================

    fun createNewWordNote(title: String, subjectTag: String) {
        viewModelScope.launch {
            val (doc, blocks) = repository.createBlankWordDocument(title, subjectTag)
            wordStartInReadMode.value = false
            _activeDocument.value = doc
            _wordBlocks.value = blocks
            navigateTo(Screen.WORD_EDITOR)
        }
    }

    fun importWordUriAndOpenEditor(uri: Uri) {
        viewModelScope.launch {
            conversionProgress.value = "Opening Word document..."
            val pair = repository.importWordFromUri(uri)
            conversionProgress.value = null
            if (pair != null) {
                // Open in clean reading mode first; the toolbar Edit button
                // switches to the full editor in one click (MS-Office style).
                wordStartInReadMode.value = true
                _activeDocument.value = pair.first
                _wordBlocks.value = pair.second
                navigateTo(Screen.WORD_EDITOR)
            }
        }
    }

    fun importWordUriAndConvertToPdf(uri: Uri) {
        viewModelScope.launch {
            conversionProgress.value = "Converting Word to PDF..."
            val pair = repository.importWordFromUri(uri)
            if (pair != null) {
                val (wordDoc, blocks) = pair
                convertWordBlocksToPdf(wordDoc.title, blocks, wordDoc.subjectTag)
            } else {
                conversionProgress.value = null
            }
        }
    }

    fun updateWordBlocks(blocks: List<WordBlock>) {
        _wordBlocks.value = blocks
    }

    fun updateWordBlock(blockId: String, transform: (WordBlock) -> WordBlock) {
        _wordBlocks.value = _wordBlocks.value.map { if (it.id == blockId) transform(it) else it }
    }

    fun addWordBlock(afterBlockId: String? = null, type: WordBlockType = WordBlockType.PARAGRAPH) {
        val current = _wordBlocks.value.toMutableList()
        val newBlock = WordBlock(type = type, text = "")
        if (afterBlockId == null) {
            current.add(newBlock)
        } else {
            val idx = current.indexOfFirst { it.id == afterBlockId }
            if (idx >= 0) current.add(idx + 1, newBlock) else current.add(newBlock)
        }
        _wordBlocks.value = current
    }

    fun removeWordBlock(blockId: String) {
        if (_wordBlocks.value.size <= 1) {
            _wordBlocks.value = listOf(WordBlock(type = WordBlockType.PARAGRAPH, text = ""))
        } else {
            _wordBlocks.value = _wordBlocks.value.filter { it.id != blockId }
        }
    }

    /** v2.7: decodes a picked image, stores it under word_images/ and appends a PICTURE block. */
    fun insertPictureIntoWord(uri: Uri) {
        if (_activeDocument.value?.kind != DocKind.WORD) return
        viewModelScope.launch(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            appContext.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0) return@launch
            var sample = 1
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return@launch

            val dir = File(appContext.filesDir, "word_images")
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, "img_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()

            val block = WordBlock(type = WordBlockType.PICTURE, text = "", imagePath = outFile.absolutePath)
            withContext(Dispatchers.Main) {
                _wordBlocks.value = _wordBlocks.value + block
            }
        }
    }

    /** v2.7: appends an empty bordered table block with a header row. */
    fun insertTableIntoWord(rows: Int, cols: Int) {
        val rowCount = rows.coerceIn(1, 20)
        val colCount = cols.coerceIn(1, 8)
        val grid = List(rowCount) { row ->
            List(colCount) { col ->
                if (row == 0) "Header ${col + 1}" else ""
            }
        }
        _wordBlocks.value = _wordBlocks.value + WordBlock(type = WordBlockType.TABLE, text = "", tableRows = grid)
    }

    /** v2.7: edits a single cell of a TABLE block. */
    fun updateWordTableCell(blockId: String, row: Int, col: Int, value: String) {
        val current = _wordBlocks.value.toMutableList()
        val index = current.indexOfFirst { it.id == blockId }
        if (index < 0) return
        val block = current[index]
        val newRows = block.tableRows.mapIndexed { rIdx, rowList ->
            if (rIdx == row) {
                rowList.mapIndexed { cIdx, cell -> if (cIdx == col) value else cell }
            } else {
                rowList
            }
        }
        current[index] = block.copy(tableRows = newRows)
        _wordBlocks.value = current
    }

    fun moveWordBlock(blockId: String, delta: Int) {
        val list = _wordBlocks.value.toMutableList()
        val idx = list.indexOfFirst { it.id == blockId }
        val target = idx + delta
        if (idx in list.indices && target in list.indices) {
            val item = list.removeAt(idx)
            list.add(target, item)
            _wordBlocks.value = list
        }
    }

    fun cleanWordWhitespace() {
        _wordBlocks.value = DocxEngine.cleanWhitespace(_wordBlocks.value)
        wordStatusMessage.value = "Cleaned extra spaces & blank lines"
    }

    fun cleanWordMergeLineBreaks() {
        _wordBlocks.value = DocxEngine.mergeBrokenLineBreaks(_wordBlocks.value)
        wordStatusMessage.value = "Merged broken PDF line wraps"
    }

    fun cleanWordAutoHeadings() {
        _wordBlocks.value = DocxEngine.autoFormatHeadings(_wordBlocks.value)
        wordStatusMessage.value = "Auto-formatted section headings & bullets"
    }

    fun cleanWordStripNoise(customNoise: String = "") {
        val before = _wordBlocks.value.size
        _wordBlocks.value = DocxEngine.stripNoiseAndWatermarks(_wordBlocks.value, customNoise)
        val removed = (before - _wordBlocks.value.size).coerceAtLeast(0)
        wordStatusMessage.value = "Removed $removed noisy/repeated lines"
    }

    fun cleanWordFullAuto() {
        var b = DocxEngine.cleanWhitespace(_wordBlocks.value)
        b = DocxEngine.stripNoiseAndWatermarks(b)
        b = DocxEngine.mergeBrokenLineBreaks(b)
        b = DocxEngine.autoFormatHeadings(b)
        _wordBlocks.value = b
        wordStatusMessage.value = "Full document cleanup complete!"
    }

    fun findAndReplaceWord(findQuery: String, replaceWith: String) {
        val (updated, count) = DocxEngine.findAndReplace(_wordBlocks.value, findQuery, replaceWith)
        _wordBlocks.value = updated
        wordStatusMessage.value = "Replaced $count occurrences"
    }

    fun saveActiveWordDocument() {
        val doc = _activeDocument.value ?: return
        viewModelScope.launch {
            val saved = repository.saveWordDocument(doc, _wordBlocks.value)
            _activeDocument.value = saved
            wordStatusMessage.value = "Saved .docx"
        }
    }

    fun shareActiveWordDocx() {
        val doc = _activeDocument.value ?: return
        viewModelScope.launch {
            val saved = repository.saveWordDocument(doc, _wordBlocks.value)
            _activeDocument.value = saved
            val file = saved.filePath?.let { File(it) } ?: return@launch
            PdfEngine.shareFile(
                appContext,
                file,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                saved.title
            )
        }
    }

    fun convertWordBlocksToPdf(
        title: String,
        blocks: List<WordBlock>,
        subjectTag: String = "",
        pageSize: PdfEngine.PageSize = PdfEngine.PageSize.A4
    ) {
        viewModelScope.launch {
            conversionProgress.value = "Generating clean PDF..."
            val pdfDir = File(appContext.filesDir, "pdfs")
            if (!pdfDir.exists()) pdfDir.mkdirs()
            val cleanBase = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "Document" }
            val outPdf = File(pdfDir, "${cleanBase}_${UUID.randomUUID().toString().take(4)}.pdf")

            withContext(Dispatchers.IO) {
                PdfEngine.convertWordToPdf(title, blocks, outPdf, pageSize)
            }

            val pdfDoc = repository.registerGeneratedPdf(
                title = "$title.pdf",
                pdfFile = outPdf,
                subjectTag = subjectTag,
                category = DocumentCategory.NOTES
            )
            conversionProgress.value = null
            _activeDocument.value = pdfDoc
            navigateTo(Screen.PDF_READER)
        }
    }

    fun exportExcelSheetToPdf(doc: Document, sheet: ExcelSheetData) {
        viewModelScope.launch {
            conversionProgress.value = "Exporting ${sheet.name} to PDF..."
            val pdfDir = File(appContext.filesDir, "pdfs")
            if (!pdfDir.exists()) pdfDir.mkdirs()
            val cleanBase = "${doc.title}_${sheet.name}".replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val outPdf = File(pdfDir, "${cleanBase}_${UUID.randomUUID().toString().take(4)}.pdf")

            withContext(Dispatchers.IO) {
                ExcelEngine.exportSheetToPdf(doc.title, sheet, outPdf)
            }

            val pdfDoc = repository.registerGeneratedPdf(
                title = "${doc.title} (${sheet.name}).pdf",
                pdfFile = outPdf,
                subjectTag = doc.subjectTag,
                category = DocumentCategory.OTHER
            )
            conversionProgress.value = null
            _activeDocument.value = pdfDoc
            navigateTo(Screen.PDF_READER)
        }
    }

    fun convertSlidesToWordNotes(doc: Document, slides: List<SlideItem>) {
        viewModelScope.launch {
            conversionProgress.value = "Converting slides to Word notes..."
            val blocks = SlidesEngine.slidesToWordBlocks(doc.title, slides)
            val wordDir = File(appContext.filesDir, "word_docs")
            if (!wordDir.exists()) wordDir.mkdirs()
            val cleanBase = doc.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "SlidesNotes" }
            val outDocx = File(wordDir, "${cleanBase}_${UUID.randomUUID().toString().take(4)}.docx")
            withContext(Dispatchers.IO) {
                DocxEngine.writeDocxFile(blocks, outDocx)
            }
            val wordDoc = Document(
                title = "${doc.title} Notes",
                kind = DocKind.WORD,
                subjectTag = doc.subjectTag,
                filePath = outDocx.absolutePath,
                totalPages = 1,
                category = DocumentCategory.NOTES
            )
            repository.saveDocument(wordDoc)
            conversionProgress.value = null
            wordStartInReadMode.value = true
            _activeDocument.value = wordDoc
            _wordBlocks.value = blocks
            navigateTo(Screen.WORD_EDITOR)
        }
    }

    fun convertSlidesToPdf(doc: Document, slides: List<SlideItem>) {
        val blocks = SlidesEngine.slidesToWordBlocks(doc.title, slides)
        convertWordBlocksToPdf(doc.title, blocks, doc.subjectTag)
    }

    // ==================== SLIDE EDITING ====================

    fun updateSlide(slideNumber: Int, updated: SlideItem) {
        _slidesList.value = _slidesList.value.map {
            if (it.number == slideNumber) updated.copy(number = slideNumber) else it
        }
    }

    fun moveSlide(slideNumber: Int, delta: Int) {
        val current = _slidesList.value.toMutableList()
        val index = current.indexOfFirst { it.number == slideNumber }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, current.lastIndex)
        if (target == index) return
        val slide = current.removeAt(index)
        current.add(target, slide)
        _slidesList.value = current.mapIndexed { idx, s -> s.copy(number = idx + 1) }
    }

    fun duplicateSlide(slideNumber: Int) {
        val current = _slidesList.value.toMutableList()
        val index = current.indexOfFirst { it.number == slideNumber }
        if (index < 0) return
        val copy = current[index].copy(
            number = current[index].number + 1,
            title = "${current[index].title} (copy)"
        )
        current.add(index + 1, copy)
        _slidesList.value = current.mapIndexed { idx, s -> s.copy(number = idx + 1) }
    }

    fun deleteSlide(slideNumber: Int) {
        if (_slidesList.value.size <= 1) return
        _slidesList.value = _slidesList.value
            .filterNot { it.number == slideNumber }
            .mapIndexed { idx, s -> s.copy(number = idx + 1) }
    }

    fun saveActiveSlides() {
        val doc = _activeDocument.value ?: return
        viewModelScope.launch {
            val saved = repository.saveSlidesDocument(doc, _slidesList.value)
            _activeDocument.value = saved
        }
    }

    // ==================== PICTURES TO PDF (WITH REARRANGING) ====================

    fun startPicturesToPdf(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            conversionProgress.value = "Loading ${uris.size} pictures..."
            val items = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val bmp = ImageProcessor.loadOrientedBitmapFromUri(appContext, uri, maxDim = 1800)
                        ?: return@mapNotNull null
                    val path = ImageProcessor.saveBitmapToFile(appContext, bmp, "pic_draft")
                    bmp.recycle()
                    PictureDraftItem(originalPath = path, processedPath = path)
                }
            }
            _pictureDrafts.value = items
            conversionProgress.value = null
            if (items.isNotEmpty()) {
                navigateTo(Screen.PICTURES_TO_PDF)
            }
        }
    }

    fun addPicturesToDraft(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            conversionProgress.value = "Adding pictures..."
            val added = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val bmp = ImageProcessor.loadOrientedBitmapFromUri(appContext, uri, maxDim = 1800)
                        ?: return@mapNotNull null
                    val path = ImageProcessor.saveBitmapToFile(appContext, bmp, "pic_draft")
                    bmp.recycle()
                    PictureDraftItem(originalPath = path, processedPath = path)
                }
            }
            _pictureDrafts.value = _pictureDrafts.value + added
            conversionProgress.value = null
        }
    }

    fun addCapturedBitmapToDraft(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = ImageProcessor.saveBitmapToFile(appContext, bitmap, "pic_draft")
            val item = PictureDraftItem(originalPath = path, processedPath = path)
            withContext(Dispatchers.Main) {
                _pictureDrafts.value = _pictureDrafts.value + item
            }
        }
    }

    fun movePictureDraft(fromIndex: Int, toIndex: Int) {
        val list = _pictureDrafts.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _pictureDrafts.value = list
    }

    fun removePictureDraft(itemId: String) {
        _pictureDrafts.value = _pictureDrafts.value.filter { it.id != itemId }
    }

    fun rotatePictureDraft(itemId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = _pictureDrafts.value.map { item ->
                if (item.id == itemId) {
                    val bmp = ImageProcessor.loadBitmap(item.processedPath) ?: return@map item
                    val rotated = ImageProcessor.rotateBitmap(bmp, 90)
                    val newPath = ImageProcessor.saveBitmapToFile(appContext, rotated, "pic_rot")
                    bmp.recycle()
                    rotated.recycle()
                    item.copy(
                        processedPath = newPath,
                        rotationDegrees = (item.rotationDegrees + 90) % 360
                    )
                } else item
            }
            withContext(Dispatchers.Main) {
                _pictureDrafts.value = updated
            }
        }
    }

    fun editPictureDraftWithScanner(itemId: String) {
        val item = _pictureDrafts.value.find { it.id == itemId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val origBmp = ImageProcessor.loadBitmap(item.originalPath) ?: return@launch
            withContext(Dispatchers.Main) {
                transientBitmap.value = origBmp
                transientCropQuad.value = item.cropQuad
                transientFilter.value = item.filterType
                transientRotation.value = item.rotationDegrees
                transientTargetDocId.value = null
                transientTargetPageId.value = null
                transientEditingPictureDraftId.value = itemId
                navigateTo(Screen.CROP)
            }
        }
    }

    fun buildPdfFromPictureDrafts(
        title: String,
        subjectTag: String,
        pageSize: PdfEngine.PageSize,
        withMargins: Boolean
    ) {
        val items = _pictureDrafts.value
        if (items.isEmpty()) return
        viewModelScope.launch {
            conversionProgress.value = "Creating PDF from ${items.size} pictures..."
            val pdfDir = File(appContext.filesDir, "pdfs")
            if (!pdfDir.exists()) pdfDir.mkdirs()
            val cleanTitle = title.ifBlank { "Notes_PDF_${System.currentTimeMillis() % 10000}" }
            val outPdf = File(pdfDir, "${cleanTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_")}_${UUID.randomUUID().toString().take(4)}.pdf")

            withContext(Dispatchers.IO) {
                PdfEngine.buildPdfFromPictures(items, outPdf, pageSize, withMargins)
            }

            val pdfDoc = repository.registerGeneratedPdf(
                title = cleanTitle,
                pdfFile = outPdf,
                subjectTag = subjectTag,
                category = DocumentCategory.NOTES
            )
            _pictureDrafts.value = emptyList()
            conversionProgress.value = null
            screenStack.clear()
            screenStack.add(Screen.HOME)
            _activeDocument.value = pdfDoc
            _currentScreen.value = Screen.PDF_READER
        }
    }

    // ==================== CAMERA & CROP/FILTER SCAN PIPELINE ====================

    fun startNewScan(bitmap: Bitmap, targetDocId: String? = null) {
        transientBitmap.value = bitmap
        transientCropQuad.value = ImageProcessor.detectDocumentQuad(bitmap)
        transientFilter.value = FilterType.MAGIC_COLOR
        transientRotation.value = 0
        transientTargetDocId.value = targetDocId
        transientTargetPageId.value = null
        transientEditingPictureDraftId.value = null
        navigateTo(Screen.CROP)
    }

    fun reDetectAutoCropQuad() {
        val bmp = transientBitmap.value ?: return
        transientCropQuad.value = ImageProcessor.detectDocumentQuad(bmp)
    }

    fun combineIdCardAndStartScan(front: Bitmap, back: Bitmap, targetDocId: String? = null) {
        viewModelScope.launch {
            conversionProgress.value = "Combining Front & Back ID sides..."
            val combined = withContext(Dispatchers.Default) {
                val frontSharp = ImageProcessor.applyFilter(front, FilterType.MAGIC_COLOR)
                val backSharp = ImageProcessor.applyFilter(back, FilterType.MAGIC_COLOR)
                ImageProcessor.combineIdCardSides(frontSharp, backSharp).also {
                    frontSharp.recycle()
                    backSharp.recycle()
                }
            }
            conversionProgress.value = null
            transientBitmap.value = combined
            transientCropQuad.value = CropQuad(
                topLeft = androidx.compose.ui.geometry.Offset(0.01f, 0.01f),
                topRight = androidx.compose.ui.geometry.Offset(0.99f, 0.01f),
                bottomRight = androidx.compose.ui.geometry.Offset(0.99f, 0.99f),
                bottomLeft = androidx.compose.ui.geometry.Offset(0.01f, 0.99f)
            )
            transientFilter.value = FilterType.ORIGINAL
            transientRotation.value = 0
            transientTargetDocId.value = targetDocId
            transientTargetPageId.value = null
            transientEditingPictureDraftId.value = null
            navigateTo(Screen.FILTER)
        }
    }

    fun splitBookAndSaveDocument(spreadBitmap: Bitmap) {
        viewModelScope.launch {
            conversionProgress.value = "Splitting book pages..."
            val doc = withContext(Dispatchers.IO) {
                val (left, right) = ImageProcessor.splitBookSpread(spreadBitmap)
                val title = "Book Scan ${java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.US).format(java.util.Date())}"
                val fullQuad = CropQuad(
                    topLeft = androidx.compose.ui.geometry.Offset(0.02f, 0.02f),
                    topRight = androidx.compose.ui.geometry.Offset(0.98f, 0.02f),
                    bottomRight = androidx.compose.ui.geometry.Offset(0.98f, 0.98f),
                    bottomLeft = androidx.compose.ui.geometry.Offset(0.02f, 0.98f)
                )
                val first = repository.createScanDocument(
                    title = title,
                    subjectTag = "",
                    bitmap = left,
                    category = DocumentCategory.SCANS,
                    filter = FilterType.MAGIC_COLOR,
                    cropQuad = fullQuad
                )
                val updated = repository.addPageToScanDocument(
                    documentId = first.id,
                    bitmap = right,
                    filter = FilterType.MAGIC_COLOR,
                    cropQuad = fullQuad
                ) ?: first
                left.recycle()
                right.recycle()
                updated
            }
            conversionProgress.value = null
            _activeDocument.value = doc
            _currentScreen.value = Screen.DOCUMENT_DETAIL
            screenStack.clear()
            screenStack.add(Screen.HOME)
        }
    }

    fun onImagePickedForScan(uri: Uri, targetDocId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = ImageProcessor.loadOrientedBitmapFromUri(appContext, uri, maxDim = 2000)
            if (bmp != null) {
                withContext(Dispatchers.Main) {
                    startNewScan(bmp, targetDocId)
                }
            }
        }
    }

    fun startReEditPage(doc: Document, page: ScanPage) {
        viewModelScope.launch(Dispatchers.IO) {
            val orig = ImageProcessor.loadBitmap(page.originalImagePath) ?: return@launch
            withContext(Dispatchers.Main) {
                transientBitmap.value = orig
                transientCropQuad.value = page.cropQuad
                transientFilter.value = page.filterType
                transientRotation.value = page.rotationDegrees
                transientTargetDocId.value = doc.id
                transientTargetPageId.value = page.id
                transientEditingPictureDraftId.value = null
                _activeDocument.value = doc
                _activePage.value = page
                generateFilterPreviews(orig, page.cropQuad)
                navigateTo(Screen.FILTER)
            }
        }
    }

    fun updateCropQuad(quad: CropQuad) {
        transientCropQuad.value = quad
    }

    fun rotateTransientBitmap() {
        val bmp = transientBitmap.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val rotated = ImageProcessor.rotateBitmap(bmp, 90)
            withContext(Dispatchers.Main) {
                transientBitmap.value = rotated
            }
        }
    }

    fun confirmCrop() {
        val bmp = transientBitmap.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            generateFilterPreviews(bmp, transientCropQuad.value)
            withContext(Dispatchers.Main) {
                navigateTo(Screen.FILTER)
            }
        }
    }

    private fun generateFilterPreviews(source: Bitmap, quad: CropQuad) {
        val cropped = ImageProcessor.cropPerspective(source, quad)
        val maxDim = 160
        val scale = minOf(1f, maxDim.toFloat() / maxOf(cropped.width, cropped.height))
        val thumb = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                cropped,
                (cropped.width * scale).toInt().coerceAtLeast(1),
                (cropped.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else cropped

        _filterPreviews.value = FilterType.values().map { filter ->
            FilterPreviewItem(filter, ImageProcessor.applyFilter(thumb, filter))
        }
    }

    fun selectFilter(filter: FilterType) {
        transientFilter.value = filter
    }

    fun saveScanResult(
        customTitle: String? = null,
        subjectTag: String = "",
        category: DocumentCategory = DocumentCategory.SCANS
    ) {
        val bmp = transientBitmap.value ?: return
        val quad = transientCropQuad.value
        val filter = transientFilter.value
        val targetDocId = transientTargetDocId.value
        val targetPageId = transientTargetPageId.value
        val draftPicId = transientEditingPictureDraftId.value

        viewModelScope.launch {
            if (draftPicId != null) {
                // Returning edited picture to PicturesToPdf draft list
                val savedPath = withContext(Dispatchers.IO) {
                    val cropped = ImageProcessor.cropPerspective(bmp, quad)
                    val filtered = ImageProcessor.applyFilter(cropped, filter)
                    ImageProcessor.saveBitmapToFile(appContext, filtered, "pic_edited")
                }
                _pictureDrafts.value = _pictureDrafts.value.map {
                    if (it.id == draftPicId) it.copy(
                        processedPath = savedPath,
                        cropQuad = quad,
                        filterType = filter
                    ) else it
                }
                transientEditingPictureDraftId.value = null
                _currentScreen.value = Screen.PICTURES_TO_PDF
                // Trim stack back to PICTURES_TO_PDF
                while (screenStack.isNotEmpty() && screenStack.last() != Screen.PICTURES_TO_PDF) {
                    screenStack.removeAt(screenStack.size - 1)
                }
                if (screenStack.lastOrNull() == Screen.PICTURES_TO_PDF) {
                    screenStack.removeAt(screenStack.size - 1)
                }
            } else if (targetPageId != null && targetDocId != null) {
                val updated = repository.updateScanPage(
                    documentId = targetDocId,
                    pageId = targetPageId,
                    newFilter = filter,
                    newCropQuad = quad
                )
                _activeDocument.value = updated
                _activePage.value = updated?.pages?.find { it.id == targetPageId }
                _currentScreen.value = Screen.DOCUMENT_DETAIL
                screenStack.clear()
                screenStack.add(Screen.HOME)
            } else if (targetDocId != null) {
                val updated = repository.addPageToScanDocument(targetDocId, bmp, filter, quad)
                _activeDocument.value = updated
                _currentScreen.value = Screen.DOCUMENT_DETAIL
                screenStack.clear()
                screenStack.add(Screen.HOME)
            } else {
                val title = customTitle?.ifBlank { null } ?: "Scan ${java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.US).format(java.util.Date())}"
                val newDoc = repository.createScanDocument(title, subjectTag, bmp, category, filter, quad)
                _activeDocument.value = newDoc
                _currentScreen.value = Screen.DOCUMENT_DETAIL
                screenStack.clear()
                screenStack.add(Screen.HOME)
            }
        }
    }

    fun deletePage(docId: String, pageId: String) {
        viewModelScope.launch {
            val updated = repository.deleteScanPage(docId, pageId)
            if (updated == null) {
                _activeDocument.value = null
                _currentScreen.value = Screen.HOME
                screenStack.clear()
            } else {
                _activeDocument.value = updated
            }
        }
    }

    // ==================== REAL OCR ====================

    fun runOcrForPage(page: ScanPage) {
        _activePage.value = page
        isOcrLoading.value = true
        navigateTo(Screen.OCR)
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = ImageProcessor.loadBitmap(page.processedImagePath)
            val result = if (bmp != null) OcrEngine.extractText(bmp) else OcrEngine.OcrResult("", 0, 0)
            withContext(Dispatchers.Main) {
                _ocrResult.value = result
                isOcrLoading.value = false
            }
        }
    }

    fun runOcrOnBitmap(bitmap: Bitmap) {
        isOcrLoading.value = true
        navigateTo(Screen.OCR)
        viewModelScope.launch(Dispatchers.IO) {
            val result = OcrEngine.extractText(bitmap)
            withContext(Dispatchers.Main) {
                _ocrResult.value = result
                isOcrLoading.value = false
            }
        }
    }

    fun openOcrResultAsWordDoc(title: String, ocrText: String) {
        viewModelScope.launch {
            val lines = ocrText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val blocks = lines.map { WordBlock(type = WordBlockType.PARAGRAPH, text = it) }
                .let { DocxEngine.autoFormatHeadings(DocxEngine.mergeBrokenLineBreaks(it)) }
            val (doc, _) = repository.createBlankWordDocument(title, _activeDocument.value?.subjectTag ?: "")
            val saved = repository.saveWordDocument(doc, blocks)
            _activeDocument.value = saved
            _wordBlocks.value = blocks
            navigateTo(Screen.WORD_EDITOR)
        }
    }

    // ==================== SIGNATURE, WATERMARK & PDF EXPORT ====================

    fun applySignatureToPage(signatureBitmap: Bitmap) {
        val doc = _activeDocument.value ?: return
        val page = _activePage.value ?: return
        viewModelScope.launch {
            val updated = repository.updateScanPage(doc.id, page.id, signatureBitmap = signatureBitmap)
            _activeDocument.value = updated
            _activePage.value = updated?.pages?.find { it.id == page.id }
            navigateBack()
        }
    }

    fun applyWatermarkToPage(watermarkText: String) {
        val doc = _activeDocument.value ?: return
        val page = _activePage.value ?: return
        viewModelScope.launch {
            val updated = repository.updateScanPage(doc.id, page.id, watermarkText = watermarkText)
            _activeDocument.value = updated
            _activePage.value = updated?.pages?.find { it.id == page.id }
        }
    }

    fun preparePdfExport(doc: Document, pageSize: PdfEngine.PageSize = PdfEngine.PageSize.A4, watermark: Boolean = false) {
        _activeDocument.value = doc
        isExportingPdf.value = true
        if (_currentScreen.value != Screen.PDF_PREVIEW) navigateTo(Screen.PDF_PREVIEW)

        viewModelScope.launch(Dispatchers.IO) {
            val file = if (doc.kind == DocKind.PDF && doc.filePath != null) {
                File(doc.filePath)
            } else {
                PdfEngine.generatePdfFromScanDocument(appContext, doc, pageSize, watermark)
            }
            withContext(Dispatchers.Main) {
                generatedPdfFile.value = file
                isExportingPdf.value = false
            }
        }
    }

    fun shareCurrentPdf() {
        val file = generatedPdfFile.value ?: return
        val doc = _activeDocument.value ?: return
        PdfEngine.shareFile(appContext, file, "application/pdf", doc.title)
    }

    fun shareDocumentFile(doc: Document) {
        val path = doc.filePath
        if (path != null) {
            val file = File(path)
            val mime = when (doc.kind) {
                DocKind.PDF -> "application/pdf"
                DocKind.WORD -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                DocKind.EXCEL -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                DocKind.SLIDES -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                DocKind.CODE -> "text/plain"
                DocKind.SCAN -> "application/pdf"
            }
            PdfEngine.shareFile(appContext, file, mime, doc.title)
        } else {
            preparePdfExport(doc)
        }
    }

    fun sharePage(page: ScanPage) {
        PdfEngine.shareFile(appContext, File(page.processedImagePath), "image/jpeg", "Page ${page.pageNumber}")
    }

    // ==================== PDF ANNOTATIONS, HIGHLIGHTS, COMMENTS & NOTES ====================

    private fun updatePdfAnnotations(transform: (PdfDocumentAnnotations) -> PdfDocumentAnnotations) {
        val current = _activePdfAnnotations.value
        if (current.docId.isBlank()) return
        val updated = transform(current)
        _activePdfAnnotations.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            PdfAnnotationStore.save(appContext, updated)
        }
    }

    fun togglePdfBookmark(pageIndex: Int) {
        val wasBookmarked = pageIndex in _activePdfAnnotations.value.bookmarkedPages
        updatePdfAnnotations { ann ->
            val next = ann.bookmarkedPages.toMutableSet()
            if (pageIndex in next) next.remove(pageIndex) else next.add(pageIndex)
            ann.copy(bookmarkedPages = next)
        }
        pdfStatusMessage.value = if (wasBookmarked) {
            "Removed bookmark from Page ${pageIndex + 1}"
        } else {
            "Bookmarked Page ${pageIndex + 1}"
        }
    }

    fun addPdfStroke(stroke: PdfStroke) {
        updatePdfAnnotations { ann ->
            ann.copy(strokes = ann.strokes + stroke)
        }
    }

    fun undoLastPdfStroke(pageIndex: Int) {
        updatePdfAnnotations { ann ->
            val lastIdx = ann.strokes.indexOfLast { it.pageIndex == pageIndex }
            if (lastIdx >= 0) {
                val mutable = ann.strokes.toMutableList()
                mutable.removeAt(lastIdx)
                ann.copy(strokes = mutable)
            } else ann
        }
    }

    fun erasePdfAnnotationAt(pageIndex: Int, xRatio: Float, yRatio: Float) {
        updatePdfAnnotations { ann ->
            val hitComment = ann.comments.firstOrNull {
                it.pageIndex == pageIndex && hypot(it.xRatio - xRatio, it.yRatio - yRatio) < 0.06f
            }
            if (hitComment != null) {
                return@updatePdfAnnotations ann.copy(comments = ann.comments.filter { it.id != hitComment.id })
            }
            val remainingStrokes = ann.strokes.filterNot { stroke ->
                stroke.pageIndex == pageIndex && stroke.points.any { pt ->
                    hypot(pt.x - xRatio, pt.y - yRatio) < 0.05f
                }
            }
            ann.copy(strokes = remainingStrokes)
        }
    }

    fun clearPdfPageAnnotations(pageIndex: Int) {
        updatePdfAnnotations { ann ->
            ann.copy(
                strokes = ann.strokes.filterNot { it.pageIndex == pageIndex },
                comments = ann.comments.filterNot { it.pageIndex == pageIndex }
            )
        }
        pdfStatusMessage.value = "Cleared annotations on Page ${pageIndex + 1}"
    }

    fun addOrUpdatePdfComment(pin: PdfCommentPin) {
        updatePdfAnnotations { ann ->
            val exists = ann.comments.any { it.id == pin.id }
            val next = if (exists) {
                ann.comments.map { if (it.id == pin.id) pin else it }
            } else {
                ann.comments + pin
            }
            ann.copy(comments = next)
        }
        pdfStatusMessage.value = "Saved sticky note on Page ${pin.pageIndex + 1}"
    }

    fun deletePdfComment(commentId: String) {
        updatePdfAnnotations { ann ->
            ann.copy(comments = ann.comments.filterNot { it.id == commentId })
        }
        pdfStatusMessage.value = "Deleted sticky note"
    }

    fun savePdfPageNote(pageIndex: Int, note: String) {
        updatePdfAnnotations { ann ->
            val next = ann.pageNotes.toMutableMap()
            if (note.isBlank()) next.remove(pageIndex) else next[pageIndex] = note.trim()
            ann.copy(pageNotes = next)
        }
        pdfStatusMessage.value = if (note.isBlank()) "Cleared note on Page ${pageIndex + 1}" else "Saved Page ${pageIndex + 1} note"
    }

    fun exportAnnotatedPdfDocument(doc: Document, includeNotesSummary: Boolean = true) {
        val pdfPath = doc.filePath ?: return
        val sourceFile = File(pdfPath)
        if (!sourceFile.exists()) return

        viewModelScope.launch {
            conversionProgress.value = "Flattening highlights & notes into PDF..."
            val pdfDir = File(appContext.filesDir, "pdfs")
            if (!pdfDir.exists()) pdfDir.mkdirs()
            val cleanBase = doc.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "Annotated" }
            val outFile = File(pdfDir, "${cleanBase}_annotated_${UUID.randomUUID().toString().take(4)}.pdf")
            val annotationsSnapshot = _activePdfAnnotations.value

            withContext(Dispatchers.IO) {
                PdfSuiteEngine.exportAnnotatedPdf(
                    sourcePdf = sourceFile,
                    docTitle = doc.title,
                    annotations = annotationsSnapshot,
                    outputFile = outFile,
                    includeNotesSummaryPage = includeNotesSummary
                ) { cur, total ->
                    conversionProgress.value = "Rendering annotated page $cur / $total..."
                }
            }

            val exportedDoc = repository.registerGeneratedPdf(
                title = "${doc.title} (Annotated)",
                pdfFile = outFile,
                subjectTag = doc.subjectTag,
                category = doc.category
            )
            conversionProgress.value = null
            _activeDocument.value = exportedDoc
            _activePdfAnnotations.value = PdfAnnotationStore.load(appContext, exportedDoc.id)
            pdfStatusMessage.value = "Saved annotated PDF copy!"
        }
    }

    // ==================== PDF POWER SUITE (MERGE, SPLIT/REORDER, COMPRESS, WATERMARK) ====================

    fun mergePdfDocuments(docsToMerge: List<Document>, outputTitle: String) {
        val files = docsToMerge.mapNotNull { d -> d.filePath?.let { File(it) }?.takeIf { it.exists() } }
        if (files.size < 2) return
        viewModelScope.launch {
            conversionProgress.value = "Merging ${files.size} PDFs..."
            val pdfDir = File(appContext.filesDir, "pdfs")
            if (!pdfDir.exists()) pdfDir.mkdirs()
            val cleanTitle = outputTitle.ifBlank { "Merged_${docsToMerge.first().title}" }
            val outFile = File(pdfDir, "${cleanTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_")}_${UUID.randomUUID().toString().take(4)}.pdf")

            withContext(Dispatchers.IO) {
                PdfSuiteEngine.mergePdfs(files, outFile) { cur, total ->
                    conversionProgress.value = "Merging page $cur of $total..."
                }
            }

            val mergedDoc = repository.registerGeneratedPdf(
                title = cleanTitle,
                pdfFile = outFile,
                subjectTag = docsToMerge.firstOrNull { it.subjectTag.isNotBlank() }?.subjectTag ?: "",
                category = DocumentCategory.HANDOUTS
            )
            conversionProgress.value = null
            _activeDocument.value = mergedDoc
            _activePdfAnnotations.value = PdfAnnotationStore.load(appContext, mergedDoc.id)
            navigateTo(Screen.PDF_READER)
        }
    }

    fun splitOrTransformPdfDocument(
        doc: Document,
        pageSpecs: List<PdfSuiteEngine.PageTransformSpec>,
        outputTitle: String
    ) {
        val source = doc.filePath?.let { File(it) } ?: return
        if (!source.exists() || pageSpecs.isEmpty()) return
        viewModelScope.launch {
            conversionProgress.value = "Building ${pageSpecs.size}-page PDF..."
            val pdfDir = File(appContext.filesDir, "pdfs")
            if (!pdfDir.exists()) pdfDir.mkdirs()
            val cleanTitle = outputTitle.ifBlank { "${doc.title} (Edited)" }
            val outFile = File(pdfDir, "${cleanTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_")}_${UUID.randomUUID().toString().take(4)}.pdf")

            withContext(Dispatchers.IO) {
                PdfSuiteEngine.splitOrReorderPdf(source, pageSpecs, outFile) { cur, total ->
                    conversionProgress.value = "Processing page $cur of $total..."
                }
            }

            val newDoc = repository.registerGeneratedPdf(
                title = cleanTitle,
                pdfFile = outFile,
                subjectTag = doc.subjectTag,
                category = doc.category
            )
            conversionProgress.value = null
            _activeDocument.value = newDoc
            _activePdfAnnotations.value = PdfAnnotationStore.load(appContext, newDoc.id)
            navigateTo(Screen.PDF_READER)
        }
    }

    fun compressPdfDocument(doc: Document, level: PdfSuiteEngine.CompressionLevel) {
        val source = doc.filePath?.let { File(it) } ?: return
        if (!source.exists()) return
        viewModelScope.launch {
            conversionProgress.value = "Compressing PDF (${level.label})..."
            val pdfDir = File(appContext.filesDir, "pdfs")
            if (!pdfDir.exists()) pdfDir.mkdirs()
            val cleanTitle = "${doc.title} (Compressed)"
            val outFile = File(pdfDir, "${doc.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")}_cmp_${UUID.randomUUID().toString().take(4)}.pdf")

            val result = withContext(Dispatchers.IO) {
                PdfSuiteEngine.compressPdf(source, level, outFile) { cur, total ->
                    conversionProgress.value = "Compressing page $cur of $total..."
                }
            }

            val newDoc = repository.registerGeneratedPdf(
                title = cleanTitle,
                pdfFile = outFile,
                subjectTag = doc.subjectTag,
                category = doc.category
            )
            conversionProgress.value = null
            val beforeKb = (result.originalBytes / 1024).coerceAtLeast(1)
            val afterKb = (result.compressedBytes / 1024).coerceAtLeast(1)
            pdfStatusMessage.value = "Compressed: ${beforeKb} KB → ${afterKb} KB"
            _activeDocument.value = newDoc
            _activePdfAnnotations.value = PdfAnnotationStore.load(appContext, newDoc.id)
            navigateTo(Screen.PDF_READER)
        }
    }

    fun watermarkPdfDocument(doc: Document, watermarkText: String, diagonal: Boolean) {
        val source = doc.filePath?.let { File(it) } ?: return
        if (!source.exists()) return
        viewModelScope.launch {
            conversionProgress.value = "Stamping watermark..."
            val pdfDir = File(appContext.filesDir, "pdfs")
            if (!pdfDir.exists()) pdfDir.mkdirs()
            val cleanTitle = "${doc.title} (Watermarked)"
            val outFile = File(pdfDir, "${doc.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")}_wm_${UUID.randomUUID().toString().take(4)}.pdf")

            withContext(Dispatchers.IO) {
                PdfSuiteEngine.watermarkPdf(source, watermarkText, diagonal, 78, outFile) { cur, total ->
                    conversionProgress.value = "Watermarking page $cur of $total..."
                }
            }

            val newDoc = repository.registerGeneratedPdf(
                title = cleanTitle,
                pdfFile = outFile,
                subjectTag = doc.subjectTag,
                category = doc.category
            )
            conversionProgress.value = null
            pdfStatusMessage.value = "Watermark applied"
            _activeDocument.value = newDoc
            _activePdfAnnotations.value = PdfAnnotationStore.load(appContext, newDoc.id)
            navigateTo(Screen.PDF_READER)
        }
    }

    // ==================== EXCEL CELL EDITING ====================

    fun updateExcelCell(sheetIndex: Int, rowIndex: Int, colIndex: Int, newValue: String) {
        val current = _excelSheets.value.toMutableList()
        val sheet = current.getOrNull(sheetIndex) ?: return
        val newRows = sheet.rows.mapIndexed { rIdx, row ->
            if (rIdx == rowIndex) {
                val padded = if (colIndex >= row.size) row + List(colIndex - row.size + 1) { "" } else row
                padded.mapIndexed { cIdx, v -> if (cIdx == colIndex) newValue else v }
            } else row
        }
        // Register / clear the formula for this cell reference
        val ref = ExcelEngine.indexToColName(colIndex) + (rowIndex + 1)
        val newFormulas = if (newValue.trimStart().startsWith("=")) {
            sheet.formulas + (ref to newValue)
        } else if (sheet.formulas.containsKey(ref)) {
            sheet.formulas - ref
        } else {
            sheet.formulas
        }
        val updated = sheet.copy(rows = newRows, formulas = newFormulas)
        // Recalculate all formula cells so dependents refresh immediately
        current[sheetIndex] = ExcelEngine.evaluateSheetFormulas(updated)
        _excelSheets.value = current
    }

    // ---- Excel cell styling (bold / italic / text color / fill) ----

    fun toggleExcelBold(sheetIndex: Int, rowIndex: Int, colIndex: Int) {
        modifyExcelStyle(sheetIndex, rowIndex, colIndex) { it.copy(bold = !it.bold) }
    }

    fun toggleExcelItalic(sheetIndex: Int, rowIndex: Int, colIndex: Int) {
        modifyExcelStyle(sheetIndex, rowIndex, colIndex) { it.copy(italic = !it.italic) }
    }

    fun setExcelTextColor(sheetIndex: Int, rowIndex: Int, colIndex: Int, hex: String?) {
        modifyExcelStyle(sheetIndex, rowIndex, colIndex) { it.copy(colorHex = hex) }
    }

    fun setExcelFill(sheetIndex: Int, rowIndex: Int, colIndex: Int, hex: String?) {
        modifyExcelStyle(sheetIndex, rowIndex, colIndex) { it.copy(fillHex = hex) }
    }

    private fun modifyExcelStyle(
        sheetIndex: Int,
        rowIndex: Int,
        colIndex: Int,
        transform: (ExcelCellStyle) -> ExcelCellStyle
    ) {
        val current = _excelSheets.value.toMutableList()
        val sheet = current.getOrNull(sheetIndex) ?: return
        val ref = ExcelEngine.indexToColName(colIndex) + (rowIndex + 1)
        val newStyle = transform(sheet.styles[ref] ?: ExcelCellStyle())
        val newStyles = if (newStyle.isDefault) sheet.styles - ref else sheet.styles + (ref to newStyle)
        current[sheetIndex] = sheet.copy(styles = newStyles)
        _excelSheets.value = current
    }

    fun addExcelRow(sheetIndex: Int) {
        val current = _excelSheets.value.toMutableList()
        val sheet = current.getOrNull(sheetIndex) ?: return
        val cols = (sheet.rows.maxOfOrNull { it.size } ?: 3).coerceAtLeast(1)
        current[sheetIndex] = sheet.copy(rows = sheet.rows + listOf(List(cols) { "" }))
        _excelSheets.value = current
    }

    fun saveActiveSpreadsheet() {
        val doc = _activeDocument.value ?: return
        viewModelScope.launch {
            val saved = repository.saveSpreadsheetDocument(doc, _excelSheets.value)
            _activeDocument.value = saved
            excelStatusMessage.value = "Saved spreadsheet changes"
        }
    }

    // ==================== CODE & LOG FILE ACTIONS ====================

    fun exportActiveCodeToPdf() {
        val doc = _activeDocument.value ?: return
        val blocks = CodeReaderEngine.toWordBlocks(doc.title, _codeLines.value)
        convertWordBlocksToPdf(doc.title, blocks, doc.subjectTag)
    }

    // ==================== QR / BARCODE SCANNER ====================

    fun scanQrFromBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            val codes = withContext(Dispatchers.IO) {
                QrBarcodeEngine.scanBitmap(bitmap)
            }
            _qrResults.value = codes
        }
    }

    fun clearQrResults() {
        _qrResults.value = emptyList()
    }

    // ==================== TEXT TO SPEECH (OFFLINE READ ALOUD) ====================

    fun speakStudyText(text: String) {
        ttsController.speak(text)
    }

    fun stopTts() {
        ttsController.stop()
    }

    fun setTtsRate(rate: Float) {
        ttsController.setRate(rate)
    }

    override fun onCleared() {
        ttsController.shutdown()
        super.onCleared()
    }

    // ==================== EXTERNAL OPEN-WITH & SHARE-TO INTENTS ====================

    fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri -> importFileFromUri(uri) }
            }
            Intent.ACTION_SEND -> {
                val streamUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (streamUri != null) {
                    importFileFromUri(streamUri)
                } else {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!sharedText.isNullOrBlank()) {
                        openOcrResultAsWordDoc("Shared Study Note", sharedText)
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (!uris.isNullOrEmpty()) {
                    startPicturesToPdf(uris)
                }
            }
        }
    }

    // ==================== BATCH LIBRARY, BATCH CAMERA, PDF->IMAGES & SPREADSHEET CREATE ====================

    fun batchDeleteDocuments(docIds: Set<String>) {
        if (docIds.isEmpty()) return
        viewModelScope.launch {
            docIds.forEach { repository.deleteDocument(it) }
            if (_activeDocument.value?.id in docIds) {
                _activeDocument.value = null
            }
        }
    }

    fun batchTagDocuments(docIds: Set<String>, subjectTag: String) {
        if (docIds.isEmpty()) return
        viewModelScope.launch {
            allDocuments.value.filter { it.id in docIds }.forEach { doc ->
                repository.renameDocument(doc.id, doc.title, subjectTag.trim())
            }
        }
    }

    fun exportPdfAsScanImages(doc: Document, pageIndices: List<Int>) {
        val source = doc.filePath?.let { File(it) } ?: return
        if (!source.exists()) return
        viewModelScope.launch {
            conversionProgress.value = "Extracting PDF pages as images..."
            val outDir = File(appContext.filesDir, "documents")
            val imageFiles = withContext(Dispatchers.IO) {
                PdfSuiteEngine.pdfToImageFiles(source, pageIndices, outDir, 1400) { cur, total ->
                    conversionProgress.value = "Rendering page $cur of $total..."
                }
            }
            if (imageFiles.isNotEmpty()) {
                val scanDoc = repository.createScanDocumentFromFiles(
                    title = "${doc.title} (Pages)",
                    subjectTag = doc.subjectTag,
                    imageFiles = imageFiles,
                    category = DocumentCategory.SCANS
                )
                conversionProgress.value = null
                _activeDocument.value = scanDoc
                navigateTo(Screen.DOCUMENT_DETAIL)
            } else {
                conversionProgress.value = null
                pdfStatusMessage.value = "Could not extract pages"
            }
        }
    }

    fun finishBatchCameraScan(tempImagePaths: List<String>, targetDocId: String? = null) {
        if (tempImagePaths.isEmpty()) return
        viewModelScope.launch {
            conversionProgress.value = "Enhancing ${tempImagePaths.size} scanned pages..."
            val processedFiles = withContext(Dispatchers.IO) {
                tempImagePaths.mapIndexedNotNull { idx, path ->
                    conversionProgress.value = "Enhancing page ${idx + 1} of ${tempImagePaths.size}..."
                    val bmp = ImageProcessor.loadOrientedBitmapFromFile(path, 2000)
                    File(path).delete()
                    if (bmp != null) {
                        val quad = ImageProcessor.detectDocumentQuad(bmp)
                        val cropped = ImageProcessor.cropPerspective(bmp, quad)
                        val enhanced = ImageProcessor.applyFilter(cropped, FilterType.MAGIC_COLOR)
                        val savedPath = ImageProcessor.saveBitmapToFile(appContext, enhanced, "batch_scan")
                        bmp.recycle()
                        cropped.recycle()
                        enhanced.recycle()
                        File(savedPath)
                    } else null
                }
            }
            if (processedFiles.isNotEmpty()) {
                val doc = if (targetDocId != null) {
                    var latest: Document? = null
                    processedFiles.forEach { file ->
                        val bmp = ImageProcessor.loadOrientedBitmapFromFile(file.absolutePath, 2000)
                        if (bmp != null) {
                            latest = repository.addPageToScanDocument(targetDocId, bmp, FilterType.ORIGINAL, CropQuad())
                            bmp.recycle()
                        }
                    }
                    latest
                } else {
                    repository.createScanDocumentFromFiles(
                        title = "Batch Scan (${processedFiles.size} Pages)",
                        subjectTag = "",
                        imageFiles = processedFiles
                    )
                }
                conversionProgress.value = null
                doc?.let {
                    _activeDocument.value = it
                    navigateTo(Screen.DOCUMENT_DETAIL)
                }
            } else {
                conversionProgress.value = null
            }
        }
    }

    fun createNewCodeFile(title: String, ext: String, subjectTag: String) {
        viewModelScope.launch {
            val (doc, content) = repository.createBlankCodeFileDocument(title, ext, subjectTag)
            _activeDocument.value = doc
            _codeContent.value = content
            _codeLines.value = content.lines()
            navigateTo(Screen.CODE_READER)
        }
    }

    // ==================== QALAM AI (v2.6) ====================

    private val aiPreferences = AiPreferences(appContext)

    val aiApiKey: StateFlow<String> = aiPreferences.apiKey
    val aiModelId: StateFlow<String> = aiPreferences.modelId

    fun setAiApiKey(key: String) {
        viewModelScope.launch { aiPreferences.setApiKey(key) }
    }

    fun setAiModel(modelId: String) {
        viewModelScope.launch { aiPreferences.setModel(modelId) }
    }

    fun navigateToAi() {
        navigateTo(Screen.AI_ASSISTANT)
    }

    /** Creates a real .docx from AI-generated blocks and opens it in the Word editor. */
    fun createAiWordDocument(title: String, blocks: List<WordBlock>) {
        if (blocks.isEmpty()) return
        viewModelScope.launch {
            val (doc, _) = repository.createBlankWordDocument(title, _activeDocument.value?.subjectTag ?: "")
            val saved = repository.saveWordDocument(doc, blocks)
            _activeDocument.value = saved
            _wordBlocks.value = blocks
            navigateTo(Screen.WORD_EDITOR)
        }
    }

    /** Saves AI-generated code as a new code file and opens it in the code editor. */
    fun createAiCodeFile(title: String, ext: String, content: String) {
        viewModelScope.launch {
            val (doc, _) = repository.createBlankCodeFileDocument(title, ext, "")
            val saved = repository.saveCodeFileDocument(doc, content)
            _activeDocument.value = saved
            _codeContent.value = content
            _codeLines.value = content.lines()
            navigateTo(Screen.CODE_READER)
        }
    }

    fun saveActiveCodeFile(content: String) {
        val doc = _activeDocument.value ?: return
        viewModelScope.launch {
            val saved = repository.saveCodeFileDocument(doc, content)
            _activeDocument.value = saved
            _codeContent.value = content
            _codeLines.value = content.lines()
        }
    }

    /** Opens a code document directly in the offline HTML/Markdown preview. */
    fun openCodePreview(doc: Document) {
        _activeDocument.value = doc
        openHtmlPreview()
    }

    fun openHtmlPreview() {
        val doc = _activeDocument.value ?: return
        val file = doc.filePath?.let { File(it) } ?: return
        if (!file.exists()) return
        _htmlPreviewFile.value = file
        navigateTo(Screen.HTML_PREVIEW)
    }

    fun createNewPresentation(title: String, subjectTag: String) {
        viewModelScope.launch {
            val (doc, slides) = repository.createBlankSlidesDocument(title, subjectTag)
            _activeDocument.value = doc
            _slidesList.value = slides
            navigateTo(Screen.SLIDES_READER)
        }
    }

    fun createNewSpreadsheet(title: String, subjectTag: String) {
        viewModelScope.launch {
            val (doc, sheets) = repository.createBlankSpreadsheetDocument(title, subjectTag)
            _activeDocument.value = doc
            _excelSheets.value = sheets
            navigateTo(Screen.EXCEL_READER)
        }
    }

    fun addExcelColumn(sheetIndex: Int) {
        val current = _excelSheets.value.toMutableList()
        val sheet = current.getOrNull(sheetIndex) ?: return
        val newColNum = (sheet.rows.maxOfOrNull { it.size } ?: 0) + 1
        val newRows = sheet.rows.mapIndexed { rIdx, row ->
            row + if (rIdx == 0) "Col $newColNum" else ""
        }
        current[sheetIndex] = sheet.copy(rows = newRows)
        _excelSheets.value = current
    }

    fun deleteExcelRow(sheetIndex: Int, rowIndex: Int) {
        val current = _excelSheets.value.toMutableList()
        val sheet = current.getOrNull(sheetIndex) ?: return
        if (sheet.rows.size <= 1 || rowIndex !in 1 until sheet.rows.size) return
        val newRows = sheet.rows.toMutableList().apply { removeAt(rowIndex) }
        current[sheetIndex] = sheet.copy(rows = newRows)
        _excelSheets.value = current
    }
}

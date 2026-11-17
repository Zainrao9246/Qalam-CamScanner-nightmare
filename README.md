# Qalam

**Qalam — Fast, clean, 100% ad-free Document Scanner, PDF Annotator & Multi-Format Reader for Android**  
Created by **Shaheer Rao** (`com.shaheerrao.camscanpro`)

Qalam is built for students and professionals (especially Virtual University & lecture note workflows) who want one fast, offline-first app to scan notes, read and annotate handouts across every common document format, clean messy Word documents, edit spreadsheets, and merge/split/compress PDFs without ads, watermarks, or paywalls.

---

## Features

### 1. PDF Reader & Full Annotation Studio
- **Zoom & Navigation:** Pinch-to-zoom (`1x`–`4x`), pan, **double-tap zoom (`1x` ↔ `2.2x`)**, `+` / `-` / `Reset` zoom controls, **Vertical Continuous Scroll** or **Horizontal Page Flip** mode, visual **All-Pages Thumbnail Grid**, and quick Jump-to-Page.
- **Highlighting & Drawing:** Smooth **Highlighter** (Yellow, Green, Cyan, Pink), **Freehand Pen Ink**, **Underline**, **Eraser**, **Undo**, and **Clear Page**.
- **Comments, Notes & Bookmarks:** Tap anywhere to drop numbered **Sticky Comment Pins (`💬`)** with title & body, write **Per-Page Study Notes**, toggle **Bookmarks (`🔖`)**, and browse everything in the **Notes & Bookmarks Drawer**.
- **Eye-Comfort Modes:** 1-tap cycle between **Day**, **Sepia (Paper)**, and **Night (Dark Inverted)** reading modes.
- **Annotated PDF Export & OCR:** Flatten highlights, ink strokes, comment pins, and a study notes summary page into a new `.pdf`, or extract text from any page offline via ML Kit OCR (with offline Read-Aloud TTS).

### 2. PDF Power Suite
- **Merge PDFs:** Combine multiple PDFs in custom order into one clean PDF (also available directly via Library Multi-Select).
- **Split, Extract, Reorder & Rotate:** Select page ranges (`1-3, 5`), reorder pages, or rotate pages (`0° / 90° / 180° / 270°`) visually.
- **Compress PDF:** Shrink PDFs with **High Quality**, **Balanced Study**, or **Smallest Size** presets and view exact before/after file sizes.
- **Watermark PDF:** Stamp custom diagonal or footer text across all pages.
- **PDF to Scan Images:** Extract any page range from a PDF into high-resolution JPG pages inside an editable Scan Document.

### 3. Multi-Format Document Readers, Editors & Study Assistant (100% Offline)
- **Create File Studio:** Create **Word `.docx`**, **Excel `.xlsx`**, **HTML**, **CSS**, **JavaScript**, **TypeScript**, **Markdown**, **JSON**, **XML**, **CSV**, **Python**, **Java**, **Kotlin**, **C/C++**, **SQL** and **YAML** files from ready-made starter templates — all generated offline.
- **Offline HTML & Markdown Preview:** Render `.html`/`.htm`/`.md` files in a built-in WebView with Day/Night modes — JavaScript, CSS and links work fully offline (the app has no INTERNET permission, so nothing is ever fetched from the network).
- **Code Editor with Edit & Save:** Tap the pencil in any code/text/web file to edit its source in a monospace editor and save back to the same file, then preview HTML/Markdown instantly.
- **Offline Read-Aloud (`TextToSpeech`) & Headings Outline:** Listen to Word/E-Book notes, Slides, and OCR scans with adjustable speed (`0.85x`–`1.5x`), plus an interactive **Headings Outline Drawer** for instant chapter/section jumps in long handouts.
- **Word & E-Book Reader / Editor (`.docx`, `.doc`, `.odt`, `.epub`, `.rtf`):** Opens in **Paper Reader Mode** (`A-` / `A+` scaling, Night Mode) with 1-tap switch to **Edit & Clean**.
- **Smart Word Cleaner:** Fixes broken PDF line wraps, extra blank lines, repeated headers/watermarks, and bullet formatting inside `.docx` files, plus Find & Replace and Word-to-PDF conversion.
- **Spreadsheet Creator, Reader & Editor (`.xlsx`, `.ods`, `.xls`, `.csv`, `.tsv`):** Create blank `.xlsx` workbooks, multi-sheet tabs, sticky headers, **tap any cell to edit**, **`+ Row` / `+ Col` / `Delete Row`**, live numeric column **`Sum / Avg / Count`** inspector, save back to `.xlsx`/`.csv`/`.tsv`, and 1-tap export to PDF.
- **Presentation Creator, Reader & Carousel (`.pptx`, `.odp`):** Create a new **`.pptx` deck** from a template, switch between vertical List mode and horizontal Slide Carousel presenter mode with speaker notes, Read-Aloud, and 1-tap conversion to editable Word study notes.
- **Code, Data & Log Reader (`.cpp`, `.c`, `.h`, `.java`, `.kt`, `.py`, `.js`, `.ts`, `.json`, `.xml`, `.sql`, `.css`, `.yaml`, `.log`):** Monospace line-numbered viewer with text search, copy-all, font size control, and PDF export.

### 4. Camera Scanner, Batch Mode, QR & Library Organization
- **Batch Multi-Page Camera Scan:** Toggle **Batch ON** in the camera to snap multiple lecture pages in sequence and tap **Done (N)** to auto-crop and filter the entire stack at once.
- **Smart Camera Modes:** **Document**, **ID Card (Front + Back combined on A4)**, and **Book 2-Page Spread Split** (automatically splits open book spreads into left/right pages).
- **Auto-Edge Detection & Filters:** Automatic 4-corner border snapping with manual handles plus **Original**, **Magic Color**, **Grayscale**, and **B&W Document** filters.
- **Pictures to PDF & Android "Open With / Share To":** Open PDF/Word/Excel/Slides/Code files or share gallery images from WhatsApp, Chrome, Files, or Classroom directly into Qalam.
- **Library Sorting, Subject Filter Pills & Long-Press Multi-Select:** Sort by Recent/Oldest/Name/Type, filter by course subject tags (`CS101`, `MTH101`, etc.), or long-press any card to bulk-merge PDFs, batch-tag, or batch-delete.
- **QR & Barcode Scanner & Digital Signatures:** Live CameraX + ML Kit offline QR/barcode scanner and ink signature stamping.

### 5. Appearance & Compatibility
- Instant **Dark Theme / Light Theme** toggle in the top-right `☰` Tools Drawer.
- Supports **Android 7.0 (API 24) through Android 16+ (API 36)** — tested for Android 10–18 devices.

---

## Download APK Without Android Studio (GitHub Actions)

You can generate an installable `.apk` directly in the cloud using GitHub Actions—no laptop build tools or USB cable required:

1. Upload or push this project to a GitHub repository.
2. Open your repository on GitHub and click the **Actions** tab.
3. Select **Build Android APK** on the left (it runs automatically on every push to `main`/`master`, or you can click **Run workflow** manually).
4. Once the run finishes with a green checkmark, scroll down to **Artifacts** and download **`Qalam-APK`**.
5. Unzip the downloaded file on your phone or laptop to get **`Qalam-by-Shaheer-Rao.apk`**, then tap it on your Android phone to install.

### Optional: Building Signed Release `.aab` / `.apk` on GitHub
To generate Play Store `.aab` bundles in GitHub Actions, add these **Repository Secrets** (`Settings -> Secrets and variables -> Actions`):
- `RELEASE_KEYSTORE_BASE64`: Base64 string of your `.jks` upload keystore (`base64 -w 0 my-upload-key.jks`)
- `STORE_PASSWORD`: Your keystore password
- `KEY_PASSWORD`: Your key password (alias: `upload`)

---

## Building Locally

### Requirements
- JDK 17
- Android SDK (API 36)
- Gradle 9.3.1 (or Android Studio Ladybug / Meerkat+)

### Commands
```bash
# 1. Decode the debug signing keystore
base64 -d debug.keystore.base64 > debug.keystore

# 2. Run unit & Robolectric tests
gradle :app:testDebugUnitTest

# 3. Build the installable APK
gradle :app:assembleDebug
```

Output APK path:
`app/build/outputs/apk/debug/app-debug.apk`

---

## License

Copyright (c) 2026 **Shaheer Rao**.  
Licensed under the [MIT License](LICENSE).

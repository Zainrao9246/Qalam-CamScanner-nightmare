package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.example.model.CropQuad
import com.example.model.DocKind
import com.example.model.Document
import com.example.model.DocumentCategory
import com.example.model.ExcelSheetData
import com.example.model.FilterType
import com.example.model.SlideItem
import com.example.model.WordBlock
import com.example.model.WordBlockType
import com.example.util.DocxEngine
import com.example.util.ExcelEngine
import com.example.util.PdfEngine
import com.example.util.SlidesEngine
import java.io.File
import java.util.UUID

/**
 * Generates real starter PDF, Word (.docx), and Scanned Document files on first launch
 * using the actual PdfEngine, DocxEngine, and ImageProcessor so the user can test
 * reading, converting, cleaning, cropping, and exporting immediately.
 */
object StarterDocumentSeeder {

    suspend fun seedRealStarterDocuments(context: Context, repository: DocumentRepository) {
        // 1. Real 3-Page Study Handout PDF (for testing PDF Reader, Night Mode, OCR, & PDF -> Word)
        val handoutBlocks = listOf(
            WordBlock(type = WordBlockType.HEADING_1, text = "CS101 - Introduction to Computing", isBold = true),
            WordBlock(type = WordBlockType.HEADING_2, text = "Lecture 12: Operating Systems & Memory Management", isBold = true),
            WordBlock(
                type = WordBlockType.PARAGRAPH,
                text = "An Operating System (OS) acts as an intermediary between the computer user and computer hardware. Its primary goals are to execute user programs, make solving user problems easier, and use hardware resources efficiently."
            ),
            WordBlock(type = WordBlockType.HEADING_2, text = "1. Core Functions of an Operating System", isBold = true),
            WordBlock(type = WordBlockType.BULLET, text = "Process Management: Scheduling CPU threads, handling multitasking, and synchronization."),
            WordBlock(type = WordBlockType.BULLET, text = "Memory Management: Allocating RAM blocks, virtual memory paging, and preventing leaks."),
            WordBlock(type = WordBlockType.BULLET, text = "File System Management: Organizing directories, permissions, and persistent disk I/O."),
            WordBlock(type = WordBlockType.BULLET, text = "Device I/O Control: Buffering and driver communication for keyboards, displays, and networks."),
            WordBlock(type = WordBlockType.HEADING_1, text = "2. CPU Scheduling Algorithms", isBold = true),
            WordBlock(
                type = WordBlockType.PARAGRAPH,
                text = "When multiple processes wait in the ready queue, the CPU scheduler decides which process runs next. Choosing the right algorithm balances throughput, turnaround time, and responsiveness."
            ),
            WordBlock(type = WordBlockType.BULLET, text = "First-Come, First-Served (FCFS): Simple FIFO queue; can suffer from convoy effect."),
            WordBlock(type = WordBlockType.BULLET, text = "Shortest Job Next (SJN): Minimizes average waiting time for known burst lengths."),
            WordBlock(type = WordBlockType.BULLET, text = "Round Robin (RR): Gives each process a fixed time quantum; ideal for interactive systems."),
            WordBlock(type = WordBlockType.HEADING_1, text = "3. Virtual Memory & Paging", isBold = true),
            WordBlock(
                type = WordBlockType.PARAGRAPH,
                text = "Virtual memory separates logical memory perceived by applications from physical RAM. By dividing memory into fixed-size pages and frames, programs larger than physical RAM can execute smoothly."
            ),
            WordBlock(type = WordBlockType.HEADING_2, text = "Exam Review Questions", isBold = true),
            WordBlock(type = WordBlockType.BULLET, text = "Explain the difference between paging and segmentation."),
            WordBlock(type = WordBlockType.BULLET, text = "What causes thrashing in virtual memory systems, and how is it prevented?"),
            WordBlock(type = WordBlockType.BULLET, text = "Compare Round Robin vs Priority Scheduling for real-time tasks."),
            WordBlock(
                type = WordBlockType.PARAGRAPH,
                text = "Tip: Open the top-right menu in this PDF Reader to toggle Night Reading Mode, run ML Kit OCR on any page, or convert this PDF straight into an editable Word (.docx) note!"
            )
        )

        val pdfDir = File(context.filesDir, "pdfs")
        if (!pdfDir.exists()) pdfDir.mkdirs()
        val handoutPdf = File(pdfDir, "CS101_Lecture12_Handout.pdf")
        PdfEngine.convertWordToPdf(
            title = "CS101 Lecture 12 Handout",
            blocks = handoutBlocks,
            outputPdfFile = handoutPdf,
            pageSize = PdfEngine.PageSize.A4
        )
        repository.registerGeneratedPdf(
            title = "CS101 Lecture 12 Handout",
            pdfFile = handoutPdf,
            subjectTag = "CS101",
            category = DocumentCategory.HANDOUTS
        )

        // 2. Real Editable Word (.docx) Study Note (with messy formatting ready for 1-Tap Clean Doc!)
        val wordBlocks = listOf(
            WordBlock(type = WordBlockType.PARAGRAPH, text = "  Lecture 14:   Binary Search Trees  "),
            WordBlock(type = WordBlockType.PARAGRAPH, text = "Virtual University Study Notes"),
            WordBlock(
                type = WordBlockType.PARAGRAPH,
                text = "A binary search tree is a rooted binary tree where each"
            ),
            WordBlock(
                type = WordBlockType.PARAGRAPH,
                text = "internal node stores a key greater than all keys in its left subtree."
            ),
            WordBlock(type = WordBlockType.PARAGRAPH, text = "Page 1 of 4"),
            WordBlock(type = WordBlockType.PARAGRAPH, text = "1.1 Key Operations:"),
            WordBlock(type = WordBlockType.BULLET, text = "Search: Average O(log n) time complexity"),
            WordBlock(type = WordBlockType.BULLET, text = "Insert: Compares recursively down left or right branch"),
            WordBlock(type = WordBlockType.BULLET, text = "Inorder Traversal: Produces sorted ascending output"),
            WordBlock(type = WordBlockType.PARAGRAPH, text = "Virtual University Study Notes"),
            WordBlock(type = WordBlockType.PARAGRAPH, text = "Virtual University Study Notes")
        )

        val wordDir = File(context.filesDir, "word_docs")
        if (!wordDir.exists()) wordDir.mkdirs()
        val noteDocx = File(wordDir, "CS301_Binary_Trees_${UUID.randomUUID().toString().take(4)}.docx")
        DocxEngine.writeDocxFile(wordBlocks, noteDocx)
        repository.saveDocument(
            Document(
                title = "CS301 Binary Trees (Tap Clean Doc)",
                kind = com.example.model.DocKind.WORD,
                subjectTag = "CS301",
                filePath = noteDocx.absolutePath,
                totalPages = 1,
                category = DocumentCategory.NOTES
            )
        )

        // 3. Real Scanned Page Image (for testing 4-Corner Crop, Magic Color, OCR, & Signature)
        val bmp = createCalculusNoteBitmap()
        repository.createScanDocument(
            title = "MTH202 Calculus Formula Sheet",
            subjectTag = "MTH202",
            bitmap = bmp,
            category = DocumentCategory.SCANS,
            filter = FilterType.MAGIC_COLOR,
            cropQuad = CropQuad()
        )
        bmp.recycle()
    }

    suspend fun seedOfficeStarterDocuments(context: Context, repository: DocumentRepository) {
        // 4. Real Multi-Sheet Excel (.xlsx) Grade & Study Schedule Workbook
        val sheetDir = File(context.filesDir, "spreadsheets")
        if (!sheetDir.exists()) sheetDir.mkdirs()
        val xlsxFile = File(sheetDir, "VU_Semester_Grades_Tracker.xlsx")
        val sheets = listOf(
            ExcelSheetData(
                name = "Course Grades",
                rows = listOf(
                    listOf("Course", "Title", "Assignments", "GDB", "Midterm", "Final Target", "Grade"),
                    listOf("CS101", "Intro to Computing", "18 / 20", "4 / 5", "32 / 35", "36 / 40", "A"),
                    listOf("CS201", "Intro to Programming", "17 / 20", "5 / 5", "29 / 35", "34 / 40", "A-"),
                    listOf("CS301", "Data Structures", "19 / 20", "5 / 5", "33 / 35", "37 / 40", "A+"),
                    listOf("MTH202", "Discrete Mathematics", "16 / 20", "4 / 5", "28 / 35", "33 / 40", "B+"),
                    listOf("ENG201", "Business Communication", "18 / 20", "4 / 5", "30 / 35", "34 / 40", "A")
                )
            ),
            ExcelSheetData(
                name = "Study Schedule",
                rows = listOf(
                    listOf("Day", "Morning Focus", "Evening Practice", "Hours", "Status"),
                    listOf("Monday", "CS101 Handouts 10-12", "Quiz Prep", "3.5", "Done"),
                    listOf("Tuesday", "CS301 Binary Trees", "C++ Lab Practice", "4.0", "Done"),
                    listOf("Wednesday", "MTH202 Integration", "Past Paper Review", "3.0", "In Progress"),
                    listOf("Thursday", "CS201 OOP Slides", "Assignment 3", "4.0", "Planned"),
                    listOf("Friday", "ENG201 Summary", "GDB Drafting", "2.5", "Planned")
                )
            )
        )
        ExcelEngine.writeXlsxFile(sheets, xlsxFile)
        repository.saveDocument(
            Document(
                title = "VU Semester Grades & Plan",
                kind = DocKind.EXCEL,
                subjectTag = "VU",
                filePath = xlsxFile.absolutePath,
                totalPages = sheets.size,
                category = DocumentCategory.OTHER
            )
        )

        // 5. Real PowerPoint (.pptx) Lecture Slides Deck
        val slidesDir = File(context.filesDir, "slides")
        if (!slidesDir.exists()) slidesDir.mkdirs()
        val pptxFile = File(slidesDir, "CS201_OOP_Lecture_Slides.pptx")
        val slides = listOf(
            SlideItem(
                number = 1,
                title = "CS201 - Object-Oriented Programming",
                bodyLines = listOf(
                    "Core Pillars: Encapsulation, Abstraction, Inheritance & Polymorphism",
                    "Designed for Virtual University Midterm Revision",
                    "Tap 'Convert to Word Notes' above to save an editable study guide!"
                ),
                notes = "Focus on constructors, virtual functions, and polymorphism examples for the exam."
            ),
            SlideItem(
                number = 2,
                title = "1. Encapsulation & Access Specifiers",
                bodyLines = listOf(
                    "Bundles data members and member functions inside a single class",
                    "private: Accessible only inside the class",
                    "protected: Accessible in class and derived subclasses",
                    "public: Accessible anywhere via object interface"
                ),
                notes = "Remember: keeping data members private enforces data hiding."
            ),
            SlideItem(
                number = 3,
                title = "2. Inheritance & Reusability",
                bodyLines = listOf(
                    "Derived class inherits attributes and methods from Base class",
                    "Single, Multilevel, Multiple & Hierarchical inheritance",
                    "Base constructor runs before Derived constructor"
                ),
                notes = "Destructors execute in reverse order: Derived first, then Base."
            ),
            SlideItem(
                number = 4,
                title = "3. Virtual Functions & Polymorphism",
                bodyLines = listOf(
                    "Compile-time: Function & Operator Overloading",
                    "Run-time: Virtual functions resolved via vtable",
                    "Pure virtual function (= 0) creates an Abstract Class"
                )
            )
        )
        SlidesEngine.writePptxFile(slides, pptxFile)
        repository.saveDocument(
            Document(
                title = "CS201 OOP Lecture Slides",
                kind = DocKind.SLIDES,
                subjectTag = "CS201",
                filePath = pptxFile.absolutePath,
                totalPages = slides.size,
                category = DocumentCategory.HANDOUTS
            )
        )
    }

    private fun createCalculusNoteBitmap(): Bitmap {
        val w = 900
        val h = 1200
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(245, 244, 238)) // Slightly warm paper photo

        val rulePaint = Paint().apply {
            color = Color.rgb(210, 224, 240)
            strokeWidth = 1.5f
        }
        for (y in 120..h step 48) {
            canvas.drawLine(0f, y.toFloat(), w.toFloat(), y.toFloat(), rulePaint)
        }

        val marginPaint = Paint().apply {
            color = Color.rgb(240, 165, 165)
            strokeWidth = 2.5f
        }
        canvas.drawLine(105f, 0f, 105f, h.toFloat(), marginPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 105, 92)
            textSize = 34f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText("MTH202 - Calculus & Integration Rules", 125f, 102f, titlePaint)

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 38, 68)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val lines = listOf(
            "1. Power Rule: d/dx [x^n] = n * x^(n - 1)",
            "2. Chain Rule: d/dx [f(g(x))] = f'(g(x)) * g'(x)",
            "3. Exponential: d/dx [e^x] = e^x",
            "4. Logarithm: d/dx [ln(x)] = 1 / x",
            "",
            "Integration Formulas:",
            "- Integral of x^n dx = x^(n+1) / (n+1) + C",
            "- Integral of sin(x) dx = -cos(x) + C",
            "- Integral of cos(x) dx = sin(x) + C",
            "",
            "Assignment Due: November 20, 2026",
            "Instructor Email: math@vu.edu.pk"
        )

        var y = 162f
        for (line in lines) {
            if (line.isNotEmpty()) {
                canvas.drawText(line, 125f, y, bodyPaint)
            }
            y += 48f
        }
        return bmp
    }
}

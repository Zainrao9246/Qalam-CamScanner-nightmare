package com.example

import com.example.model.WordBlock
import com.example.model.WordBlockType
import com.example.util.DocxEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocxEngineTest {

    @Test
    fun docxWriteAndReadRoundTripPreservesBlocks() {
        val tempFile = Files.createTempFile("test_note", ".docx").toFile()
        try {
            val inputBlocks = listOf(
                WordBlock(type = WordBlockType.HEADING_1, text = "Lecture 14: Binary Trees", isBold = true),
                WordBlock(type = WordBlockType.HEADING_2, text = "1.1 Tree Traversal", isBold = true),
                WordBlock(type = WordBlockType.PARAGRAPH, text = "Inorder visits left, root, then right."),
                WordBlock(type = WordBlockType.BULLET, text = "Preorder visits root first")
            )

            DocxEngine.writeDocxFile(inputBlocks, tempFile)
            assertTrue(tempFile.exists() && tempFile.length() > 0)

            val parsed = DocxEngine.readDocxFile(tempFile)
            assertEquals(4, parsed.size)
            assertEquals(WordBlockType.HEADING_1, parsed[0].type)
            assertEquals("Lecture 14: Binary Trees", parsed[0].text)
            assertEquals(WordBlockType.HEADING_2, parsed[1].type)
            assertEquals(WordBlockType.PARAGRAPH, parsed[2].type)
            assertEquals(WordBlockType.BULLET, parsed[3].type)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun smartCleanerFixesMessyPdfExtractedText() {
        val messy = listOf(
            WordBlock(type = WordBlockType.PARAGRAPH, text = "  Lecture 5:   Operating Systems  "),
            WordBlock(type = WordBlockType.PARAGRAPH, text = "Virtual University of Pakistan"),
            WordBlock(type = WordBlockType.PARAGRAPH, text = "Process scheduling determines which"),
            WordBlock(type = WordBlockType.PARAGRAPH, text = "ready process runs next on CPU."),
            WordBlock(type = WordBlockType.PARAGRAPH, text = "Page 1 of 12"),
            WordBlock(type = WordBlockType.PARAGRAPH, text = "Virtual University of Pakistan"),
            WordBlock(type = WordBlockType.PARAGRAPH, text = "Virtual University of Pakistan"),
            WordBlock(type = WordBlockType.PARAGRAPH, text = "   ")
        )

        var cleaned = DocxEngine.cleanWhitespace(messy)
        cleaned = DocxEngine.stripNoiseAndWatermarks(cleaned)
        cleaned = DocxEngine.mergeBrokenLineBreaks(cleaned)
        cleaned = DocxEngine.autoFormatHeadings(cleaned)

        assertEquals(2, cleaned.size)
        assertEquals(WordBlockType.HEADING_1, cleaned[0].type)
        assertEquals("Lecture 5: Operating Systems", cleaned[0].text)
        assertEquals("Process scheduling determines which ready process runs next on CPU.", cleaned[1].text)
    }

    @Test
    fun findAndReplaceUpdatesMatchesAccurately() {
        val blocks = listOf(
            WordBlock(type = WordBlockType.PARAGRAPH, text = "CS101 covers basic computing. CS101 is fun.")
        )
        val (updated, count) = DocxEngine.findAndReplace(blocks, "CS101", "CS201")
        assertEquals(2, count)
        assertEquals("CS201 covers basic computing. CS201 is fun.", updated.first().text)
    }
}

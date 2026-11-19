package com.example

import com.example.model.SlideItem
import com.example.util.SlidesEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SlidesEngineTest {

    @Test
    fun pptxWriteAndReadRoundTripPreservesText() {
        val slides = listOf(
            SlideItem(
                number = 1,
                title = "Operating Systems",
                bodyLines = listOf("Process scheduling", "Memory management", "File systems"),
                notes = "Revise for midterm"
            ),
            SlideItem(
                number = 2,
                title = "CPU Scheduling",
                bodyLines = listOf("FCFS", "Round Robin")
            )
        )
        val tempFile = Files.createTempFile("test_deck", ".pptx").toFile()
        try {
            SlidesEngine.writePptxFile(slides, tempFile)
            assertTrue(tempFile.exists() && tempFile.length() > 0)

            val read = SlidesEngine.readPptxFile(tempFile)
            assertEquals(2, read.size)
            assertEquals("Operating Systems", read[0].title)
            assertEquals(listOf("Process scheduling", "Memory management", "File systems"), read[0].bodyLines)
            assertEquals("Revise for midterm", read[0].notes)
            assertEquals("CPU Scheduling", read[1].title)
            assertEquals(listOf("FCFS", "Round Robin"), read[1].bodyLines)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun pptxRoundTripPreservesColorsSizesAndBackground() {
        val slides = listOf(
            SlideItem(
                number = 1,
                title = "Styled Slide",
                bodyLines = listOf("Point one", "Point two"),
                titleColorHex = "C62828",
                bodyColorHex = "1565C0",
                titleSizePt = 40,
                bodySizePt = 20,
                backgroundHex = "FFF8E1"
            )
        )
        val tempFile = Files.createTempFile("test_styled_deck", ".pptx").toFile()
        try {
            SlidesEngine.writePptxFile(slides, tempFile)
            val read = SlidesEngine.readPptxFile(tempFile)
            assertEquals(1, read.size)
            val out = read[0]
            assertEquals("C62828", out.titleColorHex)
            assertEquals("1565C0", out.bodyColorHex)
            assertEquals(40, out.titleSizePt)
            assertEquals(20, out.bodySizePt)
            assertEquals("FFF8E1", out.backgroundHex)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun plainSlidesGetDefaultsWhenRestyled() {
        val slides = listOf(
            SlideItem(number = 1, title = "Plain", bodyLines = listOf("line"))
        )
        val tempFile = Files.createTempFile("test_plain_deck", ".pptx").toFile()
        try {
            SlidesEngine.writePptxFile(slides, tempFile)
            val read = SlidesEngine.readPptxFile(tempFile)
            assertEquals(null, read[0].titleColorHex)
            assertEquals(null, read[0].backgroundHex)
        } finally {
            tempFile.delete()
        }
    }
}

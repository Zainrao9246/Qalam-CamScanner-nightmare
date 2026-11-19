package com.example

import com.example.model.ExcelCellStyle
import com.example.model.ExcelSheetData
import com.example.util.ExcelEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExcelFormulaTest {

    private fun sampleSheet() = ExcelSheetData(
        name = "Marks",
        rows = listOf(
            listOf("Marks", "Points", "Result"),
            listOf("85", "4.0", ""),
            listOf("72", "3.0", ""),
            listOf("91", "4.0", ""),
            listOf("60", "2.0", "")
        )
    )

    @Test
    fun formulaEvaluationSumAvgMinMaxCount() {
        val sheet = sampleSheet()
        assertEquals("248", ExcelEngine.evaluateFormula("=SUM(A2:A5)", sheet))
        assertEquals("77", ExcelEngine.evaluateFormula("=SUM(A2:A5)/COUNT(A2:A5)", sheet))
        assertEquals("3.25", ExcelEngine.evaluateFormula("=AVG(B2:B5)", sheet))
        assertEquals("91", ExcelEngine.evaluateFormula("=MAX(A2:A5)", sheet))
        assertEquals("60", ExcelEngine.evaluateFormula("=MIN(A2:A5)", sheet))
        assertEquals("4", ExcelEngine.evaluateFormula("=COUNT(A2:A5)", sheet))
    }

    @Test
    fun formulaArithmeticPrecedenceAndRefs() {
        val sheet = sampleSheet()
        assertEquals("14", ExcelEngine.evaluateFormula("=2+3*4", sheet))
        assertEquals("20", ExcelEngine.evaluateFormula("=(2+3)*4", sheet))
        assertEquals("12.5", ExcelEngine.evaluateFormula("=A2*0.1+B2", sheet))
        assertEquals("80", ExcelEngine.evaluateFormula("=-5+A2", sheet))
        assertEquals("80", ExcelEngine.evaluateFormula("=A2", sheet))
    }

    @Test
    fun formulaIfAndComparisons() {
        val sheet = sampleSheet()
        assertEquals("Pass", ExcelEngine.evaluateFormula("=IF(A2>=80,\"Pass\",\"Fail\")", sheet))
        assertEquals("Fail", ExcelEngine.evaluateFormula("=IF(A5>=80,\"Pass\",\"Fail\")", sheet))
        assertEquals("TRUE", ExcelEngine.evaluateFormula("=A2>80", sheet))
        assertEquals("FALSE", ExcelEngine.evaluateFormula("=A5<>60", sheet))
    }

    @Test
    fun evaluateSheetFormulasRecalculatesChains() {
        val sheet = ExcelSheetData(
            name = "Sheet1",
            rows = listOf(
                listOf("85", "10", ""),
                listOf("15", "20", "")
            ),
            formulas = mapOf("C1" to "=SUM(A1:A2)", "C2" to "=SUM(C1:B2)")
        )
        val recalced = ExcelEngine.evaluateSheetFormulas(sheet)
        // C1 = 85+15 = 100; C2 = 100+20 = 120 (chained through C1)
        assertEquals("100", recalced.rows[0][2])
        assertEquals("120", recalced.rows[1][2])
        assertEquals(2, recalced.formulas.size)
    }

    @Test
    fun xlsxRoundTripPreservesFormulasStylesAndCachedValues() {
        val sheet = ExcelSheetData(
            name = "Sheet1",
            rows = listOf(
                listOf("Item", "Qty", "Total"),
                listOf("Pen", "5", "25"),
                listOf("Book", "40", "80")
            ),
            formulas = mapOf("C2" to "=B2*5", "C3" to "=B3*2"),
            styles = mapOf(
                "A1" to ExcelCellStyle(bold = true, fillHex = "E6F4EE"),
                "C2" to ExcelCellStyle(bold = true, colorHex = "C62828")
            )
        )
        val tempFile = Files.createTempFile("test_sheet", ".xlsx").toFile()
        try {
            ExcelEngine.writeXlsxFile(listOf(sheet), tempFile)
            assertTrue(tempFile.exists() && tempFile.length() > 0)

            val read = ExcelEngine.readSpreadsheetFile(tempFile)
            assertEquals(1, read.size)
            val out = read[0]

            // Formulas survive the round trip
            assertEquals("=B2*5", out.formulas["C2"])
            assertEquals("=B3*2", out.formulas["C3"])
            // Cached display values are correct
            assertEquals("25", out.rows[1][2])
            assertEquals("80", out.rows[2][2])
            // Styles survive the round trip
            assertEquals(true, out.styles["A1"]?.bold == true)
            assertEquals("E6F4EE", out.styles["A1"]?.fillHex)
            assertEquals("C62828", out.styles["C2"]?.colorHex)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun invalidFormulaReturnsErrorNotCrash() {
        val sheet = sampleSheet()
        assertEquals("#ERR", ExcelEngine.evaluateFormula("=SUM((((", sheet))
        assertEquals("#NAME?", ExcelEngine.evaluateFormula("=FOOBAR(1)", sheet))
    }
}

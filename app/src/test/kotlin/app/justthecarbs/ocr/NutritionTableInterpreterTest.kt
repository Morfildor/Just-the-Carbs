package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionUnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

// Suite: geometry-first table interpretation
// Invariant: the TOTAL carbohydrate row's grams cell wins. A child nutrient's value can never be
// returned, however close it sits to the word "Carbohydrate", and a percent cell is never grams.
// Every fixture here has deliberately adversarial blockId/lineId vs. real geometry.
class NutritionTableInterpreterTest {

    private fun e(text: String, left: Int, top: Int, right: Int, bottom: Int, line: Int, block: Int = 0) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = block, lineId = line)

    private fun document(vararg elements: OcrElement): OcrDocument =
        OcrDocument(width = 800, height = 600, elements = elements.toList())

    private fun assertDecimal(expected: String, actual: BigDecimal?) {
        assertNotNull(actual)
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }

    private fun confidentValue(document: OcrDocument): BigDecimal {
        val reading = NutritionTableInterpreter.interpret(document).reading
        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        return (reading as LabelReading.Confident).candidate.value
    }

    @Test
    fun `same physical row split across ML Kit lines still reads the total`() {
        val value = confidentValue(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                // Label and value on the same baseline but different ML Kit lines and blocks.
                e("Carbohydrate", 40, 200, 240, 230, line = 1, block = 0),
                e("45", 350, 202, 390, 232, line = 9, block = 4),
                e("g", 395, 202, 410, 232, line = 9, block = 4),
            ),
        )

        assertDecimal("45", value)
    }

    @Test
    fun `different physical rows merged onto one ML Kit line still separate`() {
        val value = confidentValue(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                // ML Kit put all four on line 3. Geometry says two printed rows.
                e("Carbohydrate", 40, 200, 240, 230, line = 3),
                e("45", 350, 200, 390, 230, line = 3),
                e("of which sugars", 60, 260, 260, 290, line = 3),
                e("8", 350, 260, 390, 290, line = 3),
            ),
        )

        assertDecimal("45", value)
    }

    @Test
    fun `a sugars value physically closer to the carbohydrate label never wins`() {
        // The sugars cell is nearer the word "Carbohydrate" than the real total is. Row typing
        // decides before any proximity is measured, so proximity cannot rescue a child value.
        val value = confidentValue(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 235, line = 1),
                e("45", 350, 200, 390, 235, line = 1),
                e("of which sugars", 60, 240, 260, 268, line = 2),
                e("8", 350, 240, 390, 268, line = 2),
            ),
        )

        assertDecimal("45", value)
    }

    @Test
    fun `a dextrose child row is excluded and the total wins`() {
        val value = confidentValue(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("62", 350, 200, 390, 230, line = 1),
                e("Dextrose", 60, 260, 200, 290, line = 2),
                e("31", 350, 260, 390, 290, line = 2),
            ),
        )

        assertDecimal("62", value)
    }

    @Test
    fun `a two column table yields both the per-100 total and the serving figure`() {
        val report = NutritionTableInterpreter.interpret(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("per", 550, 100, 590, 130, line = 0),
                e("slice", 595, 100, 650, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("45", 350, 200, 390, 230, line = 1),
                e("16.2", 580, 200, 640, 230, line = 1),
            ),
        )

        assertTrue("a two-column table is not ambiguous", report.reading is LabelReading.Confident)
        assertDecimal("45", (report.reading as LabelReading.Confident).candidate.value)
        assertDecimal("16.2", report.servingCandidate?.carbsPerServing)
        assertEquals(PortionUnitKind.SLICE, report.servingCandidate?.descriptor?.kind)
    }

    @Test
    fun `a percent column is never selected as carbohydrate grams`() {
        val value = confidentValue(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("%RI", 620, 100, 670, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("45", 350, 200, 390, 230, line = 1),
                e("17", 630, 200, 670, 230, line = 1),
                e("Fat", 40, 260, 100, 290, line = 2),
                e("1.5", 350, 260, 390, 290, line = 2),
                e("2", 630, 260, 670, 290, line = 2),
            ),
        )

        assertDecimal("45", value)
    }

    @Test
    fun `a genuinely unresolvable layout is ambiguous rather than guessed`() {
        // Two per-100-g-aligned cells on the total row with nothing to separate them.
        val reading = NutritionTableInterpreter.interpret(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("45", 330, 200, 370, 230, line = 1),
                e("51", 380, 200, 420, 230, line = 1),
            ),
        ).reading

        assertTrue("expected Ambiguous, got $reading", reading is LabelReading.Ambiguous)
        val values = (reading as LabelReading.Ambiguous).candidates.map { it.value.toPlainString() }
        assertTrue(values.any { BigDecimal(it).compareTo(BigDecimal("45")) == 0 })
        assertTrue(values.any { BigDecimal(it).compareTo(BigDecimal("51")) == 0 })
    }

    @Test
    fun `no carbohydrate row at all is NotFound`() {
        val reading = NutritionTableInterpreter.interpret(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("Protein", 40, 200, 140, 230, line = 1),
                e("7.2", 350, 200, 390, 230, line = 1),
            ),
        ).reading

        assertEquals(LabelReading.NotFound, reading)
    }

    @Test
    fun `a per 100 ml table sets the ml basis`() {
        val reading = NutritionTableInterpreter.interpret(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("ml", 390, 100, 415, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("9.4", 350, 200, 400, 230, line = 1),
            ),
        ).reading

        assertTrue(reading is LabelReading.Confident)
        assertEquals(NutritionBasis.PER_100_ML, (reading as LabelReading.Confident).candidate.basis)
    }

    @Test
    fun `a serving column with no countable word yields a candidate with no descriptor`() {
        val report = NutritionTableInterpreter.interpret(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("per", 550, 100, 590, 130, line = 0),
                e("serving", 595, 100, 680, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("45", 350, 200, 390, 230, line = 1),
                e("16.2", 580, 200, 640, 230, line = 1),
            ),
        )

        assertDecimal("16.2", report.servingCandidate?.carbsPerServing)
        assertNull("'per serving' names no countable unit", report.servingCandidate?.descriptor)
    }

    @Test
    fun `a per-2-slices header keeps the explicit count`() {
        val report = NutritionTableInterpreter.interpret(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("per", 550, 100, 590, 130, line = 0),
                e("2", 595, 100, 615, 130, line = 0),
                e("slices", 620, 100, 690, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("45", 350, 200, 390, 230, line = 1),
                e("32.4", 600, 200, 660, 230, line = 1),
            ),
        )

        assertDecimal("32.4", report.servingCandidate?.carbsPerServing)
        assertDecimal("2", report.servingCandidate?.descriptor?.count)
        assertEquals(PortionUnitKind.SLICE, report.servingCandidate?.descriptor?.kind)
    }

    // ---- multiple total-carbohydrate rows (correction pass §3) --------------------------------
    //
    // A table can print the carbohydrate line twice: a bilingual package, a repeated header block,
    // or OCR reading one printed row as two. Taking `first()` resolved that by list order, which is
    // an arbitrary answer dressed up as a confident one. Every total row is now interpreted, and
    // only genuinely identical interpretations collapse.

    @Test
    fun `two total rows printing the same value collapse into one confident reading`() {
        val reading = NutritionTableInterpreter.interpret(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("45", 350, 200, 390, 230, line = 1),
                // The same figure printed again lower down — agreement, not ambiguity.
                e("Carbohydrate", 40, 300, 240, 330, line = 2),
                e("45", 350, 300, 390, 330, line = 2),
            ),
        ).reading

        assertTrue("agreeing rows are not ambiguous, got $reading", reading is LabelReading.Confident)
        assertDecimal("45", (reading as LabelReading.Confident).candidate.value)
        assertEquals(NutritionBasis.PER_100_G, reading.candidate.basis)
    }

    @Test
    fun `two total rows printing different values are ambiguous, never the first one`() {
        val reading = NutritionTableInterpreter.interpret(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("45", 350, 200, 390, 230, line = 1),
                e("Carbohydrate", 40, 300, 240, 330, line = 2),
                e("12", 350, 300, 390, 330, line = 2),
            ),
        ).reading

        assertTrue("conflicting rows must be Ambiguous, got $reading", reading is LabelReading.Ambiguous)
        val values = (reading as LabelReading.Ambiguous).candidates
            .map { it.value.stripTrailingZeros().toPlainString() }
            .toSet()
        assertEquals(setOf("45", "12"), values)
    }

    @Test
    fun `the same value under two different bases is ambiguous`() {
        // A dual-basis table where each printed total row sits under its own basis column. Both
        // readings are individually valid, so the app must ask rather than pick.
        val reading = NutritionTableInterpreter.interpret(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("per", 550, 100, 590, 130, line = 0),
                e("100", 595, 100, 635, 130, line = 0),
                e("ml", 640, 100, 665, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("45", 350, 200, 390, 230, line = 1),
                e("Carbohydrate", 40, 300, 240, 330, line = 2),
                e("45", 600, 300, 640, 330, line = 2),
            ),
        ).reading

        assertTrue("two bases must be Ambiguous, got $reading", reading is LabelReading.Ambiguous)
        val bases = (reading as LabelReading.Ambiguous).candidates.map { it.basis }.toSet()
        assertEquals(setOf(NutritionBasis.PER_100_G, NutritionBasis.PER_100_ML), bases)
    }

    @Test
    fun `a child row is still excluded when a second total row exists`() {
        val value = confidentValue(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("45", 350, 200, 390, 230, line = 1),
                e("of which sugars", 60, 250, 260, 280, line = 2),
                e("8", 350, 250, 390, 280, line = 2),
                e("Carbohydrate", 40, 300, 240, 330, line = 3),
                e("45", 350, 300, 390, 330, line = 3),
            ),
        )

        assertDecimal("45", value)
    }

    // ---- split percent tokens (correction pass §5) ----------------------------------------------

    @Test
    fun `a percent sign in its own OCR element still disqualifies the number beside it`() {
        // ML Kit emits "17" and "%" as two adjacent elements. Without row-level association the 17
        // stays eligible as a gram figure, which is how a %RI value becomes a carbohydrate answer.
        val value = confidentValue(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("30", 350, 200, 390, 230, line = 1),
                e("g", 395, 200, 410, 230, line = 1),
                e("17", 600, 200, 640, 230, line = 1),
                e("%", 645, 200, 665, 230, line = 1),
            ),
        )

        assertDecimal("30", value)
    }

    @Test
    fun `a percent sign fused into one OCR element still disqualifies it`() {
        val value = confidentValue(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("30", 350, 200, 390, 230, line = 1),
                e("g", 395, 200, 410, 230, line = 1),
                e("17%", 600, 200, 655, 230, line = 1),
            ),
        )

        assertDecimal("30", value)
    }

    @Test
    fun `a split percent cell never becomes a per-serving carbohydrate figure`() {
        // The consequence that makes this more than cosmetic. With the %RI cell sitting under a
        // "per slice" column, an undetected split percent is offered to the user as "1 slice =
        // 17 g carbs" and saved as a countable portion — a wrong figure with full confidence,
        // reached through exactly the workflow this pass exists to make work.
        val report = NutritionTableInterpreter.interpret(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("per", 580, 100, 620, 130, line = 0),
                e("slice", 625, 100, 690, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("30", 350, 200, 390, 230, line = 1),
                e("g", 395, 200, 410, 230, line = 1),
                e("17", 620, 200, 660, 230, line = 1),
                e("%", 665, 200, 685, 230, line = 1),
            ),
        )

        assertDecimal("30", (report.reading as LabelReading.Confident).candidate.value)
        assertNull(
            "a %RI cell is not a per-serving carbohydrate figure",
            report.servingCandidate,
        )
    }

    @Test
    fun `a distant percent sign does not disqualify an unrelated number`() {
        // The "%" belongs to a far-right column; the grams cell beside the label must survive.
        // Guards against the adjacency rule being widened into "any % anywhere on the row".
        val value = confidentValue(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("45", 350, 200, 390, 230, line = 1),
                e("%", 760, 200, 780, 230, line = 1),
            ),
        )

        assertDecimal("45", value)
    }

    // ---- inline per-100 basis on the nutrient row (correction pass §6) --------------------------

    @Test
    fun `an inline per 100 g on the carbohydrate row itself reads confidently`() {
        val reading = NutritionTableInterpreter.interpret(
            document(
                // No separate header row anywhere — the basis is printed inside the value row.
                e("Carbohydrate", 40, 200, 240, 230, line = 0),
                e("per", 260, 200, 300, 230, line = 0),
                e("100", 305, 200, 345, 230, line = 0),
                e("g", 350, 200, 365, 230, line = 0),
                e("45", 400, 200, 440, 230, line = 0),
                e("g", 445, 200, 460, 230, line = 0),
            ),
        ).reading

        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        assertDecimal("45", (reading as LabelReading.Confident).candidate.value)
        assertEquals(NutritionBasis.PER_100_G, reading.candidate.basis)
    }

    @Test
    fun `an inline per 100 ml on the carbohydrate row itself reads confidently`() {
        val reading = NutritionTableInterpreter.interpret(
            document(
                e("Carbohydrate", 40, 200, 240, 230, line = 0),
                e("per", 260, 200, 300, 230, line = 0),
                e("100", 305, 200, 345, 230, line = 0),
                e("ml", 350, 200, 375, 230, line = 0),
                e("4.5", 400, 200, 450, 230, line = 0),
                e("g", 455, 200, 470, 230, line = 0),
            ),
        ).reading

        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        assertDecimal("4.5", (reading as LabelReading.Confident).candidate.value)
        assertEquals(NutritionBasis.PER_100_ML, reading.candidate.basis)
    }

    @Test
    fun `the 100 in an inline per 100 g is never offered as the carbohydrate value`() {
        // The safety requirement of the inline-basis fix: "100" is part of the basis phrase, not a
        // quantity. It also clears the per-100 validator's ceiling, so nothing downstream stops it.
        val reading = NutritionTableInterpreter.interpret(
            document(
                e("Carbohydrate", 40, 200, 240, 230, line = 0),
                e("per", 260, 200, 300, 230, line = 0),
                e("100", 305, 200, 345, 230, line = 0),
                e("g", 350, 200, 365, 230, line = 0),
                e("45", 400, 200, 440, 230, line = 0),
                e("g", 445, 200, 460, 230, line = 0),
            ),
        ).reading

        val values = when (reading) {
            is LabelReading.Confident -> listOf(reading.candidate.value)
            is LabelReading.Ambiguous -> reading.candidates.map { it.value }
            LabelReading.NotFound -> emptyList()
        }
        assertTrue(
            "100 is part of the basis phrase, not a value: $values",
            values.none { it.compareTo(BigDecimal("100")) == 0 },
        )
    }

    @Test
    fun `an inline per 100 ml never offers its own 100 as the value`() {
        val reading = NutritionTableInterpreter.interpret(
            document(
                e("Carbohydrate", 40, 200, 240, 230, line = 0),
                e("per", 260, 200, 300, 230, line = 0),
                e("100", 305, 200, 345, 230, line = 0),
                e("ml", 350, 200, 375, 230, line = 0),
                e("4.5", 400, 200, 450, 230, line = 0),
                e("g", 455, 200, 470, 230, line = 0),
            ),
        ).reading

        val values = when (reading) {
            is LabelReading.Confident -> listOf(reading.candidate.value)
            is LabelReading.Ambiguous -> reading.candidates.map { it.value }
            LabelReading.NotFound -> emptyList()
        }
        assertTrue(
            "100 is part of the basis phrase, not a value: $values",
            values.none { it.compareTo(BigDecimal("100")) == 0 },
        )
    }
}

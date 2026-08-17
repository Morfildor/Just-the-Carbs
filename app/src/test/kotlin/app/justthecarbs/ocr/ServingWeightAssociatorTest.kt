package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

// Suite: serving-weight corroboration
// Invariant: a serving weight is adopted only when the table's own arithmetic reproduces the printed
// per-serving figure from it. The weight rescales every portion computed from it, so proximity to
// the right column is not enough evidence on its own — a wrong weight is a confidently wrong answer.
class ServingWeightAssociatorTest {

    @Test
    fun `the Kinder figures corroborate each other`() {
        // 53.5 g/100 g x 12.5 g = 6.6875, printed as 6.7. Exact equality would reject this correct
        // label, which is why the check has a rounding tolerance at all.
        assertTrue(
            ServingWeightAssociator.agreesWithTable(
                carbsPer100 = BigDecimal("53.5"),
                servingWeight = BigDecimal("12.5"),
                printedCarbsPerServing = BigDecimal("6.7"),
            ),
        )
    }

    @Test
    fun `a weight from the wrong column is rejected`() {
        // 25 g would predict 13.4 against a printed 6.7. No rounding explains that.
        assertFalse(
            ServingWeightAssociator.agreesWithTable(
                carbsPer100 = BigDecimal("53.5"),
                servingWeight = BigDecimal("25"),
                printedCarbsPerServing = BigDecimal("6.7"),
            ),
        )
    }

    @Test
    fun `a pack weight picked up from elsewhere on the label is rejected`() {
        assertFalse(
            "a 100 g net weight predicts the per-100 figure itself, not the serving",
            ServingWeightAssociator.agreesWithTable(
                carbsPer100 = BigDecimal("53.5"),
                servingWeight = BigDecimal("100"),
                printedCarbsPerServing = BigDecimal("6.7"),
            ),
        )
    }

    @Test
    fun `a label rounding its serving figure to a whole gram still agrees`() {
        // 61.9 g/100 g x 30 g = 18.57, printed as 19.
        assertTrue(
            ServingWeightAssociator.agreesWithTable(
                carbsPer100 = BigDecimal("61.9"),
                servingWeight = BigDecimal("30"),
                printedCarbsPerServing = BigDecimal("19"),
            ),
        )
    }

    @Test
    fun `a large serving agrees within proportional rounding`() {
        // 12.0 g/100 g x 450 g = 54.0, printed as 54.
        assertTrue(
            ServingWeightAssociator.agreesWithTable(
                carbsPer100 = BigDecimal("12.0"),
                servingWeight = BigDecimal("450"),
                printedCarbsPerServing = BigDecimal("54"),
            ),
        )
    }

    @Test
    fun `a factor-of-ten error is caught`() {
        assertFalse(
            "the failure a unit slip produces, and the one this check exists to catch",
            ServingWeightAssociator.agreesWithTable(
                carbsPer100 = BigDecimal("53.5"),
                servingWeight = BigDecimal("125"),
                printedCarbsPerServing = BigDecimal("6.7"),
            ),
        )
    }

    @Test
    fun `a multi-unit serving keeps the printed weight as the serving weight, not per unit`() {
        // "per 2 stuks (25 g)" means 25 g of serving containing 2 pieces, so weightOrVolume is 25
        // and amountPerUnit is 12.5. Scaling the printed weight by the count would store 50 g and
        // double every portion computed from it — an error that is invisible on a count-of-one
        // label, which is every other fixture here.
        val label = SlopedLabel(0.0).apply {
            row(200, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
            row(200, "per" to 560..600, "2" to 608..630, "stuks" to 638..710, block = 1, line = 0)
            row(234, "(25" to 570..630, "g)" to 638..680, block = 1, line = 1)
            row(300, "Koolhydraten" to 60..220, "/" to 228..238, "Glucides" to 246..350, block = 2, line = 0)
            row(300, "53,5" to 405..470, "g" to 478..495, block = 3, line = 0)
            // 53.5 x 25 / 100 = 13.375, printed 13,4.
            row(300, "13,4" to 580..645, "g" to 653..670, block = 4, line = 0)
        }.document()

        val descriptor = NutritionTableParser.parseWithDiagnostics(label).servingCandidate?.descriptor
        assertEquals(BigDecimal("2"), descriptor?.count?.stripTrailingZeros())
        assertEquals(
            "the printed weight is the serving's weight",
            BigDecimal("25"),
            descriptor?.weightOrVolume?.amount?.stripTrailingZeros(),
        )
        assertEquals(
            "and one unit is half of it",
            BigDecimal("12.5"),
            descriptor?.amountPerUnit?.amount?.stripTrailingZeros(),
        )
    }

    @Test
    fun `a weight is not adopted when the per-100 figure is ambiguous`() {
        // Two competing per-100 readings mean there is nothing settled to corroborate against, so
        // the descriptor keeps no weight and the direct-carbs path is used instead.
        val ambiguous = SlopedLabel(0.0).apply {
            row(200, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
            row(200, "per" to 560..605, "stuk" to 613..680, block = 1, line = 0)
            row(234, "(12,5" to 560..640, "g)" to 648..690, block = 1, line = 1)
            row(300, "Koolhydraten" to 60..220, "/" to 228..238, "Glucides" to 246..350, block = 2, line = 0)
            row(300, "53,5" to 405..470, "g" to 478..495, block = 3, line = 0)
            row(300, "6,7" to 580..635, "g" to 643..660, block = 4, line = 0)
            // A second, disagreeing carbohydrate row — a bilingual package printing the table twice
            // with one value misrecognised.
            row(350, "Kohlenhydrate" to 60..250, "Glucides" to 258..360, block = 5, line = 0)
            row(350, "58,1" to 405..470, "g" to 478..495, block = 6, line = 0)
        }.document()

        val report = NutritionTableParser.parseWithDiagnostics(ambiguous)
        assertTrue("expected ambiguity", report.reading is LabelReading.Ambiguous)
        assertTrue(
            "no weight may be adopted with nothing settled to check it against",
            report.servingCandidate?.descriptor?.weightOrVolume == null,
        )
    }

    @Test
    fun `a nutrient row containing a weight is never mistaken for the serving-weight line`() {
        // "Vetten 12,5 g" is a weight on a row, but the row says more than a weight, so it cannot
        // be the "(12,5 g)" sub-header.
        val label = SlopedLabel(0.0).apply {
            row(200, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
            row(200, "per" to 560..605, "stuk" to 613..680, block = 1, line = 0)
            row(234, "Vetten" to 560..640, "12,5" to 648..700, "g" to 708..725, block = 2, line = 0)
            row(300, "Koolhydraten" to 60..220, "/" to 228..238, "Glucides" to 246..350, block = 3, line = 0)
            row(300, "53,5" to 405..470, "g" to 478..495, block = 4, line = 0)
            row(300, "6,7" to 580..635, "g" to 643..660, block = 5, line = 0)
        }.document()

        val report = NutritionTableParser.parseWithDiagnostics(label)
        assertTrue(
            "a nutrient line was adopted as the serving weight",
            report.servingCandidate?.descriptor?.weightOrVolume == null,
        )
    }
}

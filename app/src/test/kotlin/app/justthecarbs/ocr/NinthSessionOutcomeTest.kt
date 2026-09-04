package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins what the device actually did on 2026-09-03, so a later test cannot pass for the wrong reason.
 *
 * This is the assertion half of [NinthSessionBaselineTest]. Without it a regression test claiming
 * "the white table's `2.8` now reaches the user" could pass because a fixture had quietly stopped
 * producing the failure it is supposed to be about — the fixture trap this repo has hit repeatedly.
 *
 * Everything here describes the **Pass A** document, which is what the bundles record. Where a
 * capture's correct reading came from Strategy B, that is asserted in [NinthSessionRegressionTest]
 * against the resolver instead, because the bundle preserves that pass's verdict but not its
 * document.
 */
class NinthSessionOutcomeTest {

    private fun reading(document: OcrDocument) = NutritionTableInterpreter.interpret(document).reading

    private fun rowKinds(document: OcrDocument): List<Pair<NutritionRowKind, String>> {
        val rows = LogicalRowBuilder.build(document)
        return RowClassifier.classifyAll(rows).mapIndexed { i, k -> k to rows[i].text }
    }

    // ---------------------------------------------------------------- the two that worked

    @Test
    fun `the cracker reads its printed 72 confidently from pass A`() {
        val confident = reading(NinthSessionFixtures.crackerAutoAdvance()) as LabelReading.Confident
        assertEquals(0, confident.candidate.value.compareTo(java.math.BigDecimal("72.0")))
        assertEquals(NutritionBasisRef.PER_100_G, confident.candidate.basis?.name)
    }

    @Test
    fun `the blue tub reads its printed 3 point 2 confidently from pass A`() {
        val confident = reading(NinthSessionFixtures.blueTubAutoAdvance()) as LabelReading.Confident
        assertEquals(0, confident.candidate.value.compareTo(java.math.BigDecimal("3.2")))
        assertEquals(NutritionBasisRef.PER_100_G, confident.candidate.basis?.name)
    }

    /**
     * Both working captures state their own scale, which is why they advanced.
     *
     * This is the property that separates them from the red label: the token itself carries a
     * decimal separator, so no pairing argument is needed.
     */
    @Test
    fun `both advancing captures establish their scale from their own token`() {
        listOf(
            NinthSessionFixtures.crackerAutoAdvance(),
            NinthSessionFixtures.blueTubAutoAdvance(),
        ).forEach { document ->
            val candidate = (reading(document) as LabelReading.Confident).candidate
            assertTrue(
                "expected the token's own separator to establish the scale",
                ScaleAmbiguity.check(document, candidate) is ScaleAmbiguity.Verdict.Established,
            )
        }
    }

    // ---------------------------------------------------------------- the white table

    /**
     * Pass A found the row, resolved the column, and still returned NotFound.
     *
     * The `g` printed after `2,8` came back as a `9`, so the value states no unit on a label that
     * prints them. That refusal is correct and this pass does not change it — the same misread hit
     * the **fat** row of the same capture (`Vetten, waarvan 4,8 9`), so it is a property of the
     * recognition, not something a carbohydrate-specific rule could or should repair.
     */
    @Test
    fun `the white table's pass A finds the row and the column and still refuses`() {
        val document = NinthSessionFixtures.whiteTableFirst()
        assertTrue("pass A must be NotFound", reading(document) is LabelReading.NotFound)

        val total = rowKinds(document).filter { it.first == NutritionRowKind.TOTAL_CARBOHYDRATE }
        assertEquals("exactly one total-carbohydrate row", 1, total.size)
        assertTrue(
            "the row carries the printed value with the g misread as 9: ${total.first().second}",
            total.first().second.contains("2.8"),
        )

        // The basis was never in doubt on this label.
        assertEquals(NutritionBasisRef.PER_100_G, StatedBasis.of(document)?.name)
    }

    @Test
    fun `the white table's second attempt fails the same way`() {
        val document = NinthSessionFixtures.whiteTableSecond()
        assertTrue(reading(document) is LabelReading.NotFound)
        assertEquals(NutritionBasisRef.PER_100_G, StatedBasis.of(document)?.name)
    }

    // ---------------------------------------------------------------- the green drink

    @Test
    fun `the green drink's pass A refuses on both attempts`() {
        assertTrue(reading(NinthSessionFixtures.greenDrinkNoReading()) is LabelReading.NotFound)
        assertTrue(reading(NinthSessionFixtures.greenDrinkStrategyB()) is LabelReading.NotFound)
    }

    /** The first attempt states its basis; the second lost the header's `l` and states none. */
    @Test
    fun `the green drink's stated basis differs between its two attempts`() {
        assertEquals(NutritionBasisRef.PER_100_ML, StatedBasis.of(NinthSessionFixtures.greenDrinkNoReading())?.name)
        assertNull(StatedBasis.of(NinthSessionFixtures.greenDrinkStrategyB()))
    }

    // ---------------------------------------------------------------- the red label control

    /**
     * The red label prints `7,2 g`. Pass A does not produce it, and nothing here may invent it.
     *
     * `7.2` survives in none of this capture's recognitions, so the assertion is about its
     * **absence** — a code path producing it would be fabricating it.
     */
    @Test
    fun `the red label never yields 7 point 2 from pass A`() {
        val document = NinthSessionFixtures.redLabelTwelve()
        assertTrue(reading(document) is LabelReading.NotFound)
        assertTrue(
            "no element may state the printed value; it is not in this recognition",
            document.elements.none { it.text.replace(',', '.').contains("7.2") },
        )
    }

    /** Nothing framed a nutrition table here, and a refusal is the right answer. */
    @Test
    fun `the ingredient underside and the sparse table both refuse`() {
        assertTrue(reading(NinthSessionFixtures.ingredientUnderside()) is LabelReading.NotFound)
        assertTrue(reading(NinthSessionFixtures.smallBlueTableSparse()) is LabelReading.NotFound)
    }

    /** The sparse capture recognised 13 elements in total — there was nothing to read. */
    @Test
    fun `the sparse capture recognised almost nothing`() {
        assertEquals(13, NinthSessionFixtures.smallBlueTableSparse().elements.size)
    }

    @Test
    fun `every fixture carries the device's own capture size`() {
        listOf(
            NinthSessionFixtures.greenDrinkNoReading(),
            NinthSessionFixtures.greenDrinkStrategyB(),
            NinthSessionFixtures.crackerAutoAdvance(),
            NinthSessionFixtures.whiteTableFirst(),
            NinthSessionFixtures.whiteTableSecond(),
            NinthSessionFixtures.blueTubAutoAdvance(),
            NinthSessionFixtures.ingredientUnderside(),
            NinthSessionFixtures.smallBlueTableSparse(),
            NinthSessionFixtures.redLabelTwelve(),
        ).forEach {
            assertEquals(1684, it.width)
            assertEquals(3648, it.height)
            assertNotNull(it.elements)
        }
    }
}

/** Basis names as strings, so this file needs no import of the domain enum. */
private object NutritionBasisRef {
    const val PER_100_G = "PER_100_G"
    const val PER_100_ML = "PER_100_ML"
}

package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Preconditions: the eleventh session's fixtures still reach the state the device recorded.
 *
 * Every case here asserts a fact the bundle printed, through the **real** classifiers. If one of
 * these fails, the fixture has drifted from the device and no conclusion drawn from it is worth
 * anything — fix the fixture before reading the regression tests that sit on top of it.
 */
class EleventhSessionBaselineTest {

    @Test
    fun `the cracker's Spanish row is a total carbohydrate row`() {
        val document = EleventhSessionFixtures.crackerSpanishRowSeparatorless()
        val rows = LogicalRowBuilder.build(document)
        val kinds = RowClassifier.classifyAll(rows)

        val totals = rows.indices.filter { kinds[it] == NutritionRowKind.TOTAL_CARBOHYDRATE }
        assertTrue(
            "expected at least one total-carbohydrate row, got " +
                rows.indices.joinToString { "${kinds[it]}:'${rows[it].text}'" },
            totals.isNotEmpty(),
        )
    }

    @Test
    fun `the cracker resolves exactly one per-100 column, and it is per 100 g`() {
        val document = EleventhSessionFixtures.crackerSpanishRowSeparatorless()
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)

        val perHundred = columns.filter {
            it.kind == NutritionColumnKind.PER_100_G || it.kind == NutritionColumnKind.PER_100_ML
        }
        assertEquals(
            "expected one per-100 column, got " + columns.joinToString { "${it.kind}@${it.centerX}" },
            1,
            perHundred.size,
        )
        assertEquals(NutritionColumnKind.PER_100_G, perHundred.single().kind)
    }

    @Test
    fun `the cracker reads 72 confidently, which is why it is a hazard and not a failure`() {
        val report = NutritionTableInterpreter.interpret(
            EleventhSessionFixtures.crackerSpanishRowSeparatorless(),
        )
        val reading = report.reading
        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        assertEquals(
            "72",
            (reading as LabelReading.Confident).candidate.value.stripTrailingZeros().toPlainString(),
        )
        assertEquals(NutritionBasis.PER_100_G, reading.candidate.basis)
    }

    @Test
    fun `the cracker's 72g is scale-unsupported, exactly as the bundle recorded`() {
        val document = EleventhSessionFixtures.crackerSpanishRowSeparatorless()
        val report = NutritionTableInterpreter.interpret(document)
        val candidate = (report.reading as LabelReading.Confident).candidate

        val verdict = ScaleAmbiguity.check(document, candidate)
        assertTrue(
            "expected Unsupported (a lone separatorless integer), got $verdict",
            verdict is ScaleAmbiguity.Verdict.Unsupported,
        )
    }

    @Test
    fun `the cracker's reading is refused by eligibility with no corroboration`() {
        val document = EleventhSessionFixtures.crackerSpanishRowSeparatorless()
        val report = NutritionTableInterpreter.interpret(document)
        val candidate = (report.reading as LabelReading.Confident).candidate

        val verdict = ReadingEligibility.evaluate(
            scale = ScaleAmbiguity.check(document, candidate),
            basis = app.justthecarbs.domain.CarbBasis.PerHundred(candidate.basis!!),
            corroborated = false,
        )
        assertTrue("expected Refused, got $verdict", verdict is ReadingEligibility.Verdict.Refused)
    }

    @Test
    fun `focused entry already knows the cracker's row and basis`() {
        // This is the finding that makes the routing defect a defect rather than a limitation: the
        // information the user needs was established all along. `FocusedAmountEntry` resolves a
        // target from this exact document — the app simply never offered it.
        val target = FocusedAmountEntry.of(EleventhSessionFixtures.crackerSpanishRowSeparatorless())
        assertNotNull("expected a focused-entry target", target)
        assertEquals(NutritionBasis.PER_100_G, target!!.basis)
        assertTrue(
            "expected the target row to name carbohydrate, got '${target.rowText}'",
            target.rowText.lowercase().contains("hidratos") ||
                target.rowText.lowercase().contains("koolhydraten"),
        )
    }
}

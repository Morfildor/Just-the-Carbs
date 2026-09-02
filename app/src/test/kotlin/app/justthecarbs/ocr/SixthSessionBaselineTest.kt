package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reproduces, on the JVM, exactly what the two truffle-sauce captures did on the phone.
 *
 * This class asserts the **device outcome**, not the desired one. It is the control that proves the
 * fixtures in [SixthSessionFixtures] really carry the failure - without it, a later test asserting
 * that `89` is suppressed could pass because the fixture never produced `89` in the first place,
 * which is the fixture trap this repo has now hit three times (the Dutch header, the soft-keyboard
 * geometry test, and the fifth session's own synthetic `per portion` header).
 *
 * The parse itself is unchanged by this pass and must stay unchanged: `89` is a well-formed number
 * on a correctly classified total-carbohydrate row under a correctly resolved `PER_100_ML` column.
 * Every content-based guard in the app passes it and every one of them is right to - a parser that
 * refused `89` would refuse a legitimate 89 g label too. What this pass changes is what the app
 * *does* with an unverified reading of that shape, never whether the parser produces it.
 */
class SixthSessionBaselineTest {

    private fun readingOf(document: OcrDocument): LabelReading =
        NutritionTableInterpreter.interpret(document).reading

    @Test
    fun `the conflicted capture parses the collapsed value from pass A`() {
        val reading = readingOf(SixthSessionFixtures.sauceConflictedRuns())
        val confident = reading as? LabelReading.Confident
            ?: error("expected Confident, was $reading")

        assertEquals(0, confident.candidate.value.compareTo(java.math.BigDecimal("89")))
        assertEquals(app.justthecarbs.domain.NutritionBasis.PER_100_ML, confident.candidate.basis)
    }

    @Test
    fun `the shared-scale capture parses the collapsed value from pass A`() {
        val reading = readingOf(SixthSessionFixtures.sauceSharedScaleCollapse())
        val confident = reading as? LabelReading.Confident
            ?: error("expected Confident, was $reading")

        assertEquals(0, confident.candidate.value.compareTo(java.math.BigDecimal("89")))
        assertEquals(app.justthecarbs.domain.NutritionBasis.PER_100_ML, confident.candidate.basis)
    }

    /**
     * The serving cell really does arrive fused, on both captures.
     *
     * This is load-bearing for the scale-ambiguity rule: the sibling column value is not a clean
     * `1,3` that the app could compare against, it is `13gk1%;` / `13gk19;` - the number, the unit
     * and the reference percentage welded together. A rule that required a cleanly parsed sibling
     * cell would find nothing here and would not block either capture.
     */
    @Test
    fun `the serving cell arrives fused with its unit and reference percentage`() {
        val fused = listOf(
            SixthSessionFixtures.sauceConflictedRuns() to "13gk1%;",
            SixthSessionFixtures.sauceSharedScaleCollapse() to "13gk19;",
        )
        fused.forEach { (document, token) ->
            assertTrue(
                "expected the fused serving token $token in the recognised elements",
                document.elements.any { it.text == token },
            )
        }
    }

    /**
     * The collapse is label-wide, which is why a ratio check cannot see it.
     *
     * Every one of these tokens is the printed value with its decimal separator missing. They are
     * asserted as *recognised text*, so this documents the recognizer's behaviour rather than any
     * app rule.
     */
    @Test
    fun `every value on the shared-scale capture lost its decimal separator`() {
        val texts = SixthSessionFixtures.sauceSharedScaleCollapse().elements.map { it.text }
        // printed 2,7 g fat -> '27'; 1,6 g salt -> '16'; 4,6 g sugars -> '46g'; 8,9 g carbs -> '89'
        listOf("27", "16", "46g", "89").forEach { token ->
            assertTrue("expected the collapsed token $token", texts.any { it.trim() == token })
        }
    }
}

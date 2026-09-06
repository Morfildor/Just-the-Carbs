package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The absorbed-fragment trim inside [NutrientRowSegments]'s greedy term matcher
 * (`nutrientTermsIn`'s `nameStart` walk), for `TOTAL_CARBOHYDRATE` -- the only kind it applies to.
 *
 * ## Why this exists
 *
 * The trim stops a `TOTAL_CARBOHYDRATE` term's span from absorbing the preceding nutrient's trailing
 * value into its own name box (`"vetzuren 6,4 koolhydraten 46"` must not make `vetzuren 6,4` part of
 * the carbohydrate term's geometry). This is the same real-world row shape `DeviceScanRegressionTest`
 * uses for `MergedTotalRowRecovery` (the bread label from `RealImageOcrTest` fixture 6), reused here
 * because it is a fixture already proven to reconstruct into one row exactly as a real photograph
 * does. There was previously no direct test of this branch despite it feeding [ScaleAmbiguity],
 * `NutritionTableInterpreter` and `RowClassifier` on the automatic-advance path.
 *
 * ## Why the trim is not also applied to CARBOHYDRATE_CHILD
 *
 * Widening the same trim to `CARBOHYDRATE_CHILD` spans was tried and measured against
 * `ProseActivationGateTest`: it changed which span length the greedy walk resolves earlier in the
 * same row, and made a one-row prose declaration wrongly satisfy `segmentsOf`'s linear-declaration
 * shape instead of falling through to the prose reader. See the KDoc at the trim's call site for the
 * full account. The analogous child-row hazard is real but unmeasured in this repo's corpus, and is
 * left as a known gap rather than re-introducing that regression.
 */
class NutrientRowSegmentsClauseTrimTest {

    private fun carbohydrateRow(): LogicalRow {
        val label = SlopedLabel(slopePercent = 0.0, glyphHeight = 30)
        label.row(
            400,
            "vetzuren" to 100..260,
            "6,4" to 270..330,
            "koolhydraten" to 340..580,
            "46" to 590..650,
            "waarvan" to 660..800,
            "suikers" to 810..920,
            "1,0" to 930..980,
        )
        return LogicalRowBuilder.build(label.document(width = 1125, height = 1320)).single()
    }

    @Test
    fun `precondition -- the row really does classify as a separable total-carbohydrate row`() {
        // Confirms the fixture models the hazard: if row reconstruction ever stopped merging these
        // clauses onto one row, the test below would pass for an uninteresting reason.
        assertEquals(NutritionRowKind.TOTAL_CARBOHYDRATE, RowClassifier.classify(carbohydrateRow()))
    }

    @Test
    fun `the total term's box excludes the preceding nutrient's value`() {
        val segment = NutrientRowSegments.totalCarbohydrateSegment(carbohydrateRow())
            ?: throw AssertionError("expected a separable total-carbohydrate segment")

        assertTrue(
            "the total segment must start at its own name (koolhydraten, x=340), not the " +
                "preceding fragment's value (vetzuren 6,4, starting at x=100): $segment",
            segment.startX >= 340,
        )
    }
}

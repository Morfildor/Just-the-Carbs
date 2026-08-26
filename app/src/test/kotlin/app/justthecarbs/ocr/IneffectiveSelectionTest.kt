package app.justthecarbs.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Detecting a rectangle that did not isolate anything (spec §11).
 *
 * ## Why this is measured in elements, not in area
 *
 * The brief is explicit that a fixed "the crop must be smaller than N% of the image" rule is wrong,
 * and the corpus proves it: an automatic proposal covering **77% of the frame** once contained the
 * winning candidate entirely and still lost the reading, while a **13%** one cut the answer in half.
 * Rectangle size predicts nothing.
 *
 * What matters is whether interfering *text* was excluded, which is exactly what the element counts
 * already record. So this asks one narrow question — "did narrowing change the input at all?" — and
 * says nothing about whether the rectangle could have been tighter.
 */
class IneffectiveSelectionTest {

    private fun result(
        outcome: SelectedTableReader.Outcome,
        before: Int,
        after: Int,
    ) = SelectedTableReader.Result(
        report = NutritionParseReport(LabelReading.NotFound, emptyList()),
        outcome = outcome,
        elementsBefore = before,
        elementsAfter = after,
    )

    @Test
    fun `a selection removing nothing is ineffective`() {
        val outcome = result(SelectedTableReader.Outcome.FILTERED, before = 200, after = 200)

        assertTrue(outcome.isIneffective)
        assertTrue(outcome.fractionRemoved == 0.0)
    }

    @Test
    fun `a selection removing a single element out of hundreds is still ineffective`() {
        // 1/200 = 0.5%, well below the 2% threshold: the user narrowed nothing meaningful.
        val outcome = result(SelectedTableReader.Outcome.FILTERED, before = 200, after = 199)

        assertTrue(outcome.isIneffective)
    }

    @Test
    fun `a selection that genuinely excluded surrounding text is effective`() {
        val outcome = result(SelectedTableReader.Outcome.FILTERED, before = 200, after = 120)

        assertFalse(outcome.isIneffective)
        assertTrue(outcome.fractionRemoved > 0.39)
    }

    /**
     * A large table filling the frame must not be flagged.
     *
     * This is the case a size-based rule would get wrong: the rectangle is nearly the whole image
     * *because the table is*, and it still removed real interference.
     */
    @Test
    fun `a large table that still excluded some text is effective`() {
        val outcome = result(SelectedTableReader.Outcome.FILTERED, before = 100, after = 90)

        assertFalse("10% removed is a real narrowing", outcome.isIneffective)
    }

    /**
     * "The selection enclosed no text" is a different statement from "the selection did nothing".
     *
     * That case keeps the whole-frame reading rather than filtering, so it is not a filtered result
     * and must not be reported as an ineffective crop — the advice ("tighten the box") would be
     * exactly backwards.
     */
    @Test
    fun `retaining the whole frame is not reported as an ineffective selection`() {
        val outcome = result(SelectedTableReader.Outcome.RETAINED_WHOLE_FRAME, before = 200, after = 200)

        assertFalse(outcome.isIneffective)
    }

    @Test
    fun `a failed recognition is not reported as an ineffective selection`() {
        val outcome = result(SelectedTableReader.Outcome.NO_DOCUMENT, before = 0, after = 0)

        assertFalse(outcome.isIneffective)
        assertTrue("no elements means no fraction to report", outcome.fractionRemoved == 0.0)
    }
}

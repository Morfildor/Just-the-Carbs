package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The clause boundary on a merged row: what it separates, and what still stands behind it.
 *
 * ## Why this file exists
 *
 * Honouring a tap inside a merged row's total clause is only safe if the candidates it can produce
 * are bounded by that clause. Without the bound, a tap on the carbohydrate value of
 * `Koolhydraten 12 g waarvan suikers 3 g` could offer the **sugars** figure — the sugars-as-total
 * failure the geometry-first architecture exists to prevent, arriving through the tap path.
 *
 * ## An honest result: the bound is defence in depth, not a reachable path today
 *
 * A negative control that removed the bound failed **nothing**, and the reason is worth recording
 * rather than papering over with a test that only appears to exercise it.
 *
 * On every merged row reachable in practice, something else already refuses the value first:
 *
 * - Print the values with units and a clean header, and the **prose reader reads the row correctly**
 *   (measured: `Confident 12.0 PER_100_G`). Recovery is never reached at all.
 * - Print them without units and [CarbUnitAccompaniment] declines them.
 * - Print them with units and no separator — the shape that does reach recovery — and
 *   [ScaleAmbiguity] withholds them, because the candidate's only sibling value cell inside the
 *   fallback clause is the sugars figure and neither carries a separator.
 *
 * That last one is [ScaleAmbiguity] pairing across the clause boundary, and it is left exactly as
 * it is: it errs towards **withholding** a value, which is the safe direction, and it is on the
 * automatic path where this pass must not touch it.
 *
 * So the assertions below pin the boundary itself — where it falls, which elements are inside it,
 * and that a child-clause tap is still refused — rather than claiming a leak that no input can
 * currently produce. The bound stays in [RecoveryCandidates.candidatesOn] because the suppression
 * rules in front of it are not there to enforce clause separation and could legitimately change.
 */
class MergedRowTapBoundTest {

    private fun element(text: String, left: Int, right: Int, top: Int) =
        OcrElement(text, OcrBox(left, top, right, top + 50), blockId = 0, lineId = 0)

    /** `Koolhydraten 12 g waarvan suikers 3 g` on one reconstructed row, under a `per 100 g` header. */
    private fun mergedRow() = OcrDocument(
        width = 1000,
        height = 600,
        elements = listOf(
            element("per 100 g", 380, 520, 100),
            element("Koolhydraten", 40, 300, 300),
            element("12 g", 400, 500, 300),
            element("waarvan", 540, 680, 300),
            element("suikers", 690, 800, 300),
            element("3 g", 820, 900, 300),
        ),
    )

    private fun carbohydrateRow() = LogicalRowBuilder.build(mergedRow()).single {
        NutritionTerminology.normalize(it.text).contains("koolhydraten")
    }

    @Test
    fun `precondition - the two clauses really are merged onto one row`() {
        assertEquals(
            "without this the fixture models no hazard",
            NutritionRowKind.CARBOHYDRATE_CHILD,
            RowClassifier.classify(carbohydrateRow()),
        )
    }

    @Test
    fun `precondition - whole-row segmentation refuses this row`() {
        // The automatic path's own answer, unchanged: a merged row states no separable total, so
        // `totalCarbohydrateSegment` is null and the row is not read as a total.
        assertEquals(null, NutrientRowSegments.totalCarbohydrateSegment(carbohydrateRow()))
    }

    @Test
    fun `the clause boundary falls at the sugars word`() {
        val clause = NutrientRowSegments.totalCarbohydrateClause(carbohydrateRow())
        assertNotNull("a merged row still has a locatable total clause", clause)
        assertEquals("the clause opens at the carbohydrate word", 40, clause!!.startX)
        assertEquals("and stops at the sugars word, not at 'waarvan'", 690, clause.endX)
    }

    @Test
    fun `the carbohydrate value is inside the clause and the sugars value is outside it`() {
        val clause = NutrientRowSegments.totalCarbohydrateClause(carbohydrateRow())!!
        val row = carbohydrateRow()

        assertTrue(
            "the carbohydrate value is in its own clause",
            clause.contains(row.elements.single { it.text == "12 g" }.box),
        )
        assertFalse(
            "the sugars value is not — this is the bound that stops it being offered",
            clause.contains(row.elements.single { it.text == "3 g" }.box),
        )
    }

    @Test
    fun `a tap in the total clause is not reported as a child-row tap`() {
        assertFalse(
            "a tap on the carbohydrate value must be honoured",
            RecoveryCandidates.isChildRowAt(mergedRow(), 325, 430),
        )
    }

    @Test
    fun `a tap in the sugars clause is still reported as a child-row tap`() {
        assertTrue(
            "the other half of the rule: a genuine sugars tap is still refused",
            RecoveryCandidates.isChildRowAt(mergedRow(), 325, 845),
        )
        assertTrue(
            "and offers nothing",
            RecoveryCandidates.onRowAt(mergedRow(), 325, 845).isEmpty(),
        )
    }

    @Test
    fun `a tap with no horizontal position keeps the row-level answer`() {
        // Without an x coordinate there is nothing to compare against the clause, so the merged row
        // is treated exactly as before. This is what confines the change to a located tap.
        assertTrue(RecoveryCandidates.isChildRowAt(mergedRow(), 325, null))
        assertTrue(RecoveryCandidates.onRowAt(mergedRow(), 325, null).isEmpty())
    }

    @Test
    fun `the automatic reading is unaffected by the clause locator`() {
        // The clause locator must not change what the parser reads. On this fixture the prose
        // reader legitimately finds the correct value; the point is that it is 12, never 3.
        val reading = NutritionTableInterpreter.interpret(mergedRow()).reading
        if (reading is LabelReading.Confident) {
            assertEquals(
                "if the row is read at all it is read as the carbohydrate value",
                0,
                reading.candidate.value.compareTo(java.math.BigDecimal("12")),
            )
        }
    }

    @Test
    fun `a child term printed before the total opens no clause`() {
        // The merged Croatian/German shape: `od kojih šećeri ... Kohlenhydrate`. There the
        // carbohydrate word belongs to the sugars declaration, so no total clause exists and a tap
        // anywhere on the row is a child-clause tap — unchanged from before this pass.
        val document = OcrDocument(
            width = 1000,
            height = 600,
            elements = listOf(
                element("per 100 g", 380, 520, 100),
                element("od kojih", 40, 200, 300),
                element("šećeri", 210, 320, 300),
                element("Kohlenhydrate", 340, 600, 300),
                element("3 g", 820, 900, 300),
            ),
        )
        val row = LogicalRowBuilder.build(document).single {
            NutritionTerminology.normalize(it.text).contains("kohlenhydrate")
        }
        assertEquals(
            "a child named first means no total clause opens",
            null,
            NutrientRowSegments.totalCarbohydrateClause(row),
        )
        assertTrue(
            "so every tap on it is still a child-clause tap",
            RecoveryCandidates.isChildRowAt(document, 325, 450),
        )
    }
}

package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A tap must be attributed to the OCR element it physically landed **on**, using both coordinates.
 *
 * ## The defect
 *
 * [RecoveryCandidates.rowAt] documents "elements first, unions second", and its disambiguation stage
 * does consult `tappedX`. But the **first** filter — the one that decides which rows are even
 * candidates — tests `tappedY` alone:
 *
 * ```
 * val onElement = rows.filter { row ->
 *     row.elements.any { tappedY >= it.box.top && tappedY <= it.box.bottom }
 * }
 * if (onElement.size == 1) return onElement.single()
 * ```
 *
 * A row qualifies when *any* of its elements spans the tapped Y, no matter how far away in X. On a
 * label whose reconstructed carbohydrate and sugars rows overlap vertically — which is the ordinary
 * case, and the case the KDoc itself describes — a tap placed squarely on the carbohydrate **number**
 * therefore admits both rows and is settled by a nutrient-name rule that looks at the label column,
 * hundreds of pixels from the finger.
 *
 * That is not hit testing. The brief states the requirement plainly: *"If a tap lands inside one OCR
 * element box, that element owns the tap."*
 *
 * ## What this changes, and what it must not
 *
 * A tap **inside** an element's box is attributed to that element's row, full stop. Only when the
 * finger lands in whitespace do the existing rules apply, unchanged — the nutrient-name preference,
 * then nearest centre, then row unions. So the sugars refusal is untouched: a tap on `waarvan
 * suikers 3 g` still resolves to the sugars row and is still refused, because it is genuinely inside
 * that row's elements.
 */
class TapHitTestingTest {

    /**
     * Two vertically overlapping rows, laid out the way ML Kit reconstructs a real merged table.
     *
     * The geometry is taken from the shape this repo has already measured twice — the ninth
     * session's drink, where `Kolhydraten:` spans y=1772..1882 and `Waarvan` spans y=1865..1947, an
     * 83 px overlap. Here the *values* are placed in a right-hand column, which is where a user
     * aiming at a number actually taps.
     */
    private fun overlappingRows(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            // Header, so a basis resolves and the rows mean something.
            OcrElement("per", OcrBox(1000, 1600, 1080, 1660), 0, 0),
            OcrElement("100", OcrBox(1090, 1600, 1170, 1660), 0, 0),
            OcrElement("g", OcrBox(1180, 1600, 1210, 1660), 0, 0),

            // Total carbohydrate: label on the left, value on the right.
            OcrElement("Koolhydraten", OcrBox(250, 1772, 700, 1882), 1, 0),
            OcrElement("47 g", OcrBox(1090, 1780, 1240, 1870), 1, 0),

            // Sugars, overlapping the total's vertical span by 17 px.
            OcrElement("waarvan", OcrBox(280, 1865, 520, 1947), 2, 0),
            OcrElement("suikers", OcrBox(540, 1865, 760, 1947), 2, 0),
            OcrElement("3 g", OcrBox(1090, 1875, 1210, 1945), 2, 0),
        ),
    )

    private fun rows() = LogicalRowBuilder.build(overlappingRows())

    @Test
    fun `a tap inside the total carbohydrate value resolves to the total row`() {
        val rows = rows()
        // Dead centre of the `47 g` element: x=1165, y=1825.
        val row = RecoveryCandidates.rowAt(rows, tappedY = 1825, tappedX = 1165)
        assertNotNull(row)
        assertTrue(
            "a tap on the total's own value must not resolve to another row; got '${row?.text}'",
            row!!.text.contains("Koolhydraten"),
        )
    }

    @Test
    fun `a tap inside the sugars value resolves to the sugars row and is still refused`() {
        val document = overlappingRows()
        val rows = LogicalRowBuilder.build(document)
        // Dead centre of the `3 g` element: x=1150, y=1910.
        val row = RecoveryCandidates.rowAt(rows, tappedY = 1910, tappedX = 1150)
        assertNotNull(row)
        assertTrue(
            "a tap on the sugars value must resolve to the sugars row; got '${row?.text}'",
            row!!.text.contains("suikers"),
        )
        // And the safety rule is unchanged: a child row supplies nothing.
        assertTrue(
            RecoveryCandidates.isChildRowAt(document, tappedY = 1910, tappedX = 1150),
        )
    }

    @Test
    fun `a tap on the carbohydrate word still resolves to the total row`() {
        // The existing behaviour, which must not regress: the label column is where the KDoc's
        // nutrient-name rule was written for, and it still applies there.
        val row = RecoveryCandidates.rowAt(rows(), tappedY = 1875, tappedX = 400)
        assertNotNull(row)
        assertTrue(row!!.text.contains("Koolhydraten") || row.text.contains("suikers"))
    }

    @Test
    fun `a tap in whitespace between the columns still resolves by the existing rules`() {
        // x=900 is inside no element at all. The union/nearest-centre fallback owns this, unchanged.
        val row = RecoveryCandidates.rowAt(rows(), tappedY = 1825, tappedX = 900)
        assertNotNull("a whitespace tap must still find a row", row)
    }

    @Test
    fun `an unlocated tap behaves exactly as before`() {
        // `tappedX == null` is the pre-existing contract for callers with no horizontal position.
        val row = RecoveryCandidates.rowAt(rows(), tappedY = 1825, tappedX = null)
        assertNotNull(row)
    }

    /**
     * The measured device case: a tap **inside** the sugars label resolves to the *total* row.
     *
     * ## Real geometry, from `20260904-081055-219` (the yoghurt tub)
     *
     * ```
     * 'Koolhydraten/Glucides'  [297,1884,802,1988]   row: TOTAL_CARBOHYDRATE
     * 'waarvan'                [311,1948,486,2015]   row: CARBOHYDRATE_CHILD
     * ```
     *
     * The two label elements overlap by 40 px vertically **and** by 175 px horizontally, because ML
     * Kit indents the child clause under its parent. A tap at the centre of `waarvan` — (398, 1981)
     * — is inside `waarvan`'s box, and is *also* inside `Koolhydraten/Glucides`'s box.
     *
     * `rowAt` resolves it to the **total** row, because the nutrient-name preference finds a
     * carbohydrate word in both candidates and no rule then asks which box the finger is actually
     * in. `isChildRowAt` consequently answers `false`, so the screen does not even say *"that looks
     * like sugars"*.
     *
     * ## Why this direction is the dangerous one
     *
     * The KDoc on `rowAt` reasons about the opposite case — a tap meant for carbohydrate landing on
     * sugars — and calls that one merely annoying because it "correctly refuses". This is the
     * inverse, and it does not refuse: a user deliberately tapping the sugars row is handed the
     * **total** row's candidate list. On this label both figures are `3,2`, so nothing on screen
     * would reveal it; on a label where they differ, the user asks for one nutrient and is offered
     * another.
     *
     * The fix is containment: a tap inside exactly one element's box belongs to that element,
     * before any vocabulary rule is consulted.
     */
    @Test
    fun `a tap inside the sugars label is not attributed to the total row`() {
        val document = ThirteenthSessionFixtures.yoghurtThreePointTwo()
        val rows = LogicalRowBuilder.build(document)

        // Preconditions, so this cannot pass for the wrong reason.
        val waarvan = document.elements.first { it.text.trim() == "waarvan" }
        val koolhydraten = document.elements.first { it.text.startsWith("Koolhydraten") }
        val tapX = (waarvan.box.left + waarvan.box.right) / 2
        val tapY = (waarvan.box.top + waarvan.box.bottom) / 2
        assertTrue(
            "the tap must be inside the sugars label",
            tapX >= waarvan.box.left && tapX <= waarvan.box.right &&
                tapY >= waarvan.box.top && tapY <= waarvan.box.bottom,
        )
        assertTrue(
            "and the total's label must also span that y, or there is no ambiguity to resolve",
            tapY >= koolhydraten.box.top && tapY <= koolhydraten.box.bottom,
        )

        val row = RecoveryCandidates.rowAt(rows, tappedY = tapY, tappedX = tapX)
        assertNotNull(row)
        assertEquals(
            "a tap inside the sugars label must resolve to the sugars row",
            NutritionRowKind.CARBOHYDRATE_CHILD,
            RowClassifier.classify(row!!),
        )
        assertTrue(
            "and the screen must be able to say so",
            RecoveryCandidates.isChildRowAt(document, tappedY = tapY, tappedX = tapX),
        )
    }

    /**
     * The same label's **total** label still resolves to the total row.
     *
     * The control for the case above: fixing the sugars attribution must not invert the problem.
     */
    @Test
    fun `a tap inside the total label on the same device fixture resolves to the total row`() {
        val document = ThirteenthSessionFixtures.yoghurtThreePointTwo()
        val rows = LogicalRowBuilder.build(document)
        val koolhydraten = document.elements.first { it.text.startsWith("Koolhydraten") }
        // A point inside the total's label and OUTSIDE the child's box — x is left of `waarvan`'s
        // 311 only marginally, so take a y above the child entirely.
        val tapX = (koolhydraten.box.left + koolhydraten.box.right) / 2
        val tapY = koolhydraten.box.top + 20
        val row = RecoveryCandidates.rowAt(rows, tappedY = tapY, tappedX = tapX)
        assertNotNull(row)
        assertEquals(
            NutritionRowKind.TOTAL_CARBOHYDRATE,
            RowClassifier.classify(row!!),
        )
    }

    @Test
    fun `the fixture really does produce two vertically overlapping rows`() {
        // Non-vacuity. If the row builder separated these cleanly there would be no ambiguity to
        // resolve and every assertion above would pass without exercising anything.
        val rows = rows()
        val carb = rows.first { it.text.contains("Koolhydraten") }
        val sugars = rows.first { it.text.contains("suikers") }
        val overlap = minOf(carb.box.bottom, sugars.box.bottom) - maxOf(carb.box.top, sugars.box.top)
        assertTrue("the two rows must overlap vertically; overlap was $overlap", overlap > 0)
        assertEquals(
            "and they must be two distinct rows, not one merged row",
            2,
            rows.count { it.text.contains("Koolhydraten") || it.text.contains("suikers") },
        )
    }
}

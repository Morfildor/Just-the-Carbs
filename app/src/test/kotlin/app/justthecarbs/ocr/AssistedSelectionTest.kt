package app.justthecarbs.ocr

import app.justthecarbs.domain.CarbBasis
import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * User-assisted row and value selection (spec §17, §18, §25 "Assisted row/value").
 *
 * The central safety claim: tapping the carbohydrate row must not be able to hand back the sugars
 * row's number. That is the exact failure mode the whole geometry-first rewrite exists to prevent,
 * and an assisted path that reintroduced it would be worse than no assisted path — the user would
 * reasonably trust a value they believe they selected themselves.
 *
 * ### Moved from `AssistedSelection` to [RecoveryCandidates] (2026-09-01, third phone session)
 *
 * These rules used to be enforced by `AssistedSelection`, which returned bare numbers that a later
 * screen attached a basis to. That two-step shape is what produced `1.3 g / 100 ml` from a figure
 * printed per 250 ml, so the object was replaced by one that only ever emits basis-complete
 * readings. **Every rule below is retained**; two are now stricter, and each says so where it is
 * asserted.
 */
class AssistedSelectionTest {

    /**
     * A two-row label with the hazard that matters: a total and a child, printed close together,
     * where the child's value is the one a sloppy implementation would pick up.
     */
    private fun label() = SlopedLabel(slopePercent = 0.0).apply {
        row(50, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
        row(190, "Koolhydraten" to 60..220, "53,5" to 380..450, "g" to 458..475, block = 1, line = 0)
        row(260, "waarvan" to 80..170, "suikers" to 178..250, "47,6" to 380..450, block = 2, line = 0)
    }.document(width = 1200, height = 400)

    // ---- tapping a row -------------------------------------------------------------------------

    /** THE safety test: the total row's tap yields the total's number and not the child's. */
    @Test
    fun `tapping the carbohydrate row cannot return the sugars value`() {
        val document = label()

        val candidates = RecoveryCandidates.onRowAt(document, tappedY = 195)

        val values = candidates.map { it.reading.amount.stripTrailingZeros().toPlainString() }
        assertTrue("expected the total's own value, got $values", values.contains("53.5"))
        assertFalse("the sugars figure must not be reachable from the total row", values.contains("47.6"))
    }

    /**
     * And the converse, so the restriction is genuinely positional rather than value-based.
     *
     * **Stricter than before.** The old behaviour returned the sugars row's own number when the user
     * tapped it; the new behaviour returns nothing and reports the row as a child, so the screen can
     * say *"This looks like sugars — tap the total carbohydrate row instead."* A child nutrient can
     * never supply the total, and the tap says which row the user meant, not what that row says.
     */
    @Test
    fun `tapping the sugars row offers nothing and is reported as a child row`() {
        val document = label()

        assertTrue(RecoveryCandidates.isChildRowAt(document, tappedY = 265))
        assertEquals(
            emptyList<RecoveryCandidates.Candidate>(),
            RecoveryCandidates.onRowAt(document, tappedY = 265),
        )
    }

    @Test
    fun `the tapped row's text is echoed back so the user can check what they hit`() {
        val text = RecoveryCandidates.rowTextAt(label(), tappedY = 195)

        assertNotNull(text)
        assertTrue("expected the row's own text, got '$text'", text!!.contains("Koolhydraten"))
    }

    @Test
    fun `a tap that lands on no row yields nothing`() {
        val document = label()

        assertEquals(
            emptyList<RecoveryCandidates.Candidate>(),
            RecoveryCandidates.onRowAt(document, tappedY = 395),
        )
        assertEquals(null, RecoveryCandidates.rowTextAt(document, tappedY = 395))
    }

    // ---- what may be offered at all ------------------------------------------------------------

    /**
     * The `0gjikovi` hazard, in the assisted path.
     *
     * ML Kit read Slovenian "Ogljikovi" with a leading zero, which once became a confident-looking
     * carbohydrate value. The automatic path closed this by requiring a value cell to stand alone.
     * The assisted path must not offer it either — a user tapping a word is not choosing a number,
     * and presenting one would invite exactly the wrong confirmation.
     */
    @Test
    fun `a digit embedded in a word is never offered as a number`() {
        val document = SlopedLabel(slopePercent = 0.0).apply {
            row(50, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
            row(100, "0gjikovi" to 60..220, "hidrati" to 230..320, block = 1, line = 0)
        }.document(width = 800, height = 300)

        val texts = RecoveryCandidates.of(document).map { it.rawText }

        assertFalse("'0gjikovi' must not be offered as the number 0", texts.any { it.contains("gjikovi") })
    }

    /** A number carrying its printed unit is still one number. */
    @Test
    fun `a value with a unit suffix is recognised as its number`() {
        val document = SlopedLabel(slopePercent = 0.0).apply {
            row(50, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
            row(100, "Koolhydraten" to 60..220, "53,5g" to 380..460, block = 1, line = 0)
        }.document(width = 800, height = 300)

        val candidates = RecoveryCandidates.of(document)

        assertEquals(1, candidates.size)
        assertEquals(0, candidates.first().reading.amount.compareTo(BigDecimal("53.5")))
        assertEquals(CarbBasis.PerHundred(NutritionBasis.PER_100_G), candidates.first().reading.basis)
    }

    @Test
    fun `a null document yields nothing rather than throwing`() {
        assertEquals(emptyList<RecoveryCandidates.Candidate>(), RecoveryCandidates.of(null))
        assertEquals(emptyList<RecoveryCandidates.Candidate>(), RecoveryCandidates.onRowAt(null, 100))
        assertEquals(null, RecoveryCandidates.rowTextAt(null, 100))
        assertFalse(RecoveryCandidates.isChildRowAt(null, 100))
    }

    /**
     * Recognizer confidence must never filter what the user may choose.
     *
     * **Retained deliberately.** The user is looking at the printed package; a number the recognizer
     * was unsure about is exactly the one they are best placed to confirm, and hiding it would
     * reintroduce the judgement the assisted path exists to avoid. Confidence stays a diagnostic.
     */
    @Test
    fun `a low-confidence number is still offered for the user to judge`() {
        val document = OcrDocument(
            width = 800,
            height = 300,
            elements = listOf(
                OcrElement("per", OcrBox(380, 40, 430, 70), blockId = 0, lineId = 0),
                OcrElement("100", OcrBox(438, 40, 495, 70), blockId = 0, lineId = 0),
                OcrElement("g", OcrBox(503, 40, 520, 70), blockId = 0, lineId = 0),
                OcrElement("Koolhydraten", OcrBox(60, 90, 220, 120), blockId = 1, lineId = 0),
                OcrElement("53,5", OcrBox(380, 90, 460, 120), blockId = 1, lineId = 0, confidence = 0.15f),
                OcrElement("g", OcrBox(468, 90, 490, 120), blockId = 1, lineId = 0),
            ),
        )

        val candidates = RecoveryCandidates.of(document)

        assertEquals(1, candidates.size)
        assertEquals(0, candidates.first().reading.amount.compareTo(BigDecimal("53.5")))
    }
}

package app.justthecarbs.ocr

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

        val candidates = AssistedSelection.candidatesOnRowAt(document, tappedY = 195)

        val values = candidates.map { it.value.stripTrailingZeros().toPlainString() }
        assertTrue("expected the total's own value, got $values", values.contains("53.5"))
        assertFalse("the sugars figure must not be reachable from the total row", values.contains("47.6"))
    }

    /** And the converse, so the restriction is genuinely positional rather than value-based. */
    @Test
    fun `tapping the sugars row returns only the sugars value`() {
        val document = label()

        val values = AssistedSelection.candidatesOnRowAt(document, tappedY = 265)
            .map { it.value.stripTrailingZeros().toPlainString() }

        assertTrue(values.contains("47.6"))
        assertFalse(values.contains("53.5"))
    }

    @Test
    fun `the tapped row's text is echoed back so the user can check what they hit`() {
        val text = AssistedSelection.rowTextAt(label(), tappedY = 195)

        assertNotNull(text)
        assertTrue("expected the row's own text, got '$text'", text!!.contains("Koolhydraten"))
        assertTrue(text.contains("53,5"))
    }

    @Test
    fun `tapping empty space yields no candidates and no row`() {
        val document = label()

        assertEquals(emptyList<AssistedSelection.NumericCandidate>(),
            AssistedSelection.candidatesOnRowAt(document, tappedY = 395))
        assertEquals(null, AssistedSelection.rowTextAt(document, tappedY = 395))
    }

    // ---- tapping a number ----------------------------------------------------------------------

    @Test
    fun `numeric candidates include every value-shaped token`() {
        val values = AssistedSelection.numericCandidates(label())
            .map { it.value.stripTrailingZeros().toPlainString() }

        assertTrue(values.contains("53.5"))
        assertTrue(values.contains("47.6"))
        assertTrue("the basis header's 100 is a number the user may need", values.contains("100"))
    }

    /**
     * The `0gjikovi` hazard must not reopen here.
     *
     * ML Kit read Slovenian "Ogljikovi" as "0gjikovi"; that leading zero once became a legitimate-
     * looking carbohydrate value. The automatic path closed this by requiring a value cell to stand
     * alone. The assisted path must not offer it either — a user tapping a word is not choosing a
     * number, and presenting one would invite exactly the wrong confirmation.
     */
    @Test
    fun `a digit embedded in a word is never offered as a number`() {
        val document = SlopedLabel(slopePercent = 0.0).apply {
            row(100, "0gjikovi" to 60..220, "hidrati" to 230..320, block = 0, line = 0)
        }.document(width = 800, height = 300)

        val values = AssistedSelection.numericCandidates(document).map { it.text }

        assertFalse("'0gjikovi' must not be offered as the number 0", values.any { it.contains("gjikovi") })
        assertTrue(values.isEmpty())
    }

    /** A number carrying its printed unit is still one number. */
    @Test
    fun `a value with a unit suffix is recognised as its number`() {
        val document = SlopedLabel(slopePercent = 0.0).apply {
            row(100, "53,5g" to 380..460, block = 0, line = 0)
        }.document(width = 800, height = 300)

        val candidates = AssistedSelection.numericCandidates(document)

        assertEquals(1, candidates.size)
        assertEquals(0, candidates.first().value.compareTo(BigDecimal("53.5")))
    }

    @Test
    fun `a null document yields nothing rather than throwing`() {
        assertEquals(emptyList<AssistedSelection.NumericCandidate>(),
            AssistedSelection.numericCandidates(null))
        assertEquals(emptyList<AssistedSelection.NumericCandidate>(),
            AssistedSelection.candidatesOnRowAt(null, 100))
        assertEquals(null, AssistedSelection.rowTextAt(null, 100))
    }

    /** Confidence rides along for diagnostics; it must never filter what the user may choose. */
    @Test
    fun `a low-confidence number is still offered for the user to judge`() {
        val document = OcrDocument(
            width = 800,
            height = 300,
            elements = listOf(
                OcrElement("53,5", OcrBox(380, 90, 460, 120), blockId = 0, lineId = 0, confidence = 0.15f),
            ),
        )

        val candidates = AssistedSelection.numericCandidates(document)

        assertEquals(1, candidates.size)
        assertEquals(0.15f, candidates.first().confidence!!, 0.001f)
    }
}

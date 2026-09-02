package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [CarbUnitAccompaniment] against the tokens a real device actually produced.
 *
 * Every fixture below is copied verbatim, with its geometry, from the five-capture evidence bundle
 * recorded on a Samsung SM-S928B on 2026-08-31 (`docs/Scan evidence 31-08-26/`). They are not
 * invented shapes: each one is a token ML Kit returned from a photograph of a real package, which is
 * the only kind of fixture that can pin this rule honestly — the whole defect is about how a
 * recognizer mangles a printed unit glyph, and a synthetic fixture would simply spell it correctly.
 */
class CarbUnitAccompanimentTest {

    private fun element(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrElement(
            text = text,
            box = OcrBox(left, top, right, bottom),
            // Block and line ids are deliberately constant across every fixture here: this rule is
            // geometry- and text-based, and must not consult ML Kit's own grouping. Giving them all
            // one value means a rule that started reading them would fail these tests rather than
            // silently passing on a signal the row builder already refuses to trust.
            blockId = 0,
            lineId = 0,
        )

    // ---------------------------------------------------------------------------------------
    // Accepted: a unit is genuinely present.
    // ---------------------------------------------------------------------------------------

    /**
     * Sondey (`20260831-135943-434`): the value and its unit are two separate elements on one row,
     * 13 px apart with identical y bounds. This is the ordinary layout for a per-100 column and it
     * must keep reading, or the rule would refuse the commonest correct shape on real packaging.
     */
    @Test
    fun `a bare value with an adjacent unit element is accompanied`() {
        val value = element("72,0", 1066, 1902, 1150, 1944)
        val unit = element("g", 1163, 1902, 1187, 1944)

        assertTrue(CarbUnitAccompaniment.isAccompanied(value, listOf(value, unit)))
    }

    /** Boursin (`20260831-140056-376`): the unit is a suffix on the token, with trailing comma. */
    @Test
    fun `a valid unit suffix on the token is accompanied`() {
        val value = element("3g,", 1397, 1747, 1462, 1817)

        assertTrue(CarbUnitAccompaniment.isAccompanied(value, listOf(value)))
    }

    /** Fanta (`20260831-140208-173`): the same printed 0,5 g that scan 4 corrupted, read correctly. */
    @Test
    fun `a unit suffix with no separator is accompanied`() {
        val value = element("0.5g", 944, 1970, 1054, 2045)

        assertTrue(CarbUnitAccompaniment.isAccompanied(value, listOf(value)))
    }

    @Test
    fun `every well formed suffix from the bundle is accompanied`() {
        listOf("0g", "19g,", "5,5g.", "28g,", "0,3g", "3,1g", "1,1g", "100g")
            .forEach { text ->
                val value = element(text, 100, 100, 200, 150)
                assertTrue(
                    "expected '$text' to be accompanied",
                    CarbUnitAccompaniment.isAccompanied(value, listOf(value)),
                )
            }
    }

    /** Millilitres are a basis this app has, so they accompany exactly as grams do. */
    @Test
    fun `a millilitre unit is accompanied`() {
        val value = element("250ml", 100, 100, 200, 150)
        assertTrue(CarbUnitAccompaniment.isAccompanied(value, listOf(value)))

        val bare = element("250", 100, 100, 180, 150)
        val unit = element("ml", 190, 100, 230, 150)
        assertTrue(CarbUnitAccompaniment.isAccompanied(bare, listOf(bare, unit)))
    }

    // ---------------------------------------------------------------------------------------
    // Declined: unit -> digit. Absence of a unit is the ONLY available signal.
    // ---------------------------------------------------------------------------------------

    /**
     * **The scan-4 confident-wrong.** Fanta (`20260831-140132-710`): the box `[922,1823,1012,1894]`
     * spans exactly the printed characters `0.5 g`, and ML Kit folded the `g` into the number. The
     * parser reached `CONFIDENT 0.59 PER_100_ML` on a can printing 0,5 g.
     *
     * Nothing about the token itself is suspicious — `0.59` is a perfectly ordinary carbohydrate
     * quantity — so no plausibility rule can catch it. The one observable fact is that the row states
     * no unit anywhere.
     */
    @Test
    fun `the scan 4 confident wrong is declined`() {
        val value = element("0.59", 922, 1823, 1012, 1894)
        val name = element("Koolhydraten:", 348, 1827, 567, 1888)

        assertFalse(CarbUnitAccompaniment.isAccompanied(value, listOf(name, value)))
    }

    @Test
    fun `every unit to digit corruption from the bundle is declined`() {
        listOf("0.59", "2,50", "09", "3,49", "72,0", "0.5")
            .forEach { text ->
                val value = element(text, 100, 100, 200, 150)
                assertFalse(
                    "expected '$text' with no unit to be declined",
                    CarbUnitAccompaniment.isAccompanied(value, listOf(value)),
                )
            }
    }

    // ---------------------------------------------------------------------------------------
    // Declined: unit -> letter. The suffix is present but is not a unit.
    // ---------------------------------------------------------------------------------------

    /** Boursin (`20260831-140033-054`): the printed `3 g` came back as `3q.`. */
    @Test
    fun `a corrupted unit letter is declined`() {
        val value = element("3q.", 1445, 2111, 1478, 2154)

        assertFalse(CarbUnitAccompaniment.isAccompanied(value, listOf(value)))
    }

    @Test
    fun `every unit to letter corruption from the bundle is declined`() {
        listOf("3q.", "28q,", "6.5q.", "2.5c", "1,1q.")
            .forEach { text ->
                val value = element(text, 100, 100, 200, 150)
                assertFalse(
                    "expected corrupted unit '$text' to be declined",
                    CarbUnitAccompaniment.isAccompanied(value, listOf(value)),
                )
            }
    }

    // ---------------------------------------------------------------------------------------
    // The rule is unconditional — no sibling comparison, no table-scope lookup.
    // ---------------------------------------------------------------------------------------

    /**
     * The owner's explicit correction to the original brief: a sibling-conditional rule ("decline
     * when siblings carry units and this token does not") would let `3q.` through, because the
     * Boursin declaration carries well-formed `19g` and `5,5g.` on neighbouring clauses. Scope is
     * the token and its immediate neighbour only.
     */
    @Test
    fun `a corrupted token is declined even when well formed units sit nearby`() {
        val corrupted = element("3q.", 1445, 2111, 1478, 2154)
        val goodSibling = element("19g,", 1200, 2111, 1290, 2154)
        val anotherGood = element("5,5g.", 1600, 2111, 1700, 2154)

        assertFalse(
            CarbUnitAccompaniment.isAccompanied(
                corrupted,
                listOf(goodSibling, corrupted, anotherGood),
            ),
        )
    }

    /** A unit far away on the row is not this value's unit. Adjacency is a geometric claim. */
    @Test
    fun `a distant unit element does not accompany`() {
        val value = element("0.59", 922, 1823, 1012, 1894)
        val distantUnit = element("g", 1600, 1823, 1624, 1894)

        assertFalse(CarbUnitAccompaniment.isAccompanied(value, listOf(value, distantUnit)))
    }

    /** A unit on a different printed row is not this value's unit either. */
    @Test
    fun `a unit on another row does not accompany`() {
        val value = element("0.59", 922, 1823, 1012, 1894)
        val unitBelow = element("g", 1020, 2100, 1044, 2160)

        assertFalse(CarbUnitAccompaniment.isAccompanied(value, listOf(value, unitBelow)))
    }

    /**
     * The unit must FOLLOW the value, as printed. A `g` to the left belongs to the previous
     * column's value, and accepting it would let one column's unit vouch for another's number.
     *
     * The fixture places the `g` as close on the left as a real unit sits on the right (a ~13 px
     * gap), so the only thing that can reject it is the direction test. An earlier version of this
     * fixture put the `g` 132 px away, where the distance bound rejected it regardless — the test
     * passed while pinning nothing, and a negative control removing the direction check did not
     * fail it.
     */
    @Test
    fun `a unit preceding the value does not accompany`() {
        val value = element("0.59", 922, 1823, 1012, 1894)
        // Right edge 13 px left of the value's left edge — the mirror image of the Sondey spacing,
        // so distance cannot be what rejects it. Only the direction test can.
        val unitBefore = element("g", 885, 1823, 909, 1894)
        check(value.box.left - unitBefore.box.right == 13) { "fixture must mirror the real spacing" }

        assertFalse(CarbUnitAccompaniment.isAccompanied(value, listOf(unitBefore, value)))
    }

    // ---------------------------------------------------------------------------------------
    // It refuses; it never repairs.
    // ---------------------------------------------------------------------------------------

    /**
     * The rule returns a boolean and holds no repair path. `0.59` must never become `0.5`, and
     * `3q.` must never become `3` — the decimal point is what OCR is least reliable about, and
     * unlike a refusal a wrong repair is invisible to the person dosing from it.
     */
    @Test
    fun `the filter exposes no repair and preserves the original text`() {
        val value = element("0.59", 922, 1823, 1012, 1894)

        assertFalse(CarbUnitAccompaniment.isAccompanied(value, listOf(value)))
        assertEquals("0.59", value.text)
    }
}

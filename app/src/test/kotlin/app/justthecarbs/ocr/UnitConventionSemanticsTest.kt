package app.justthecarbs.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A basis denominator and a nutrient amount unit are different conventions.
 *
 * ## The measured gap
 *
 * On `20260904-113653-044` the app recorded:
 *
 * ```
 * unit-accompaniment: this label states units in its headers only; bare values are ordinary here
 * ```
 *
 * The photograph says otherwise. That Fanta bottle prints `g` on **every one of its twelve value
 * cells** — `0 g`, `0 g`, `0,5 g`, `0,5 g`, `0 g`, `0 g` in each of two columns. It is as
 * units-on-values as a label gets.
 *
 * ## Why the policy said the opposite, and why raising the threshold is not the fix
 *
 * [UnitAccompanimentPolicy] counts value cells whose unit survived recognition. ML Kit turned five of
 * the six `g` glyphs in the 100 ml column into `9` (`09`, `0.59`, `0.59`) or dropped them (`0`), and
 * mangled the 250 ml column's `1,3 g` into `131`. Exactly **one** `0g` survived, against
 * `MIN_UNIT_BEARING_CELLS = 2`.
 *
 * So the evidence for the convention is destroyed by the very corruption the rule exists to catch:
 * **the more thoroughly the `g` glyphs are damaged, the more ordinary a bare value looks.** The
 * threshold is unreachable precisely on the labels that need it. Lowering it to one is not the answer
 * either — the policy's own KDoc explains that a single `0g` could itself be a misrecognition.
 *
 * ## The distinction that resolves it
 *
 * `100 ml` in a header is a **denominator**: it says what the column is measured per. It is not
 * evidence about whether nutrient *amounts* carry their own `g`. The two conventions are independent,
 * and a label may state a basis in `ml` while printing every amount in `g` — which is exactly what
 * this bottle does.
 *
 * Counting a *corrupted* unit as a witness to the convention is what closes the gap: a token like
 * `09` or `0.59` on a value row is not evidence that the label omits units, it is evidence that
 * something was there. It cannot be counted as a clean unit — that would defeat the rule — but it must
 * stop counting as proof of a bare-value convention.
 */
class UnitConventionSemanticsTest {

    private fun element(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrElement(text, OcrBox(left, top, right, bottom), 0, 0)

    /**
     * The Fanta 100 ml column exactly as the device recognised it.
     *
     * Geometry is the device's own, from the bundle's `recognized.txt`. One `0g` survives; every other
     * value cell lost its unit to a digit.
     */
    private fun fantaAsRecognised(): OcrDocument = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            element("PER:", 479, 1343, 580, 1417),
            element("100", 1271, 1343, 1345, 1417),
            element("ml", 1355, 1343, 1400, 1417),
            element("Vetten:", 478, 1623, 634, 1697),
            element("waarvan", 498, 1710, 683, 1792),
            element("verzadigde", 701, 1710, 990, 1792),
            element("vetzuren:", 1008, 1710, 1262, 1792),
            element("0g", 1319, 1710, 1387, 1792),
            element("Koolhydraten:", 486, 1794, 799, 1882),
            element("0.59", 1271, 1802, 1378, 1884),
            element("waarvan", 496, 1895, 685, 1969),
            element("sukers:", 703, 1899, 912, 1969),
            element("0.59", 1266, 1895, 1391, 1991),
            element("Eiwitten:", 473, 1984, 673, 2059),
            element("09", 1316, 1998, 1389, 2083),
            element("Zout:", 471, 2070, 586, 2153),
            element("0", 1318, 2094, 1351, 2162),
        ),
    )

    /**
     * The P0 semantic assertion.
     *
     * One clean `0g` plus four corrupted unit glyphs is a units-on-values label, so the accompaniment
     * rule must be askable and the bare `0.59` must be declinable.
     */
    @Test
    fun `a label whose unit glyphs were corrupted still counts as printing units on values`() {
        val document = fantaAsRecognised()
        val rows = LogicalRowBuilder.build(document)

        assertTrue(
            "the Fanta prints g on every value cell; corrupted glyphs must not read as a bare-value convention",
            UnitAccompanimentPolicy.mayDeclineBareValues(document, rows),
        )
    }

    /**
     * A millilitre denominator says nothing about amount units.
     *
     * The document below states its basis in `ml` and prints no unit on any value cell. That is a
     * genuine header-only label and the rule must stay unasked — otherwise the fix above becomes a
     * blanket decline and the coverage regression the policy exists to avoid returns.
     */
    @Test
    fun `a millilitre basis header is not evidence that values carry units`() {
        val document = OcrDocument(
            width = 1684,
            height = 3648,
            elements = listOf(
                element("PER:", 479, 1343, 580, 1417),
                element("100", 1271, 1343, 1345, 1417),
                element("ml", 1355, 1343, 1400, 1417),
                element("Vetten:", 478, 1623, 634, 1697),
                element("2.1", 1319, 1623, 1387, 1697),
                element("Koolhydraten:", 486, 1794, 799, 1882),
                element("4.8", 1271, 1794, 1378, 1882),
                element("Eiwitten:", 473, 1984, 673, 2059),
                element("3.6", 1316, 1984, 1389, 2059),
            ),
        )
        val rows = LogicalRowBuilder.build(document)

        assertFalse(
            "no value cell carries a unit; this label states units in its header only",
            UnitAccompanimentPolicy.mayDeclineBareValues(document, rows),
        )
    }

    /**
     * A corrupted glyph alone is not a convention either.
     *
     * Without at least one surviving clean unit, "every value ends in a digit" is indistinguishable
     * from a label that genuinely prints bare numbers. Requiring a real unit somewhere keeps the
     * evidence positive rather than inferred from damage.
     */
    @Test
    fun `corrupted glyphs alone do not establish a units-on-values convention`() {
        val document = OcrDocument(
            width = 1684,
            height = 3648,
            elements = listOf(
                element("PER:", 479, 1343, 580, 1417),
                element("100", 1271, 1343, 1345, 1417),
                element("ml", 1355, 1343, 1400, 1417),
                element("Vetten:", 478, 1623, 634, 1697),
                element("21", 1319, 1623, 1387, 1697),
                element("Koolhydraten:", 486, 1794, 799, 1882),
                element("4.8", 1271, 1794, 1378, 1882),
                element("Eiwitten:", 473, 1984, 673, 2059),
                element("36", 1316, 1984, 1389, 2059),
            ),
        )
        val rows = LogicalRowBuilder.build(document)

        assertFalse(
            "no clean unit survives anywhere; the convention is not demonstrated",
            UnitAccompanimentPolicy.mayDeclineBareValues(document, rows),
        )
    }

    /**
     * The ordinary units-on-values label is unaffected.
     *
     * Two clean unit-bearing cells already satisfied the policy and must continue to, with no reliance
     * on the corrupted-glyph evidence added for the Fanta.
     */
    @Test
    fun `a cleanly recognised units-on-values label is unaffected`() {
        val document = OcrDocument(
            width = 1684,
            height = 3648,
            elements = listOf(
                element("per", 479, 1343, 580, 1417),
                element("100", 1271, 1343, 1345, 1417),
                element("g", 1355, 1343, 1400, 1417),
                element("Vetten:", 478, 1623, 634, 1697),
                element("2.1g", 1319, 1623, 1387, 1697),
                element("Koolhydraten:", 486, 1794, 799, 1882),
                element("4.8", 1271, 1794, 1378, 1882),
                element("Eiwitten:", 473, 1984, 673, 2059),
                element("3.6g", 1316, 1984, 1389, 2059),
            ),
        )
        val rows = LogicalRowBuilder.build(document)

        assertTrue(UnitAccompanimentPolicy.mayDeclineBareValues(document, rows))
    }

    /**
     * The header's own `100 ml` must never be the witness.
     *
     * Already true and pinned here because the fix touches the counting logic: if a basis token ever
     * started counting as an amount unit, every headed table would look units-on-values and the
     * coverage regression would be silent.
     */
    @Test
    fun `the basis header does not witness the amount convention`() {
        val document = OcrDocument(
            width = 1684,
            height = 3648,
            elements = listOf(
                element("per", 479, 1343, 580, 1417),
                element("100", 1090, 1343, 1164, 1417),
                element("g", 1174, 1343, 1219, 1417),
                element("100", 1271, 1343, 1345, 1417),
                element("ml", 1355, 1343, 1400, 1417),
                element("Koolhydraten:", 486, 1794, 799, 1882),
                element("4.8", 1271, 1794, 1378, 1882),
            ),
        )
        val rows = LogicalRowBuilder.build(document)

        assertFalse(
            "two header basis tokens are not two unit-bearing value cells",
            UnitAccompanimentPolicy.mayDeclineBareValues(document, rows),
        )
    }
}

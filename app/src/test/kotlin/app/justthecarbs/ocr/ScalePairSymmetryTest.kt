package app.justthecarbs.ocr

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A separatorless **pair** demonstrates ambiguity for **both** of its members, not just the left one.
 *
 * ## The capture that found it
 *
 * `docs/Scan Evıdence 4th test/20260904-160639-565` — a protein bar printing
 * `Koolhydraten/Glucides 46 g / 100 g` and `12 g / 25 g reep`. Neither value kept a decimal
 * separator, so a common rescaling of the pair is exactly as consistent with the recognised text as
 * the pair itself. That is [ScaleAmbiguity.Verdict.Ambiguous] by this file's own stated rule:
 *
 * > When the recognizer did not preserve a decimal separator anywhere in a **pair** of values that
 * > move together, and dividing the whole pair by a common power of ten yields an equally
 * > self-consistent reading, the absolute scale is **not established by this evidence**.
 *
 * The bundle's recovery list recorded the asymmetry in the app's own words:
 *
 * ```
 * suppressed '46' @x=1161: a common rescaling of '46' and '12' is equally consistent …
 * offered    '12' @x=1443: 12 g / serving | selectable (awaiting a tap)
 * ```
 *
 * One number of the pair is refused **because of** the other, and the other is offered. The same
 * shape appears twice more in that session (`160501-961` suppressing `159` while offering `18`, and
 * `160532-812` suppressing `15` while offering `38g`), so it is a property of the rule rather than of
 * one photograph.
 *
 * ## Why it happened, and why the guard it came from is kept
 *
 * [ScaleAmbiguity] looks for the paired value among elements **to the right of** the candidate. That
 * bound was added for a real reason — a nutrient name must never become the candidate's "paired
 * column value", or a legitimate single-column `41g` label reads as ambiguous because the word
 * beside it pairs with its own figure.
 *
 * But direction is not what stops a *name* being read as a value; `looksLikeAValueCell` already
 * does that, and it requires the token to **begin with a digit**, so `Koolhydraten`, `Vetten` and
 * `E471` are all excluded from either side. What the rightward bound actually removed was the
 * rightmost cell's ability to see its own pair, and the pair relation is symmetric: if `46` and `12`
 * being separatorless makes `46` ambiguous, it makes `12` ambiguous by precisely the same evidence.
 *
 * ## What this is not
 *
 * It is not a widening of what may be shown — it is a refusal added, on one side of a pair that was
 * already refused on the other. Nothing that was suppressed becomes offerable. And no value is
 * repaired: `12` never becomes `1.2`, and the withheld figure routes to focused entry with its basis
 * preserved, where the user types the digits printed on the package in front of them.
 */
class ScalePairSymmetryTest {

    /**
     * The carbohydrate row of `20260904-160639-565`, with the geometry the device recorded.
     *
     * Two value cells and a nutrient name, laid out left to right exactly as a two-column table
     * prints them. Nothing else is needed: [ScaleAmbiguity] asks only about the candidate's own row.
     */
    private fun bar(carb: String, serving: String) = OcrDocument(
        width = 1684,
        height = 3648,
        elements = listOf(
            OcrElement("Koolhydraten/Glucides", OcrBox(134, 1842, 700, 1909), 0, 0),
            OcrElement(carb, OcrBox(1120, 1842, 1210, 1909), 0, 0),
            OcrElement(serving, OcrBox(1400, 1842, 1490, 1909), 0, 0),
        ),
    )

    private fun candidateFor(document: OcrDocument, text: String, left: Int) = CarbCandidate(
        sourceLine = "Koolhydraten/Glucides",
        label = "Koolhydraten/Glucides",
        value = BigDecimal(text.filter { it.isDigit() || it == '.' }),
        basis = null,
        score = 0,
        geometry = OcrBox(left, 1842, left + 90, 1909),
        evidence = emptyList(),
        column = null,
    )

    /**
     * The left member. This already held before the fix and is the control.
     */
    @Test
    fun `the left member of a separatorless pair is ambiguous`() {
        val document = bar(carb = "46", serving = "12")
        val verdict = ScaleAmbiguity.check(document, candidateFor(document, "46", 1120))

        assertTrue(
            "'46' paired with '12', neither separated: a common rescaling is equally consistent",
            verdict is ScaleAmbiguity.Verdict.Ambiguous,
        )
    }

    /**
     * The right member. **This is the defect** — it reported `Unsupported` and was offered.
     */
    @Test
    fun `the right member of the same separatorless pair is equally ambiguous`() {
        val document = bar(carb = "46", serving = "12")
        val verdict = ScaleAmbiguity.check(document, candidateFor(document, "12", 1400))

        assertTrue(
            "'12' is paired with '46' by the same evidence that refused '46'; the pair relation is " +
                "symmetric and a rescaling of it is equally consistent, so this must not report " +
                "Unsupported — it was, and the bundle offered '12 g / serving' as a result",
            verdict is ScaleAmbiguity.Verdict.Ambiguous,
        )
    }

    /**
     * The pair is named the same way from either side, so a bundle reads coherently.
     *
     * Not cosmetic: the recovery list prints `candidateText` and `pairedText` verbatim, and a
     * sentence naming the candidate as its own pair would be unreadable while debugging a scan.
     */
    @Test
    fun `each member names the other as its pair`() {
        val document = bar(carb = "46", serving = "12")

        val left = ScaleAmbiguity.check(document, candidateFor(document, "46", 1120))
            as ScaleAmbiguity.Verdict.Ambiguous
        val right = ScaleAmbiguity.check(document, candidateFor(document, "12", 1400))
            as ScaleAmbiguity.Verdict.Ambiguous

        assertEquals("46", left.candidateText)
        assertEquals("12", left.pairedText)
        assertEquals("12", right.candidateText)
        assertEquals("46", right.pairedText)
    }

    /**
     * A separator anywhere on the row still establishes the scale, from either side.
     *
     * The recognizer demonstrably preserved decimal points on this row, so a member's own lack of one
     * is information rather than damage — and that reasoning never depended on which side the
     * separated sibling sits.
     */
    @Test
    fun `a separated sibling establishes the scale from either side`() {
        val document = bar(carb = "4,6", serving = "12")

        val right = ScaleAmbiguity.check(document, candidateFor(document, "12", 1400))
        assertTrue(
            "a separator survived on this row, to the candidate's left",
            right is ScaleAmbiguity.Verdict.Established,
        )
    }

    /**
     * A lone value with no numeric sibling is still [ScaleAmbiguity.Verdict.Unsupported].
     *
     * This is the `41g` control the rightward bound was written to protect. The nutrient name to its
     * left must not pair with it — and it does not, because `looksLikeAValueCell` requires a leading
     * digit, which is the guard that was actually doing the work all along.
     */
    @Test
    fun `a lone value beside a nutrient name is unsupported, not ambiguous`() {
        val document = OcrDocument(
            width = 1684,
            height = 3648,
            elements = listOf(
                OcrElement("Koolhydraten", OcrBox(134, 1842, 700, 1909), 0, 0),
                OcrElement("41g", OcrBox(1120, 1842, 1210, 1909), 0, 0),
            ),
        )

        val verdict = ScaleAmbiguity.check(document, candidateFor(document, "41", 1120))

        assertTrue(
            "the nutrient name is not a value cell and must not become a pair",
            verdict is ScaleAmbiguity.Verdict.Unsupported,
        )
    }

    /**
     * A nutrient name containing digits is still not a pair.
     *
     * `E471` and `Omega-3` begin with a letter, so the leading-digit rule excludes them from either
     * direction. Pinned because relaxing the direction makes the leading-digit rule the *only* thing
     * standing between a name and a pairing.
     */
    @Test
    fun `a nutrient name containing digits is not a pair`() {
        val document = OcrDocument(
            width = 1684,
            height = 3648,
            elements = listOf(
                OcrElement("E471", OcrBox(134, 1842, 400, 1909), 0, 0),
                OcrElement("Omega-3", OcrBox(420, 1842, 700, 1909), 0, 0),
                OcrElement("41g", OcrBox(1120, 1842, 1210, 1909), 0, 0),
            ),
        )

        val verdict = ScaleAmbiguity.check(document, candidateFor(document, "41", 1120))

        assertTrue(
            "names beginning with a letter are excluded whichever side they sit on",
            verdict is ScaleAmbiguity.Verdict.Unsupported,
        )
    }
}

package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Recovering the basis a label *stated* when the value could not be read (1.0.3 P1).
 *
 * ## The defect
 *
 * A coconut-milk table printed `per 100 ml` plainly enough that [ColumnClassifier] resolved the
 * column. The carbohydrate *value* still needed assistance, and at that point the app asked
 * "2.5 g carbs — per what?" and offered `/100 g` alongside `/100 ml`.
 *
 * That is a fact the app had already established being thrown away and then asked for again, with
 * the wrong answer one tap from the right one. Confidence in the **value** and confidence in the
 * **basis** are separate facts arrived at by separate stages; flattening them into one
 * all-or-nothing state is what produced the question.
 *
 * ## Why this is not a weakening
 *
 * Nothing here reads a value, ranks a candidate or lowers a threshold. It asks the *existing*
 * column classifier the one question it already answers — "what is this table measured per?" — and
 * reports the answer only when it is unambiguous. Every uncertain case returns null and the user is
 * asked exactly as before, which is the pre-existing behaviour.
 */
class StatedBasisTest {

    private fun doc(vararg elements: OcrElement) = OcrDocument(1200, 400, elements.toList())

    /** A header phrase sits centred over the column it heads, not at the label margin. */
    private fun perMl() = doc(
        OcrElement("per", OcrBox(330, 90, 380, 115), 0, 0),
        OcrElement("100", OcrBox(388, 90, 440, 115), 0, 0),
        OcrElement("ml", OcrBox(448, 90, 490, 115), 0, 0),
        OcrElement("Koolhydraten", OcrBox(60, 180, 220, 205), 1, 0),
        OcrElement("2,5", OcrBox(380, 180, 450, 205), 1, 0),
    )

    private fun perG() = doc(
        OcrElement("per", OcrBox(330, 90, 380, 115), 0, 0),
        OcrElement("100", OcrBox(388, 90, 440, 115), 0, 0),
        OcrElement("g", OcrBox(448, 90, 470, 115), 0, 0),
        OcrElement("Koolhydraten", OcrBox(60, 180, 220, 205), 1, 0),
        OcrElement("53,5", OcrBox(380, 180, 450, 205), 1, 0),
    )

    // ---- 1. the case the fix exists for --------------------------------------------------------

    @Test
    fun `a label stating per 100 ml yields that basis`() {
        assertEquals(NutritionBasis.PER_100_ML, StatedBasis.of(perMl()))
    }

    @Test
    fun `a label stating per 100 g yields that basis`() {
        assertEquals(NutritionBasis.PER_100_G, StatedBasis.of(perG()))
    }

    /**
     * Non-vacuity guard for the whole class.
     *
     * These fixtures are only evidence about the real pipeline if the real [ColumnClassifier]
     * genuinely resolves a column from them. Asserted directly, because a fixture whose header the
     * classifier never recognises would make every positive case above pass for the wrong reason —
     * and this repo has already shipped one fixture that failed exactly that way.
     */
    @Test
    fun `precondition - the real classifier resolves these fixtures`() {
        listOf(perMl() to NutritionColumnKind.PER_100_ML, perG() to NutritionColumnKind.PER_100_G)
            .forEach { (document, expected) ->
                val columns = ColumnClassifier.classify(
                    LogicalRowBuilder.build(document),
                    document.width,
                )
                assertEquals(
                    "the fixture must genuinely classify as $expected, or these tests prove nothing",
                    listOf(expected),
                    columns.map { it.kind },
                )
            }
    }

    // ---- 2. every uncertain case still asks ----------------------------------------------------

    /** No header at all. Nothing to preserve, so the user is asked exactly as before. */
    @Test
    fun `a label stating no basis yields null`() {
        val document = doc(
            OcrElement("Koolhydraten", OcrBox(60, 180, 220, 205), 0, 0),
            OcrElement("53,5", OcrBox(380, 180, 450, 205), 0, 0),
        )
        assertNull(StatedBasis.of(document))
    }

    /**
     * Both bases printed. THE safety case.
     *
     * This is an ambiguity, not a confident basis — it is precisely the question the user must
     * answer. Auto-selecting either would be the "grams of what?" error committed on the app's own
     * initiative, which is strictly worse than asking.
     */
    @Test
    fun `a label stating both bases yields null rather than picking one`() {
        val document = doc(
            OcrElement("per", OcrBox(330, 90, 380, 115), 0, 0),
            OcrElement("100", OcrBox(388, 90, 440, 115), 0, 0),
            OcrElement("g", OcrBox(448, 90, 470, 115), 0, 0),
            OcrElement("per", OcrBox(700, 90, 750, 115), 0, 0),
            OcrElement("100", OcrBox(758, 90, 810, 115), 0, 0),
            OcrElement("ml", OcrBox(818, 90, 860, 115), 0, 0),
            OcrElement("Koolhydraten", OcrBox(60, 180, 220, 205), 1, 0),
            OcrElement("53,5", OcrBox(380, 180, 450, 205), 1, 0),
            OcrElement("48,1", OcrBox(750, 180, 820, 205), 1, 0),
        )
        assertNull(StatedBasis.of(document))
    }

    /**
     * Two columns stating the *same* basis is agreement, not conflict.
     *
     * Multilingual packaging routinely prints one basis twice ("per 100 g / pro 100 g"), and
     * refusing that would lose the basis on exactly the labels this app is aimed at.
     */
    @Test
    fun `two columns stating the same basis still yields it`() {
        val document = doc(
            OcrElement("per", OcrBox(330, 90, 380, 115), 0, 0),
            OcrElement("100", OcrBox(388, 90, 440, 115), 0, 0),
            OcrElement("g", OcrBox(448, 90, 470, 115), 0, 0),
            OcrElement("pro", OcrBox(700, 90, 750, 115), 0, 0),
            OcrElement("100", OcrBox(758, 90, 810, 115), 0, 0),
            OcrElement("g", OcrBox(818, 90, 840, 115), 0, 0),
            OcrElement("Koolhydraten", OcrBox(60, 180, 220, 205), 1, 0),
            OcrElement("53,5", OcrBox(380, 180, 450, 205), 1, 0),
            OcrElement("53,5", OcrBox(750, 180, 820, 205), 1, 0),
        )
        assertEquals(NutritionBasis.PER_100_G, StatedBasis.of(document))
    }

    /**
     * A per-serving column is not a per-100 basis and must never be reported as one.
     *
     * `NutritionBasis` has exactly two members and both mean "per 100". A serving column answers a
     * different question, so mapping it onto either would attach a per-serving figure to a per-100
     * unit — a silently wrong result rather than a refused one.
     */
    @Test
    fun `a serving column alone yields null`() {
        val document = doc(
            OcrElement("per", OcrBox(330, 90, 380, 115), 0, 0),
            OcrElement("stuk", OcrBox(388, 90, 450, 115), 0, 0),
            OcrElement("Koolhydraten", OcrBox(60, 180, 220, 205), 1, 0),
            OcrElement("6,7", OcrBox(380, 180, 450, 205), 1, 0),
        )
        assertNull(StatedBasis.of(document))
    }

    /**
     * A per-100 column alongside a serving column still yields the per-100 basis.
     *
     * This is the ordinary shape of European packaging, and the serving column says nothing that
     * contradicts the per-100 header. Only a *contradicting per-100 basis* is a conflict.
     */
    @Test
    fun `a per-100 column beside a serving column still yields the per-100 basis`() {
        val document = doc(
            OcrElement("per", OcrBox(330, 90, 380, 115), 0, 0),
            OcrElement("100", OcrBox(388, 90, 440, 115), 0, 0),
            OcrElement("g", OcrBox(448, 90, 470, 115), 0, 0),
            OcrElement("per", OcrBox(700, 90, 750, 115), 0, 0),
            OcrElement("stuk", OcrBox(758, 90, 820, 115), 0, 0),
            OcrElement("Koolhydraten", OcrBox(60, 180, 220, 205), 1, 0),
            OcrElement("53,5", OcrBox(380, 180, 450, 205), 1, 0),
            OcrElement("6,7", OcrBox(750, 180, 820, 205), 1, 0),
        )
        assertEquals(NutritionBasis.PER_100_G, StatedBasis.of(document))
    }

    /** A null document is the no-recognition case and must not throw. */
    @Test
    fun `a null document yields null`() {
        assertNull(StatedBasis.of(null))
    }

    @Test
    fun `an empty document yields null`() {
        assertNull(StatedBasis.of(OcrDocument(1200, 400, emptyList())))
    }
}

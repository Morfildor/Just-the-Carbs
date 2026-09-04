package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A nutrient name printed in several languages is one declaration, not several competing ones.
 *
 * ## The dead end this closes
 *
 * `docs/Scan Evidence 3rd testr/20260904-134420-616` photographs a Turkish rice-flour box whose
 * carbohydrate row is printed as `Karbonhidrat / Kohlenhydrate glucides 80 g / carbohydrate /
 * koolhydraten-kulhydrat`. ML Kit reconstructs that as several rows, and each one names a
 * carbohydrate term, so each types `TOTAL_CARBOHYDRATE`.
 *
 * [FocusedAmountEntry] required `singleOrNull` over those declarations, and the guard therefore
 * fired on a label that **agrees with itself in five languages** — sending a capture whose row and
 * basis were both established to the crop screen, where dragging corners cannot help.
 *
 * ## Why relaxing it is not a weakening
 *
 * The guard exists to stop the app arbitrating between two declarations that state **different
 * figures** — there, picking one is a decision the app has no evidence for. That risk is about
 * *values*, and a row that prints only a nutrient name states no value at all: it cannot be the
 * thing the user is asked to confirm, and it cannot disagree with anything.
 *
 * So the rule becomes: consider the total-carbohydrate declarations that **carry a value**. One
 * such declaration is the target; two that carry different values still return null, which is the
 * arbitration the guard was written for and is pinned below.
 */
class WrappedNutrientNameTest {

    private fun element(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = 0, lineId = 0)

    private fun capture(suffix: String) =
        SeventeenthSessionCorpus.captures.single { it.bundle.endsWith(suffix) }

    /**
     * The measurement: the label really does produce several total-carbohydrate rows.
     */
    @Test
    fun `a multilingual carbohydrate name produces several total rows`() {
        val document = capture("134420-616").passA()
        val totals = LogicalRowBuilder.build(document)
            .count { RowClassifier.classify(it) == NutritionRowKind.TOTAL_CARBOHYDRATE }

        assertTrue("expected several total rows, found $totals", totals > 1)
    }

    /**
     * And the outcome: the basis was established all along, so focused entry is reachable.
     */
    @Test
    fun `a wrapped name still yields a focused entry target`() {
        val document = capture("134420-616").passA()

        val target = FocusedAmountEntry.of(document)

        assertNotNull("expected a focused-entry target", target)
        assertEquals(NutritionBasis.PER_100_G, target!!.basis)
    }

    /**
     * The guard the relaxation must not remove.
     *
     * Two total-carbohydrate declarations printing **different figures** is a genuine conflict, and
     * the app must not choose between them. This is the shape of a packet carrying two products'
     * tables side by side.
     */
    @Test
    fun `two total rows stating different values still yield no target`() {
        val document = OcrDocument(
            width = 1000,
            height = 400,
            elements = listOf(
                element("per 100 g", 380, 10, 520, 40),
                element("Koolhydraten", 20, 60, 260, 90),
                element("27", 400, 60, 470, 90),
                element("Koolhydraten", 20, 120, 260, 150),
                element("2,7", 400, 120, 470, 150),
            ),
        )

        assertNull(FocusedAmountEntry.of(document))
    }

    /**
     * A name-only row alongside one carrying a value is not a conflict.
     *
     * This is the multilingual case in miniature, held as a unit test so the rule is pinned
     * independently of any one photograph.
     */
    @Test
    fun `a name-only total row does not compete with the row carrying the value`() {
        val document = OcrDocument(
            width = 1000,
            height = 400,
            elements = listOf(
                element("per 100 g", 380, 10, 520, 40),
                element("Karbonhidrat", 20, 60, 260, 90),
                element("Kohlenhydrate", 20, 120, 280, 150),
                element("80", 400, 120, 470, 150),
                element("carbohydrate", 20, 180, 260, 210),
            ),
        )

        val target = FocusedAmountEntry.of(document)

        assertNotNull(target)
        assertEquals(NutritionBasis.PER_100_G, target!!.basis)
        assertTrue(
            "the target must be the row that carries the value, was '${target.rowText}'",
            target.rowText.contains("80"),
        )
    }
}

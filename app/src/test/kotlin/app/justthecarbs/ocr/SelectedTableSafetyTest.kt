package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Every safety guard must survive a user-confirmed selection, unchanged.
 *
 * ## The claim being defended
 *
 * A manual crop is a strong signal, and the tempting mistake is to treat it as *permission*: the user
 * has pointed at the table, so surely a number inside it can be trusted more readily. That inference
 * is wrong and this class exists to keep it out.
 *
 * The selection asserts **"the nutrition table is in here"**. It does not assert **"a number in here
 * is the carbohydrate value"**. Inside any real selection there are still sugars rows, saturated-fat
 * rows, protein rows, reference-intake percentages and unit annotations — the crop removes the
 * *ingredient list*, not the label's own internal structure. So every rule that decides what a figure
 * *means* has to keep working exactly as it did on a full frame.
 *
 * Each test below therefore runs a selection that **includes** the hazard and asserts the parser
 * still refuses it. A test that cropped the hazard away would prove nothing.
 */
class SelectedTableSafetyTest {

    private fun parseSelected(document: OcrDocument, region: NormalizedRegion): NutritionParseReport {
        val filtered = ElementRegionFilter.filter(document, region)
            ?: throw AssertionError("the selection retained no elements")
        return NutritionTableParser.parseWithDiagnostics(filtered)
    }

    private fun value(report: NutritionParseReport): BigDecimal? =
        (report.reading as? LabelReading.Confident)?.candidate?.value

    /** A selection generously enclosing everything in these small fixtures. */
    private val wholeTable = NormalizedRegion(0.02, 0.02, 0.98, 0.98)

    // ------------------------------------------------------------------ the unit-marker hazard

    @Test
    fun `a bracketed g misread as 9 inside the selection still cannot become the value`() {
        // The measured Kinder failure, now inside a user-confirmed crop. This is the case where the
        // "the user selected it, so trust it" instinct would be most damaging: (9) sits squarely in
        // the middle of the table the user pointed at.
        val document = SlopedLabel(slopePercent = 0.0).apply {
            row(50, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
            row(120, "Energie" to 60..170, block = 1, line = 0)
            row(120, "2270" to 380..450, "kJ" to 458..495, block = 2, line = 0)
            row(190, "Kohlenhydrate" to 60..250, block = 3, line = 0)
            row(190, "(9)" to 300..340, block = 4, line = 0)
            row(190, "53,5" to 380..450, "g" to 458..475, block = 5, line = 0)
        }.document(width = 600, height = 300)

        val report = parseSelected(document, wholeTable)

        assertNotEquals(
            "a unit annotation must never become the carbohydrate value, selection or not",
            0,
            value(report)?.compareTo(BigDecimal("9")) ?: -1,
        )
        assertEquals(0, value(report)?.compareTo(BigDecimal("53.5")))
    }

    @Test
    fun `a legitimate single-digit carbohydrate value inside a selection is still read`() {
        // The negative control for the rule above. If the unit-marker guard keyed on "small number"
        // rather than position, this correct low-carbohydrate reading would be destroyed — and every
        // yoghurt, cheese and cream product in the corpus with it.
        val document = SlopedLabel(slopePercent = 0.0).apply {
            row(50, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
            row(120, "Eiwitten" to 60..190, block = 1, line = 0)
            row(120, "3,5" to 380..440, "g" to 448..465, block = 2, line = 0)
            row(190, "Koolhydraten" to 60..220, block = 3, line = 0)
            row(190, "9" to 380..410, "g" to 418..435, block = 4, line = 0)
        }.document(width = 600, height = 300)

        val report = parseSelected(document, wholeTable)

        assertEquals(
            "a genuine 9 g/100 g reading must survive",
            0,
            value(report)?.compareTo(BigDecimal("9")) ?: -1,
        )
    }

    // ------------------------------------------------------------------ neighbouring nutrient rows

    @Test
    fun `a sugars child row inside the selection never supplies the total`() {
        val document = SlopedLabel(slopePercent = 0.0).apply {
            row(50, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
            row(120, "Koolhydraten" to 60..220, block = 1, line = 0)
            row(120, "46,0" to 380..450, "g" to 458..475, block = 2, line = 0)
            row(190, "waarvan" to 80..185, "suikers" to 193..290, block = 3, line = 0)
            row(190, "1,6" to 380..440, "g" to 448..465, block = 4, line = 0)
        }.document(width = 600, height = 300)

        val report = parseSelected(document, wholeTable)

        assertEquals(0, value(report)?.compareTo(BigDecimal("46.0")))
        assertNotEquals("the sugars figure must not win", 0, value(report)?.compareTo(BigDecimal("1.6")) ?: -1)
    }

    @Test
    fun `a saturated fat row inside the selection never supplies the total`() {
        // Two of the nine real fixtures once returned the saturated-fat figure as total carbohydrate.
        // That is the single worst output this app can produce, so it is pinned under selection too.
        val document = SlopedLabel(slopePercent = 0.0).apply {
            row(50, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
            row(120, "Vetten" to 60..170, block = 1, line = 0)
            row(120, "29,5" to 380..450, "g" to 458..475, block = 2, line = 0)
            row(190, "waarvan" to 80..185, "verzadigde" to 193..330, block = 3, line = 0)
            row(190, "18,2" to 380..450, "g" to 458..475, block = 4, line = 0)
            row(260, "Koolhydraten" to 60..220, block = 5, line = 0)
            row(260, "53,5" to 380..450, "g" to 458..475, block = 6, line = 0)
        }.document(width = 600, height = 400)

        val report = parseSelected(document, wholeTable)

        assertEquals(0, value(report)?.compareTo(BigDecimal("53.5")))
        assertNotEquals("saturated fat must not win", 0, value(report)?.compareTo(BigDecimal("18.2")) ?: -1)
        assertNotEquals("total fat must not win", 0, value(report)?.compareTo(BigDecimal("29.5")) ?: -1)
    }

    @Test
    fun `a protein row inside the selection never supplies the total`() {
        val document = SlopedLabel(slopePercent = 0.0).apply {
            row(50, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
            row(120, "Eiwitten" to 60..190, block = 1, line = 0)
            row(120, "8,7" to 380..440, "g" to 448..465, block = 2, line = 0)
            row(190, "Koolhydraten" to 60..220, block = 3, line = 0)
            row(190, "53,5" to 380..450, "g" to 458..475, block = 4, line = 0)
        }.document(width = 600, height = 300)

        val report = parseSelected(document, wholeTable)

        assertEquals(0, value(report)?.compareTo(BigDecimal("53.5")))
        assertNotEquals("protein must not win", 0, value(report)?.compareTo(BigDecimal("8.7")) ?: -1)
    }

    // ------------------------------------------------------------------ the basis cannot be invented

    @Test
    fun `a selection excluding the basis header does not manufacture a basis`() {
        // The most important test in this class. A crop that cuts off "per 100 g" leaves a value with
        // no established basis, and the app must refuse rather than assume per-100-g — assuming it is
        // exactly the substitution that would put a wrong number in front of someone dosing insulin.
        val document = SlopedLabel(slopePercent = 0.0).apply {
            row(50, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
            row(190, "Koolhydraten" to 60..220, block = 1, line = 0)
            row(190, "53,5" to 380..450, "g" to 458..475, block = 2, line = 0)
        }.document(width = 600, height = 300)

        // Starts below the header band at y=50..70.
        val report = parseSelected(document, NormalizedRegion(0.02, 0.35, 0.98, 0.98))

        assertTrue(
            "a headerless selection must not produce a confident per-100 reading; got ${report.reading}",
            report.reading !is LabelReading.Confident,
        )
    }

    @Test
    fun `a body-only selection is refused even though the value itself is unambiguous`() {
        // Distinguishes "the parser could not find a value" from "the parser found a value it cannot
        // place". Only one number is present, so any rule that promoted a lone candidate would fire
        // here — and would be wrong, because per 100 g and per portion are different answers.
        val document = SlopedLabel(slopePercent = 0.0).apply {
            row(50, "per" to 380..430, "portie" to 438..530, block = 0, line = 0)
            row(190, "Koolhydraten" to 60..220, block = 1, line = 0)
            row(190, "27,0" to 380..450, "g" to 458..475, block = 2, line = 0)
        }.document(width = 600, height = 300)

        val report = parseSelected(document, NormalizedRegion(0.02, 0.35, 0.98, 0.98))

        assertTrue(
            "a per-portion figure with the header cropped away must not become a per-100 result; " +
                "got ${report.reading}",
            report.reading !is LabelReading.Confident ||
                (report.reading as LabelReading.Confident).candidate.basis != NutritionBasis.PER_100_G,
        )
    }

    // ------------------------------------------------------------------ merged-table protection

    @Test
    fun `a merged total and child row inside a selection is still refused as a total`() {
        // Row reconstruction can merge a total row with its sugars child when the printed rows sit
        // close together. The child exclusion is unconditional and type-level, and a manual selection
        // must not turn it into a soft preference.
        val document = SlopedLabel(slopePercent = 0.0).apply {
            row(50, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
            row(
                190,
                "Koolhydraten" to 60..220, "waarvan" to 228..330, "suikers" to 338..430,
                block = 1, line = 0,
            )
            row(190, "1,6" to 450..500, "g" to 508..525, block = 2, line = 0)
        }.document(width = 600, height = 300)

        val report = parseSelected(document, wholeTable)

        assertTrue(
            "a row naming a child nutrient must not yield a total; got ${report.reading}",
            report.reading !is LabelReading.Confident,
        )
    }

    // ------------------------------------------------------------------ the filter adds no values

    @Test
    fun `filtering can only remove candidates, never create one`() {
        // The structural property that separates this design from the reverted Pass B. Pass B ran a
        // second recognition and could therefore produce tokens Pass A never saw; this stage cannot,
        // because it only ever drops elements from the one recognition that happened.
        val document = SlopedLabel(slopePercent = 0.0).apply {
            row(50, "per" to 380..430, "100" to 438..495, "g" to 503..520, block = 0, line = 0)
            row(190, "Koolhydraten" to 60..220, block = 1, line = 0)
            row(190, "53,5" to 380..450, "g" to 458..475, block = 2, line = 0)
        }.document(width = 600, height = 300)

        val everySelectionValue = listOf(
            NormalizedRegion(0.02, 0.02, 0.98, 0.98),
            NormalizedRegion(0.0, 0.0, 0.9, 0.9),
            NormalizedRegion(0.05, 0.05, 1.0, 1.0),
        ).mapNotNull { region ->
            ElementRegionFilter.filter(document, region)
                ?.let { value(NutritionTableParser.parseWithDiagnostics(it)) }
        }

        assertTrue(
            "no selection may yield a value absent from the unfiltered label; got $everySelectionValue",
            everySelectionValue.all { it.compareTo(BigDecimal("53.5")) == 0 },
        )
    }
}

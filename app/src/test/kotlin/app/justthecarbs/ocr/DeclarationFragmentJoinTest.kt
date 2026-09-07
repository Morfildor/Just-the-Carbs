package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Pins the fragmented-multilingual-declaration recovery inside [NutritionDocumentModel]'s private
 * `NutrientDeclarationBuilder`, against the real device geometry that motivated it.
 *
 * ## The capture
 *
 * `docs/scan-evidence (6).zip`, `20260907-164816-836` — a nine-language nutrition panel whose
 * carbohydrate name alone spans four physically distinct printed rows (`Carbohydrate/ Kolhydrat/`,
 * `Hilihydraatit/ Kohlenhydrate/`, `Koolhydraten/ Hidratos Glucides/`, `Weglowodany: de carbono/`)
 * before the printed `58,9 g` appears on a fifth row, `Of which 58,9 g des` — reconstruction debris
 * from the neighbouring child-nutrient clause ("of which sugars"/"dont sucres"), not a real second
 * clause of its own.
 *
 * Before this fix: the row builder split the four name rows into two separate
 * `TOTAL_CARBOHYDRATE`-typed declarations (capped by `MAX_DECLARATION_ROWS = 3`), and the value row
 * — typed `OTHER`, since it names no nutrient of any kind — never joined either. The label read
 * `NotFound` despite every fact needed to read it correctly (row, basis, value) being present and
 * legible in the recognized text.
 */
class DeclarationFragmentJoinTest {

    private fun confidentValue(document: OcrDocument): BigDecimal {
        val reading = NutritionTableParser.parseWithDiagnostics(document).reading
        assertTrue("expected a confident reading, got $reading", reading is LabelReading.Confident)
        return (reading as LabelReading.Confident).candidate.value
    }

    /**
     * The real capture that motivated this fix. Non-vacuous by construction: this exact fixture,
     * unmodified, read `NotFound` on the code before this change — see this class's own KDoc and the
     * git history of `NutritionDocumentModel.kt` for the negative control performed by temporarily
     * reverting the production change and re-running this fixture.
     */
    @Test
    fun `the nine-language declaration fragmented across five rows reads its printed 58,9 g`() {
        val document = SeptemberSeventhSessionFixtures.capture164816()

        val value = confidentValue(document)

        assertEquals(0, value.compareTo(BigDecimal("58.9")))
    }

    @Test
    fun `the recovered declaration states PER_100_G, matching the label's own resolved column`() {
        val document = SeptemberSeventhSessionFixtures.capture164816()

        val reading = NutritionTableParser.parseWithDiagnostics(document).reading as LabelReading.Confident

        assertEquals(NutritionBasis.PER_100_G, reading.candidate.basis)
    }

    // ------------------------------------------------------------- safety: this can never absorb a child row

    /**
     * A row naming a child nutrient (sugars, fibre, polyols, …) is [NutritionRowKind.CARBOHYDRATE_CHILD]
     * unconditionally, checked BEFORE this stage ever runs — so a fragment-join can never be asked
     * to consider a row that names a child term, regardless of how many name-only rows precede it.
     * This is the same guarantee [MergedTotalRowRecovery] rests on, verified here at the level this
     * fix actually operates: the same real capture, deliberately checked for the OPPOSITE property —
     * that its recovered declaration never absorbs the genuine sugars clause that follows it on the
     * label (`Sockerarter/oista sugars/ varav`, `43,59`).
     */
    @Test
    fun `the recovered declaration never absorbs the sugars clause that follows it`() {
        val document = SeptemberSeventhSessionFixtures.capture164816()

        val value = confidentValue(document)

        // 43.59 (misread from the printed 4,5 g sugars figure) must never be reported as the total.
        assertTrue(
            "expected the carbohydrate value, not the sugars row's 43.59, got $value",
            value.compareTo(BigDecimal("43.59")) != 0,
        )
    }

    /**
     * A synthetic worst case: a name-only carbohydrate declaration immediately followed by an
     * `OTHER`-typed row that DOES name a different nutrient (fat) even though it also carries a
     * numeric value with no unit issues. [isNutrientlessValueContinuation]'s `nutrientAnchors`
     * check must refuse this — admitting it would let an unrelated nutrient's figure become the
     * carbohydrate total.
     */
    @Test
    fun `a value row naming a different nutrient is never absorbed as the carbohydrate value`() {
        fun element(text: String, left: Int, top: Int, right: Int, bottom: Int) =
            OcrElement(text, OcrBox(left, top, right, bottom), blockId = 0, lineId = 0)

        val document = OcrDocument(
            width = 1000,
            height = 400,
            elements = listOf(
                element("per 100 g", 380, 10, 520, 40),
                element("Koolhydraten", 20, 60, 260, 90),
                element("Glucides", 20, 100, 200, 130),
                // A row naming FAT, not carbohydrate — must never be treated as this declaration's
                // own value row, however tempting its bare position looks.
                element("Vetten", 20, 140, 200, 170),
                element("16,4", 400, 140, 470, 170),
                element("g", 475, 140, 495, 170),
            ),
        )

        val report = NutritionTableParser.parseWithDiagnostics(document)

        assertTrue(
            "expected no confident reading — the fat row must not supply a carbohydrate value, got ${report.reading}",
            report.reading !is LabelReading.Confident,
        )
    }

    /**
     * A synthetic control proving the join genuinely requires a SINGLE value cell on the continuation
     * row — a real column-ownership ambiguity, not merely two numbers anywhere on the row. Two
     * per-100 columns (a multilingual header collapse, exactly as [CrossColumnEvidenceReachTest]
     * documents on real labels) both claim a value on the same continuation row, so
     * [isNutrientlessValueContinuation]'s "exactly one aligned cell" check must refuse it rather than
     * guess which of the two the carbohydrate figure is.
     *
     * The first version of this fixture placed the second number outside any resolved column, where
     * [alignedCells] itself already filters it out — a single unambiguous cell survived and the
     * parser correctly read it, which is not what this test exists to prove. Two DISTINCT per-100
     * columns are required to make both numbers genuinely column-owned at once.
     */
    @Test
    fun `a value row with two column-owned numbers is never absorbed`() {
        fun element(text: String, left: Int, top: Int, right: Int, bottom: Int) =
            OcrElement(text, OcrBox(left, top, right, bottom), blockId = 0, lineId = 0)

        val document = OcrDocument(
            width = 1000,
            height = 400,
            elements = listOf(
                // Two distinct per-100 g headers, far enough apart that ColumnClassifier resolves
                // them as two separate columns rather than collapsing them into one printed column.
                element("per 100 g", 360, 10, 500, 40),
                element("per 100 g", 660, 10, 800, 40),
                element("Koolhydraten", 20, 60, 260, 90),
                element("Glucides", 20, 100, 200, 130),
                // Two bare numbers, one under each column — both genuinely column-owned.
                element("46", 390, 140, 460, 170),
                element("12", 690, 140, 760, 170),
            ),
        )

        val report = NutritionTableParser.parseWithDiagnostics(document)

        assertTrue(
            "expected no confident reading from a continuation row with two column-owned values, " +
                "got ${report.reading}",
            report.reading !is LabelReading.Confident,
        )
    }
}

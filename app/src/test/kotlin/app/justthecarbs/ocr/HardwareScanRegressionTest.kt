package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four labels from the 2026-09-01 device session, run through the whole parser.
 *
 * Each case states the outcome the *user* must get, not the behaviour of one stage — every one of
 * these failures was a composition failure in which no single stage was misbehaving. Geometry comes
 * from [HardwareLabelFixtures], transcribed from the evidence bundles.
 *
 * The safety claim these assertions defend, in the order that matters:
 *
 * 1. **Never a confident wrong value.** A wrong number the user doses from is the worst thing this
 *    app can produce; `NotFound` is always preferable.
 * 2. **Never a fabricated basis.** A right number under the wrong denominator is a wrong number.
 * 3. Only then: read as many labels automatically as possible.
 */
class HardwareScanRegressionTest {

    private fun report(document: OcrDocument) = NutritionTableParser.parseWithDiagnostics(document)

    private fun confident(document: OcrDocument): CarbCandidate? =
        (report(document).reading as? LabelReading.Confident)?.candidate

    private fun allCandidates(document: OcrDocument): List<CarbCandidate> =
        when (val reading = report(document).reading) {
            is LabelReading.Confident -> listOf(reading.candidate)
            is LabelReading.Ambiguous -> reading.candidates
            LabelReading.NotFound -> emptyList()
        }

    // ============================================================ A: the green drink

    /**
     * **The headline defect.** The drink prints `0,5 g/100 ml` and `1,3 g/250 ml`; the device
     * produced `1.3 g/100 ml`, which is 2.6x the true per-100 figure.
     *
     * This asserts the prohibition directly rather than asserting the desired value, because the
     * prohibition is the safety property: no route through the parser, now or later, may attach the
     * 250 ml figure to a per-100-ml basis.
     */
    @Test
    fun `the drink never reports the 250 ml value as a per-100-ml reading`() {
        val offending = allCandidates(HardwareLabelFixtures.greenDrink()).filter {
            it.value.compareTo(BigDecimal("1.3")) == 0 && it.basis == NutritionBasis.PER_100_ML
        }

        assertTrue(
            "1.3 g/100 ml is the 250 ml column wearing the 100 ml basis: $offending",
            offending.isEmpty(),
        )
    }

    /**
     * `0.59` is the printed `0,5 g` with its unit glyph recognised as a `9`. It is a well-formed
     * number on the correct row in the correct column, so only [CarbUnitAccompaniment]'s
     * "where is the unit?" question can refuse it.
     *
     * Note this is asserted about the *whole parser*, not about the filter in isolation — the filter
     * already had unit tests and the device still shipped the value, because nothing called it.
     */
    @Test
    fun `the drink never reports the unit-corrupted 0_59 token as a value`() {
        val offending = allCandidates(HardwareLabelFixtures.greenDrink())
            .filter { it.value.compareTo(BigDecimal("0.59")) == 0 }

        assertTrue("0.59 is 0,5 g with the g read as a 9: $offending", offending.isEmpty())
    }

    /**
     * The two printed columns must be resolved as two columns at their own positions.
     *
     * The device resolved **one** column, `PER_100_ML @ x=1199.5`, anchored over the 250 ml values —
     * so both cells bound to it and the parser reported "2 distinct total-carbohydrate readings".
     * The 100 ml values sit at centre ~941 and the 250 ml values at ~1171.
     */
    @Test
    fun `the drink resolves separate columns for its 100 ml and 250 ml headers`() {
        val document = HardwareLabelFixtures.greenDrink()
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)

        // Exactly one per-100 column, anchored over the 0,5 g cells (centre ~941). The device
        // emitted one column at x=1199.5 covering both printed columns, which is what let the
        // 250 ml figure inherit the 100 ml basis.
        val perHundred = columns.single { it.kind == NutritionColumnKind.PER_100_ML }
        assertEquals("the 100 ml column sits over the 0,5 values", 941.0, perHundred.centerX, 60.0)

        // The 250 ml column exists as a *position with no established basis*. `NutritionBasis` has
        // no member meaning "per 250 ml", so naming it UNKNOWN — rather than letting it be absorbed
        // into the per-100 span — is what keeps its cells out of a per-100 reading.
        val offBasis = columns.single { it.kind == NutritionColumnKind.UNKNOWN }
        assertEquals("the 250 ml column sits over the 13 g values", 1171.0, offBasis.centerX, 60.0)
    }

    /**
     * A 250 ml column is not a per-100 basis and must never be read as one. `NutritionBasis` has no
     * member meaning "per 250 ml", so the only safe handling is to refuse the cell — never to map it
     * onto [NutritionBasis.PER_100_ML].
     */
    @Test
    fun `the drink's 250 ml column never supplies a per-100 reading`() {
        val perHundredMl = allCandidates(HardwareLabelFixtures.greenDrink())
            .filter { it.basis == NutritionBasis.PER_100_ML }

        assertTrue(
            "only the 100 ml column may supply a per-100-ml value: $perHundredMl",
            perHundredMl.all { it.value.compareTo(BigDecimal("13")) != 0 },
        )
    }

    /**
     * With `0.59` refused and the 250 ml column excluded, nothing usable remains — which is the
     * correct, safe outcome. The user is routed to recovery rather than shown a number.
     */
    @Test
    fun `the drink refuses rather than guessing`() {
        assertEquals(LabelReading.NotFound, report(HardwareLabelFixtures.greenDrink()).reading)
    }

    // ============================================================ B: the cracker canary

    /**
     * **The canary.** This label already worked on the device and must be untouched by every change
     * in this pass. If any other assertion in this file is satisfied by loosening a rule, this one
     * fails.
     */
    @Test
    fun `the cracker still reads 72 g per 100 g`() {
        val candidate = confident(HardwareLabelFixtures.crackerBag())
            ?: throw AssertionError("expected a confident reading, got ${report(HardwareLabelFixtures.crackerBag()).reading}")

        assertEquals(0, candidate.value.compareTo(BigDecimal("72.0")))
        assertEquals(NutritionBasis.PER_100_G, candidate.basis)
    }

    /** The `9%` reference cell on the same row is never a carbohydrate quantity. */
    @Test
    fun `the cracker never reports its reference-intake percentage as carbohydrate`() {
        val values = allCandidates(HardwareLabelFixtures.crackerBag()).map { it.value }

        assertFalse("9 is the %RI cell", values.any { it.compareTo(BigDecimal("9")) == 0 })
    }

    /** Its per-portion column is read as a serving figure, not as a second per-100 reading. */
    @Test
    fun `the cracker's 22,5 g portion figure never becomes a per-100 reading`() {
        val values = allCandidates(HardwareLabelFixtures.crackerBag()).map { it.value }

        assertFalse("22.5 is the portion column", values.any { it.compareTo(BigDecimal("22.5")) == 0 })
    }

    // ============================================================ C: the Korean sauce

    /**
     * `Total Carb. 6g` must be found. The row also names `Fiber`, which under the whole-row rule
     * types it `CARBOHYDRATE_CHILD` and makes the total unreachable — the device found no total row
     * at all on this label.
     */
    @Test
    fun `the sauce finds its total carbohydrate row despite the fibre on the same row`() {
        val document = HardwareLabelFixtures.koreanSauce()
        val rows = LogicalRowBuilder.build(document)

        val carbRow = rows.single { it.text.contains("Total") && it.text.contains("Carb") }
        assertEquals(
            "a row stating Total Carb. explicitly is a total row, not a child row",
            NutritionRowKind.TOTAL_CARBOHYDRATE,
            RowClassifier.classify(carbRow),
        )
    }

    /**
     * **The basis prohibition for this label.** `6 g` is per 18 g serving. Until a typed per-serving
     * basis exists end to end, it may not be presented as per 100 g or per 100 ml under any
     * circumstances — that would be a fabricated denominator on a dosing input.
     */
    @Test
    fun `the sauce never labels its per-serving value as a per-100 figure`() {
        val offending = allCandidates(HardwareLabelFixtures.koreanSauce())
            .filter { it.value.compareTo(BigDecimal("6")) == 0 }

        assertTrue(
            "6 g is per 18 g serving; presenting it per 100 g/ml fabricates a basis: $offending",
            offending.isEmpty(),
        )
    }

    /** The fibre value on the same row is a child nutrient and can never be the total. */
    @Test
    fun `the sauce never reports its fibre value as total carbohydrate`() {
        val values = allCandidates(HardwareLabelFixtures.koreanSauce()).map { it.value }

        assertFalse("1 g is the fibre figure", values.any { it.compareTo(BigDecimal("1")) == 0 })
    }

    /** `2`, `4` and `22` are %DV figures, never grams of carbohydrate. */
    @Test
    fun `the sauce never reports a percent-DV number as carbohydrate`() {
        val values = allCandidates(HardwareLabelFixtures.koreanSauce()).map { it.value.stripTrailingZeros() }

        listOf("2", "4", "22", "500", "35").forEach { forbidden ->
            assertFalse(
                "$forbidden is a %DV or a milligram/calorie figure, not carbohydrate grams",
                values.any { it.compareTo(BigDecimal(forbidden)) == 0 },
            )
        }
    }

    /**
     * With no per-100 column on the label at all, the safe outcome is a refusal that routes to
     * recovery. What must never happen is a number presented under an invented basis.
     */
    @Test
    fun `the sauce produces no per-100 reading at all`() {
        assertEquals(LabelReading.NotFound, report(HardwareLabelFixtures.koreanSauce()).reading)
    }

    // ============================================================ D: the multilingual table

    /**
     * The three printed columns — 100 g, 9 g portion, RI — must be resolved separately. The device
     * resolved one `PER_100_G` anchored at **x=1439.5**, which sits over the 9 g portion values, so
     * the 59,2 g cell at centre ~1292 bound to nothing and the parser reported no usable per-100
     * cell.
     */
    @Test
    fun `the multilingual header anchors its per-100 column over the per-100 values`() {
        val document = HardwareLabelFixtures.multilingualTable()
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)

        val perHundred = columns.single { it.kind == NutritionColumnKind.PER_100_G }
        // After the damaged `o/` is split off, the basis tokens are `100` [1281..1343] and
        // `g|` [1351..1402], anchoring at 1341.5 — which sits over the `59,2` cell (centre 1292).
        assertEquals(
            "the per-100 column must anchor on its own basis tokens, not reach into the 9 g column",
            1292.0,
            perHundred.centerX,
            60.0,
        )
        assertNotEquals(
            "1439.5 is the anchor the device produced; it sits over the portion column",
            1439.5,
            perHundred.centerX,
            10.0,
        )
    }

    /**
     * The 9 g portion column must be a column of its **own**, separate from the per-100 one.
     *
     * It is [NutritionColumnKind.UNKNOWN] rather than `PER_SERVING`: `9 g` states a quantity, and
     * `NutritionBasis` has no member meaning "per 9 g", so the honest record is a position whose
     * meaning is not established. What matters is that it is not absorbed into the per-100 span —
     * that absorption is what put the device's anchor at x=1439.5 and lost the 59,2 g cell.
     */
    @Test
    fun `the multilingual header resolves a separate column for its 9 g portion`() {
        val document = HardwareLabelFixtures.multilingualTable()
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)

        val portion = columns.filter {
            it.kind == NutritionColumnKind.UNKNOWN || it.kind == NutritionColumnKind.PER_SERVING
        }
        assertEquals("expected one column for 'o/9g', got $columns", 1, portion.size)
        // 'o/9g' splits to `9g` [1481..1528] -> centre 1504.5, over the `54g` cell at 1484.
        assertEquals(1504.5, portion.single().centerX, 60.0)
    }

    /** The printed 59,2 g/100 g must be read, and read with the right basis. */
    @Test
    fun `the multilingual table reads 59_2 g per 100 g`() {
        val candidate = confident(HardwareLabelFixtures.multilingualTable())
            ?: throw AssertionError(
                "expected a confident reading, got ${report(HardwareLabelFixtures.multilingualTable()).reading}",
            )

        assertEquals(0, candidate.value.compareTo(BigDecimal("59.2")))
        assertEquals(NutritionBasis.PER_100_G, candidate.basis)
    }

    /**
     * `54g` is the printed `5,4 g` with its decimal point lost. 54 g of carbohydrate in a 9 g
     * portion is impossible, and the table's own arithmetic says so: 59,2/100 x 9 = 5,3.
     *
     * It must never be reported, and — per the brief — the impossibility must block it rather than
     * a repair silently rewriting it to 5.4.
     */
    @Test
    fun `the multilingual table never reports the decimal-damaged 54 g portion figure`() {
        val values = allCandidates(HardwareLabelFixtures.multilingualTable()).map { it.value }

        assertFalse(
            "54 g in a 9 g portion is impossible; it is 5,4 g with the point lost",
            values.any { it.compareTo(BigDecimal("54")) == 0 },
        )
    }

    /** The RI percentages are never carbohydrate quantities. */
    @Test
    fun `the multilingual table never reports its RI percentages as carbohydrate`() {
        val values = allCandidates(HardwareLabelFixtures.multilingualTable()).map { it.value }

        assertFalse("2 is the %RI cell", values.any { it.compareTo(BigDecimal("2")) == 0 })
    }

    /** Fibre (20,0 g) and sugars (1,5 g) are children and can never supply the total. */
    @Test
    fun `the multilingual table never reports a child nutrient as the total`() {
        val values = allCandidates(HardwareLabelFixtures.multilingualTable()).map { it.value }

        listOf("20.0", "1.5", "10.8").forEach { forbidden ->
            assertFalse(
                "$forbidden is a child nutrient or protein figure",
                values.any { it.compareTo(BigDecimal(forbidden)) == 0 },
            )
        }
    }
}

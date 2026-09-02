package app.justthecarbs.ocr

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [CrossColumnConsistency] against the arithmetic the four device labels actually print.
 *
 * The worked cases in the brief are the load-bearing ones and each appears below verbatim.
 */
class CrossColumnConsistencyTest {

    private fun check(perHundred: String, portion: String, printed: String) =
        CrossColumnConsistency.check(BigDecimal(perHundred), BigDecimal(portion), BigDecimal(printed))

    // ---------------------------------------------------------------- consistent, from real labels

    /** Green drink: `0,5 x 250 / 100 = 1,25`, printed rounded as `1,3`. */
    @Test
    fun `the drink's 250 ml portion figure is consistent with its per-100 figure`() {
        assertTrue(check("0.5", "250", "1.3") is CrossColumnConsistency.Verdict.Consistent)
    }

    /** Multilingual table: `59,2 x 9 / 100 = 5,328`, printed rounded as `5,4`. */
    @Test
    fun `the multilingual 9 g portion figure is consistent with its per-100 figure`() {
        assertTrue(check("59.2", "9", "5.4") is CrossColumnConsistency.Verdict.Consistent)
    }

    /** Cracker bag: `72,0 x 31,25 / 100 = 22,5`. The portion is exactly what the label prints. */
    @Test
    fun `the cracker's portion figure is consistent with its per-100 figure`() {
        assertTrue(check("72.0", "31.25", "22.5") is CrossColumnConsistency.Verdict.Consistent)
    }

    /** Sondey: `61,9 x 25 / 100 = 15,475`, printed `15,5`. */
    @Test
    fun `a quarter-portion rounding difference is still consistent`() {
        assertTrue(check("61.9", "25", "15.5") is CrossColumnConsistency.Verdict.Consistent)
    }

    // ---------------------------------------------------------------- conflicting, from real labels

    /**
     * **The drink's hazard.** `13` for a 250 ml portion cannot follow from `0,5 g/100 ml` — the
     * printed figure is `1,3` and the `13g` token is the 250 ml *column's* value read whole.
     */
    @Test
    fun `13 g for a 250 ml portion conflicts with a 0_5 per 100 ml figure`() {
        assertTrue(check("0.5", "250", "13") is CrossColumnConsistency.Verdict.Conflicting)
    }

    /**
     * **The multilingual hazard.** `54 g` of carbohydrate in a 9 g portion is arithmetically
     * impossible against `59,2 g/100 g`, and physically impossible outright — a 9 g portion cannot
     * contain 54 g of anything.
     */
    @Test
    fun `54 g in a 9 g portion conflicts with a 59_2 per 100 g figure`() {
        assertTrue(check("59.2", "9", "54") is CrossColumnConsistency.Verdict.Conflicting)
    }

    /** A portion figure larger than the portion itself is always a conflict. */
    @Test
    fun `a portion figure exceeding the portion mass conflicts`() {
        assertTrue(check("50", "10", "40") is CrossColumnConsistency.Verdict.Conflicting)
    }

    // ---------------------------------------------------------------- not comparable

    @Test
    fun `a zero portion size is not comparable`() {
        assertEquals(
            CrossColumnConsistency.Verdict.NotComparable,
            check("59.2", "0", "5.4"),
        )
    }

    @Test
    fun `a negative portion size is not comparable`() {
        assertEquals(
            CrossColumnConsistency.Verdict.NotComparable,
            check("59.2", "-9", "5.4"),
        )
    }

    /** A genuine zero-carbohydrate product is comparable and consistent, not "not comparable". */
    @Test
    fun `a zero carbohydrate product is consistent with a zero portion figure`() {
        assertTrue(check("0", "250", "0") is CrossColumnConsistency.Verdict.Consistent)
    }

    // ---------------------------------------------------------------- decimal-shift hypotheses

    /**
     * The multilingual `54g`: shifting the point left gives `5,4`, which is exactly consistent.
     * Reported as a hypothesis — never applied.
     */
    @Test
    fun `a lost decimal point is reported as a unique hypothesis`() {
        val hypothesis = CrossColumnConsistency.decimalShiftHypothesis(
            BigDecimal("59.2"), BigDecimal("9"), BigDecimal("54"),
        )

        assertEquals(0, BigDecimal("5.4").compareTo(hypothesis))
    }

    /** The drink's `13` likewise shifts to `1,3`. */
    @Test
    fun `the drink's 13 shifts to a consistent 1_3`() {
        val hypothesis = CrossColumnConsistency.decimalShiftHypothesis(
            BigDecimal("0.5"), BigDecimal("250"), BigDecimal("13"),
        )

        assertEquals(0, BigDecimal("1.3").compareTo(hypothesis))
    }

    /** A value that is already consistent yields no hypothesis — there is nothing to explain. */
    @Test
    fun `a consistent value yields no hypothesis`() {
        assertNull(
            CrossColumnConsistency.decimalShiftHypothesis(
                BigDecimal("59.2"), BigDecimal("9"), BigDecimal("5.4"),
            ),
        )
    }

    /**
     * A conflict no single decimal shift explains yields no hypothesis. The check reports the
     * conflict; it does not search for an interpretation that fits.
     */
    @Test
    fun `a conflict no shift explains yields no hypothesis`() {
        assertNull(
            CrossColumnConsistency.decimalShiftHypothesis(
                BigDecimal("59.2"), BigDecimal("9"), BigDecimal("871"),
            ),
        )
    }

    /**
     * **The uniqueness requirement, stated as its own case.** A hypothesis is offered only when
     * exactly one placement works. This is what stops the mechanism becoming a repair that always
     * finds something.
     */
    @Test
    fun `no hypothesis is offered when the printed value is already impossible in both directions`() {
        // 0 carbohydrate per 100 g: no shift of 7 can ever be consistent with a 50 g portion.
        assertNull(
            CrossColumnConsistency.decimalShiftHypothesis(
                BigDecimal("0"), BigDecimal("50"), BigDecimal("7"),
            ),
        )
    }

    /** A hypothesis is never negative or zero — those are not readings. */
    @Test
    fun `a shift producing a non-positive value is not a hypothesis`() {
        assertNull(
            CrossColumnConsistency.decimalShiftHypothesis(
                BigDecimal("0"), BigDecimal("100"), BigDecimal("0"),
            ),
        )
    }
}

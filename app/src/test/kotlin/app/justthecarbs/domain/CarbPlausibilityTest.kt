package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The plausibility barrier in front of the assisted reading path (1.0.3 P0).
 *
 * ## The defect this exists for
 *
 * A physical-device recording showed a red label printing roughly `7,9 g` of carbohydrate. The
 * assisted path offered the OCR-derived values `794` and `790` — and offered them through the
 * *same two full-emphasis buttons*, `Use / 100 g` and `Use / 100 ml`, that a perfectly ordinary
 * `7.9` gets. A user who taps one is committing an impossible figure with a single tap and nothing
 * on screen said it was impossible.
 *
 * `790 g` of carbohydrate in 100 g of food is not "unlikely", it is arithmetically impossible: the
 * carbohydrate cannot outweigh the food it is in. That is a fact the app can state, so it should.
 *
 * ## What this must NOT do
 *
 * It must not repair. `790` does not become `79.0` and does not become `7.90` — the digits and the
 * decimal point are exactly what OCR is unreliable about, so an app that "helpfully" moves the
 * point is guessing at the one thing it has the least standing to guess. It refuses and lets the
 * user type the number they can see.
 *
 * It must not clamp either: a silently clamped `100` looks like a value that was read.
 */
class CarbPlausibilityTest {

    private fun per100g(value: String) =
        CarbPlausibility.isPlausiblePer100(BigDecimal(value), NutritionBasis.PER_100_G)

    private fun per100ml(value: String) =
        CarbPlausibility.isPlausiblePer100(BigDecimal(value), NutritionBasis.PER_100_ML)

    // ---- 1. the measured defect ----------------------------------------------------------------

    /** The exact value observed on the device recording. */
    @Test
    fun `790 per 100 g is not plausible`() {
        assertFalse(per100g("790"))
    }

    /** The other value the same label produced. */
    @Test
    fun `794 per 100 g is not plausible`() {
        assertFalse(per100g("794"))
    }

    /**
     * And the same figures are impossible per 100 *ml* too, which is why both buttons must go.
     *
     * The per-ml ceiling is a density bound rather than a mass bound — a heavy syrup really can
     * exceed 100 g per 100 ml — so it is looser. It is nowhere near loose enough for 790.
     */
    @Test
    fun `790 per 100 ml is not plausible either`() {
        assertFalse(per100ml("790"))
    }

    // ---- 2. everything ordinary still passes ---------------------------------------------------

    /** The value actually printed on the label that produced the defect. */
    @Test
    fun `the printed value on the failing label remains plausible`() {
        assertTrue(per100g("7.9"))
    }

    /** Pure glucose powder. The boundary is inclusive because 100 g/100 g is real, not a limit. */
    @Test
    fun `100 per 100 g remains plausible`() {
        assertTrue(per100g("100"))
    }

    @Test
    fun `zero is plausible`() {
        assertTrue(per100g("0"))
    }

    @Test
    fun `a sugar syrup above 100 per 100 ml remains plausible`() {
        assertTrue(per100ml("120"))
    }

    @Test
    fun `a negative value is not plausible`() {
        assertFalse(per100g("-1"))
    }

    // ---- 3. the two things it must never do ----------------------------------------------------

    /**
     * No decimal is inferred. There is deliberately no API here that returns a corrected number,
     * so "did it silently fix it" is not a question this type can be asked — the only answer it
     * gives is yes or no.
     *
     * Asserted through the return type to keep it a behavioural claim rather than a comment: a
     * future `correct()` helper would not compile against this test's expectations.
     */
    @Test
    fun `the barrier answers only yes or no and never returns a repaired value`() {
        val answer: Boolean = CarbPlausibility.isPlausiblePer100(
            BigDecimal("790"),
            NutritionBasis.PER_100_G,
        )
        assertFalse(answer)
    }

    /**
     * The barrier agrees with the validator that already guards every remote value.
     *
     * This is the property that keeps one rule in the app rather than two that can drift: OCR and
     * Open Food Facts are different sources of the same kind of claim, and a figure impossible from
     * one is impossible from the other.
     */
    @Test
    fun `the barrier agrees with the remote value validator across the range`() {
        listOf("0", "7.9", "48.2", "99.9", "100", "100.1", "150", "200", "200.1", "790", "794")
            .forEach { raw ->
                NutritionBasis.entries.forEach { basis ->
                    val viaValidator =
                        NutritionValueValidator.validateCarbsPer100(raw.toDouble(), basis) != null
                    assertEquals(
                        "$raw / $basis must be judged identically by both",
                        viaValidator,
                        CarbPlausibility.isPlausiblePer100(BigDecimal(raw), basis),
                    )
                }
            }
    }

    /**
     * A value implausible on *both* bases has no acceptable interpretation at all.
     *
     * This is what the assisted screen needs to decide between "hide one button" and "offer no
     * ordinary accept path at all", so it is stated as its own question rather than left to the
     * caller to derive by calling twice and combining.
     */
    @Test
    fun `a value impossible on every basis is reported as having no plausible basis`() {
        assertFalse(CarbPlausibility.hasAnyPlausibleBasis(BigDecimal("790")))
        assertFalse(CarbPlausibility.hasAnyPlausibleBasis(BigDecimal("794")))
    }

    @Test
    fun `a value possible on at least one basis is reported as having one`() {
        assertTrue(CarbPlausibility.hasAnyPlausibleBasis(BigDecimal("7.9")))
        // Impossible per 100 g, legitimate per 100 ml. Still has a plausible reading.
        assertTrue(CarbPlausibility.hasAnyPlausibleBasis(BigDecimal("150")))
        assertFalse(per100g("150"))
        assertTrue(per100ml("150"))
    }
}

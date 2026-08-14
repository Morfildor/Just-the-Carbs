package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Comparing a package label against the value in use (development-pass brief §12).
 *
 * The comparison must classify and never decide: there is no outcome here that means "applied".
 */
class LabelComparisonTest {

    private fun compare(
        current: String,
        detected: String,
        currentBasis: NutritionBasis = NutritionBasis.PER_100_G,
        detectedBasis: NutritionBasis = NutritionBasis.PER_100_G,
    ) = LabelComparison.compare(
        current = BigDecimal(current),
        currentBasis = currentBasis,
        detected = BigDecimal(detected),
        detectedBasis = detectedBasis,
    )

    @Test
    fun `identical values match`() {
        assertTrue(compare("48.2", "48.2") is LabelVerdict.Match)
    }

    /**
     * The scale trap. `BigDecimal("48.2").equals(BigDecimal("48.20"))` is false because equals
     * compares scale as well as value, so an `equals`-based implementation would tell the user
     * their package disagrees with itself and ask them to resolve a conflict that is not there.
     */
    @Test
    fun `a trailing zero is the same value, not a mismatch`() {
        assertTrue(compare("48.2", "48.20") is LabelVerdict.Match)
        assertTrue(compare("48.20", "48.2") is LabelVerdict.Match)
        assertTrue(compare("48", "48.000") is LabelVerdict.Match)
    }

    @Test
    fun `different values are a mismatch carrying both figures`() {
        val verdict = compare("48.2", "47.3")

        assertTrue(verdict is LabelVerdict.Mismatch)
        verdict as LabelVerdict.Mismatch
        assertEquals(0, BigDecimal("48.2").compareTo(verdict.current))
        assertEquals(0, BigDecimal("47.3").compareTo(verdict.detected))
    }

    /** Even a tiny difference is a mismatch — the app does not decide what is close enough (§12). */
    @Test
    fun `a difference of one tenth is still a mismatch`() {
        assertTrue(compare("48.2", "48.3") is LabelVerdict.Mismatch)
    }

    /**
     * A per-100-g reading against a per-100-ml product is not a near-match, it is incomparable:
     * 9.4 and 9.6 would look almost equal while measuring different things, and reconciling them
     * needs a density the app does not have (§17).
     */
    @Test
    fun `a different basis is reported as incomparable rather than as a close match`() {
        val verdict = compare(
            current = "9.4",
            detected = "9.6",
            currentBasis = NutritionBasis.PER_100_ML,
            detectedBasis = NutritionBasis.PER_100_G,
        )

        assertTrue(verdict is LabelVerdict.BasisMismatch)
        verdict as LabelVerdict.BasisMismatch
        assertEquals(NutritionBasis.PER_100_ML, verdict.currentBasis)
        assertEquals(NutritionBasis.PER_100_G, verdict.detectedBasis)
    }

    /** Basis is checked before value, so identical numbers in different units never read as a match. */
    @Test
    fun `equal numbers in different bases are not a match`() {
        val verdict = compare(
            current = "9.4",
            detected = "9.4",
            currentBasis = NutritionBasis.PER_100_ML,
            detectedBasis = NutritionBasis.PER_100_G,
        )

        assertTrue(verdict is LabelVerdict.BasisMismatch)
    }
}

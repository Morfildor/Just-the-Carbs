package app.justthecarbs.ui

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * [parseDetectedLabelReading] guards the saved-state handoff a label reading takes back to the
 * calculator (§12), and closes a §5 (startup-hardening pass) hazard: the writing side
 * (`onUseValue`) always supplies a real [NutritionBasis], so a missing, blank or unrecognised basis
 * here means the round trip corrupted the value — and the rule is the same one the OCR
 * basis-unknown card enforces on the reading side: never guess the denominator, and never crash on
 * corrupted input.
 */
class LabelHandoffParsingTest {

    @Test
    fun `a well-formed carbs and basis pair parses`() {
        val result = parseDetectedLabelReading("48.2", NutritionBasis.PER_100_G.name)

        assertEquals(BigDecimal("48.2") to NutritionBasis.PER_100_G, result)
    }

    @Test
    fun `a null basis produces no reading rather than defaulting to grams`() {
        assertNull(parseDetectedLabelReading("48.2", null))
    }

    @Test
    fun `a blank basis produces no reading rather than defaulting to grams`() {
        assertNull(parseDetectedLabelReading("48.2", ""))
    }

    @Test
    fun `an unrecognised basis name produces no reading and does not crash`() {
        // A downgraded or corrupted saved-state value, not a normal path. `entries.firstOrNull`
        // inside the function is what keeps this from throwing the way `.valueOf` would.
        assertNull(parseDetectedLabelReading("48.2", "PER_100_FURLONG"))
    }

    @Test
    fun `a null carbs value produces no reading`() {
        assertNull(parseDetectedLabelReading(null, NutritionBasis.PER_100_G.name))
    }

    @Test
    fun `a blank carbs value produces no reading`() {
        assertNull(parseDetectedLabelReading("", NutritionBasis.PER_100_G.name))
    }

    @Test
    fun `an unparsable carbs value produces no reading and does not crash`() {
        assertNull(parseDetectedLabelReading("not-a-number", NutritionBasis.PER_100_G.name))
    }

    @Test
    fun `both halves missing produces no reading`() {
        assertNull(parseDetectedLabelReading(null, null))
    }

    @Test
    fun `a well-formed millilitre pair parses with the correct basis`() {
        val result = parseDetectedLabelReading("9.4", NutritionBasis.PER_100_ML.name)

        assertEquals(BigDecimal("9.4") to NutritionBasis.PER_100_ML, result)
    }
}

package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The value-and-basis seam.
 *
 * The rule these pin is one sentence: **a carbohydrate figure and what it is measured per are one
 * fact, and no stage may separate them.** A device recording produced `1.3 g / 100 ml` from a figure
 * the package prints as `1,3 g per 250 ml` precisely because the two travelled separately and the
 * basis was supplied at the end by a button.
 */
class CarbReadingTest {

    private fun perHundred(value: String, basis: NutritionBasis = NutritionBasis.PER_100_ML) =
        CarbReading(BigDecimal(value), CarbBasis.PerHundred(basis), BasisProvenance.DECLARED)

    private fun perQuantity(
        value: String,
        quantity: String,
        unit: NutritionBasis = NutritionBasis.PER_100_ML,
        word: String? = null,
    ) = CarbReading(
        BigDecimal(value),
        CarbBasis.PerQuantity(BigDecimal(quantity), unit, word),
        BasisProvenance.DECLARED,
    )

    // ---------------------------------------------------------------- identity includes the basis

    @Test
    fun `the same number under two different bases is two different readings`() {
        val per250 = perQuantity("1.3", "250")
        val per100 = perHundred("1.3")

        assertNotEquals(per250, per100)
        assertTrue("the numbers agree but the readings must not", !per250.sameReadingAs(per100))
    }

    @Test
    fun `a serving reading does not agree with the same number per 100 g`() {
        val perServing = perQuantity("6", "18", NutritionBasis.PER_100_G, "serving")
        val per100 = perHundred("6", NutritionBasis.PER_100_G)

        assertNotEquals(perServing, per100)
        assertTrue(!perServing.sameReadingAs(per100))
    }

    @Test
    fun `two readings of the same value and basis agree despite differing scale`() {
        assertTrue(perHundred("6").sameReadingAs(perHundred("6.00")))
    }

    @Test
    fun `deduplicating a list keeps readings that differ only in basis`() {
        val readings = listOf(perQuantity("1.3", "250"), perHundred("1.3"))
        assertEquals(2, readings.distinct().size)
    }

    // ---------------------------------------------------------------- normalization

    @Test
    fun `six grams per eighteen gram serving normalizes to thirty three per hundred`() {
        val derived = perQuantity("6", "18", NutritionBasis.PER_100_G, "serving").normalizedToPerHundred()

        assertEquals(BigDecimal("33.33333333"), derived!!.amount)
        assertEquals(CarbBasis.PerHundred(NutritionBasis.PER_100_G), derived.basis)
    }

    @Test
    fun `one point three per two hundred and fifty millilitres normalizes to about half a gram`() {
        val derived = perQuantity("1.3", "250").normalizedToPerHundred()

        assertEquals(0, derived!!.amount.compareTo(BigDecimal("0.52")))
        assertEquals(CarbBasis.PerHundred(NutritionBasis.PER_100_ML), derived.basis)
    }

    @Test
    fun `normalization never yields the printed number under a per-100 basis`() {
        // The exact defect: 1.3 per 250 ml must not become 1.3 per 100 ml.
        val derived = perQuantity("1.3", "250").normalizedToPerHundred()!!
        assertNotEquals(0, derived.amount.compareTo(BigDecimal("1.3")))
    }

    @Test
    fun `the original reading survives normalization`() {
        val printed = perQuantity("6", "18", NutritionBasis.PER_100_G, "serving")
        val derived = printed.normalizedToPerHundred()!!

        assertEquals(printed, derived.derivedFrom)
        assertEquals(BigDecimal("6"), printed.amount)
    }

    @Test
    fun `an already per-hundred reading normalizes to itself unchanged`() {
        val printed = perHundred("59.2", NutritionBasis.PER_100_G)
        val derived = printed.normalizedToPerHundred()

        assertEquals(printed, derived)
        assertNull("a printed reading was not derived from anything", derived!!.derivedFrom)
    }

    @Test
    fun `a serving of unknown size cannot be normalized`() {
        val reading = CarbReading(
            BigDecimal("6"),
            CarbBasis.PerUnknownServing("serving"),
            BasisProvenance.DECLARED,
        )
        assertNull(reading.normalizedToPerHundred())
    }

    @Test
    fun `normalization refuses a result that could not be a carbohydrate figure`() {
        // A serving mass misread as 1 g would turn 6 g into 600 g per 100 g.
        val reading = perQuantity("6", "1", NutritionBasis.PER_100_G)
        assertNull(reading.normalizedToPerHundred())
    }

    @Test
    fun `a basis quantity must be positive`() {
        listOf("0", "-5").forEach { bad ->
            runCatching { CarbBasis.PerQuantity(BigDecimal(bad), NutritionBasis.PER_100_G) }
                .onSuccess { throw AssertionError("$bad was accepted as a basis quantity") }
        }
    }

    // ---------------------------------------------------------------- labels

    @Test
    fun `every basis renders a label naming its quantity, never a bare unit`() {
        assertEquals("100 ml", CarbBasis.PerHundred(NutritionBasis.PER_100_ML).label)
        assertEquals("250 ml", CarbBasis.PerQuantity(BigDecimal("250"), NutritionBasis.PER_100_ML).label)
        assertEquals(
            "18 g serving",
            CarbBasis.PerQuantity(BigDecimal("18"), NutritionBasis.PER_100_G, "serving").label,
        )
        assertEquals("serving", CarbBasis.PerUnknownServing("serving").label)
    }

    @Test
    fun `a label never renders as a bare number`() {
        val bases = listOf(
            CarbBasis.PerHundred(NutritionBasis.PER_100_G),
            CarbBasis.PerQuantity(BigDecimal("9"), NutritionBasis.PER_100_G, "portion"),
            CarbBasis.PerUnknownServing(),
        )
        bases.forEach { basis ->
            assertTrue(
                "'${basis.label}' must name a unit or a serving word",
                basis.label.any { it.isLetter() },
            )
        }
    }
}

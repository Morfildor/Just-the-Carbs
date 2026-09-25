package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * Protein per 100 is validated under exactly the carbohydrate rules (design spec 2026-09-24,
 * section 9), and a [Product] cannot hold a protein figure without saying where it came from.
 */
class ProteinValueTest {

    private fun protein(raw: String?, basis: NutritionBasis = NutritionBasis.PER_100_G) =
        NutritionValueValidator.validateProteinPer100(raw, basis)

    @Test
    fun `a plain figure is accepted`() {
        assertEquals(0, BigDecimal("6.3").compareTo(protein("6.3")))
    }

    @Test
    fun `surrounding spaces do not refuse a figure`() {
        assertEquals(0, BigDecimal("6.3").compareTo(protein(" 6.3 ")))
    }

    @Test
    fun `missing, blank and unparsable text are refused`() {
        assertNull(protein(null))
        assertNull(protein(""))
        assertNull(protein("n/a"))
        assertNull(protein("6,3 g"))
    }

    @Test
    fun `non-finite and negative figures are refused`() {
        assertNull(protein("NaN"))
        assertNull(protein("Infinity"))
        assertNull(protein("-0.5"))
    }

    @Test
    fun `the gram and millilitre ceilings are the carbohydrate ones`() {
        assertEquals(0, BigDecimal("100").compareTo(protein("100")))
        assertNull(protein("100.1"))
        assertEquals(0, BigDecimal("200").compareTo(protein("200", NutritionBasis.PER_100_ML)))
        assertNull(protein("200.1", NutritionBasis.PER_100_ML))
    }

    /** One rule for both nutrients, so the two can never drift apart. */
    @Test
    fun `protein and carbohydrate validation agree across the range`() {
        val samples = listOf(-1.0, 0.0, 0.1, 6.3, 57.5, 99.99, 100.0, 100.01, 150.0, 199.9, 200.0, 250.0)
        for (basis in NutritionBasis.entries) {
            for (value in samples) {
                assertEquals(
                    "value $value under $basis",
                    NutritionValueValidator.validateCarbsPer100(value, basis),
                    protein(value.toString(), basis),
                )
            }
        }
    }

    private fun product(protein: BigDecimal?, origin: ProductDataOrigin?) = Product(
        barcode = "1",
        name = "Nutella",
        carbsPer100 = BigDecimal("57.5"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        proteinPer100 = protein,
        proteinOrigin = origin,
    )

    @Test(expected = IllegalArgumentException::class)
    fun `a protein figure without a source cannot be constructed`() {
        product(BigDecimal("6.3"), null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a protein source without a figure cannot be constructed`() {
        product(null, ProductDataOrigin.OPEN_FOOD_FACTS)
    }

    @Test
    fun `both or neither is a product`() {
        product(BigDecimal("6.3"), ProductDataOrigin.OPEN_FOOD_FACTS)
        product(null, null)
    }
}

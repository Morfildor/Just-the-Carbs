package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class PackageQuantityParserTest {

    private fun assertParses(input: String, amount: String, basis: NutritionBasis) {
        val parsed = PackageQuantityParser.parse(input)
        assertEquals("basis of '$input'", basis, parsed?.basis)
        assertEquals("amount of '$input'", 0, BigDecimal(amount).compareTo(parsed?.amount))
    }

    @Test
    fun `reads grams`() {
        assertParses("500 g", "500", NutritionBasis.PER_100_G)
        assertParses("250g", "250", NutritionBasis.PER_100_G)
    }

    @Test
    fun `reads millilitres`() {
        assertParses("500 ml", "500", NutritionBasis.PER_100_ML)
    }

    @Test
    fun `converts to the basis unit`() {
        assertParses("1 l", "1000", NutritionBasis.PER_100_ML)
        assertParses("33 cl", "330", NutritionBasis.PER_100_ML)
        assertParses("2 dl", "200", NutritionBasis.PER_100_ML)
        assertParses("1 kg", "1000", NutritionBasis.PER_100_G)
    }

    @Test
    fun `accepts a comma decimal, as Dutch packaging prints it`() {
        assertParses("1,5 L", "1500", NutritionBasis.PER_100_ML)
    }

    @Test
    fun `is case insensitive`() {
        assertParses("500 ML", "500", NutritionBasis.PER_100_ML)
    }

    @Test
    fun `rejects a quantity with no unit, because the unit is the point`() {
        assertNull(PackageQuantityParser.parse("500"))
    }

    /** "6 x 33 cl": one bottle or the crate? That question belongs to the user, not to a regex. */
    @Test
    fun `refuses to guess at a multipack`() {
        assertNull(PackageQuantityParser.parse("6 x 33 cl"))
        assertNull(PackageQuantityParser.parse("4 x 125 g"))
    }

    @Test
    fun `rejects unusable input`() {
        assertNull(PackageQuantityParser.parse(null))
        assertNull(PackageQuantityParser.parse(""))
        assertNull(PackageQuantityParser.parse("family pack"))
        assertNull(PackageQuantityParser.parse("0 g"))
        assertNull(PackageQuantityParser.parse("500 oz"))
    }

    @Test
    fun `infers a millilitre basis from a liquid quantity`() {
        assertEquals(NutritionBasis.PER_100_ML, PackageQuantityParser.inferBasis("1 l"))
    }

    @Test
    fun `infers a gram basis from a solid quantity`() {
        assertEquals(NutritionBasis.PER_100_G, PackageQuantityParser.inferBasis("500 g"))
    }

    @Test
    fun `falls back to grams when the quantity cannot be read`() {
        assertEquals(NutritionBasis.PER_100_G, PackageQuantityParser.inferBasis(null))
        assertEquals(NutritionBasis.PER_100_G, PackageQuantityParser.inferBasis("family pack"))
    }
}

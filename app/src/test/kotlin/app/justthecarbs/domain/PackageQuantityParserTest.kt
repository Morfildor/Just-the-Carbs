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
    fun `a parsed quantity still carries its basis`() {
        assertEquals(NutritionBasis.PER_100_ML, PackageQuantityParser.parse("1 l")!!.basis)
        assertEquals(NutritionBasis.PER_100_G, PackageQuantityParser.parse("500 g")!!.basis)
    }

    /**
     * `inferBasis` used to live here and answered `PER_100_G` for anything it could not read. It was
     * removed in the 2026-08-26 release pass; the three cases that covered it now live in
     * [PackageBasisResolverTest], which asserts the opposite outcome — unreadable means unresolved.
     *
     * This test exists so the removal is a stated fact rather than an absence someone might restore
     * by accident: a total function from quantity text to a basis is the defect, not a convenience.
     */
    @Test
    fun `unreadable quantities produce no quantity at all`() {
        assertNull(PackageQuantityParser.parse(null))
        assertNull(PackageQuantityParser.parse("family pack"))
    }
}

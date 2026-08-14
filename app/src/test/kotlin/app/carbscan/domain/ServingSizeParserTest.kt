package app.carbscan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * Cautious accept/reject rules from the countable-portions brief §5/§20. False negatives are
 * acceptable; false positive portion mappings are not — every accepted case must state an explicit
 * count-to-quantity relationship, never a bare weight.
 */
class ServingSizeParserTest {

    private fun assertParses(input: String, kind: PortionUnitKind, amountPerUnit: String, basis: NutritionBasis) {
        val parsed = ServingSizeParser.parse(input)
        assertEquals("kind of '$input'", kind, parsed?.kind)
        assertEquals("basis of '$input'", basis, parsed?.basis)
        val actualAmount = requireNotNull(parsed?.amountPerUnit) { "no amountPerUnit parsed for '$input'" }
        assertEquals("amountPerUnit of '$input'", 0, BigDecimal(amountPerUnit).compareTo(actualAmount))
    }

    @Test
    fun `reads a single slice with a parenthesised weight`() {
        assertParses("1 slice (36 g)", PortionUnitKind.SLICE, "36", NutritionBasis.PER_100_G)
    }

    @Test
    fun `normalizes a multi-count serving to a per-unit weight`() {
        assertParses("2 slices (70 g)", PortionUnitKind.SLICE, "35", NutritionBasis.PER_100_G)
    }

    @Test
    fun `reads a comma-separated weight`() {
        assertParses("1 slice, 35 g", PortionUnitKind.SLICE, "35", NutritionBasis.PER_100_G)
    }

    @Test
    fun `reads a decimal weight`() {
        assertParses("1 biscuit (12.5 g)", PortionUnitKind.BISCUIT, "12.5", NutritionBasis.PER_100_G)
    }

    @Test
    fun `reads an equals-separated weight`() {
        assertParses("1 cookie = 15 g", PortionUnitKind.COOKIE, "15", NutritionBasis.PER_100_G)
    }

    @Test
    fun `reads a bar with no comma`() {
        assertParses("1 bar (40 g)", PortionUnitKind.BAR, "40", NutritionBasis.PER_100_G)
    }

    @Test
    fun `reads a weight with no separator at all`() {
        assertParses("1 piece 22 g", PortionUnitKind.PIECE, "22", NutritionBasis.PER_100_G)
    }

    @Test
    fun `reads a millilitre serving`() {
        assertParses("1 scoop (30 ml)", PortionUnitKind.SCOOP, "30", NutritionBasis.PER_100_ML)
    }

    @Test
    fun `recognizes english portion as a serving synonym`() {
        assertParses("1 portion (25 g)", PortionUnitKind.SERVING, "25", NutritionBasis.PER_100_G)
    }

    @Test
    fun `recognizes dutch unit words for input recognition, independent of app language`() {
        assertParses("1 sneetje (35 g)", PortionUnitKind.SLICE, "35", NutritionBasis.PER_100_G)
        assertParses("2 sneetjes (70 g)", PortionUnitKind.SLICE, "35", NutritionBasis.PER_100_G)
        assertParses("1 reep (40 g)", PortionUnitKind.BAR, "40", NutritionBasis.PER_100_G)
        assertParses("1 zakje (12 g)", PortionUnitKind.SACHET, "12", NutritionBasis.PER_100_G)
    }

    @Test
    fun `rejects a bare weight with no count relationship`() {
        assertNull(ServingSizeParser.parse("30 g"))
    }

    @Test
    fun `rejects serving size prose with no explicit count`() {
        assertNull(ServingSizeParser.parse("serving size 40 g"))
    }

    @Test
    fun `rejects an approximate weight`() {
        assertNull(ServingSizeParser.parse("approx. 35 g"))
    }

    @Test
    fun `rejects a unit word with a weight but no leading count`() {
        assertNull(ServingSizeParser.parse("portion 25 g"))
    }

    @Test
    fun `rejects a count and unit word with no weight at all`() {
        assertNull(ServingSizeParser.parse("1 slice"))
    }

    @Test
    fun `rejects a unit word and number with no weight unit`() {
        assertNull(ServingSizeParser.parse("slice 35"))
    }

    @Test
    fun `rejects unusable or malformed input`() {
        assertNull(ServingSizeParser.parse(null))
        assertNull(ServingSizeParser.parse(""))
        assertNull(ServingSizeParser.parse("family pack"))
        assertNull(ServingSizeParser.parse("????"))
    }
}

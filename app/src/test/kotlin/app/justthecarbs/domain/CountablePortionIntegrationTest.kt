package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/**
 * The countable-portions brief's anchor case (§1, §20): there is exactly one carbohydrate formula.
 * [PortionResolver] only ever produces the `portion` value [CarbCalculator] already accepts.
 */
class CountablePortionIntegrationTest {

    @Test
    fun `2 slices at 36 g each, 42 g carbs per 100 g, resolves to 72 g and 30_2 g carbs`() {
        val resolvedGrams = PortionResolver.resolve(count = BigDecimal("2"), amountPerUnit = BigDecimal("36"))
        val result = CarbCalculator.calculate(
            carbsPer100 = BigDecimal("42"),
            portion = resolvedGrams,
            basis = NutritionBasis.PER_100_G,
        )

        assertEquals(0, BigDecimal("72").compareTo(resolvedGrams))
        assertEquals(0, BigDecimal("30.24").compareTo(result.exact))
        assertEquals(BigDecimal("30.2"), result.decimal)
        assertEquals(30, result.wholeGrams)
    }

    @Test
    fun `the basis carried by the resolved portion is untouched by the resolver`() {
        val resolvedMl = PortionResolver.resolve(count = BigDecimal("1.5"), amountPerUnit = BigDecimal("200"))
        val result = CarbCalculator.calculate(
            carbsPer100 = BigDecimal("9.4"),
            portion = resolvedMl,
            basis = NutritionBasis.PER_100_ML,
        )

        assertEquals(NutritionBasis.PER_100_ML, result.basis)
    }

    // ---- direct-carb portions (spec §13): the path with no weight anywhere -----------------------

    /**
     * The consumer workflow the whole pass exists for: scan → choose Slices → enter 4 → read the
     * total, having never been asked what a slice weighs.
     */
    @Test
    fun `4 slices at 14_2 g carbs each totals 56_8 g without any weight`() {
        val exact = DirectCarbCalculator.exactCarbs(
            count = BigDecimal("4"),
            carbsPerUnit = BigDecimal("14.2"),
        )

        assertEquals(0, BigDecimal("56.8").compareTo(exact))
    }

    /**
     * The two paths agree where they can both be applied, which is what makes the direct-carb route
     * a genuine shortcut rather than a second, differently-behaved calculator: 2 slices of 36 g at
     * 42 g/100 g is 15.12 g per slice, and counting two of those gives the same 30.24 g the
     * weight-based path produced above.
     */
    @Test
    fun `the direct-carb path agrees with the weight path on the same product`() {
        val perSliceCarbs = CarbCalculator.calculate(
            carbsPer100 = BigDecimal("42"),
            portion = BigDecimal("36"),
            basis = NutritionBasis.PER_100_G,
        ).exact

        val viaCount = DirectCarbCalculator.exactCarbs(count = BigDecimal("2"), carbsPerUnit = perSliceCarbs)

        assertEquals(0, BigDecimal("30.24").compareTo(viaCount))
    }

    @Test
    fun `a direct-carb meal item records no grams at all`() {
        val item = MealItem.directCarbs(
            productBarcode = "111",
            displayName = "Crackers",
            portionDescription = "4 slices",
            count = BigDecimal("4"),
            carbsPerUnit = BigDecimal("14.2"),
            exactCarbs = DirectCarbCalculator.exactCarbs(BigDecimal("4"), BigDecimal("14.2")),
            addedAt = java.time.Instant.EPOCH,
        )

        assertEquals(MealItemKind.DIRECT_CARBS, item.kind)
        assertEquals(null, item.resolvedAmount)
        assertEquals(0, BigDecimal("56.8").compareTo(item.exactCarbs))
    }
}

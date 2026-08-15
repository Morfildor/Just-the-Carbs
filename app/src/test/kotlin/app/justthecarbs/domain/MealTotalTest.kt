package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * The temporary meal total (development-pass brief §9, §29).
 *
 * The defect these tests exist to prevent is summing values that have already been rounded for
 * display. That produces a total which is individually explicable and collectively wrong, and it is
 * exactly the kind of error this app cannot afford: the number gets typed into a bolus calculator.
 */
class MealTotalTest {

    private var nextId = 1L

    private fun item(exactCarbs: String, basis: NutritionBasis = NutritionBasis.PER_100_G) = MealItem.weightBased(
        id = nextId++,
        productBarcode = "500${nextId}",
        displayName = "Item $nextId",
        portionDescription = "1 portion",
        resolvedAmount = BigDecimal("100"),
        basis = basis,
        carbsPer100 = BigDecimal(exactCarbs),
        exactCarbs = BigDecimal(exactCarbs),
        addedAt = Instant.parse("2026-08-14T10:00:00Z"),
    )

    @Test
    fun `an empty meal totals zero`() {
        assertEquals(0, MealTotal.exact(emptyList()).compareTo(BigDecimal.ZERO))
    }

    @Test
    fun `a single item totals its own exact value`() {
        val total = MealTotal.exact(listOf(item("28.4")))
        assertEquals(0, total.compareTo(BigDecimal("28.4")))
    }

    @Test
    fun `multiple items sum exactly`() {
        // The brief's own worked example: bread 28.4 + milk 9.6 = 38.0
        val total = MealTotal.exact(listOf(item("28.4"), item("9.6")))
        assertEquals(0, total.compareTo(BigDecimal("38.0")))
    }

    /**
     * The regression this whole design exists for (§9).
     *
     * 18.65 and 21.65 each round to one decimal as 18.7 and 21.7 (HALF_UP), which sum to 40.4; and
     * to whole grams as 19 and 22, which sum to 41. The correct answer is 40.30. Summing first and
     * formatting afterwards is the only way to get it.
     */
    @Test
    fun `the total is a sum of exact values, not of rounded display values`() {
        val total = MealTotal.exact(listOf(item("18.65"), item("21.65")))

        assertEquals(0, total.compareTo(BigDecimal("40.30")))

        val asResult = MealTotal.asResult(listOf(item("18.65"), item("21.65")))
        assertEquals("40.3", ResultFormatter.decimal(asResult.exact))
        assertEquals(40, asResult.wholeGrams)

        // What the defect would have produced, pinned so the difference is explicit.
        val sumOfRoundedDecimals = BigDecimal("18.7").add(BigDecimal("21.7"))
        assertEquals(0, sumOfRoundedDecimals.compareTo(BigDecimal("40.4")))
        val sumOfRoundedWholes = 19 + 22
        assertEquals(41, sumOfRoundedWholes)
    }

    @Test
    fun `many small items do not accumulate a rounding drift`() {
        // Ten items that each round DOWN to one decimal. Rounding per item and then summing loses
        // 0.4 g; summing exactly does not.
        val items = List(10) { item("1.04") }
        assertEquals(0, MealTotal.exact(items).compareTo(BigDecimal("10.40")))
    }

    @Test
    fun `removing an item removes exactly its own contribution`() {
        val bread = item("28.4")
        val milk = item("9.6")
        val afterRemoval = listOf(bread, milk).filterNot { it.id == milk.id }

        assertEquals(0, MealTotal.exact(afterRemoval).compareTo(BigDecimal("28.4")))
    }

    @Test
    fun `a cleared meal totals zero again`() {
        val items = listOf(item("28.4"), item("9.6"))
        assertEquals(0, MealTotal.exact(items - items.toSet()).compareTo(BigDecimal.ZERO))
    }

    /**
     * A meal can mix a per-100-g bread with a per-100-ml milk. Both contribute *grams of
     * carbohydrate*, so they add — the basis never becomes a conversion factor (§17). Nothing here
     * converts millilitres into grams; it converts neither, because both figures are already carbs.
     */
    @Test
    fun `grams and millilitre based items sum as plain carbohydrate grams`() {
        val bread = item("28.4", NutritionBasis.PER_100_G)
        val milk = item("9.6", NutritionBasis.PER_100_ML)

        assertEquals(0, MealTotal.exact(listOf(bread, milk)).compareTo(BigDecimal("38.0")))
    }

    @Test
    fun `a countable item contributes the carbs of its resolved amount`() {
        // 2 slices x 36 g = 72 g of a 42 g/100 g bread -> 30.24 g
        val twoSlices = MealItem.weightBased(
            id = 1,
            productBarcode = "5449000000996",
            displayName = "Sliced Bread",
            portionDescription = "2 slices",
            resolvedAmount = BigDecimal("72"),
            basis = NutritionBasis.PER_100_G,
            carbsPer100 = BigDecimal("42"),
            exactCarbs = CarbCalculator.calculate(
                BigDecimal("42"), BigDecimal("72"), NutritionBasis.PER_100_G,
            ).exact,
            addedAt = Instant.parse("2026-08-14T10:00:00Z"),
        )

        assertEquals(0, MealTotal.exact(listOf(twoSlices)).compareTo(BigDecimal("30.24")))
        assertEquals("2 slices", twoSlices.portionDescription)
    }

    @Test
    fun `a package fraction item keeps its human readable description`() {
        val halfPack = MealItem.weightBased(
            id = 1,
            productBarcode = "8710398",
            displayName = "Crackers",
            portionDescription = "½ pack",
            resolvedAmount = BigDecimal("200"),
            basis = NutritionBasis.PER_100_G,
            carbsPer100 = BigDecimal("70"),
            exactCarbs = CarbCalculator.calculate(
                BigDecimal("70"), BigDecimal("200"), NutritionBasis.PER_100_G,
            ).exact,
            addedAt = Instant.parse("2026-08-14T10:00:00Z"),
        )

        assertEquals(0, MealTotal.exact(listOf(halfPack)).compareTo(BigDecimal("140.0")))
        assertEquals("½ pack", halfPack.portionDescription)
    }

    /**
     * An item is a snapshot: changing the product it came from must not move a number the user has
     * already seen and accepted (§9).
     */
    @Test
    fun `an item keeps its own carbs when the source product value changes`() {
        val added = item("34.704")
        // The product is later corrected from 48.2 to 51.0 per 100 g. The stored item is unaffected
        // because it holds its own carbsPer100 and exactCarbs rather than a reference.
        val correctedProductCarbs = BigDecimal("51.0")

        assertEquals(0, MealTotal.exact(listOf(added)).compareTo(BigDecimal("34.704")))
        assertEquals(0, added.carbsPer100!!.compareTo(BigDecimal("34.704")))
        assertEquals(0, correctedProductCarbs.compareTo(BigDecimal("51.0")))
    }
}

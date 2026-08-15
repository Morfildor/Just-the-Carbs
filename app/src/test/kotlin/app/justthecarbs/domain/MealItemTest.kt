package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

// Suite: meal items of both kinds
// Invariant: a DIRECT_CARBS item has NO resolved grams — not zero, not empty, null. Faking a weight
// would make "4 slices" indistinguishable from having weighed 140 g, which the app cannot know.
class MealItemTest {

    private fun weightItem(exact: String) = MealItem.weightBased(
        productBarcode = "111",
        displayName = "Bread",
        portionDescription = "72 g",
        resolvedAmount = BigDecimal("72"),
        basis = NutritionBasis.PER_100_G,
        carbsPer100 = BigDecimal("48.2"),
        exactCarbs = BigDecimal(exact),
        addedAt = Instant.EPOCH,
    )

    private fun directItem(exact: String) = MealItem.directCarbs(
        productBarcode = "222",
        displayName = "Crackers",
        portionDescription = "4 slices",
        count = BigDecimal("4"),
        carbsPerUnit = BigDecimal("14.2"),
        exactCarbs = BigDecimal(exact),
        addedAt = Instant.EPOCH,
    )

    @Test
    fun `a direct-carb item has no resolved grams`() {
        val item = directItem("56.8")

        assertEquals(MealItemKind.DIRECT_CARBS, item.kind)
        assertNull("no weight is known, so none may be recorded", item.resolvedAmount)
        assertNull(item.basis)
        assertNull(item.carbsPer100)
    }

    @Test
    fun `a direct-carb item keeps its count and per-unit value`() {
        val item = directItem("56.8")

        assertEquals(0, BigDecimal("4").compareTo(item.count))
        assertEquals(0, BigDecimal("14.2").compareTo(item.carbsPerUnit))
    }

    @Test
    fun `a weight-based item has no count or per-unit value`() {
        val item = weightItem("34.704")

        assertEquals(MealItemKind.WEIGHT_BASED, item.kind)
        assertNull(item.count)
        assertNull(item.carbsPerUnit)
    }

    @Test
    fun `a mixed meal totals both kinds exactly`() {
        val total = MealTotal.exact(listOf(weightItem("34.704"), directItem("56.8")))

        assertEquals(0, BigDecimal("91.504").compareTo(total))
    }

    @Test
    fun `a direct-carb-only meal still produces a result`() {
        // The list is non-empty but no item carries a basis. Before this change the fallback read
        // items.firstOrNull()?.basis, which is null here — a non-empty list with no basis at all.
        val result = MealTotal.asResult(listOf(directItem("56.8")))

        assertEquals(0, BigDecimal("56.8").compareTo(result.exact))
        assertEquals(NutritionBasis.PER_100_G, result.basis)
    }

    @Test
    fun `a mixed meal takes its label basis from the first item that has one`() {
        val result = MealTotal.asResult(listOf(directItem("56.8"), weightItem("34.704")))

        assertEquals(NutritionBasis.PER_100_G, result.basis)
    }

    @Test
    fun `an empty meal totals zero`() {
        assertEquals(0, BigDecimal.ZERO.compareTo(MealTotal.exact(emptyList())))
    }
}

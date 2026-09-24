package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/** [withPortion]: resizing a meal line recalculates from the line's own stored figures only. */
class MealItemEditTest {

    private val addedAt = Instant.parse("2026-09-23T08:00:00Z")

    private val weighed = MealItem.weightBased(
        id = 4,
        productBarcode = "111",
        displayName = "Bread",
        portionDescription = "2 slices",
        resolvedAmount = BigDecimal("70"),
        basis = NutritionBasis.PER_100_G,
        carbsPer100 = BigDecimal("48.2"),
        exactCarbs = BigDecimal("33.740"),
        addedAt = addedAt,
    )

    private val counted = MealItem.directCarbs(
        id = 5,
        productBarcode = "222",
        displayName = "Crackers",
        portionDescription = "2 crackers",
        count = BigDecimal("2"),
        carbsPerUnit = BigDecimal("14.2"),
        exactCarbs = BigDecimal("28.4"),
        addedAt = addedAt,
    )

    @Test
    fun `a weighed line is recalculated by the calculator from its stored per-100 figure`() {
        val edited = weighed.withPortion(BigDecimal("35"), "35 g")!!

        assertEquals(
            CarbCalculator.calculate(BigDecimal("48.2"), BigDecimal("35"), NutritionBasis.PER_100_G).exact,
            edited.exactCarbs,
        )
        assertEquals(BigDecimal("35"), edited.resolvedAmount)
        assertEquals("35 g", edited.portionDescription)
    }

    @Test
    fun `a counted line is recalculated from its stored per-unit figure and gains no weight`() {
        val edited = counted.withPortion(BigDecimal("3"), "3 × 14.2 g carbs")!!

        assertEquals(DirectCarbCalculator.exactCarbs(BigDecimal("3"), BigDecimal("14.2")), edited.exactCarbs)
        assertEquals(BigDecimal("3"), edited.count)
        assertNull("no weight is invented on this path", edited.resolvedAmount)
        assertNull(edited.basis)
    }

    @Test
    fun `identity, name, basis and time are kept, so the line keeps its place`() {
        val edited = weighed.withPortion(BigDecimal("35"), "35 g")!!

        assertEquals(weighed.id, edited.id)
        assertEquals(weighed.productBarcode, edited.productBarcode)
        assertEquals(weighed.displayName, edited.displayName)
        assertEquals(weighed.addedAt, edited.addedAt)
        assertEquals(weighed.basis, edited.basis)
        assertEquals(weighed.carbsPer100, edited.carbsPer100)
        assertEquals(weighed.kind, edited.kind)
    }

    @Test
    fun `zero or a negative amount is not an edit`() {
        assertNull(weighed.withPortion(BigDecimal.ZERO, "0 g"))
        assertNull(counted.withPortion(BigDecimal("-1"), "-1"))
    }

    @Test
    fun `a row missing its kind's figures cannot be recalculated`() {
        assertNull(weighed.copy(carbsPer100 = null).withPortion(BigDecimal("10"), "10 g"))
        assertNull(counted.copy(carbsPerUnit = null).withPortion(BigDecimal("1"), "1"))
    }

    @Test
    fun `the editable amount is grams for a weighed line and the count for a counted one`() {
        assertEquals(BigDecimal("70"), weighed.editableAmount)
        assertEquals(BigDecimal("2"), counted.editableAmount)
    }
}

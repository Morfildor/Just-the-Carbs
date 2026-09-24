package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class MealStalenessTest {

    private val now: Instant = Instant.parse("2026-09-23T08:00:00Z")

    private fun line(addedAt: Instant, carbs: String = "10") = MealItem.weightBased(
        productBarcode = "1",
        displayName = "Bread",
        portionDescription = "20 g",
        resolvedAmount = BigDecimal("20"),
        basis = NutritionBasis.PER_100_G,
        carbsPer100 = BigDecimal("50"),
        exactCarbs = BigDecimal(carbs),
        addedAt = addedAt,
    )

    @Test
    fun `an empty meal is never stale`() {
        assertNull(MealStaleness.check(emptyList(), now))
    }

    @Test
    fun `a meal added to just now is not stale`() {
        assertNull(MealStaleness.check(listOf(line(now.minusSeconds(60))), now))
    }

    @Test
    fun `the threshold is two hours since the last addition, inclusive`() {
        val justUnder = now.minus(MealStaleness.AFTER).plusMillis(1)
        assertNull(MealStaleness.check(listOf(line(justUnder)), now))
        assertNotNull(MealStaleness.check(listOf(line(now.minus(MealStaleness.AFTER))), now))
        assertEquals(Duration.ofHours(2), MealStaleness.AFTER)
    }

    @Test
    fun `the most recent addition decides, not the oldest`() {
        // A long dinner: the plate eight hours ago would be stale on its own, the dessert is not.
        val items = listOf(line(now.minus(Duration.ofHours(8))), line(now.minus(Duration.ofMinutes(30))))
        assertNull(MealStaleness.check(items, now))
    }

    @Test
    fun `a stale meal reports its size, exact total and age`() {
        val items = listOf(
            line(now.minus(Duration.ofHours(10)), carbs = "18.65"),
            line(now.minus(Duration.ofHours(9)), carbs = "21.65"),
        )
        val stale = MealStaleness.check(items, now)!!
        assertEquals(2, stale.itemCount)
        assertEquals(0, BigDecimal("40.30").compareTo(stale.exactCarbs))
        assertEquals(Duration.ofHours(9), stale.sinceLastAdded)
    }

    @Test
    fun `a last addition far in the future means the clock moved, and is treated as stale`() {
        val stale = MealStaleness.check(listOf(line(now.plus(Duration.ofHours(3)))), now)
        assertNotNull(stale)
        assertEquals(Duration.ofHours(3), stale!!.sinceLastAdded)
    }

    @Test
    fun `a clock correction of seconds is not stale`() {
        assertNull(MealStaleness.check(listOf(line(now.plusSeconds(5))), now))
    }
}

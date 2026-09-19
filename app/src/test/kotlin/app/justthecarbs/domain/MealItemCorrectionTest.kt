package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Correcting one meal line to a different amount of the same thing.
 *
 * Every expected figure here is worked out by hand from the line's own snapshot — `48.2 × 85 / 100`
 * is `40.970` — rather than by calling the calculator under test, so a correction that consulted
 * anything but the snapshot, or rounded on the way, fails.
 */
class MealItemCorrectionTest {

    private val addedAt = Instant.parse("2026-09-18T12:00:00Z")

    /** Added from the calculator as "2 slices" of a weight-based unit: the snapshot keeps only 70 g. */
    private val bread = MealItem.weightBased(
        id = 42,
        productBarcode = "8710400000001",
        displayName = "Wholegrain Bread",
        portionDescription = "2 slices",
        resolvedAmount = BigDecimal("70"),
        basis = NutritionBasis.PER_100_G,
        carbsPer100 = BigDecimal("48.2"),
        exactCarbs = BigDecimal("33.740"),
        addedAt = addedAt,
    )

    private val juice = MealItem.weightBased(
        id = 7,
        productBarcode = "8710400000002",
        displayName = "Orange Juice",
        portionDescription = "200 ml",
        resolvedAmount = BigDecimal("200"),
        basis = NutritionBasis.PER_100_ML,
        carbsPer100 = BigDecimal("9.4"),
        exactCarbs = BigDecimal("18.800"),
        addedAt = addedAt,
    )

    private val crispbread = MealItem.directCarbs(
        id = 9,
        productBarcode = "8710400000003",
        displayName = "Crispbread",
        portionDescription = "4 slices",
        count = BigDecimal("4"),
        carbsPerUnit = BigDecimal("14.2"),
        exactCarbs = BigDecimal("56.8"),
        addedAt = addedAt,
    )

    // ---- what is edited ----------------------------------------------------------------------

    @Test
    fun `a weighed line is edited as the amount it resolved to, not the words it was added with`() {
        assertEquals(BigDecimal("70"), MealItemCorrection.amountOf(bread))
    }

    @Test
    fun `a direct-carb line is edited as its count`() {
        assertEquals(BigDecimal("4"), MealItemCorrection.amountOf(crispbread))
    }

    // ---- the recalculation -------------------------------------------------------------------

    @Test
    fun `a weighed correction multiplies the line's own per-100 figure`() {
        val corrected = MealItemCorrection.corrected(bread, BigDecimal("85"), "85 g")

        assertEquals(BigDecimal("85"), corrected.resolvedAmount)
        assertEquals(0, BigDecimal("40.97").compareTo(corrected.exactCarbs))
        assertEquals(BigDecimal("48.2"), corrected.carbsPer100)
        assertEquals(NutritionBasis.PER_100_G, corrected.basis)
        assertEquals("85 g", corrected.portionDescription)
    }

    @Test
    fun `the corrected figure is the calculator's exact product, never rounded`() {
        // 48.2 × 72.25 = 3482.450, and ÷ 100 as a scale shift is 34.82450 — every digit, same scale.
        // A rounded result (34.8, 34.82) or a divide() with its own scale would differ here.
        val corrected = MealItemCorrection.corrected(bread, BigDecimal("72.25"), "72.25 g")

        assertEquals(BigDecimal("34.82450"), corrected.exactCarbs)
        assertEquals(BigDecimal("34.82450"), MealItemCorrection.exactCarbs(bread, BigDecimal("72.25")))
    }

    @Test
    fun `a millilitre line is still measured per 100 ml after correction`() {
        val corrected = MealItemCorrection.corrected(juice, BigDecimal("250"), "250 ml")

        assertEquals(NutritionBasis.PER_100_ML, corrected.basis)
        assertEquals(BigDecimal("250"), corrected.resolvedAmount)
        assertEquals(BigDecimal("9.4"), corrected.carbsPer100)
        // 9.4 × 250 / 100 = 23.5
        assertEquals(0, BigDecimal("23.5").compareTo(corrected.exactCarbs))
    }

    @Test
    fun `a direct-carb correction multiplies the count by the line's carbs per unit`() {
        val corrected = MealItemCorrection.corrected(crispbread, BigDecimal("3"), "3 × 14.2 g carbs")

        assertEquals(BigDecimal("3"), corrected.count)
        assertEquals(BigDecimal("14.2"), corrected.carbsPerUnit)
        // 3 × 14.2 = 42.6
        assertEquals(BigDecimal("42.6"), corrected.exactCarbs)
        assertEquals("3 × 14.2 g carbs", corrected.portionDescription)
    }

    @Test
    fun `a direct-carb correction never invents a weight`() {
        val corrected = MealItemCorrection.corrected(crispbread, BigDecimal("3"), "3 × 14.2 g carbs")

        assertEquals(MealItemKind.DIRECT_CARBS, corrected.kind)
        assertNull(corrected.resolvedAmount)
        assertNull(corrected.basis)
        assertNull(corrected.carbsPer100)
    }

    @Test
    fun `a fractional count keeps every digit`() {
        // 1.5 × 14.2 = 21.30 exactly.
        assertEquals(BigDecimal("21.30"), MealItemCorrection.exactCarbs(crispbread, BigDecimal("1.5")))
    }

    // ---- what is kept ------------------------------------------------------------------------

    @Test
    fun `the replacement is the same line - id, product, name, position and shape are kept`() {
        val corrected = MealItemCorrection.corrected(bread, BigDecimal("85"), "85 g")

        assertEquals(42L, corrected.id)
        assertEquals("8710400000001", corrected.productBarcode)
        assertEquals("Wholegrain Bread", corrected.displayName)
        assertEquals(addedAt, corrected.addedAt)
        assertEquals(MealItemKind.WEIGHT_BASED, corrected.kind)
        assertNull(corrected.count)
        assertNull(corrected.carbsPerUnit)
    }

    @Test
    fun `a quick calculation line keeps having no barcode`() {
        val quick = bread.copy(productBarcode = null, displayName = "Quick calculation")

        val corrected = MealItemCorrection.corrected(quick, BigDecimal("85"), "85 g")

        assertNull(corrected.productBarcode)
        assertEquals("Quick calculation", corrected.displayName)
    }

    // ---- what counts as a correction ---------------------------------------------------------

    @Test
    fun `only a positive amount that differs from the line's own is a correction`() {
        assertTrue(MealItemCorrection.isCorrection(bread, BigDecimal("85")))
        assertTrue(MealItemCorrection.isCorrection(crispbread, BigDecimal("0.5")))

        assertFalse("unchanged", MealItemCorrection.isCorrection(bread, BigDecimal("70")))
        assertFalse("unchanged at another scale", MealItemCorrection.isCorrection(bread, BigDecimal("70.00")))
        assertFalse("zero", MealItemCorrection.isCorrection(bread, BigDecimal.ZERO))
        assertFalse("negative", MealItemCorrection.isCorrection(bread, BigDecimal("-5")))
        assertFalse("nothing typed", MealItemCorrection.isCorrection(bread, null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a replacement cannot be built from a zero amount`() {
        MealItemCorrection.corrected(bread, BigDecimal.ZERO, "0 g")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a replacement cannot be built from a negative count`() {
        MealItemCorrection.corrected(crispbread, BigDecimal("-1"), "-1 × 14.2 g carbs")
    }
}

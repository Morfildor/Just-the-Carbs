package app.justthecarbs.ui.home

import app.justthecarbs.domain.CarbCalculator
import app.justthecarbs.domain.DirectCarbCalculator
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionConversion
import app.justthecarbs.domain.PortionResolver
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.PortionUnitKind
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * What *Quick Add* would write for a remembered product — and, as importantly, when it refuses.
 *
 * Every figure is compared with the calculator's own primitives rather than with a literal, so the
 * tests pin "same calculation as the normal Add-to-meal" rather than "some number that happened to
 * be right today".
 */
class QuickAddPlanTest {

    private fun product(
        lastPortion: String? = null,
        mode: InputMode? = null,
        unitId: Long? = null,
        count: String? = null,
        basis: NutritionBasis = NutritionBasis.PER_100_G,
        carbsPer100: String = "48.2",
        favorite: Boolean = false,
    ) = Product(
        barcode = "8710000000001",
        name = "Wholegrain Bread",
        carbsPer100 = BigDecimal(carbsPer100),
        basis = basis,
        dataSource = ProductDataOrigin.MANUAL,
        verificationStatus = VerificationStatus.USER_VERIFIED,
        lastPortion = lastPortion?.let(::BigDecimal),
        lastInputMode = mode,
        lastSelectedPortionUnitId = unitId,
        lastCount = count?.let(::BigDecimal),
        favorite = favorite,
    )

    private fun unit(
        conversion: PortionConversion,
        id: Long = 7,
        barcode: String = "8710000000001",
    ) = PortionUnit(
        id = id,
        productBarcode = barcode,
        kind = PortionUnitKind.SLICE,
        customLabel = null,
        conversion = conversion,
        dataSource = ProductDataOrigin.MANUAL,
        verificationStatus = VerificationStatus.USER_VERIFIED,
        verifiedAt = Instant.EPOCH,
        originalRemoteConversion = null,
        latestRemoteConversion = null,
        rawRemoteServingText = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private val slice35g = PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G)
    private val slice14carbs = PortionConversion.DirectCarbs(BigDecimal("14.2"))

    // ---- eligible shapes --------------------------------------------------------------------

    @Test
    fun `a remembered weight becomes a weighed line with the calculator's exact figure`() {
        val plan = quickAddPlan(product(lastPortion = "72", mode = InputMode.GRAMS), unit = null)

        plan as QuickAddPlan.Weighed
        assertEquals(BigDecimal("72"), plan.resolvedAmount)
        assertEquals(NutritionBasis.PER_100_G, plan.basis)
        assertEquals(BigDecimal("48.2"), plan.carbsPer100)
        assertEquals(
            CarbCalculator.calculate(BigDecimal("48.2"), BigDecimal("72"), NutritionBasis.PER_100_G).exact,
            plan.exactCarbs,
        )
        assertEquals(InputMode.GRAMS, plan.inputMode)
        assertNull(plan.portionUnitId)
        assertNull(plan.count)
        assertEquals("Wholegrain Bread", plan.displayName)
    }

    /** Products remembered before input modes existed carry no mode; their portion is grams. */
    @Test
    fun `a legacy weight with no remembered mode is still a weighed line`() {
        val plan = quickAddPlan(product(lastPortion = "250", mode = null), unit = null)

        plan as QuickAddPlan.Weighed
        assertEquals(InputMode.GRAMS, plan.inputMode)
        assertEquals(BigDecimal("250"), plan.resolvedAmount)
    }

    @Test
    fun `a count against a weight-based unit resolves through PortionResolver then CarbCalculator`() {
        val plan = quickAddPlan(
            product(mode = InputMode.PORTION_UNIT, unitId = 7, count = "2"),
            unit(slice35g),
        )

        plan as QuickAddPlan.Weighed
        val resolved = PortionResolver.resolve(BigDecimal("2"), BigDecimal("35"))
        assertEquals(resolved, plan.resolvedAmount)
        assertEquals(
            CarbCalculator.calculate(BigDecimal("48.2"), resolved, NutritionBasis.PER_100_G).exact,
            plan.exactCarbs,
        )
        // Recorded as the user chose it — two slices — not as the 70 g behind it.
        assertEquals(InputMode.PORTION_UNIT, plan.inputMode)
        assertEquals(7L, plan.portionUnitId)
        assertEquals(BigDecimal("2"), plan.count)
    }

    @Test
    fun `a count against a direct-carb unit carries no weight at all`() {
        val plan = quickAddPlan(
            product(lastPortion = "65", mode = InputMode.PORTION_UNIT, unitId = 7, count = "4"),
            unit(slice14carbs),
        )

        plan as QuickAddPlan.DirectCarbs
        assertEquals(BigDecimal("4"), plan.count)
        assertEquals(BigDecimal("14.2"), plan.carbsPerUnit)
        assertEquals(DirectCarbCalculator.exactCarbs(BigDecimal("4"), BigDecimal("14.2")), plan.exactCarbs)
        assertEquals(7L, plan.portionUnitId)
        assertEquals(InputMode.PORTION_UNIT, plan.inputMode)
    }

    /**
     * The whole promise of the pill: the figure it adds is the figure the card prints. Both come
     * from `rememberedCarbs`, and this pins that they cannot drift apart for any eligible shape.
     */
    @Test
    fun `the added figure is always the figure the card shows`() {
        val cases = listOf(
            product(lastPortion = "72", mode = InputMode.GRAMS) to null,
            product(mode = InputMode.PORTION_UNIT, unitId = 7, count = "1.5") to unit(slice35g),
            product(mode = InputMode.PORTION_UNIT, unitId = 7, count = "3") to unit(slice14carbs),
        )
        for ((p, u) in cases) {
            val plan = quickAddPlan(p, u)
            assertNotNull("eligible: $p", plan)
            assertEquals(rememberedCarbs(p, u)!!.exactCarbs, plan!!.exactCarbs)
        }
    }

    // ---- refusals ---------------------------------------------------------------------------

    @Test
    fun `a favourite that was never used cannot be quick-added`() {
        assertNull(quickAddPlan(product(favorite = true), unit = null))
    }

    @Test
    fun `a zero portion is not a portion`() {
        assertNull(quickAddPlan(product(lastPortion = "0", mode = InputMode.GRAMS), unit = null))
    }

    @Test
    fun `a zero count is not a portion`() {
        assertNull(
            quickAddPlan(product(mode = InputMode.PORTION_UNIT, unitId = 7, count = "0"), unit(slice35g)),
        )
    }

    /**
     * The one place this is stricter than the card's label. `rememberedCarbs` still describes the
     * old gram amount here, truthfully labelled as grams; adding it would put 72 g on the plate of
     * someone whose last choice was "2 slices".
     */
    @Test
    fun `a count whose unit is gone falls back to a label but never to an action`() {
        val lost = product(lastPortion = "72", mode = InputMode.PORTION_UNIT, unitId = 7, count = "2")

        assertNotNull("the card still has something truthful to show", rememberedCarbs(lost, null))
        assertNull("but nothing may be added", quickAddPlan(lost, unit = null))
    }

    @Test
    fun `a count with no remembered count is refused`() {
        val noCount = product(lastPortion = "72", mode = InputMode.PORTION_UNIT, unitId = 7, count = null)
        assertNull(quickAddPlan(noCount, unit(slice35g)))
    }

    @Test
    fun `a unit belonging to another product is refused`() {
        val p = product(mode = InputMode.PORTION_UNIT, unitId = 7, count = "2")
        assertNull(quickAddPlan(p, unit(slice35g, barcode = "someone-else")))
    }

    @Test
    fun `a weight-based unit in the other basis is refused`() {
        val p = product(mode = InputMode.PORTION_UNIT, unitId = 7, count = "2", basis = NutritionBasis.PER_100_ML)
        assertNull(quickAddPlan(p, unit(slice35g)))
    }

    @Test
    fun `a product without a barcode is refused`() {
        assertNull(quickAddPlan(product(lastPortion = "72", mode = InputMode.GRAMS).copy(barcode = ""), unit = null))
    }

    @Test
    fun `a grams-mode product with no remembered portion is refused`() {
        assertNull(quickAddPlan(product(lastPortion = null, mode = InputMode.GRAMS), unit = null))
    }

    // ---- local alias (1.0.8) -------------------------------------------------------------------

    @Test
    fun `a weighed plan carries the product's canonical name when it has no alias`() {
        val plan = quickAddPlan(product(lastPortion = "72", mode = InputMode.GRAMS), unit = null)
        assertEquals("Wholegrain Bread", plan?.displayName)
    }

    @Test
    fun `a weighed plan carries the user's own name when there is one`() {
        // The meal line a Quick Add creates *now* says what the user currently calls the product.
        val renamed = product(lastPortion = "72", mode = InputMode.GRAMS).copy(localAlias = "Breakfast bread")
        assertEquals("Breakfast bread", quickAddPlan(renamed, unit = null)?.displayName)
    }

    @Test
    fun `a countable plan carries the user's own name`() {
        val renamed = product(mode = InputMode.PORTION_UNIT, unitId = 7, count = "2")
            .copy(localAlias = "Breakfast bread")
        val plan = quickAddPlan(renamed, unit(PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G)))
        assertEquals("Breakfast bread", plan?.displayName)
    }

    @Test
    fun `a direct-carb plan carries the user's own name`() {
        val renamed = product(mode = InputMode.PORTION_UNIT, unitId = 7, count = "2")
            .copy(localAlias = "Breakfast bread")
        val plan = quickAddPlan(renamed, unit(PortionConversion.DirectCarbs(BigDecimal("14.2"))))
        assertEquals("Breakfast bread", plan?.displayName)
    }

    @Test
    fun `renaming changes only the name a plan carries, never its figures`() {
        // The rename is presentation. Eligibility, the remembered portion and the arithmetic are
        // identical either side of it — asserted by comparing the two plans field for field with
        // the name put back, so a future field is covered without a new assertion.
        val plain = product(lastPortion = "72", mode = InputMode.GRAMS)
        val renamed = plain.copy(localAlias = "Breakfast bread")

        val before = quickAddPlan(plain, unit = null) as QuickAddPlan.Weighed
        val after = quickAddPlan(renamed, unit = null) as QuickAddPlan.Weighed

        assertEquals("Breakfast bread", after.displayName)
        assertEquals(before, after.copy(displayName = before.displayName))
    }
}

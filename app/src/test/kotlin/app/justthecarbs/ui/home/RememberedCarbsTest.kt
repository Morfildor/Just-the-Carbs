package app.justthecarbs.ui.home

import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionConversion
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.PortionUnitKind
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * What a Home card is allowed to claim the user last did (P0 correctness audit).
 *
 * The defect these pin: `recordUse` keeps `lastPortion` when a direct-carb portion is used, because
 * no weight exists on that path and nulling a previously-known gram amount would lose real
 * information. That is correct on its own terms, but it means a product can legitimately hold
 *
 *     lastInputMode = PORTION_UNIT, lastCount = 4, unit = DirectCarbs(14.2), lastPortion = 65
 *
 * where the 65 g is a *stale* leftover from an earlier weight-based use. The old Home card read
 * `lastPortion` unconditionally, so it printed the count in the label ("4 slices") while computing
 * the number from the stale grams — a wrong carb figure under a correct-looking portion, which is
 * the worst shape this bug could take.
 */
class RememberedCarbsTest {

    private val now = Instant.parse("2026-08-16T10:00:00Z")

    private fun product(
        carbsPer100: String = "48.2",
        basis: NutritionBasis = NutritionBasis.PER_100_G,
        lastPortion: String? = null,
        lastCount: String? = null,
        lastInputMode: InputMode? = null,
        lastUnitId: Long? = null,
    ) = Product(
        barcode = "1234567890123",
        name = "Test bread",
        carbsPer100 = BigDecimal(carbsPer100),
        basis = basis,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
        lastPortion = lastPortion?.let(::BigDecimal),
        lastCount = lastCount?.let(::BigDecimal),
        lastInputMode = lastInputMode,
        lastSelectedPortionUnitId = lastUnitId,
    )

    private fun unit(conversion: PortionConversion, id: Long = 1L) = PortionUnit(
        id = id,
        productBarcode = "1234567890123",
        kind = PortionUnitKind.SLICE,
        customLabel = null,
        conversion = conversion,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
        verifiedAt = null,
        originalRemoteConversion = null,
        latestRemoteConversion = null,
        rawRemoteServingText = null,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `a direct-carb use is count times carbs per unit`() {
        // The headline case from the brief: 4 slices x 14.2 g carbs = 56.8 g. No weight is involved
        // at any point, and none may be invented.
        val p = product(lastCount = "4", lastInputMode = InputMode.PORTION_UNIT, lastUnitId = 1L)
        val u = unit(PortionConversion.DirectCarbs(BigDecimal("14.2")))

        val remembered = rememberedCarbs(p, u)

        assertTrue(remembered is RememberedCarbs.Countable)
        assertEquals(0, BigDecimal("56.8").compareTo(remembered!!.exactCarbs))
        assertEquals(BigDecimal("4"), (remembered as RememberedCarbs.Countable).count)
    }

    @Test
    fun `a stale gram portion cannot influence a direct-carb result`() {
        // The actual defect. `lastPortion = 65` survives from an earlier weight-based use of the
        // same product; the old code computed 48.2/100 x 65 = 31.33 g and displayed it under the
        // label "4 slices". The correct answer depends only on the count and the conversion.
        val p = product(
            lastPortion = "65",
            lastCount = "4",
            lastInputMode = InputMode.PORTION_UNIT,
            lastUnitId = 1L,
        )
        val u = unit(PortionConversion.DirectCarbs(BigDecimal("14.2")))

        val remembered = rememberedCarbs(p, u)

        assertEquals(0, BigDecimal("56.8").compareTo(remembered!!.exactCarbs))
        // Explicitly *not* the stale-gram answer, so a regression cannot pass this by coincidence.
        assertTrue(BigDecimal("31.33").compareTo(remembered.exactCarbs) != 0)
    }

    @Test
    fun `a direct-carb product with no history at all still resolves`() {
        // A product only ever used as a count has no gram figure to fall back to. The old code's
        // `lastPortion?.let { ... }` returned null here and the card said "never used" despite a
        // perfectly good remembered count.
        val p = product(lastCount = "2", lastInputMode = InputMode.PORTION_UNIT, lastUnitId = 1L)
        val u = unit(PortionConversion.DirectCarbs(BigDecimal("14.2")))

        val remembered = rememberedCarbs(p, u)

        assertEquals(0, BigDecimal("28.4").compareTo(remembered!!.exactCarbs))
    }

    @Test
    fun `a weight-based countable use resolves through the per-100 figure`() {
        // 3 slices x 35 g = 105 g, then 48.2 g/100 g x 105 g = 50.61 g. This path must keep going
        // through CarbCalculator so the app retains one formula for anything with a weight.
        val p = product(lastCount = "3", lastInputMode = InputMode.PORTION_UNIT, lastUnitId = 1L)
        val u = unit(
            PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G),
        )

        val remembered = rememberedCarbs(p, u)

        assertTrue(remembered is RememberedCarbs.Countable)
        assertEquals(0, BigDecimal("50.610").compareTo(remembered!!.exactCarbs))
    }

    @Test
    fun `a weight-based countable use ignores a stale gram portion too`() {
        // Same stale-state hazard as the direct-carb case: the count and the unit decide, not
        // whatever `lastPortion` happens to hold.
        val p = product(
            lastPortion = "500",
            lastCount = "3",
            lastInputMode = InputMode.PORTION_UNIT,
            lastUnitId = 1L,
        )
        val u = unit(PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G))

        val remembered = rememberedCarbs(p, u)

        assertEquals(0, BigDecimal("50.610").compareTo(remembered!!.exactCarbs))
    }

    @Test
    fun `a gram use still reads the remembered portion`() {
        // The unchanged original behaviour, pinned so the fix cannot regress it.
        val p = product(lastPortion = "65", lastInputMode = InputMode.GRAMS)

        val remembered = rememberedCarbs(p, null)

        assertTrue(remembered is RememberedCarbs.Weight)
        assertEquals(0, BigDecimal("31.330").compareTo(remembered!!.exactCarbs))
    }

    @Test
    fun `a gram use with no remembered portion is not a result`() {
        val p = product(lastInputMode = InputMode.GRAMS)

        assertNull(rememberedCarbs(p, null))
    }

    @Test
    fun `countable mode without a resolvable unit falls back to the remembered weight`() {
        // The unit row was deleted (FK cascade) but the product still says PORTION_UNIT. Falling
        // back to the gram amount is right here because that value was genuinely resolved once;
        // what must never happen is pairing it with a count label, which the typed result prevents.
        val p = product(
            lastPortion = "65",
            lastCount = "4",
            lastInputMode = InputMode.PORTION_UNIT,
            lastUnitId = 1L,
        )

        val remembered = rememberedCarbs(p, null)

        assertTrue(remembered is RememberedCarbs.Weight)
        assertEquals(0, BigDecimal("31.330").compareTo(remembered!!.exactCarbs))
    }

    @Test
    fun `countable mode with a unit but no count is not a countable result`() {
        // A half-written state: a unit is selected but no count was ever recorded. There is no
        // count to multiply, so this must not claim a countable result.
        val p = product(lastInputMode = InputMode.PORTION_UNIT, lastUnitId = 1L)
        val u = unit(PortionConversion.DirectCarbs(BigDecimal("14.2")))

        assertNull(rememberedCarbs(p, u))
    }

    @Test
    fun `a direct-carb result carries no basis to display`() {
        // DirectCarbs has no per-100 basis and CarbResult.basis is non-null, which is why this
        // returns its own type rather than a CarbResult. Pinning it stops a later "simplification"
        // from routing this back through CarbCalculator with an invented basis.
        val p = product(lastCount = "4", lastInputMode = InputMode.PORTION_UNIT, lastUnitId = 1L)
        val u = unit(PortionConversion.DirectCarbs(BigDecimal("14.2")))

        val remembered = rememberedCarbs(p, u) as RememberedCarbs.Countable

        assertNull(remembered.resolvedAmount)
    }

    @Test
    fun `a weight-based countable result exposes the amount it resolved to`() {
        // The mirror of the case above: a weight-based count *does* have grams behind it, and the
        // card may show them.
        val p = product(lastCount = "3", lastInputMode = InputMode.PORTION_UNIT, lastUnitId = 1L)
        val u = unit(PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G))

        val remembered = rememberedCarbs(p, u) as RememberedCarbs.Countable

        assertEquals(0, BigDecimal("105").compareTo(remembered.resolvedAmount!!))
    }
}

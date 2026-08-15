package app.justthecarbs.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * [PortionUnit] mirrors [Product]'s provenance/verification split and remote-refresh gating
 * (countable-portions brief §2, §9) — these tests pin the same two rules for the per-unit case.
 */
class PortionUnitTest {

    private fun unit(
        dataSource: ProductDataOrigin = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
        amountPerUnit: BigDecimal = BigDecimal("36"),
        latestRemoteAmountPerUnit: BigDecimal? = null,
    ) = PortionUnit(
        productBarcode = "5449000000996",
        kind = PortionUnitKind.SLICE,
        customLabel = null,
        conversion = PortionConversion.WeightBased(amountPerUnit, NutritionBasis.PER_100_G),
        dataSource = dataSource,
        verificationStatus = verificationStatus,
        verifiedAt = null,
        originalRemoteConversion = null,
        latestRemoteConversion = latestRemoteAmountPerUnit?.let {
            PortionConversion.WeightBased(it, NutritionBasis.PER_100_G)
        },
        rawRemoteServingText = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `an unverified remote unit is remote-refreshable`() {
        assertTrue(unit(dataSource = ProductDataOrigin.OPEN_FOOD_FACTS, verificationStatus = VerificationStatus.UNVERIFIED).isRemoteRefreshable)
    }

    @Test
    fun `a user-verified remote unit is not remote-refreshable`() {
        assertFalse(unit(dataSource = ProductDataOrigin.OPEN_FOOD_FACTS, verificationStatus = VerificationStatus.USER_VERIFIED).isRemoteRefreshable)
    }

    @Test
    fun `a user-authored unit is never remote-refreshable, verified or not`() {
        assertFalse(unit(dataSource = ProductDataOrigin.MANUAL, verificationStatus = VerificationStatus.UNVERIFIED).isRemoteRefreshable)
    }

    @Test
    fun `no remote difference when the latest remote amount matches the effective amount`() {
        // 36 and 36.0 are the same weight. The comparison must be numeric, not textual — a trailing
        // zero arriving from the provider is not a reformulation and must not raise a notice.
        val u = unit(amountPerUnit = BigDecimal("36"), latestRemoteAmountPerUnit = BigDecimal("36.0"))
        assertFalse(u.remoteConversionDiffers)
    }

    @Test
    fun `a remote difference is reported when the latest remote amount changed`() {
        val u = unit(amountPerUnit = BigDecimal("36"), latestRemoteAmountPerUnit = BigDecimal("38"))
        assertTrue(u.remoteConversionDiffers)
    }

    @Test
    fun `no remote difference when no latest remote amount has been recorded`() {
        val u = unit(amountPerUnit = BigDecimal("36"), latestRemoteAmountPerUnit = null)
        assertFalse(u.remoteConversionDiffers)
    }

    @Test
    fun `a change of conversion kind is a real difference`() {
        val u = unit().copy(latestRemoteConversion = PortionConversion.DirectCarbs(BigDecimal("14.2")))
        assertTrue("gaining a printed weight, or losing one, is worth surfacing", u.remoteConversionDiffers)
    }
}

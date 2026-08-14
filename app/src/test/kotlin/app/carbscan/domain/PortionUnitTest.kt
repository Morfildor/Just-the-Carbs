package app.carbscan.domain

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
        amountPerUnit = amountPerUnit,
        basis = NutritionBasis.PER_100_G,
        dataSource = dataSource,
        verificationStatus = verificationStatus,
        verifiedAt = null,
        originalRemoteAmountPerUnit = null,
        latestRemoteAmountPerUnit = latestRemoteAmountPerUnit,
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
        val u = unit(amountPerUnit = BigDecimal("36"), latestRemoteAmountPerUnit = BigDecimal("36.0"))
        assertFalse(u.remoteAmountDiffers)
    }

    @Test
    fun `a remote difference is reported when the latest remote amount changed`() {
        val u = unit(amountPerUnit = BigDecimal("36"), latestRemoteAmountPerUnit = BigDecimal("38"))
        assertTrue(u.remoteAmountDiffers)
    }

    @Test
    fun `no remote difference when no latest remote amount has been recorded`() {
        val u = unit(amountPerUnit = BigDecimal("36"), latestRemoteAmountPerUnit = null)
        assertFalse(u.remoteAmountDiffers)
    }
}

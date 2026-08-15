package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

// Suite: portion-unit conversion invariants
// Invariant: the freeze rule (a user-authored or user-verified unit is never silently overwritten by
// a refresh) is identical for both conversion kinds — it keys on provenance and verification, never
// on what kind of number the unit holds.
class PortionConversionTest {

    private fun unit(
        conversion: PortionConversion,
        dataSource: ProductDataOrigin = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
        latestRemoteConversion: PortionConversion? = null,
    ) = PortionUnit(
        productBarcode = "111",
        kind = PortionUnitKind.SLICE,
        customLabel = null,
        conversion = conversion,
        dataSource = dataSource,
        verificationStatus = verificationStatus,
        verifiedAt = null,
        originalRemoteConversion = null,
        latestRemoteConversion = latestRemoteConversion,
        rawRemoteServingText = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private val weight = PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G)
    private val direct = PortionConversion.DirectCarbs(BigDecimal("14.2"))

    @Test
    fun `an unverified remote weight unit is refreshable`() {
        assertTrue(unit(weight).isRemoteRefreshable)
    }

    @Test
    fun `an unverified remote direct-carb unit is refreshable on the same rule`() {
        assertTrue(unit(direct).isRemoteRefreshable)
    }

    @Test
    fun `a verified unit is frozen whichever conversion it holds`() {
        assertFalse(unit(weight, verificationStatus = VerificationStatus.USER_VERIFIED).isRemoteRefreshable)
        assertFalse(unit(direct, verificationStatus = VerificationStatus.USER_VERIFIED).isRemoteRefreshable)
    }

    @Test
    fun `a user-authored unit is frozen whichever conversion it holds`() {
        assertFalse(unit(weight, dataSource = ProductDataOrigin.MANUAL).isRemoteRefreshable)
        assertFalse(unit(direct, dataSource = ProductDataOrigin.MANUAL).isRemoteRefreshable)
    }

    @Test
    fun `a differing latest remote conversion is detected`() {
        val changed = unit(
            weight,
            latestRemoteConversion = PortionConversion.WeightBased(BigDecimal("40"), NutritionBasis.PER_100_G),
        )
        assertTrue(changed.remoteConversionDiffers)
    }

    @Test
    fun `an identical latest remote conversion does not differ`() {
        val same = unit(
            weight,
            latestRemoteConversion = PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G),
        )
        assertFalse(same.remoteConversionDiffers)
    }

    @Test
    fun `a trailing zero is not a difference`() {
        // Compared numerically: "35.0" from the provider is the same weight as a stored "35", and
        // surfacing it as a change would show a notice about nothing.
        val same = unit(
            weight,
            latestRemoteConversion = PortionConversion.WeightBased(BigDecimal("35.0"), NutritionBasis.PER_100_G),
        )
        assertFalse(same.remoteConversionDiffers)
    }

    @Test
    fun `the same number under a different basis is a difference`() {
        val changed = unit(
            weight,
            latestRemoteConversion = PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_ML),
        )
        assertTrue("35 ml is not 35 g", changed.remoteConversionDiffers)
    }

    @Test
    fun `a conversion that changed kind counts as differing`() {
        assertTrue(unit(weight, latestRemoteConversion = direct).remoteConversionDiffers)
    }

    @Test
    fun `no latest remote conversion means nothing differs`() {
        assertFalse(unit(weight).remoteConversionDiffers)
    }

    @Test
    fun `a weight conversion must be positive`() {
        val failed = runCatching {
            PortionConversion.WeightBased(BigDecimal.ZERO, NutritionBasis.PER_100_G)
        }.isFailure
        assertTrue("a zero-gram slice is not a portion", failed)
    }

    @Test
    fun `a zero-carb unit is allowed`() {
        // A sugar-free sachet genuinely contains 0 g of carbohydrate. Rejecting it would be wrong.
        assertEquals(BigDecimal.ZERO, PortionConversion.DirectCarbs(BigDecimal.ZERO).carbsPerUnit)
    }
}

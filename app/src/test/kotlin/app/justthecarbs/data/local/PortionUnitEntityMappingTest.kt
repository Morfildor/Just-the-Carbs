package app.justthecarbs.data.local

import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionConversion
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.PortionUnitKind
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

// Suite: portion-unit persistence mapping
// Invariant: a DIRECT_CARBS unit stores NULL in conversionBasis — a carbs-per-item figure has no
// g/ml basis, and a sentinel there would later be read back as a real measurement.
class PortionUnitEntityMappingTest {

    private fun unit(conversion: PortionConversion) = PortionUnit(
        id = 7,
        productBarcode = "111",
        kind = PortionUnitKind.SLICE,
        customLabel = null,
        conversion = conversion,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
        verifiedAt = null,
        originalRemoteConversion = null,
        latestRemoteConversion = null,
        rawRemoteServingText = "1 slice (35 g)",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `a weight conversion round-trips`() {
        val original = unit(PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G))

        assertEquals(original, original.toEntity().toDomain())
    }

    @Test
    fun `a direct-carb conversion round-trips`() {
        val original = unit(PortionConversion.DirectCarbs(BigDecimal("14.2")))

        assertEquals(original, original.toEntity().toDomain())
    }

    @Test
    fun `a direct-carb unit stores no basis`() {
        val entity = unit(PortionConversion.DirectCarbs(BigDecimal("14.2"))).toEntity()

        assertEquals("DIRECT_CARBS", entity.conversionKind)
        assertEquals("14.2", entity.conversionValue)
        assertNull("carbs per item has no g/ml basis", entity.conversionBasis)
    }

    @Test
    fun `a weight unit stores its basis`() {
        val entity = unit(PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_ML)).toEntity()

        assertEquals("WEIGHT", entity.conversionKind)
        assertEquals("35", entity.conversionValue)
        assertEquals("PER_100_ML", entity.conversionBasis)
    }

    @Test
    fun `remote conversions round-trip independently of the effective one`() {
        val original = unit(PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G)).copy(
            originalRemoteConversion = PortionConversion.WeightBased(BigDecimal("36"), NutritionBasis.PER_100_G),
            latestRemoteConversion = PortionConversion.DirectCarbs(BigDecimal("14.2")),
        )

        assertEquals(original, original.toEntity().toDomain())
    }

    @Test
    fun `an absent remote conversion stays absent`() {
        val entity = unit(PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G)).toEntity()

        assertNull(entity.latestRemoteConversionKind)
        assertNull(entity.latestRemoteConversionValue)
        assertNull(entity.latestRemoteConversionBasis)
    }

    @Test
    fun `a trailing zero is normalized before storage`() {
        // The column is TEXT, so "35.0" and "35" would otherwise be two different stored portions.
        val entity = unit(PortionConversion.WeightBased(BigDecimal("35.0"), NutritionBasis.PER_100_G)).toEntity()

        assertEquals("35", entity.conversionValue)
    }
}

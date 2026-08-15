package app.justthecarbs.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionConversion
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.PortionUnitKind
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.VerificationStatus
import java.math.BigDecimal
import java.time.Instant

/** Discriminant values for the stored [PortionConversion]. Persisted strings — do not rename. */
private const val KIND_WEIGHT = "WEIGHT"
private const val KIND_DIRECT_CARBS = "DIRECT_CARBS"

/**
 * The stored form of a [PortionUnit] (countable-portions brief §2, §6; spec §11).
 *
 * Same conventions as [ProductEntity]: decimal columns are TEXT, never REAL, and timestamps are
 * epoch milliseconds. Rows are deleted along with their product (`onDelete = CASCADE`) — a
 * countable unit has no meaning once the product it describes is gone.
 *
 * [PortionConversion] is a sealed type, which Room cannot store directly, so it is flattened into a
 * discriminant plus a value plus an optional basis. This is the only place that flattening happens.
 * [conversionBasis] is NULL exactly when the conversion is `DIRECT_CARBS` — a carbohydrate-per-item
 * figure has no g/ml basis, and writing one would let a later read mistake it for a measurement.
 */
@Entity(
    tableName = "portion_units",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["barcode"],
            childColumns = ["productBarcode"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["productBarcode"])],
)
data class PortionUnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productBarcode: String,
    /** [PortionUnitKind] name. */
    val kind: String,
    val customLabel: String?,
    /** "WEIGHT" or "DIRECT_CARBS". */
    val conversionKind: String,
    /** amountPerUnit for WEIGHT, carbsPerUnit for DIRECT_CARBS. */
    val conversionValue: String,
    /** [NutritionBasis] name for WEIGHT; NULL for DIRECT_CARBS. */
    val conversionBasis: String?,
    /** [ProductDataOrigin] name. */
    val dataSource: String,
    /** [VerificationStatus] name. */
    val verificationStatus: String,
    val verifiedAt: Long?,
    val originalRemoteConversionKind: String?,
    val originalRemoteConversionValue: String?,
    val originalRemoteConversionBasis: String?,
    val latestRemoteConversionKind: String?,
    val latestRemoteConversionValue: String?,
    val latestRemoteConversionBasis: String?,
    val rawRemoteServingText: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

private fun PortionConversion.kindName(): String = when (this) {
    is PortionConversion.WeightBased -> KIND_WEIGHT
    is PortionConversion.DirectCarbs -> KIND_DIRECT_CARBS
}

/** Normalized before storage: the column is TEXT, so "35" and "35.0" would otherwise differ. */
private fun PortionConversion.valueText(): String = when (this) {
    is PortionConversion.WeightBased -> amountPerUnit.stripTrailingZeros().toPlainString()
    is PortionConversion.DirectCarbs -> carbsPerUnit.stripTrailingZeros().toPlainString()
}

private fun PortionConversion.basisName(): String? = when (this) {
    is PortionConversion.WeightBased -> basis.name
    is PortionConversion.DirectCarbs -> null
}

private fun conversionFrom(kind: String?, value: String?, basis: String?): PortionConversion? {
    if (kind == null || value == null) return null
    return when (kind) {
        KIND_WEIGHT -> PortionConversion.WeightBased(
            amountPerUnit = BigDecimal(value),
            // A WEIGHT row without a basis is a corrupt row, not a defaulted one; failing loudly
            // here beats silently calling millilitres grams.
            basis = NutritionBasis.valueOf(requireNotNull(basis) { "a WEIGHT conversion requires a basis" }),
        )
        KIND_DIRECT_CARBS -> PortionConversion.DirectCarbs(carbsPerUnit = BigDecimal(value))
        else -> error("unknown conversion kind '$kind'")
    }
}

fun PortionUnit.toEntity(): PortionUnitEntity = PortionUnitEntity(
    id = id,
    productBarcode = productBarcode,
    kind = kind.name,
    customLabel = customLabel,
    conversionKind = conversion.kindName(),
    conversionValue = conversion.valueText(),
    conversionBasis = conversion.basisName(),
    dataSource = dataSource.name,
    verificationStatus = verificationStatus.name,
    verifiedAt = verifiedAt?.toEpochMilli(),
    originalRemoteConversionKind = originalRemoteConversion?.kindName(),
    originalRemoteConversionValue = originalRemoteConversion?.valueText(),
    originalRemoteConversionBasis = originalRemoteConversion?.basisName(),
    latestRemoteConversionKind = latestRemoteConversion?.kindName(),
    latestRemoteConversionValue = latestRemoteConversion?.valueText(),
    latestRemoteConversionBasis = latestRemoteConversion?.basisName(),
    rawRemoteServingText = rawRemoteServingText,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

fun PortionUnitEntity.toDomain(): PortionUnit = PortionUnit(
    id = id,
    productBarcode = productBarcode,
    kind = PortionUnitKind.valueOf(kind),
    customLabel = customLabel,
    conversion = requireNotNull(conversionFrom(conversionKind, conversionValue, conversionBasis)) {
        "a stored portion unit must have a conversion"
    },
    dataSource = ProductDataOrigin.valueOf(dataSource),
    verificationStatus = VerificationStatus.valueOf(verificationStatus),
    verifiedAt = verifiedAt?.let(Instant::ofEpochMilli),
    originalRemoteConversion = conversionFrom(
        originalRemoteConversionKind,
        originalRemoteConversionValue,
        originalRemoteConversionBasis,
    ),
    latestRemoteConversion = conversionFrom(
        latestRemoteConversionKind,
        latestRemoteConversionValue,
        latestRemoteConversionBasis,
    ),
    rawRemoteServingText = rawRemoteServingText,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

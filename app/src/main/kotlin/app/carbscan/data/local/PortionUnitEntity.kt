package app.carbscan.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.carbscan.domain.NutritionBasis
import app.carbscan.domain.PortionUnit
import app.carbscan.domain.PortionUnitKind
import app.carbscan.domain.ProductDataOrigin
import app.carbscan.domain.VerificationStatus
import java.math.BigDecimal
import java.time.Instant

/**
 * The stored form of a [PortionUnit] (countable-portions brief §2, §6).
 *
 * Same conventions as [ProductEntity]: decimal columns are TEXT, never REAL, and timestamps are
 * epoch milliseconds. Rows are deleted along with their product (`onDelete = CASCADE`) — a
 * countable unit has no meaning once the product it describes is gone.
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
    val amountPerUnit: String,
    /** [NutritionBasis] name. */
    val basis: String,
    /** [ProductDataOrigin] name. */
    val dataSource: String,
    /** [VerificationStatus] name. */
    val verificationStatus: String,
    val verifiedAt: Long?,
    val originalRemoteAmountPerUnit: String?,
    val latestRemoteAmountPerUnit: String?,
    val rawRemoteServingText: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

fun PortionUnit.toEntity(): PortionUnitEntity = PortionUnitEntity(
    id = id,
    productBarcode = productBarcode,
    kind = kind.name,
    customLabel = customLabel,
    amountPerUnit = amountPerUnit.toPlainString(),
    basis = basis.name,
    dataSource = dataSource.name,
    verificationStatus = verificationStatus.name,
    verifiedAt = verifiedAt?.toEpochMilli(),
    originalRemoteAmountPerUnit = originalRemoteAmountPerUnit?.toPlainString(),
    latestRemoteAmountPerUnit = latestRemoteAmountPerUnit?.toPlainString(),
    rawRemoteServingText = rawRemoteServingText,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

fun PortionUnitEntity.toDomain(): PortionUnit = PortionUnit(
    id = id,
    productBarcode = productBarcode,
    kind = PortionUnitKind.valueOf(kind),
    customLabel = customLabel,
    amountPerUnit = BigDecimal(amountPerUnit),
    basis = NutritionBasis.valueOf(basis),
    dataSource = ProductDataOrigin.valueOf(dataSource),
    verificationStatus = VerificationStatus.valueOf(verificationStatus),
    verifiedAt = verifiedAt?.let(Instant::ofEpochMilli),
    originalRemoteAmountPerUnit = originalRemoteAmountPerUnit?.let(::BigDecimal),
    latestRemoteAmountPerUnit = latestRemoteAmountPerUnit?.let(::BigDecimal),
    rawRemoteServingText = rawRemoteServingText,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

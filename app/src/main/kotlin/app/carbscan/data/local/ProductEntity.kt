package app.carbscan.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.carbscan.domain.NutritionBasis
import app.carbscan.domain.Product
import app.carbscan.domain.ProductDataOrigin
import app.carbscan.domain.VerificationStatus
import java.math.BigDecimal
import java.time.Instant

/**
 * The stored form of a [Product] (brief §33).
 *
 * Every carbohydrate figure is a **TEXT** column holding the plain decimal string, not a REAL.
 * SQLite's REAL is a binary double: round-tripping 48.2 through it can hand back
 * 48.200000000000003, and this app's whole reason to exist is showing a number the user then types
 * into a bolus calculator. Text costs a parse and keeps the decimal exact.
 *
 * Timestamps are epoch milliseconds, the only representation SQLite sorts correctly for free.
 */
@Entity(
    tableName = "products",
    indices = [Index(value = ["favorite", "lastUsedAt"])],
)
data class ProductEntity(
    @PrimaryKey val barcode: String,
    val name: String,
    val carbsPer100: String,
    val basis: String,
    /** [ProductDataOrigin] name. Provenance — permanent for the life of the row. */
    val dataSource: String,
    /** [VerificationStatus] name. Changes independently of [dataSource]. */
    val verificationStatus: String,
    val brand: String?,
    val packageAmount: String?,
    val servingAmount: String?,
    val imageUrl: String?,
    val originalRemoteCarbs: String?,
    val verifiedAt: Long?,
    val remoteUpdatedAt: Long?,
    val lastUsedAt: Long?,
    val lastPortion: String?,
    val favorite: Boolean,
)

fun Product.toEntity(): ProductEntity = ProductEntity(
    barcode = barcode,
    name = name,
    carbsPer100 = carbsPer100.toPlainString(),
    basis = basis.name,
    dataSource = dataSource.name,
    verificationStatus = verificationStatus.name,
    brand = brand,
    packageAmount = packageAmount?.toPlainString(),
    servingAmount = servingAmount?.toPlainString(),
    imageUrl = imageUrl,
    originalRemoteCarbs = originalRemoteCarbs?.toPlainString(),
    verifiedAt = verifiedAt?.toEpochMilli(),
    remoteUpdatedAt = remoteUpdatedAt?.toEpochMilli(),
    lastUsedAt = lastUsedAt?.toEpochMilli(),
    lastPortion = lastPortion?.toPlainString(),
    favorite = favorite,
)

fun ProductEntity.toDomain(): Product = Product(
    barcode = barcode,
    name = name,
    carbsPer100 = BigDecimal(carbsPer100),
    basis = NutritionBasis.valueOf(basis),
    dataSource = ProductDataOrigin.valueOf(dataSource),
    verificationStatus = VerificationStatus.valueOf(verificationStatus),
    brand = brand,
    packageAmount = packageAmount?.let(::BigDecimal),
    servingAmount = servingAmount?.let(::BigDecimal),
    imageUrl = imageUrl,
    originalRemoteCarbs = originalRemoteCarbs?.let(::BigDecimal),
    verifiedAt = verifiedAt?.let(Instant::ofEpochMilli),
    remoteUpdatedAt = remoteUpdatedAt?.let(Instant::ofEpochMilli),
    lastUsedAt = lastUsedAt?.let(Instant::ofEpochMilli),
    lastPortion = lastPortion?.let(::BigDecimal),
    favorite = favorite,
)

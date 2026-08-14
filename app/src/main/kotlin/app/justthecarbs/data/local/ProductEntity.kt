package app.justthecarbs.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.VerificationStatus
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
    /** 400 px front image for the calculator hero (§5). Null on rows cached before v4. */
    val largeImageUrl: String?,
    val originalRemoteCarbs: String?,
    val latestRemoteCarbs: String?,
    val verifiedAt: Long?,
    val remoteUpdatedAt: Long?,
    val lastUsedAt: Long?,
    val lastPortion: String?,
    val favorite: Boolean,
    /** [app.justthecarbs.domain.InputMode] name, or null if never used with a countable portion. */
    val lastInputMode: String?,
    val lastSelectedPortionUnitId: Long?,
    val lastCount: String?,
    /** Compact JSON for validated gallery metadata. Null on rows cached before v5. */
    val galleryImagesJson: String? = null,
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
    largeImageUrl = largeImageUrl,
    originalRemoteCarbs = originalRemoteCarbs?.toPlainString(),
    latestRemoteCarbs = latestRemoteCarbs?.toPlainString(),
    verifiedAt = verifiedAt?.toEpochMilli(),
    remoteUpdatedAt = remoteUpdatedAt?.toEpochMilli(),
    lastUsedAt = lastUsedAt?.toEpochMilli(),
    lastPortion = lastPortion?.toPlainString(),
    favorite = favorite,
    lastInputMode = lastInputMode?.name,
    lastSelectedPortionUnitId = lastSelectedPortionUnitId,
    lastCount = lastCount?.toPlainString(),
    galleryImagesJson = ProductImageCacheCodec.encode(images),
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
    largeImageUrl = largeImageUrl,
    originalRemoteCarbs = originalRemoteCarbs?.let(::BigDecimal),
    latestRemoteCarbs = latestRemoteCarbs?.let(::BigDecimal),
    verifiedAt = verifiedAt?.let(Instant::ofEpochMilli),
    remoteUpdatedAt = remoteUpdatedAt?.let(Instant::ofEpochMilli),
    lastUsedAt = lastUsedAt?.let(Instant::ofEpochMilli),
    lastPortion = lastPortion?.let(::BigDecimal),
    favorite = favorite,
    lastInputMode = lastInputMode?.let(InputMode::valueOf),
    lastSelectedPortionUnitId = lastSelectedPortionUnitId,
    lastCount = lastCount?.let(::BigDecimal),
    images = ProductImageCacheCodec.decode(galleryImagesJson),
)

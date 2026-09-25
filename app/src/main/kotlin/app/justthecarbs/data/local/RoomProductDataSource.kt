package app.justthecarbs.data.local

import app.justthecarbs.domain.LocalProductDataSource
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductFetchResult
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.domain.RecentUseSnapshot
import app.justthecarbs.domain.toInputModeOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant

/**
 * Room-backed [LocalProductDataSource] — priority 1 and 2 of the §10 lookup order.
 *
 * The mapping to domain types happens here so that everything above this class works in plain
 * Kotlin: `ProductRepository` and the whole calculation layer stay unit-testable on the JVM.
 */
class RoomProductDataSource(private val dao: ProductDao) : LocalProductDataSource {

    override suspend fun fetch(barcode: String): ProductFetchResult =
        dao.findByBarcode(barcode)
            ?.let { ProductFetchResult.Found(it.toDomain()) }
            ?: ProductFetchResult.NotFound

    override suspend fun save(product: Product) = dao.upsert(product.toEntity())

    override suspend fun saveIfAbsent(product: Product): Boolean = dao.insertIfAbsent(product.toEntity()) != -1L

    override fun observeRecents(limit: Int): Flow<List<Product>> =
        dao.observeRecents(limit).map { entities -> entities.map { it.toDomain() } }

    override suspend fun forgetRecentUse(barcode: String): RecentUseSnapshot? =
        dao.forgetRecentUse(barcode)?.toDomain()

    override suspend fun restoreRecentUse(snapshot: RecentUseSnapshot) =
        dao.restoreRecentUse(snapshot.toRow())

    /**
     * Every stored product as a search candidate, for matching typed names on the device.
     *
     * Room-only rather than on [LocalProductDataSource]: it serves the search screens, not the §10
     * lookup, and a tap on one of these still runs the ordinary lookup. A row whose figure or basis
     * cannot be read shows no figure rather than a guessed one, the same rule remote hits follow.
     */
    fun observeSearchable(): Flow<List<ProductSearchHit>> =
        dao.observeSearchable().map { rows -> rows.map { it.toSearchHit() } }

    suspend fun clearRecentHistory() = dao.clearRecentHistory()

    suspend fun deleteAllProducts() = dao.deleteAllProducts()
}

private fun SearchableProductRow.toSearchHit(): ProductSearchHit {
    val basis = NutritionBasis.entries.firstOrNull { it.name == basis }
    val carbs = carbsPer100.toBigDecimalOrNull()?.takeIf { basis != null }
    return ProductSearchHit(
        barcode = barcode,
        name = name,
        brand = brand,
        packageQuantity = packageAmount?.toBigDecimalOrNull()?.takeIf { basis != null }
            ?.let { "${it.stripTrailingZeros().toPlainString()} ${basis?.unitLabel}" },
        carbsPer100 = carbs,
        basis = basis.takeIf { carbs != null },
        imageUrl = imageUrl,
    )
}

private fun RecentUseSnapshotRow.toDomain(): RecentUseSnapshot = RecentUseSnapshot(
    barcode = barcode,
    lastUsedAt = lastUsedAt?.let(Instant::ofEpochMilli),
    lastPortion = lastPortion?.let(::BigDecimal),
    lastInputMode = lastInputMode.toInputModeOrNull(),
    lastSelectedPortionUnitId = lastSelectedPortionUnitId,
    lastCount = lastCount?.let(::BigDecimal),
    portionUsage = portionUsage.map { it.toDomain() },
)

private fun RecentUseSnapshot.toRow(): RecentUseSnapshotRow = RecentUseSnapshotRow(
    barcode = barcode,
    lastUsedAt = lastUsedAt?.toEpochMilli(),
    // `toPlainString()` on a `BigDecimal` parsed from the stored TEXT returns that same text: scale
    // is carried, so a stored "65.0" restores as "65.0" and not as "65". The columns are TEXT for
    // exactly this reason, and an Undo that silently renormalised a decimal would be a quiet edit.
    lastPortion = lastPortion?.toPlainString(),
    lastInputMode = lastInputMode?.name,
    lastSelectedPortionUnitId = lastSelectedPortionUnitId,
    lastCount = lastCount?.toPlainString(),
    portionUsage = portionUsage.map { it.toEntity() },
)

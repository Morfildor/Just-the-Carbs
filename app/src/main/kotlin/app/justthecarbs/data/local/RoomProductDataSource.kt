package app.justthecarbs.data.local

import app.justthecarbs.domain.LocalProductDataSource
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductFetchResult
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

    override fun observeRecents(limit: Int): Flow<List<Product>> =
        dao.observeRecents(limit).map { entities -> entities.map { it.toDomain() } }

    override suspend fun setLocalAlias(barcode: String, alias: String?) =
        dao.setLocalAlias(barcode, alias)

    override suspend fun forgetRecentUse(barcode: String): RecentUseSnapshot? =
        dao.forgetRecentUse(barcode)?.toDomain()

    override suspend fun restoreRecentUse(snapshot: RecentUseSnapshot) =
        dao.restoreRecentUse(snapshot.toRow())

    suspend fun clearRecentHistory() = dao.clearRecentHistory()

    suspend fun deleteAllProducts() = dao.deleteAllProducts()
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

package app.justthecarbs.data.local

import app.justthecarbs.domain.LocalProductDataSource
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductFetchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    suspend fun clearRecentHistory() = dao.clearRecentHistory()

    suspend fun deleteAllProducts() = dao.deleteAllProducts()
}

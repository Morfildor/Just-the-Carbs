package app.justthecarbs.domain

import kotlinx.coroutines.flow.Flow

/**
 * Where [PortionUnit]s are kept (countable-portions brief §2). Local-only: unlike [Product], there
 * is no remote "portion unit source" abstraction — a remote suggestion arrives embedded in a
 * [ProductFetchResult.Found] as a [PortionUnitCandidate], and [app.justthecarbs.data.ProductRepository]
 * decides whether it becomes a stored unit.
 */
interface PortionUnitStore {
    suspend fun findByBarcode(barcode: String): List<PortionUnit>
    fun observeByBarcode(barcode: String): Flow<List<PortionUnit>>
    suspend fun findById(id: Long): PortionUnit?

    /** Insert or update. Returns the saved unit, with [PortionUnit.id] populated on insert. */
    suspend fun save(unit: PortionUnit): PortionUnit

    suspend fun delete(unit: PortionUnit)
}

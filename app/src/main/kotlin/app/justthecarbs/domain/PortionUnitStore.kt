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

    /**
     * Batch lookup for Home's recents row (P1 §13): one call for the whole visible list instead of
     * one per product. The default — [findById] once per id — is what every in-memory test fake gets
     * for free and is correct there, since a fake has no per-call cost to amortise.
     * [RoomPortionUnitDataSource] overrides this with a single `WHERE id IN (...)` query, which is
     * the actual fix: without it, Home issued one Room query per recent product on every emission.
     */
    suspend fun findByIds(ids: List<Long>): List<PortionUnit> =
        ids.distinct().mapNotNull { findById(it) }

    /** Insert or update. Returns the saved unit, with [PortionUnit.id] populated on insert. */
    suspend fun save(unit: PortionUnit): PortionUnit

    suspend fun delete(unit: PortionUnit)
}

package app.justthecarbs.data.local

import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.PortionUnitStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPortionUnitDataSource(private val dao: PortionUnitDao) : PortionUnitStore {

    override suspend fun findByBarcode(barcode: String): List<PortionUnit> =
        dao.findByBarcode(barcode).map { it.toDomain() }

    override fun observeByBarcode(barcode: String): Flow<List<PortionUnit>> =
        dao.observeByBarcode(barcode).map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: Long): PortionUnit? = dao.findById(id)?.toDomain()

    override suspend fun save(unit: PortionUnit): PortionUnit {
        val entity = unit.toEntity()
        val id = dao.upsert(entity)
        // Room's @Upsert returns the new rowId on insert, -1 on update (the row already had one).
        return if (id > 0) unit.copy(id = id) else unit
    }

    override suspend fun delete(unit: PortionUnit) {
        dao.delete(unit.toEntity())
    }
}

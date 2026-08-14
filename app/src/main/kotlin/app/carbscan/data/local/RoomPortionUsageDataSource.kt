package app.carbscan.data.local

import app.carbscan.domain.InputMode
import app.carbscan.domain.PortionUsage
import app.carbscan.domain.PortionUsageStore
import java.math.BigDecimal

class RoomPortionUsageDataSource(private val dao: PortionUsageDao) : PortionUsageStore {

    override suspend fun findByBarcode(barcode: String): List<PortionUsage> =
        dao.findByBarcode(barcode).map { it.toDomain() }

    override suspend fun findVariant(
        barcode: String,
        inputMode: InputMode,
        portionUnitId: Long?,
        amount: BigDecimal,
    ): PortionUsage? = dao.findVariant(
        barcode = barcode,
        inputMode = inputMode.name,
        unitId = portionUnitId,
        // toPlainString() on both sides of the comparison: the column is TEXT, so "2" and "2.0" are
        // different rows. The repository normalises before it gets here.
        amount = amount.toPlainString(),
    )?.toDomain()

    override suspend fun save(usage: PortionUsage): PortionUsage {
        if (usage.id != 0L) {
            dao.update(usage.toEntity())
            return usage
        }
        val id = dao.insert(usage.toEntity())
        return usage.copy(id = id)
    }

    override suspend fun delete(usage: PortionUsage) {
        dao.delete(usage.toEntity())
    }
}

package app.justthecarbs.data.local

import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.PortionUsage
import app.justthecarbs.domain.PortionUsageStore
import java.math.BigDecimal
import java.time.Instant

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
        unitId = portionUnitId ?: PortionUsageEntity.NO_UNIT_SENTINEL,
        // toPlainString() on both sides of the comparison: the column is TEXT, so "2" and "2.0" are
        // different rows. The repository normalises before it gets here.
        amount = amount.toPlainString(),
    )?.toDomain()

    /**
     * A plain upsert-by-identity, satisfying the [PortionUsageStore] contract for a caller that
     * already has the exact row it wants written. No production call site uses this any more —
     * [ProductRepository.recordPortionUsage] (the sole caller that used to read-decide-then-`save`)
     * now calls [recordUse] below directly, which is where the P0 §5 atomicity fix actually lives.
     */
    override suspend fun save(usage: PortionUsage): PortionUsage {
        val id = dao.upsert(usage.toEntity())
        return if (usage.id != 0L) usage else usage.copy(id = id)
    }

    /** Atomically records usage. Room implements this with an API-26-compatible transaction;
     * in-memory stores use the default implementation. */
    override suspend fun recordUse(
        barcode: String,
        inputMode: InputMode,
        portionUnitId: Long?,
        amount: BigDecimal,
        now: Instant,
    ): PortionUsage {
        val unitId = portionUnitId ?: PortionUsageEntity.NO_UNIT_SENTINEL
        val normalisedAmount = amount.toPlainString()
        dao.recordUse(
            barcode = barcode,
            inputMode = inputMode.name,
            unitId = unitId,
            amount = normalisedAmount,
            now = now.toEpochMilli(),
        )
        return checkNotNull(dao.findVariant(barcode, inputMode.name, unitId, normalisedAmount)?.toDomain()) {
            "recordUse just wrote this row; it must be findable immediately afterward"
        }
    }

    override suspend fun delete(usage: PortionUsage) {
        dao.delete(usage.toEntity())
    }
}

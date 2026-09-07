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

    /**
     * The real fix for P0 §5's read-decide-write race: one atomic `INSERT ... ON CONFLICT ... DO
     * UPDATE` statement, so two concurrent calls for the same variant cannot interleave a read from
     * one with a write from the other. See [PortionUsageDao.recordUse]'s KDoc for the failure this
     * closes. The returned [PortionUsage] is re-read rather than reconstructed from the arguments,
     * because the statement does not report whether it inserted or incremented, or what the
     * resulting `id`/`usageCount` are — SQLite's `INSERT ... ON CONFLICT` has no `RETURNING`-based
     * Room binding this codebase's SQLite version supports here, so a follow-up `findVariant` is the
     * simplest correct way to hand the caller the row as it now stands.
     */
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

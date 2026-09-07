package app.justthecarbs.domain

import java.math.BigDecimal

/**
 * Storage for per-product portion-usage aggregates (brief §13, §22).
 *
 * Every read is scoped to one barcode. There is deliberately no "all usage" accessor: without one,
 * the app is structurally incapable of listing everything the user has eaten, which is the privacy
 * boundary the brief draws around this feature rather than a limitation of it.
 */
interface PortionUsageStore {

    suspend fun findByBarcode(barcode: String): List<PortionUsage>

    /** The row for one exact variant (mode + unit + amount), or null. */
    suspend fun findVariant(
        barcode: String,
        inputMode: InputMode,
        portionUnitId: Long?,
        amount: BigDecimal,
    ): PortionUsage?

    suspend fun save(usage: PortionUsage): PortionUsage

    suspend fun delete(usage: PortionUsage)

    /**
     * Atomically record one use of a portion variant: insert it at count 1 if it has never been
     * seen, or increment an existing row's count (P0 §5).
     *
     * The default implementation — [findVariant] to decide, then [save] — is what every in-memory
     * test fake gets for free, and it is correct there: a fake backed by a plain
     * `MutableMap`/`MutableList` under a single-threaded test dispatcher has no window for two
     * calls to interleave, so there is nothing for atomicity to protect against. [RoomPortionUsageDataSource]
     * overrides this with a single `INSERT ... ON CONFLICT ... DO UPDATE` statement, because SQLite
     * genuinely does have concurrent writers and a read-decide-write pattern there is a real race —
     * see its KDoc for the failure this fixes.
     */
    suspend fun recordUse(
        barcode: String,
        inputMode: InputMode,
        portionUnitId: Long?,
        amount: BigDecimal,
        now: java.time.Instant,
    ): PortionUsage {
        val existing = findVariant(barcode, inputMode, portionUnitId, amount)
        return if (existing == null) {
            save(
                PortionUsage(
                    productBarcode = barcode,
                    inputMode = inputMode,
                    portionUnitId = portionUnitId,
                    amount = amount,
                    usageCount = 1,
                    lastUsedAt = now,
                ),
            )
        } else {
            save(existing.copy(usageCount = existing.usageCount + 1, lastUsedAt = now))
        }
    }
}

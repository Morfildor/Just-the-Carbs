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

    /** Atomically records usage. Room implements this with an API-26-compatible transaction;
     * in-memory stores use the default implementation. */
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

package app.carbscan.domain

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
}

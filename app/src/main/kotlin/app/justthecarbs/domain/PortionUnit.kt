package app.justthecarbs.domain

import java.math.BigDecimal
import java.time.Instant

/**
 * A countable portion for one product, e.g. "1 slice = 36 g" (countable-portions brief §2).
 *
 * Mirrors [Product]'s provenance/verification split exactly, and for the same reason: a unit can
 * arrive from Open Food Facts and later be verified by the user without collapsing those two facts
 * into one (owner's standing correction — see [ProductDataOrigin]/[VerificationStatus]).
 */
data class PortionUnit(
    val id: Long = 0,
    val productBarcode: String,
    val kind: PortionUnitKind,
    /** Required iff [kind] is [PortionUnitKind.CUSTOM]; user text, never translated. */
    val customLabel: String?,
    /** The effective/verified amount used in calculations. Never changed by a remote refresh alone. */
    val amountPerUnit: BigDecimal,
    val basis: NutritionBasis,
    val dataSource: ProductDataOrigin,
    val verificationStatus: VerificationStatus,
    val verifiedAt: Instant?,
    /** The first remote value ever seen for this unit. Immutable once set. */
    val originalRemoteAmountPerUnit: BigDecimal?,
    /** The most recent remote value seen, recorded even when NOT applied (brief §7, §9). */
    val latestRemoteAmountPerUnit: BigDecimal?,
    /** The raw OFF serving_size text this unit was parsed from, kept for diagnostics/re-parsing. */
    val rawRemoteServingText: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** Same rule as [Product.isRemoteRefreshable]: user-authored or user-verified units are frozen. */
    val isRemoteRefreshable: Boolean
        get() = !dataSource.isUserAuthored && verificationStatus == VerificationStatus.UNVERIFIED

    /** Same rule as [Product.remoteValueDiffers], extended to the per-unit amount. */
    val remoteAmountDiffers: Boolean
        get() = latestRemoteAmountPerUnit?.let { it.compareTo(amountPerUnit) != 0 } == true
}

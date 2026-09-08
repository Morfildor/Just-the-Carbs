package app.justthecarbs.domain

import java.time.Instant

/**
 * A countable portion for one product — "1 slice = 36 g", or "1 slice = 14.2 g carbs"
 * (countable-portions brief §2, spec §8).
 *
 * Mirrors [Product]'s provenance/verification split exactly, and for the same reason: a unit can
 * arrive from Open Food Facts and later be verified by the user without collapsing those two facts
 * into one (owner's standing correction — see [ProductDataOrigin]/[VerificationStatus]).
 *
 * What the unit knows is held in [conversion], so a unit that only knows carbohydrate-per-item is a
 * first-class shape rather than a weight-based unit with a fabricated weight.
 */
data class PortionUnit(
    val id: Long = 0,
    val productBarcode: String,
    val kind: PortionUnitKind,
    /** Required iff [kind] is [PortionUnitKind.CUSTOM]; user text, never translated. */
    val customLabel: String?,
    /** The effective/verified conversion used in calculations. Never changed by a refresh alone. */
    val conversion: PortionConversion,
    val dataSource: ProductDataOrigin,
    val verificationStatus: VerificationStatus,
    val verifiedAt: Instant?,
    /** The first remote conversion ever seen for this unit. Immutable once set. */
    val originalRemoteConversion: PortionConversion?,
    /** The most recent remote conversion seen, recorded even when NOT applied (brief §7, §9). */
    val latestRemoteConversion: PortionConversion?,
    /** The raw OFF serving_size text this unit was parsed from, kept for diagnostics/re-parsing. */
    val rawRemoteServingText: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /**
     * Same rule as [Product.isRemoteRefreshable]: user-authored or user-verified units are frozen.
     *
     * Deliberately independent of which [PortionConversion] the unit holds — the freeze protects the
     * user's judgement, and that is the same fact whether they confirmed a weight or a carb figure.
     */
    val isRemoteRefreshable: Boolean
        get() = !dataSource.isUserAuthored && verificationStatus == VerificationStatus.UNVERIFIED

    /**
     * Whether the provider now reports something different. A change of conversion *kind* counts:
     * moving from a carbs-per-item figure to a printed weight is a real change worth surfacing.
     *
     * Values are compared **numerically**, not structurally. `BigDecimal`'s own `equals` treats `36`
     * and `36.0` as different because it compares scale as well as value, and a trailing zero
     * arriving from the provider is a formatting artefact, not a reformulation — reporting it would
     * put a "the online portion changed" notice in front of the user for no change at all.
     */
    val remoteConversionDiffers: Boolean
        get() = latestRemoteConversion?.let { !it.sameAs(conversion) } == true

    private fun PortionConversion.sameAs(other: PortionConversion): Boolean = when {
        this is PortionConversion.WeightBased && other is PortionConversion.WeightBased ->
            basis == other.basis && amountPerUnit.compareTo(other.amountPerUnit) == 0
        this is PortionConversion.DirectCarbs && other is PortionConversion.DirectCarbs ->
            carbsPerUnit.compareTo(other.carbsPerUnit) == 0
        // Different kinds: a unit that gained or lost a printed weight genuinely changed.
        else -> false
    }
}

/** Weight and volume cannot be exchanged without a density; direct carbs have no such unit. */
fun PortionUnit.isCompatibleWith(basis: NutritionBasis): Boolean = when (val value = conversion) {
    is PortionConversion.WeightBased -> value.basis == basis
    is PortionConversion.DirectCarbs -> true
}

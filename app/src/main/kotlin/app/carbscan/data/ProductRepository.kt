package app.carbscan.data

import app.carbscan.domain.InputMode
import app.carbscan.domain.LocalProductDataSource
import app.carbscan.domain.NutritionBasis
import app.carbscan.domain.PortionUnit
import app.carbscan.domain.PortionUnitCandidate
import app.carbscan.domain.PortionUnitKind
import app.carbscan.domain.PortionUnitStore
import app.carbscan.domain.Product
import app.carbscan.domain.ProductDataOrigin
import app.carbscan.domain.ProductDataSource
import app.carbscan.domain.ProductFetchResult
import app.carbscan.domain.VerificationStatus
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant

/**
 * What a background refresh found (corrections #5, #10).
 *
 * Deliberately does NOT carry a product for the caller to install. An open calculator session uses
 * an immutable snapshot; a refresh can inform the user that the online figure moved, but only the
 * user decides whether the number they are working with changes.
 */
sealed interface RefreshOutcome {
    data object Unchanged : RefreshOutcome

    /** The provider now reports a different figure. Recorded locally; not applied. */
    data class RemoteDiffers(val latestRemoteCarbs: java.math.BigDecimal) : RefreshOutcome
}

/**
 * Owns the lookup priority of brief §10 — the one place that decides which carbohydrate number the
 * user is shown.
 *
 * Two rules drive every method here:
 *
 * - **Local first.** Anything already on the device is shown without a network call. Open Food
 *   Facts permits 15 reads/min/IP, so cache-first is correctness, not performance (§7, §32).
 * - **User data is never overwritten.** Remote data can only ever replace a [ProductSource.REMOTE]
 *   record. Verified and manual products are untouchable by sync (§23).
 */
class ProductRepository(
    private val local: LocalProductDataSource,
    private val remote: ProductDataSource,
    private val portionUnits: PortionUnitStore,
    private val clock: Clock = Clock.systemUTC(),
) {

    /**
     * Resolve a barcode for the calculator.
     *
     * Anything cached wins outright — including a stale remote copy, which is what keeps the app
     * working offline. Refreshing is a separate, optional call so it can never delay a calculation.
     */
    suspend fun lookup(barcode: String): ProductFetchResult {
        val cached = local.fetch(barcode)
        if (cached is ProductFetchResult.Found) return cached

        return when (val fetched = remote.fetch(barcode)) {
            is ProductFetchResult.Found -> {
                local.save(fetched.product)
                // First sighting of this product: a suggested countable unit becomes a stored one
                // outright, since there is nothing local yet for it to conflict with (§7, §9).
                fetched.portionUnitCandidate?.let { candidate ->
                    portionUnits.save(newPortionUnitFromCandidate(barcode, candidate))
                }
                fetched
            }
            // NotFound, Unusable and Failed all leave the cache untouched: the app does not record
            // an absence, and it never caches a value it refused to trust (§13).
            else -> fetched
        }
    }

    /**
     * Optional background refresh of a cached product (§10.2, corrections #5 and #10).
     *
     * Never returns a product for the caller to swap in mid-session. The calculator holds an
     * immutable snapshot, so what a refresh produces is *information about* a change, which the
     * user then chooses to act on or ignore.
     */
    suspend fun refreshFromRemote(barcode: String): RefreshOutcome {
        val existing = (local.fetch(barcode) as? ProductFetchResult.Found)?.product
            ?: return RefreshOutcome.Unchanged

        val fetchedResult = remote.fetch(barcode) as? ProductFetchResult.Found
            ?: return RefreshOutcome.Unchanged
        val fetched = fetchedResult.product

        refreshPortionUnitFromCandidate(barcode, fetchedResult.portionUnitCandidate)

        val remoteCarbs = fetched.carbsPer100
        val differs = remoteCarbs.compareTo(existing.carbsPer100) != 0

        // §23: a verified or user-authored product belongs to the user. The newer remote figure is
        // *recorded* so the app can mention that the product may have been reformulated (§24), but
        // the value in use is never replaced.
        if (!existing.isRemoteRefreshable) {
            local.save(existing.copy(latestRemoteCarbs = remoteCarbs, remoteUpdatedAt = clock.instant()))
            return if (differs) RefreshOutcome.RemoteDiffers(remoteCarbs) else RefreshOutcome.Unchanged
        }

        // Plain cached remote data: the cache may be brought up to date, because it is the
        // provider's value either way and the user has not expressed an opinion about it.
        // Remote owns the product facts; the device owns how the user has been using it, so
        // merging rather than replacing stops a refresh from clearing a favourite.
        local.save(
            fetched.copy(
                favorite = existing.favorite,
                lastPortion = existing.lastPortion,
                lastUsedAt = existing.lastUsedAt,
                latestRemoteCarbs = remoteCarbs,
                remoteUpdatedAt = clock.instant(),
            ),
        )
        return if (differs) RefreshOutcome.RemoteDiffers(remoteCarbs) else RefreshOutcome.Unchanged
    }

    /**
     * Record that the user confirmed this product against the physical package (§23).
     *
     * [Product.dataSource] is deliberately left alone. A product that came from Open Food Facts and
     * was then verified is *both* things, and flattening that into one field would throw away the
     * provenance — the app could no longer tell a user-typed product from a downloaded one the user
     * happened to check.
     *
     * The figure that was on screen beforehand is preserved as [Product.originalRemoteCarbs] so
     * *Reset to online value* remains available and the override stays auditable.
     */
    suspend fun saveVerification(
        barcode: String,
        verifiedCarbsPer100: BigDecimal,
        basis: NutritionBasis,
        name: String? = null,
        packageAmount: BigDecimal? = null,
    ) {
        val existing = requireExisting(barcode)
        // Only downloaded data has an "online value" to fall back to. Keep the *first* one, so
        // repeated verifications do not overwrite the original with a previous correction of it.
        val onlineOriginal = when {
            existing.dataSource.isUserAuthored -> null
            else -> existing.originalRemoteCarbs ?: existing.carbsPer100
        }
        local.save(
            existing.copy(
                name = name ?: existing.name,
                carbsPer100 = verifiedCarbsPer100,
                basis = basis,
                packageAmount = packageAmount ?: existing.packageAmount,
                verificationStatus = VerificationStatus.USER_VERIFIED,
                originalRemoteCarbs = onlineOriginal,
                verifiedAt = clock.instant(),
            ),
        )
    }

    /**
     * Apply a newer online value that the user has explicitly chosen to accept (corrections #5, #10).
     *
     * Only ever reached from a deliberate tap — never from a background refresh. The figure being
     * replaced is preserved as the original online value if none is recorded yet, so the change
     * stays auditable and reversible, and the record drops back to unverified: the user has not
     * checked *this* number against a package.
     */
    suspend fun applyLatestRemoteValue(barcode: String) {
        val existing = requireExisting(barcode)
        val latest = existing.latestRemoteCarbs ?: return
        local.save(
            existing.copy(
                carbsPer100 = latest,
                originalRemoteCarbs = existing.originalRemoteCarbs ?: existing.carbsPer100,
                verificationStatus = VerificationStatus.UNVERIFIED,
                verifiedAt = null,
            ),
        )
    }

    /**
     * Undo a verification, returning to the online figure (§23, behind the overflow menu).
     * The provenance is untouched — it was always Open Food Facts data and still is.
     */
    suspend fun resetToOnlineValue(barcode: String) {
        val existing = requireExisting(barcode)
        val online = existing.originalRemoteCarbs ?: return
        local.save(
            existing.copy(
                carbsPer100 = online,
                verificationStatus = VerificationStatus.UNVERIFIED,
                originalRemoteCarbs = null,
                verifiedAt = null,
            ),
        )
    }

    /**
     * Store a product the user authored: typed in by hand (§27) or built from an OCR read they
     * confirmed (§29).
     *
     * Both count as verified, because in each case the user was reading the physical package when
     * they entered the number — that is precisely what verification means here.
     */
    suspend fun saveUserAuthoredProduct(
        product: Product,
        origin: ProductDataOrigin = ProductDataOrigin.MANUAL,
    ) {
        require(origin.isUserAuthored) { "$origin is not a user-authored origin" }
        val timestamp = clock.instant()
        local.save(
            product.copy(
                dataSource = origin,
                verificationStatus = VerificationStatus.USER_VERIFIED,
                verifiedAt = timestamp,
                // Authoring a product counts as using it. Recents are keyed on lastUsedAt, and
                // without this the product is saved but absent from the only screen that lists
                // products — created, then unreachable.
                lastUsedAt = timestamp,
            ),
        )
    }

    /**
     * Remember the portion so the next visit pre-fills it, and float the product up Recents (§20, §21).
     *
     * [mode]/[portionUnitId]/[count] are optional so grams-only usage (no countable units for this
     * product) does not need to pass anything new (§11, §12). Passing [InputMode.GRAMS] explicitly
     * clears any previously remembered countable selection, so switching back to grams and using it
     * is itself what "remembers grams" next time.
     */
    suspend fun recordUse(
        barcode: String,
        portion: BigDecimal,
        mode: InputMode? = null,
        portionUnitId: Long? = null,
        count: BigDecimal? = null,
    ) {
        val existing = requireExisting(barcode)
        local.save(
            existing.copy(
                lastPortion = portion,
                lastUsedAt = clock.instant(),
                lastInputMode = mode ?: existing.lastInputMode,
                lastSelectedPortionUnitId = if (mode == InputMode.GRAMS) null else portionUnitId ?: existing.lastSelectedPortionUnitId,
                lastCount = if (mode == InputMode.GRAMS) null else count ?: existing.lastCount,
            ),
        )
    }

    suspend fun setFavorite(barcode: String, favorite: Boolean) {
        val existing = requireExisting(barcode)
        local.save(existing.copy(favorite = favorite))
    }

    fun observeRecents(limit: Int): Flow<List<Product>> = local.observeRecents(limit)

    // ---- countable portions (brief §2, §6-§9) --------------------------------------------------

    fun observePortionUnits(barcode: String): Flow<List<PortionUnit>> = portionUnits.observeByBarcode(barcode)

    suspend fun findPortionUnits(barcode: String): List<PortionUnit> = portionUnits.findByBarcode(barcode)

    suspend fun findPortionUnit(id: Long): PortionUnit? = portionUnits.findById(id)

    /**
     * A unit the user defines themselves (§6): a known [kind] with their own weight, or a fully
     * custom label. Always counts as verified — the user is reading their own kitchen scale or
     * package, exactly what verification means elsewhere in this app.
     */
    suspend fun saveUserPortionUnit(
        barcode: String,
        kind: PortionUnitKind,
        amountPerUnit: BigDecimal,
        basis: NutritionBasis,
        customLabel: String? = null,
    ): PortionUnit {
        require(kind != PortionUnitKind.CUSTOM || !customLabel.isNullOrBlank()) {
            "a custom portion unit requires a label"
        }
        val now = clock.instant()
        return portionUnits.save(
            PortionUnit(
                productBarcode = barcode,
                kind = kind,
                customLabel = customLabel,
                amountPerUnit = amountPerUnit,
                basis = basis,
                dataSource = ProductDataOrigin.MANUAL,
                verificationStatus = VerificationStatus.USER_VERIFIED,
                verifiedAt = now,
                originalRemoteAmountPerUnit = null,
                latestRemoteAmountPerUnit = null,
                rawRemoteServingText = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /**
     * The user checked a remote-sourced unit against the package (§7), optionally correcting the
     * weight while doing so. Mirrors [saveVerification]: provenance stays Open Food Facts, only
     * verification state and the effective amount change.
     */
    suspend fun verifyPortionUnit(unitId: Long, confirmedAmountPerUnit: BigDecimal? = null): PortionUnit {
        val existing = requirePortionUnit(unitId)
        val verified = existing.copy(
            amountPerUnit = confirmedAmountPerUnit ?: existing.amountPerUnit,
            verificationStatus = VerificationStatus.USER_VERIFIED,
            verifiedAt = clock.instant(),
            updatedAt = clock.instant(),
        )
        return portionUnits.save(verified)
    }

    /** Deliberate acceptance of a newer remote amount (§7, mirrors [applyLatestRemoteValue]). */
    suspend fun applyLatestRemotePortionUnit(unitId: Long): PortionUnit {
        val existing = requirePortionUnit(unitId)
        val latest = existing.latestRemoteAmountPerUnit ?: return existing
        val applied = existing.copy(
            amountPerUnit = latest,
            verificationStatus = VerificationStatus.UNVERIFIED,
            verifiedAt = null,
            updatedAt = clock.instant(),
        )
        return portionUnits.save(applied)
    }

    suspend fun deletePortionUnit(unit: PortionUnit) = portionUnits.delete(unit)

    private fun newPortionUnitFromCandidate(barcode: String, candidate: PortionUnitCandidate): PortionUnit {
        val now = clock.instant()
        return PortionUnit(
            productBarcode = barcode,
            kind = candidate.kind,
            customLabel = null,
            amountPerUnit = candidate.amountPerUnit,
            basis = candidate.basis,
            dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
            verificationStatus = VerificationStatus.UNVERIFIED,
            verifiedAt = null,
            originalRemoteAmountPerUnit = candidate.amountPerUnit,
            latestRemoteAmountPerUnit = candidate.amountPerUnit,
            rawRemoteServingText = candidate.rawServingText,
            createdAt = now,
            updatedAt = now,
        )
    }

    /**
     * Same rule as the product's own carbohydrate refresh, applied per unit (§7, §9): an unverified
     * Open-Food-Facts-sourced unit is kept in sync outright, while a user-verified or user-authored
     * one only has its "latest remote" figure recorded for a notice, never its effective amount.
     */
    private suspend fun refreshPortionUnitFromCandidate(barcode: String, candidate: PortionUnitCandidate?) {
        if (candidate == null) return
        val existingUnits = portionUnits.findByBarcode(barcode)
        val matching = existingUnits.firstOrNull {
            it.dataSource == ProductDataOrigin.OPEN_FOOD_FACTS && it.kind == candidate.kind
        }

        if (matching == null) {
            portionUnits.save(newPortionUnitFromCandidate(barcode, candidate))
            return
        }

        if (matching.isRemoteRefreshable) {
            portionUnits.save(
                matching.copy(
                    amountPerUnit = candidate.amountPerUnit,
                    latestRemoteAmountPerUnit = candidate.amountPerUnit,
                    rawRemoteServingText = candidate.rawServingText,
                    updatedAt = clock.instant(),
                ),
            )
        } else {
            portionUnits.save(
                matching.copy(
                    latestRemoteAmountPerUnit = candidate.amountPerUnit,
                    rawRemoteServingText = candidate.rawServingText,
                    updatedAt = clock.instant(),
                ),
            )
        }
    }

    private suspend fun requirePortionUnit(unitId: Long): PortionUnit =
        portionUnits.findById(unitId) ?: error("no portion unit with id $unitId")

    private suspend fun requireExisting(barcode: String): Product =
        (local.fetch(barcode) as? ProductFetchResult.Found)?.product
            ?: error("no local product for barcode $barcode")
}

package app.carbscan.data

import app.carbscan.domain.LocalProductDataSource
import app.carbscan.domain.NutritionBasis
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
                fetched
            }
            // NotFound, Unusable and Failed all leave the cache untouched: the app does not record
            // an absence, and it never caches a value it refused to trust (§13).
            else -> fetched
        }
    }

    /**
     * Optional background refresh of a cached remote product (§10.2).
     *
     * @return true if the stored record changed.
     */
    suspend fun refreshFromRemote(barcode: String): Boolean {
        val existing = (local.fetch(barcode) as? ProductFetchResult.Found)?.product

        // The whole point of §23: a verified or user-authored product belongs to the user and a
        // sync may not touch it. Returning early — rather than fetching and then discarding — also
        // spends no request against the 15/min rate limit.
        if (existing != null && !existing.isRemoteRefreshable) return false

        val fetched = remote.fetch(barcode) as? ProductFetchResult.Found ?: return false

        // Remote owns the product facts; the device owns how the user has been using it. Merging
        // rather than replacing is what stops a refresh from silently clearing a favourite.
        val merged = fetched.product.copy(
            favorite = existing?.favorite ?: false,
            lastPortion = existing?.lastPortion,
            lastUsedAt = existing?.lastUsedAt,
            remoteUpdatedAt = clock.instant(),
        )
        local.save(merged)
        return true
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
        local.save(
            product.copy(
                dataSource = origin,
                verificationStatus = VerificationStatus.USER_VERIFIED,
                verifiedAt = clock.instant(),
            ),
        )
    }

    /** Remember the portion so the next visit pre-fills it, and float the product up Recents (§20, §21). */
    suspend fun recordUse(barcode: String, portion: BigDecimal) {
        val existing = requireExisting(barcode)
        local.save(existing.copy(lastPortion = portion, lastUsedAt = clock.instant()))
    }

    suspend fun setFavorite(barcode: String, favorite: Boolean) {
        val existing = requireExisting(barcode)
        local.save(existing.copy(favorite = favorite))
    }

    fun observeRecents(limit: Int): Flow<List<Product>> = local.observeRecents(limit)

    private suspend fun requireExisting(barcode: String): Product =
        (local.fetch(barcode) as? ProductFetchResult.Found)?.product
            ?: error("no local product for barcode $barcode")
}

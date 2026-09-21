package app.justthecarbs.data

import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.LocalProductDataSource
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.MealStore
import app.justthecarbs.domain.MealItemKind
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionConversion
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.PortionUnitCandidate
import app.justthecarbs.domain.PortionUnitKind
import app.justthecarbs.domain.PortionUnitStore
import app.justthecarbs.domain.PortionUsage
import app.justthecarbs.domain.PortionUsageStore
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.ProductDataSource
import app.justthecarbs.domain.ProductFetchResult
import app.justthecarbs.domain.ProductSearchResult
import app.justthecarbs.domain.ProductSearchSource
import app.justthecarbs.domain.RecentUseSnapshot
import app.justthecarbs.domain.UsualPortionSelector
import app.justthecarbs.domain.VerificationStatus
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration

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
    data class RemoteDiffers(val latestRemoteCarbs: java.math.BigDecimal, val basis: NutritionBasis) : RefreshOutcome
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
    private val meal: MealStore,
    private val portionUsage: PortionUsageStore,
    private val searchSource: ProductSearchSource,
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
                // Stamped as synced *now*, because it was. Without this the row is saved with a
                // null `remoteUpdatedAt`, which reads as "never refreshed" — so the background
                // refresh that follows immediately in the same load re-downloads the bytes still
                // in hand, spending a second request from the 15 reads/min/IP budget on a product
                // fetched microseconds earlier. See [refreshFromRemote]'s freshness guard.
                //
                // The caller gets the *stamped* product, not the one that came off the wire: the
                // returned product and the cached row are the same record, so a caller reading
                // `remoteUpdatedAt` cannot conclude "never synced" about a row this very call has
                // just marked as synced.
                val stamped = fetched.product.copy(remoteUpdatedAt = clock.instant())
                local.save(stamped)
                // First sighting of this product: a suggested countable unit becomes a stored one
                // outright, since there is nothing local yet for it to conflict with (§7, §9).
                fetched.portionUnitCandidate?.let { candidate ->
                    portionUnits.save(newPortionUnitFromCandidate(barcode, candidate))
                }
                fetched.copy(product = stamped)
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

        // Already up to date — nothing a second request could learn.
        //
        // The calculator calls this straight after [lookup], which is correct for a product served
        // from the cache (that path never touched the network, so this is the only thing that can
        // notice a reformulation) and wasteful for one just downloaded. Deciding here rather than at
        // the call site keeps the freshness rule in the class that owns the lookup priority, and
        // means every caller gets it — the ViewModel does not have to know whether the lookup it
        // just made was a cache hit or a miss.
        if (isRemotelyFresh(existing)) return RefreshOutcome.Unchanged

        val fetchedResult = remote.fetch(barcode) as? ProductFetchResult.Found
            ?: return RefreshOutcome.Unchanged
        val fetched = fetchedResult.product

        refreshPortionUnitFromCandidate(barcode, fetchedResult.portionUnitCandidate)

        // Re-read immediately before writing, rather than merging onto the snapshot taken before
        // `remote.fetch` suspended (P0 §4). The network call above can take hundreds of
        // milliseconds; if the user favourites this product, changes its remembered portion, or
        // records a new use while it is in flight, `existing` no longer describes the row on disk.
        // Merging onto it anyway is a classic lost update: this save would silently roll that
        // change back to whatever `existing` held at the *start* of the network call. Falling back
        // to `existing` only if the row has vanished entirely (a real edge case a concurrent
        // deletion could produce) rather than aborting the refresh outright.
        val latest = (local.fetch(barcode) as? ProductFetchResult.Found)?.product ?: existing

        val remoteCarbs = fetched.carbsPer100
        val differs = remoteCarbs.compareTo(latest.carbsPer100) != 0 || fetched.basis != latest.basis

        // §23: a verified or user-authored product belongs to the user. The newer remote figure is
        // *recorded* so the app can mention that the product may have been reformulated (§24), but
        // the value in use is never replaced.
        if (!latest.isRemoteRefreshable) {
            // `latest.copy` starts from the stored row, so `localAlias` is carried by construction
            // here — unlike the refreshable branch below, which starts from the wire. Both branches
            // are covered by tests, because "preserved by construction" is a property of the shape
            // of this expression and the next edit to it could silently remove it.
            //
            // Written transactionally as well, which makes that construction argument a belt to the
            // transaction's braces: `latest` was read before this function's own `refreshPortion‑
            // UnitFromCandidate` and the basis-change clearing, and a rename landing in either
            // window would otherwise be rolled back here.
            local.saveProductFacts(
                latest.copy(
                    latestRemoteCarbs = remoteCarbs,
                    latestRemoteBasis = fetched.basis,
                    remoteUpdatedAt = clock.instant(),
                    // Images are display-only metadata. Refreshing them must not move the product
                    // facts or the immutable calculation snapshot the user is working with.
                    images = fetched.images.ifEmpty { latest.images },
                    imageUrl = fetched.imageUrl ?: latest.imageUrl,
                    largeImageUrl = fetched.largeImageUrl ?: latest.largeImageUrl,
                ),
            )
            return if (differs) RefreshOutcome.RemoteDiffers(remoteCarbs, fetched.basis) else RefreshOutcome.Unchanged
        }

        // Plain cached remote data: the cache may be brought up to date, because it is the
        // provider's value either way and the user has not expressed an opinion about it.
        // Remote owns the product facts; the device owns how the user has been using it, so
        // merging rather than replacing stops a refresh from clearing a favourite.
        //
        // Every device-owned field is carried forward from `latest`, not just the three this once
        // preserved (`favorite`, `lastPortion`, `lastUsedAt`): `lastInputMode`,
        // `lastSelectedPortionUnitId` and `lastCount` together *are* a remembered countable portion
        // ("2 slices"), and omitting them let an ordinary background refresh silently erase it,
        // since `fetched` — a value straight off the wire — carries `Product`'s defaults (null) for
        // all three.
        val usageCompatible = clearUsageForBasisChange(latest, fetched.basis)
        // Transactional: this branch starts from `fetched`, a value straight off the wire that
        // holds `Product`'s defaults for every device-owned column, so the preservation below is
        // load-bearing rather than incidental — and the transaction is what makes it hold against a
        // rename or a star landing during the network call or the usage clearing above.
        local.saveProductFacts(
            fetched.copy(
                favorite = latest.favorite,
                lastPortion = usageCompatible.lastPortion,
                lastUsedAt = latest.lastUsedAt,
                lastInputMode = usageCompatible.lastInputMode,
                lastSelectedPortionUnitId = usageCompatible.lastSelectedPortionUnitId,
                lastCount = usageCompatible.lastCount,
                // The personal name is device-owned, like the favourite and the remembered portion
                // above it, and must be carried forward explicitly: `fetched` came off the wire and
                // therefore holds `Product`'s default (null) for it. Omitting this line would make
                // an ordinary background refresh silently discard a rename — the user would open a
                // product they had named, see the provider's name back, and have nothing on screen
                // to explain it. Remote owns what the product *is*; the device owns what this user
                // calls it.
                localAlias = latest.localAlias,
                // A response may temporarily omit selected_images. Keep previously validated
                // display metadata rather than turning a cached offline gallery into an empty one.
                images = fetched.images.ifEmpty { latest.images },
                imageUrl = fetched.imageUrl ?: latest.imageUrl,
                largeImageUrl = fetched.largeImageUrl ?: latest.largeImageUrl,
                latestRemoteCarbs = remoteCarbs,
                    latestRemoteBasis = fetched.basis,
                remoteUpdatedAt = clock.instant(),
            ),
        )
        return if (differs) RefreshOutcome.RemoteDiffers(remoteCarbs, fetched.basis) else RefreshOutcome.Unchanged
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
        val compatible = clearUsageForBasisChange(existing, basis)
        // Re-read immediately before writing, exactly as [refreshFromRemote] does and for the same
        // reason: `clearUsageForBasisChange` above suspends whenever the basis actually changes
        // (it deletes this product's usage rows), and a rename or a favourite landing in that
        // window would be rolled back by this whole-row save.
        //
        // Only the **independently owned** columns are taken from the newer row. Everything else
        // here is a coherent set of product facts the user is deliberately replacing, and must come
        // from the snapshot the decisions above were made from — re-deriving `originalRemoteCarbs`
        // from a row that may itself have been re-verified in the meantime would overwrite the
        // first online value with a correction of it, which is the thing the `?:` chain above
        // exists to prevent.
        val originalBasis = when {
            existing.dataSource.isUserAuthored -> null
            existing.originalRemoteCarbs != null -> existing.originalRemoteBasis
            existing.verificationStatus == VerificationStatus.UNVERIFIED -> existing.basis
            else -> null
        }
        // The transactional write: the alias and the star are preserved *inside* the transaction,
        // so there is no window between reading them and writing the row. A Kotlin re-read here
        // would only narrow that window — the read and the write would still be two statements.
        local.saveProductFacts(
            compatible.copy(
                name = name ?: existing.name,
                carbsPer100 = verifiedCarbsPer100,
                basis = basis,
                packageAmount = packageAmount ?: compatible.packageAmount,
                verificationStatus = VerificationStatus.USER_VERIFIED,
                originalRemoteCarbs = onlineOriginal,
                originalRemoteBasis = originalBasis,
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
        val basis = existing.latestRemoteBasis ?: return
        val compatible = clearUsageForBasisChange(existing, basis)
        // Transactional, for [saveVerification]'s reason: the name and the star are not part of
        // what accepting a newer figure replaces.
        local.saveProductFacts(
            compatible.copy(
                basis = basis,
                carbsPer100 = latest,
                originalRemoteCarbs = existing.originalRemoteCarbs ?: existing.carbsPer100,
                originalRemoteBasis = if (existing.originalRemoteCarbs != null) existing.originalRemoteBasis else existing.basis,
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
        val basis = existing.originalRemoteBasis ?: return
        val compatible = clearUsageForBasisChange(existing, basis)
        // Transactional, for [saveVerification]'s reason. Undoing a verification is a statement
        // about a figure and about nothing else the user owns.
        local.saveProductFacts(
            compatible.copy(
                basis = basis,
                carbsPer100 = online,
                verificationStatus = VerificationStatus.UNVERIFIED,
                originalRemoteCarbs = null,
                originalRemoteBasis = null,
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
     *
     * [portion] is **nullable**, and that is the whole point of its type (correction pass §4).
     * `lastPortion` is strictly a resolved mass or volume in the product's own basis unit — it
     * pre-fills the grams field and nothing else. A direct-carb portion ("4 slices × 14.2 g carbs")
     * resolves no weight at all, so null is the only honest value, and the previously remembered
     * weight is left exactly as it was. The caller must never substitute the count here: "4 slices"
     * written into `lastPortion` becomes "4 g" the next time the grams field is shown.
     */
    suspend fun recordUse(
        barcode: String,
        portion: BigDecimal?,
        mode: InputMode? = null,
        portionUnitId: Long? = null,
        count: BigDecimal? = null,
        expectedBasis: NutritionBasis? = null,
    ) {
        val existing = requireExisting(barcode)
        if (expectedBasis != null && expectedBasis != existing.basis) return

        // A narrow write over the five remembered-use columns, not `save(existing.copy(...))`.
        //
        // Those five are a coherent group and move together, which is why they are one statement.
        // Everything *else* on the row is independently owned — the alias, the star, the figure,
        // verification — and a whole-row save from `existing` would roll each of them back to
        // whatever it was when this function read, discarding anything that landed while the
        // suspension below it ran. The measured case: a rename saved during a recorded use came
        // back null. The coalescing and the grams-mode clearing now live in the statement, because
        // applying them here is precisely what required the stale snapshot.
        local.recordUsageColumns(
            barcode = barcode,
            // Null preserves: only ever advanced by a real resolved amount, so a direct-carb use
            // leaves the previously remembered weight exactly as it was.
            lastPortion = portion,
            lastUsedAt = clock.instant(),
            lastInputMode = mode,
            lastSelectedPortionUnitId = portionUnitId,
            lastCount = count,
        )

        // Feed *Usual* from the same event that already means "the user settled on this portion"
        // (§13). The variant recorded is what the user actually chose — a count against a unit in
        // countable mode, the raw amount in grams mode — never the resolved grams behind a count,
        // which would make "2 slices" indistinguishable from having typed 72 g.
        val effectiveMode = mode ?: existing.lastInputMode ?: InputMode.GRAMS
        val variantAmount = if (effectiveMode == InputMode.PORTION_UNIT) count else portion
        if (variantAmount != null) {
            recordPortionUsage(
                barcode = barcode,
                inputMode = effectiveMode,
                portionUnitId = portionUnitId ?: existing.lastSelectedPortionUnitId,
                amount = variantAmount,
            )
        }
    }

    private suspend fun clearUsageForBasisChange(product: Product, basis: NutritionBasis): Product {
        if (product.basis == basis) return product
        portionUsage.findByBarcode(product.barcode).forEach { portionUsage.delete(it) }
        return product.copy(packageAmount = null, servingAmount = null, lastPortion = null,
            lastInputMode = null, lastSelectedPortionUnitId = null, lastCount = null)
    }

    /**
     * Stars or unstars a product, writing that one column.
     *
     * Deliberately **not** `local.save(existing.copy(favorite = …))`, which is what this was and
     * which is the same shape [setLocalAlias] already refuses. A star is toggled from Home or from
     * a product screen that may have been open for minutes, so the snapshot behind it is exactly
     * the one most likely to be stale — and writing it back would roll back a rename, a recorded
     * use or a refreshed figure that landed while it was open.
     *
     * The read is kept so an unknown barcode still fails the way every other metadata write on this
     * repository fails, rather than silently doing nothing; the *write* reads none of it.
     */
    suspend fun setFavorite(barcode: String, favorite: Boolean) {
        requireExisting(barcode)
        local.setFavorite(barcode, favorite)
    }

    /**
     * Gives this product a personal name on this device, or removes the one it has.
     *
     * The normalisation rule lives here, at the one boundary every caller passes through:
     * surrounding whitespace is trimmed, a blank result becomes `null`, and an alias is capped at
     * [Product.MAX_LOCAL_ALIAS_LENGTH]. So "an alias of spaces" is not a state the store, the search or the
     * UI ever has to consider — `Product.localAlias` is null or meaningful, never in between.
     *
     * Deliberately **not** implemented as `local.save(existing.copy(localAlias = …))`, which is how
     * [setFavorite] above works and would have been the obvious symmetry. That shape re-writes every
     * column from a product the caller is holding, and a rename is the one action most likely to be
     * performed on a stale snapshot: the product screen loads once and stays open. Writing the whole
     * row back would roll back a refreshed figure, a toggled favourite or a recorded use that landed
     * while the user was deciding what to call something. [LocalProductDataSource.setLocalAlias]
     * writes one column and reads none, so nothing else can be lost.
     *
     * Nothing else moves. Provenance, verification, `originalRemoteCarbs`/`latestRemoteCarbs` and
     * the remembered portion are untouched, and naming a product is emphatically **not** a statement
     * that its figure has been checked against the package — §23's verification means one specific
     * thing and a nickname is not evidence for it.
     */
    suspend fun setLocalAlias(barcode: String, alias: String?) {
        val normalised = alias?.trim()?.take(Product.MAX_LOCAL_ALIAS_LENGTH)?.takeIf { it.isNotEmpty() }
        local.setLocalAlias(barcode, normalised)
    }

    fun observeRecents(limit: Int): Flow<List<Product>> = local.observeRecents(limit)

    /**
     * *Remove from Recent* for one product — the per-product form of Settings' *Clear recent
     * history* (§43), with the same definition of "usage" and the same list of things it preserves.
     *
     * Delegates rather than composing the two writes here, because atomicity is the whole point: see
     * [LocalProductDataSource.forgetRecentUse]. Note the contrast with
     * [clearUsageForBasisChange], which clears the same facts non-atomically — that one is a
     * consequence of a value the user has just changed, not a promise made to them about what has
     * been erased, and it has no Undo to be exact for.
     *
     * Returns the snapshot to hold for Undo, or null if the barcode is unknown.
     */
    suspend fun forgetRecentUse(barcode: String): RecentUseSnapshot? = local.forgetRecentUse(barcode)

    /** Puts one [forgetRecentUse] back. Restores usage only; never resurrects a deleted product. */
    suspend fun restoreRecentUse(snapshot: RecentUseSnapshot) = local.restoreRecentUse(snapshot)

    /**
     * Free-text product search — the fallback when a barcode does not resolve (spec §9).
     *
     * Returns candidates only. Selecting one goes through [lookup] like any other barcode, so a
     * searched product is cached, validated and given provenance by exactly the same path as a
     * scanned one — there is no second way for a product to enter this app.
     */
    suspend fun search(terms: String): ProductSearchResult = searchSource.search(terms)

    // ---- countable portions (brief §2, §6-§9) --------------------------------------------------

    fun observePortionUnits(barcode: String): Flow<List<PortionUnit>> = portionUnits.observeByBarcode(barcode)

    suspend fun findPortionUnits(barcode: String): List<PortionUnit> = portionUnits.findByBarcode(barcode)

    suspend fun findPortionUnit(id: Long): PortionUnit? = portionUnits.findById(id)

    /** Batch lookup for Home's recents row (P1 §13) — one call for the whole visible list. */
    suspend fun findPortionUnits(ids: List<Long>): List<PortionUnit> = portionUnits.findByIds(ids)

    /**
     * A unit the user defines themselves (§6): a known [kind] with their own weight, or a fully
     * custom label. Always counts as verified — the user is reading their own kitchen scale or
     * package, exactly what verification means elsewhere in this app.
     */
    suspend fun saveUserPortionUnit(
        barcode: String,
        kind: PortionUnitKind,
        conversion: PortionConversion,
        customLabel: String? = null,
        origin: ProductDataOrigin = ProductDataOrigin.MANUAL,
    ): PortionUnit {
        require(kind != PortionUnitKind.CUSTOM || !customLabel.isNullOrBlank()) {
            "a custom portion unit requires a label"
        }
        require(origin.isUserAuthored) { "$origin is not a user-authored origin" }
        val now = clock.instant()
        return portionUnits.save(
            PortionUnit(
                productBarcode = barcode,
                kind = kind,
                customLabel = customLabel,
                conversion = conversion,
                dataSource = origin,
                verificationStatus = VerificationStatus.USER_VERIFIED,
                verifiedAt = now,
                originalRemoteConversion = null,
                latestRemoteConversion = null,
                rawRemoteServingText = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /**
     * Create a product and its first countable portion, in that order (correction pass §2).
     *
     * The ordering is the entire point. `portion_units.productBarcode` is a foreign key to
     * `products.barcode`, so a portion for a product that does not exist yet cannot be stored at
     * all. The OCR capture flow reaches exactly that situation: the barcode was scanned, Open Food
     * Facts did not recognise it, and the user then photographs the nutrition table — the case where
     * capturing a countable portion is most valuable, and the one where a naive save fails.
     *
     * Both writes are awaited, and a failure in either propagates to the caller rather than being
     * swallowed. That is what lets the UI show *saved* only once the data is genuinely on disk: the
     * previous flow set its "saved" flag synchronously, immediately after firing an asynchronous
     * save, so a failed insert still rendered as success.
     *
     * A single Room transaction is not used here because the two writes go through two independent
     * store abstractions ([LocalProductDataSource] and [PortionUnitStore]) that the domain layer
     * deliberately keeps separate. Ordering plus propagated failure gives the property that matters:
     * a portion never exists without its product. The reverse — a product with no portion — is a
     * perfectly ordinary state the user can retry from, not corruption.
     */
    suspend fun saveProductWithPortionUnit(
        product: Product,
        kind: PortionUnitKind,
        conversion: PortionConversion,
        customLabel: String? = null,
        origin: ProductDataOrigin = ProductDataOrigin.OCR,
    ): PortionUnit {
        require(product.barcode.isNotEmpty()) { "a portion unit requires a product barcode" }
        saveUserAuthoredProduct(product, origin = origin)
        return saveUserPortionUnit(
            barcode = product.barcode,
            kind = kind,
            conversion = conversion,
            customLabel = customLabel,
            origin = origin,
        )
    }

    /**
     * The user checked a remote-sourced unit against the package (§7), optionally correcting the
     * weight while doing so. Mirrors [saveVerification]: provenance stays Open Food Facts, only
     * verification state and the effective amount change.
     */
    suspend fun verifyPortionUnit(unitId: Long, confirmedConversion: PortionConversion? = null): PortionUnit {
        val existing = requirePortionUnit(unitId)
        val verified = existing.copy(
            conversion = confirmedConversion ?: existing.conversion,
            verificationStatus = VerificationStatus.USER_VERIFIED,
            verifiedAt = clock.instant(),
            updatedAt = clock.instant(),
        )
        return portionUnits.save(verified)
    }

    /** Deliberate acceptance of a newer remote amount (§7, mirrors [applyLatestRemoteValue]). */
    suspend fun applyLatestRemotePortionUnit(unitId: Long): PortionUnit {
        val existing = requirePortionUnit(unitId)
        val latest = existing.latestRemoteConversion ?: return existing
        val applied = existing.copy(
            conversion = latest,
            verificationStatus = VerificationStatus.UNVERIFIED,
            verifiedAt = null,
            updatedAt = clock.instant(),
        )
        return portionUnits.save(applied)
    }

    suspend fun deletePortionUnit(unit: PortionUnit) = portionUnits.delete(unit)

    // ---- temporary meal (development-pass brief §7-§10) ----------------------------------------

    /**
     * The current meal, in the order the user added things.
     *
     * There is exactly one meal and it lives only until [clearMeal]. Nothing here takes or returns a
     * meal id, a date, or a name, because the app stores a working total — not a food diary (§8).
     */
    fun observeMealItems(): Flow<List<MealItem>> = meal.observeItems()

    suspend fun findMealItems(): List<MealItem> = meal.findItems()

    /**
     * Add a completed calculation to the meal (§9).
     *
     * Takes the already-computed [exactCarbs] rather than recomputing from the product: the item is
     * a snapshot of the number the user actually saw and accepted, and re-deriving it here would be
     * a second place where a carbohydrate figure gets produced. The app keeps one formula, in
     * [app.justthecarbs.domain.CarbCalculator], and this method only records its output.
     */
    suspend fun addMealItem(
        productBarcode: String?,
        displayName: String,
        portionDescription: String,
        resolvedAmount: BigDecimal,
        basis: NutritionBasis,
        carbsPer100: BigDecimal,
        exactCarbs: BigDecimal,
    ): MealItem = meal.add(
        MealItem.weightBased(
            productBarcode = productBarcode?.takeIf { it.isNotEmpty() },
            displayName = displayName,
            portionDescription = portionDescription,
            resolvedAmount = resolvedAmount,
            basis = basis,
            carbsPer100 = carbsPer100,
            exactCarbs = exactCarbs,
            addedAt = clock.instant(),
        ),
    )

    /**
     * Add a completed direct-carb calculation to the meal (spec §12).
     *
     * Deliberately takes no resolved amount and no basis: on this path the app does not know what
     * the portion weighs. Writing a derived gram figure here would make a counted portion
     * indistinguishable from a weighed one, which is the exact confusion [MealItemKind] exists to
     * prevent. As with [addMealItem], [exactCarbs] is the number the user already saw — nothing is
     * recomputed here.
     */
    suspend fun addDirectCarbMealItem(
        productBarcode: String?,
        displayName: String,
        portionDescription: String,
        count: BigDecimal,
        carbsPerUnit: BigDecimal,
        exactCarbs: BigDecimal,
    ): MealItem = meal.add(
        MealItem.directCarbs(
            productBarcode = productBarcode?.takeIf { it.isNotEmpty() },
            displayName = displayName,
            portionDescription = portionDescription,
            count = count,
            carbsPerUnit = carbsPerUnit,
            exactCarbs = exactCarbs,
            addedAt = clock.instant(),
        ),
    )

    /** Replace a line wholesale — the caller has recalculated it via `CarbCalculator` (§10). */
    suspend fun updateMealItem(item: MealItem) = meal.update(item)

    suspend fun removeMealItem(item: MealItem) = meal.remove(item)

    /**
     * Put back an item the user has just removed, for Undo (§5.3).
     *
     * Re-inserts the **snapshot** rather than recomputing anything: a `MealItem` already holds every
     * figure needed to re-display and re-total the line, and re-deriving it from the product here
     * would reintroduce exactly the drift the snapshot exists to prevent — the product may have been
     * refreshed, corrected or deleted between the removal and the Undo.
     *
     * `id` is cleared because the row it named is gone; SQLite assigns a new one on insert. Nothing
     * else about the line changes, including [MealItem.addedAt], so a restored item sorts back into
     * its original position rather than jumping to the end of the meal.
     */
    suspend fun restoreMealItem(item: MealItem): MealItem = meal.add(item.copy(id = 0))

    /** End the meal. A `DELETE FROM`, not an archive — nothing is kept (§8). */
    suspend fun clearMeal() = meal.clear()

    // ---- usual portions (brief §13, §22) -------------------------------------------------------

    /**
     * The portions this product is usually eaten in, best first, or empty until a pattern exists.
     *
     * Delegates the entire decision to [UsualPortionSelector] so the "what counts as usual" rules
     * live in one pure, JVM-testable place rather than in a SQL `ORDER BY`.
     */
    suspend fun usualPortions(barcode: String): List<PortionUsage> =
        UsualPortionSelector.suggest(portionUsage.findByBarcode(barcode))

    /**
     * Count one use of one portion variant (§13).
     *
     * Increments an aggregate row; it never appends an event. Repeatedly eating two slices raises a
     * counter from 1 to 2 to 3 — it does not accumulate three timestamps — so the table can answer
     * "what is usual?" and remains structurally unable to answer "when did you eat?" (§22).
     *
     * Pruning runs here rather than on a schedule: the moment a product's usage changes is exactly
     * when its low-value variants become identifiable, and doing it inline means the app has no
     * background job quietly grooming a record of the user's meals.
     */
    suspend fun recordPortionUsage(
        barcode: String,
        inputMode: InputMode,
        portionUnitId: Long?,
        amount: BigDecimal,
    ) {
        if (barcode.isEmpty() || amount.signum() <= 0) return
        // Normalised so "2", "2.0" and "2.00" are one variant, not three. The column is TEXT, so
        // without this the unique index would treat them as distinct rows and nothing would ever
        // reach the two-uses threshold.
        val normalised = amount.stripTrailingZeros()
        val unitId = portionUnitId.takeIf { inputMode == InputMode.PORTION_UNIT }

        // A single atomic call (P0 §5), not a find-then-decide-then-save sequence: two concurrent
        // recordings of the identical variant (e.g. rapid double-tap Add-to-meal calling
        // `rememberUsage` twice) must never race a read from one against a write from the other.
        // See [PortionUsageDao.recordUse]'s KDoc for the failure this replaces.
        portionUsage.recordUse(barcode, inputMode, unitId, normalised, clock.instant())

        UsualPortionSelector.prunable(portionUsage.findByBarcode(barcode))
            .forEach { portionUsage.delete(it) }
    }

    /**
     * Whether this record was synced recently enough that refreshing it again would learn nothing.
     *
     * A null [Product.remoteUpdatedAt] is deliberately **not** fresh: it means the row has never
     * been refreshed — a product cached before this stamp existed, or one the user authored — and
     * those must still be checked. Only a positive, recent timestamp suppresses a request.
     *
     * The window is short on purpose. It exists to collapse the duplicate request inside one load,
     * not to become a cache policy: a user who reopens a product minutes later still gets a fresh
     * check, which is what keeps the reformulation notice (§24, correction #10) meaningful.
     */
    private fun isRemotelyFresh(product: Product): Boolean {
        val lastSync = product.remoteUpdatedAt ?: return false
        val age = Duration.between(lastSync, clock.instant())
        // A negative age means the stamp is in the future — a clock change, not freshness.
        return !age.isNegative && age < REMOTE_FRESHNESS_WINDOW
    }

    private fun newPortionUnitFromCandidate(barcode: String, candidate: PortionUnitCandidate): PortionUnit {
        val now = clock.instant()
        return PortionUnit(
            productBarcode = barcode,
            kind = candidate.kind,
            customLabel = null,
            conversion = candidate.conversion,
            dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
            verificationStatus = VerificationStatus.UNVERIFIED,
            verifiedAt = null,
            originalRemoteConversion = candidate.conversion,
            latestRemoteConversion = candidate.conversion,
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
                    conversion = candidate.conversion,
                    latestRemoteConversion = candidate.conversion,
                    rawRemoteServingText = candidate.rawServingText,
                    updatedAt = clock.instant(),
                ),
            )
        } else {
            portionUnits.save(
                matching.copy(
                    latestRemoteConversion = candidate.conversion,
                    rawRemoteServingText = candidate.rawServingText,
                    updatedAt = clock.instant(),
                ),
            )
        }
    }

    private suspend fun requirePortionUnit(unitId: Long): PortionUnit =
        portionUnits.findById(unitId) ?: error("no portion unit with id $unitId")

    /**
     * The stored product for [barcode], or null — a **local-only** read that never touches the
     * network.
     *
     * Added for one question the OCR portion flow has to answer before offering to save: does a
     * `products` row exist for this barcode? That is what `portion_units.productBarcode`'s foreign
     * key requires, and it is not the same question as "is the barcode string non-empty", which is
     * what the flow previously used (correction pass §2).
     *
     * Deliberately not [lookup]: that falls through to Open Food Facts and would spend one of the
     * 15 reads/min/IP budget, and worse, could *create* the row as a side effect — turning a
     * question into an action.
     */
    suspend fun findLocal(barcode: String): Product? =
        (local.fetch(barcode) as? ProductFetchResult.Found)?.product

    private suspend fun requireExisting(barcode: String): Product =
        (local.fetch(barcode) as? ProductFetchResult.Found)?.product
            ?: error("no local product for barcode $barcode")

    private companion object {
        /**
         * How recently a product must have been synced for a refresh to be skipped.
         *
         * Sized to cover one load — the fetch, the save and the refresh call that follows it — and
         * nothing more. Deliberately far shorter than any interval at which a user would revisit a
         * product, so the only request it ever removes is the redundant one.
         */
        val REMOTE_FRESHNESS_WINDOW: Duration = Duration.ofSeconds(30)
    }
}

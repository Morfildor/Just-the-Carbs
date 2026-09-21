package app.justthecarbs.data

import app.justthecarbs.domain.LocalProductDataSource
import app.justthecarbs.domain.MealStore
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionUnit
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
import app.justthecarbs.domain.VerificationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * *Rename on this device* — [Product.localAlias] (1.0.8).
 *
 * An alias is device-local presentation metadata, and every test here is a statement of something it
 * must **not** disturb. That emphasis is deliberate: the feature's whole risk is not that renaming
 * fails but that it quietly takes something else with it — a favourite, a verified figure, a
 * refreshed value — because a rename was implemented as "write the product back with a new name".
 *
 * The fake below is therefore *column-accurate* rather than convenient: `setLocalAlias` mutates one
 * field of the stored row and copies nothing else, exactly as the `UPDATE` in `ProductDao` does. A
 * fake that reimplemented the alias write as a whole-row save would make every preservation test
 * here pass while the real defect it exists to catch sat in production.
 */
class LocalAliasTest {

    private val now: Instant = Instant.parse("2026-09-20T10:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val barcode = "8712100849060"

    private fun product(
        name: String = "AH Volkoren Tarwebrood 800g",
        carbs: String = "41.5",
        alias: String? = null,
        origin: ProductDataOrigin = ProductDataOrigin.OPEN_FOOD_FACTS,
        status: VerificationStatus = VerificationStatus.UNVERIFIED,
        favorite: Boolean = false,
    ) = Product(
        barcode = barcode,
        name = name,
        carbsPer100 = BigDecimal(carbs),
        basis = NutritionBasis.PER_100_G,
        dataSource = origin,
        verificationStatus = status,
        favorite = favorite,
        localAlias = alias,
    )

    /**
     * A local store whose `setLocalAlias` writes **one column**, like the real `UPDATE`.
     *
     * See the class KDoc: implementing it as `stored[barcode] = product.copy(localAlias = …)` would
     * be simpler and would destroy the point of half these tests.
     */
    private class ColumnAccurateLocal(seed: List<Product> = emptyList()) : LocalProductDataSource {
        val stored = seed.associateBy { it.barcode }.toMutableMap()
        var aliasWrites = 0
            private set
        var fullSaves = 0
            private set

        override suspend fun fetch(barcode: String): ProductFetchResult =
            stored[barcode]?.let { ProductFetchResult.Found(it) } ?: ProductFetchResult.NotFound

        override suspend fun save(product: Product) {
            fullSaves++
            stored[product.barcode] = product
        }

        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(stored.values.toList())

        // Column-accurate, like the real `UPDATE`s and `@Transaction` in `ProductDao` (1.0.8
        // lost-update hardening). Implementing these as whole-row copies would make every
        // preservation test in this repo pass while the defect they exist to catch sat in
        // production — the trap `LocalAliasTest` already records for the alias write.
        override suspend fun setFavorite(barcode: String, favorite: Boolean) {
            stored[barcode] = stored[barcode]?.copy(favorite = favorite) ?: return
        }

        override suspend fun recordUsageColumns(
            barcode: String,
            lastPortion: java.math.BigDecimal?,
            lastUsedAt: java.time.Instant,
            lastInputMode: app.justthecarbs.domain.InputMode?,
            lastSelectedPortionUnitId: Long?,
            lastCount: java.math.BigDecimal?,
        ) {
            val existing = stored[barcode] ?: return
            stored[barcode] = existing.copy(
                lastPortion = lastPortion ?: existing.lastPortion,
                lastUsedAt = lastUsedAt,
                lastInputMode = lastInputMode ?: existing.lastInputMode,
                lastSelectedPortionUnitId =
                    if (lastInputMode == app.justthecarbs.domain.InputMode.GRAMS) null
                    else lastSelectedPortionUnitId ?: existing.lastSelectedPortionUnitId,
                lastCount =
                    if (lastInputMode == app.justthecarbs.domain.InputMode.GRAMS) null
                    else lastCount ?: existing.lastCount,
            )
        }

        override suspend fun saveProductFacts(product: Product) {
            val current = stored[product.barcode]
            save(
                if (current == null) product
                else product.copy(localAlias = current.localAlias, favorite = current.favorite),
            )
        }

        override suspend fun setLocalAlias(barcode: String, alias: String?) {
            aliasWrites++
            // One column. Nothing is read from a caller-supplied product, so nothing else can move.
            stored[barcode] = stored[barcode]?.copy(localAlias = alias) ?: return
        }

        override suspend fun forgetRecentUse(barcode: String): RecentUseSnapshot? =
            error("this fake does not implement forgetRecentUse")

        override suspend fun restoreRecentUse(snapshot: RecentUseSnapshot) =
            error("this fake does not implement restoreRecentUse")
    }

    private class FakeRemote(private val result: ProductFetchResult) : ProductDataSource {
        override suspend fun fetch(barcode: String): ProductFetchResult = result
    }

    // Empty collaborators. This file is about one column on `products`; portion units, the meal and
    // usage are untouched by an alias and are stubbed rather than faked so a change in any of them
    // cannot make an alias test go red for an unrelated reason.
    private class NoPortionUnits : PortionUnitStore {
        override suspend fun findByBarcode(barcode: String): List<PortionUnit> = emptyList()
        override fun observeByBarcode(barcode: String): Flow<List<PortionUnit>> = flowOf(emptyList())
        override suspend fun findById(id: Long): PortionUnit? = null
        override suspend fun save(unit: PortionUnit): PortionUnit = unit
        override suspend fun delete(unit: PortionUnit) = Unit
    }

    private class NoMeal : MealStore {
        override fun observeItems(): Flow<List<MealItem>> = flowOf(emptyList())
        override suspend fun findItems(): List<MealItem> = emptyList()
        override suspend fun add(item: MealItem): MealItem = item
        override suspend fun update(item: MealItem) = Unit
        override suspend fun remove(item: MealItem) = Unit
        override suspend fun clear() = Unit
    }

    private class NoUsage : PortionUsageStore {
        override suspend fun findByBarcode(barcode: String): List<PortionUsage> = emptyList()
        override suspend fun findVariant(
            barcode: String,
            inputMode: app.justthecarbs.domain.InputMode,
            portionUnitId: Long?,
            amount: BigDecimal,
        ): PortionUsage? = null
        override suspend fun save(usage: PortionUsage): PortionUsage = usage
        override suspend fun delete(usage: PortionUsage) = Unit
    }

    private val noSearch = object : ProductSearchSource {
        override suspend fun search(terms: String) = ProductSearchResult.NoMatches
    }

    private fun repositoryOf(
        local: LocalProductDataSource,
        remote: ProductDataSource = FakeRemote(ProductFetchResult.NotFound),
    ) = ProductRepository(
        local = local,
        remote = remote,
        portionUnits = NoPortionUnits(),
        meal = NoMeal(),
        portionUsage = NoUsage(),
        searchSource = noSearch,
        clock = clock,
    )

    private fun storedProduct(local: ColumnAccurateLocal) = local.stored.getValue(barcode)

    // ---- displayName ----------------------------------------------------------------------------

    @Test
    fun `displayName is the canonical name when there is no alias`() {
        assertEquals("AH Volkoren Tarwebrood 800g", product().displayName)
    }

    @Test
    fun `displayName prefers a non-blank alias`() {
        assertEquals("Breakfast bread", product(alias = "Breakfast bread").displayName)
    }

    @Test
    fun `a blank alias is not a name and falls back to the canonical one`() {
        // Belt-and-braces: the repository normalises blank to null on write, so this guards a row
        // stored before that rule rather than a state the app can currently produce.
        assertEquals("AH Volkoren Tarwebrood 800g", product(alias = "   ").displayName)
        assertTrue(product(alias = "   ").hasLocalAlias.not())
    }

    @Test
    fun `hasLocalAlias reports whether the user has named this product`() {
        assertTrue(product(alias = "Breakfast bread").hasLocalAlias)
        assertTrue(product().hasLocalAlias.not())
    }

    // ---- setting and clearing ---------------------------------------------------------------------

    @Test
    fun `setting an alias stores it`() = runTest {
        val local = ColumnAccurateLocal(listOf(product()))
        repositoryOf(local).setLocalAlias(barcode, "Breakfast bread")

        assertEquals("Breakfast bread", storedProduct(local).localAlias)
        assertEquals("Breakfast bread", storedProduct(local).displayName)
    }

    @Test
    fun `the canonical name is never overwritten`() = runTest {
        val local = ColumnAccurateLocal(listOf(product()))
        repositoryOf(local).setLocalAlias(barcode, "Breakfast bread")

        // The whole reason the alias is a second column: the product still knows what it is called.
        assertEquals("AH Volkoren Tarwebrood 800g", storedProduct(local).name)
    }

    @Test
    fun `clearing an alias restores the canonical display name`() = runTest {
        val local = ColumnAccurateLocal(listOf(product(alias = "Breakfast bread")))
        repositoryOf(local).setLocalAlias(barcode, null)

        assertNull(storedProduct(local).localAlias)
        assertEquals("AH Volkoren Tarwebrood 800g", storedProduct(local).displayName)
    }

    @Test
    fun `surrounding whitespace is trimmed`() = runTest {
        val local = ColumnAccurateLocal(listOf(product()))
        repositoryOf(local).setLocalAlias(barcode, "  Breakfast bread  ")

        assertEquals("Breakfast bread", storedProduct(local).localAlias)
    }

    @Test
    fun `a blank alias is stored as null rather than as spaces`() = runTest {
        val local = ColumnAccurateLocal(listOf(product(alias = "Breakfast bread")))
        repositoryOf(local).setLocalAlias(barcode, "   ")

        // Normalised at the one boundary every caller passes through, so "an alias of spaces" is
        // not a state the store, the search or the UI ever has to consider.
        assertNull(storedProduct(local).localAlias)
    }

    @Test
    fun `an over-long alias is capped rather than refused`() = runTest {
        val local = ColumnAccurateLocal(listOf(product()))
        repositoryOf(local).setLocalAlias(barcode, "x".repeat(200))

        assertEquals(Product.MAX_LOCAL_ALIAS_LENGTH, storedProduct(local).localAlias?.length)
    }

    @Test
    fun `setting an alias writes only the alias`() = runTest {
        val local = ColumnAccurateLocal(listOf(product()))
        repositoryOf(local).setLocalAlias(barcode, "Breakfast bread")

        // The narrow write, asserted as such: a rename must never go through the whole-row save
        // path, because that path re-writes every column from a product the caller is holding.
        assertEquals(1, local.aliasWrites)
        assertEquals(0, local.fullSaves)
    }

    @Test
    fun `renaming an unsaved barcode stores nothing`() = runTest {
        val local = ColumnAccurateLocal()
        repositoryOf(local).setLocalAlias("0000000000000", "Breakfast bread")

        // An alias is metadata *about* a saved product, never a reason to create one.
        assertTrue(local.stored.isEmpty())
    }

    // ---- what an alias must not disturb -----------------------------------------------------------

    @Test
    fun `renaming changes nothing else about the product`() = runTest {
        val before = product(
            alias = null,
            origin = ProductDataOrigin.OPEN_FOOD_FACTS,
            status = VerificationStatus.USER_VERIFIED,
            favorite = true,
        ).copy(
            originalRemoteCarbs = BigDecimal("40"),
            originalRemoteBasis = NutritionBasis.PER_100_G,
            latestRemoteCarbs = BigDecimal("42"),
            latestRemoteBasis = NutritionBasis.PER_100_G,
            lastPortion = BigDecimal("65"),
            verifiedAt = now,
        )
        val local = ColumnAccurateLocal(listOf(before))
        repositoryOf(local).setLocalAlias(barcode, "Breakfast bread")

        // Compared as whole objects with the alias put back, so a field added to Product later is
        // covered by this test automatically rather than needing a new assertion nobody writes.
        assertEquals(before, storedProduct(local).copy(localAlias = null))
    }

    @Test
    fun `renaming does not mark a product verified`() = runTest {
        val local = ColumnAccurateLocal(listOf(product(status = VerificationStatus.UNVERIFIED)))
        repositoryOf(local).setLocalAlias(barcode, "Breakfast bread")

        // Naming something is not evidence about its carbohydrate figure. §23's verification means
        // one specific thing — the user checked the number against the package — and a nickname is
        // not that, however deliberate the act of typing it felt.
        assertEquals(VerificationStatus.UNVERIFIED, storedProduct(local).verificationStatus)
        assertNull(storedProduct(local).verifiedAt)
    }

    @Test
    fun `renaming does not change provenance`() = runTest {
        val local = ColumnAccurateLocal(listOf(product(origin = ProductDataOrigin.OPEN_FOOD_FACTS)))
        repositoryOf(local).setLocalAlias(barcode, "Breakfast bread")

        assertEquals(ProductDataOrigin.OPEN_FOOD_FACTS, storedProduct(local).dataSource)
    }

    @Test
    fun `a user-authored product may be renamed without its authored name being replaced`() = runTest {
        val local = ColumnAccurateLocal(
            listOf(
                product(
                    name = "Mum's banana bread",
                    origin = ProductDataOrigin.MANUAL,
                    status = VerificationStatus.USER_VERIFIED,
                ),
            ),
        )
        repositoryOf(local).setLocalAlias(barcode, "Sunday cake")

        // Manual and OCR products are not special-cased into overwriting their own name: the same
        // two-field model applies, so "what I typed" and "what I call it" stay separable.
        assertEquals("Mum's banana bread", storedProduct(local).name)
        assertEquals("Sunday cake", storedProduct(local).displayName)
        assertEquals(ProductDataOrigin.MANUAL, storedProduct(local).dataSource)
    }

    // ---- remote refresh ----------------------------------------------------------------------------

    private fun remoteVersion(carbs: String, name: String = "AH Volkoren Tarwebrood 800g") = Product(
        barcode = barcode,
        name = name,
        carbsPer100 = BigDecimal(carbs),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
        // Straight off the wire: no alias, which is exactly the hazard. A refresh branch that
        // starts from this product and forgets to carry the stored alias forward silently discards
        // the rename.
    )

    @Test
    fun `a refresh of an ordinary remote product preserves the alias`() = runTest {
        val local = ColumnAccurateLocal(
            listOf(product(alias = "Breakfast bread", carbs = "41.5").copy(remoteUpdatedAt = null)),
        )
        val remote = FakeRemote(ProductFetchResult.Found(remoteVersion("43.0")))

        val outcome = repositoryOf(local, remote).refreshFromRemote(barcode)

        // The refreshable branch rebuilds the row from the wire, so this is the branch where the
        // alias would be lost by omission. The figure updates; the personal name does not.
        assertTrue(outcome is RefreshOutcome.RemoteDiffers)
        assertEquals(BigDecimal("43.0"), storedProduct(local).carbsPer100)
        assertEquals("Breakfast bread", storedProduct(local).localAlias)
        assertEquals("Breakfast bread", storedProduct(local).displayName)
    }

    @Test
    fun `a refresh of a verified product preserves the alias`() = runTest {
        val local = ColumnAccurateLocal(
            listOf(
                product(alias = "Breakfast bread", carbs = "41.5", status = VerificationStatus.USER_VERIFIED)
                    .copy(remoteUpdatedAt = null),
            ),
        )
        val remote = FakeRemote(ProductFetchResult.Found(remoteVersion("43.0")))

        repositoryOf(local, remote).refreshFromRemote(barcode)

        // The verified branch preserves the alias by construction (it copies the stored row), but
        // it is asserted anyway: "preserved by construction" is a property of the *shape* of that
        // expression, and the next edit to it could remove the property without removing a test.
        assertEquals("Breakfast bread", storedProduct(local).localAlias)
        assertEquals(BigDecimal("41.5"), storedProduct(local).carbsPer100)
    }

    @Test
    fun `a refresh that renames the product upstream leaves the user's own name in force`() = runTest {
        val local = ColumnAccurateLocal(
            listOf(product(alias = "Breakfast bread").copy(remoteUpdatedAt = null)),
        )
        val remote = FakeRemote(
            ProductFetchResult.Found(remoteVersion("41.5", name = "AH Volkoren Tarwebrood 800 g")),
        )

        repositoryOf(local, remote).refreshFromRemote(barcode)

        // The provider corrected the canonical name — that is its to correct, and it is taken. What
        // the user sees does not move, because what they see is their own name.
        assertEquals("AH Volkoren Tarwebrood 800 g", storedProduct(local).name)
        assertEquals("Breakfast bread", storedProduct(local).displayName)
    }

    // ---- verification and reset ----------------------------------------------------------------

    @Test
    fun `verifying against the package preserves the alias`() = runTest {
        val local = ColumnAccurateLocal(listOf(product(alias = "Breakfast bread")))

        repositoryOf(local).saveVerification(
            barcode = barcode,
            verifiedCarbsPer100 = BigDecimal("44"),
            basis = NutritionBasis.PER_100_G,
        )

        assertEquals("Breakfast bread", storedProduct(local).localAlias)
        assertEquals(VerificationStatus.USER_VERIFIED, storedProduct(local).verificationStatus)
    }

    @Test
    fun `resetting to the online value preserves the alias`() = runTest {
        val local = ColumnAccurateLocal(
            listOf(
                product(alias = "Breakfast bread", carbs = "44").copy(
                    originalRemoteCarbs = BigDecimal("41.5"),
                    originalRemoteBasis = NutritionBasis.PER_100_G,
                    verificationStatus = VerificationStatus.USER_VERIFIED,
                ),
            ),
        )

        repositoryOf(local).resetToOnlineValue(barcode)

        // Resetting the *figure* says nothing about the *name*. Two orthogonal facts, and the one
        // the user did not ask to undo stays where it was.
        assertEquals(BigDecimal("41.5"), storedProduct(local).carbsPer100)
        assertEquals("Breakfast bread", storedProduct(local).localAlias)
    }

    @Test
    fun `toggling a favourite preserves the alias`() = runTest {
        val local = ColumnAccurateLocal(listOf(product(alias = "Breakfast bread")))
        repositoryOf(local).setFavorite(barcode, true)

        assertEquals("Breakfast bread", storedProduct(local).localAlias)
        assertTrue(storedProduct(local).favorite)
    }

    @Test
    fun `renaming preserves a favourite set while the screen was open`() = runTest {
        // The lost-update case the narrow write exists for, stated as a scenario rather than as a
        // call count. A screen holds a snapshot taken before the star was tapped; renaming from
        // that stale snapshot with a whole-row save would put `favorite = false` back.
        val local = ColumnAccurateLocal(listOf(product(favorite = false)))
        val repository = repositoryOf(local)

        val staleSnapshot = storedProduct(local)
        repository.setFavorite(barcode, true)
        assertTrue(storedProduct(local).favorite)

        repository.setLocalAlias(barcode, "Breakfast bread")

        assertTrue("the favourite set after the snapshot must survive the rename", storedProduct(local).favorite)
        assertTrue(staleSnapshot.favorite.not())
        assertSame(true, storedProduct(local).favorite)
    }
}

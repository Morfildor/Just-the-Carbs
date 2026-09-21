package app.justthecarbs.data

import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.LocalProductDataSource
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.MealStore
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Independently owned product columns survive a concurrent write to a different one (1.0.8).
 *
 * ## The race, and why a fake could hide it
 *
 * Several repository operations had the shape:
 *
 * ```
 *   read the product  ->  suspend (another store, the clock, the network)  ->  copy(...)  ->  save()
 * ```
 *
 * `save()` writes **every column**, from a snapshot taken before the suspension. So any write that
 * landed during that window was silently rolled back by the second half of the first operation —
 * not overwritten by a newer value, but reverted to a value that was already stale when it was read.
 *
 * The concrete sequence, which [renaming during a recorded use is not rolled back] reproduces:
 *
 * ```
 *   recordUse   reads product(localAlias = null)
 *   rename      writes localAlias = "Breakfast bread"     <- lands here
 *   recordUse   writes its whole-row snapshot             <- alias is null again
 * ```
 *
 * It needs no unusual timing. `recordUse` genuinely suspends between its read and its write, and a
 * rename is performed on a screen that has been open — and therefore holding a snapshot — for as
 * long as the user took to decide on a name.
 *
 * ## The fake is a scheduler, not a database
 *
 * [InterleavingLocal] lets a test say *"let the rename land in the middle of the recorded use"* and
 * have that happen deterministically, rather than hoping two coroutines collide. Its `save` writes
 * whole rows and its narrow updates write single columns, exactly as `ProductDao` does — a fake
 * that implemented the narrow updates as whole-row copies would make every test here pass while the
 * defect sat in production, which is the trap `LocalAliasTest` already records.
 */
class ProductWriteRaceTest {

    private val now: Instant = Instant.parse("2026-09-21T10:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val barcode = "8712100849060"

    private fun product(
        alias: String? = null,
        favorite: Boolean = false,
        lastPortion: String? = null,
        lastUsedAt: Instant? = null,
        status: VerificationStatus = VerificationStatus.UNVERIFIED,
        carbs: String = "41.5",
    ) = Product(
        barcode = barcode,
        name = "AH Volkoren Tarwebrood 800g",
        carbsPer100 = BigDecimal(carbs),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = status,
        favorite = favorite,
        localAlias = alias,
        lastPortion = lastPortion?.let(::BigDecimal),
        lastUsedAt = lastUsedAt,
    )

    /**
     * A column-accurate store that can be made to suspend at a chosen point.
     *
     * [pauseBeforeSave] is awaited inside `save`, after the caller has read and copied. Completing
     * it from the test is what lets a second operation land *between* a read and its write, which
     * is the whole window the defect lived in.
     */
    private class InterleavingLocal(seed: Product) : LocalProductDataSource {

        /**
         * The device-owned columns are read **after** the pause, exactly as the real
         * `@Transaction saveProductFacts` reads them inside the transaction.
         *
         * The ordering here is the whole point of the fake. Reading them before awaiting the gate
         * would model a Kotlin re-read just *before* the write — which narrows the lost-update
         * window without closing it, because the read and the write would still be two statements
         * with a gap between them. A test built that way would pass against an implementation that
         * still loses the update under real concurrency.
         */
        override suspend fun saveProductFacts(product: Product) {
            pauseBeforeSave?.await()
            fullSaves++
            val current = stored[product.barcode]
            stored[product.barcode] = if (current == null) {
                product
            } else {
                product.copy(localAlias = current.localAlias, favorite = current.favorite)
            }
        }
        val stored = mutableMapOf(seed.barcode to seed)
        var pauseBeforeSave: CompletableDeferred<Unit>? = null
        var fullSaves = 0
            private set
        var aliasWrites = 0
            private set
        var favoriteWrites = 0
            private set
        var usageWrites = 0
            private set

        override suspend fun fetch(barcode: String): ProductFetchResult =
            stored[barcode]?.let { ProductFetchResult.Found(it) } ?: ProductFetchResult.NotFound

        override suspend fun save(product: Product) {
            pauseBeforeSave?.await()
            fullSaves++
            stored[product.barcode] = product
        }

        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(stored.values.toList())

        // One column, as the real `UPDATE products SET localAlias = ...` does.
        override suspend fun setLocalAlias(barcode: String, alias: String?) {
            aliasWrites++
            stored[barcode] = stored[barcode]?.copy(localAlias = alias) ?: return
        }

        // One column, as the real `UPDATE products SET favorite = ...` does.
        override suspend fun setFavorite(barcode: String, favorite: Boolean) {
            favoriteWrites++
            stored[barcode] = stored[barcode]?.copy(favorite = favorite) ?: return
        }

        // The coherent usage group, written together as one statement does.
        override suspend fun recordUsageColumns(
            barcode: String,
            lastPortion: BigDecimal?,
            lastUsedAt: Instant,
            lastInputMode: InputMode?,
            lastSelectedPortionUnitId: Long?,
            lastCount: BigDecimal?,
        ) {
            usageWrites++
            val existing = stored[barcode] ?: return
            stored[barcode] = existing.copy(
                lastPortion = lastPortion ?: existing.lastPortion,
                lastUsedAt = lastUsedAt,
                lastInputMode = lastInputMode ?: existing.lastInputMode,
                lastSelectedPortionUnitId = if (lastInputMode == InputMode.GRAMS) {
                    null
                } else {
                    lastSelectedPortionUnitId ?: existing.lastSelectedPortionUnitId
                },
                lastCount = if (lastInputMode == InputMode.GRAMS) null else lastCount ?: existing.lastCount,
            )
        }

        override suspend fun forgetRecentUse(barcode: String): RecentUseSnapshot? =
            error("this fake does not implement forgetRecentUse")

        override suspend fun restoreRecentUse(snapshot: RecentUseSnapshot) =
            error("this fake does not implement restoreRecentUse")
    }

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

    private class RecordingUsage : PortionUsageStore {
        override suspend fun findByBarcode(barcode: String): List<PortionUsage> = emptyList()
        override suspend fun findVariant(
            barcode: String,
            inputMode: InputMode,
            portionUnitId: Long?,
            amount: BigDecimal,
        ): PortionUsage? = null
        override suspend fun save(usage: PortionUsage): PortionUsage = usage
        override suspend fun delete(usage: PortionUsage) = Unit
    }

    private fun repositoryOf(local: LocalProductDataSource) = ProductRepository(
        local = local,
        remote = object : ProductDataSource {
            override suspend fun fetch(barcode: String): ProductFetchResult = ProductFetchResult.NotFound
        },
        portionUnits = NoPortionUnits(),
        meal = NoMeal(),
        portionUsage = RecordingUsage(),
        searchSource = object : ProductSearchSource {
            override suspend fun search(terms: String) = ProductSearchResult.NoMatches
        },
        clock = clock,
    )

    // ---- rename against the other writers --------------------------------------------------------

    @Test
    fun `renaming during a recorded use is not rolled back`() = runTest {
        // THE ORIGINAL RACE. `recordUse` reads a product with no alias; the rename lands while it is
        // suspended; `recordUse` then writes. Before the hardening its whole-row save reverted the
        // alias to the null it had read.
        val local = InterleavingLocal(product())
        val repository = repositoryOf(local)

        val gate = CompletableDeferred<Unit>()
        local.pauseBeforeSave = gate

        val use = launch { repository.recordUse(barcode, BigDecimal("65"), mode = InputMode.GRAMS) }
        // `recordUse` has read and is parked before its write.
        kotlinx.coroutines.yield()

        repository.setLocalAlias(barcode, "Breakfast bread")
        gate.complete(Unit)
        use.join()

        assertEquals(
            "the alias written during the use must survive it",
            "Breakfast bread",
            local.stored.getValue(barcode).localAlias,
        )
        assertEquals(
            "and the use must still have been recorded",
            BigDecimal("65"),
            local.stored.getValue(barcode).lastPortion,
        )
    }

    @Test
    fun `renaming during a favourite toggle is not rolled back`() = runTest {
        val local = InterleavingLocal(product())
        val repository = repositoryOf(local)

        val gate = CompletableDeferred<Unit>()
        local.pauseBeforeSave = gate

        val star = launch { repository.setFavorite(barcode, true) }
        kotlinx.coroutines.yield()

        repository.setLocalAlias(barcode, "Breakfast bread")
        gate.complete(Unit)
        star.join()

        val stored = local.stored.getValue(barcode)
        assertEquals("Breakfast bread", stored.localAlias)
        assertTrue("and the star must have landed", stored.favorite)
    }

    @Test
    fun `a favourite toggled during a recorded use is not rolled back`() {
        runTest {
            val local = InterleavingLocal(product())
            val repository = repositoryOf(local)

            val gate = CompletableDeferred<Unit>()
            local.pauseBeforeSave = gate

            val use = launch { repository.recordUse(barcode, BigDecimal("65"), mode = InputMode.GRAMS) }
            kotlinx.coroutines.yield()

            repository.setFavorite(barcode, true)
            gate.complete(Unit)
            use.join()

            val stored = local.stored.getValue(barcode)
            assertTrue("the star must survive the recorded use", stored.favorite)
            assertEquals(BigDecimal("65"), stored.lastPortion)
        }
    }

    @Test
    fun `a recorded use during a rename is not rolled back`() = runTest {
        // The same pair in the other order. A narrow alias write reads nothing, so there is no
        // snapshot for a concurrent use to be reverted from — this is the direction that was
        // already safe, asserted so it stays that way.
        val local = InterleavingLocal(product())
        val repository = repositoryOf(local)

        repository.setLocalAlias(barcode, "Breakfast bread")
        repository.recordUse(barcode, BigDecimal("65"), mode = InputMode.GRAMS)

        val stored = local.stored.getValue(barcode)
        assertEquals("Breakfast bread", stored.localAlias)
        assertEquals(BigDecimal("65"), stored.lastPortion)
    }

    // ---- the narrow writes read nothing ----------------------------------------------------------

    @Test
    fun `setFavorite writes one column and takes no whole-row snapshot`() {
        runTest {
            val local = InterleavingLocal(product(alias = "Breakfast bread"))
            repositoryOf(local).setFavorite(barcode, true)

            assertEquals("the star must be a narrow write", 1, local.favoriteWrites)
            assertEquals("and must not write the whole row", 0, local.fullSaves)
            assertEquals("Breakfast bread", local.stored.getValue(barcode).localAlias)
        }
    }

    @Test
    fun `recordUse writes its usage columns together and no others`() {
        runTest {
            val local = InterleavingLocal(product(alias = "Breakfast bread", favorite = true))
            repositoryOf(local).recordUse(barcode, BigDecimal("65"), mode = InputMode.GRAMS)

            assertEquals("usage is one coherent write", 1, local.usageWrites)
            assertEquals("and not a whole-row save", 0, local.fullSaves)
            val stored = local.stored.getValue(barcode)
            assertEquals("Breakfast bread", stored.localAlias)
            assertTrue(stored.favorite)
        }
    }

    @Test
    fun `an unknown barcode is not created by a narrow write`() {
        runTest {
            // Metadata is *about* a saved product and is never a reason to create one. The alias
            // write already held this rule; the others must too.
            val local = InterleavingLocal(product())
            val repository = repositoryOf(local)

            runCatching { repository.setFavorite("0000000000000", true) }
            runCatching { repository.recordUse("0000000000000", BigDecimal("65"), mode = InputMode.GRAMS) }

            assertNull(local.stored["0000000000000"])
        }
    }

    // ---- what the usage write must still do -------------------------------------------------------

    @Test
    fun `a grams use clears the remembered count and unit`() {
        runTest {
            val local = InterleavingLocal(
                product().copy(
                    lastInputMode = InputMode.PORTION_UNIT,
                    lastSelectedPortionUnitId = 7L,
                    lastCount = BigDecimal("2"),
                ),
            )
            repositoryOf(local).recordUse(barcode, BigDecimal("65"), mode = InputMode.GRAMS)

            val stored = local.stored.getValue(barcode)
            assertNull("grams mode forgets the unit", stored.lastSelectedPortionUnitId)
            assertNull("and the count", stored.lastCount)
            assertEquals(InputMode.GRAMS, stored.lastInputMode)
        }
    }

    @Test
    fun `a direct-carb use preserves the previously remembered weight`() {
        runTest {
            // `lastPortion` is strictly a resolved mass in the product's own basis unit. A direct
            // carb portion resolves no weight, so null must leave the previous value alone rather
            // than erase it — the rule `recordUse` already held, restated against the narrow write.
            val local = InterleavingLocal(product(lastPortion = "65"))
            repositoryOf(local).recordUse(
                barcode,
                portion = null,
                mode = InputMode.PORTION_UNIT,
                portionUnitId = 7L,
                count = BigDecimal("4"),
            )

            val stored = local.stored.getValue(barcode)
            assertEquals(BigDecimal("65"), stored.lastPortion)
            assertEquals(BigDecimal("4"), stored.lastCount)
        }
    }

    @Test
    fun `a use whose basis no longer matches is still refused`() {
        runTest {
            // The calculation-session guard (§8): a use recorded against a basis the product no
            // longer has is dropped entirely rather than written under the new one.
            val local = InterleavingLocal(product())
            repositoryOf(local).recordUse(
                barcode,
                BigDecimal("65"),
                mode = InputMode.GRAMS,
                expectedBasis = NutritionBasis.PER_100_ML,
            )

            assertEquals("nothing may be written at all", 0, local.usageWrites)
            assertNull(local.stored.getValue(barcode).lastPortion)
        }
    }

    @Test
    fun `recording a use marks the product used`() {
        runTest {
            val local = InterleavingLocal(product())
            repositoryOf(local).recordUse(barcode, BigDecimal("65"), mode = InputMode.GRAMS)

            assertEquals(now, local.stored.getValue(barcode).lastUsedAt)
        }
    }

    // ---- the broader, coherent writes -------------------------------------------------------------

    @Test
    fun `verification during a rename keeps the name the user chose`() {
        runTest {
            // Verification legitimately replaces a coherent set of product facts, so it *is* a
            // whole-row write. What it must not do is carry an independently owned column backwards
            // with it. This is the direction that matters: the rename lands while verification is
            // suspended (its basis change is deleting usage rows), and the alias must survive.
            val local = InterleavingLocal(product())
            val repository = repositoryOf(local)

            val gate = CompletableDeferred<Unit>()
            local.pauseBeforeSave = gate

            val verify = launch {
                repository.saveVerification(
                    barcode = barcode,
                    verifiedCarbsPer100 = BigDecimal("44"),
                    basis = NutritionBasis.PER_100_G,
                )
            }
            kotlinx.coroutines.yield()

            repository.setLocalAlias(barcode, "Breakfast bread")
            gate.complete(Unit)
            verify.join()

            val stored = local.stored.getValue(barcode)
            assertEquals("the rename must survive a concurrent verification", "Breakfast bread", stored.localAlias)
            assertEquals("and the verified figure must land", 0, BigDecimal("44").compareTo(stored.carbsPer100))
            assertEquals(VerificationStatus.USER_VERIFIED, stored.verificationStatus)
        }
    }

    @Test
    fun `resetting to the online value keeps a rename and a favourite`() {
        runTest {
            val local = InterleavingLocal(
                product(alias = "Breakfast bread", favorite = true, carbs = "44", status = VerificationStatus.USER_VERIFIED)
                    .copy(originalRemoteCarbs = BigDecimal("41.5"), originalRemoteBasis = NutritionBasis.PER_100_G),
            )
            repositoryOf(local).resetToOnlineValue(barcode)

            val stored = local.stored.getValue(barcode)
            assertEquals("resetting a figure says nothing about the name", "Breakfast bread", stored.localAlias)
            assertTrue("nor about the star", stored.favorite)
            assertEquals(0, BigDecimal("41.5").compareTo(stored.carbsPer100))
            assertEquals(VerificationStatus.UNVERIFIED, stored.verificationStatus)
        }
    }

    @Test
    fun `accepting a newer online value keeps a rename and a favourite`() {
        runTest {
            val local = InterleavingLocal(
                product(alias = "Breakfast bread", favorite = true)
                    .copy(latestRemoteCarbs = BigDecimal("44"), latestRemoteBasis = NutritionBasis.PER_100_G),
            )
            repositoryOf(local).applyLatestRemoteValue(barcode)

            val stored = local.stored.getValue(barcode)
            assertEquals("Breakfast bread", stored.localAlias)
            assertTrue(stored.favorite)
            assertEquals(0, BigDecimal("44").compareTo(stored.carbsPer100))
        }
    }

    @Test
    fun `a basis change still clears the usage that no longer applies`() {
        runTest {
            // The hardening must not weaken this: a remembered portion in grams is meaningless once
            // the product is measured per 100 ml, so it goes — while the name and the star, which
            // say nothing about the basis, stay.
            val local = InterleavingLocal(
                product(alias = "Breakfast bread", favorite = true, lastPortion = "65")
                    .copy(lastInputMode = InputMode.GRAMS),
            )
            repositoryOf(local).saveVerification(
                barcode = barcode,
                verifiedCarbsPer100 = BigDecimal("9.4"),
                basis = NutritionBasis.PER_100_ML,
            )

            val stored = local.stored.getValue(barcode)
            assertNull("a portion in the old basis must not survive", stored.lastPortion)
            assertNull(stored.lastInputMode)
            assertEquals("but the name is not about the basis", "Breakfast bread", stored.localAlias)
            assertTrue("nor is the star", stored.favorite)
        }
    }

    @Test
    fun `clearing a favourite is equally narrow`() {
        runTest {
            val local = InterleavingLocal(product(alias = "Breakfast bread", favorite = true))
            repositoryOf(local).setFavorite(barcode, false)

            val stored = local.stored.getValue(barcode)
            assertFalse(stored.favorite)
            assertEquals("Breakfast bread", stored.localAlias)
            assertEquals(0, local.fullSaves)
        }
    }
}

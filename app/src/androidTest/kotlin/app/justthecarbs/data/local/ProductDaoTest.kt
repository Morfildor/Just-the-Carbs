package app.justthecarbs.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.PortionUnitKind
import app.justthecarbs.domain.VerificationStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

/**
 * Covers the parts of §59 "product history" that live in SQL rather than in Kotlin: ordering,
 * the favourites-float-to-top rule, and the exactness of the TEXT decimal round-trip.
 *
 * NOTE: this is an **instrumented** test. It needs a device or emulator, and none is available in
 * the current development environment, so it has been compiled but never executed. Treat it as
 * unverified until it has been run on hardware.
 */
@RunWith(AndroidJUnit4::class)
class ProductDaoTest {

    private lateinit var database: JustTheCarbsDatabase
    private lateinit var dao: ProductDao

    private val epoch = Instant.parse("2026-08-13T10:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            JustTheCarbsDatabase::class.java,
        ).build()
        dao = database.productDao()
    }

    @After
    fun tearDown() = database.close()

    private fun product(
        barcode: String,
        carbs: String = "48.2",
        usedSecondsAfterEpoch: Long? = null,
        favorite: Boolean = false,
        origin: ProductDataOrigin = ProductDataOrigin.OPEN_FOOD_FACTS,
        verification: VerificationStatus = VerificationStatus.UNVERIFIED,
    ) = Product(
        barcode = barcode,
        name = "Product $barcode",
        carbsPer100 = BigDecimal(carbs),
        basis = NutritionBasis.PER_100_G,
        dataSource = origin,
        verificationStatus = verification,
        lastUsedAt = usedSecondsAfterEpoch?.let(epoch::plusSeconds),
        lastPortion = usedSecondsAfterEpoch?.let { BigDecimal("65") },
        favorite = favorite,
    )

    @Test
    fun recentsAreOrderedByMostRecentlyUsed() = runTest {
        dao.upsert(product("111", usedSecondsAfterEpoch = 10).toEntity())
        dao.upsert(product("222", usedSecondsAfterEpoch = 30).toEntity())
        dao.upsert(product("333", usedSecondsAfterEpoch = 20).toEntity())

        val recents = dao.observeRecents(limit = 10).first()

        assertEquals(listOf("222", "333", "111"), recents.map { it.barcode })
    }

    @Test
    fun favouritesFloatAboveMoreRecentlyUsedProducts() = runTest {
        dao.upsert(product("111", usedSecondsAfterEpoch = 10, favorite = true).toEntity())
        dao.upsert(product("222", usedSecondsAfterEpoch = 99).toEntity())

        val recents = dao.observeRecents(limit = 10).first()

        assertEquals(listOf("111", "222"), recents.map { it.barcode })
    }

    @Test
    fun aStarredProductAppearsBeforeItHasEverBeenUsed() = runTest {
        dao.upsert(product("111", favorite = true).toEntity())

        assertEquals(1, dao.observeRecents(limit = 10).first().size)
    }

    @Test
    fun anUnusedUnstarredProductIsNotARecent() = runTest {
        dao.upsert(product("111").toEntity())

        assertEquals(0, dao.observeRecents(limit = 10).first().size)
    }

    @Test
    fun recentsRespectTheLimit() = runTest {
        repeat(5) { dao.upsert(product("bc$it", usedSecondsAfterEpoch = it.toLong()).toEntity()) }

        assertEquals(3, dao.observeRecents(limit = 3).first().size)
    }

    /** The reason carbohydrate columns are TEXT: a REAL column would not survive this. */
    @Test
    fun carbohydrateValuesRoundTripExactly() = runTest {
        dao.upsert(product("111", carbs = "48.2").toEntity())

        val stored = dao.findByBarcode("111")!!.toDomain()

        assertEquals(BigDecimal("48.2"), stored.carbsPer100)
    }

    @Test
    fun verificationTimestampRoundTrips() = runTest {
        val verified = product("111", verification = VerificationStatus.USER_VERIFIED)
            .copy(verifiedAt = epoch, originalRemoteCarbs = BigDecimal("50.0"))
        dao.upsert(verified.toEntity())

        val stored = dao.findByBarcode("111")!!.toDomain()

        assertEquals(epoch, stored.verifiedAt)
        assertEquals(VerificationStatus.USER_VERIFIED, stored.verificationStatus)
        assertEquals(ProductDataOrigin.OPEN_FOOD_FACTS, stored.dataSource)
        assertEquals(BigDecimal("50.0"), stored.originalRemoteCarbs)
    }

    @Test
    fun upsertReplacesTheExistingRowRatherThanAddingOne() = runTest {
        dao.upsert(product("111", carbs = "48.2", usedSecondsAfterEpoch = 10).toEntity())
        dao.upsert(product("111", carbs = "50.1", usedSecondsAfterEpoch = 20).toEntity())

        assertEquals(1, dao.observeRecents(limit = 10).first().size)
        assertEquals(BigDecimal("50.1"), dao.findByBarcode("111")!!.toDomain().carbsPer100)
    }

    /** §43 clearing history must not destroy what §23 calls the reliability feature. */
    @Test
    fun clearingRecentHistoryKeepsVerifiedProductsAndFavourites() = runTest {
        dao.upsert(
            product("111", usedSecondsAfterEpoch = 10, verification = VerificationStatus.USER_VERIFIED)
                .copy(verifiedAt = epoch)
                .toEntity(),
        )
        dao.upsert(product("222", usedSecondsAfterEpoch = 20, favorite = true).toEntity())

        dao.clearRecentHistory()

        val verified = dao.findByBarcode("111")!!.toDomain()
        assertNotNull("the verified product itself must survive", verified)
        assertEquals(VerificationStatus.USER_VERIFIED, verified.verificationStatus)
        assertNull("but it no longer shows as recent", verified.lastUsedAt)

        val favourite = dao.findByBarcode("222")!!.toDomain()
        assertNotNull("the favourite product itself must survive", favourite)
        assertEquals("and stays a favourite", true, favourite.favorite)
        assertEquals("with its carbohydrate value intact", BigDecimal("48.2"), favourite.carbsPer100)
    }

    /**
     * The half of the promise the old implementation did not keep.
     *
     * `clearRecentHistory` carried `WHERE favorite = 0`, so a favourite kept its entire usage
     * history through an action whose label says nothing about exempting rows. Starring a product
     * says "keep this near the top of Home"; it is not consent to retain a record of eating it.
     */
    @Test
    fun clearingRecentHistoryClearsFavouritesUsageToo() = runTest {
        dao.upsert(product("222", usedSecondsAfterEpoch = 20, favorite = true).toEntity())

        dao.clearRecentHistory()

        val favourite = dao.findByBarcode("222")!!.toDomain()
        assertNull("a favourite's last-used time is history too", favourite.lastUsedAt)
        assertNull("and so is its last portion", favourite.lastPortion)
    }

    /**
     * The other half: three columns that *are* a remembered portion were never cleared at all.
     *
     * `lastInputMode` + `lastSelectedPortionUnitId` + `lastCount` is exactly "2 slices". Leaving them
     * meant a cleared product still pre-filled the count the user last ate, which is the thing the
     * action claims to forget — asserted per column, because clearing two of the five and calling it
     * done is precisely the failure being fixed.
     */
    @Test
    fun clearingRecentHistoryClearsEveryRememberedPortionColumn() = runTest {
        dao.upsert(
            product("111", usedSecondsAfterEpoch = 10)
                .copy(
                    lastInputMode = InputMode.PORTION_UNIT,
                    lastSelectedPortionUnitId = 7L,
                    lastCount = BigDecimal("2"),
                )
                .toEntity(),
        )

        dao.clearRecentHistory()

        val cleared = dao.findByBarcode("111")!!
        assertNull("lastUsedAt", cleared.lastUsedAt)
        assertNull("lastPortion", cleared.lastPortion)
        assertNull("lastInputMode", cleared.lastInputMode)
        assertNull("lastSelectedPortionUnitId", cleared.lastSelectedPortionUnitId)
        assertNull("lastCount", cleared.lastCount)
    }

    /**
     * A previously used portion must not come back after the user clears history.
     *
     * `portion_usage` is what *Usual* offers as one-tap shortcuts, and clearing recents left it
     * completely untouched — so the user was told their history was gone and the next visit to the
     * same product still offered the portions they had built up. The product survives, as it must;
     * the record of using it does not.
     */
    @Test
    fun aUsedPortionCannotReappearAfterClearingHistory() = runTest {
        val usage = database.portionUsageDao()
        dao.upsert(product("111", usedSecondsAfterEpoch = 10).toEntity())
        usage.upsert(
            PortionUsageEntity(
                productBarcode = "111",
                inputMode = InputMode.GRAMS.name,
                portionUnitId = PortionUsageEntity.NO_UNIT_SENTINEL,
                amount = "65",
                usageCount = 4,
                lastUsedAt = epoch.toEpochMilli(),
            ),
        )
        assertEquals(1, usage.findByBarcode("111").size)

        dao.clearRecentHistory()

        assertEquals("no usual portion survives", 0, usage.findByBarcode("111").size)
        assertNotNull("but the product does", dao.findByBarcode("111"))
    }

    @Test
    fun deletingAllProductsEmptiesTheStore() = runTest {
        dao.upsert(product("111", usedSecondsAfterEpoch = 10).toEntity())

        dao.deleteAllProducts()

        assertNull(dao.findByBarcode("111"))
    }

    /**
     * Re-scanning a cleared barcode must not resurrect the deleted product's history.
     *
     * `portion_usage` carries a bare `productBarcode` with **no foreign key** — deliberately, so
     * usage is not coupled to the product row's lifetime — which meant `DELETE FROM products` left
     * every usage aggregate orphaned in place. Scanning the same barcode again recreated the product
     * row, the orphans matched it by string, and portions the user had deleted reappeared as
     * shortcuts on a product they had never used.
     *
     * The full round trip is exercised rather than just the delete, because the resurrection is the
     * observable defect and a `SELECT` immediately after the delete would not show it.
     */
    @Test
    fun rescanningAClearedBarcodeResurrectsNoUsageHistory() = runTest {
        val usage = database.portionUsageDao()
        val units = database.portionUnitDao()

        dao.upsert(product("111", usedSecondsAfterEpoch = 10).toEntity())
        val unitId = units.upsert(
            PortionUnitEntity(
                productBarcode = "111",
                kind = PortionUnitKind.SLICE.name,
                customLabel = null,
                conversionKind = "WEIGHT",
                conversionValue = "36",
                conversionBasis = NutritionBasis.PER_100_G.name,
                dataSource = ProductDataOrigin.MANUAL.name,
                verificationStatus = VerificationStatus.USER_VERIFIED.name,
                verifiedAt = epoch.toEpochMilli(),
                originalRemoteConversionKind = null,
                originalRemoteConversionValue = null,
                originalRemoteConversionBasis = null,
                latestRemoteConversionKind = null,
                latestRemoteConversionValue = null,
                latestRemoteConversionBasis = null,
                rawRemoteServingText = null,
                createdAt = epoch.toEpochMilli(),
                updatedAt = epoch.toEpochMilli(),
            ),
        )
        usage.upsert(
            PortionUsageEntity(
                productBarcode = "111",
                inputMode = InputMode.PORTION_UNIT.name,
                portionUnitId = unitId,
                amount = "2",
                usageCount = 6,
                lastUsedAt = epoch.toEpochMilli(),
            ),
        )
        // Precondition, so a later refactor that stops writing usage cannot make this pass vacuously.
        assertEquals(1, usage.findByBarcode("111").size)
        assertEquals(1, units.findByBarcode("111").size)

        dao.deleteAllProducts()

        // Step 4: the same barcode is scanned again and saved from a fresh lookup.
        dao.upsert(product("111").toEntity())

        assertEquals("no usual portion resurfaces", 0, usage.findByBarcode("111").size)
        assertEquals("no portion unit resurfaces", 0, units.findByBarcode("111").size)
        val rescanned = dao.findByBarcode("111")!!.toDomain()
        assertNull("and the product remembers no portion", rescanned.lastPortion)
        assertNull("nor a count", rescanned.lastCount)
    }

    // ---- Remove from Recent, one product -------------------------------------------------------

    /** A used product with two usual portions, which is the state the removal has to erase. */
    private suspend fun seedUsedProduct(barcode: String, favorite: Boolean = false) {
        dao.upsert(
            product(barcode, usedSecondsAfterEpoch = 10, favorite = favorite)
                .copy(
                    lastInputMode = InputMode.PORTION_UNIT,
                    lastSelectedPortionUnitId = 7L,
                    lastCount = BigDecimal("2"),
                )
                .toEntity(),
        )
        val usage = database.portionUsageDao()
        usage.upsert(
            PortionUsageEntity(
                productBarcode = barcode, inputMode = InputMode.PORTION_UNIT.name,
                portionUnitId = 7L, amount = "2", usageCount = 6, lastUsedAt = epoch.toEpochMilli(),
            ),
        )
        usage.upsert(
            PortionUsageEntity(
                productBarcode = barcode, inputMode = InputMode.GRAMS.name,
                portionUnitId = PortionUsageEntity.NO_UNIT_SENTINEL, amount = "65",
                usageCount = 3, lastUsedAt = epoch.toEpochMilli(),
            ),
        )
    }

    /**
     * The whole point of a *per-product* removal: the product beside it is untouched.
     *
     * Asserted first because it is the one failure mode that would make this feature worse than the
     * global action it is scoped from — a user clearing one product and silently losing another's
     * history would have no way to notice until the shortcuts were gone.
     */
    @Test
    fun forgettingOneProductLeavesEveryOtherProductsUsageAlone() = runTest {
        val usage = database.portionUsageDao()
        seedUsedProduct("111")
        seedUsedProduct("222")

        dao.forgetRecentUse("111")

        val untouched = dao.findByBarcode("222")!!
        assertNotNull("the other product's last-used time survives", untouched.lastUsedAt)
        assertEquals("and its remembered count", "2", untouched.lastCount)
        assertEquals("and both its usual portions", 2, usage.findByBarcode("222").size)
    }

    /**
     * Per column, for the same reason the global action's own test is: clearing three of the five
     * and calling it done is exactly the half-promise that shipped once already.
     */
    @Test
    fun forgettingOneProductClearsEveryRememberedPortionColumn() = runTest {
        seedUsedProduct("111")

        dao.forgetRecentUse("111")

        val cleared = dao.findByBarcode("111")!!
        assertNull("lastUsedAt", cleared.lastUsedAt)
        assertNull("lastPortion", cleared.lastPortion)
        assertNull("lastInputMode", cleared.lastInputMode)
        assertNull("lastSelectedPortionUnitId", cleared.lastSelectedPortionUnitId)
        assertNull("lastCount", cleared.lastCount)
    }

    @Test
    fun forgettingOneProductDeletesItsUsualPortions() = runTest {
        val usage = database.portionUsageDao()
        seedUsedProduct("111")
        assertEquals(2, usage.findByBarcode("111").size)

        dao.forgetRecentUse("111")

        assertEquals("no usual portion survives", 0, usage.findByBarcode("111").size)
    }

    /**
     * What the action must *not* destroy. A verified carbohydrate value is §23's reliability
     * feature, and a removal labelled "Remove from Recent" that quietly discarded it would be the
     * worst possible reading of an ambiguous label.
     */
    @Test
    fun forgettingOneProductKeepsEveryProductFactAndItsPortionUnits() = runTest {
        val units = database.portionUnitDao()
        dao.upsert(
            product("111", carbs = "48.2", usedSecondsAfterEpoch = 10,
                origin = ProductDataOrigin.OPEN_FOOD_FACTS,
                verification = VerificationStatus.USER_VERIFIED)
                .copy(verifiedAt = epoch)
                .toEntity(),
        )
        units.upsert(
            PortionUnitEntity(
                productBarcode = "111", kind = PortionUnitKind.SLICE.name, customLabel = null,
                conversionKind = "WEIGHT", conversionValue = "36",
                conversionBasis = NutritionBasis.PER_100_G.name,
                dataSource = ProductDataOrigin.MANUAL.name,
                verificationStatus = VerificationStatus.USER_VERIFIED.name,
                verifiedAt = epoch.toEpochMilli(),
                originalRemoteConversionKind = null, originalRemoteConversionValue = null,
                originalRemoteConversionBasis = null, latestRemoteConversionKind = null,
                latestRemoteConversionValue = null, latestRemoteConversionBasis = null,
                rawRemoteServingText = null,
                createdAt = epoch.toEpochMilli(), updatedAt = epoch.toEpochMilli(),
            ),
        )

        dao.forgetRecentUse("111")

        val kept = dao.findByBarcode("111")!!.toDomain()
        assertEquals("name", "Product 111", kept.name)
        assertEquals("carbohydrate value", BigDecimal("48.2"), kept.carbsPer100)
        assertEquals("basis", NutritionBasis.PER_100_G, kept.basis)
        assertEquals("provenance", ProductDataOrigin.OPEN_FOOD_FACTS, kept.dataSource)
        assertEquals("verification", VerificationStatus.USER_VERIFIED, kept.verificationStatus)
        assertEquals("verified-at", epoch, kept.verifiedAt)
        assertEquals("its portion-unit definition", 1, units.findByBarcode("111").size)
    }

    @Test
    fun forgettingAFavouriteKeepsItStarredAndStillVisible() = runTest {
        seedUsedProduct("111", favorite = true)

        dao.forgetRecentUse("111")

        val kept = dao.findByBarcode("111")!!.toDomain()
        assertEquals("still a favourite", true, kept.favorite)
        assertNull("but no longer remembers how it was eaten", kept.lastUsedAt)
        assertEquals(
            "and stays on Home, because favourites are listed whether used or not",
            listOf("111"),
            dao.observeRecents(limit = 10).first().map { it.barcode },
        )
    }

    /**
     * The user-visible outcome for an ordinary product. `observeRecents` selects on
     * `lastUsedAt IS NOT NULL OR favorite = 1`, so clearing the timestamp is what removes the row
     * from Home — asserted through the real query rather than by re-reading the column, because the
     * query is the thing the user actually sees.
     */
    @Test
    fun forgettingANonFavouriteRemovesItFromRecents() = runTest {
        seedUsedProduct("111")
        seedUsedProduct("222")

        dao.forgetRecentUse("111")

        assertEquals(
            listOf("222"),
            dao.observeRecents(limit = 10).first().map { it.barcode },
        )
    }

    @Test
    fun forgettingAnUnknownBarcodeReportsThatNothingWasErased() = runTest {
        assertNull(dao.forgetRecentUse("nosuchbarcode"))
    }

    /**
     * Undo, end to end at the storage layer: every erased fact comes back.
     *
     * The snapshot is captured by the removal itself, so this also pins that
     * [ProductDao.forgetRecentUse] returns what it erased rather than what it left behind — a
     * snapshot read *after* the clear would restore nulls and this test would fail.
     */
    @Test
    fun undoingARemovalRestoresEveryUsageFactExactly() = runTest {
        val usage = database.portionUsageDao()
        seedUsedProduct("111")
        val before = dao.findByBarcode("111")!!
        val usageBefore = usage.findByBarcode("111")
            .map { Triple(it.inputMode, it.portionUnitId, it.amount) to (it.usageCount to it.lastUsedAt) }
            .toMap()

        val snapshot = dao.forgetRecentUse("111")!!
        dao.restoreRecentUse(snapshot)

        val after = dao.findByBarcode("111")!!
        assertEquals("lastUsedAt", before.lastUsedAt, after.lastUsedAt)
        assertEquals("lastPortion", before.lastPortion, after.lastPortion)
        assertEquals("lastInputMode", before.lastInputMode, after.lastInputMode)
        assertEquals("lastSelectedPortionUnitId", before.lastSelectedPortionUnitId, after.lastSelectedPortionUnitId)
        assertEquals("lastCount", before.lastCount, after.lastCount)

        val usageAfter = usage.findByBarcode("111")
            .map { Triple(it.inputMode, it.portionUnitId, it.amount) to (it.usageCount to it.lastUsedAt) }
            .toMap()
        assertEquals("every usual portion, with its count and recency", usageBefore, usageAfter)
        assertEquals("and it is on Home again", 1, dao.observeRecents(limit = 10).first().size)
    }

    /**
     * An Undo restores usage; it must not roll back an edit made while the Snackbar was up.
     *
     * The removal is reversible precisely because it touches nothing but usage — re-inserting the
     * whole product row from the snapshot would be simpler and would silently revert a value the
     * user verified against the package in the intervening seconds.
     */
    @Test
    fun undoingARemovalDoesNotRevertAnEditMadeDuringTheUndoWindow() = runTest {
        seedUsedProduct("111")
        val snapshot = dao.forgetRecentUse("111")!!

        // The user opens the product and verifies its value while the Snackbar is still showing.
        dao.upsert(
            dao.findByBarcode("111")!!.toDomain()
                .copy(
                    carbsPer100 = BigDecimal("52.7"),
                    verificationStatus = VerificationStatus.USER_VERIFIED,
                    favorite = true,
                )
                .toEntity(),
        )

        dao.restoreRecentUse(snapshot)

        val after = dao.findByBarcode("111")!!.toDomain()
        assertEquals("the verified value stands", BigDecimal("52.7"), after.carbsPer100)
        assertEquals("and the verification", VerificationStatus.USER_VERIFIED, after.verificationStatus)
        assertEquals("and the favourite toggled meanwhile", true, after.favorite)
        assertNotNull("while the usage came back", after.lastUsedAt)
    }

    /**
     * Undo must never resurrect a product deleted during the window.
     *
     * Re-inserting the usage rows alone would orphan them against a barcode with no product, which
     * is exactly the shape that let a deleted product's portions reappear on the next scan.
     */
    @Test
    fun undoingARemovalOfADeletedProductRestoresNothingAtAll() = runTest {
        val usage = database.portionUsageDao()
        seedUsedProduct("111")
        val snapshot = dao.forgetRecentUse("111")!!

        dao.deleteAllProducts()
        dao.restoreRecentUse(snapshot)

        assertNull("the product is not recreated", dao.findByBarcode("111"))
        assertEquals("and no usage is orphaned behind it", 0, usage.findByBarcode("111").size)
    }

    /**
     * The global action is unchanged by the arrival of the per-product one.
     *
     * The two share a definition of "usage" and deliberately **not** a statement: this asserts the
     * global clear still exempts no row, which is the property a shared statement with an optional
     * `WHERE` would be one careless call site away from losing.
     */
    @Test
    fun theGlobalClearStillForgetsEveryProductIncludingFavourites() = runTest {
        val usage = database.portionUsageDao()
        seedUsedProduct("111")
        seedUsedProduct("222", favorite = true)

        dao.clearRecentHistory()

        for (barcode in listOf("111", "222")) {
            val cleared = dao.findByBarcode(barcode)!!
            assertNull("$barcode lastUsedAt", cleared.lastUsedAt)
            assertNull("$barcode lastCount", cleared.lastCount)
            assertEquals("$barcode usual portions", 0, usage.findByBarcode(barcode).size)
        }
        assertEquals("the favourite is still starred", true, dao.findByBarcode("222")!!.favorite)
    }

    // ---- products found by name on the device (search) ---------------------------------------

    @Test
    fun everyStoredProductIsSearchableFavouritesThenRecentThenByName() = runTest {
        dao.upsert(product("111").copy(name = "banana").toEntity())
        dao.upsert(product("222", usedSecondsAfterEpoch = 10).toEntity())
        dao.upsert(product("333", usedSecondsAfterEpoch = 30).toEntity())
        dao.upsert(product("444", favorite = true).toEntity())
        dao.upsert(product("local:abc", origin = ProductDataOrigin.MANUAL).copy(name = "Apple").toEntity())

        val rows = RoomProductDataSource(dao).observeSearchable().first()

        // Never-used products are included, not only Recents; the name order ignores case.
        assertEquals(listOf("444", "333", "222", "local:abc", "111"), rows.map { it.barcode })
    }

    @Test
    fun aSearchableRowCarriesTheStoredFigureAndBasis() = runTest {
        dao.upsert(
            product("555", carbs = "9.40").copy(
                name = "Chocomel",
                brand = "Nutricia",
                basis = NutritionBasis.PER_100_ML,
                packageAmount = BigDecimal("1000"),
            ).toEntity(),
        )

        val hit = RoomProductDataSource(dao).observeSearchable().first().single()

        assertEquals("Chocomel", hit.name)
        assertEquals("Nutricia", hit.brand)
        assertEquals(0, BigDecimal("9.40").compareTo(hit.carbsPer100))
        assertEquals(NutritionBasis.PER_100_ML, hit.basis)
        assertEquals("1000 ml", hit.packageQuantity)
    }
}

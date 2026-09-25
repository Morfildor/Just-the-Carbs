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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Protein travels with the nutrient record and is never written on its own (design spec
 * 2026-09-24, section 9): stored with a fresh lookup, replaced wholesale by a refresh that may
 * replace the record, untouched by one that may not, dropped when the basis changes, and never
 * carried onto a product the user authored.
 */
class ProteinRepositoryTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC)
    private val barcode = "8000500310427"

    private fun product(
        carbs: String = "57.5",
        protein: String? = "6.3",
        origin: ProductDataOrigin = ProductDataOrigin.OPEN_FOOD_FACTS,
        status: VerificationStatus = VerificationStatus.UNVERIFIED,
        basis: NutritionBasis = NutritionBasis.PER_100_G,
    ) = Product(
        barcode = barcode,
        name = "Nutella",
        carbsPer100 = BigDecimal(carbs),
        basis = basis,
        dataSource = origin,
        verificationStatus = status,
        proteinPer100 = protein?.let(::BigDecimal),
        proteinOrigin = protein?.let { ProductDataOrigin.OPEN_FOOD_FACTS },
    )

    private class FakeLocal(seed: Product? = null) : LocalProductDataSource {
        var stored: Product? = seed

        override suspend fun fetch(barcode: String): ProductFetchResult =
            stored?.let { ProductFetchResult.Found(it) } ?: ProductFetchResult.NotFound

        override suspend fun save(product: Product) {
            stored = product
        }

        override fun observeRecents(limit: Int): Flow<List<Product>> = flowOf(listOfNotNull(stored))
        override suspend fun forgetRecentUse(barcode: String): RecentUseSnapshot? = null
        override suspend fun restoreRecentUse(snapshot: RecentUseSnapshot) = Unit
    }

    private class FakeRemote(private val product: Product) : ProductDataSource {
        override suspend fun fetch(barcode: String): ProductFetchResult = ProductFetchResult.Found(product)
    }

    private object NoUnits : PortionUnitStore {
        override suspend fun findByBarcode(barcode: String): List<PortionUnit> = emptyList()
        override fun observeByBarcode(barcode: String): Flow<List<PortionUnit>> = flowOf(emptyList())
        override suspend fun findById(id: Long): PortionUnit? = null
        override suspend fun save(unit: PortionUnit): PortionUnit = unit
        override suspend fun delete(unit: PortionUnit) = Unit
    }

    private object NoMeal : MealStore {
        override fun observeItems(): Flow<List<MealItem>> = flowOf(emptyList())
        override suspend fun findItems(): List<MealItem> = emptyList()
        override suspend fun add(item: MealItem): MealItem = item
        override suspend fun update(item: MealItem) = Unit
        override suspend fun remove(item: MealItem) = Unit
        override suspend fun clear() = Unit
    }

    private object NoUsage : PortionUsageStore {
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

    private object NoSearch : ProductSearchSource {
        override suspend fun search(terms: String) = ProductSearchResult.NoMatches
    }

    private fun repository(local: FakeLocal, remote: Product) = ProductRepository(
        local = local,
        remote = FakeRemote(remote),
        portionUnits = NoUnits,
        meal = NoMeal,
        portionUsage = NoUsage,
        searchSource = NoSearch,
        clock = clock,
    )

    @Test
    fun `a fresh lookup stores the protein with its source`() = runTest {
        val local = FakeLocal()

        repository(local, product()).lookup(barcode)

        assertEquals(0, BigDecimal("6.3").compareTo(local.stored!!.proteinPer100))
        assertEquals(ProductDataOrigin.OPEN_FOOD_FACTS, local.stored!!.proteinOrigin)
    }

    @Test
    fun `a refresh of plain online data replaces the protein with the record's`() = runTest {
        val local = FakeLocal(product(protein = "6.3"))

        repository(local, product(protein = "7.0")).refreshFromRemote(barcode)

        assertEquals(0, BigDecimal("7.0").compareTo(local.stored!!.proteinPer100))
    }

    @Test
    fun `a refresh of plain online data that lost its protein drops it with the record`() = runTest {
        val local = FakeLocal(product(protein = "6.3"))

        repository(local, product(protein = null)).refreshFromRemote(barcode)

        assertNull(local.stored!!.proteinPer100)
        assertNull(local.stored!!.proteinOrigin)
    }

    // A verified product keeps the carbohydrate figure the user checked, but protein is never
    // checked (the verify dialog asks about carbs), so it follows the online record as long as the
    // record states it per the same basis (2026-09-25 review).

    @Test
    fun `a verified product cached before protein existed gains it on refresh`() = runTest {
        val local = FakeLocal(product(protein = null, status = VerificationStatus.USER_VERIFIED))

        repository(local, product(protein = "6.3")).refreshFromRemote(barcode)

        assertEquals(0, BigDecimal("6.3").compareTo(local.stored!!.proteinPer100))
        assertEquals(ProductDataOrigin.OPEN_FOOD_FACTS, local.stored!!.proteinOrigin)
        assertEquals(0, BigDecimal("57.5").compareTo(local.stored!!.carbsPer100))
    }

    @Test
    fun `a verified product follows the record's protein, including its absence`() = runTest {
        val local = FakeLocal(product(protein = "6.3", status = VerificationStatus.USER_VERIFIED))

        repository(local, product(protein = null)).refreshFromRemote(barcode)

        assertNull(local.stored!!.proteinPer100)
        assertNull(local.stored!!.proteinOrigin)
    }

    @Test
    fun `a verified product keeps its protein when the record states another basis`() = runTest {
        val local = FakeLocal(product(protein = "6.3", status = VerificationStatus.USER_VERIFIED))

        repository(local, product(protein = "9.9", basis = NutritionBasis.PER_100_ML)).refreshFromRemote(barcode)

        assertEquals(0, BigDecimal("6.3").compareTo(local.stored!!.proteinPer100))
        assertEquals(NutritionBasis.PER_100_G, local.stored!!.basis)
    }

    @Test
    fun `accepting a newer online value pairs it with the same record's protein`() = runTest {
        val local = FakeLocal(product(carbs = "57.5", protein = "6.3", status = VerificationStatus.USER_VERIFIED))
        val repository = repository(local, product(carbs = "60", protein = "7.0"))

        val outcome = repository.refreshFromRemote(barcode)
        repository.applyLatestRemoteValue(barcode)

        assertEquals(RefreshOutcome.RemoteDiffers(BigDecimal("60"), NutritionBasis.PER_100_G), outcome)
        assertEquals(0, BigDecimal("60").compareTo(local.stored!!.carbsPer100))
        assertEquals(0, BigDecimal("7.0").compareTo(local.stored!!.proteinPer100))
    }

    @Test
    fun `a protein-only difference on a verified product is not news`() = runTest {
        val local = FakeLocal(product(protein = "6.3", status = VerificationStatus.USER_VERIFIED))

        val outcome = repository(local, product(protein = "7.0")).refreshFromRemote(barcode)

        assertEquals(RefreshOutcome.Unchanged, outcome)
    }

    @Test
    fun `a refresh never gives a product the user authored an online protein figure`() = runTest {
        val manual = product(protein = null, origin = ProductDataOrigin.MANUAL, status = VerificationStatus.USER_VERIFIED)
        val local = FakeLocal(manual)

        repository(local, product(protein = "6.3")).refreshFromRemote(barcode)

        assertNull(local.stored!!.proteinPer100)
        assertNull(local.stored!!.proteinOrigin)
    }

    @Test
    fun `a protein-only difference is not news`() = runTest {
        val local = FakeLocal(product(protein = "6.3"))

        val outcome = repository(local, product(protein = "7.0")).refreshFromRemote(barcode)

        assertEquals(RefreshOutcome.Unchanged, outcome)
    }

    @Test
    fun `a verification that keeps the basis keeps the protein`() = runTest {
        val local = FakeLocal(product())

        repository(local, product()).saveVerification(barcode, BigDecimal("58"), NutritionBasis.PER_100_G)

        assertEquals(0, BigDecimal("6.3").compareTo(local.stored!!.proteinPer100))
        assertEquals(ProductDataOrigin.OPEN_FOOD_FACTS, local.stored!!.proteinOrigin)
    }

    @Test
    fun `a verification that changes the basis drops the protein`() = runTest {
        val local = FakeLocal(product())

        repository(local, product()).saveVerification(barcode, BigDecimal("58"), NutritionBasis.PER_100_ML)

        assertNull(local.stored!!.proteinPer100)
        assertNull(local.stored!!.proteinOrigin)
    }

    @Test
    fun `a product the user authors carries no protein in this version`() = runTest {
        val local = FakeLocal()

        repository(local, product()).saveUserAuthoredProduct(product(), ProductDataOrigin.MANUAL)

        assertNull(local.stored!!.proteinPer100)
        assertNull(local.stored!!.proteinOrigin)
    }
}

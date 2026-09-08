package app.justthecarbs.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.VerificationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

/** Exercises compatible insert/update transactions, uniqueness and concurrent counts in SQLite. */
@RunWith(AndroidJUnit4::class)
class PortionUsageDaoTest {

    private lateinit var database: JustTheCarbsDatabase
    private lateinit var productDao: ProductDao
    private lateinit var usageDao: PortionUsageDao

    private val now = Instant.parse("2026-09-07T10:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            JustTheCarbsDatabase::class.java,
        ).build()
        productDao = database.productDao()
        usageDao = database.portionUsageDao()
    }

    @After
    fun tearDown() = database.close()

    private fun product(barcode: String) = Product(
        barcode = barcode,
        name = "Sliced Bread",
        carbsPer100 = BigDecimal("42"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
    )

    /**
     * The core P0 §5 guarantee, driven against the real database: 20 concurrent recordings of the
     * *identical* variant must land as one logical row whose count is exactly 20 — never fewer (a
     * lost update from two writers reading the same starting count) and never duplicated rows (the
     * old NULL-holed unique index letting a grams-mode variant multiply). Launched on
     * `Dispatchers.IO` — a real thread pool, not the single-threaded test dispatcher — because a
     * race that can only be demonstrated by genuinely concurrent threads proves nothing if it is
     * driven by a dispatcher that never runs two coroutines at once.
     */
    @Test
    fun concurrentRecordingsOfTheIdenticalVariantProduceOneRowWithTheExpectedCount() = runTest {
        productDao.upsert(product("concurrent-grams").toEntity())

        val concurrency = 20
        withContext(Dispatchers.IO) {
            (1..concurrency).map {
                async {
                    usageDao.recordUse(
                        barcode = "concurrent-grams",
                        inputMode = "GRAMS",
                        unitId = PortionUsageEntity.NO_UNIT_SENTINEL,
                        amount = "65",
                        now = now.toEpochMilli(),
                    )
                }
            }.awaitAll()
        }

        val rows = usageDao.findByBarcode("concurrent-grams")
        assertEquals("exactly one logical row must exist for the variant", 1, rows.size)
        assertEquals(
            "the count must equal the number of concurrent recordings, with none lost",
            concurrency,
            rows.single().usageCount,
        )
    }

    /** Same guarantee for a countable-portion variant, where the old index did already protect uniqueness. */
    @Test
    fun concurrentRecordingsOfAnIdenticalCountableVariantProduceOneRowWithTheExpectedCount() = runTest {
        productDao.upsert(product("concurrent-countable").toEntity())

        val concurrency = 20
        withContext(Dispatchers.IO) {
            (1..concurrency).map {
                async {
                    usageDao.recordUse(
                        barcode = "concurrent-countable",
                        inputMode = "PORTION_UNIT",
                        unitId = 7L,
                        amount = "2",
                        now = now.toEpochMilli(),
                    )
                }
            }.awaitAll()
        }

        val rows = usageDao.findByBarcode("concurrent-countable")
        assertEquals(1, rows.size)
        assertEquals(concurrency, rows.single().usageCount)
    }

    /** Different variants of the same product must never merge into each other. */
    @Test
    fun concurrentRecordingsOfDistinctVariantsRemainSeparateRows() = runTest {
        productDao.upsert(product("mixed").toEntity())

        withContext(Dispatchers.IO) {
            listOf(
                async { repeat(5) { usageDao.recordUse("mixed", "GRAMS", PortionUsageEntity.NO_UNIT_SENTINEL, "65", now.toEpochMilli()) } },
                async { repeat(3) { usageDao.recordUse("mixed", "GRAMS", PortionUsageEntity.NO_UNIT_SENTINEL, "130", now.toEpochMilli()) } },
                async { repeat(7) { usageDao.recordUse("mixed", "PORTION_UNIT", 1L, "2", now.toEpochMilli()) } },
            ).awaitAll()
        }

        val rows = usageDao.findByBarcode("mixed").associateBy { Triple(it.inputMode, it.portionUnitId, it.amount) }
        assertEquals(3, rows.size)
        assertEquals(5, rows.getValue(Triple("GRAMS", PortionUsageEntity.NO_UNIT_SENTINEL, "65")).usageCount)
        assertEquals(3, rows.getValue(Triple("GRAMS", PortionUsageEntity.NO_UNIT_SENTINEL, "130")).usageCount)
        assertEquals(7, rows.getValue(Triple("PORTION_UNIT", 1L, "2")).usageCount)
    }

    /** A first use, ordinary sequential case: one row, count 1. */
    @Test
    fun aFirstRecordingCreatesOneRowAtCountOne() = runTest {
        productDao.upsert(product("first").toEntity())

        usageDao.recordUse("first", "GRAMS", PortionUsageEntity.NO_UNIT_SENTINEL, "65", now.toEpochMilli())

        val rows = usageDao.findByBarcode("first")
        assertEquals(1, rows.size)
        assertEquals(1, rows.single().usageCount)
    }

    /** Sequential recordings of the same variant increment rather than insert duplicates. */
    @Test
    fun sequentialRecordingsOfTheSameVariantIncrementRatherThanDuplicate() = runTest {
        productDao.upsert(product("sequential").toEntity())

        repeat(4) {
            usageDao.recordUse("sequential", "GRAMS", PortionUsageEntity.NO_UNIT_SENTINEL, "65", now.toEpochMilli())
        }

        val rows = usageDao.findByBarcode("sequential")
        assertEquals(1, rows.size)
        assertEquals(4, rows.single().usageCount)
    }
}

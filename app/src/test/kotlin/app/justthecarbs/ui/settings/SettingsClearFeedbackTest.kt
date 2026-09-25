package app.justthecarbs.ui.settings

import app.justthecarbs.data.local.PortionUsageEntity
import app.justthecarbs.data.local.ProductDao
import app.justthecarbs.data.local.ProductEntity
import app.justthecarbs.data.local.RoomProductDataSource
import app.justthecarbs.data.local.SearchableProductRow
import app.justthecarbs.data.settings.SettingsRepository
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * The confirmation after *Clear recent history* / *Clear saved products*: reported only once the
 * clear has actually finished, and a clear that failed is reported as failed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsClearFeedbackTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Only the clearing statements do anything; a failing one throws as a real database would. */
    private class Dao(private val fail: Boolean = false) : ProductDao() {
        private fun write() {
            if (fail) throw IllegalStateException("disk I/O error")
        }

        override suspend fun findByBarcode(barcode: String): ProductEntity? = null
        override suspend fun upsert(product: ProductEntity) = Unit
        override suspend fun insertIfAbsent(product: ProductEntity): Long = -1L
        override fun observeRecents(limit: Int): Flow<List<ProductEntity>> = flowOf(emptyList())
        override fun observeSearchable(): Flow<List<SearchableProductRow>> = flowOf(emptyList())
        override suspend fun clearProductUsageColumns() = write()
        override suspend fun deleteAllPortionUsage() = write()
        override suspend fun clearProductUsageColumnsFor(barcode: String) = Unit
        override suspend fun deletePortionUsageFor(barcode: String) = Unit
        override suspend fun findPortionUsageFor(barcode: String): List<PortionUsageEntity> = emptyList()
        override suspend fun insertPortionUsage(usage: PortionUsageEntity) = Unit
        override suspend fun restoreProductUsageColumns(
            barcode: String,
            lastUsedAt: Long?,
            lastPortion: String?,
            lastInputMode: String?,
            lastSelectedPortionUnitId: Long?,
            lastCount: String?,
        ) = Unit
        override suspend fun deleteAllPortionUnits() = write()
        override suspend fun deleteProductRows() = write()
    }

    private fun viewModel(dao: ProductDao): SettingsViewModel {
        val dir = createTempDirectory("settings-clear").toFile()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { File(dir, "settings.preferences_pb") },
        )
        return SettingsViewModel(SettingsRepository.forTesting(store), RoomProductDataSource(dao))
    }

    @Test
    fun `clearing recent history reports it once the clear has finished`() = runTest(dispatcher) {
        val vm = viewModel(Dao())

        vm.clearRecents()
        assertNull("nothing is reported before the clear has run", vm.cleared.value)
        advanceUntilIdle()

        assertEquals(ClearReport(ClearedData.RECENT_HISTORY), vm.cleared.value)
    }

    @Test
    fun `clearing saved products reports it once the clear has finished`() = runTest(dispatcher) {
        val vm = viewModel(Dao())

        vm.clearProducts()
        advanceUntilIdle()

        assertEquals(ClearReport(ClearedData.SAVED_PRODUCTS), vm.cleared.value)
    }

    @Test
    fun `a report is consumed once it has been shown`() = runTest(dispatcher) {
        val vm = viewModel(Dao())
        vm.clearRecents()
        advanceUntilIdle()

        vm.onClearedShown()

        assertNull(vm.cleared.value)
    }

    /**
     * A failed clear is reported as failed (2026-09-25 review). It used to escape the ViewModel's
     * scope uncaught, which on a device ends the app on a disk error.
     */
    @Test
    fun `a clear that fails is reported as failed rather than thrown`() = runTest(dispatcher) {
        val vm = viewModel(Dao(fail = true))

        vm.clearProducts()
        advanceUntilIdle()

        assertEquals(ClearReport(ClearedData.SAVED_PRODUCTS, failed = true), vm.cleared.value)
    }

    @Test
    fun `a failed recent-history clear is reported the same way`() = runTest(dispatcher) {
        val vm = viewModel(Dao(fail = true))

        vm.clearRecents()
        advanceUntilIdle()

        assertEquals(ClearReport(ClearedData.RECENT_HISTORY, failed = true), vm.cleared.value)
    }
}

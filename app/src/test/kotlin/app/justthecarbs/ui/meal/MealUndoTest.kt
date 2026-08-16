package app.justthecarbs.ui.meal

import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.MealItemKind
import app.justthecarbs.domain.MealStore
import app.justthecarbs.domain.NutritionBasis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Remove → Undo restores the exact snapshot (§5.3).
 *
 * Exercises the store contract the Undo path depends on rather than the Compose Snackbar, because
 * the property that matters is that the *restored line is the removed line* — same carbs, same
 * portion text, same kind, same position — and that is decided entirely by what gets re-inserted.
 */
class MealUndoTest {

    private val now = Instant.parse("2026-08-16T10:00:00Z")

    private class FakeMealStore : MealStore {
        val stored = mutableListOf<MealItem>()
        private var nextId = 1L
        private val flow = MutableStateFlow<List<MealItem>>(emptyList())

        override fun observeItems(): Flow<List<MealItem>> = flow.asStateFlow()

        override suspend fun findItems(): List<MealItem> = sorted()

        override suspend fun add(item: MealItem): MealItem {
            val saved = item.copy(id = nextId++)
            stored += saved
            flow.value = sorted()
            return saved
        }

        override suspend fun update(item: MealItem) {
            val index = stored.indexOfFirst { it.id == item.id }
            if (index >= 0) stored[index] = item
            flow.value = sorted()
        }

        override suspend fun remove(item: MealItem) {
            stored.removeAll { it.id == item.id }
            flow.value = sorted()
        }

        override suspend fun clear() {
            stored.clear()
            flow.value = emptyList()
        }

        /** Mirrors the DAO's `ORDER BY addedAt ASC, id ASC`, which is what makes position stable. */
        private fun sorted() = stored.sortedWith(compareBy({ it.addedAt }, { it.id }))
    }

    private fun weightItem(
        name: String,
        carbs: String,
        addedAt: Instant = now,
    ) = MealItem.weightBased(
        productBarcode = "111",
        displayName = name,
        portionDescription = "65 g",
        resolvedAmount = BigDecimal("65"),
        basis = NutritionBasis.PER_100_G,
        carbsPer100 = BigDecimal("48.2"),
        exactCarbs = BigDecimal(carbs),
        addedAt = addedAt,
    )

    @Test
    fun `an undone removal restores the item with its original figures`() = runBlocking {
        val store = FakeMealStore()
        val added = store.add(weightItem("Test Bread", "31.330"))

        store.remove(added)
        assertEquals(0, store.findItems().size)

        // What `restoreMealItem` does: re-insert the snapshot with its id cleared.
        store.add(added.copy(id = 0))

        val restored = store.findItems().single()
        assertEquals("Test Bread", restored.displayName)
        assertEquals("65 g", restored.portionDescription)
        assertEquals(MealItemKind.WEIGHT_BASED, restored.kind)
        // The figure is the one the user originally saw — never recomputed from the product.
        assertEquals(0, BigDecimal("31.330").compareTo(restored.exactCarbs))
        assertEquals(added.addedAt, restored.addedAt)
    }

    @Test
    fun `a restored item returns to its original position`() = runBlocking {
        // `addedAt` is preserved by the restore, and the DAO orders by it, so an item removed from
        // the middle of a meal comes back to the middle rather than jumping to the end.
        val store = FakeMealStore()
        store.add(weightItem("First", "10", now))
        val middle = store.add(weightItem("Middle", "20", now.plusSeconds(60)))
        store.add(weightItem("Last", "30", now.plusSeconds(120)))

        store.remove(middle)
        store.add(middle.copy(id = 0))

        assertEquals(
            listOf("First", "Middle", "Last"),
            store.findItems().map { it.displayName },
        )
    }

    @Test
    fun `a direct-carb item keeps having no weight after being restored`() = runBlocking {
        // The invariant that matters most on this path: restoring must not invent a resolvedAmount.
        val store = FakeMealStore()
        val added = store.add(
            MealItem.directCarbs(
                productBarcode = "222",
                displayName = "Crispbread",
                portionDescription = "4 slices",
                count = BigDecimal("4"),
                carbsPerUnit = BigDecimal("14.2"),
                exactCarbs = BigDecimal("56.8"),
                addedAt = now,
            ),
        )

        store.remove(added)
        store.add(added.copy(id = 0))

        val restored = store.findItems().single()
        assertEquals(MealItemKind.DIRECT_CARBS, restored.kind)
        assertNotNull(restored.count)
        assertEquals(null, restored.resolvedAmount)
        assertEquals(null, restored.basis)
        assertEquals(0, BigDecimal("56.8").compareTo(restored.exactCarbs))
    }

    @Test
    fun `the observed flow reflects the restore`() = runBlocking {
        // The screen renders from `observeItems`, so a restore that writes the row but never emits
        // would look exactly like the Undo doing nothing.
        val store = FakeMealStore()
        val added = store.add(weightItem("Test Bread", "31.330"))
        store.remove(added)

        store.add(added.copy(id = 0))

        assertEquals(1, store.observeItems().first().size)
    }
}

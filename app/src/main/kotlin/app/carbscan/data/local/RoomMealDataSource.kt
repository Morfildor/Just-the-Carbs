package app.carbscan.data.local

import app.carbscan.domain.MealItem
import app.carbscan.domain.MealStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomMealDataSource(private val dao: MealItemDao) : MealStore {

    override fun observeItems(): Flow<List<MealItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun findItems(): List<MealItem> = dao.findAll().map { it.toDomain() }

    override suspend fun add(item: MealItem): MealItem {
        val id = dao.insert(item.toEntity())
        return item.copy(id = id)
    }

    override suspend fun update(item: MealItem) {
        dao.update(item.toEntity())
    }

    override suspend fun remove(item: MealItem) {
        dao.delete(item.toEntity())
    }

    override suspend fun clear() {
        dao.clear()
    }
}

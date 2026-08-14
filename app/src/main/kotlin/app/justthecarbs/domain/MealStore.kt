package app.justthecarbs.domain

import kotlinx.coroutines.flow.Flow

/**
 * Storage for the one temporary meal (brief §8).
 *
 * There is no meal id anywhere in this interface, and that is the point: the store holds *the*
 * current working meal, not a collection of meals. Ending a meal is [clear], not "close meal 47" —
 * so the app has no shape into which a meal history could later be poured without a deliberate,
 * visible schema change.
 */
interface MealStore {

    /** Insertion order — the order the user added things in. */
    fun observeItems(): Flow<List<MealItem>>

    suspend fun findItems(): List<MealItem>

    /** Insert. Returns the item with [MealItem.id] populated. */
    suspend fun add(item: MealItem): MealItem

    suspend fun update(item: MealItem)

    suspend fun remove(item: MealItem)

    suspend fun clear()
}

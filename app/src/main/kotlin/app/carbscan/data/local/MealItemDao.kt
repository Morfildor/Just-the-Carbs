package app.carbscan.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * The one working meal (brief §8).
 *
 * There is no "meals" table and no meal id: this table *is* the current meal, and clearing it is a
 * `DELETE FROM`. That is the schema-level expression of "only a current working session, not a
 * meal-history database".
 */
@Dao
interface MealItemDao {

    /** Insertion order, which is the order the user added things in. */
    @Query("SELECT * FROM current_meal_items ORDER BY addedAt ASC, id ASC")
    fun observeAll(): Flow<List<MealItemEntity>>

    @Query("SELECT * FROM current_meal_items ORDER BY addedAt ASC, id ASC")
    suspend fun findAll(): List<MealItemEntity>

    @Query("SELECT COUNT(*) FROM current_meal_items")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(item: MealItemEntity): Long

    @Update
    suspend fun update(item: MealItemEntity)

    @Delete
    suspend fun delete(item: MealItemEntity)

    @Query("DELETE FROM current_meal_items")
    suspend fun clear()
}

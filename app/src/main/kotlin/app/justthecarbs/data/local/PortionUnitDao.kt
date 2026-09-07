package app.justthecarbs.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PortionUnitDao {

    @Query("SELECT * FROM portion_units WHERE productBarcode = :barcode ORDER BY createdAt ASC")
    fun observeByBarcode(barcode: String): Flow<List<PortionUnitEntity>>

    @Query("SELECT * FROM portion_units WHERE productBarcode = :barcode ORDER BY createdAt ASC")
    suspend fun findByBarcode(barcode: String): List<PortionUnitEntity>

    @Query("SELECT * FROM portion_units WHERE id = :id")
    suspend fun findById(id: Long): PortionUnitEntity?

    @Query("SELECT * FROM portion_units WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<Long>): List<PortionUnitEntity>

    @Upsert
    suspend fun upsert(portionUnit: PortionUnitEntity): Long

    @Delete
    suspend fun delete(portionUnit: PortionUnitEntity)
}

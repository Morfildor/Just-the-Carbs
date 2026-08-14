package app.justthecarbs.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Query("SELECT * FROM products WHERE barcode = :barcode")
    suspend fun findByBarcode(barcode: String): ProductEntity?

    @Upsert
    suspend fun upsert(product: ProductEntity)

    /**
     * Recents for the home screen (§21), with favourites floated to the top (§22) rather than given
     * a tab of their own.
     *
     * `favorite DESC` works because SQLite stores the flag as 0/1. Favourites are included even
     * before they have been used, so starring a product never makes it disappear from home.
     */
    @Query(
        """
        SELECT * FROM products
        WHERE lastUsedAt IS NOT NULL OR favorite = 1
        ORDER BY favorite DESC, lastUsedAt DESC
        LIMIT :limit
        """,
    )
    fun observeRecents(limit: Int): Flow<List<ProductEntity>>

    /**
     * *Clear recent history* (§43). Deliberately not a delete: it forgets that the user ate the
     * thing, while keeping products they verified or starred. Erasing verified values as a side
     * effect of clearing history would destroy exactly the data §23 calls the reliability feature.
     */
    @Query("UPDATE products SET lastUsedAt = NULL, lastPortion = NULL WHERE favorite = 0")
    suspend fun clearRecentHistory()

    /** *Clear locally saved products* (§43). Confirmation is the caller's job. */
    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()
}

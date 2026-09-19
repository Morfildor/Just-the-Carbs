package app.justthecarbs.data.local

import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.SavedProduct
import app.justthecarbs.domain.SavedProductSearchSource
import java.math.BigDecimal

/**
 * [SavedProductSearchSource] over Room.
 *
 * The whole adapter: read the narrow projection, convert storage types to domain ones. No filtering
 * and no ordering happen here — both belong to
 * [app.justthecarbs.domain.SavedProductSearch], which is pure and therefore JVM-testable, and
 * splitting them across the two layers is how a query and a matcher end up disagreeing about what
 * counts as a match.
 *
 * A row whose stored basis is unrecognisable is **dropped rather than defaulted**. That is the same
 * rule the rest of the app applies to an unresolved denominator: a figure whose unit is unknown is
 * not a figure anyone can act on, and guessing grams here would put a number on a search card that
 * the product screen would then contradict. It is not reachable from any current write path —
 * `basis` is always written from [NutritionBasis.name] — so this exists so that a corrupt or
 * downgraded row costs one missing search result rather than an exception that takes the search
 * with it.
 */
class RoomSavedProductSearchSource(private val dao: ProductDao) : SavedProductSearchSource {

    override suspend fun allProducts(): List<SavedProduct> =
        dao.findAllForSearch().mapNotNull { row ->
            val basis = NutritionBasis.entries.firstOrNull { it.name == row.basis } ?: return@mapNotNull null
            val carbs = row.carbsPer100.toBigDecimalOrNull() ?: return@mapNotNull null
            SavedProduct(
                barcode = row.barcode,
                name = row.name,
                brand = row.brand,
                carbsPer100 = carbs,
                basis = basis,
                imageUrl = row.imageUrl,
                favorite = row.favorite,
                lastUsedAt = row.lastUsedAt,
            )
        }

    private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(this) }.getOrNull()
}

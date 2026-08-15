package app.justthecarbs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.MealItemKind
import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal
import java.time.Instant

/**
 * One line of the temporary meal — an **immutable calculation snapshot** (brief §9).
 *
 * There is deliberately no foreign key to `products`. A meal item must keep showing the number it
 * was added with even if the product is later reformulated, re-verified, corrected or deleted; a
 * cascading FK would delete the row and a restricting FK would block the product delete. So the
 * facts needed to re-display and re-total the line are copied in at add time and never read back
 * from the product again.
 *
 * [exactCarbs] is the **unrounded** result and is present for both kinds. The weight-specific and
 * count-specific columns are nullable, gated by [itemKind]: a `DIRECT_CARBS` line has no grams at
 * all, and storing a sentinel there would make "4 slices" read back as a weighed portion.
 *
 * Every decimal is TEXT for the same reason as everywhere else in this schema: SQLite's REAL is a
 * binary double and cannot round-trip 48.2 exactly.
 */
@Entity(tableName = "current_meal_items")
data class MealItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Null for a quick calculation, which has no barcode to attribute the line to. */
    val productBarcode: String?,
    val displayName: String,
    /** Human-readable, e.g. "2 slices", "½ pack", "200 ml" — never merely the resolved grams (§10). */
    val portionDescription: String,
    /** [MealItemKind] name. Always present; never inferred from which columns are null. */
    val itemKind: String,
    val resolvedAmount: String?,
    val basis: String?,
    val carbsPer100: String?,
    val count: String?,
    val carbsPerUnit: String?,
    val exactCarbs: String,
    /** Ordering only. Never shown to the user — this is calculator memory, not a dated diary (§8). */
    val addedAt: Long,
)

fun MealItem.toEntity(): MealItemEntity = MealItemEntity(
    id = id,
    productBarcode = productBarcode,
    displayName = displayName,
    portionDescription = portionDescription,
    itemKind = kind.name,
    resolvedAmount = resolvedAmount?.toPlainString(),
    basis = basis?.name,
    carbsPer100 = carbsPer100?.toPlainString(),
    count = count?.toPlainString(),
    carbsPerUnit = carbsPerUnit?.toPlainString(),
    exactCarbs = exactCarbs.toPlainString(),
    addedAt = addedAt.toEpochMilli(),
)

fun MealItemEntity.toDomain(): MealItem = when (MealItemKind.valueOf(itemKind)) {
    MealItemKind.WEIGHT_BASED -> MealItem.weightBased(
        id = id,
        productBarcode = productBarcode,
        displayName = displayName,
        portionDescription = portionDescription,
        resolvedAmount = BigDecimal(requireNotNull(resolvedAmount) { "a weight-based item needs an amount" }),
        basis = NutritionBasis.valueOf(requireNotNull(basis) { "a weight-based item needs a basis" }),
        carbsPer100 = BigDecimal(requireNotNull(carbsPer100) { "a weight-based item needs carbsPer100" }),
        exactCarbs = BigDecimal(exactCarbs),
        addedAt = Instant.ofEpochMilli(addedAt),
    )
    MealItemKind.DIRECT_CARBS -> MealItem.directCarbs(
        id = id,
        productBarcode = productBarcode,
        displayName = displayName,
        portionDescription = portionDescription,
        count = BigDecimal(requireNotNull(count) { "a direct-carb item needs a count" }),
        carbsPerUnit = BigDecimal(requireNotNull(carbsPerUnit) { "a direct-carb item needs carbsPerUnit" }),
        exactCarbs = BigDecimal(exactCarbs),
        addedAt = Instant.ofEpochMilli(addedAt),
    )
}

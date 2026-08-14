package app.justthecarbs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.justthecarbs.domain.MealItem
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
 * [exactCarbs] is the **unrounded** result. The meal total is the sum of these, formatted only
 * afterwards, so the total can never be a sum of already-rounded display values (§9).
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
    val resolvedAmount: String,
    val basis: String,
    val carbsPer100: String,
    val exactCarbs: String,
    /** Ordering only. Never shown to the user — this is calculator memory, not a dated diary (§8). */
    val addedAt: Long,
)

fun MealItem.toEntity(): MealItemEntity = MealItemEntity(
    id = id,
    productBarcode = productBarcode,
    displayName = displayName,
    portionDescription = portionDescription,
    resolvedAmount = resolvedAmount.toPlainString(),
    basis = basis.name,
    carbsPer100 = carbsPer100.toPlainString(),
    exactCarbs = exactCarbs.toPlainString(),
    addedAt = addedAt.toEpochMilli(),
)

fun MealItemEntity.toDomain(): MealItem = MealItem(
    id = id,
    productBarcode = productBarcode,
    displayName = displayName,
    portionDescription = portionDescription,
    resolvedAmount = BigDecimal(resolvedAmount),
    basis = NutritionBasis.valueOf(basis),
    carbsPer100 = BigDecimal(carbsPer100),
    exactCarbs = BigDecimal(exactCarbs),
    addedAt = Instant.ofEpochMilli(addedAt),
)

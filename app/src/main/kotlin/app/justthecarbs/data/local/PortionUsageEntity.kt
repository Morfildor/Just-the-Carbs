package app.justthecarbs.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.PortionUsage
import java.math.BigDecimal
import java.time.Instant

/**
 * How often one particular portion has been used for one product — the input to *Usual* (§13).
 *
 * **Aggregate rows only, never a per-use event log** (§22). One row per distinct portion variant,
 * with a running count and the last-used timestamp. There is therefore no timeline to reconstruct:
 * the app can tell that "2 slices" is this product's usual portion, and cannot tell when any
 * individual slice was eaten. That is a deliberate privacy boundary, not an optimisation — an
 * eating history is exactly what this app must not accumulate.
 *
 * The unique index is what makes a repeat use an increment rather than a new row. `portionUnitId`
 * participates in it so a count of "2" against a slice unit never merges with a raw "2 g" — the
 * whole point of keeping gram, millilitre and countable variants distinct (§13).
 *
 * **`portionUnitId` is `NOT NULL`, storing `NO_UNIT_SENTINEL` (0) for a GRAMS-mode row** — never a
 * SQL `NULL` (P0 §5, `MIGRATION_6_7`). SQLite's ordinary uniqueness treats every `NULL` as distinct
 * from every other `NULL`, including from itself, so a column that is nullable in this index can
 * hold unlimited "duplicate" rows despite `unique = true` — the index protected every countable
 * variant and protected *no* grams-mode variant at all. `0` is a safe sentinel: `PortionUnit.id`
 * is `@PrimaryKey(autoGenerate = true)`, and Room/SQLite `AUTOINCREMENT` never assigns `0` to a
 * real saved row (`PortionUnit(id = 0, ...)` is this codebase's own existing "not yet saved"
 * convention). The domain type [PortionUsage.portionUnitId] stays nullable — only the storage
 * representation changed, at the one seam ([toEntity]/[toDomain]) that already owns that mapping.
 */
@Entity(
    tableName = "portion_usage",
    indices = [
        Index(
            value = ["productBarcode", "inputMode", "portionUnitId", "amount"],
            unique = true,
        ),
    ],
)
data class PortionUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productBarcode: String,
    /** [InputMode] name — GRAMS or PORTION_UNIT. */
    val inputMode: String,
    /** The countable unit this amount counts, or [NO_UNIT_SENTINEL] in GRAMS mode. Never SQL NULL. */
    val portionUnitId: Long,
    /** A count in PORTION_UNIT mode ("2" slices), or a base amount in GRAMS mode ("60"). */
    val amount: String,
    val usageCount: Int,
    /** Recency, used only to break frequency ties (§13). Never displayed (§22). */
    val lastUsedAt: Long,
) {
    companion object {
        /** No countable unit exists at this id — real ids start at 1 under `AUTOINCREMENT`. */
        const val NO_UNIT_SENTINEL = 0L
    }
}

fun PortionUsage.toEntity(): PortionUsageEntity = PortionUsageEntity(
    id = id,
    productBarcode = productBarcode,
    inputMode = inputMode.name,
    portionUnitId = portionUnitId ?: PortionUsageEntity.NO_UNIT_SENTINEL,
    amount = amount.toPlainString(),
    usageCount = usageCount,
    lastUsedAt = lastUsedAt.toEpochMilli(),
)

fun PortionUsageEntity.toDomain(): PortionUsage = PortionUsage(
    id = id,
    productBarcode = productBarcode,
    inputMode = InputMode.entries.firstOrNull { it.name == inputMode } ?: InputMode.GRAMS,
    portionUnitId = portionUnitId.takeIf { it != PortionUsageEntity.NO_UNIT_SENTINEL },
    amount = BigDecimal(amount),
    usageCount = usageCount,
    lastUsedAt = Instant.ofEpochMilli(lastUsedAt),
)

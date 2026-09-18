package app.justthecarbs.ui.home

import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionConversion
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.Product
import java.math.BigDecimal

/**
 * The exact meal line *Quick Add* would write for a remembered product, or — through
 * [quickAddPlan] returning null — the statement that it cannot write one honestly.
 *
 * Built from [rememberedCarbs], the same decision that produced the figure printed on the card, so
 * the number the user sees and the number that lands in the meal cannot come from two
 * calculations. Nothing here multiplies anything itself: [RememberedCarbs] already went through
 * [app.justthecarbs.domain.CarbCalculator], [app.justthecarbs.domain.PortionResolver] or
 * [app.justthecarbs.domain.DirectCarbCalculator], and this type only carries the result along with
 * the facts the meal line and the usage record need.
 *
 * The two variants mirror the Product screen's own pending write exactly — a weighed line holds a
 * resolved amount, a basis and a per-100 figure; a direct-carb line holds a count and carbs per unit
 * and **no weight at all** — so a line added from Home is indistinguishable from one added from the
 * calculator for the same portion.
 */
sealed interface QuickAddPlan {
    val barcode: String
    val displayName: String
    val exactCarbs: BigDecimal
    val basis: NutritionBasis

    /** What to record as usage, exactly as the calculator would have recorded it. */
    val inputMode: InputMode
    val portionUnitId: Long?
    val count: BigDecimal?

    /** A weight, typed directly (`35 g`) or resolved from a count against a weight-based unit. */
    data class Weighed(
        override val barcode: String,
        override val displayName: String,
        override val exactCarbs: BigDecimal,
        override val basis: NutritionBasis,
        val resolvedAmount: BigDecimal,
        val carbsPer100: BigDecimal,
        override val inputMode: InputMode,
        override val portionUnitId: Long?,
        override val count: BigDecimal?,
    ) : QuickAddPlan

    /** A count against a direct-carb unit. There is no weight on this path and none is invented. */
    data class DirectCarbs(
        override val barcode: String,
        override val displayName: String,
        override val exactCarbs: BigDecimal,
        override val basis: NutritionBasis,
        override val count: BigDecimal,
        val carbsPerUnit: BigDecimal,
        override val portionUnitId: Long,
    ) : QuickAddPlan {
        override val inputMode: InputMode get() = InputMode.PORTION_UNIT
    }
}

/**
 * What Quick Add would add for this product, or null when the remembered use cannot be
 * reconstructed without guessing.
 *
 * Stricter than [rememberedCarbs] in one deliberate place. When the product was last used as a
 * count but its unit can no longer be resolved (deleted, or belonging to another product),
 * [rememberedCarbs] falls back to the last gram amount so the card still has something truthful to
 * *describe*. That is safe for a label and wrong for an action: the user's last choice was
 * "2 slices", and silently adding "72 g" instead would be a portion they did not pick. So that case
 * — and every other incomplete one — is ineligible, and the card still opens the product on tap.
 */
fun quickAddPlan(product: Product, unit: PortionUnit?): QuickAddPlan? {
    if (product.barcode.isEmpty()) return null
    val remembered = rememberedCarbs(product, unit) ?: return null

    if (product.lastInputMode == InputMode.PORTION_UNIT) {
        // A count was the last choice: only a count may be added.
        if (remembered !is RememberedCarbs.Countable) return null
        if (unit == null || unit.productBarcode != product.barcode) return null
        if (remembered.count.signum() <= 0) return null

        return when (val conversion = unit.conversion) {
            is PortionConversion.DirectCarbs -> QuickAddPlan.DirectCarbs(
                barcode = product.barcode,
                displayName = product.name,
                exactCarbs = remembered.exactCarbs,
                basis = product.basis,
                count = remembered.count,
                carbsPerUnit = conversion.carbsPerUnit,
                portionUnitId = unit.id,
            )

            is PortionConversion.WeightBased -> QuickAddPlan.Weighed(
                barcode = product.barcode,
                displayName = product.name,
                exactCarbs = remembered.exactCarbs,
                basis = product.basis,
                // Non-null by construction on this branch; checked rather than asserted so a
                // future change to RememberedCarbs fails closed instead of crashing Home.
                resolvedAmount = remembered.resolvedAmount ?: return null,
                carbsPer100 = product.carbsPer100,
                inputMode = InputMode.PORTION_UNIT,
                portionUnitId = unit.id,
                count = remembered.count,
            )
        }
    }

    if (remembered !is RememberedCarbs.Weight) return null
    if (remembered.portion.signum() <= 0) return null
    return QuickAddPlan.Weighed(
        barcode = product.barcode,
        displayName = product.name,
        exactCarbs = remembered.exactCarbs,
        basis = product.basis,
        resolvedAmount = remembered.portion,
        carbsPer100 = product.carbsPer100,
        inputMode = InputMode.GRAMS,
        portionUnitId = null,
        count = null,
    )
}

package app.justthecarbs.ui.home

import app.justthecarbs.domain.CarbCalculator
import app.justthecarbs.domain.DirectCarbCalculator
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.PortionConversion
import app.justthecarbs.domain.PortionResolver
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.Product
import java.math.BigDecimal

/**
 * What a product's remembered use resolves to, in the shape the card needs to *describe* it.
 *
 * Two variants rather than a bare number because the label and the figure must come from the same
 * decision. The defect this replaces printed a count ("4 slices") next to a carb value derived from
 * a stale gram amount — each half was individually defensible and together they were a lie. Here a
 * caller cannot obtain the value without also learning which kind of use produced it.
 */
sealed interface RememberedCarbs {

    /** The exact carbohydrate total. Never pre-rounded — display rounding is [app.justthecarbs.domain.ResultFormatter]'s alone. */
    val exactCarbs: BigDecimal

    /** A remembered weight/volume portion: the original grams-mode path, unchanged. */
    data class Weight(
        override val exactCarbs: BigDecimal,
        val portion: BigDecimal,
    ) : RememberedCarbs

    /**
     * A remembered count against a countable unit.
     *
     * [resolvedAmount] is null for a [PortionConversion.DirectCarbs] unit, because no weight exists
     * on that path and none is invented — the same rule the calculator and `MealItem` already follow.
     */
    data class Countable(
        override val exactCarbs: BigDecimal,
        val count: BigDecimal,
        val resolvedAmount: BigDecimal?,
    ) : RememberedCarbs
}

/**
 * The carbohydrate figure for how this product was last used, or null if it has no usable history.
 *
 * Branches on the remembered input mode and the unit's [PortionConversion] rather than reaching for
 * whichever nullable field happens to be populated. That ordering is the whole point: `lastPortion`
 * legitimately survives a direct-carb use (see `ProductRepository.recordUse` — nulling it would
 * discard a real gram amount the user established earlier), so "has a gram value" is *not* evidence
 * that the gram value describes the most recent use. Reading it first is what produced a wrong carb
 * number under a correct-looking "4 slices" label.
 *
 * Falling back to [Product.lastPortion] when a countable unit cannot be resolved is deliberate and
 * safe: that amount was genuinely resolved at some point, and the [RememberedCarbs.Weight] result
 * forces the caller to label it as a weight rather than pairing it with a count.
 */
fun rememberedCarbs(product: Product, unit: PortionUnit?): RememberedCarbs? {
    val count = product.lastCount
    val countable = product.lastInputMode == InputMode.PORTION_UNIT && unit != null && count != null

    if (countable) {
        return when (val conversion = unit!!.conversion) {
            is PortionConversion.DirectCarbs -> RememberedCarbs.Countable(
                exactCarbs = DirectCarbCalculator.exactCarbs(count!!, conversion.carbsPerUnit),
                count = count,
                resolvedAmount = null,
            )

            is PortionConversion.WeightBased -> {
                val resolved = PortionResolver.resolve(count!!, conversion.amountPerUnit)
                RememberedCarbs.Countable(
                    exactCarbs = CarbCalculator.calculate(
                        carbsPer100 = product.carbsPer100,
                        portion = resolved,
                        basis = product.basis,
                    ).exact,
                    count = count,
                    resolvedAmount = resolved,
                )
            }
        }
    }

    val portion = product.lastPortion ?: return null
    return RememberedCarbs.Weight(
        exactCarbs = CarbCalculator.calculate(
            carbsPer100 = product.carbsPer100,
            portion = portion,
            basis = product.basis,
        ).exact,
        portion = portion,
    )
}

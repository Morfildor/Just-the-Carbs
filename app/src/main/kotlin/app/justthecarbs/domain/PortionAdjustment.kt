package app.justthecarbs.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The accelerator operations behind the portion quick-adjust rail (1.0.8): halve, double, and step
 * down or up by a fixed amount.
 *
 * One place the arithmetic happens, for every surface that offers it — the product calculator's
 * weight and count fields and the meal-line editor — because three call sites each doing
 * `current * 2` is three chances to reach for a `Double` and three different answers about what
 * halving 75 g means. The screens decide which operations to *show*; this decides what they do.
 *
 * ## Exact throughout, and never a float
 *
 * Everything is [BigDecimal], matching [PortionParser]'s output and [CarbCalculator]'s input, so an
 * adjusted portion is the same kind of number as a typed one and flows into the existing
 * calculation untouched. Doubling is exact. Halving is the only operation that can produce a digit
 * the input did not have, and it is the reason [HALVE_SCALE] exists.
 *
 * ## Never negative, and zero is not a portion
 *
 * [apply] floors at zero rather than returning a negative or refusing: a portion below nothing is
 * not a state the field can hold, and the existing `−10` behaviour already clamped this way.
 * **Zero is left reachable on purpose** — it is an ordinary thing to type on the way to another
 * number, and blocking it would fight the user mid-edit. What zero must never be is a *saved*
 * quantity, and that is enforced where it belongs: `canSave`/`isCorrection` require a positive
 * amount, and the calculator shows no result for one.
 */
object PortionAdjustment {

    /**
     * What the rail's four controls do.
     *
     * [Step] carries its own size because the weight rail's step is scaled to the package (see
     * `quickAdjustStep`) while a count's is always one — the difference belongs to the caller that
     * knows the product, not to a second enum here.
     */
    sealed interface Operation {
        /** Half of the current amount — `75 → 37.5`. */
        data object Halve : Operation

        /** Twice the current amount — `75 → 150`. */
        data object Double : Operation

        /** A fixed move, signed: `-10`, `+1`. */
        data class Step(val delta: BigDecimal) : Operation {
            constructor(delta: Int) : this(BigDecimal(delta))
        }
    }

    /**
     * [operation] applied to [current], floored at zero.
     *
     * A null or unparsable [current] is treated as zero, which is what the field holds before
     * anything is typed: `+10` on an empty field gives 10, and `½` gives nothing to halve and stays
     * at zero rather than erroring.
     */
    fun apply(current: BigDecimal?, operation: Operation): BigDecimal {
        val from = current ?: BigDecimal.ZERO
        val result = when (operation) {
            Operation.Halve -> halve(from)
            Operation.Double -> from.multiply(TWO)
            is Operation.Step -> from.add(operation.delta)
        }
        return result.max(BigDecimal.ZERO)
    }

    /**
     * Half of [value], to at most [HALVE_SCALE] decimal places.
     *
     * The rounding is what stops repeated halving producing a portion no scale can weigh and no
     * field can show: 75 → 37.5 → 18.75 → 9.4 rather than 9.375 → 4.6875 → … Bounded rather than
     * exact because this is a *portion*, a quantity someone measures, and a gram to three decimal
     * places is precision the app does not have and the user cannot act on.
     *
     * Trailing zeros are stripped so an exact half reads as one — `150 → 75`, never `75.0` — which
     * is also what [ResultFormatter.editable] would otherwise have to undo. `stripTrailingZeros`
     * can return a value at negative scale (`50` becomes `5E+1`), so the scale is normalised back:
     * that form is equal by `compareTo` but prints as `5E+1`, and it is a trap this codebase has
     * hit before.
     */
    private fun halve(value: BigDecimal): BigDecimal =
        value.divide(TWO, HALVE_SCALE, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .let { if (it.scale() < 0) it.setScale(0) else it }

    private val TWO = BigDecimal(2)

    /**
     * How far halving may go: two decimal places.
     *
     * Enough that halving a decimal portion is not immediately lossy (12.5 → 6.25), and short
     * enough to stay a number a person can read off a field at a glance. It bounds the *operation*
     * only — a value typed with more precision is never truncated by being on this screen.
     */
    const val HALVE_SCALE = 2
}

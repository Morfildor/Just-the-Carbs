package app.justthecarbs.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * What a carbohydrate figure is measured *per* — the quantity **and** its unit, not just the unit.
 *
 * ## Why an enum was not enough
 *
 * [NutritionBasis] has exactly two members and both mean "per 100". That is the right model for a
 * *stored product*, where the app has already decided the figure is per 100 g or per 100 ml. It is
 * the wrong model for a figure just read off a label, because packaging routinely prints columns
 * this app cannot represent:
 *
 * ```
 * per 100 ml  |  per 250 ml        <- a green drink, two columns
 * per 100 g   |  per 9 g portion   <- a Baltic multilingual table
 * Serv. size: 1 Tbsp (18 g)        <- a US linear panel, no per-100 column at all
 * ```
 *
 * Before this type existed, an OCR value travelled through the pipeline as a bare [BigDecimal] and
 * acquired a [NutritionBasis] later — at the recovery screen, from a button the user pressed. That
 * is exactly how a device recording produced **`1.3 g / 100 ml`** from a figure the package prints
 * as *1,3 g per 250 ml*: the number was right, the basis was fabricated, and the result was wrong by
 * a factor of 2.6 with nothing on screen to say so.
 *
 * ## The invariant
 *
 * A [CarbReading] cannot be constructed without a basis, and a basis cannot be constructed without
 * its quantity. There is no path by which a value acquires a basis after the fact — that is enforced
 * by the type, not by a rule someone must remember.
 *
 * The one exception is deliberate and separate: **fully manual entry**, where the user is the source
 * of the data and is entitled to state the basis themselves. That path does not build a
 * [CarbReading] at all; it constructs a product directly. See `ManualEntryViewModel`.
 */
sealed interface CarbBasis {

    /** How to render this basis to a user, e.g. `100 ml`, `18 g serving`. Never a bare unit. */
    val label: String

    /**
     * A basis this app can represent as a stored product: per 100 g or per 100 ml.
     *
     * The only kind a [CarbReading] can be *used* from without conversion.
     */
    data class PerHundred(val basis: NutritionBasis) : CarbBasis {
        override val label: String get() = "100 ${basis.unitLabel}"
    }

    /**
     * A quantity the label declared that is not 100 — `250 ml`, `9 g`, `18 g`.
     *
     * The quantity is kept as a number rather than being folded into a name, because it is the only
     * thing that makes the figure convertible: `1.3 g / 250 ml` is `0.52 g / 100 ml`, and that
     * arithmetic needs the 250.
     *
     * [servingWord] is the countable thing the label named, when it named one — "Tbsp", "slice",
     * "portie". Presentational only; the arithmetic uses [quantity] and [unit] alone.
     */
    data class PerQuantity(
        val quantity: BigDecimal,
        val unit: NutritionBasis,
        val servingWord: String? = null,
    ) : CarbBasis {
        init {
            require(quantity.signum() > 0) { "A declared basis quantity must be positive" }
        }

        override val label: String get() = buildString {
            append(quantity.stripTrailingZeros().toPlainString())
            append(' ')
            append(unit.unitLabel)
            servingWord?.let { append(" $it") }
        }
    }

    /**
     * The label said "per serving" and never said how much a serving weighs.
     *
     * A real and common shape, and one that **cannot** be normalized: with no quantity there is no
     * arithmetic. It exists as its own case so the difference between "per 18 g" and "per an unknown
     * amount" survives into the UI, where the first can be converted and the second must be shown as
     * what it is.
     */
    data class PerUnknownServing(val servingWord: String? = null) : CarbBasis {
        override val label: String get() = servingWord ?: "serving"
    }
}

/**
 * How a [CarbReading]'s basis came to be known — for provenance, never for arithmetic.
 *
 * The distinction that matters is [Declared] versus [UserSupplied]: the first is a fact read off the
 * package, the second is the user answering a question. They must not become indistinguishable
 * downstream, which is what happened when a bare number acquired a basis at the recovery screen and
 * then looked exactly like a parsed one.
 */
enum class BasisProvenance {
    /** Read from a column header, an inline phrase or a serving declaration on the package. */
    DECLARED,

    /** The user chose it, on a screen that told them the app did not know. */
    USER_SUPPLIED,
}

/**
 * A carbohydrate amount together with what it is measured per, and how that was established.
 *
 * ## Identity includes the basis
 *
 * [equals] is the data-class default over all four fields, which is the point: `1.3 g / 250 ml` and
 * `1.3 g / 100 ml` are **different readings**, and any stage that deduplicates or looks for agreement
 * between two readings gets that for free. Before this type, agreement was keyed on the value alone,
 * so a per-250 figure and a per-100 figure carrying the same number corroborated each other.
 *
 * Note [amount] is compared with [BigDecimal.equals], which is scale-sensitive — `6` and `6.0` are
 * different. Callers that need numeric equality use [sameReadingAs].
 *
 * ## Normalization is explicit and never silent
 *
 * [normalizedToPerHundred] returns a *new* reading and leaves this one untouched, so the printed
 * figure and the derived one both survive. Nothing in this class rewrites [amount] in place.
 */
data class CarbReading(
    val amount: BigDecimal,
    val basis: CarbBasis,
    val provenance: BasisProvenance,
    /**
     * The reading this one was derived from, when it was derived rather than read.
     *
     * Non-null only on the output of [normalizedToPerHundred]. Kept so the UI can say *"33.3 g carbs
     * / 100 g — from 6 g per 18 g serving"* rather than presenting a computed figure as if the
     * package had printed it.
     */
    val derivedFrom: CarbReading? = null,
) {

    /** Whether this reading states the same amount and the same basis as [other], ignoring scale. */
    fun sameReadingAs(other: CarbReading): Boolean =
        amount.compareTo(other.amount) == 0 && basis == other.basis

    /**
     * This reading expressed per 100 of its own unit, or null when that cannot be done honestly.
     *
     * Null in exactly two cases, both of which mean *there is no arithmetic to do*:
     *
     * - the basis is [CarbBasis.PerUnknownServing] — no quantity, so no conversion exists;
     * - the result would not be a possible carbohydrate figure, which is
     *   [NutritionValueValidator]'s existing question and not a new rule invented here. A serving
     *   mass misread as `1 g` would turn `6 g` into `600 g/100 g`, and refusing that is the same
     *   refusal every other stage already makes.
     *
     * A basis that is already [CarbBasis.PerHundred] returns `this` unchanged rather than
     * round-tripping through the arithmetic, so a printed per-100 figure is never altered by being
     * asked for its per-100 form.
     *
     * ### Rounding
     *
     * [MathContext] with [DIVISION_PRECISION] significant digits and [RoundingMode.HALF_UP], because
     * `6 / 18` does not terminate. This is a *division* precision, not a display rounding: the
     * displayed figure is produced by `ResultFormatter` from this value exactly as it is for every
     * other carbohydrate number in the app, so the "round once, at the presentation boundary" rule
     * this repo already holds to is unchanged.
     */
    fun normalizedToPerHundred(): CarbReading? = when (val b = basis) {
        is CarbBasis.PerHundred -> this
        is CarbBasis.PerUnknownServing -> null
        is CarbBasis.PerQuantity -> {
            val perHundred = amount
                .multiply(BigDecimal(100))
                .divide(b.quantity, MathContext(DIVISION_PRECISION, RoundingMode.HALF_UP))
            if (NutritionValueValidator.validateCarbsPer100(perHundred.toDouble(), b.unit) == null) {
                null
            } else {
                CarbReading(
                    amount = perHundred,
                    basis = CarbBasis.PerHundred(b.unit),
                    provenance = provenance,
                    derivedFrom = this,
                )
            }
        }
    }

    private companion object {
        /**
         * Significant digits kept through the per-100 division.
         *
         * Ten is far more than any label prints and far less than [BigDecimal]'s unbounded default,
         * which would otherwise throw [ArithmeticException] on a non-terminating quotient — `6 / 18`
         * being exactly that case.
         */
        const val DIVISION_PRECISION = 10
    }
}

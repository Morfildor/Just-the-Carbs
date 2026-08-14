package app.justthecarbs.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * How the result is presented (§18, §43, correction #6).
 *
 * The default puts the **decimal** figure first. The result exists to be transcribed into another
 * calculator, and leading with the rounded value discards precision at the one moment precision
 * matters. The whole gram is still shown, as a convenience, beneath.
 *
 * The brief (§18) originally specified the opposite emphasis; the owner revisited it. If a
 * downstream tool is ever confirmed to accept whole grams only, [WHOLE_DOMINANT] restores the
 * original hierarchy — the constraint should be documented alongside that decision.
 */
enum class ResultStyle {
    /** `31.3 g` dominant, `≈ 31 g whole grams` beneath. Default. */
    DECIMAL_DOMINANT,

    /** `31 g` dominant, `31.3 g calculated` beneath. For a whole-gram-only destination. */
    WHOLE_DOMINANT,
}

/**
 * Turns a [CarbResult] into text.
 *
 * Display is locale-aware — a Dutch user reads `31,3`, not `31.3` (§42) — while everything
 * upstream stayed in [BigDecimal]. Formatting is the last step, and the only place a decimal
 * separator is allowed to be ambiguous.
 */
object ResultFormatter {

    /**
     * e.g. `31.3` / `31,3`. Always exactly one decimal place, so the value never jitters in width.
     *
     * **The rounding mode is set explicitly.** `DecimalFormat` defaults to HALF_EVEN (banker's
     * rounding) while the calculation layer uses HALF_UP, so leaving it at the default made the
     * app *display* a different decimal from the one it had computed: 15.45 became `15.4` on
     * screen and `15.5` in the domain. Two rounding modes in one app is one too many.
     */
    fun decimal(value: BigDecimal, locale: Locale = Locale.getDefault()): String =
        DecimalFormat("0.0", DecimalFormatSymbols(locale))
            .apply { roundingMode = RoundingMode.HALF_UP }
            .format(value)

    /** e.g. `31`. */
    fun whole(value: Int, locale: Locale = Locale.getDefault()): String =
        DecimalFormat("0", DecimalFormatSymbols(locale))
            .apply { roundingMode = RoundingMode.HALF_UP }
            .format(value)

    /**
     * The value placed on the clipboard by *Copy* (§19).
     *
     * Only the number — never `31 g carbs` — because it is going straight into another app's input
     * field, where a unit suffix would have to be deleted by hand.
     *
     * It is also formatted with [Locale.ROOT] rather than the display locale: a bolus calculator
     * expecting `31.3` would misread the Dutch `31,3`, and this is the one string that leaves the
     * app for a machine rather than for a person.
     */
    fun clipboardValue(result: CarbResult, style: ResultStyle): String = when (style) {
        ResultStyle.DECIMAL_DOMINANT -> decimal(result.exact, Locale.ROOT)
        ResultStyle.WHOLE_DOMINANT -> result.wholeGrams.toString()
    }
}

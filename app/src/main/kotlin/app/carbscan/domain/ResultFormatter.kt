package app.carbscan.domain

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** How the result is presented (§18, §43). */
enum class ResultStyle {
    /** Dominant whole gram with the decimal legible beneath — the default (design decision 3.2). */
    WHOLE_WITH_DECIMAL,

    /** Decimal only, for users who would rather not see a rounded figure at all. */
    DECIMAL_ONLY,
}

/**
 * Turns a [CarbResult] into text.
 *
 * Display is locale-aware — a Dutch user reads `31,3`, not `31.3` (§42) — while everything
 * upstream stayed in [BigDecimal]. Formatting is the last step, and the only place a decimal
 * separator is allowed to be ambiguous.
 */
object ResultFormatter {

    /** e.g. `31.3` / `31,3`. Always exactly one decimal place, so the value never jitters in width. */
    fun decimal(value: BigDecimal, locale: Locale = Locale.getDefault()): String =
        DecimalFormat("0.0", DecimalFormatSymbols(locale)).format(value)

    /** e.g. `31`. */
    fun whole(value: Int, locale: Locale = Locale.getDefault()): String =
        DecimalFormat("0", DecimalFormatSymbols(locale)).format(value)

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
        ResultStyle.WHOLE_WITH_DECIMAL -> result.wholeGrams.toString()
        ResultStyle.DECIMAL_ONLY -> decimal(result.exact, Locale.ROOT)
    }
}

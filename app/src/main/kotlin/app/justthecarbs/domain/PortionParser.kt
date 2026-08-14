package app.justthecarbs.domain

import java.math.BigDecimal

/**
 * Turns what the user typed into a portion number, or into nothing at all (brief §16, §42).
 *
 * Both `.` and `,` are accepted as the decimal separator on every locale. This is deliberate: a
 * locale-bound `NumberFormat` on an English-locale phone parses the Dutch `6,5` as `65`, a
 * ten-fold portion error with no visible symptom. Anything ambiguous is rejected instead.
 *
 * There is no thousands separator, because a portion never needs one — which is exactly what makes
 * "one separator, maximum" an unambiguous rule.
 */
object PortionParser {

    /** Digits with at most one `.` or `,` — no sign, no exponent, no unit suffix. */
    private val PORTION = Regex("""\d*[.,]?\d*""")

    /**
     * @return the portion, or `null` if the text is not a usable number. A negative or malformed
     *   entry yields `null` rather than a guess: the caller shows no result at all (brief §13).
     */
    fun parse(text: String): BigDecimal? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (!PORTION.matches(trimmed)) return null

        val normalised = trimmed.replace(',', '.')
        if (normalised == ".") return null

        // ".5" and "5." are ordinary mid-typing states. BigDecimal rejects "5.", so normalise both
        // ends rather than making the result flicker away while the user is still typing.
        val padded = normalised
            .let { if (it.startsWith('.')) "0$it" else it }
            .let { if (it.endsWith('.')) it.dropLast(1) else it }
        return BigDecimal(padded)
    }
}

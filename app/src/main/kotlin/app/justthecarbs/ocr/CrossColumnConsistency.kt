package app.justthecarbs.ocr

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Checks a per-100 figure against the same nutrient's portion figure, using the portion size the
 * label prints.
 *
 * ## What this is for, and what it is emphatically not for
 *
 * A nutrition table states the same quantity twice, in different units. That redundancy is free
 * evidence: `59,2 g/100 g` over a `9 g` portion must be about `5,3 g`, and the label prints `5,4 g`.
 * When the two agree, a reading has independent structural support that no single cell can give it.
 * When they cannot both be right, something has been misread and the app must not advance.
 *
 * **It is a consistency check, never a repair, and never a chooser.** It reports a verdict; it does
 * not pick the "better" number, does not rewrite a token, and does not rank candidates. A conflict
 * blocks automatic advancement — it does not resolve to whichever value looks rounder, is larger, or
 * appeared more often. That would be exactly the scoring the geometry-first architecture removed.
 *
 * ## The tolerance is a rounding tolerance, not a percentage
 *
 * Labels round their printed figures, and the rounding is *per cell*: `59,2 x 9 / 100 = 5,328`,
 * printed as `5,4`. So the comparison allows the derived value to differ from the printed one by
 * whatever rounding to the printed value's own precision could produce, plus a small allowance for
 * the per-100 figure having been rounded before this arithmetic ever ran.
 *
 * A broad percentage tolerance was rejected: at 10% it accepts `54` against a 9 g portion of a 59,2
 * g/100 g product only if you also accept an order of magnitude, and at 2% it rejects legitimate
 * labels whose two cells were rounded in opposite directions. Deriving the bound from the printed
 * precision is what makes it neither.
 *
 * ## Decimal-shift hypotheses
 *
 * A value that fails the check by almost exactly a factor of ten is the signature of a lost decimal
 * point, which is a real and common OCR failure — `5,4 g` came back as `54g` on the multilingual
 * table in `docs/Scan Evidence 01-09-26/20260901-211619-534`.
 *
 * [decimalShiftHypothesis] reports that possibility. It exists so a diagnostic can say *why* a cell
 * was refused, and so a future recovery UI can offer the user a specific question. It is **not** a
 * repair path: the caller is given a hypothesis, never a corrected value silently substituted for
 * the recognised one, and the original token stays in the diagnostics unchanged. The brief's
 * conditions are enforced here — exactly one placement may satisfy the other column, and the result
 * must be plausible — so a hypothesis is only ever offered when it is unique.
 */
internal object CrossColumnConsistency {

    /** What the two columns say about each other. */
    sealed interface Verdict {
        /** The portion figure is what the per-100 figure predicts, within label rounding. */
        data class Consistent(val derived: BigDecimal, val printed: BigDecimal) : Verdict

        /**
         * The two cannot both be right. Automatic advancement must not happen.
         *
         * [derived] is what the per-100 figure predicts for this portion; [printed] is what the
         * portion cell actually says.
         */
        data class Conflicting(val derived: BigDecimal, val printed: BigDecimal) : Verdict

        /** Not enough printed information to compare — no portion size, or a zero/absent figure. */
        data object NotComparable : Verdict
    }

    /**
     * Whether [printedPortionCarbs] is consistent with [perHundred] over a portion of
     * [portionSize], in the same unit the per-100 figure is measured in.
     */
    fun check(
        perHundred: BigDecimal,
        portionSize: BigDecimal,
        printedPortionCarbs: BigDecimal,
    ): Verdict {
        if (portionSize.signum() <= 0 || perHundred.signum() < 0) return Verdict.NotComparable

        val derived = perHundred.multiply(portionSize).divide(HUNDRED, MathContext.DECIMAL64)
        return if (withinLabelRounding(derived, printedPortionCarbs)) {
            Verdict.Consistent(derived, printedPortionCarbs)
        } else {
            Verdict.Conflicting(derived, printedPortionCarbs)
        }
    }

    /**
     * The decimal placement of [printedPortionCarbs] that would make it consistent, or null.
     *
     * Null unless **exactly one** shift works, which is what stops this becoming a search for any
     * interpretation that fits. Shifts are limited to one place either way: a two-place error is not
     * the shape a lost decimal separator produces, and widening the search is how a hypothesis stops
     * being evidence.
     *
     * The caller must treat this as a *question to ask*, never as a value to use.
     */
    fun decimalShiftHypothesis(
        perHundred: BigDecimal,
        portionSize: BigDecimal,
        printedPortionCarbs: BigDecimal,
    ): BigDecimal? {
        if (check(perHundred, portionSize, printedPortionCarbs) !is Verdict.Conflicting) return null

        val candidates = listOf(
            printedPortionCarbs.movePointLeft(1),
            printedPortionCarbs.movePointRight(1),
        ).filter { candidate ->
            candidate.signum() > 0 && check(perHundred, portionSize, candidate) is Verdict.Consistent
        }

        return candidates.singleOrNull()
    }

    /**
     * Whether [derived] rounds to [printed] at [printed]'s own precision, with a small allowance for
     * the per-100 figure itself having been rounded.
     *
     * The allowance is half a unit in the printed value's last place — the most that rounding it
     * could have moved it — doubled, because the per-100 figure this was derived from was rounded
     * too and the two errors can compound in the same direction.
     *
     * Worked against the real labels:
     * - `59,2 x 9 / 100 = 5,328` vs printed `5,4`: last place 0,1, allowance 0,1 -> consistent.
     * - `0,5 x 250 / 100 = 1,25` vs printed `1,3`: last place 0,1, allowance 0,1 -> consistent.
     * - `0,5 x 250 / 100 = 1,25` vs printed `13`: last place 1, allowance 1 -> conflicting.
     * - `59,2 x 9 / 100 = 5,328` vs printed `54`: last place 1, allowance 1 -> conflicting.
     */
    private fun withinLabelRounding(derived: BigDecimal, printed: BigDecimal): Boolean {
        val lastPlace = BigDecimal.ONE.movePointLeft(maxOf(printed.scale(), 0))
        val allowance = lastPlace.multiply(ALLOWANCE_IN_LAST_PLACES)
        return derived.subtract(printed).abs().setScale(6, RoundingMode.HALF_UP) <=
            allowance.setScale(6, RoundingMode.HALF_UP)
    }

    /**
     * How many units of the printed value's last decimal place the derived value may differ by.
     *
     * One, not two: half a unit accounts for rounding the portion cell, and another half for the
     * per-100 cell it was derived from. Raising this admits a genuine misread; lowering it rejects
     * labels whose two cells rounded in opposite directions.
     */
    private val ALLOWANCE_IN_LAST_PLACES = BigDecimal.ONE

    private val HUNDRED = BigDecimal("100")
}

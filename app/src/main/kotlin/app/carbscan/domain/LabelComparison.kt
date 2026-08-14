package app.carbscan.domain

import java.math.BigDecimal

/**
 * The result of holding a package label up against the value the app is already showing
 * (development-pass brief §12, design spec §7).
 *
 * There is deliberately no `Accepted` state and no "apply automatically" case. Every outcome here
 * still requires the user to tap, including [Match] — see [LabelComparison] for why.
 */
sealed interface LabelVerdict {

    /** The package agrees with the current value. Still requires an explicit confirmation. */
    data class Match(val current: BigDecimal, val detected: BigDecimal) : LabelVerdict

    /** The package disagrees. The app presents both and chooses neither. */
    data class Mismatch(val current: BigDecimal, val detected: BigDecimal) : LabelVerdict

    /**
     * The label was read but in a different basis than the product is declared in — e.g. the app
     * holds a per-100-ml drink and the package's table is per 100 g.
     *
     * Kept separate from [Mismatch] because the two figures are not comparable at all: 9.4 and 9.6
     * would look like a near-match while measuring different things. Converting would need a
     * density the app does not have (§17), so it refuses and says so.
     */
    data class BasisMismatch(
        val current: BigDecimal,
        val currentBasis: NutritionBasis,
        val detected: BigDecimal,
        val detectedBasis: NutritionBasis,
    ) : LabelVerdict
}

/**
 * Compares a detected label value with the one in use (§12).
 *
 * Pure and JVM-testable — no camera, no ML Kit — so the comparison rules are pinned by unit tests
 * rather than by pointing a phone at a packet of biscuits.
 *
 * **This never decides anything.** It classifies, and the UI presents the classification with
 * explicit choices. The distinction matters: an app that silently accepted a matching OCR read
 * would be asserting "the package agrees" on the strength of a camera frame, and one that silently
 * applied a mismatching one would be overwriting a verified value with a guess. Both are ruled out
 * by there being no code path from here to a stored value.
 */
object LabelComparison {

    /**
     * Values are compared with [BigDecimal.compareTo], not `equals`.
     *
     * `BigDecimal("48.2") == BigDecimal("48.20")` is **false** — `equals` includes scale — so using
     * it here would report a mismatch between a label reading of `48.20` and a stored `48.2`, and
     * send the user to resolve a conflict that does not exist.
     */
    fun compare(
        current: BigDecimal,
        currentBasis: NutritionBasis,
        detected: BigDecimal,
        detectedBasis: NutritionBasis,
    ): LabelVerdict = when {
        currentBasis != detectedBasis -> LabelVerdict.BasisMismatch(
            current = current,
            currentBasis = currentBasis,
            detected = detected,
            detectedBasis = detectedBasis,
        )
        current.compareTo(detected) == 0 -> LabelVerdict.Match(current, detected)
        else -> LabelVerdict.Mismatch(current, detected)
    }
}

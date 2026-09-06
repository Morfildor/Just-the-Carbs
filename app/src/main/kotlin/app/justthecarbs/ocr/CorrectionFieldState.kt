package app.justthecarbs.ocr

import app.justthecarbs.domain.CarbPlausibility
import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal

/**
 * The submit-eligibility rule for direct correction of a **known** total-carbohydrate row/basis —
 * used by [app.justthecarbs.ui.scan.AssistedReadingScreen]'s
 * `AssistStep.CorrectingKnownAmount` step, reached after rejecting an ordinary OCR proposal or a
 * [ConfirmationEligibility]-admitted [app.justthecarbs.ui.scan.VerificationScreenMode.ScaleUnresolved]
 * reading.
 *
 * ## The rule (task §6)
 *
 * > The initial OCR value may be shown pre-filled and selected. Submission remains unavailable until
 * > the user has actually changed the value after pressing "not correct". Once changed, ordinary
 * > numeric parsing and [CarbPlausibility] apply.
 *
 * Pure so both the keyboard's `ImeAction.Done`/`KeyboardActions` path and the visible confirm button
 * call the identical function — the task's explicit requirement that there be no second, drifting
 * validation copy for the two input paths.
 *
 * ## Why "must differ from the rejected value" rather than "must be non-blank"
 *
 * The value the user just rejected may still be a syntactically valid, physically plausible number —
 * that is exactly the case this screen exists for (`40` where the package prints `4.0`, say). A
 * bare non-blank check would let the rejected figure be resubmitted completely unchanged, silently
 * treating the rejection as though it never happened. Requiring an actual edit is what makes a
 * correction a correction.
 */
internal object CorrectionFieldState {

    /**
     * Whether [typed] may be submitted as a correction of [rejectedValue] under [basis].
     *
     * [typed] uses the same comma-or-dot decimal input every other typed-amount field in this app
     * accepts. Returns null (not submittable) when [typed] does not parse, has not changed from
     * [rejectedValue], or is not [CarbPlausibility]-plausible under [basis] — the same barrier
     * [AssistedReadingScreen]'s ordinary typed-value path already applies, so a correction cannot
     * bypass it merely by arriving through a different screen.
     */
    fun submittableAmount(
        typed: String,
        rejectedValue: BigDecimal?,
        basis: NutritionBasis,
    ): BigDecimal? {
        val parsed = typed.replace(',', '.').toBigDecimalOrNull() ?: return null
        if (rejectedValue != null && parsed.compareTo(rejectedValue) == 0) return null
        if (!CarbPlausibility.isPlausiblePer100(parsed, basis)) return null
        return parsed
    }
}

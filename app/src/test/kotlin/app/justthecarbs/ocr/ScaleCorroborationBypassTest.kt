package app.justthecarbs.ocr

import app.justthecarbs.domain.CarbBasis
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scale rule must not be bypassable by corroboration, for **any** scale verdict that fails to
 * establish absolute scale — not only for a demonstrated ambiguity.
 *
 * ## The gap this closes, measured on the thirteenth session
 *
 * The tenth pass moved [ScaleAmbiguity.Verdict.Ambiguous] ahead of the `corroborated` branch in
 * [ReadingEligibility.evaluate], on the correct ground that both verification routes are
 * scale-invariant. [ScaleAmbiguity.Verdict.Unsupported] was deliberately left *behind* that branch,
 * so a separatorless lone value two runs agreed on still advanced.
 *
 * `docs/Scan evidence 04-09 1st test/20260904-081421-421` is that path taken on hardware:
 *
 * ```
 * scale evidence  : UNSUPPORTED — candidate '11'; no paired value in this clause to share a scale
 *                   with, and the candidate kept no decimal separator of its own
 * automatic-verification: DISTINCT_OCR_AGREEMENT
 * final UI action : AUTO_ADVANCE
 * ```
 *
 * The value was **right** — that jar prints `koolhydraten 11 g` — so this is not a wrong reading. It
 * is the *reasoning* that is unsound, and the brief names the resulting state a release blocker:
 * "0 cross-run agreement bypassing unresolved absolute scale".
 *
 * ## Why `Unsupported` + corroboration is not evidence of scale
 *
 * `Unsupported` means the candidate carried no separator **and** nothing on its row could be paired
 * with it. Corroboration then reports that a second run read the same digits, or that the table's
 * ratios are coherent. Both are unchanged when every value on the label is multiplied by ten (see
 * `ScaleInvarianceTest`), so neither observes the separator that is missing. Two runs reading `11`
 * for a printed `1,1` agree perfectly and are both wrong.
 *
 * The eighth session already proved a recognizer repeats its own separator loss across four
 * recognitions of one package, so "a second run agreed" is not independence about this question.
 *
 * ## What this does NOT assert
 *
 * It does not assert that an integer is suspicious. A genuine printed `11 g` must still reach the
 * user — and it does, one tap later, through [ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE] on
 * the frozen photograph where the package is visible beside the number. What is refused is skipping
 * *both* confirmations on evidence that cannot see the decimal point.
 */
class ScaleCorroborationBypassTest {

    private fun verdictFor(scale: ScaleAmbiguity.Verdict?, corroborated: Boolean) =
        ReadingEligibility.evaluate(
            scale = scale,
            basis = CarbBasis.PerHundred(app.justthecarbs.domain.NutritionBasis.PER_100_G),
            corroborated = corroborated,
        )

    @Test
    fun `an established scale is eligible with or without corroboration`() {
        val established = ScaleAmbiguity.Verdict.Established("the candidate's own token carries a decimal separator")
        assertTrue(verdictFor(established, corroborated = false).isEligible)
        assertTrue(verdictFor(established, corroborated = true).isEligible)
    }

    @Test
    fun `a demonstrated ambiguity is refused even when corroborated`() {
        val ambiguous = ScaleAmbiguity.Verdict.Ambiguous("89", "13", "pair carries no separator")
        assertFalse(verdictFor(ambiguous, corroborated = true).isEligible)
    }

    /**
     * The thirteenth session's peanut butter: corroboration may **show** it and may not **skip** the
     * confirmation.
     *
     * ## Why the refusal lives on the advance gate rather than on eligibility
     *
     * The first version of this fix refused an `Unsupported` + corroborated reading inside
     * [ReadingEligibility.evaluate], which is the object both surfaces ask. Measured, that was too
     * blunt: the `41 g` control fell all the way to recovery with an **empty** candidate list, so a
     * figure the app had read correctly was neither shown nor offered, and the user had to retype
     * it. That fails the brief's recall gate as squarely as the original bug failed its safety gate.
     *
     * The two questions are genuinely different, so they now have two answers:
     *
     * | question | asked by | `Unsupported` + corroborated |
     * |---|---|---|
     * | may this be shown? | [ReadingEligibility.evaluate] | **yes** — on the frozen photograph |
     * | may confirmation be skipped? | [ReadingEligibility.mayAdvanceWithoutConfirmation] | **no** |
     *
     * Showing a wrong figure beside the highlighted row on a package the user is holding is
     * recoverable. Putting it straight into the calculator is not.
     */
    @Test
    fun `an unsupported scale may still be shown when corroborated`() {
        val unsupported = ScaleAmbiguity.Verdict.Unsupported("11", "no paired value in this clause")
        assertTrue(
            "a corroborated reading must still reach the user",
            verdictFor(unsupported, corroborated = true).isEligible,
        )
    }

    @Test
    fun `an unsupported scale may NOT skip the confirmation, however well corroborated`() {
        val unsupported = ScaleAmbiguity.Verdict.Unsupported("11", "no paired value in this clause")
        assertFalse(
            "corroboration is scale-invariant and cannot establish that '11' is not a collapsed '1,1'",
            ReadingEligibility.mayAdvanceWithoutConfirmation(unsupported),
        )
    }

    @Test
    fun `a demonstrated ambiguity may not skip the confirmation either`() {
        assertFalse(
            ReadingEligibility.mayAdvanceWithoutConfirmation(
                ScaleAmbiguity.Verdict.Ambiguous("89", "13", "pair carries no separator"),
            ),
        )
    }

    @Test
    fun `only an established scale may skip the confirmation`() {
        assertTrue(
            ReadingEligibility.mayAdvanceWithoutConfirmation(
                ScaleAmbiguity.Verdict.Established("the candidate's own token carries a separator"),
            ),
        )
        // A missing verdict is not permission — the safe direction for a missing input to a gate.
        assertFalse(ReadingEligibility.mayAdvanceWithoutConfirmation(null))
    }

    @Test
    fun `a declared serving basis still admits an unsupported value`() {
        // The Korean sauce control. The label PRINTED the serving this figure is measured per, so
        // the pairing is the package's assertion rather than the app's inference. Removing this
        // branch deletes `6 g / 18 g serving` across the third, fourth and fifth sessions.
        val unsupported = ScaleAmbiguity.Verdict.Unsupported("6", "no paired value in this clause")
        val verdict = ReadingEligibility.evaluate(
            scale = unsupported,
            basis = CarbBasis.PerQuantity(java.math.BigDecimal("18"), app.justthecarbs.domain.NutritionBasis.PER_100_G),
            corroborated = false,
        )
        assertTrue(verdict.reason, verdict.isEligible)
    }
}

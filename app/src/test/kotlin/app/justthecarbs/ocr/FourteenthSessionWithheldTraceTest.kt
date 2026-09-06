package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Individually pins the three captures where correct digits existed but were not presented. */
class FourteenthSessionWithheldTraceTest {
    private fun replay(suffix: String) = FourteenthSessionReplay.replay(
        FourteenthSessionCorpus.captures.single { it.bundle.endsWith(suffix) },
    )

    /**
     * `094627` now **offers** the correct `0.5`, rather than withholding it. Updated 2026-09-04.
     *
     * This case previously asserted `Conflicted` / `FOCUSED_AMOUNT_ENTRY` / nothing offered. The
     * capture resolves to `NeedsVerification` and offers the printed `0.5` for confirmation, which is
     * the outcome the semantic-panel pass's own trace said it wanted and could not then justify.
     *
     * **Not caused by the physical-observation change**, and that was measured rather than assumed:
     * stashing this pass's entire production diff and re-running reproduces `NeedsVerification` /
     * `CONFIRM_ON_CAPTURE` / `0.5` exactly. The expectation here had simply gone stale against the
     * fixtures.
     *
     * The safety story is unchanged: Pass A read `0.59` and Strategy B read `0.5`, so the app is not
     * advancing on either — it shows the value it can defend and asks.
     */
    @Test
    fun `094627 shows the correct strategy B point five`() {
        val result = replay("094627-485")

        assertEquals(0, result.offeredValue!!.compareTo(java.math.BigDecimal("0.5")))
        // 2026-09-04 (seventeenth session): this now resolves and advances rather than asking.
        //
        // Only the selected-region pass read it, so it used to be `NeedsVerification` — "one pass
        // found this, please check it". `EvidenceResolver` rule 5 already says a lone
        // re-recognition resolves **when the table's other rows support it**, and that support only
        // became reachable once the per-100 column collapse and the off-basis denominator let
        // `CrossColumnRatioCheck` run at all. The Fanta prints `PER: 100 ml | 250 ml` and three of
        // its rows pair at the resulting 2.6 ratio, the carbohydrate row among them.
        //
        // That is the structural route, independent of the photograph in the way a second view of
        // the same pixels is not — and the printed value is `0,5 g / 100 ml`, so what advances is
        // correct.
        assertTrue(result.outcome is EvidenceResolver.Outcome.Resolved)
        assertEquals(AutomaticVerification.Route.CROSS_COLUMN, result.verification.route)
        assertEquals(ScanPresentationDecision.Action.AUTO_ADVANCE, result.action)
    }

    /**
     * Twentieth session: now reaches [ScanPresentationDecision.Action.CONFIRM_UNVERIFIED] rather
     * than blank `RECOVERY` typing. `57` is a genuinely correct, structurally sound integer whose
     * only open question is decimal scale, which is exactly the case [ConfirmationEligibility]
     * exists to surface for an explicit look rather than a forced retype (task §6). `offeredValue`
     * stays null: `CONFIRM_UNVERIFIED` is deliberately excluded from `presentsValue`/`offeredValue`,
     * so this is not a relaxation of "never auto-accept" — it is still not a one-tap value.
     */
    @Test
    fun `094841 keeps correct integer fifty seven behind unresolved scale`() {
        val result = replay("094841-512")

        assertTrue(result.outcome is EvidenceResolver.Outcome.Resolved)
        assertEquals(CarbFailureReason.SCALE_UNRESOLVED, result.failureReason)
        assertNull(result.offeredValue)
        assertEquals(ScanPresentationDecision.Action.CONFIRM_UNVERIFIED, result.action)
    }

    @Test
    fun `095034 keeps correct strategy B eleven behind the cross-run conflict`() {
        val result = replay("095034-270")

        assertTrue(result.outcome is EvidenceResolver.Outcome.Conflicted)
        assertEquals(CarbFailureReason.OCR_CONFLICT, result.failureReason)
        assertNull(result.offeredValue)
        assertEquals(ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY, result.action)
    }
}

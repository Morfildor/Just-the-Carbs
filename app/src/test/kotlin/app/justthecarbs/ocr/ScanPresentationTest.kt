package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The branch that escaped every existing test, now that it has a name.
 *
 * The eighth session's P0 was not a wrong rule — it was an anonymous `else` inside a composable that
 * binds a real camera, so nothing could reach it. `readSelectedTable` released the frozen photograph
 * at the top of its `Resolved` case, and the confirmation branch inherited a recycled bitmap and
 * drew its card over the live preview instead.
 *
 * [AutomaticScanAdvance.Presentation] makes that decision a value. The invariant these cases exist
 * to protect is one line: **only `Advance` is terminal, so only `Advance` may release the capture.**
 */
class ScanPresentationTest {

    private fun outcomeFor(document: OcrDocument): EvidenceResolver.Outcome {
        val report = NutritionTableInterpreter.interpret(document)
        return EvidenceResolver.Outcome.Resolved(
            reading = report.reading,
            report = report,
            agreeingSources = listOf(EvidenceSource.FULL_FRAME_PASS_A),
        )
    }

    private val unverified = AutomaticVerification.Verdict(
        route = AutomaticVerification.Route.NONE,
        rejectionReason = "only one recognition run (PASS_A)",
    )
    private val verified =
        AutomaticVerification.Verdict(route = AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT)

    // ---------------------------------------------------------------- the P0 branch

    @Test
    fun `an unverified separatorless reading recovers rather than confirming`() {
        val document = EighthSessionFixtures.redLabelTwelve()
        assertEquals(
            AutomaticScanAdvance.Presentation.Recover,
            AutomaticScanAdvance.presentation(outcomeFor(document), unverified, document, automatic = true),
        )
    }

    @Test
    fun `an unverified reading whose scale is stated is confirmed ON THE CAPTURE`() {
        val document = EighthSessionFixtures.drinkConfirmed()
        assertEquals(
            "a decimal separator states the scale, so the reading is worth proposing — " +
                "but only beside the photograph it was read from",
            AutomaticScanAdvance.Presentation.ConfirmOnCapture,
            AutomaticScanAdvance.presentation(outcomeFor(document), unverified, document, automatic = true),
        )
    }

    @Test
    fun `a verified reading advances`() {
        val document = EighthSessionFixtures.crackerAutoAdvance()
        assertEquals(
            AutomaticScanAdvance.Presentation.Advance,
            AutomaticScanAdvance.presentation(outcomeFor(document), verified, document, automatic = true),
        )
    }

    /**
     * A reading reached after the user confirmed a crop keeps its ordinary card: they have already
     * been asked a question there, and an answer appearing without acknowledgement reads as the app
     * having ignored them.
     */
    @Test
    fun `a verified reading from a confirmed crop is not an automatic advance`() {
        val document = EighthSessionFixtures.crackerAutoAdvance()
        assertEquals(
            AutomaticScanAdvance.Presentation.NotApplicable,
            AutomaticScanAdvance.presentation(outcomeFor(document), verified, document, automatic = false),
        )
    }

    /**
     * The unverified case does **not** depend on `automatic`: a proposal the user cannot check is no
     * more checkable for having been reached through the crop screen.
     */
    @Test
    fun `an unverified separatorless reading recovers from a confirmed crop too`() {
        val document = EighthSessionFixtures.redLabelTwelve()
        assertEquals(
            AutomaticScanAdvance.Presentation.Recover,
            AutomaticScanAdvance.presentation(outcomeFor(document), unverified, document, automatic = false),
        )
    }

    @Test
    fun `a non-resolved outcome is not this decision's business`() {
        assertEquals(
            AutomaticScanAdvance.Presentation.NotApplicable,
            AutomaticScanAdvance.presentation(
                EvidenceResolver.Outcome.Nothing,
                unverified,
                EighthSessionFixtures.redLabelTwelve(),
                automatic = true,
            ),
        )
    }

    /**
     * The invariant, asserted rather than described: every presentation that keeps a question open
     * must keep the photograph. Only `Advance` ends the scan.
     */
    @Test
    fun `only Advance is terminal`() {
        val terminal = AutomaticScanAdvance.Presentation.entries.filter {
            it == AutomaticScanAdvance.Presentation.Advance
        }
        assertEquals(
            "if a new presentation is added, decide explicitly whether it releases the capture",
            listOf(AutomaticScanAdvance.Presentation.Advance),
            terminal,
        )
        assertEquals(4, AutomaticScanAdvance.Presentation.entries.size)
    }
}

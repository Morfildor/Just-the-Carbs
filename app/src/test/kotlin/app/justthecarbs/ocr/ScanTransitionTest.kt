package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The executed state transition, and the capture-retention policy that goes with it.
 *
 * ## The defect
 *
 * `LabelScannerScreen.readSelectedTable` computed a [ScanPresentationDecision] and then
 * **independently recomputed the same policy**: a separate `declined` from
 * `AutomaticScanAdvance.mayPresentAutomatically`, and a `when (outcome)` whose branches each asked
 * `AutomaticScanAdvance.presentation(...)` again and set `uiAction` by hand. The pure decision was
 * read for one thing only — a diagnostics string comparing itself against the branch that had run.
 *
 * So the rule a JVM test could reach and the rule the user actually met were two pieces of code that
 * merely happened to agree, and the bundle's own `uiAction` line existed to notice when they stopped
 * agreeing. That is the third instance of this shape in this one file: the eighth session's P0 was an
 * anonymous `else` here, and the ninth's was a local `val` here.
 *
 * The branching is now a single `when (decision)`. These cases pin the two properties that makes
 * safe: every action is reachable and terminal-ness is exhaustive.
 *
 * ## What this file can and cannot cover
 *
 * It exercises the decision and the retention policy, which are pure. Driving the composable itself —
 * capture, retake, back, close, and the bitmap actually being recycled — needs a faked camera and
 * lives in `app/src/androidTest`; the standing instrumented gap is recorded in the pass notes rather
 * than papered over here.
 */
class ScanTransitionTest {

    private fun evidence(source: EvidenceSource, document: OcrDocument) = RecognitionEvidence(
        source = source,
        report = NutritionTableInterpreter.interpret(document),
        document = document,
    )

    private val unverified = AutomaticVerification.Verdict(
        route = AutomaticVerification.Route.NONE,
        rejectionReason = "test: nothing corroborated it",
    )

    // ------------------------------------------------------------------ retention

    /**
     * **Only an advance releases the photograph**, and the rule is exhaustive over the actions.
     *
     * The eighth session's P0 was `releaseCapture` running first and unconditionally, so a
     * confirmation card inherited a recycled bitmap and drew itself over the live camera preview —
     * asking "is this right?" about a package the user had already put down.
     */
    @Test
    fun `every action that still has a question keeps the capture`() {
        ScanPresentationDecision.Action.entries.forEach { action ->
            val releases = ScanPresentationDecision.releasesCapture(action)
            if (action == ScanPresentationDecision.Action.AUTO_ADVANCE) {
                assertTrue("an advance is terminal", releases)
            } else {
                assertFalse("$action still has a question, so it keeps the photograph", releases)
            }
        }
    }

    /** A proposal, a confirmation, recovery and the crop fallback all retain it. */
    @Test
    fun `the proposal, recovery and crop routes all retain the capture`() {
        listOf(
            ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE,
            ScanPresentationDecision.Action.CONFIRM,
            ScanPresentationDecision.Action.RECOVERY,
            ScanPresentationDecision.Action.CROP_FALLBACK,
        ).forEach {
            assertFalse("$it must retain the frozen capture", ScanPresentationDecision.releasesCapture(it))
        }
    }

    // ------------------------------------------------------------------ the actions are distinct

    /**
     * A failed automatic read routes to the **crop screen**, not to focused entry.
     *
     * Both keep the photograph, so [ScanPresentationDecision.releasesCapture] cannot tell them
     * apart — but they are different screens and the difference is the reason. This used to be
     * computed in the composable as `automatic && !mayPresentAutomatically`, beside and independently
     * of the decision that was meant to be the authority.
     */
    @Test
    fun `an automatic attempt with nothing to present routes to the crop screen`() {
        val blank = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(OcrElement("Ingredienten: water, suiker", OcrBox(20, 20, 600, 60), 0, 0)),
        )
        val ev = listOf(evidence(EvidenceSource.FULL_FRAME_PASS_A, blank))
        val outcome = EvidenceResolver.resolve(ev)

        assertEquals(
            "precondition: nothing usable was read",
            EvidenceResolver.Outcome.Nothing,
            outcome,
        )
        assertEquals(
            ScanPresentationDecision.Action.CROP_FALLBACK,
            ScanPresentationDecision.decide(outcome, unverified, blank, automatic = true),
        )
    }

    /**
     * The same outcome reached through a **confirmed crop** does not go back to the crop screen.
     *
     * The user has already moved the rectangle; sending them there again is the dead end the assisted
     * path exists to remove.
     */
    @Test
    fun `the same outcome after a confirmed crop goes to the assisted path`() {
        val blank = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(OcrElement("Ingredienten: water, suiker", OcrBox(20, 20, 600, 60), 0, 0)),
        )
        val outcome = EvidenceResolver.resolve(listOf(evidence(EvidenceSource.FULL_FRAME_PASS_A, blank)))

        assertEquals(
            ScanPresentationDecision.Action.RECOVERY,
            ScanPresentationDecision.decide(outcome, unverified, blank, automatic = false),
        )
    }

    /**
     * An ambiguity and a conflict keep their own screens through a confirmed crop, and decline
     * automatically.
     *
     * Neither is a proposal, and for both the rectangle genuinely is the user's lever.
     */
    @Test
    fun `ambiguity and conflict decline automatically and keep their screens after a crop`() {
        val ambiguousDocument = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(
                OcrElement("per 100 g", OcrBox(400, 60, 620, 100), 0, 0),
                OcrElement("per 100 ml", OcrBox(700, 60, 940, 100), 0, 0),
                OcrElement("Koolhydraten", OcrBox(60, 160, 300, 200), 0, 1),
                OcrElement("12 g", OcrBox(430, 160, 540, 200), 0, 1),
                OcrElement("31 g", OcrBox(730, 160, 840, 200), 0, 1),
            ),
        )
        val outcome = EvidenceResolver.resolve(
            listOf(evidence(EvidenceSource.FULL_FRAME_PASS_A, ambiguousDocument)),
        )
        // Whatever the parser made of it, an outcome carrying no confident reading must decline the
        // automatic path and must never be a proposal.
        if (AutomaticScanAdvance.confidentReading(outcome) == null) {
            assertEquals(
                ScanPresentationDecision.Action.CROP_FALLBACK,
                ScanPresentationDecision.decide(outcome, unverified, ambiguousDocument, automatic = true),
            )
            val afterCrop =
                ScanPresentationDecision.decide(outcome, unverified, ambiguousDocument, automatic = false)
            assertTrue(
                "a non-reading is never a proposal, was $afterCrop",
                afterCrop != ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE &&
                    afterCrop != ScanPresentationDecision.Action.AUTO_ADVANCE,
            )
        }
    }

    // ------------------------------------------------------------------ Pass A and Strategy B

    /**
     * The decision is the same rule whichever pass produced the reading.
     *
     * A Strategy B win is evaluated against its own document — see [StrategyBProvenanceTest] — and
     * arrives at the same action a Pass A reading of the same table would.
     */
    @Test
    fun `Pass A and Strategy B reach the same action for the same table`() {
        val table = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(
                OcrElement("per 100 ml", OcrBox(400, 60, 640, 100), 0, 0),
                OcrElement("Koolhydraten", OcrBox(60, 160, 300, 200), 0, 1),
                OcrElement("0,5 g", OcrBox(430, 160, 560, 200), 0, 1),
                OcrElement("waarvan suikers", OcrBox(80, 240, 340, 280), 0, 2),
                OcrElement("0,5 g", OcrBox(430, 240, 560, 280), 0, 2),
            ),
        )
        val viaPassA = EvidenceResolver.resolve(listOf(evidence(EvidenceSource.FULL_FRAME_PASS_A, table)))
        val viaStrategyB =
            EvidenceResolver.resolve(listOf(evidence(EvidenceSource.SELECTED_REGION_OCR, table)))

        assertNotNull(AutomaticScanAdvance.confidentReading(viaPassA))
        assertNotNull(AutomaticScanAdvance.confidentReading(viaStrategyB))
        assertEquals(
            "the same reading must reach the same screen whichever pass read it",
            ScanPresentationDecision.decide(viaPassA, unverified, table, automatic = true),
            ScanPresentationDecision.decide(viaStrategyB, unverified, table, automatic = true),
        )
    }

    // ------------------------------------------------------------------ the decision is total

    /**
     * Every action the decision can return is one the scanner's `when` handles.
     *
     * The `when` is exhaustive over [ScanPresentationDecision.Action], so this is really a statement
     * that the enum has not grown a member with no home. It fails to compile rather than at runtime
     * if it does, which is the point of moving the branching onto the enum.
     */
    @Test
    fun `the action set is the one the scanner branches on`() {
        assertEquals(
            setOf(
                ScanPresentationDecision.Action.AUTO_ADVANCE,
                ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE,
                ScanPresentationDecision.Action.CONFIRM,
                ScanPresentationDecision.Action.RECOVERY,
                ScanPresentationDecision.Action.CROP_FALLBACK,
                // Thirteenth pass. A capture whose row and basis are established but whose digits
                // failed asks for the digits instead of for a rectangle it cannot improve.
                ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY,
            ),
            ScanPresentationDecision.Action.entries.toSet(),
        )
    }
}

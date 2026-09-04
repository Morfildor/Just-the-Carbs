package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The null-basis proposal hazard: investigated, and found **already closed at the type level**.
 *
 * ## The finding as it was reported
 *
 * [app.justthecarbs.ui.scan.VerificationScreen] opens with
 *
 * ```
 * val basis = candidate.basis ?: NutritionBasis.PER_100_G
 * ```
 *
 * and renders that basis in the headline figure and hands it to `onConfirm`. Read alone, that is the
 * app answering "grams of what?" on the user's behalf — the one question this codebase says it must
 * never answer — because a candidate whose column was never resolved would be shown, and confirmed,
 * as *per 100 g*.
 *
 * [AutomaticScanAdvance.presentation] appears to make it reachable, too: it has an explicit
 * `candidate.basis == null` branch that can return
 * [AutomaticScanAdvance.Presentation.ConfirmOnCapture], which is exactly the state that screen
 * renders.
 *
 * ## Why no production code changed
 *
 * **The state is unconstructible.** `LabelReading.Confident` carries
 *
 * ```
 * require(candidate.basis != null) { "A confident OCR result must have a per-100 basis" }
 * ```
 *
 * so there is no way to build a confident reading with no basis, and every route to both the
 * proposal screen and the advance goes through [AutomaticScanAdvance.confidentReading], which
 * returns a `Confident`. The first version of this file tried to construct one and failed with that
 * `IllegalArgumentException` on four of five cases — which is the measurement, and it is what
 * settles the question.
 *
 * So the `?:` in the screen and the null-basis branch in `presentation` are both **unreachable
 * defensive code**, not live defects. Removing them was considered and rejected: they cost nothing,
 * they fail in the safe direction if the `require` is ever relaxed, and deleting a guard on the
 * strength of an invariant held in a different file is how the guard's absence becomes invisible the
 * day that invariant moves.
 *
 * These cases pin the invariant the reachability argument rests on, so it cannot be relaxed silently.
 */
class NullBasisProposalTest {

    private fun report(document: OcrDocument) = NutritionTableInterpreter.interpret(document)

    private fun evidence(source: EvidenceSource, document: OcrDocument) = RecognitionEvidence(
        source = source,
        report = report(document),
        document = document,
    )

    /** A candidate the parser accepted but could not place under any column. */
    private fun unplacedCandidate() = CarbCandidate(
        sourceLine = "Koolhydraten 0,5 g",
        label = "Koolhydraten",
        value = BigDecimal("0.5"),
        basis = null,
        score = 100,
        geometry = OcrBox(430, 160, 560, 200),
        evidence = emptyList(),
        column = null,
    )

    /**
     * **The invariant everything else rests on.** A confident reading cannot lack a basis.
     *
     * This is what makes the `?: PER_100_G` fallback unreachable rather than merely unlikely. If this
     * case ever fails, that fallback becomes live and the screen starts inventing a basis — so the
     * assertion belongs here, beside the reasoning, rather than only in the parser's own tests.
     */
    @Test
    fun `a confident reading cannot be built without a basis`() {
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            LabelReading.Confident(unplacedCandidate())
        }
        assertNotNull(thrown.message)
        assertEquals("A confident OCR result must have a per-100 basis", thrown.message)
    }

    /**
     * An unplaced value is therefore never a confident reading — it is `NotFound`.
     *
     * Driven through the real interpreter on a table whose basis header the recognizer destroyed,
     * so this measures the production path rather than the `require` in isolation.
     */
    @Test
    fun `a table with no resolvable basis column yields no confident reading`() {
        val headerless = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(
                // The basis header, destroyed: `1009` is not a per-100 spelling.
                OcrElement("1009", OcrBox(400, 60, 640, 100), 0, 0),
                OcrElement("Koolhydraten", OcrBox(60, 160, 300, 200), 0, 1),
                OcrElement("0,5 g", OcrBox(430, 160, 560, 200), 0, 1),
                OcrElement("waarvan suikers", OcrBox(80, 240, 340, 280), 0, 2),
                OcrElement("0,5 g", OcrBox(430, 240, 560, 280), 0, 2),
            ),
        )
        val outcome = EvidenceResolver.resolve(
            listOf(evidence(EvidenceSource.FULL_FRAME_PASS_A, headerless)),
        )

        assertNull(
            "a value the parser cannot place is not a confident reading",
            AutomaticScanAdvance.confidentReading(outcome),
        )
    }

    /**
     * And so it is never proposed and never advanced — through the decision the scanner executes.
     *
     * The property that actually matters to a user, asserted on the outcome rather than on the
     * unconstructible state: no basis resolved means no figure offered.
     */
    @Test
    fun `an unplaceable value is never proposed or advanced`() {
        val headerless = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(
                OcrElement("1009", OcrBox(400, 60, 640, 100), 0, 0),
                OcrElement("Koolhydraten", OcrBox(60, 160, 300, 200), 0, 1),
                OcrElement("0,5 g", OcrBox(430, 160, 560, 200), 0, 1),
                OcrElement("waarvan suikers", OcrBox(80, 240, 340, 280), 0, 2),
                OcrElement("0,5 g", OcrBox(430, 240, 560, 280), 0, 2),
            ),
        )
        val ev = listOf(evidence(EvidenceSource.FULL_FRAME_PASS_A, headerless))
        val outcome = EvidenceResolver.resolve(ev)
        val verification = AutomaticVerification.verify(ev)

        val action = ScanPresentationDecision.decide(outcome, verification, headerless, automatic = true)
        assertEquals(
            "nothing placeable was read, so the rectangle is the user's lever",
            ScanPresentationDecision.Action.CROP_FALLBACK,
            action,
        )
    }

    /**
     * The control: a candidate that **was** placed is unaffected.
     *
     * Without this the cases above would be indistinguishable from "never propose anything".
     */
    @Test
    fun `a placed candidate is still proposed exactly as before`() {
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
        val outcome = EvidenceResolver.resolve(listOf(evidence(EvidenceSource.FULL_FRAME_PASS_A, table)))
        val confident = AutomaticScanAdvance.confidentReading(outcome)

        assertNotNull(confident)
        assertEquals(
            "precondition: this candidate is placed",
            NutritionBasis.PER_100_ML,
            confident!!.candidate.basis,
        )
        assertEquals(
            AutomaticScanAdvance.Presentation.ConfirmOnCapture,
            AutomaticScanAdvance.presentation(
                outcome,
                AutomaticVerification.Verdict(AutomaticVerification.Route.NONE),
                table,
                automatic = true,
            ),
        )
    }
}

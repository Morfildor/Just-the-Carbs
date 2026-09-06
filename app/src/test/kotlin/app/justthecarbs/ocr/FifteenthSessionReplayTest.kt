package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 21-capture physical replay, and the gate for this pass.
 *
 * ## The gate
 *
 * **Zero wrong automatic readings**, with same-frame transformed OCR unable to impersonate
 * independent evidence. `20260904-113653-044` is the capture that made this necessary: a Fanta bottle
 * printing `0,5 g / 100 ml` reached Quick Calculation showing `0.59` with no confirmation.
 *
 * ## The no-regression rule
 *
 * A correct reading that the device showed must still be shown. Automatic advancement may be
 * withdrawn where it rested on same-frame agreement — the value stays on screen and the user gains a
 * tap — but a correct value must never fall out of sight into recovery or focused entry. That
 * direction is asserted per capture in [everyCorrectReadingTheDeviceShowedIsStillShown], not just in
 * aggregate, so a swap of one loss for one gain cannot hide inside a total.
 */
class FifteenthSessionReplayTest {

    private val results = FifteenthSessionReplay.replayAll()

    /** Printed for the pass report; a replay whose table nobody can read is hard to trust. */
    @Test
    fun `the physical replay table`() {
        println(FifteenthSessionReplay.table(results))
    }

    // ================================================================================== the hard gate

    /** No capture may present a wrong carbohydrate figure without a confirmation step. */
    @Test
    fun `no capture advances automatically on a wrong value`() {
        val wrong = results.filter {
            it.classification == FifteenthSessionReplay.Classification.WRONG_AUTO
        }
        assertTrue(
            "wrong automatic readings: " + wrong.joinToString {
                "${it.capture.bundle} offered ${it.offeredValue} for ${it.capture.printedCarbs}"
            },
            wrong.isEmpty(),
        )
    }

    /**
     * The P0 capture, named and asserted on its own.
     *
     * Kept separate from the aggregate above so a regression here reads as "the Fanta advanced again"
     * rather than as a count changing.
     */
    @Test
    fun `the Fanta capture never advances automatically`() {
        val fanta = results.single { it.capture.bundle == "20260904-113653-044" }

        assertNotEquals(
            "0.59 is the printed 0,5 g with its unit glyph read as a 9; it must never auto-advance",
            ScanPresentationDecision.Action.AUTO_ADVANCE,
            fanta.action,
        )
    }

    /** Same-frame agreement may not be the reason anything advances. */
    @Test
    fun `no capture is verified by agreement within one physical observation`() {
        val sameFrameVerified = results.filter {
            it.verification.route == AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT
        }
        assertTrue(
            "every view in this corpus is one photograph, so none may reach DISTINCT_OCR_AGREEMENT: " +
                sameFrameVerified.joinToString { it.capture.bundle },
            sameFrameVerified.isEmpty(),
        )
    }

    // ================================================================================ no regressions

    /**
     * Every correct value the device put on screen is still on screen — with one named, deliberate
     * exception.
     *
     * This is the user's explicit constraint: standing still is acceptable, going backwards is not.
     * `AUTO_ADVANCE -> CONFIRM_ON_CAPTURE` keeps the value visible and is allowed; anything that hides
     * a correct value is a regression.
     *
     * ## The one deliberate exception: `20260904-113818-873`
     *
     * That capture's `57` reaches its `CONFIRM_ON_CAPTURE` **only** via same-observation agreement
     * (`route = NONE`, `viewsAgree = true`) — the identical evidence shape as `20260904-113950-065`'s
     * Hellmann's `13`, one row above in this same corpus. The 2026-09-05 evidence-reliability plan's
     * global constraint states the rule directly: "Unsupported decimal scale from a single physical
     * observation must never prefill a value for one-tap acceptance." A rule permissive enough to
     * keep `57` on the one-tap card is also permissive enough to put `13` there — that is not a
     * coincidence, it is the same evidence at `AutomaticVerification`'s resolution.
     *
     * `57` is not lost: it moves from `CONFIRM_ON_CAPTURE` to `RECOVERY`, one screen further, with the
     * frozen photograph and the correct value still present in the evidence — see the dedicated test
     * below. Excluded from this aggregate rather than silently passing so the exclusion is visible
     * and named, not a hole in the filter.
     */
    @Test
    fun `every correct reading the device showed is still shown, except the named same-observation exception`() {
        val lost = results.filter { result ->
            if (result.capture.bundle == SAME_OBSERVATION_SCALE_EXCEPTION) return@filter false
            val truth = result.capture.printedCarbs ?: return@filter false
            val deviceShowedIt = result.capture.deviceAction in PRESENTING_ACTIONS &&
                result.capture.correctValueInEvidence
            if (!deviceShowedIt) return@filter false
            val stillShown = result.presentsValue &&
                result.offeredValue?.compareTo(truth) == 0 &&
                result.offeredBasis == result.capture.printedBasis
            // A capture the device showed a *wrong* value for is not a reading we must preserve.
            val deviceWasCorrect = result.capture.failureLayer ==
                FifteenthSessionCorpus.FailureLayer.NONE
            deviceWasCorrect && !stillShown
        }

        assertTrue(
            "correct readings the device showed and this pass hid: " +
                lost.joinToString { "${it.capture.bundle} -> ${it.action}" },
            lost.isEmpty(),
        )
    }

    /**
     * The named exception, asserted on its own so a change to its evidence shape or its routing
     * reads as "the 57g capture changed" rather than silently passing through the filter above.
     *
     * `57` is withheld from one-tap confirmation because same-observation agreement cannot see a
     * missing decimal separator any more than a single recognition run can — both inherit the same
     * pixels. It still reaches the user through [ScanPresentationDecision.Action.CONFIRM_UNVERIFIED]
     * (twentieth session; previously `RECOVERY`), which keeps the frozen photograph and requires an
     * EXPLICIT visual-comparison tap before the value is used — never a one-tap shortcut. `57` is a
     * genuinely correct reading (task §6: avoid forced typing solely because a value is an integer),
     * so surfacing it for an explicit look rather than forcing a blind retype is the intended
     * improvement, not a relaxation of the "never auto-accept" rule this test's name still states.
     */
    @Test
    fun `the named same-observation exception is withheld from confirmation but not lost`() {
        val result = results.single { it.capture.bundle == SAME_OBSERVATION_SCALE_EXCEPTION }

        assertEquals(
            "precondition: this capture's only corroboration is same-observation agreement",
            AutomaticVerification.Route.NONE,
            result.verification.route,
        )
        assertTrue("precondition: same-observation views did agree", result.verification.viewsAgree)
        assertEquals(
            "57 must not be prefilled for one-tap acceptance on same-observation agreement alone -- " +
                "it now reaches an EXPLICIT confirmation screen instead of blank recovery typing",
            ScanPresentationDecision.Action.CONFIRM_UNVERIFIED,
            result.action,
        )
        assertFalse(
            "CONFIRM_UNVERIFIED is not a one-tap presenting action -- it requires the user's own " +
                "explicit comparison against the photograph before the value is used",
            result.presentsValue,
        )
    }

    /**
     * The structural route is untouched.
     *
     * `114241-317` advances on [AutomaticVerification.Route.CROSS_COLUMN], which reads the label's own
     * other rows rather than the same pixels twice. It is genuinely scale-sensitive evidence and this
     * pass must not cost it — that is the difference between removing a bad route and removing
     * automation.
     */
    @Test
    fun `the cross-column verified capture still advances automatically`() {
        val bread = results.single { it.capture.bundle == "20260904-114241-317" }

        assertEquals(AutomaticVerification.Route.CROSS_COLUMN, bread.verification.route)
        assertEquals(ScanPresentationDecision.Action.AUTO_ADVANCE, bread.action)
        assertEquals(0, bread.offeredValue?.compareTo(bread.capture.printedCarbs))
    }

    /**
     * The corpus still contains the defect it was collected for.
     *
     * Without this, a fixture regenerated from a later export could quietly drop the Fanta and every
     * assertion above would pass over a corpus that no longer tests anything.
     */
    @Test
    fun `the corpus still contains the capture that motivated this pass`() {
        val fanta = FifteenthSessionCorpus.captures.single { it.bundle == "20260904-113653-044" }

        assertEquals("AUTO_ADVANCE", fanta.deviceAction)
        assertEquals("DISTINCT_OCR_AGREEMENT", fanta.deviceVerification)
        assertTrue(fanta.autoRestedOnSameFrameAgreement)
    }

    private companion object {
        val PRESENTING_ACTIONS = setOf("AUTO_ADVANCE", "CONFIRM_ON_CAPTURE", "CONFIRM")

        /**
         * See `every correct reading the device showed is still shown, except the named
         * same-observation exception` and the dedicated test below for why this one capture is
         * named rather than silently passing.
         */
        const val SAME_OBSERVATION_SCALE_EXCEPTION = "20260904-113818-873"
    }
}

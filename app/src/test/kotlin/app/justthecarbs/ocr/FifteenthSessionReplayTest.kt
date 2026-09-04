package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
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
     * Every correct value the device put on screen is still on screen.
     *
     * This is the user's explicit constraint: standing still is acceptable, going backwards is not.
     * `AUTO_ADVANCE -> CONFIRM_ON_CAPTURE` keeps the value visible and is allowed; anything that hides
     * a correct value is a regression.
     */
    @Test
    fun `every correct reading the device showed is still shown`() {
        val lost = results.filter { result ->
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
    }
}

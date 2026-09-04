package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The device's verdict on the physical-observation build, plus the defect it exposed.
 *
 * These 16 captures were taken **on the fixed build**, so unlike every earlier corpus this one is not
 * a reconstruction of what a previous APK did — it is a measurement of the shipped behaviour.
 */
class SixteenthSessionReplayTest {

    private fun replay(suffix: String) = SixteenthSessionReplay.replay(
        SixteenthSessionCorpus.captures.single { it.bundle.endsWith(suffix) },
    )

    @Test
    fun `all physical documents retain their complete recognized element lists`() {
        SixteenthSessionCorpus.captures.forEach { capture ->
            assertTrue(capture.bundle, capture.passA().elements.isNotEmpty())
            assertTrue(capture.bundle, capture.strategyB().elements.isNotEmpty())
        }
    }

    /**
     * The hard gate. No capture in this corpus may advance automatically on a wrong figure, and none
     * may offer one for confirmation either.
     *
     * The second half is what this session added: `124935` offered `6.29` on a package printing
     * `6,2 g`, and it did so through `CONFIRM_ON_CAPTURE` rather than `AUTO_ADVANCE`, so a gate
     * written only against wrong *autos* would have passed it.
     */
    @Test
    fun `no capture shows the user a wrong carbohydrate figure`() {
        val results = SixteenthSessionReplay.replayAll()
        println(SixteenthSessionReplay.table(results))

        val wrong = results.filter {
            it.classification == SixteenthSessionReplay.Classification.WRONG_AUTO ||
                it.classification == SixteenthSessionReplay.Classification.WRONG_PROPOSAL
        }
        assertEquals(
            "wrong figures shown: ${wrong.map { "${it.capture.bundle}=${it.offeredValue}" }}",
            0,
            wrong.size,
        )
    }

    /**
     * The corrupted `6,29` must never be the figure the user is shown.
     *
     * The package prints `6,2 g / 100 ml`. ML Kit fused the `g` onto the value on **every** cell of
     * this capture, so the label read as one that states units in its headers only, and the
     * accompaniment rule that exists for exactly this corruption was never asked.
     *
     * Note what is *not* asserted: nothing requires `6.29` to become `6.2`. Manufacturing the digit
     * is prohibited. The requirement is only that a value the app cannot defend is not presented as
     * one it can.
     */
    @Test
    fun `the corrupted six point two nine is never offered`() {
        val result = replay("124935-320")

        assertNotEquals(
            "6.29 was offered as if it were a read value",
            0,
            result.offeredValue?.compareTo(BigDecimal("6.29")) ?: -1,
        )
    }

    /**
     * The control, and the reason the cause is not in doubt.
     *
     * `124924` is the **same physical package** photographed seconds earlier, where the unit glyphs
     * survived. It must keep reading `6.2`, so the fix for `124935` cannot be a blanket refusal of
     * this label's layout.
     */
    @Test
    fun `the sibling capture of the same package still reads six point two`() {
        val result = replay("124924-679")

        assertEquals(0, result.offeredValue!!.compareTo(BigDecimal("6.2")))
    }

    /**
     * Every correct reading the device showed must still be shown.
     *
     * The device offered a correct figure on five captures. That count may grow and must not shrink —
     * the standing instruction is that a pass may stand still but may never regress.
     */
    @Test
    fun `no correct reading the device showed is lost`() {
        val results = SixteenthSessionReplay.replayAll()

        val deviceShowedCorrect = SixteenthSessionCorpus.captures.filter { capture ->
            val offered = capture.deviceOffered ?: return@filter false
            val truth = capture.printedCarbs ?: return@filter false
            offered.compareTo(truth) == 0
        }
        assertEquals(5, deviceShowedCorrect.size)

        deviceShowedCorrect.forEach { capture ->
            val result = results.single { it.capture.bundle == capture.bundle }
            assertEquals(
                "${capture.bundle} lost its correct reading",
                0,
                result.offeredValue?.compareTo(capture.printedCarbs!!) ?: -1,
            )
        }
    }

    /**
     * The physical-observation rule, measured on the device rather than argued.
     *
     * Three bundles record two recognition runs over one JPEG (`124711`, `124724`, `124924`). Under
     * the previous build each would have been `DISTINCT_OCR_AGREEMENT` and advanced automatically;
     * all three confirm instead, and none of the sixteen advances.
     */
    @Test
    fun `same-frame agreement never advances automatically`() {
        val results = SixteenthSessionReplay.replayAll()

        results.forEach { result ->
            assertNotEquals(
                "${result.capture.bundle} advanced on same-frame evidence",
                AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT,
                result.verification.route,
            )
        }
        assertEquals(
            0,
            results.count { it.action == ScanPresentationDecision.Action.AUTO_ADVANCE },
        )
    }
}

package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The 29-capture session's own verdict, and the gate for the changes made against it.
 *
 * The first test is the one that makes every other claim in this class meaningful: the replay must
 * reproduce what the device did, or it is measuring the fixture rather than the app.
 */
class SeventeenthSessionReplayTest {

    private fun replay(suffix: String) = SeventeenthSessionReplay.replay(
        SeventeenthSessionCorpus.captures.single { it.bundle.endsWith(suffix) },
    )

    @Test
    fun `all physical documents retain their complete recognized element lists`() {
        SeventeenthSessionCorpus.captures.forEach { capture ->
            assertTrue(capture.bundle, capture.passA().elements.isNotEmpty())
            assertTrue(capture.bundle, capture.strategyB().elements.isNotEmpty())
        }
    }

    /**
     * The hard gate, and it is deliberately wider than "no wrong auto".
     *
     * A wrong figure offered for one-tap confirmation is the failure the sixteenth session found,
     * and it arrived through `CONFIRM_ON_CAPTURE`, so a gate written only against `AUTO_ADVANCE`
     * would have passed it.
     */
    @Test
    fun `no capture shows the user a wrong carbohydrate figure`() {
        val results = SeventeenthSessionReplay.replayAll()
        println(SeventeenthSessionReplay.table(results))

        val wrong = results.filter {
            it.classification == SeventeenthSessionReplay.Classification.WRONG_AUTO ||
                it.classification == SeventeenthSessionReplay.Classification.WRONG_PROPOSAL
        }
        assertEquals(
            "wrong figures shown: ${wrong.map { "${it.capture.bundle}=${it.offeredValue}" }}",
            0,
            wrong.size,
        )
    }

    /**
     * Every correct figure the device showed must still be shown, and with the same basis — with one
     * named, deliberate exception.
     *
     * The device offered a correct value on 14 captures. That count may grow and must never shrink
     * except for the one capture below, whose value moves one screen further rather than
     * disappearing: the standing instruction is that a pass may stand still but may never regress.
     *
     * ## The one deliberate exception: `20260904-134428-088`
     *
     * That capture's `80` reached its `CONFIRM_ON_CAPTURE` **only** via same-observation agreement
     * (`route = NONE`, `viewsAgree = true`) — the identical evidence shape the sixteenth/fifteenth
     * sessions' Hellmann's `1,3 -> 13` case reaches the user through. The 2026-09-05
     * evidence-reliability plan's global constraint states the rule directly: "Unsupported decimal
     * scale from a single physical observation must never prefill a value for one-tap acceptance."
     *
     * `80` is not lost — it now routes to [ScanPresentationDecision.Action.RECOVERY], which keeps the
     * frozen photograph, one screen further than the one-tap card. See the dedicated test below.
     */
    @Test
    fun `no correct reading the device showed is lost, except the named same-observation exception`() {
        val results = SeventeenthSessionReplay.replayAll()

        val deviceShowedCorrect = SeventeenthSessionCorpus.captures.filter { capture ->
            val offered = capture.deviceOffered ?: return@filter false
            val truth = capture.printedCarbs ?: return@filter false
            offered.compareTo(truth) == 0
        }
        assertEquals(14, deviceShowedCorrect.size)

        deviceShowedCorrect
            .filter { it.bundle != SAME_OBSERVATION_SCALE_EXCEPTION }
            .forEach { capture ->
                val result = results.single { it.capture.bundle == capture.bundle }
                assertEquals(
                    "${capture.bundle} lost its correct reading",
                    0,
                    result.offeredValue?.compareTo(capture.printedCarbs!!) ?: -1,
                )
                assertEquals(
                    "${capture.bundle} changed the basis it offers",
                    capture.printedBasis,
                    result.offeredBasis,
                )
            }
    }

    /**
     * The named exception, asserted on its own so a change to its evidence shape or its routing
     * reads as "the rice-flour capture changed" rather than silently passing through the filter
     * above.
     *
     * `80` is withheld from ONE-TAP confirmation because same-observation agreement cannot see a
     * missing decimal separator any more than a single recognition run can — both inherit the same
     * pixels. Twentieth session: it now reaches [ScanPresentationDecision.Action.CONFIRM_UNVERIFIED]
     * (an explicit visual-confirmation screen), rather than blank `RECOVERY` typing — task §6's own
     * "avoid forced typing solely because a value is an integer." Never a one-tap shortcut, asserted
     * explicitly below.
     */
    @Test
    fun `the named same-observation exception is withheld from confirmation but not lost`() {
        val result = replay("134428-088")

        assertEquals(
            "precondition: this capture's only corroboration is same-observation agreement",
            AutomaticVerification.Route.NONE,
            result.verification.route,
        )
        assertTrue("precondition: same-observation views did agree", result.verification.viewsAgree)
        assertEquals(
            "80 must not be prefilled for a ONE-TAP acceptance on same-observation agreement alone " +
                "-- it reaches an explicit visual-confirmation screen instead",
            ScanPresentationDecision.Action.CONFIRM_UNVERIFIED,
            result.action,
        )
        assertTrue(
            "must never be a one-tap shortcut past the user's own comparison",
            result.action != ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE &&
                result.action != ScanPresentationDecision.Action.AUTO_ADVANCE,
        )
    }

    /**
     * Same-frame agreement still never advances automatically.
     *
     * Six bundles record two recognition runs over one JPEG. The physical-observation rule refuses
     * to treat them as corroboration, and nothing in this pass may reopen that.
     */
    @Test
    fun `same-frame agreement never advances automatically`() {
        SeventeenthSessionReplay.replayAll().forEach { result ->
            if (result.action == ScanPresentationDecision.Action.AUTO_ADVANCE) {
                assertEquals(
                    "${result.capture.bundle} advanced on same-frame evidence",
                    AutomaticVerification.Route.CROSS_COLUMN,
                    result.verification.route,
                )
            }
        }
    }

    /**
     * An automatic advance must always carry a value that matches the print.
     *
     * This is the invariant the whole pass is judged on: raising the automatic rate is only
     * acceptable while every automatic reading is correct.
     */
    @Test
    fun `every automatic advance is correct`() {
        SeventeenthSessionReplay.replayAll()
            .filter { it.action == ScanPresentationDecision.Action.AUTO_ADVANCE }
            .forEach { result ->
                val truth = result.capture.printedCarbs
                assertTrue(
                    "${result.capture.bundle} advanced with no ground truth to check against",
                    truth != null,
                )
                assertEquals(
                    "${result.capture.bundle} advanced on a wrong value",
                    0,
                    result.offeredValue!!.compareTo(truth!!),
                )
                assertEquals(
                    "${result.capture.bundle} advanced on a wrong basis",
                    result.capture.printedBasis,
                    result.offeredBasis,
                )
            }
    }

    /**
     * The labels whose own table corroborates their carbohydrate row.
     *
     * `134917-744` (Baltic crispbread, `Ø/100 g` beside `Ø/9 g`) states the serving ratio on three
     * independent rows and its second column resolves `UNKNOWN` — correct for reading a value, and
     * no obstacle to a ratio. `134719-477` (coconut water, `100ml` beside a `PER_SERVING` 250 ml
     * column) is the ordinary shape, unblocked by the per-100 collapse.
     *
     * Both were read correctly and both previously made the user confirm.
     */
    @Test
    fun `a label whose own table corroborates its carbohydrate row advances automatically`() {
        listOf("134917-744" to "59.2", "134719-477" to "3.2").forEach { (suffix, expected) ->
            val result = replay(suffix)
            assertEquals(
                "$suffix did not advance (${result.verification.route})",
                ScanPresentationDecision.Action.AUTO_ADVANCE,
                result.action,
            )
            assertEquals(
                AutomaticVerification.Route.CROSS_COLUMN,
                result.verification.route,
            )
            assertEquals(0, result.offeredValue!!.compareTo(BigDecimal(expected)))
        }
    }

    /**
     * A capture whose table cannot supply three clean pairs still asks the user, and that is right.
     *
     * `134233-470` looks like it should verify — it prints `Ø/100 g` beside `Ø/125 g` and its
     * printed rows really do all state the ratio 1.25. On this recognition they do not survive as
     * *pairs*: row reconstruction put the fat row's `10,0 g` and its serving `12,5 g` on separate
     * rows, the energy row's per-100 cell pairs with the neighbouring `152 kcal` rather than with
     * `629`, and the protein row picks up the salt value. One coherent pair remains, against three.
     *
     * That is the check declining to answer, not failing. It is recorded as an assertion so a
     * future change that "fixes" it by loosening the pair rules has to argue with this case first.
     */
    @Test
    fun `a table that cannot supply three clean pairs still asks the user`() {
        val result = replay("134233-470")

        assertEquals(0, result.offeredValue!!.compareTo(BigDecimal("3.2")))
        assertEquals(
            ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE,
            result.action,
        )
    }

    /**
     * The control that any column-collapsing rule must not break.
     *
     * `134552-198` photographs two side-by-side per-100 g tables for **different** products —
     * noodles at 27 g and bouillon at 2,7 g — emitting three `PER_100_G` columns spanning 443 px.
     * Those are a genuine disagreement, not a multilingual restatement of one column, and merging
     * them would invent a table that is not printed.
     */
    @Test
    fun `two different products' per-hundred tables are never merged into one column`() {
        val result = replay("134552-198")

        assertEquals(0, result.offeredValue!!.compareTo(BigDecimal("27")))
        assertTrue(
            "the noodles/bouillon capture must not advance on a merged column",
            result.action != ScanPresentationDecision.Action.AUTO_ADVANCE,
        )
    }

    /**
     * A multilingual nutrient name wrapped across rows is one declaration, not several.
     *
     * `134420-616` prints `Karbonhidrat / Kohlenhydrate glucides 80 g / carbohydrate` across four
     * reconstructed rows, each typing `TOTAL_CARBOHYDRATE`. The basis is established and the row is
     * known, so the honest destination is focused entry — one tap on a known basis, rather than a
     * crop gesture that cannot help.
     */
    @Test
    fun `a name wrapped across languages still reaches focused entry`() {
        val result = replay("134420-616")

        assertEquals(
            ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY,
            result.action,
        )
        assertEquals(
            app.justthecarbs.domain.NutritionBasis.PER_100_G,
            result.focusedTarget!!.basis,
        )
    }

    /**
     * The scale rule is untouched: a separatorless value is still never offered.
     *
     * `134311-325` prints 1,3 g/100 ml and was recognised as a bare `13g`. Manufacturing `1.3` from
     * it is prohibited, and so is offering `13`.
     */
    @Test
    fun `a value that lost its decimal separator is still never offered`() {
        listOf("134311-325", "134322-389").forEach { suffix ->
            val result = replay(suffix)
            assertTrue(
                "$suffix offered ${result.offeredValue}",
                result.offeredValue == null ||
                    result.offeredValue.compareTo(BigDecimal("13")) != 0,
            )
        }
    }

    private companion object {
        /**
         * See `no correct reading the device showed is lost, except the named same-observation
         * exception` and the dedicated test below for why this one capture is named rather than
         * silently passing.
         */
        const val SAME_OBSERVATION_SCALE_EXCEPTION = "20260904-134428-088"
    }
}

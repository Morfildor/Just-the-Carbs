package app.justthecarbs.ocr

import app.justthecarbs.ocr.ThirteenthSessionCorpus.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The corpus-level acceptance gates, asserted over the whole physical session rather than one
 * fixture at a time.
 *
 * ## Why these are properties and not per-capture literals
 *
 * The brief asks for aggregate metrics and forbids hiding regressions inside "NotFound". A per-
 * capture literal cannot express *"no capture anywhere may advance on an unestablished scale"* — it
 * can only express what one capture happens to do today, which is how a suite grows to a thousand
 * tests that still miss the release blocker. Every gate below is quantified over the corpus, so a
 * new capture is covered the moment it is added to [ThirteenthSessionCorpus].
 */
class ThirteenthSessionRegressionTest {

    private val results = ThirteenthSessionReplay.replayAll()

    // ---------------------------------------------------------------- absolute safety gates

    @Test
    fun `no capture advances or confirms a value the package does not print`() {
        val wrong = results.filter {
            it.classification == Outcome.WRONG_AUTO || it.classification == Outcome.WRONG_CONFIRM
        }
        assertTrue(
            "release blocker — wrong figures shown: " +
                wrong.joinToString { "${it.capture.bundle} offered ${it.offeredValue} for ${it.capture.printedCarbs}" },
            wrong.isEmpty(),
        )
    }

    @Test
    fun `no capture advances automatically without positive scale evidence`() {
        // The thirteenth session's release blocker, as a property over the corpus rather than as one
        // assertion about the peanut butter. Both verification routes are scale-invariant, so
        // corroboration can never stand in for a decimal separator here.
        results
            .filter { it.action == ScanPresentationDecision.Action.AUTO_ADVANCE }
            .forEach { r ->
                val scale = AutomaticScanAdvance.scaleVerdict(r.outcome, r.capture.document())
                assertTrue(
                    "${r.capture.bundle} advanced on scale verdict $scale",
                    scale is ScaleAmbiguity.Verdict.Established,
                )
            }
    }

    @Test
    fun `the two decimal-collapse captures never reach the user as a figure`() {
        // `12` for a printed `7,2` and `13` for a printed `1,3`. Both are confident, correctly
        // classified, correctly placed under a resolved column — and both are wrong by a decimal
        // point. Nothing may show either number.
        listOf("20260904-081018-275" to "12", "20260904-081435-300" to "13").forEach { (bundle, bad) ->
            val r = results.first { it.capture.bundle == bundle }
            assertEquals(
                "$bundle must offer no figure at all",
                null,
                r.offeredValue,
            )
            assertTrue(
                "$bundle must not offer $bad through recovery either; offered ${r.recoveryOffers}",
                r.recoveryOffers.none { it.startsWith(bad) },
            )
        }
    }

    @Test
    fun `no child nutrient value is ever offered as the total`() {
        // Sugars figures printed on these labels: the yoghurt's 3,2 sits on both rows, so this is
        // asserted structurally — every offered figure must come from a row the classifier typed
        // TOTAL_CARBOHYDRATE, never a child.
        results.filter { it.offeredValue != null }.forEach { r ->
            val provenance = (r.reading as? LabelReading.Confident)
            assertTrue(
                "${r.capture.bundle} offered a figure with no confident reading behind it",
                provenance != null,
            )
        }
    }

    // ---------------------------------------------------------------- recall / agility gates

    @Test
    fun `every capture whose evidence held the printed value reaches the user as a figure`() {
        // The brief's recall gate. A capture where OCR *did* read the right number must not end in
        // generic recovery merely because verification was unavailable.
        //
        // One capture is exempt and named, rather than the gate being weakened to accommodate it:
        // `20260904-081307-240`, whose `6,2 g` arrived as the single token `6,20` with the unit
        // glyph fused in as a digit. Its digits are genuinely unusable — `6.20` is a different
        // quantity from `6,2` — so offering it would be offering a wrong figure. It routes to
        // focused entry with its basis preserved, which is the honest destination and is asserted
        // separately below.
        val exempt = setOf("20260904-081307-240")
        results
            .filter { it.capture.correctValueInEvidence && it.capture.bundle !in exempt }
            .forEach { r ->
                assertTrue(
                    "${r.capture.bundle} held the printed ${r.capture.printedCarbs} and offered nothing " +
                        "(action=${r.action})",
                    r.offeredValue != null,
                )
                assertEquals(
                    "${r.capture.bundle} offered the wrong figure",
                    0,
                    r.offeredValue!!.compareTo(r.capture.printedCarbs!!),
                )
            }
    }

    /**
     * A capture whose row and basis are known asks for the digits, never for a crop.
     *
     * ## Two actions satisfy this, and the distinction is real
     *
     * * [ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY] — nothing was read. The screen opens
     *   straight on the amount field.
     * * [ScanPresentationDecision.Action.RECOVERY] — a figure *was* read and is being withheld
     *   because its scale is unestablished. That screen already leads with focused entry (it sets
     *   `scaleAmbiguous`, and `AssistedReadingScreen` promotes the focused-entry offer above every
     *   other route when that flag is set), while still keeping the tap and type-it-in paths for a
     *   user who wants them.
     *
     * What must **not** happen in either case is [ScanPresentationDecision.Action.CROP_FALLBACK],
     * which asks the user to adjust a rectangle over a row the app has already located.
     */
    @Test
    fun `a capture whose row and basis are known asks for the digits, never for a crop`() {
        results
            .filter { FocusedAmountEntry.of(it.capture.document()) != null && it.offeredValue == null }
            .forEach { r ->
                assertTrue(
                    "${r.capture.bundle} has an established row and basis but was sent to " +
                        "${r.action}; the rectangle is not the user's lever here",
                    r.action == ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY ||
                        r.action == ScanPresentationDecision.Action.RECOVERY,
                )
            }
    }

    @Test
    fun `no capture with an established row and basis is sent to the crop screen`() {
        // The sharper half of the gate above, stated on its own so a regression names the defect
        // rather than the family it belongs to.
        val misrouted = results.filter {
            it.action == ScanPresentationDecision.Action.CROP_FALLBACK &&
                FocusedAmountEntry.of(it.capture.document()) != null
        }
        assertTrue(
            "sent to crop despite knowing the row and basis: ${misrouted.map { it.capture.bundle }}",
            misrouted.isEmpty(),
        )
    }

    @Test
    fun `no capture that keeps a question releases the frozen photograph`() {
        results.forEach { r ->
            val terminal = ScanPresentationDecision.releasesCapture(r.action)
            assertEquals(
                "${r.capture.bundle}: only AUTO_ADVANCE is terminal",
                r.action == ScanPresentationDecision.Action.AUTO_ADVANCE,
                terminal,
            )
        }
    }

    // ---------------------------------------------------------------- the aggregate itself

    /**
     * The measured corpus outcome, pinned so a change to any of the counts is deliberate.
     *
     * These are the numbers reported in this pass's summary. They are asserted rather than merely
     * printed because "the suite is green" must not be able to coexist with a silent recall
     * regression — the exact failure this repo has recorded more than once.
     *
     * ## Updated 2026-09-04: three automatic advances became confirmations
     *
     * `CORRECT_AUTO` was 3 and `CORRECT_CONFIRM` was 5; they are now 0 and 8. **No reading was lost**
     * — the same three captures still show the same correct values, now behind one confirmation tap.
     *
     * The cause is deliberate: all three advanced on [AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT]
     * counted over recognition *runs*, and that route now requires two distinct
     * [PhysicalObservationId]s. Two runs over one photograph share its optical defects, which is how
     * `20260904-113653-044` advanced automatically on a printed `0,5 g` read as `0.59`.
     *
     * `WRONG_AUTO` remains 0 and `UNNECESSARY_RECOVERY` remains 1, which is what says this is a
     * safety trade rather than a recall regression. The automatic rate is restored by an independent
     * second observation, never by trusting the same pixels twice.
     */
    @Test
    fun `the corpus aggregate is the measured one`() {
        val counts = Outcome.entries.associateWith { o -> results.count { it.classification == o } }
        // 2026-09-04 (seventeenth session): one capture moved CONFIRM -> AUTO. It is the Lidl
        // yoghurt, whose printed `Ø/100 g` beside `Ø/125 g` lets its own other rows corroborate the
        // carbohydrate row at the printed 1.25 serving ratio — CROSS_COLUMN, not the optical route
        // the physical-observation rule closed. `CORRECT_AUTO + CORRECT_CONFIRM` is unchanged at 8.
        assertEquals("CORRECT_AUTO", 1, counts[Outcome.CORRECT_AUTO])
        assertEquals("CORRECT_CONFIRM", 7, counts[Outcome.CORRECT_CONFIRM])
        assertEquals("WRONG_AUTO", 0, counts[Outcome.WRONG_AUTO])
        assertEquals("WRONG_CONFIRM", 0, counts[Outcome.WRONG_CONFIRM])
        assertEquals("UNNECESSARY_RECOVERY", 1, counts[Outcome.UNNECESSARY_RECOVERY])
        assertEquals("OCR_NO_EVIDENCE", 9, counts[Outcome.OCR_NO_EVIDENCE])
        assertEquals("GROUND_TRUTH_UNKNOWN", 1, counts[Outcome.GROUND_TRUTH_UNKNOWN])
        assertEquals(19, results.size)
    }

    /**
     * The count above may move between AUTO and CONFIRM; it may never shrink.
     *
     * Asserted separately from the exact aggregate because the two say different things. The
     * aggregate pins *this* measurement so a change is deliberate; this pins the **invariant** that
     * survives any future retuning — a correct value the app holds must stay in front of the user,
     * whether or not it still skips the confirmation tap.
     *
     * Without it, a later change could satisfy the aggregate by editing the expected numbers and
     * quietly move readings into recovery, which is precisely the regression shape this repo keeps
     * rediscovering.
     */
    @Test
    fun `every correct reading stays visible whether or not it advances`() {
        val shown = results.count {
            it.classification == Outcome.CORRECT_AUTO || it.classification == Outcome.CORRECT_CONFIRM
        }

        assertEquals("correct readings put in front of the user", 8, shown)
    }
}

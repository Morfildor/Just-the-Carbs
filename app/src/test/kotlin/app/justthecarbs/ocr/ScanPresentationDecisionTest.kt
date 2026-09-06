package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scanner's final presentation decision, now that a JVM test can reach it.
 *
 * ## Why this file exists
 *
 * The ninth session's veto fix was correct and **reverting it failed zero JVM tests**. Not because
 * the suite was weak about the rule, but because the rule was a local `val` inside a composable that
 * binds a camera — unreachable from the JVM by construction, the same shape as the eighth session's
 * P0 (an anonymous `else` in the same file). A rule no test can reach can be silently reverted.
 *
 * [ScanPresentationDecision] is that rule as a value. These cases are the negative control the
 * previous pass could not write: reverting `mayPresentAutomatically` to `mayAdvance` fails them.
 */
class ScanPresentationDecisionTest {

    /** One capture's views, so none of them can corroborate another (2026-09-04). */
    private fun evidence(source: EvidenceSource, document: OcrDocument) = RecognitionEvidence(
        source = source,
        report = NutritionTableInterpreter.interpret(document),
        document = document,
        physicalObservation = PhysicalObservationId("ONE_CAPTURE"),
    )

    private fun sessionEvidence(passA: OcrDocument, strategyB: OcrDocument) = listOf(
        evidence(EvidenceSource.FULL_FRAME_PASS_A, passA),
        evidence(EvidenceSource.FILTERED_PASS_A, passA),
        evidence(EvidenceSource.SELECTED_REGION_OCR, strategyB),
    )

    private val unverified = AutomaticVerification.Verdict(
        route = AutomaticVerification.Route.NONE,
        rejectionReason = "test: nothing corroborated it",
    )

    // ---------------------------------------------------------------- the veto, reachable at last

    /**
     * **The ninth session's defect, as a JVM assertion.**
     *
     * A correct reading the resolver marked `NeedsVerification` must be *presented*, not discarded.
     * Reverting the veto to `mayAdvance` makes this `RECOVERY`, because `mayAdvance` answers `false`
     * for that outcome.
     */
    @Test
    fun `an uncorroborated confident reading is confirmed on the capture, not vetoed`() {
        listOf(
            NinthSessionFixtures.whiteTableFirst() to
                NinthSessionStrategyBDocuments.whiteTableUnitRecognised(),
            NinthSessionFixtures.whiteTableSecond() to
                NinthSessionStrategyBDocuments.whiteTableSecondUnitRecognised(),
            NinthSessionFixtures.greenDrinkStrategyB() to
                NinthSessionStrategyBDocuments.greenDrinkUnitRecognised(),
        ).forEach { (passA, strategyB) ->
            val ev = sessionEvidence(passA, strategyB)
            assertEquals(
                "a correct reading nothing corroborated must still reach the user",
                ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE,
                ScanPresentationDecision.decide(
                    EvidenceResolver.resolve(ev),
                    AutomaticVerification.verify(ev),
                    strategyB,
                    automatic = true,
                ),
            )
        }
    }

    /** A verified reading still skips both confirmations. */
    @Test
    fun `a verified reading advances`() {
        val document = NinthSessionFixtures.crackerAutoAdvance()
        val ev = listOf(
            evidence(EvidenceSource.FULL_FRAME_PASS_A, document),
            evidence(EvidenceSource.FILTERED_PASS_A, document),
        )
        assertEquals(
            ScanPresentationDecision.Action.AUTO_ADVANCE,
            ScanPresentationDecision.decide(
                EvidenceResolver.resolve(ev),
                AutomaticVerification.verify(ev),
                document,
                automatic = true,
            ),
        )
    }

    /**
     * The red label's `12`: confident, uncorroborated, and its scale unsupported.
     *
     * Twentieth session: it now reaches [ScanPresentationDecision.Action.CONFIRM_UNVERIFIED] rather
     * than blank `RECOVERY` typing — the row, clause, unit and column are all structurally sound and
     * undisputed, and [ConfirmationEligibility] offers it for one EXPLICIT visual comparison against
     * the photograph. It is never a one-tap shortcut: `CONFIRM_ON_CAPTURE` and `AUTO_ADVANCE` stay
     * unreachable, asserted explicitly below.
     */
    @Test
    fun `an unsupported-scale reading reaches explicit confirmation rather than a one-tap shortcut`() {
        val document = EighthSessionFixtures.redLabelTwelve()
        val ev = listOf(
            evidence(EvidenceSource.FULL_FRAME_PASS_A, document),
            evidence(EvidenceSource.FILTERED_PASS_A, document),
        )
        val decision = ScanPresentationDecision.decide(
            EvidenceResolver.resolve(ev),
            AutomaticVerification.verify(ev),
            document,
            automatic = true,
        )
        assertEquals(ScanPresentationDecision.Action.CONFIRM_UNVERIFIED, decision)
        assertTrue(
            "must never be a one-tap shortcut past the user's own comparison",
            decision != ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE &&
                decision != ScanPresentationDecision.Action.AUTO_ADVANCE,
        )
    }

    /**
     * A capture with nothing to read, **and nothing established about the label**, falls back to the
     * crop screen.
     *
     * ## Narrowed in the thirteenth pass, deliberately
     *
     * This case used to assert `CROP_FALLBACK` for all three fixtures on the ground that "nothing was
     * read here, so the rectangle is the user's lever". That ground holds for
     * [NinthSessionFixtures.smallBlueTableSparse], which resolves no carbohydrate row and no single
     * per-100 basis — there really is nothing for the app to ask about.
     *
     * It does **not** hold for the other two, and the thirteenth session measured the cost. Both
     * resolve a `PER_100_ML` column *and* locate a carbohydrate row; only the digits failed
     * (`Koolhydraten: 0.59 13`, where the printed `g` became a `9`). Sending those to the crop screen
     * asks the user to adjust a rectangle that is already correct, over a row the app has already
     * found — and the device recording shows exactly that loop. They now route to
     * [ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY], which asks for the one thing still
     * missing.
     *
     * **No safety property is weakened by the change.** Focused entry displays no value, proposes no
     * value and repairs no value: the refused digits stay refused and the human types what they can
     * read. The basis it preserves is one the label printed and the classifier resolved, which is
     * precisely the fact that must not be re-guessed.
     */
    @Test
    fun `captures with nothing established fall back to the crop screen`() {
        val ev = listOf(
            evidence(EvidenceSource.FULL_FRAME_PASS_A, NinthSessionFixtures.smallBlueTableSparse()),
            evidence(EvidenceSource.FILTERED_PASS_A, NinthSessionFixtures.smallBlueTableSparse()),
        )
        val document = NinthSessionFixtures.smallBlueTableSparse()
        // The precondition, asserted so this cannot pass for the wrong reason: there is genuinely
        // no focused-entry target here, which is what makes the crop screen the honest destination.
        assertEquals(null, FocusedAmountEntry.of(document))
        assertEquals(
            ScanPresentationDecision.Action.CROP_FALLBACK,
            ScanPresentationDecision.decide(
                EvidenceResolver.resolve(ev),
                AutomaticVerification.verify(ev),
                document,
                automatic = true,
            ),
        )
    }

    /**
     * A capture that read no value but *did* establish the row and basis asks for the digits.
     *
     * The other half of the case above. These are the two ninth-session fixtures whose destination
     * changed, and the reason is that the app already knows everything except the number.
     */
    @Test
    fun `captures with an established row and basis ask for the digits`() {
        listOf(
            NinthSessionFixtures.ingredientUnderside(),
            NinthSessionFixtures.greenDrinkNoReading(),
        ).forEach { document ->
            val ev = listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, document),
                evidence(EvidenceSource.FILTERED_PASS_A, document),
            )
            // Precondition: a target exists. Without this the assertion below could pass on a
            // fixture that had stopped resolving a basis entirely.
            assertEquals(
                app.justthecarbs.domain.NutritionBasis.PER_100_ML,
                FocusedAmountEntry.of(document)?.basis,
            )
            assertEquals(
                ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY,
                ScanPresentationDecision.decide(
                    EvidenceResolver.resolve(ev),
                    AutomaticVerification.verify(ev),
                    document,
                    automatic = true,
                ),
            )
        }
    }

    /** Through a confirmed crop a verified reading keeps its ordinary card. */
    @Test
    fun `a verified reading from a confirmed crop is an ordinary confirmation`() {
        val document = NinthSessionFixtures.crackerAutoAdvance()
        val ev = listOf(
            evidence(EvidenceSource.FULL_FRAME_PASS_A, document),
            evidence(EvidenceSource.FILTERED_PASS_A, document),
        )
        assertEquals(
            ScanPresentationDecision.Action.CONFIRM,
            ScanPresentationDecision.decide(
                EvidenceResolver.resolve(ev),
                AutomaticVerification.verify(ev),
                document,
                automatic = false,
            ),
        )
    }

    /**
     * The invariant the eighth session's P0 violated, asserted rather than described.
     *
     * Everything that still has a question for the user keeps the photograph that question is about.
     */
    @Test
    fun `only an automatic advance releases the capture`() {
        ScanPresentationDecision.Action.entries.forEach { action ->
            assertEquals(
                "only AUTO_ADVANCE is terminal",
                action == ScanPresentationDecision.Action.AUTO_ADVANCE,
                ScanPresentationDecision.releasesCapture(action),
            )
        }
        // Seven since the twentieth pass added CONFIRM_UNVERIFIED, which also keeps the photograph —
        // it is an explicit visual comparison, never a shortcut past one. The count is asserted so a
        // new action has to be considered here rather than silently inheriting a terminal/non-terminal
        // answer.
        assertEquals(7, ScanPresentationDecision.Action.entries.size)
    }

    /**
     * The decision never contradicts the gate it is built from.
     *
     * `AUTO_ADVANCE` implies `mayAdvanceVerified`; anything that keeps the photograph implies it is
     * *not* an advance. Stated as a property over every committed fixture so a future edit to either
     * side shows up here rather than on a phone.
     */
    @Test
    fun `the decision agrees with the underlying gates on every fixture`() {
        listOf(
            NinthSessionFixtures.crackerAutoAdvance(),
            NinthSessionFixtures.blueTubAutoAdvance(),
            NinthSessionFixtures.whiteTableFirst(),
            NinthSessionFixtures.greenDrinkStrategyB(),
            NinthSessionFixtures.redLabelTwelve(),
            EighthSessionFixtures.redLabelTwelve(),
            EighthSessionFixtures.drinkConfirmed(),
            EighthSessionFixtures.crackerAutoAdvance(),
            SeventhSessionFixtures.truffleSeparatorlessPair(),
            SixthSessionFixtures.sauceSharedScaleCollapse(),
            ThirdSessionFixtures.koreanSauceLinearPanel(),
        ).forEach { document ->
            val ev = listOf(
                evidence(EvidenceSource.FULL_FRAME_PASS_A, document),
                evidence(EvidenceSource.FILTERED_PASS_A, document),
            )
            val outcome = EvidenceResolver.resolve(ev)
            val verification = AutomaticVerification.verify(ev)
            val action = ScanPresentationDecision.decide(outcome, verification, document, automatic = true)

            if (action == ScanPresentationDecision.Action.AUTO_ADVANCE) {
                assertTrue(
                    "an advance must satisfy the verified gate",
                    AutomaticScanAdvance.mayAdvanceVerified(outcome, verification, document),
                )
            }
            if (action == ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE) {
                assertTrue(
                    "a confirmation must satisfy the central eligibility rule",
                    AutomaticScanAdvance.mayConfirm(outcome, verification, document),
                )
                assertFalse(
                    "and it must not be a silent acceptance",
                    AutomaticScanAdvance.mayAdvanceVerified(outcome, verification, document),
                )
            }
        }
    }

    /** An unverified reading is never proposed with no basis behind it. */
    @Test
    fun `nothing is confirmed without a basis unless something corroborated it`() {
        val document = NinthSessionFixtures.redLabelTwelve()
        val ev = listOf(
            evidence(EvidenceSource.FULL_FRAME_PASS_A, document),
            evidence(EvidenceSource.FILTERED_PASS_A, document),
        )
        val outcome = EvidenceResolver.resolve(ev)
        assertFalse(AutomaticScanAdvance.mayConfirm(outcome, unverified, document))
    }
}

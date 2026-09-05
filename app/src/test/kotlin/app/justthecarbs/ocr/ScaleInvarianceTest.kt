package app.justthecarbs.ocr

import app.justthecarbs.domain.CarbBasis
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scale-invariance invariant, stated once and applied to every corroboration route.
 *
 * ## The contradiction this closes
 *
 * [ReadingEligibility] checked `corroborated` **first** and returned `Eligible` on it outright,
 * documenting the order as load-bearing because a reading two distinct runs agree on "has had its
 * scale settled by a route that is not scale-invariant".
 *
 * That premise is false for **both** routes the app actually has, and the falsity is arithmetic
 * rather than a gap in the implementation:
 *
 * | route | what it compares | effect of multiplying every recognised value by ten |
 * |---|---|---|
 * | [AutomaticVerification.Route.CROSS_COLUMN] | this row's serving-to-per-100 ratio against the table's median | **none** — both sides scale together, the ratio is identical |
 * | [AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT] | two runs' recognised digits | **none** — a systematic separator loss repeats across runs of the same recognizer on the same package |
 *
 * So neither route can distinguish a printed `8,9` read as `89` from a genuine `89`. The eighth and
 * ninth sessions both recorded the recognizer dropping a separator from *every* value on a label,
 * which is precisely the input that makes both routes agree on the wrong scale.
 *
 * ## The invariant
 *
 * > Evidence unchanged by multiplying all recognized values by ten cannot establish absolute decimal
 * > scale.
 *
 * Each test below constructs a document and its ×10 twin, checks the corroboration route reports the
 * *same* verdict on both — that is what makes the route demonstrably scale-invariant rather than
 * assumed to be — and then asserts the eligibility decision does not rest on it.
 *
 * ## What is deliberately preserved
 *
 * Two signals are genuinely not scale-invariant and still admit a figure:
 *
 * * **A decimal separator in the candidate's own recognised token.** `8,9` and `89` are different
 *   text; a ×10 rescale of the document changes it, so it is evidence about scale.
 * * **A serving basis the label declared** ([CarbBasis.PerQuantity]). The Korean sauce's
 *   `Serv. size: 1 Tbsp (18 g)` is a sentence a human wrote and the app read back, and the `6 g` it
 *   governs is admitted on that declaration. Rescaling the document changes the declared quantity
 *   too, so this is not a scale-invariant signal either.
 *
 * No magnitude limit is used anywhere here, and none may be added: a rule refusing large numbers
 * would refuse flour and sugar while still admitting a collapsed `4,6` -> `46`.
 */
class ScaleInvarianceTest {

    private fun report(document: OcrDocument): NutritionParseReport =
        NutritionTableInterpreter.interpret(document)

    private fun confidentEvidence(
        source: EvidenceSource,
        document: OcrDocument,
    ): RecognitionEvidence = RecognitionEvidence(
        source = source,
        report = report(document),
        document = document,
    )

    private fun confidentCandidate(document: OcrDocument): CarbCandidate {
        val candidate = (report(document).reading as? LabelReading.Confident)?.candidate
        assertNotNull("precondition: the fixture must parse confidently", candidate)
        return candidate!!
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * A two-column table whose carbohydrate row prints `<carb>` and `<serving>`, plus four other
     * nutrient rows so [CrossColumnRatioCheck] has the three supporting pairs it requires.
     *
     * Every value is supplied by the caller, so the same builder produces both a correctly-separated
     * label and its uniformly decimal-collapsed twin. That is what makes the ×10 control honest: the
     * two documents differ **only** in the punctuation of their numbers, which is exactly the
     * difference a dropped separator makes.
     */
    private fun table(
        carb: String,
        carbServing: String,
        fat: String,
        fatServing: String,
        sugars: String,
        sugarsServing: String,
        fibre: String,
        fibreServing: String,
        salt: String,
        saltServing: String,
    ) = OcrDocument(
        width = 1200,
        height = 1200,
        elements = listOf(
            OcrElement("per 100 ml", OcrBox(400, 100, 640, 140), 0, 0),
            OcrElement("per portie", OcrBox(760, 100, 1000, 140), 0, 0),
            OcrElement("Vetten", OcrBox(60, 200, 300, 240), 0, 1),
            OcrElement(fat, OcrBox(430, 200, 560, 240), 0, 1),
            OcrElement(fatServing, OcrBox(790, 200, 920, 240), 0, 1),
            OcrElement("Koolhydraten", OcrBox(60, 280, 300, 320), 0, 2),
            OcrElement(carb, OcrBox(430, 280, 560, 320), 0, 2),
            OcrElement(carbServing, OcrBox(790, 280, 920, 320), 0, 2),
            OcrElement("waarvan suikers", OcrBox(80, 360, 340, 400), 0, 3),
            OcrElement(sugars, OcrBox(430, 360, 560, 400), 0, 3),
            OcrElement(sugarsServing, OcrBox(790, 360, 920, 400), 0, 3),
            OcrElement("Vezels", OcrBox(60, 440, 300, 480), 0, 4),
            OcrElement(fibre, OcrBox(430, 440, 560, 480), 0, 4),
            OcrElement(fibreServing, OcrBox(790, 440, 920, 480), 0, 4),
            OcrElement("Zout", OcrBox(60, 520, 300, 560), 0, 5),
            OcrElement(salt, OcrBox(430, 520, 560, 560), 0, 5),
            OcrElement(saltServing, OcrBox(790, 520, 920, 560), 0, 5),
        ),
    )

    /** The label as printed: every value keeps its decimal separator. */
    private fun printedTable() = table(
        carb = "8,9 g", carbServing = "1,3 g",
        fat = "2,7 g", fatServing = "0,4 g",
        sugars = "4,6 g", sugarsServing = "0,7 g",
        fibre = "1,2 g", fibreServing = "0,2 g",
        salt = "1,6 g", saltServing = "0,2 g",
    )

    /**
     * The same label with the separator dropped from **every** value — the uniform three-row collapse
     * the sixth session measured. Each number is exactly ten times the printed one.
     */
    private fun collapsedTable() = table(
        carb = "89 g", carbServing = "13 g",
        fat = "27 g", fatServing = "4 g",
        sugars = "46 g", sugarsServing = "7 g",
        fibre = "12 g", fibreServing = "2 g",
        salt = "16 g", saltServing = "2 g",
    )

    // ------------------------------------------------------------------ the routes are scale-invariant

    /**
     * The cross-column route reports the identical verdict on a label and its ×10 twin.
     *
     * This is the measurement the invariant rests on, and it is asserted rather than argued: if the
     * ratio check could tell the two apart, the eligibility rule below would be free to trust it.
     */
    @Test
    fun `the cross-column route cannot tell a label from its ten-times twin`() {
        val printed = printedTable()
        val collapsed = collapsedTable()

        val printedVerdict = AutomaticVerification.verify(printed, report(printed))
        val collapsedVerdict = AutomaticVerification.verify(collapsed, report(collapsed))

        assertEquals(
            "precondition: the printed label must verify through the cross-column route",
            AutomaticVerification.Route.CROSS_COLUMN,
            printedVerdict.route,
        )
        assertEquals(
            "the ×10 twin verifies identically — every ratio survives the rescale",
            AutomaticVerification.Route.CROSS_COLUMN,
            collapsedVerdict.route,
        )
        assertEquals(
            "the supporting-row count is identical too, so nothing here distinguishes the two",
            printedVerdict.supportingRows,
            collapsedVerdict.supportingRows,
        )
    }

    /**
     * Two distinct recognition runs repeating one systematic error agree exactly as strongly as two
     * runs reading a correct value.
     *
     * `DISTINCT_OCR_AGREEMENT` counts *runs*, which is the right question for an independent misread
     * of one digit and says nothing about a separator the recognizer dropped from the whole label on
     * both passes.
     */
    @Test
    fun `distinct runs repeating one systematic error agree just as strongly`() {
        val collapsed = collapsedTable()
        // Two genuinely distinct runs, both reading the collapsed label — which is what happens when
        // the separator is absent from the pixels rather than lost by one pass.
        val verdict = AutomaticVerification.verify(
            listOf(
                confidentEvidence(EvidenceSource.FULL_FRAME_PASS_A, collapsed),
                confidentEvidence(EvidenceSource.SELECTED_REGION_OCR, collapsed),
            ),
        )
        assertTrue(
            "precondition: the runs must corroborate each other, was $verdict",
            verdict.mayAdvanceAutomatically,
        )
    }

    // ------------------------------------------------------------------ the invariant

    /**
     * **The rule.** A separatorless pair is refused whatever corroborated it.
     *
     * This is the case the old ordering admitted: `corroborated` short-circuited to `Eligible` before
     * [ScaleAmbiguity] was consulted at all, so the truffle's `89` was confirmable the moment either
     * route reported agreement — and both routes report agreement on exactly this input.
     */
    @Test
    fun `corroboration does not bypass a demonstrated scale ambiguity`() {
        val collapsed = collapsedTable()
        val scale = ScaleAmbiguity.check(collapsed, confidentCandidate(collapsed))
        assertTrue(
            "precondition: the collapsed pair must be demonstrably ambiguous, was $scale",
            scale is ScaleAmbiguity.Verdict.Ambiguous,
        )

        listOf(true, false).forEach { corroborated ->
            val verdict = ReadingEligibility.evaluate(
                scale = scale,
                basis = CarbBasis.PerHundred(app.justthecarbs.domain.NutritionBasis.PER_100_ML),
                corroborated = corroborated,
            )
            assertFalse(
                "a separatorless pair must be refused with corroborated=$corroborated, was $verdict",
                verdict.isEligible,
            )
        }
    }

    /**
     * The same, driven through the production gate rather than the pure rule.
     *
     * [AutomaticScanAdvance.mayConfirm] is what the scanner asks, so pinning only
     * [ReadingEligibility] would leave the wiring free to pass a different `corroborated`.
     */
    @Test
    fun `the truffle collapse is never offered for confirmation however it was verified`() {
        val collapsed = collapsedTable()
        val evidence = listOf(
            confidentEvidence(EvidenceSource.FULL_FRAME_PASS_A, collapsed),
            confidentEvidence(EvidenceSource.SELECTED_REGION_OCR, collapsed),
        )
        val outcome = EvidenceResolver.resolve(evidence)
        val verification = AutomaticVerification.verify(evidence)

        assertTrue(
            "precondition: something must have verified it, or this test proves nothing",
            verification.mayAdvanceAutomatically,
        )
        assertFalse(
            "89/13 must never be offered for confirmation",
            AutomaticScanAdvance.mayConfirm(outcome, verification, collapsed),
        )
        assertFalse(
            "and it must never advance",
            AutomaticScanAdvance.mayAdvanceVerified(outcome, verification, collapsed),
        )
        assertEquals(
            "it routes to focused entry with the digits withheld",
            AutomaticScanAdvance.Presentation.Recover,
            AutomaticScanAdvance.presentation(outcome, verification, collapsed, automatic = true),
        )
    }

    /**
     * A uniform decimal collapse across every row preserves all cross-column ratios, and is refused.
     *
     * Distinct from the test above in what it measures: this one asserts the *table-level*
     * corroboration route positively supports the collapsed reading and the reading is still refused,
     * which is the precise shape the brief names.
     */
    @Test
    fun `a uniform decimal collapse preserving every ratio never advances`() {
        val collapsed = collapsedTable()
        val structural = AutomaticVerification.verify(collapsed, report(collapsed))
        assertEquals(
            "precondition: the ratios must all still line up",
            AutomaticVerification.Route.CROSS_COLUMN,
            structural.route,
        )

        val outcome = EvidenceResolver.resolve(
            listOf(confidentEvidence(EvidenceSource.FULL_FRAME_PASS_A, collapsed)),
        )
        assertFalse(
            "cross-column support is scale-invariant and must not admit the collapsed value",
            AutomaticScanAdvance.mayAdvanceVerified(outcome, structural, collapsed),
        )
        assertFalse(
            "nor may it be proposed",
            AutomaticScanAdvance.mayConfirm(outcome, structural, collapsed),
        )
    }

    // ------------------------------------------------------------------ what stays admitted

    /**
     * The printed label is untouched. Its separators survived, so its scale is stated.
     *
     * Without this the rule above would be indistinguishable from "refuse two-column labels".
     */
    @Test
    fun `a label whose separators survived is unaffected`() {
        val printed = printedTable()
        val evidence = listOf(confidentEvidence(EvidenceSource.FULL_FRAME_PASS_A, printed))
        val outcome = EvidenceResolver.resolve(evidence)
        val verification = AutomaticVerification.verify(printed, report(printed))

        val candidate = confidentCandidate(printed)
        assertEquals(
            "the printed value is read as printed",
            0,
            candidate.value.compareTo(BigDecimal("8.9")),
        )
        assertTrue(
            "a stated scale must still advance",
            AutomaticScanAdvance.mayAdvanceVerified(outcome, verification, printed),
        )
    }

    /**
     * A declared serving basis still admits a separatorless integer.
     *
     * This is the Korean sauce's `6 g / 18 g serving`. The declaration is a sentence the label
     * printed, not a column the app inferred, and a ×10 rescale of the document would change the
     * declared `18 g` too — so it is not a scale-invariant signal and remains valid evidence.
     */
    @Test
    fun `a declared serving basis still admits a separatorless integer`() {
        val verdict = ReadingEligibility.evaluate(
            scale = ScaleAmbiguity.Verdict.Unsupported("6", "no paired value"),
            basis = CarbBasis.PerQuantity(BigDecimal("18"), app.justthecarbs.domain.NutritionBasis.PER_100_G),
            corroborated = false,
        )
        assertTrue("the label declared the serving this figure is measured per", verdict.isEligible)
    }

    /**
     * **The `41` control, end to end: it still reaches the user, now through focused entry.**
     *
     * `20260902-131357-353` reads `41g` — integer-like, no separator, and correct.
     *
     * ## What changed in the thirteenth pass, and why
     *
     * This case previously asserted `AUTO_ADVANCE`, on the reasoning that only a *demonstrated*
     * ambiguity (a separatorless **pair**) need be refused, so corroboration could settle a lone
     * integer. The thirteenth session measured what that permits:
     * `20260904-081421-421` skipped both confirmations with `scale evidence: UNSUPPORTED` and
     * `automatic-verification: DISTINCT_OCR_AGREEMENT` recorded in its own bundle.
     *
     * The value there was right, and the reasoning would have admitted a collapsed `1,1` in exactly
     * the same way — because both verification routes are scale-invariant, which is the property
     * every other case in this class measures. The brief lists that state as a release blocker:
     * *"0 cross-run agreement bypassing unresolved absolute scale"*.
     *
     * ## Reversed again (2026-09-05): same-observation agreement no longer buys the one-tap card
     *
     * The intervening pass (a fourteenth-session KDoc, since superseded) let same-photograph view
     * agreement (`route = NONE`, `viewsAgree = true`) still put `41` behind a one-tap
     * `CONFIRM_ON_CAPTURE` card, on the ground that a correct reading should not cost the user an
     * extra screen. That is exactly the door the documented Hellmann's `1,3 -> 13` case reaches the
     * user through: the same evidence shape (`route = NONE`, `viewsAgree = true`) is indistinguishable
     * from this one, so a rule permissive enough to keep `41` on the confirmation card is also
     * permissive enough to put `13` there. The 2026-09-05 evidence-reliability plan closes that gap
     * directly: "Unsupported decimal scale from a single physical observation must never prefill a
     * value for one-tap acceptance. Blank focused entry is the correct outcome."
     *
     * `41` is not withheld or retyped from nothing — [ScanPresentationDecision.Action.RECOVERY] keeps
     * the frozen photograph and opens focused entry with the basis preserved
     * ([FocusedAmountEntry]), one screen further than the one-tap card but still short of a blank
     * manual-entry screen.
     *
     * **Manual-QA row 29.12 ("auto-advances") remains narrowed as before; row 30.18 ("still
     * *reaches*") still holds, one screen further than it did between the thirteenth and this pass.**
     * Both rows are unticked, so no hardware-verified behaviour is contradicted — see
     * `docs/manual-qa.md` §33.
     */
    @Test
    fun `the separatorless 41 control reaches the user through focused entry, not a one-tap confirmation`() {
        val document = OcrDocument(
            width = 1000,
            height = 1000,
            elements = listOf(
                OcrElement("per 100 ml", OcrBox(400, 100, 620, 140), 0, 0),
                OcrElement("Koolhydraten", OcrBox(60, 200, 300, 240), 0, 1),
                OcrElement("41g", OcrBox(430, 200, 500, 240), 0, 1),
            ),
        )
        val evidence = listOf(
            confidentEvidence(EvidenceSource.FULL_FRAME_PASS_A, document),
            confidentEvidence(EvidenceSource.SELECTED_REGION_OCR, document),
        )
        val outcome = EvidenceResolver.resolve(evidence)
        val verification = AutomaticVerification.verify(evidence)

        assertTrue(
            "precondition: a lone integer is Unsupported, not Ambiguous — that is the whole point",
            ScaleAmbiguity.check(document, confidentCandidate(document))
                is ScaleAmbiguity.Verdict.Unsupported,
        )
        assertTrue(
            "precondition: this evidence is same-observation agreement, route = NONE — the exact " +
                "shape the Hellmann's 1,3 -> 13 case reaches the user through",
            verification.route == AutomaticVerification.Route.NONE && verification.viewsAgree,
        )
        assertEquals(
            "same-photograph agreement alone must not prefill 41 for one-tap acceptance; it still " +
                "reaches the user, one screen further, through focused entry on the frozen photograph",
            ScanPresentationDecision.Action.RECOVERY,
            ScanPresentationDecision.decide(outcome, verification, document, automatic = true),
        )
        // The value the parser read is unaltered by the routing change.
        assertEquals(
            BigDecimal("41.0"),
            AutomaticScanAdvance.confidentReading(outcome)?.candidate?.value,
        )
    }

    /**
     * A demonstrated ambiguity still outranks a declared serving basis.
     *
     * `Ambiguous` is *positive evidence of doubt* rather than an absence of evidence, so it refuses
     * even where a declaration would otherwise admit. Pinned so the ordering cannot be "simplified"
     * into checking the basis first.
     */
    @Test
    fun `a demonstrated ambiguity outranks a declared serving basis`() {
        val verdict = ReadingEligibility.evaluate(
            scale = ScaleAmbiguity.Verdict.Ambiguous("89", "13", "a common rescaling fits"),
            basis = CarbBasis.PerQuantity(BigDecimal("18"), app.justthecarbs.domain.NutritionBasis.PER_100_G),
            corroborated = false,
        )
        assertFalse("a demonstrated ambiguity is not overridden by a declaration", verdict.isEligible)
    }
}

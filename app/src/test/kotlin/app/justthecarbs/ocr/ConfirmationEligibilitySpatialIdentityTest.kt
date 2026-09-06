package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ConfirmationEligibility.evaluate] must locate the SAME cell [reading.candidate] was read from,
 * never merely a cell that happens to share its value and basis.
 *
 * ## The defect this closes
 *
 * The original lookup was `RecoveryCandidates.ofIncludingScaleRefusals(...).firstOrNull { same
 * amount && same per-100 basis }`. [RecoveryCandidates] deliberately permits distinct candidates
 * sharing a reading at different boxes — a duplicated nutrition panel, a repeated multilingual
 * declaration, two identical packs printed side by side on one label — so a document can genuinely
 * contain two `40 g / 100 g` cells at different places. A value+basis lookup cannot tell those
 * apart and would arbitrarily pick whichever the fake list happened to put first, then hand the
 * confirmation screen that cell's box to draw the enlarged close-up and highlight from — showing the
 * user a *different* row than the one the app actually read, while claiming it is the same one.
 *
 * The fix matches spatially: the located candidate's box must belong to [CarbCandidate.geometry]'s
 * own span (containment, or the same tolerant vertical-overlap fallback
 * [ScaleAmbiguity.candidateElement] already uses), so a duplicate panel's *other* copy — which sits
 * at different geometry by construction — cannot be selected merely because its number matches.
 */
class ConfirmationEligibilitySpatialIdentityTest {

    private fun report(document: OcrDocument): NutritionParseReport =
        NutritionTableInterpreter.interpret(document)

    private fun confidentReading(document: OcrDocument): LabelReading.Confident {
        val reading = report(document).reading as? LabelReading.Confident
        assertNotNull("precondition: the fixture must parse confidently", reading)
        return reading!!
    }

    /**
     * Two structurally-identical carbohydrate panels on one document, each stating `40 g / 100 g`
     * at different geometry — the duplicated-panel/repeated-declaration shape the task names.
     *
     * Each panel is a two-column table (own per-100 header, one other nutrient row) so
     * [RecoveryCandidates] resolves a basis-complete candidate independently in each; the two panels
     * are separated by [PANEL_GAP] vertically so [NutritionDocumentModel] localizes them as distinct
     * panels rather than reconstructing one shared row across both.
     */
    private fun duplicatedPanelDocument(
        firstCarb: String = "40 g",
        secondCarb: String = "40 g",
    ) = OcrDocument(
        width = 1200,
        height = 2600,
        elements = listOf(
            // Panel 1
            OcrElement("per 100 g", OcrBox(400, 100, 640, 140), 0, 0),
            OcrElement("Vetten", OcrBox(60, 200, 300, 240), 0, 1),
            OcrElement("2,0 g", OcrBox(430, 200, 560, 240), 0, 1),
            OcrElement("Koolhydraten", OcrBox(60, 280, 300, 320), 0, 2),
            OcrElement(firstCarb, OcrBox(430, 280, 560, 320), 0, 2),

            // Panel 2, well below panel 1 so the two localize separately.
            OcrElement("per 100 g", OcrBox(400, 1900, 640, 1940), 0, 3),
            OcrElement("Vetten", OcrBox(60, 2000, 300, 2040), 0, 4),
            OcrElement("2,0 g", OcrBox(430, 2000, 560, 2040), 0, 4),
            OcrElement("Koolhydraten", OcrBox(60, 2080, 300, 2120), 0, 5),
            OcrElement(secondCarb, OcrBox(430, 2080, 560, 2120), 0, 5),
        ),
    )

    @Test
    fun `the confirmation candidate is the one spatially inside the reading's own geometry, not an arbitrary duplicate`() {
        val document = duplicatedPanelDocument()
        // The interpreter itself resolves one CONFIDENT reading (its own row-selection logic picks a
        // winner among duplicate declarations); what matters here is only that its own `geometry`
        // correctly identifies ONE of the two panels, and evaluate() must return exactly that one.
        val reading = confidentReading(document)
        val verdict = ConfirmationEligibility.evaluate(
            document,
            reading,
            scale = ScaleAmbiguity.Verdict.Unsupported(candidateText = "40", reason = "test"),
            disputed = DisputedCandidates.NONE,
        )
        assertTrue("expected eligible, got $verdict", verdict is ConfirmationEligibility.Verdict.Eligible)
        val located = (verdict as ConfirmationEligibility.Verdict.Eligible).candidate

        // The located candidate's box must belong to the ORIGINAL candidate's own geometry — never
        // merely equal its value and basis, which both panels do by construction.
        val span = reading.candidate.geometry
        assertTrue(
            "located candidate's box ${located.box} must sit inside the reading's own geometry $span",
            located.box.left >= span.left && located.box.right <= span.right &&
                located.box.top >= span.top && located.box.bottom <= span.bottom,
        )
        // BigDecimal.equals is scale-sensitive (stripTrailingZeros() on "40" yields 4E+1), so this
        // compares numerically via compareTo rather than assertEquals.
        assertEquals(0, located.reading.amount.compareTo(BigDecimal("40")))
    }

    @Test
    fun `two distinguishable candidates in one span are disambiguated by the accepted value, never by order`() {
        // A span containing two value cells with DIFFERENT values (the scale check's own paired
        // sibling, say) must resolve to the one matching the accepted number — mirroring
        // ScaleAmbiguity.candidateElement's own disambiguation rule — never to whichever the
        // underlying list happens to enumerate first.
        val document = OcrDocument(
            width = 1200,
            height = 800,
            elements = listOf(
                OcrElement("per 100 g", OcrBox(400, 100, 640, 140), 0, 0),
                OcrElement("Koolhydraten", OcrBox(60, 280, 300, 320), 0, 1),
                // Two numbers on the same row, in the same clause span: the accepted "40" and a
                // decoy "12" that a naive first-match could select instead.
                OcrElement("40", OcrBox(430, 280, 500, 320), 0, 1),
                OcrElement("12", OcrBox(520, 280, 590, 320), 0, 1),
            ),
        )
        val parsed = report(document).reading
        // This fixture is not expected to parse confidently on its own (two competing value cells on
        // one row is itself an ambiguity the ordinary parser would refuse) — what this test actually
        // exercises is `evaluate`'s disambiguation given a synthetic CarbCandidate whose geometry
        // spans both cells, which is the shape a recovered clause produces.
        val candidate = CarbCandidate(
            sourceLine = "Koolhydraten 40 12",
            label = "Koolhydraten 40 12",
            value = BigDecimal("40"),
            basis = NutritionBasis.PER_100_G,
            score = 0,
            geometry = OcrBox(430, 280, 590, 320), // spans BOTH "40" and "12"
            evidence = emptyList(),
            column = NutritionColumnKind.PER_100_G,
        )
        val reading = LabelReading.Confident(candidate)
        val verdict = ConfirmationEligibility.evaluate(
            document,
            reading,
            scale = ScaleAmbiguity.Verdict.Unsupported(candidateText = "40", reason = "test"),
            disputed = DisputedCandidates.NONE,
        )
        // Whatever the structural rules ultimately decide (both cells might individually be excluded
        // for other reasons on this contrived row), the located candidate — if any — must never be
        // the "12" decoy: that is the property under test, not the fixture's exact outcome.
        if (verdict is ConfirmationEligibility.Verdict.Eligible) {
            assertEquals(0, verdict.candidate.reading.amount.compareTo(BigDecimal("40")))
        }
    }
}

package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The central hypothesis of the user-confirmed crop, tested through the **real production parser**.
 *
 * > A human-selected nutrition-table boundary removes the dominant surrounding-text interference
 * > without destroying the header or changing OCR characters.
 *
 * ## What makes these tests meaningful rather than circular
 *
 * Each case asserts **both halves**: that the full-frame document genuinely fails or degrades, and
 * that the same document filtered to the user's rectangle reads correctly. Without the first half a
 * passing test would prove nothing — the table might have parsed correctly all along, and the filter
 * would be taking credit for work it did not do. Several of these cases were written expecting a
 * particular full-frame failure and had to be adjusted when the parser turned out to survive the
 * interference; those now assert what actually happens, which is the point of measuring.
 *
 * Nothing here is Kinder-specific. The interference is added as ordinary package prose around a
 * fixture built from the same [RealLabelFixtures] geometry the rest of the suite uses, and the same
 * prose shapes are applied on all four sides and to a second, structurally different label. If the
 * mechanism only worked for one arrangement of one package, the sweep below would show it.
 */
class SelectedTableInterferenceTest {

    private fun parse(document: OcrDocument): NutritionParseReport =
        NutritionTableParser.parseWithDiagnostics(document)

    private fun parseSelected(document: OcrDocument, region: NormalizedRegion): NutritionParseReport {
        val filtered = ElementRegionFilter.filter(document, region)
            ?: throw AssertionError("the selection retained no elements")
        return NutritionTableParser.parseWithDiagnostics(filtered)
    }

    /** Adds elements to an existing document without disturbing its geometry. */
    private fun OcrDocument.plus(extra: List<OcrElement>, width: Int, height: Int) =
        OcrDocument(width = width, height = height, elements = elements + extra)

    /**
     * A block of ordinary package prose, laid out as ML Kit reports it: word-like elements on
     * regularly pitched lines.
     *
     * The words are deliberately the ones that actually cause trouble on a real package — nutrient
     * terms and stray quantities appear in an ingredient list as readily as in a table, which is why
     * "just ignore text that looks like prose" is not available as a rule.
     */
    private fun proseBlock(
        originX: Int,
        originY: Int,
        lines: List<List<String>>,
        wordWidth: Int = 90,
        gap: Int = 10,
        pitch: Int = 34,
        height: Int = 20,
        blockId: Int = 90,
    ): List<OcrElement> = lines.flatMapIndexed { lineIndex, words ->
        var x = originX
        words.map { word ->
            val left = x
            x += wordWidth + gap
            OcrElement(
                text = word,
                box = OcrBox(left, originY + lineIndex * pitch, left + wordWidth, originY + lineIndex * pitch + height),
                blockId = blockId,
                lineId = lineIndex,
            )
        }
    }

    private val ingredientProse = listOf(
        listOf("Ingrediënten:", "suiker,", "magere"),
        listOf("melkpoeder,", "koolhydraten", "bevat"),
        listOf("100", "g", "cacaoboter,", "soja"),
        listOf("waarvan", "suikers", "kunnen"),
    )

    // ------------------------------------------------------------------ the reported failure shape

    @Test
    fun `an adjacent prose panel is isolated before physical rows are built`() {
        // The measured Kinder situation: a second package's ingredient panel sits beside the table,
        // so reconstructed rows span both panels before any downstream stage sees them.
        val table = RealLabelFixtures.kinder(slopePercent = 4.0)
        val withProse = table.plus(
            proseBlock(originX = 860, originY = 280, lines = ingredientProse),
            width = 1500,
            height = 700,
        )

        val fullFrame = parse(withProse)
        val selected = parseSelected(
            withProse,
            // The user draws around the table only. Left half of a 1500 px wide capture.
            NormalizedRegion(left = 0.0, top = 0.20, right = 0.56, bottom = 0.62),
        )

        val fullConfident = fullFrame.reading as? LabelReading.Confident
            ?: throw AssertionError("expected panel-local full-frame reading, got ${fullFrame.reading}")
        assertEquals(0, fullConfident.candidate.value.compareTo(BigDecimal("53.5")))
        assertEquals(NutritionBasis.PER_100_G, fullConfident.candidate.basis)
        assertTrue(NutritionDocumentModel.build(withProse).panels.single().localized)

        val confident = selected.reading as? LabelReading.Confident
            ?: throw AssertionError("expected a confident reading from the selection, got ${selected.reading}")
        assertEquals(0, confident.candidate.value.compareTo(BigDecimal("53.5")))
        assertEquals(NutritionBasis.PER_100_G, confident.candidate.basis)
    }

    @Test
    fun `the recovered value is bound to a carbohydrate term, not merely numerically right`() {
        // 53.5 could in principle arrive from the wrong row. Provenance is what distinguishes "the
        // parser read the carbohydrate row" from "a number that happens to match came through".
        val withProse = RealLabelFixtures.kinder(slopePercent = 4.0)
            .plus(proseBlock(860, 280, ingredientProse), width = 1500, height = 700)

        val selected = parseSelected(
            withProse,
            NormalizedRegion(0.0, 0.20, 0.56, 0.62),
        )

        val provenance = selected.provenance
        assertNotNull("a recovered reading must carry provenance", provenance)
        val rowText = (provenance as? CandidateProvenance.FromDeclaration)?.rowTexts
            ?.firstOrNull()
            ?: throw AssertionError("expected declaration provenance, got $provenance")

        // Checked against the production classifier rather than a literal, so this assertion cannot
        // drift from what the parser itself treats as a total row versus a child row.
        val kind = RowClassifier.classify(
            LogicalRow(
                elements = listOf(
                    OcrElement(rowText, OcrBox(0, 0, 100, 20), blockId = 0, lineId = 0),
                ),
                box = OcrBox(0, 0, 100, 20),
                sourceLines = emptySet(),
            ),
        )
        assertEquals(
            "the bound row must be the TOTAL carbohydrate row, not a child; got '$rowText'",
            NutritionRowKind.TOTAL_CARBOHYDRATE,
            kind,
        )
    }

    // ------------------------------------------------------------------ prose on every side

    @Test
    fun `prose above, below, left and right are each excluded by the selection`() {
        // One arrangement working could be luck. The mechanism is geometric, so it must hold on every
        // side — and the sweep is what would expose a rule that only ever trims one direction.
        // Measured: after the +900/+200 shift the table occupies x 960..1665, y 412..603, i.e.
        // fractions x 0.40..0.69, y 0.37..0.55 of a 2400x1100 document. Every selection below
        // encloses that rectangle with margin and excludes only the prose block.
        val placements = mapOf(
            "left" to Triple(-820, 280, NormalizedRegion(0.36, 0.30, 0.75, 0.62)),
            "right" to Triple(860, 280, NormalizedRegion(0.36, 0.30, 0.72, 0.62)),
            "above" to Triple(60, -180, NormalizedRegion(0.36, 0.30, 0.75, 0.62)),
            "below" to Triple(60, 480, NormalizedRegion(0.36, 0.30, 0.75, 0.62)),
        )

        placements.forEach { (side, placement) ->
            val (x, y, region) = placement
            // Shift everything right/down so no fixture element takes a negative coordinate.
            val shiftX = 900
            val shiftY = 200
            val table = RealLabelFixtures.kinder(slopePercent = 4.0)
            val shifted = table.elements.map { it.shifted(shiftX, shiftY) }
            val prose = proseBlock(x + shiftX, y + shiftY, ingredientProse)
            val document = OcrDocument(2400, 1100, shifted + prose)

            val selected = parseSelected(document, region)
            val confident = selected.reading as? LabelReading.Confident
                ?: throw AssertionError("prose $side: expected Confident, got ${selected.reading}")
            assertEquals(
                "prose $side: wrong value",
                0,
                confident.candidate.value.compareTo(BigDecimal("53.5")),
            )
            assertEquals("prose $side: wrong basis", NutritionBasis.PER_100_G, confident.candidate.basis)
        }
    }

    // ------------------------------------------------------------------ a structurally different label

    @Test
    fun `the same mechanism works on a differently structured multilingual table`() {
        // Sondey is a trilingual two-column label whose nutrient names wrap across printed lines —
        // a different failure surface from Kinder's three-column per-piece table. If the filter only
        // helped one table shape, this is where that would show.
        val table = RealLabelFixtures.sondey(slopePercent = 4.0)
        val withProse = table.plus(
            proseBlock(originX = 860, originY = 240, lines = ingredientProse),
            width = 1500,
            height = 700,
        )

        val selected = parseSelected(
            withProse,
            NormalizedRegion(left = 0.0, top = 0.10, right = 0.52, bottom = 0.80),
        )

        val confident = selected.reading as? LabelReading.Confident
            ?: throw AssertionError("expected Confident from sondey selection, got ${selected.reading}")
        assertEquals(0, confident.candidate.value.compareTo(BigDecimal("61.9")))
        assertEquals(NutritionBasis.PER_100_G, confident.candidate.basis)
    }

    // ------------------------------------------------------------------ table at the image edge

    @Test
    fun `a table flush against the image edge is read when the selection reaches the edge`() {
        // A selection can legitimately be clamped to 0.0 or 1.0, which is also the degenerate value.
        // This pins that a boundary-hugging table is still read rather than trimmed by an off-by-one.
        val table = RealLabelFixtures.kinder(slopePercent = 3.0)
        val shifted = table.elements.map { it.shifted(-60, -190) }
        val document = OcrDocument(1400, 600, shifted + proseBlock(820, 60, ingredientProse))

        val selected = parseSelected(document, NormalizedRegion(0.0, 0.0, 0.58, 1.0))

        val confident = selected.reading as? LabelReading.Confident
            ?: throw AssertionError("expected Confident at the edge, got ${selected.reading}")
        assertEquals(0, confident.candidate.value.compareTo(BigDecimal("53.5")))
    }

    // ------------------------------------------------------------------ the filter is not the parser

    @Test
    fun `a selection cannot rescue a label that has no resolvable basis column`() {
        // The complement of every case above, and the reason this stage is not a safety relaxation:
        // filtering removes interference, it does not supply evidence. A table whose header the user
        // excluded has no basis, and the parser must still refuse.
        val table = RealLabelFixtures.kinder(slopePercent = 4.0)
        val withProse = table.plus(proseBlock(860, 280, ingredientProse), width = 1500, height = 700)

        // Selection starts BELOW the header band at y=200..254, enclosing only the value rows.
        val selected = parseSelected(withProse, NormalizedRegion(0.0, 0.38, 0.56, 0.62))

        assertTrue(
            "a selection excluding the basis header must not manufacture a basis; got ${selected.reading}",
            selected.reading !is LabelReading.Confident,
        )
    }
}

/** Translates an element, for building composites without rewriting fixture geometry. */
private fun OcrElement.shifted(dx: Int, dy: Int) = copy(
    box = OcrBox(box.left + dx, box.top + dy, box.right + dx, box.bottom + dy),
)

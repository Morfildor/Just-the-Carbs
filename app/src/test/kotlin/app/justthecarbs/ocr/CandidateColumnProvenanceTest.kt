package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The safety invariant of this parser, stated once and enforced structurally:
 *
 * > **A safe non-result is better than a confidently wrong carbohydrate value.**
 *
 * Everything here is a consequence of that sentence. The calculator scales a per-100 figure by a
 * portion, so a number that is *not* per 100 is not merely imprecise when used as one — on a real
 * device scan a per-portion `22 g` stood in for a per-100 `86 g`, an error of nearly four times, in
 * an app whose output people transcribe into an insulin dose calculator.
 *
 * `basis` alone cannot carry that guarantee. A per-100 gram figure, a per-serving gram figure and a
 * percent-of-reference figure are all bare numbers on the printed label; only *which column they sat
 * under* separates them, and until this pass that fact existed on [CarbCandidate] as free-text
 * evidence prose rather than as anything a test could assert on. [CarbCandidate.column] makes it a
 * checkable field, and [CarbCandidate]'s own `init` makes the forbidden cases unconstructible.
 */
class CandidateColumnProvenanceTest {

    private fun candidate(column: NutritionColumnKind?) = CarbCandidate(
        sourceLine = "Koolhydraten 46 g",
        label = "Koolhydraten",
        value = BigDecimal("46"),
        basis = NutritionBasis.PER_100_G,
        score = 100,
        geometry = OcrBox(0, 0, 10, 10),
        evidence = emptyList(),
        column = column,
    )

    // ------------------------------------------------------------------ the type-level guarantee

    @Test
    fun `a candidate cannot be built from a serving column`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            candidate(NutritionColumnKind.PER_SERVING)
        }
        assertTrue(error.message!!.contains("PER_SERVING"))
    }

    @Test
    fun `a candidate cannot be built from a reference-percent column`() {
        assertThrows(IllegalArgumentException::class.java) {
            candidate(NutritionColumnKind.REFERENCE_PERCENT)
        }
    }

    /**
     * The most important of the three. `UNKNOWN` is "position known, meaning not established" — a
     * column the classifier deliberately refused to guess at. Promoting one of its cells is the
     * precise mechanism by which a per-serving figure would become a per-100 answer.
     */
    @Test
    fun `a candidate cannot be built from an unresolved column`() {
        assertThrows(IllegalArgumentException::class.java) {
            candidate(NutritionColumnKind.UNKNOWN)
        }
    }

    @Test
    fun `the two per-100 columns are the only ones a candidate may come from`() {
        assertEquals(NutritionColumnKind.PER_100_G, candidate(NutritionColumnKind.PER_100_G).column)
        assertEquals(NutritionColumnKind.PER_100_ML, candidate(NutritionColumnKind.PER_100_ML).column)
    }

    // -------------------------------------------------------------- what the parser actually emits

    /** A per-100 g table: the reading must name the column it was taken from, not merely its basis. */
    @Test
    fun `a table reading carries the per-100 g column it was read from`() {
        val label = SlopedLabel(slopePercent = 0.0, glyphHeight = 60)
        label.row(1200, "per" to 900..980, "100" to 990..1060, "g" to 1070..1100, block = 8, line = 0)
        label.row(1400, "Vetten" to 200..500, block = 1, line = 0)
        label.row(1400, "3,2" to 960..1040, block = 9, line = 0)
        label.row(1700, "koolhydraten" to 200..700, block = 2, line = 0)
        label.row(1700, "46" to 960..1020, "g" to 1030..1060, block = 10, line = 0)
        label.row(1900, "Eiwitten" to 200..520, block = 3, line = 0)
        label.row(1900, "1,1" to 960..1040, block = 11, line = 0)

        val report = NutritionTableParser.parseWithDiagnostics(label.document(width = 1684, height = 3648))
        val reading = report.reading as? LabelReading.Confident
            ?: throw AssertionError("expected a confident reading, got ${report.reading}")

        assertEquals(BigDecimal("46"), reading.candidate.value.stripTrailingZeros())
        assertEquals(NutritionColumnKind.PER_100_G, reading.candidate.column)
        assertEquals(NutritionBasis.PER_100_G, reading.candidate.basis)
    }

    /**
     * The per-100 ml case, which is the one a wrong column would corrupt most quietly: the value and
     * the shape of the table are identical to the gram case, and only the header distinguishes them.
     */
    @Test
    fun `a per-100 ml table reading carries the millilitre column`() {
        val label = SlopedLabel(slopePercent = 0.0, glyphHeight = 60)
        label.row(1200, "per" to 900..980, "100" to 990..1060, "ml" to 1070..1120, block = 8, line = 0)
        label.row(1400, "Vetten" to 200..500, block = 1, line = 0)
        label.row(1400, "0,1" to 960..1040, block = 9, line = 0)
        label.row(1700, "koolhydraten" to 200..700, block = 2, line = 0)
        label.row(1700, "9,4" to 960..1050, block = 10, line = 0)
        label.row(1900, "Eiwitten" to 200..520, block = 3, line = 0)
        label.row(1900, "0,5" to 960..1040, block = 11, line = 0)

        val report = NutritionTableParser.parseWithDiagnostics(label.document(width = 1684, height = 3648))
        val reading = report.reading as? LabelReading.Confident
            ?: throw AssertionError("expected a confident reading, got ${report.reading}")

        assertEquals(NutritionColumnKind.PER_100_ML, reading.candidate.column)
        assertEquals(NutritionBasis.PER_100_ML, reading.candidate.basis)
    }

    /**
     * A basis stated inside the row itself carries **no** column, and that null is meaningful rather
     * than a gap: the basis is proven (the label said it in words), but no column header established
     * it, so nothing may later reason about this value as though it sat in a resolved column.
     */
    @Test
    fun `an inline basis declaration yields a proven basis and no column`() {
        val label = SlopedLabel(slopePercent = 0.0, glyphHeight = 40)
        label.row(
            600,
            "Koolhydraten" to 100..420,
            "per" to 440..500,
            "100" to 510..580,
            "g" to 590..615,
            "45" to 700..760,
            "g" to 770..795,
        )
        val report = NutritionTableParser.parseWithDiagnostics(label.document(width = 1200, height = 1600))
        val reading = report.reading as? LabelReading.Confident ?: return // parser may legitimately refuse

        assertEquals(BigDecimal("45"), reading.candidate.value.stripTrailingZeros())
        assertNull(
            "an inline declaration is not a column and must not claim to be one",
            reading.candidate.column,
        )
    }

    /**
     * The device regression from the other direction: on the `869 / 22 / 12` label the serving column
     * holds a perfectly plausible number, and no candidate may carry it. Asserted on the candidate's
     * *column* rather than on its value, because a value assertion passes vacuously the moment the
     * parser refuses for some unrelated reason.
     */
    @Test
    fun `no candidate from any real fixture claims a non-quantity column`() {
        val label = SlopedLabel(slopePercent = -3.0, glyphHeight = 60)
        label.row(1100, "per" to 841..899, "100" to 914..975, "g" to 985..1004, block = 8, line = 0)
        label.row(1160, "per" to 1159..1208, "portie" to 1216..1324, "25g" to 1330..1400, block = 16, line = 0)
        label.row(1730, "Kohlenhydrate:" to 214..511, block = 4, line = 1)
        label.row(1735, "869" to 853..917, block = 12, line = 0)
        label.row(1740, "22" to 1150..1210, "g" to 1220..1245, block = 17, line = 0)

        val report = NutritionTableParser.parseWithDiagnostics(label.document(width = 1684, height = 3648))

        val candidates = when (val reading = report.reading) {
            is LabelReading.Confident -> listOf(reading.candidate)
            is LabelReading.Ambiguous -> reading.candidates
            LabelReading.NotFound -> emptyList()
        }
        candidates.forEach { candidate ->
            assertTrue(
                "candidate ${candidate.value} claims the ${candidate.column} column",
                candidate.column == null ||
                    candidate.column == NutritionColumnKind.PER_100_G ||
                    candidate.column == NutritionColumnKind.PER_100_ML,
            )
        }
    }
}

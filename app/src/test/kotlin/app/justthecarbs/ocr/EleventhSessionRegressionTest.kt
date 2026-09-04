package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The eleventh session's defects, each pinned so it cannot return silently.
 *
 * `docs/Scan Evidence 03-09 2nd test/` — twelve captures on a Samsung SM-S928B against a build
 * carrying the tenth pass. **No wrong value reached the user through any route**, including a
 * recurrence of the eighth session's `12` which was refused as a cross-run dispute. What the session
 * exposed instead was the opposite failure, in two shapes.
 */
class EleventhSessionRegressionTest {

    // ------------------------------------------------------------------------------------------
    // The fused connective, and the per-45 g figure that wore a per-100 g basis
    // ------------------------------------------------------------------------------------------

    /**
     * **The release-relevant one.** `30,2 g` is printed under `per 45g` and must never be offered as
     * a per-100 g figure.
     *
     * A user dosing from `30,2 g / 100 g` on a 45 g gel would be reading a figure 2.2x adrift of the
     * label in the safe direction and 1.5x in the unsafe one, depending which way they applied it —
     * and nothing on screen distinguishes it from the correct `67,0`.
     */
    @Test
    fun `the per-45 gram figure is never offered under a per-100 gram basis`() {
        val report = NutritionTableInterpreter.interpret(
            EleventhSessionFixtures.gelFusedConnectiveHeader(),
        )

        val offered: List<BigDecimal> = when (val reading = report.reading) {
            is LabelReading.Confident -> listOf(reading.candidate.value)
            is LabelReading.Ambiguous -> reading.candidates.map { it.value }
            LabelReading.NotFound -> emptyList()
        }

        assertTrue(
            "30.2 is the per-45-g cell and must not be offered as a per-100 figure; offered $offered",
            offered.none { it.compareTo(BigDecimal("30.2")) == 0 },
        )
    }

    /**
     * The 45 g column exists as its own column, and its meaning is `UNKNOWN`.
     *
     * `UNKNOWN` rather than a per-45-g basis is the whole point: [NutritionBasis] has two members and
     * neither means "per 45 g", so the honest outcome is a position whose meaning was not
     * established — and a cell in an `UNKNOWN` column is never used for any figure. This is the same
     * rule the third session added for `per 250 ml`, reached through the fused-connective spelling.
     */
    @Test
    fun `the fused connective header still yields its own column`() {
        val document = EleventhSessionFixtures.gelFusedConnectiveHeader()
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)

        assertTrue(
            "expected a second column for 'per45 g'; got " +
                columns.joinToString { "${it.kind}@${it.centerX}" },
            columns.size >= 2,
        )
        val offBasis = columns.filter { it.centerX > 1280 }
        assertTrue(
            "the 45 g column must not claim a per-100 basis; got " +
                offBasis.joinToString { "${it.kind}@${it.centerX}" },
            offBasis.isNotEmpty() && offBasis.all { it.kind == NutritionColumnKind.UNKNOWN },
        )
    }

    /**
     * Separating the columns is what lets the printed per-100 figure be read at all — measured on
     * the same geometry with the carbohydrate row **not** merged into the sugars clause.
     *
     * Two independent things went wrong on this label and only one of them is the column. This
     * fixture isolates the column half: same header band, same cells, with the sugars clause moved
     * to its own printed row as most packages print it. Before the fused-connective fix this read
     * `Ambiguous [67.0, 30.2]` — the per-45-g figure offered under a per-100-g basis. After it, the
     * `30,2` sits in an `UNKNOWN` column and cannot be offered, and `67,0` reads confidently.
     *
     * The second half — the merged row — is pinned by the two cases below, which assert the honest
     * outcome rather than a value the parser cannot safely produce.
     */
    @Test
    fun `separating the columns recovers the printed per-100 gram figure`() {
        val report = NutritionTableInterpreter.interpret(
            EleventhSessionFixtures.gelUnmergedCarbohydrateRow(),
        )
        val reading = report.reading
        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        assertEquals(
            0,
            BigDecimal("67.0").compareTo((reading as LabelReading.Confident).candidate.value),
        )
        assertEquals(NutritionBasis.PER_100_G, reading.candidate.basis)
    }

    /**
     * The merged row is still refused, and that refusal is **not** what this pass relaxes.
     *
     * On the capture as recognised, `Koolhydraten` and `waarvan suikers` land on one reconstructed
     * row with both values printed to the right of the sugars term. So the carbohydrate segment
     * carries no value of its own and [NutrientRowSegments]'s merged-row guard discards the row —
     * the same guard that stops a Croatian `od kojih šećeri` merged with a German `Kohlenhydrate`
     * being read as a total.
     *
     * Relaxing it to rescue this label would reintroduce sugars-as-total, which is the worst
     * outcome this app can produce. The reading stays refused; what changes is that the user is now
     * given somewhere to go, which the next case pins.
     */
    @Test
    fun `the merged carbohydrate row is still refused`() {
        val report = NutritionTableInterpreter.interpret(
            EleventhSessionFixtures.gelFusedConnectiveHeader(),
        )
        assertEquals(
            "the merged row must not yield a reading",
            LabelReading.NotFound,
            report.reading,
        )
    }

    /**
     * The merged row still establishes a focused-entry target, so the label is not a dead end.
     *
     * This is the outcome the column fix buys: without a resolved per-100 column
     * [FocusedAmountEntry] returns null and the screen can only offer retake or full manual entry.
     * With it, the app states the row it found and the basis the label printed, and asks for the one
     * number it could not read — which is exactly what it is entitled to ask.
     */
    @Test
    fun `the merged carbohydrate row still reaches focused entry with the printed basis`() {
        val target = FocusedAmountEntry.of(EleventhSessionFixtures.gelFusedConnectiveHeader())

        assertNotNull("the column fix must leave a focused-entry target", target)
        assertEquals(NutritionBasis.PER_100_G, target!!.basis)
        assertTrue(
            "the target row must be the carbohydrate row, got '${target.rowText}'",
            target.rowText.lowercase().contains("koolhydraten"),
        )
    }

    /**
     * Recovery may not offer the `30,2` either.
     *
     * The automatic path and the tap surface are two doors to the same figure, and the tenth pass's
     * whole argument is that a tap establishes *which row* and never *what a cell is measured per*.
     * A cell in an `UNKNOWN` column has no honest label, so it is not offered at all.
     */
    @Test
    fun `recovery never offers the per-45 gram cell`() {
        val document = EleventhSessionFixtures.gelFusedConnectiveHeader()
        val candidates = RecoveryCandidates.of(document, DisputedCandidates.NONE)

        assertTrue(
            "recovery must not offer 30.2; offered " +
                candidates.joinToString { "${it.reading.amount}/${it.reading.basis}" },
            candidates.none { it.reading.amount.compareTo(BigDecimal("30.2")) == 0 },
        )
    }

    // ------------------------------------------------------------------------------------------
    // The per-100 header that lost its `1` to a lowercase `l`
    // ------------------------------------------------------------------------------------------

    /**
     * `per l00 g` still heads a per-100 column, and the printed figure reads.
     *
     * `20260903-143036-432` resolved **zero** columns because one glyph came back as a letter, and
     * everything downstream rests on the header: no column, no basis, no reading, and
     * [FocusedAmountEntry] returns null so even the escape hatch is unavailable. The label was a
     * total dead end for the sake of a single character.
     *
     * `l` for `1` is the narrowest possible instance of a class this repo already tracks — `(g)` as
     * `(9)`, `Ø` as `o`, `1` as `l`. It is admitted **only** inside a quantity that is otherwise
     * exactly `100` and only where a basis unit follows, so it can never turn an ordinary word into
     * a number.
     */
    @Test
    fun `a per-100 header misread with a letter ell still resolves its column`() {
        val document = EleventhSessionFixtures.gelHeaderWithLetterEllForOne()
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)

        val perHundred = columns.filter { it.kind == NutritionColumnKind.PER_100_G }
        assertTrue(
            "expected a PER_100_G column from 'per l00 g'; got " +
                columns.joinToString { "${it.kind}@${it.centerX}" },
            perHundred.isNotEmpty(),
        )
    }

    /**
     * And the value under it reads confidently, at the printed figure.
     *
     * The column existing is not the point on its own — this asserts the reading the user gets.
     */
    @Test
    fun `the letter-ell header still yields the printed per-100 gram figure`() {
        val report = NutritionTableInterpreter.interpret(
            EleventhSessionFixtures.gelHeaderWithLetterEllForOne(),
        )
        val reading = report.reading
        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        assertEquals(
            0,
            BigDecimal("67.0").compareTo((reading as LabelReading.Confident).candidate.value),
        )
        assertEquals(NutritionBasis.PER_100_G, reading.candidate.basis)
    }

    /**
     * The repair is confined to a quantity, and never rewrites a word.
     *
     * `lood` (Dutch for lead) and `ml` must not become numbers, and a bare `l00` with no unit after
     * it heads no column — the unit is what makes the token a basis statement rather than a stray
     * character sequence. Without this the rule would be a general letter-to-digit substitution,
     * which is precisely the kind of repair this repo has refused before: a wrong repair is
     * invisible, because the user sees a plausible number and has no reason to check it.
     */
    @Test
    fun `the letter-ell allowance never turns an ordinary word into a quantity`() {
        val document = OcrDocument(
            width = 1684,
            height = 3648,
            elements = listOf(
                OcrElement("Bevat", OcrBox(200, 1400, 340, 1470), 0, 0),
                OcrElement("lood", OcrBox(350, 1400, 470, 1470), 0, 0),
                OcrElement("en", OcrBox(480, 1400, 540, 1470), 0, 0),
                OcrElement("l00", OcrBox(550, 1400, 660, 1470), 0, 0),
                OcrElement("stuks", OcrBox(670, 1400, 800, 1470), 0, 0),
            ),
        )
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)

        assertTrue(
            "no per-100 column may come from prose; got " +
                columns.joinToString { "${it.kind}@${it.centerX}" },
            columns.none {
                it.kind == NutritionColumnKind.PER_100_G || it.kind == NutritionColumnKind.PER_100_ML
            },
        )
    }

    // ------------------------------------------------------------------------------------------
    // The withheld cracker reading, and the route out of it
    // ------------------------------------------------------------------------------------------

    /**
     * The cracker's `72g` stays withheld — this fix must not become a way to show it.
     *
     * `20260903-142926-419` is a lone separatorless integer under an inferred per-hundred basis with
     * nothing corroborating it, which is precisely the red label's `12` shape. It is refused, and the
     * routing change below gives the user somewhere to go **without** relaxing that.
     */
    @Test
    fun `the separatorless cracker reading is still refused`() {
        val document = EleventhSessionFixtures.crackerSpanishRowSeparatorless()
        val report = NutritionTableInterpreter.interpret(document)
        val candidate = (report.reading as LabelReading.Confident).candidate

        val verdict = ReadingEligibility.evaluate(
            scale = ScaleAmbiguity.check(document, candidate),
            basis = app.justthecarbs.domain.CarbBasis.PerHundred(candidate.basis!!),
            corroborated = false,
        )
        assertTrue("expected Refused, got $verdict", verdict is ReadingEligibility.Verdict.Refused)
    }

    /**
     * Recovery does not offer it either, on any surface.
     *
     * Pins the symmetry the tenth pass established: the automatic path and the tap surface consult
     * one eligibility decision, so a figure withheld from one is withheld from both.
     */
    @Test
    fun `recovery never offers the separatorless cracker reading`() {
        val candidates = RecoveryCandidates.of(
            EleventhSessionFixtures.crackerSpanishRowSeparatorless(),
            DisputedCandidates.NONE,
        )
        assertTrue(
            "recovery must not offer 72; offered " +
                candidates.joinToString { "${it.reading.amount}/${it.reading.basis}" },
            candidates.none { it.reading.amount.compareTo(BigDecimal("72")) == 0 },
        )
    }

    /**
     * The row and the basis **are** established, which is what makes focused entry an honest offer.
     *
     * This is the fact the screen was failing to use. Its UI consequence is pinned by
     * `UnverifiedProposalLifecycleTest.aWithheldReadingOffersFocusedEntryWithoutRequiringAFailedTapFirst`;
     * this pins the domain half, so the two cannot drift apart.
     */
    @Test
    fun `a withheld cracker reading still establishes a focused-entry target`() {
        val target = FocusedAmountEntry.of(EleventhSessionFixtures.crackerSpanishRowSeparatorless())

        assertNotNull("the row and basis were established; focused entry must have a target", target)
        assertEquals(NutritionBasis.PER_100_G, target!!.basis)
    }
}

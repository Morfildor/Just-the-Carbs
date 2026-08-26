package app.justthecarbs.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Suite: RECOGNITION-TO-PARSER, on photographs of real packages.
//
// SCOPE, corrected 2026-08-17 — this class does NOT test the camera pipeline. It recognises each
// whole asset with `InputImage.fromBitmap(asset, 0)`, which bypasses `StillImageLoader` (decode, EXIF
// rotation, region handling) and `LabelAnalyzer.analyzeStill` entirely. It therefore measures how the
// parser reacts to real ML Kit output — valuable, and the reason the geometry rewrite was possible —
// but it is NOT evidence about what a user gets from the scanner.
//
// That gap hid a defect that cost BOTH canaries. The shipped still path cropped to the scan region
// before recognition, which removed the basis header band on tall labels: sondey and kinder each went
// Confident here and `NotFound` on the device, sondey reporting `rejected: 61.9: REFERENCE_PERCENT
// column`. Every case below was green throughout.
//
// **`ProductionStillPipelineTest` is the suite that measures the shipped feature.** Add production
// -path regressions there. Keep this class for parser-vs-recognition questions.
//
// Invariant here: given real recognizer output, the parser returns the total carbohydrate and refuses
// to substitute sugars, percentages or unrelated numbers.
//
// Every other OCR test in this repo starts from an OcrDocument — text and boxes a human typed into a
// fixture — so it exercises the parser's reaction to recognition that already succeeded. It cannot
// see how ML Kit actually segments a trilingual row, whether "61,9" survives as one token, or which
// boxes get merged. That gap is why a 400-test green suite coexisted with a scanner that failed on
// real packaging.
//
// THE NINE FIXTURES ARE COMMITTED AND MANDATORY (2026-08-16). A missing one FAILS. The previous
// `Assume`-based skip is precisely how a green suite coexists with a broken scanner: it reports
// success for a run that measured nothing.
//
// **Some cases below assert a NotFound.** That is deliberate and is not a lowered bar. Where ML Kit's
// recognition itself lost the evidence a reading depends on, refusing is the correct product
// behaviour — a false confident carbohydrate value is substantially worse than no value, because the
// user doses insulin from it. Each such case names the measured stage that ran out of evidence, so
// that if a future change makes it Confident, the change is examined rather than assumed to be
// progress.
class RealImageOcrTest {

    /**
     * The **instrumentation** context, not `targetContext`.
     *
     * androidTest assets are packaged into the test APK; `targetContext` is the app under test and
     * has no idea they exist. Reading from the wrong one throws `FileNotFoundException`, which the
     * previous version of this class treated as "photographs not supplied" — so the mistake did not
     * fail, it silently skipped every case and reported green.
     */
    private val testContext get() = InstrumentationRegistry.getInstrumentation().context

    /**
     * The fixture, or a test failure.
     *
     * These images are committed (2026-08-16) precisely so CI cannot skip the highest-value
     * regression tests. A missing fixture is a broken checkout, not a reason to report green.
     */
    private fun loadAsset(name: String): Bitmap {
        val stream = try {
            testContext.assets.open("ocr_real/$name")
        } catch (e: IOException) {
            throw AssertionError("ocr_real/$name is missing; it is a committed, mandatory fixture", e)
        }
        return stream.use {
            requireNotNull(BitmapFactory.decodeStream(it)) { "ocr_real/$name is present but could not be decoded" }
        }
    }

    /** Runs production's own recognizer and mapper — not a test double of either. */
    private fun documentFor(bitmap: Bitmap): OcrDocument {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val latch = CountDownLatch(1)
            var text: Text? = null
            var failure: Exception? = null
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { text = it; latch.countDown() }
                .addOnFailureListener { failure = it; latch.countDown() }
            assertTrue("ML Kit did not answer within 30s", latch.await(30, TimeUnit.SECONDS))
            failure?.let { throw it }
            return MlKitOcrMapper.toDocument(text!!, bitmap.width, bitmap.height)
        } finally {
            recognizer.close()
        }
    }

    /** Fixture name to `(document, report)`, so each case reads as one line of setup. */
    private fun parse(name: String): Pair<OcrDocument, NutritionParseReport> {
        val document = documentFor(loadAsset(name))
        return document to NutritionTableParser.parseWithDiagnostics(document)
    }

    /**
     * The diagnostics block, so a failing real image names the stage that ran out of evidence rather
     * than sending the next person to reconstruct the geometry by hand.
     */
    private fun explain(document: OcrDocument, report: NutritionParseReport): String =
        "\n" + OcrDiagnosticsReport.render(document, report)

    private fun valuesOffered(reading: LabelReading): List<BigDecimal> = when (reading) {
        is LabelReading.Confident -> listOf(reading.candidate.value)
        is LabelReading.Ambiguous -> reading.candidates.map { it.value }
        LabelReading.NotFound -> emptyList()
    }

    /** The confident candidate, or a failure naming the stage that gave up. */
    private fun confidentCandidate(
        document: OcrDocument,
        report: NutritionParseReport,
    ): CarbCandidate {
        assertTrue(
            "expected a confident reading${explain(document, report)}",
            report.reading is LabelReading.Confident,
        )
        return (report.reading as LabelReading.Confident).candidate
    }

    /** Asserts none of [forbidden] is offered as a total, whatever the reading's shape. */
    private fun assertNeverOffered(
        document: OcrDocument,
        report: NutritionParseReport,
        vararg forbidden: String,
    ) {
        val offered = valuesOffered(report.reading)
        forbidden.forEach { value ->
            assertTrue(
                "$value must never be offered as a total carbohydrate${explain(document, report)}",
                offered.none { it.compareTo(BigDecimal(value)) == 0 },
            )
        }
    }

    /**
     * Asserts the value was bound to a carbohydrate term and NOT to a child term.
     *
     * This is the whole assertion for a prose fixture whose total and sugars print the same number:
     * the value proves nothing there, only the bound term does. Checked against
     * [NutritionTerminology]'s own vocabularies rather than a hand-written word list, so the test
     * cannot drift from what the parser actually treats as a child term.
     */
    private fun assertProseSpanNamesTheTotal(
        document: OcrDocument,
        report: NutritionParseReport,
    ): CandidateProvenance.FromProseSpan {
        val provenance = report.provenance
        assertTrue(
            "expected prose-span provenance${explain(document, report)}",
            provenance is CandidateProvenance.FromProseSpan,
        )
        val span = provenance as CandidateProvenance.FromProseSpan
        val term = NutritionTerminology.normalize(span.nutrientTerm)
        assertTrue(
            "'$term' must be a carbohydrate term${explain(document, report)}",
            NutritionTerminology.carbohydrateTerms.any { NutritionTerminology.normalize(it) == term },
        )
        assertFalse(
            "'$term' must NOT be a child term — a sugars misread would pass a value-only assertion" +
                explain(document, report),
            NutritionTerminology.exclusionTerms.any { NutritionTerminology.normalize(it) == term },
        )
        return span
    }

    /**
     * Asserts the value came from a table row that names the carbohydrate term rather than the sugars
     * term. The row text is the observable proof the tabular path read the total row.
     */
    private fun assertRowNamesTheTotalNotSugars(
        document: OcrDocument,
        report: NutritionParseReport,
    ): CandidateProvenance.FromRow {
        val provenance = report.provenance
        assertTrue(
            "expected row provenance${explain(document, report)}",
            provenance is CandidateProvenance.FromRow,
        )
        val row = provenance as CandidateProvenance.FromRow
        val rowText = NutritionTerminology.normalize(row.rowText)
        assertTrue(
            "the source row must name a carbohydrate term${explain(document, report)}",
            NutritionTerminology.carbohydrateTerms.any { NutritionTerminology.normalize(it) in rowText },
        )
        return row
    }

    // ---- 1. Juice, bilingual, per 100 ml ------------------------------------------------------

    /**
     * ACCEPTED RECOGNITION-STAGE FAILURE, measured 2026-08-17.
     *
     * The package prints 9,0 g per 100 ml and the parser DOES reconstruct the total row correctly
     * (`Koolhydraten / Kohlenhydrate 9,0 g`). What is missing is the basis: ML Kit fused the German
     * header into `100mienthaltendurchschnittlich`, so no PER_100_ML column resolves and the value
     * cannot be placed on the label. Diagnostics: `rejected: 9.0: no column`.
     *
     * Refusing is correct here. The app has no density data, so a per-100-ml figure presented as
     * per-100-g would be a silently wrong number on a screen someone doses from. This asserts the
     * refusal AND that nothing was invented in its place.
     */
    @Test
    fun juiceRefusesRatherThanPlacingItsTotalOnAnUnknownBasis() {
        val (document, report) = parse(JUICE)

        assertEquals(
            "recognition lost the per-100-ml header; a reading here would be unplaceable" +
                explain(document, report),
            LabelReading.NotFound,
            report.reading,
        )
        assertTrue(
            "a refusal must offer no value at all${explain(document, report)}",
            valuesOffered(report.reading).isEmpty(),
        )
        assertNull("no provenance without a reading${explain(document, report)}", report.provenance)
    }

    // ---- 2. Grated cheese, multicolumn + %RI ---------------------------------------------------

    /**
     * ACCEPTED RECOGNITION-STAGE FAILURE, measured 2026-08-17. **This case asserts a WRONG value on
     * purpose. Read this before "fixing" it.**
     *
     * The package prints **2,0 g** per 100 g. ML Kit genuinely returns the token **`2,09`** — the
     * trailing `9` is the neighbouring column's digit welded on during recognition. Every stage
     * downstream behaves correctly given that input: the row is the total-carbohydrate row, the
     * column is PER_100_G, and 2.09 is a legitimate carbohydrate quantity, so nothing can refuse it.
     *
     * The defect is unrecoverable **at the parser**, and the repair anyone would reach for — a rule
     * that trims a digit off a value adjacent to another column — is exactly the kind of rule that
     * silently corrupts correct readings elsewhere. It is recorded here as the measured truth rather
     * than papered over. Do NOT assert 2.0, and do NOT write a digit-repair rule to reach it.
     *
     * What this case therefore protects is the surrounding behaviour: the value comes from the total
     * row and not the sugars column, and the fixture's genuinely forbidden values stay absent.
     */
    @Test
    fun gratedCheeseReportsTheDigitRecognitionActuallyProduced() {
        val (document, report) = parse(CHEESE)
        val candidate = confidentCandidate(document, report)

        assertEquals(
            "ACCEPTED recognition failure: ML Kit returns '2,09' where the package prints 2,0" +
                explain(document, report),
            BigDecimal("2.09"),
            candidate.value.stripTrailingZeros(),
        )
        assertEquals(app.justthecarbs.domain.NutritionBasis.PER_100_G, candidate.basis)
        assertRowNamesTheTotalNotSugars(document, report)
        // 0.5 is a %RI-adjacent figure and 50 is the serving weight: neither is ever a total.
        assertNeverOffered(document, report, "0.5", "50")
    }

    /**
     * The serving column is read even though its header arrives as `o/portie 50 g` — ML Kit reads the
     * package's `Ø` ("average per") as `o`. The per-serving carbohydrate figure is recovered; the
     * descriptor is not, because `o` is not a connective and the owner ruled (2026-08-17) against
     * adding a bare `o` as one — it would match far too broadly. Asserted as measured.
     */
    @Test
    fun gratedCheeseReadsItsPerServingFigureButNotItsDescriptor() {
        val (document, report) = parse(CHEESE)
        val serving = report.servingCandidate

        assertNotNull("expected a per-serving figure${explain(document, report)}", serving)
        assertEquals(
            "wrong per-serving carbohydrate${explain(document, report)}",
            BigDecimal("1"),
            serving!!.carbsPerServing.stripTrailingZeros(),
        )
        assertNull(
            "the 'o/portie' header states no parseable unit — owner ruling, left unresolved" +
                explain(document, report),
            serving.descriptor,
        )
    }

    // ---- 3. Jar, multilingual prose ------------------------------------------------------------

    /**
     * ACCEPTED PROSE-STAGE REFUSAL, measured 2026-08-17.
     *
     * The package prints 1,6 g per 100 g in a run-on multilingual sentence. Two independent things
     * block a reading, and both are safe:
     *
     * 1. The tabular path resolves no per-100 cell (`rejected: 1.6: no column`).
     * 2. The prose reader assembles **zero declarations**, because a declaration must open with a
     *    recognized basis phrase and this label's is `Naringsindhold (100g):` — a Danish noun, with
     *    no connective anywhere. `ProseNutritionReader.basisPhraseAt` requires a connective before
     *    `100 g`, so no span is ever opened and condition 1 has nothing to evaluate.
     *
     * Widening the basis phrase to bare nouns or to `(100g)` with no connective is a materially more
     * permissive change than this pass authorizes, and the same label prints **1,6 g for its sugars
     * too** — so a loosened rule that bound the wrong term would be undetectable by value. Refusing
     * is correct until the owner rules on basis-phrase vocabulary.
     */
    @Test
    fun jarProseRefusesBecauseItsBasisPhraseOpensNoDeclaration() {
        val (document, report) = parse(JAR)

        assertEquals(
            "no declaration opens on this label; a reading would be unplaceable" +
                explain(document, report),
            LabelReading.NotFound,
            report.reading,
        )
        // 20 is the saturated-fat figure this fixture used to return confidently (Task 8 closed it).
        assertNeverOffered(document, report, "20", "500")
        assertNull("no provenance without a reading${explain(document, report)}", report.provenance)
        assertNull("prose must never produce a serving${explain(document, report)}", report.servingCandidate)
    }

    // ---- 4. Cheese lid, curved prose -----------------------------------------------------------

    /**
     * ACCEPTED PROSE-STAGE REFUSAL, measured 2026-08-17. Same mechanism as the jar, different cause.
     *
     * The package prints `Kulhydrat: 3g. dont … sukkerarter: 2,5g`. The 2026-08-17 amendment (the
     * sequence spans the declaration rather than one reconstructed row) is what this label needed and
     * it is in place — the total clause and the child clause do land on different reconstructed rows
     * here. But no declaration opens: ML Kit welds the label's trilingual `Pour / Per / Pro` into the
     * single token **`PourPerlPro 100g:`**, so there is no connective and `basisPhraseAt` never fires.
     *
     * Verified by control: substituting a literal `per` into the same recognized text yields one
     * declaration and prose eligibility. So the amendment works and the blocker is upstream of it.
     *
     * **2,5 stays forbidden** — it is this label's sugars figure, and the earlier golden table wrongly
     * recorded it as the total. 19 is the saturated-fat figure the pipeline once returned confidently.
     */
    @Test
    fun cheeseLidProseRefusesAndNeverOffersItsSugarsOrFatFigure() {
        val (document, report) = parse(LID)

        assertEquals(
            "no declaration opens: 'PourPerlPro 100g:' carries no connective" +
                explain(document, report),
            LabelReading.NotFound,
            report.reading,
        )
        assertNeverOffered(document, report, "2.5", "19", "125")
        assertNull("no provenance without a reading${explain(document, report)}", report.provenance)
        assertNull("prose must never produce a serving${explain(document, report)}", report.servingCandidate)
    }

    // ---- 5. Witte kaas, single column ----------------------------------------------------------

    /**
     * ACCEPTED RECOGNITION-STAGE FAILURE, measured 2026-08-17. Same shape as the juice.
     *
     * The total row reconstructs correctly (`Koolhydraten 2,3 g`) but recognition lost the per-100
     * header, so no basis column resolves and the value cannot be placed (`rejected: 2.3: no column`).
     */
    @Test
    fun witteKaasRefusesRatherThanPlacingItsTotalOnAnUnknownBasis() {
        val (document, report) = parse(WITTE_KAAS)

        assertEquals(
            "recognition lost the per-100 header; a reading here would be unplaceable" +
                explain(document, report),
            LabelReading.NotFound,
            report.reading,
        )
        assertNeverOffered(document, report, "200")
        assertNull("no provenance without a reading${explain(document, report)}", report.provenance)
    }

    // ---- 6. Stokbrood, dense prose -------------------------------------------------------------

    /**
     * THE PROSE READER'S ONE POSITIVE CASE on the real corpus. The package prints
     * `… koolhydraten 46g, waarvan suikers 1,0 g …` as running text with a genuine `per 100 g`
     * connective, so a declaration opens and the nutrient-value-child-value sequence completes.
     *
     * Provenance is the load-bearing assertion, not the value: this label states several gram figures
     * in one sentence, and only the bound nutrient term proves 46 was the carbohydrate rather than a
     * neighbouring nutrient that happened to validate.
     */
    @Test
    fun stokbroodReadsFortySixFromProseAndNeverItsSugarsOrFibre() {
        val (document, report) = parse(STOKBROOD)
        val candidate = confidentCandidate(document, report)

        assertEquals(
            "wrong total carbohydrate${explain(document, report)}",
            BigDecimal("46"),
            candidate.value.stripTrailingZeros(),
        )
        assertEquals(app.justthecarbs.domain.NutritionBasis.PER_100_G, candidate.basis)
        assertProseSpanNamesTheTotal(document, report)
        assertNeverOffered(document, report, "1.0", "4.7", "12")
        // Per-100 only: prose never yields a serving figure.
        assertNull("prose must not produce a serving${explain(document, report)}", report.servingCandidate)
    }

    // ---- 7. Yoghurt, serving column + %RI ------------------------------------------------------

    /**
     * A readable table with a serving column. The total row merges the total and its "of which"
     * clause into one reconstructed row (`koolhydraten, waarvan 5,0 g 7,5 g`), so the value is
     * distinguished by COLUMN rather than by row: 5,0 sits under per-100 and 7,5 under per-serving.
     *
     * The per-serving column is classified but its `(150 g)` weight is not associated — it prints on
     * a separate merged row and the bare `schaaltje` did not reach the descriptor. Asserted as
     * measured; a missing serving shortcut is a convenience gap, not a safety one.
     */
    @Test
    fun yoghurtReadsFivePerHundredGramsFromTheTableNotTheServingColumn() {
        val (document, report) = parse(YOGHURT)
        val candidate = confidentCandidate(document, report)

        assertEquals(
            "wrong total carbohydrate${explain(document, report)}",
            BigDecimal("5"),
            candidate.value.stripTrailingZeros(),
        )
        assertEquals(app.justthecarbs.domain.NutritionBasis.PER_100_G, candidate.basis)
        assertRowNamesTheTotalNotSugars(document, report)
        // 7.5 is the per-serving figure and must never be presented as the per-100 total; 3.0 is a
        // %RI-adjacent figure; 150 is the serving weight.
        assertNeverOffered(document, report, "7.5", "3.0", "150")
    }

    // ---- 8. Sondey ------------------------------------------------------------------------------

    @Test
    fun sondeyReadsSixtyOnePointNinePerHundredGrams() {
        val (document, report) = parse(SONDEY)
        val candidate = confidentCandidate(document, report)

        assertEquals(
            "wrong total carbohydrate${explain(document, report)}",
            BigDecimal("61.9"),
            candidate.value.stripTrailingZeros(),
        )
        assertEquals(app.justthecarbs.domain.NutritionBasis.PER_100_G, candidate.basis)
        assertRowNamesTheTotalNotSugars(document, report)
    }

    @Test
    fun sondeyNeverOffersItsSugarsFigure() {
        val (document, report) = parse(SONDEY)
        assertNeverOffered(document, report, "47.6")
    }

    // ---- 9. Kinder ------------------------------------------------------------------------------

    @Test
    fun kinderReadsFiftyThreePointFivePerHundredGrams() {
        val (document, report) = parse(KINDER)
        val candidate = confidentCandidate(document, report)

        assertEquals(
            "wrong total carbohydrate${explain(document, report)}",
            BigDecimal("53.5"),
            candidate.value.stripTrailingZeros(),
        )
        assertEquals(app.justthecarbs.domain.NutritionBasis.PER_100_G, candidate.basis)
        assertRowNamesTheTotalNotSugars(document, report)
    }

    @Test
    fun kinderNeverOffersAPercentageOrItsSugarsFigure() {
        val (document, report) = parse(KINDER)
        assertNeverOffered(document, report, "3", "7", "53.3")
    }

    @Test
    fun kinderReadsItsPerPieceRelationshipWhenRecognitionSupportsIt() {
        val (document, report) = parse(KINDER)
        val serving = report.servingCandidate

        assertNotNull("no per-serving figure was read${explain(document, report)}", serving)
        assertEquals(
            "wrong per-piece carbohydrate${explain(document, report)}",
            BigDecimal("6.7"),
            serving!!.carbsPerServing.stripTrailingZeros(),
        )
        assertEquals(
            app.justthecarbs.domain.PortionUnitKind.PIECE,
            serving.descriptor?.kind,
        )
        // The printed piece weight is adopted only when the table's own arithmetic corroborates it
        // (53.5 x 12.5 / 100 = 6.6875, printed 6.7). If recognition lost the "(12.5 g)" line the
        // descriptor legitimately carries no weight and the direct-carbs path is used instead — so
        // this asserts the value only when a weight is present, never that one must be.
        serving.descriptor?.weightOrVolume?.let { weight ->
            assertEquals(
                "an associated piece weight must be the printed one${explain(document, report)}",
                BigDecimal("12.5"),
                weight.amount.stripTrailingZeros(),
            )
        }
    }

    // ---- The activation gate --------------------------------------------------------------------

    /**
     * The prose reader must be unreachable for a readable table. All four fixtures below return
     * `Confident` from the tabular path, so row provenance is the observable proof the prose stage
     * was never consulted — a `FromProseSpan` on any of them would mean the second reader had started
     * answering for tables, which is the failure the two independent gates exist to prevent.
     */
    @Test
    fun theProseReaderIsNeverConsultedForAReadableTable() {
        listOf(SONDEY, KINDER, CHEESE, YOGHURT).forEach { name ->
            val (document, report) = parse(name)
            assertTrue(
                "$name must be read by the table path${explain(document, report)}",
                report.provenance is CandidateProvenance.FromRow,
            )
        }
    }

    /**
     * Every fixture is present and decodable. Without this, a checkout that lost the assets would
     * fail case-by-case with nine confusing parser errors instead of one clear message — and the
     * `AssertionError` in [loadAsset] only fires for cases that actually run.
     */
    @Test
    fun allNineFixturesArePresent() {
        listOf(JUICE, CHEESE, JAR, LID, WITTE_KAAS, STOKBROOD, YOGHURT, SONDEY, KINDER).forEach {
            assertTrue("$it decoded to an empty bitmap", loadAsset(it).width > 0)
        }
    }

    private companion object {
        const val JUICE = "real_juice_bilingual_per100ml_01.jpg"
        const val CHEESE = "real_grated_cheese_multicolumn_02.jpg"
        const val JAR = "real_jar_prose_multilingual_03.jpg"
        const val LID = "real_lid_prose_curved_04.jpg"
        const val WITTE_KAAS = "real_witte_kaas_single_column_05.jpg"
        const val STOKBROOD = "real_stokbrood_prose_dense_06.jpg"
        const val YOGHURT = "real_yoghurt_serving_column_07.jpg"
        const val SONDEY = "sondey_multilingual_100g.jpg"
        const val KINDER = "kinder_multicolumn_piece.jpg"
    }
}

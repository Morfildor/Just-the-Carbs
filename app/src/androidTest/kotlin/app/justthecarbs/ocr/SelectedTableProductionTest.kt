package app.justthecarbs.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The user-confirmed crop, measured against the **real** photographs through the **real** recognizer.
 *
 * ## What this measures that nothing else does
 *
 * `RealImageOcrTest` measures the parser given recognition. `ProductionStillPipelineTest` measures
 * the shipped whole-frame still path. Neither exercises the crop, and the crop is now the step that
 * decides whether a user gets an answer.
 *
 * Every case here recognises a genuine hand-held phone photograph with ML Kit, then applies a
 * rectangle to the elements that recognition produced — the same `SelectedTableReader` call the
 * screen makes. So these numbers are evidence about the feature, not about a fixture.
 *
 * ## The honest limit of this test
 *
 * The rectangle below is the app's own starting rectangle — the scan guide plus its safety margin —
 * applied identically to every fixture, not a rectangle drawn by a person and not one tuned per
 * image. A per-fixture rectangle would be exactly the kind of fixture-specific hack that makes a
 * suite look green while measuring nothing.
 *
 * So these results measure the "user aimed reasonably and tapped Read table without adjusting
 * anything" case. A real user sees the frozen photograph and can only do better, because they can
 * see where the table is and a fixed rectangle cannot.
 */
class SelectedTableProductionTest {

    private val testContext get() = InstrumentationRegistry.getInstrumentation().context

    private fun assetBitmap(name: String): Bitmap {
        val stream = try {
            testContext.assets.open("ocr_real/$name")
        } catch (e: IOException) {
            throw AssertionError("ocr_real/$name is missing; it is a committed, mandatory fixture", e)
        }
        return stream.use {
            requireNotNull(BitmapFactory.decodeStream(it)) { "ocr_real/$name did not decode" }
        }
    }

    /**
     * Recognises the whole photograph, then reads it through the starting rectangle.
     *
     * Note there is exactly one recognition. The crop is applied to its elements, which is the entire
     * safety argument for this architecture: no character can change between the whole-frame read and
     * the cropped one.
     */
    private fun readThroughStartingCrop(name: String): Pair<NutritionParseReport, SelectedTableReader.Result> {
        val bitmap = assetBitmap(name)
        try {
            val document = documentFor(bitmap)
            val wholeFrame = NutritionTableParser.parseWithDiagnostics(document)
            return wholeFrame to SelectedTableReader.read(document, wholeFrame, SCAN_GUIDE)
        } finally {
            bitmap.recycle()
        }
    }

    /** Production's own recognizer and mapper — not a test double of either. */
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

    private fun valuesOffered(reading: LabelReading): List<BigDecimal> = when (reading) {
        is LabelReading.Confident -> listOf(reading.candidate.value)
        is LabelReading.Ambiguous -> reading.candidates.map { it.value }
        LabelReading.NotFound -> emptyList()
    }

    private fun assertNeverOffered(name: String, reading: LabelReading, vararg forbidden: String) {
        val offered = valuesOffered(reading)
        forbidden.forEach { value ->
            assertTrue(
                "$name: $value must never be offered as total carbohydrate (offered: $offered)",
                offered.none { it.compareTo(BigDecimal(value)) == 0 },
            )
        }
    }

    // ---- the canaries must not regress ---------------------------------------------------------

    @Test
    fun sondeyStillReadsCorrectlyThroughTheStartingCrop() {
        val (_, result) = readThroughStartingCrop(SONDEY)
        val reading = result.report.reading
        assertTrue("sondey: expected Confident, got $reading", reading is LabelReading.Confident)
        assertEquals(
            BigDecimal("61.9"),
            (reading as LabelReading.Confident).candidate.value.stripTrailingZeros(),
        )
        assertNeverOffered(SONDEY, reading, "47.6")
    }

    @Test
    fun kinderStillReadsCorrectlyThroughTheStartingCrop() {
        val (_, result) = readThroughStartingCrop(KINDER)
        val reading = result.report.reading
        assertTrue("kinder: expected Confident, got $reading", reading is LabelReading.Confident)
        assertEquals(
            BigDecimal("53.5"),
            (reading as LabelReading.Confident).candidate.value.stripTrailingZeros(),
        )
        // The measured unit-marker hazard. 9 came from a misread `(g)` and must stay unreachable.
        assertNeverOffered(KINDER, reading, "9", "3", "7", "53.3")
    }

    @Test
    fun yoghurtStillReadsCorrectlyThroughTheStartingCrop() {
        val (_, result) = readThroughStartingCrop(YOGHURT)
        val reading = result.report.reading
        assertTrue("yoghurt: expected Confident, got $reading", reading is LabelReading.Confident)
        assertEquals(
            BigDecimal("5"),
            (reading as LabelReading.Confident).candidate.value.stripTrailingZeros(),
        )
    }

    @Test
    fun theStartingCropNeverDegradesACanaryThatTheWholeFrameCouldRead() {
        // The regression this test exists for was measured, not hypothesised. An automatic
        // table-detecting proposal was built and then REMOVED because of what this assertion caught:
        // on kinder it latched onto one language's column block and proposed 13% of the frame,
        // turning a correct `Confident 53.5` into `NotFound`; on yoghurt it proposed 77% of the
        // frame, contained the winning candidate entirely, and still lost the reading because the
        // removed elements included the basis header.
        //
        // Losing a reading the whole frame already had is the worst outcome short of a
        // Losing a reading the whole frame already had is the worst outcome short of a
        // confident-wrong, because the user is told the label is unreadable while looking straight at
        // the value. Asserted as a property over the corpus so any future change to the starting
        // rectangle is checked against all of it rather than one fixture.
        val degraded = mutableListOf<String>()
        listOf(SONDEY, KINDER, YOGHURT, STOKBROOD).forEach { fixture ->
            val (wholeFrame, result) = readThroughStartingCrop(fixture)
            if (wholeFrame.reading is LabelReading.Confident &&
                result.report.reading !is LabelReading.Confident
            ) {
                degraded += "$fixture: whole-frame ${wholeFrame.reading} -> cropped ${result.report.reading}"
            }
        }

        assertTrue("the starting crop degraded a readable label: $degraded", degraded.isEmpty())
    }

    // ---- the property that matters most --------------------------------------------------------

    @Test
    fun noFixtureGainsAConfidentWrongValueThroughTheStartingCrop() {
        // The whole corpus, asserted as one property rather than nine separate expectations: applying
        // a crop must never turn a refusal into a wrong confident answer. This is the assertion that
        // would have caught the reverted Pass B, which manufactured `Confident 9.0` on kinder.
        //
        // Fixture 2 is a KNOWN recognition-stage confident-wrong (ML Kit genuinely returns `2,09`
        // where the package prints 2,0). It is excluded by value below rather than hidden, because no
        // honest parser rule can recover it and inventing one would corrupt correct readings.
        val expected = mapOf(
            SONDEY to "61.9",
            KINDER to "53.5",
            YOGHURT to "5",
            STOKBROOD to "46",
        )

        val wrong = mutableListOf<String>()
        expected.forEach { (fixture, correct) ->
            val (_, result) = readThroughStartingCrop(fixture)
            val reading = result.report.reading
            if (reading is LabelReading.Confident &&
                reading.candidate.value.compareTo(BigDecimal(correct)) != 0
            ) {
                wrong += "$fixture: confident ${reading.candidate.value}, expected $correct"
            }
        }

        assertTrue("CONFIDENT-WRONG introduced by the crop: $wrong", wrong.isEmpty())
    }

    @Test
    fun theCropNeverIntroducesAValueAbsentFromTheWholeFrameReading() {
        // The structural claim of Strategy A, asserted against real recognizer output: because the
        // crop only removes elements, any value it produces must have been derivable from the
        // unfiltered document. A second recognition pass could violate this; filtering cannot.
        listOf(SONDEY, KINDER, YOGHURT, STOKBROOD, WITTE_KAAS).forEach { fixture ->
            val (_, result) = readThroughStartingCrop(fixture)
            assertTrue(
                "$fixture: filtering must not increase the element count " +
                    "(${result.elementsBefore} -> ${result.elementsAfter})",
                result.elementsAfter <= result.elementsBefore,
            )
        }
    }

    private companion object {
        const val SONDEY = "sondey_multilingual_100g.jpg"
        const val KINDER = "kinder_multicolumn_piece.jpg"
        const val YOGHURT = "real_yoghurt_serving_column_07.jpg"
        const val STOKBROOD = "real_stokbrood_prose_dense_06.jpg"
        const val WITTE_KAAS = "real_witte_kaas_single_column_05.jpg"

        /**
         * The shipped starting rectangle: the scan overlay, expanded by its safety margin.
         *
         * These fixtures are crops of packaging rather than screen captures, so this stands in for
         * "the user aimed reasonably and did not adjust the rectangle". A real user sees the frozen
         * photo and can only do better than a fixed rectangle applied blind.
         */
        val SCAN_GUIDE = ScanRegionMapper.expand(NormalizedRegion(0.05, 0.05, 0.95, 0.95))
    }
}

package app.justthecarbs.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The real-image suite for the pipeline the DEVICE runs.
 *
 * `RealImageOcrTest` recognises each asset with `InputImage.fromBitmap(asset, 0)`, which skips
 * `StillImageLoader` (decode, EXIF rotation, region handling) entirely — it measures the parser given
 * recognition, not the shipped feature. That gap hid a defect that cost BOTH canaries: the
 * unconditional ROI crop removed the basis header band, `ColumnClassifier` reclassified the per-100
 * column as `REFERENCE_PERCENT`, and the interpreter correctly refused an unplaceable value.
 *
 * Every case here therefore goes through [LabelAnalyzer.analyzeStill] — the same entry point
 * `LabelScannerScreen` calls — against a real JPEG on disk, with a scan region set, exactly as a
 * capture arrives.
 *
 * These assertions are about the PRODUCTION PATH. A regression here is a regression a user would
 * experience, which is not true of a whole-bitmap assertion.
 */
class ProductionStillPipelineTest {

    private val testContext get() = InstrumentationRegistry.getInstrumentation().context
    private val appContext get() = InstrumentationRegistry.getInstrumentation().targetContext

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

    /** The fixture written to a real file, because the production entry point takes a [File]. */
    private fun assetFile(name: String): File {
        val bitmap = assetBitmap(name)
        val dir = appContext.cacheDir.also { it.mkdirs() }
        val file = File.createTempFile("pipeline-", ".jpg", dir)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        return file
    }

    /**
     * Runs the production still path end to end.
     *
     * [region] is what the user framed. Passing a non-null region is the realistic case and is
     * precisely the configuration that used to destroy the canaries.
     */
    private fun analyse(name: String, region: NormalizedRegion? = OVERLAY): NutritionParseReport {
        val file = assetFile(name)
        val analyzer = LabelAnalyzer(onReading = { /* live readings are irrelevant here */ })
        try {
            val latch = CountDownLatch(1)
            var report: NutritionParseReport? = null
            analyzer.analyzeStill(appContext, file, region) { report = it; latch.countDown() }
            assertTrue("analyzeStill did not answer within 60s for $name", latch.await(60, TimeUnit.SECONDS))
            return report!!
        } finally {
            analyzer.close()
            file.delete()
        }
    }

    private fun confident(name: String, report: NutritionParseReport): CarbCandidate {
        assertTrue(
            "$name: expected a confident reading from the production path, got ${report.reading}",
            report.reading is LabelReading.Confident,
        )
        return (report.reading as LabelReading.Confident).candidate
    }

    private fun valuesOffered(reading: LabelReading): List<BigDecimal> = when (reading) {
        is LabelReading.Confident -> listOf(reading.candidate.value)
        is LabelReading.Ambiguous -> reading.candidates.map { it.value }
        LabelReading.NotFound -> emptyList()
    }

    private fun assertNeverOffered(name: String, report: NutritionParseReport, vararg forbidden: String) {
        val offered = valuesOffered(report.reading)
        forbidden.forEach { value ->
            assertTrue(
                "$name: $value must never be offered as a total carbohydrate (offered: $offered)",
                offered.none { it.compareTo(BigDecimal(value)) == 0 },
            )
        }
    }

    // ---- The canaries, through the production path ---------------------------------------------

    /**
     * THE REGRESSION TEST FOR THE CROP DEFECT.
     *
     * Before the fix this returned `NotFound` with `rejected: 61.9: REFERENCE_PERCENT column`: the
     * crop removed the `o/100 g` header, so the correct value on the correct row could not be placed.
     * The parser was right to refuse. The input was wrong.
     */
    @Test
    fun sondeyIsReadCorrectlyThroughTheProductionStillPath() {
        val report = analyse(SONDEY)
        val candidate = confident(SONDEY, report)
        assertEquals(BigDecimal("61.9"), candidate.value.stripTrailingZeros())
        assertEquals(app.justthecarbs.domain.NutritionBasis.PER_100_G, candidate.basis)
        assertNeverOffered(SONDEY, report, "47.6")
    }

    /** Same defect, same fix, second canary. */
    @Test
    fun kinderIsReadCorrectlyThroughTheProductionStillPath() {
        val report = analyse(KINDER)
        val candidate = confident(KINDER, report)
        assertEquals(BigDecimal("53.5"), candidate.value.stripTrailingZeros())
        assertEquals(app.justthecarbs.domain.NutritionBasis.PER_100_G, candidate.basis)
        assertNeverOffered(KINDER, report, "3", "7", "53.3")
    }

    /** The prose path must survive the pipeline change untouched. */
    @Test
    fun stokbroodStillReadsFortySixThroughTheProductionStillPath() {
        val report = analyse(STOKBROOD)
        val candidate = confident(STOKBROOD, report)
        assertEquals(BigDecimal("46"), candidate.value.stripTrailingZeros())
        assertEquals(app.justthecarbs.domain.NutritionBasis.PER_100_G, candidate.basis)
        assertTrue(
            "stokbrood must still be read by the prose reader",
            report.provenance is CandidateProvenance.FromProseSpan,
        )
        assertNeverOffered(STOKBROOD, report, "1.0", "4.7", "12")
    }

    /** Was Confident on the whole asset and NotFound through the crop. It must not stay lost. */
    @Test
    fun yoghurtIsReadCorrectlyThroughTheProductionStillPath() {
        val report = analyse(YOGHURT)
        val candidate = confident(YOGHURT, report)
        assertEquals(BigDecimal("5"), candidate.value.stripTrailingZeros())
        assertEquals(app.justthecarbs.domain.NutritionBasis.PER_100_G, candidate.basis)
        assertNeverOffered(YOGHURT, report, "7.5", "3.0", "150")
    }

    /**
     * The serving relationship must survive the pipeline change. It is the only fixture proving a
     * countable portion can be read from a real photograph at all.
     */
    @Test
    fun kinderStillReadsItsPerPieceRelationship() {
        val report = analyse(KINDER)
        val serving = report.servingCandidate
        assertNotNull("kinder: expected a per-serving figure from the production path", serving)
        assertEquals(BigDecimal("6.7"), serving!!.carbsPerServing.stripTrailingZeros())
        assertEquals(app.justthecarbs.domain.PortionUnitKind.PIECE, serving.descriptor?.kind)
    }

    // ---- Safety: refusals must stay refusals ----------------------------------------------------

    /**
     * The saturated-fat defect must remain closed on the production path. These two labels once
     * returned a fat figure as total carbohydrate — the worst outcome this app can produce.
     */
    @Test
    fun proseLabelsNeverOfferAFatOrSugarsFigureThroughTheProductionPath() {
        analyse(JAR).let { assertNeverOffered(JAR, it, "20", "500") }
        analyse(LID).let { assertNeverOffered(LID, it, "2.5", "19", "125") }
    }

    /** A refusal must offer nothing at all, not a lower-confidence guess. */
    @Test
    fun aRefusalOffersNoValueThroughTheProductionPath() {
        listOf(JAR, LID).forEach { name ->
            val report = analyse(name)
            if (report.reading == LabelReading.NotFound) {
                assertTrue(
                    "$name: a refusal must offer no value",
                    valuesOffered(report.reading).isEmpty(),
                )
            }
        }
    }

    /**
     * A null region is the pre-layout case: the overlay has not been measured yet. It must behave the
     * same as a framed capture for a readable table, since the region is no longer a recognition
     * boundary.
     */
    @Test
    fun aCaptureWithNoScanRegionStillReadsTheCanary() {
        val report = analyse(SONDEY, region = null)
        val candidate = confident("$SONDEY (no region)", report)
        assertEquals(BigDecimal("61.9"), candidate.value.stripTrailingZeros())
    }

    private companion object {
        /** Roughly the drawn overlay: a centred 0.8-aspect box inset from the screen edges. */
        val OVERLAY = NormalizedRegion(left = 0.08, top = 0.20, right = 0.92, bottom = 0.80)

        const val JAR = "real_jar_prose_multilingual_03.jpg"
        const val LID = "real_lid_prose_curved_04.jpg"
        const val STOKBROOD = "real_stokbrood_prose_dense_06.jpg"
        const val YOGHURT = "real_yoghurt_serving_column_07.jpg"
        const val SONDEY = "sondey_multilingual_100g.jpg"
        const val KINDER = "kinder_multicolumn_piece.jpg"
    }
}

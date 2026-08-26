package app.justthecarbs.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

/**
 * Measurement of the path the DEVICE actually runs, not the path the golden suite runs.
 *
 * `RealImageOcrTest` recognises each asset with `InputImage.fromBitmap(asset, 0)`. That skips
 * `StillImageLoader` (decode + EXIF rotation + ROI crop) entirely, so it measures the parser given
 * recognition, not the camera pipeline. This class runs the production still path on the same
 * fixtures and prints, per fixture:
 *
 *  - the reading from the whole asset (what the golden suite sees), and
 *  - the reading after the production crop at several simulated capture scales.
 *
 * Scale matters because the fixtures are ~1MP pre-cropped images while a real capture is 8MP cropped
 * to roughly a third of frame. Recognition of millimetre-high print is strongly scale-dependent, so a
 * fixture result at 1MP is not evidence about a device result at 8MP. This prints both.
 *
 * Asserts nothing. It exists to make the baseline honest before production code is touched.
 */
class ProductionStillPathBaselineTest {

    private val testContext get() = InstrumentationRegistry.getInstrumentation().context

    private fun asset(name: String): Bitmap =
        testContext.assets.open("ocr_real/$name").use {
            checkNotNull(BitmapFactory.decodeStream(it)) { "$name did not decode" }
        }

    private fun recognise(bitmap: Bitmap): Text {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val latch = CountDownLatch(1)
            var text: Text? = null
            var failure: Exception? = null
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { text = it; latch.countDown() }
                .addOnFailureListener { failure = it; latch.countDown() }
            check(latch.await(60, TimeUnit.SECONDS)) { "ML Kit did not answer" }
            failure?.let { throw it }
            return text!!
        } finally {
            recognizer.close()
        }
    }

    private fun readingOf(bitmap: Bitmap): NutritionParseReport {
        val text = recognise(bitmap)
        val document = MlKitOcrMapper.toDocument(text, bitmap.width, bitmap.height)
        return NutritionTableParser.parseWithDiagnostics(document)
    }

    private fun summarise(report: NutritionParseReport): String = when (val r = report.reading) {
        is LabelReading.Confident ->
            "Confident ${r.candidate.value.stripTrailingZeros().toPlainString()} ${r.candidate.basis}" +
                " via ${report.provenance?.javaClass?.simpleName}"
        is LabelReading.Ambiguous ->
            "Ambiguous " + r.candidates.joinToString { it.value.stripTrailingZeros().toPlainString() }
        LabelReading.NotFound -> "NotFound"
    }

    /**
     * Writes the fixture to a real file and runs it through [StillImageLoader], which is what the
     * camera path uses. This exercises the decode, the EXIF branch and the crop arithmetic together —
     * none of which any existing test covers with a real image.
     */
    private fun throughProductionLoader(name: String, region: NormalizedRegion?): Bitmap? {
        val source = asset(name)
        // The APP's cache dir, not the instrumentation APK's: the latter is not guaranteed to exist
        // on the device and File.createTempFile does not create parent directories.
        val cacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            .also { it.mkdirs() }
        val file = File.createTempFile("baseline-", ".jpg", cacheDir)
        file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return try {
            StillImageLoader.load(file, region?.let { ScanRegionMapper.expand(it) })
        } finally {
            file.delete()
        }
    }

    /**
     * The corpus measured through [LabelAnalyzer.analyzeStill] — the exact entry point the scanner
     * calls. This is the number that describes the shipped feature.
     */
    @Test
    fun measureEveryFixtureThroughTheRealProductionEntryPoint() {
        Log.i(TAG, "##### PRODUCTION ENTRY POINT (analyzeStill) #####")
        var correct = 0
        var wrong = 0
        var ambiguous = 0
        var notFound = 0

        FIXTURES.forEach { name ->
            val source = asset(name)
            val dir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
                .also { it.mkdirs() }
            val file = File.createTempFile("prod-", ".jpg", dir)
            file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            source.recycle()

            val analyzer = LabelAnalyzer(onReading = { })
            val latch = CountDownLatch(1)
            var report: NutritionParseReport? = null
            analyzer.analyzeStill(
                InstrumentationRegistry.getInstrumentation().targetContext,
                file,
                OVERLAY,
            ) { report = it; latch.countDown() }
            check(latch.await(60, TimeUnit.SECONDS)) { "analyzeStill timed out for $name" }
            analyzer.close()
            file.delete()

            val r = report!!
            val actual = summarise(r)
            val expected = EXPECTED[name]
            val verdict = when (val reading = r.reading) {
                is LabelReading.Confident -> {
                    val got = reading.candidate.value.stripTrailingZeros()
                    if (expected != null && got.compareTo(expected) == 0) {
                        correct++; "CORRECT"
                    } else if (expected == null) {
                        wrong++; "CONFIDENT-WRONG (nothing expected)"
                    } else {
                        wrong++; "CONFIDENT-WRONG (expected $expected)"
                    }
                }
                is LabelReading.Ambiguous -> { ambiguous++; "AMBIGUOUS" }
                LabelReading.NotFound -> { notFound++; "NOTFOUND" }
            }
            Log.i(TAG, String.format("%-40s %-12s %s", name, verdict, actual))
        }

        Log.i(TAG, "TOTALS correct=$correct confident-wrong=$wrong ambiguous=$ambiguous notFound=$notFound of ${FIXTURES.size}")
        Log.i(TAG, "##### END #####")
    }

    @Test
    fun measureWholeAssetVersusProductionCropAndScale() {
        Log.i(TAG, "##### PRODUCTION STILL PATH BASELINE #####")
        FIXTURES.forEach { name ->
            val whole = asset(name)
            Log.i(TAG, "===== $name ${whole.width}x${whole.height} =====")
            Log.i(TAG, "  whole-asset (what RealImageOcrTest measures): ${summarise(readingOf(whole))}")

            // The production loader with no region: decode + EXIF only, no crop. Isolates whether the
            // decode/rotate round trip alone changes recognition.
            throughProductionLoader(name, null)?.let {
                Log.i(TAG, "  via StillImageLoader, no crop ${it.width}x${it.height}: ${summarise(readingOf(it))}")
                it.recycle()
            }

            // The overlay is a 0.8-aspect box inset from the screen edges. This approximates a user
            // who framed the table inside it. The crop is what production sends ML Kit.
            throughProductionLoader(name, CENTRED_OVERLAY)?.let {
                Log.i(TAG, "  via StillImageLoader, overlay crop ${it.width}x${it.height}: ${summarise(readingOf(it))}")
                it.recycle()
            }

            // Scale sweep: recognition of small print is scale-sensitive, and the device feeds ML Kit
            // a far larger image than these fixtures. Downscaling shows the direction of that
            // sensitivity without pretending an upscale recovers detail that was never captured.
            listOf(0.5f, 0.75f).forEach { factor ->
                val scaled = Bitmap.createScaledBitmap(
                    whole,
                    (whole.width * factor).toInt(),
                    (whole.height * factor).toInt(),
                    true,
                )
                Log.i(TAG, "  scaled x$factor ${scaled.width}x${scaled.height}: ${summarise(readingOf(scaled))}")
                scaled.recycle()
            }
            whole.recycle()
        }
        Log.i(TAG, "##### END BASELINE #####")
    }

    private companion object {
        const val TAG = "OcrProdBaseline"

        /** Roughly the drawn overlay: a centred 0.8-aspect box inset from the screen edges. */
        val CENTRED_OVERLAY = NormalizedRegion(left = 0.08, top = 0.20, right = 0.92, bottom = 0.80)
        val OVERLAY = CENTRED_OVERLAY

        /**
         * The value each package actually PRINTS, where the corpus has established one.
         *
         * Absent entries are labels with no agreed correct reading yet; a confident value on one is
         * counted as wrong rather than quietly ignored. Grated cheese maps to 2.09 — the value
         * recognition genuinely produces at full scale — because this table measures the pipeline as
         * it is, not as it should be. See the audit's Finding 7 for why that is recoverable later.
         */
        val EXPECTED: Map<String, BigDecimal> = mapOf(
            "real_grated_cheese_multicolumn_02.jpg" to BigDecimal("2.09"),
            "real_witte_kaas_single_column_05.jpg" to BigDecimal("2.3"),
            "real_stokbrood_prose_dense_06.jpg" to BigDecimal("46"),
            "real_yoghurt_serving_column_07.jpg" to BigDecimal("5"),
            "sondey_multilingual_100g.jpg" to BigDecimal("61.9"),
            "kinder_multicolumn_piece.jpg" to BigDecimal("53.5"),
        )

        val FIXTURES = listOf(
            "real_juice_bilingual_per100ml_01.jpg",
            "real_grated_cheese_multicolumn_02.jpg",
            "real_jar_prose_multilingual_03.jpg",
            "real_lid_prose_curved_04.jpg",
            "real_witte_kaas_single_column_05.jpg",
            "real_stokbrood_prose_dense_06.jpg",
            "real_yoghurt_serving_column_07.jpg",
            "sondey_multilingual_100g.jpg",
            "kinder_multicolumn_piece.jpg",
        )
    }
}

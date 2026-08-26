package app.justthecarbs.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * MEASUREMENT ONLY — answers two questions before any production code is designed around them.
 *
 * ## Question 1: does the bundled recognizer actually populate confidence?
 *
 * `Text.Element.getConfidence()` and `Text.Symbol.getConfidence()` exist in the API
 * (`play-services-mlkit-text-recognition-common:19.1.0`, verified with `javap`). Existing in the API
 * is not the same as being populated: ML Kit has historically returned `NaN` for confidence on the
 * on-device Latin path, and a resolver that weighted a `NaN` would silently compare garbage.
 *
 * The pass spec (§7) says to retain confidence metadata. This measures whether there is any metadata
 * to retain **before** `OcrElement` grows a field and every parser test has to reason about it. If
 * confidence is `NaN` here, then "strong OCR confidence for the numeric element" (§8) is not an
 * available signal on this engine and the resolver must not pretend otherwise.
 *
 * ## Question 2: what does a native-resolution crop actually recognise?
 *
 * §3 requires cropping the *upright source pixels* with no resize and recognising those. The historic
 * hazard is that re-recognition manufactured `(g)` -> `(9)`. That failure involved **rescaling**.
 * This measures the no-resize case specifically, per fixture, so the evidence resolver is calibrated
 * against what actually happens rather than against the rescaled experiment's reputation.
 *
 * Nothing here asserts a product outcome; it prints a table. Read it, then design.
 */
class RecognizerCapabilityProbeTest {

    private val testContext get() = InstrumentationRegistry.getInstrumentation().context

    private fun asset(name: String): Bitmap {
        val stream = try {
            testContext.assets.open("ocr_real/$name")
        } catch (e: IOException) {
            throw AssertionError("ocr_real/$name is missing; it is a committed, mandatory fixture", e)
        }
        return stream.use {
            requireNotNull(BitmapFactory.decodeStream(it)) { "ocr_real/$name did not decode" }
        }
    }

    private fun recognise(bitmap: Bitmap): Text {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val latch = CountDownLatch(1)
            var out: Text? = null
            var failure: Exception? = null
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { out = it; latch.countDown() }
                .addOnFailureListener { failure = it; latch.countDown() }
            check(latch.await(90, TimeUnit.SECONDS)) { "recognition timed out" }
            failure?.let { throw it }
            return out!!
        } finally {
            recognizer.close()
        }
    }

    /** Q1: is confidence populated, or NaN, on the bundled Latin recognizer? */
    @Test
    fun reportsWhetherConfidenceMetadataIsPopulated() {
        val report = StringBuilder("\n=== CONFIDENCE METADATA PROBE (bundled Latin recognizer) ===\n")
        FIXTURES.forEach { name ->
            val bitmap = asset(name)
            val text = try {
                recognise(bitmap)
            } finally {
                bitmap.recycle()
            }

            var elements = 0
            var nanElement = 0
            var nanAngle = 0
            var symbols = 0
            var nanSymbol = 0
            var withLanguage = 0
            val samples = mutableListOf<String>()

            text.textBlocks.forEach { block ->
                block.lines.forEach { line ->
                    line.elements.forEach { element ->
                        elements++
                        val c = element.confidence
                        if (c.isNaN()) nanElement++
                        if (element.angle.isNaN()) nanAngle++
                        if (!element.recognizedLanguage.isNullOrBlank() &&
                            element.recognizedLanguage != "und"
                        ) {
                            withLanguage++
                        }
                        element.symbols.forEach { symbol ->
                            symbols++
                            if (symbol.confidence.isNaN()) nanSymbol++
                        }
                        // Numeric-looking tokens are what the resolver would actually weigh.
                        if (samples.size < 6 && element.text.any { it.isDigit() }) {
                            samples += "'${element.text}'=${"%.3f".format(c)}"
                        }
                    }
                }
            }

            report.append(
                "%-42s elements=%-4d elemNaN=%-4d symbols=%-5d symNaN=%-5d angleNaN=%-4d lang=%-4d\n"
                    .format(name, elements, nanElement, symbols, nanSymbol, nanAngle, withLanguage),
            )
            report.append("    numeric samples: ${samples.joinToString(" ")}\n")
        }
        report.append("=== END CONFIDENCE PROBE ===\n")
        println(report)
    }

    /**
     * Q2: native-resolution crop of the source pixels, no resize — what changes?
     *
     * The crop rectangle here is the same generous overlay the production path starts from, so this
     * measures the realistic case rather than a hand-tuned tight box.
     */
    @Test
    fun reportsWhatANativeResolutionCropRecognises() {
        val report = StringBuilder("\n=== NATIVE-RESOLUTION CROP PROBE (no resize) ===\n")
        FIXTURES.forEach { name ->
            val full = asset(name)
            val fullText = recognise(full)
            val fullDoc = MlKitOcrMapper.toDocument(fullText, full.width, full.height)
            val fullReport = NutritionTableParser.parseWithDiagnostics(fullDoc)

            // Crop the ORIGINAL pixels. No scaling, no re-encode: Bitmap.createBitmap over a
            // sub-rectangle copies source pixels verbatim, which is exactly what §3 requires.
            val left = (full.width * OVERLAY.left).toInt().coerceIn(0, full.width - 1)
            val top = (full.height * OVERLAY.top).toInt().coerceIn(0, full.height - 1)
            val right = (full.width * OVERLAY.right).toInt().coerceIn(left + 1, full.width)
            val bottom = (full.height * OVERLAY.bottom).toInt().coerceIn(top + 1, full.height)
            val crop = Bitmap.createBitmap(full, left, top, right - left, bottom - top)

            val cropText = recognise(crop)
            val cropDoc = MlKitOcrMapper.toDocument(cropText, crop.width, crop.height)
            val cropReport = NutritionTableParser.parseWithDiagnostics(cropDoc)

            report.append(
                "%-42s  full=%-28s crop=%-28s (px %dx%d -> %dx%d)\n".format(
                    name,
                    describe(fullReport.reading),
                    describe(cropReport.reading),
                    full.width,
                    full.height,
                    crop.width,
                    crop.height,
                ),
            )

            crop.recycle()
            full.recycle()
        }
        report.append("=== END CROP PROBE ===\n")
        println(report)
    }

    private fun describe(reading: LabelReading): String = when (reading) {
        is LabelReading.Confident ->
            "Confident ${reading.candidate.value.stripTrailingZeros().toPlainString()}" +
                "/${reading.candidate.basis?.name ?: "no-basis"}"
        is LabelReading.Ambiguous -> "Ambiguous(${reading.candidates.size})"
        LabelReading.NotFound -> "NotFound"
    }

    private companion object {
        val OVERLAY = NormalizedRegion(left = 0.08, top = 0.20, right = 0.92, bottom = 0.80)

        val FIXTURES = listOf(
            "sondey_multilingual_100g.jpg",
            "kinder_multicolumn_piece.jpg",
            "real_yoghurt_serving_column_07.jpg",
            "real_stokbrood_prose_dense_06.jpg",
            "real_witte_kaas_single_column_05.jpg",
            "real_grated_cheese_multicolumn_02.jpg",
            "real_juice_bilingual_per100ml_01.jpg",
            "real_jar_prose_multilingual_03.jpg",
            "real_lid_prose_curved_04.jpg",
        )
    }
}

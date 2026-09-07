package app.justthecarbs.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.ExploratoryExperiment
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * MEASUREMENT ONLY — does native-resolution crop re-OCR (§3) ever help, at ANY tightness?
 *
 * ## Why this sweep exists
 *
 * The first probe cropped at the production overlay (0.08..0.92 x 0.20..0.80) and measured
 * Confident -> NotFound on three fixtures, plus a NEW wrong value on grated cheese (2.09 -> 2.04).
 * Before concluding that re-recognition is a dead end, the obvious confound has to be excluded: that
 * rectangle is a *generic* box, not a table crop. It can cut the basis header or half a declaration,
 * which is a property of the rectangle rather than of re-recognition.
 *
 * So this sweeps the crop from "almost the whole frame" to "tight around the centre" and reports what
 * a fresh recognition of those exact source pixels yields at each step. No resizing at any step —
 * `Bitmap.createBitmap` over a sub-rectangle copies source pixels verbatim.
 *
 * ## What each outcome would mean
 *
 * - **Some tightness recovers a reading the full frame lost** -> re-recognition earns its place as an
 *   evidence source, and the resolver's job is to decide when to trust it.
 * - **Every tightness is neutral-or-worse** -> §3's premise does not hold on this corpus, and the
 *   pass must pivot: the value is in the ASSISTED path (§17-19), not in another recognition pass.
 *
 * Either way this is measurement, not a product assertion, so it prints rather than asserts.
 */
@ExploratoryExperiment
class CropTightnessSweepTest {

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

    private fun readCrop(source: Bitmap, inset: Double): String {
        if (inset <= 0.0) {
            val text = recognise(source)
            val doc = MlKitOcrMapper.toDocument(text, source.width, source.height)
            return describe(NutritionTableParser.parseWithDiagnostics(doc).reading)
        }
        val left = (source.width * inset).toInt().coerceIn(0, source.width - 2)
        val top = (source.height * inset).toInt().coerceIn(0, source.height - 2)
        val right = (source.width * (1 - inset)).toInt().coerceIn(left + 1, source.width)
        val bottom = (source.height * (1 - inset)).toInt().coerceIn(top + 1, source.height)
        val crop = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        return try {
            val text = recognise(crop)
            val doc = MlKitOcrMapper.toDocument(text, crop.width, crop.height)
            describe(NutritionTableParser.parseWithDiagnostics(doc).reading)
        } finally {
            crop.recycle()
        }
    }

    @Test
    fun sweepsCropTightnessAcrossTheRealCorpus() {
        val report = StringBuilder("\n=== CROP TIGHTNESS SWEEP (native resolution, no resize) ===\n")
        report.append("inset = fraction removed from EACH edge. 0.00 = whole frame.\n")
        report.append("%-42s %-26s %-26s %-26s %-26s\n".format("fixture", "0.00 (full)", "0.05", "0.10", "0.15"))

        FIXTURES.forEach { name ->
            val bitmap = asset(name)
            try {
                val cells = INSETS.map { inset ->
                    runCatching { readCrop(bitmap, inset) }.getOrElse { "ERR:${it::class.simpleName}" }
                }
                report.append("%-42s %-26s %-26s %-26s %-26s\n".format(name, cells[0], cells[1], cells[2], cells[3]))
            } finally {
                bitmap.recycle()
            }
        }
        report.append("=== END CROP TIGHTNESS SWEEP ===\n")
        println(report)
    }

    private fun describe(reading: LabelReading): String = when (reading) {
        is LabelReading.Confident ->
            "Conf ${reading.candidate.value.stripTrailingZeros().toPlainString()}" +
                "/${reading.candidate.basis?.name?.removePrefix("PER_") ?: "none"}"
        is LabelReading.Ambiguous -> "Ambig(${reading.candidates.size})"
        LabelReading.NotFound -> "NotFound"
    }

    private companion object {
        val INSETS = listOf(0.00, 0.05, 0.10, 0.15)

        val FIXTURES = listOf(
            "sondey_multilingual_100g.jpg",
            "kinder_multicolumn_piece.jpg",
            "real_yoghurt_serving_column_07.jpg",
            "real_stokbrood_prose_dense_06.jpg",
            "real_witte_kaas_single_column_05.jpg",
            "real_grated_cheese_multicolumn_02.jpg",
        )
    }
}

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
 * §12 OCR engine bake-off — the measurement half. MEASUREMENT ONLY; asserts nothing.
 *
 * ## Why this is one engine per run rather than two engines side by side
 *
 * The obvious design — put both recognizers in the test APK and compare them in one pass — **is not
 * buildable**, and that was established by inspecting the artifacts rather than by trying and
 * guessing at the error:
 *
 * ```
 * com.google.mlkit:text-recognition:16.0.1        (bundled model)
 * com.google.android.gms:play-services-mlkit-text-recognition:19.0.1  (Play Services model)
 * ```
 *
 * Both define `com.google.mlkit.vision.text.latin.TextRecognizerOptions`. They are two
 * *implementations of one API*, deliberately interchangeable and mutually exclusive — not two
 * engines that can coexist. Adding both to any one variant is a duplicate-class failure, which is
 * why the pass brief authorises a **sequential dependency swap**.
 *
 * So this class measures **whichever recognizer is on the classpath**, and the two runs are compared
 * against each other. It prints the engine identity it actually observed, so a result can never be
 * mis-attributed to the wrong engine after the fact.
 *
 * ## Running the swap
 *
 * ```powershell
 * # Run 1 — baseline, as shipped
 * .\gradlew.bat :app:connectedDebugAndroidTest --tests "*OcrEngineBakeOffTest"
 *
 * # Run 2 — swap the dependency in gradle/libs.versions.toml:
 * #   mlkit-text = { group = "com.google.android.gms",
 * #                  name = "play-services-mlkit-text-recognition", version = "19.0.1" }
 * # then re-run the same command and diff the two tables.
 * ```
 *
 * Note the Play Services model is delivered on demand, so its first run on a clean device may report
 * `UNAVAILABLE` — a real operational property worth measuring, not a reason to discard the run.
 */
class OcrEngineBakeOffTest {

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

    /**
     * Which recognizer implementation is actually linked, so the printed table is self-identifying.
     *
     * Determined from the artifact-specific marker class rather than from a build flag: a flag says
     * what someone intended to build, this says what is running.
     */
    private fun engineIdentity(): String {
        val bundledMarker = runCatching {
            Class.forName(
                "com.google.android.gms.dynamite.descriptors.com.google.mlkit.dynamite.text.latin" +
                    ".ModuleDescriptor",
            )
        }.isSuccess
        return if (bundledMarker) {
            "BUNDLED (com.google.mlkit:text-recognition)"
        } else {
            "PLAY-SERVICES (com.google.android.gms:play-services-mlkit-text-recognition)"
        }
    }

    private data class Measurement(val outcome: String, val elapsedMs: Long, val elements: Int)

    private fun measure(bitmap: Bitmap): Measurement {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val started = System.nanoTime()
        try {
            val latch = CountDownLatch(1)
            var text: Text? = null
            var failure: Exception? = null
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { text = it; latch.countDown() }
                .addOnFailureListener { failure = it; latch.countDown() }

            if (!latch.await(120, TimeUnit.SECONDS)) {
                return Measurement("TIMEOUT", (System.nanoTime() - started) / 1_000_000, 0)
            }
            failure?.let {
                // On-demand model not yet delivered. Reported distinctly so it is never silently
                // counted as a recognition loss for this engine.
                return Measurement(
                    "UNAVAILABLE(${it::class.simpleName})",
                    (System.nanoTime() - started) / 1_000_000,
                    0,
                )
            }

            val elapsed = (System.nanoTime() - started) / 1_000_000
            val document = MlKitOcrMapper.toDocument(text!!, bitmap.width, bitmap.height)
            val report = NutritionTableParser.parseWithDiagnostics(document)
            return Measurement(describe(report.reading), elapsed, document.elements.size)
        } finally {
            recognizer.close()
        }
    }

    @Test
    fun measuresTheLinkedRecognizerAcrossTheRealCorpus() {
        val report = StringBuilder("\n=== OCR ENGINE BAKE-OFF (§12) ===\n")
        report.append("engine: ${engineIdentity()}\n")
        report.append("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} ")
        report.append("(API ${android.os.Build.VERSION.SDK_INT})\n")
        report.append("%-42s %-28s %-8s %-6s\n".format("fixture", "outcome", "ms", "elements"))

        var correct = 0
        FIXTURES.forEach { (name, expected) ->
            val bitmap = asset(name)
            val measurement = try {
                measure(bitmap)
            } finally {
                bitmap.recycle()
            }
            val hit = expected != null && measurement.outcome.startsWith("Conf $expected/")
            if (hit) correct++
            report.append(
                "%-42s %-28s %-8d %-6d %s\n".format(
                    name,
                    measurement.outcome,
                    measurement.elapsedMs,
                    measurement.elements,
                    if (hit) "OK" else "",
                ),
            )
        }

        report.append("correct (parser-level, whole asset): $correct/${FIXTURES.size}\n")
        report.append("=== END BAKE-OFF ===\n")
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
        /**
         * Fixture to the value printed on the package, where one is known.
         *
         * Null means the corpus has no single agreed expectation for a whole-asset read, so the row
         * is reported without a correctness verdict rather than being scored against a guess.
         */
        val FIXTURES = listOf(
            "sondey_multilingual_100g.jpg" to "61.9",
            "kinder_multicolumn_piece.jpg" to "53.5",
            "real_yoghurt_serving_column_07.jpg" to "5",
            "real_stokbrood_prose_dense_06.jpg" to "46",
            "real_witte_kaas_single_column_05.jpg" to "2.3",
            "real_grated_cheese_multicolumn_02.jpg" to "2",
            "real_juice_bilingual_per100ml_01.jpg" to "9",
            "real_jar_prose_multilingual_03.jpg" to "1.6",
            "real_lid_prose_curved_04.jpg" to "3",
        )
    }
}

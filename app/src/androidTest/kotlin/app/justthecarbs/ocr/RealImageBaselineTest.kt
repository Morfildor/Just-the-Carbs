package app.justthecarbs.ocr

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.ExploratoryExperiment
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Measurement, not regression. Prints the full pipeline state for every fixture so the failure STAGE
 * is known before any production code is touched. Delete or keep as a diagnostic aid — it asserts
 * nothing and can never fail for a parser reason.
 */
@ExploratoryExperiment
class RealImageBaselineTest {

    private val testContext get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun dumpEveryFixture() {
        FIXTURES.forEach { name ->
            val stream = testContext.assets.open("ocr_real/$name")
            val bitmap = stream.use { BitmapFactory.decodeStream(it) }
            checkNotNull(bitmap) { "$name did not decode" }

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val latch = CountDownLatch(1)
            var text: Text? = null
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { text = it; latch.countDown() }
                .addOnFailureListener { latch.countDown() }
            latch.await(30, TimeUnit.SECONDS)
            recognizer.close()

            val document = MlKitOcrMapper.toDocument(text!!, bitmap.width, bitmap.height)
            val report = NutritionTableParser.parseWithDiagnostics(document)

            Log.i(TAG, "===== $name ${bitmap.width}x${bitmap.height} =====")
            Log.i(TAG, "RAW: ${text!!.text.replace("\n", " | ")}")
            Log.i(TAG, OcrDiagnosticsReport.render(document, report))
            Log.i(TAG, "READING: ${report.reading}")
            Log.i(TAG, "SERVING: ${report.servingCandidate}")
            Log.i(TAG, "PROVENANCE: ${report.provenance}")
        }
    }

    private companion object {
        const val TAG = "OcrBaseline"
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

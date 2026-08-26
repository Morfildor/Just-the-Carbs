package app.justthecarbs.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * What tapping "Read table" actually costs.
 *
 * The performance claim of Strategy A is that confirming a crop needs **no second recognition**, so
 * the re-parse should be imperceptible next to the ML Kit pass that already happened. That claim is
 * worth measuring rather than asserting: if the re-parse were expensive, the crop step would add a
 * visible stall to every scan and the extra tap would stop being worth it.
 *
 * The assertion is deliberately loose — this runs on a shared emulator, and a tight bound would make
 * it a flaky test rather than a useful one. It is here to catch an order-of-magnitude regression
 * (someone reintroducing recognition behind the crop), not to police milliseconds.
 */
class SelectedTableLatencyTest {

    private val testContext get() = InstrumentationRegistry.getInstrumentation().context

    private fun assetBitmap(name: String): Bitmap =
        testContext.assets.open("ocr_real/$name").use {
            requireNotNull(BitmapFactory.decodeStream(it))
        }

    private fun documentFor(bitmap: Bitmap): OcrDocument {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val latch = CountDownLatch(1)
            var text: Text? = null
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { text = it; latch.countDown() }
                .addOnFailureListener { latch.countDown() }
            assertTrue(latch.await(30, TimeUnit.SECONDS))
            return MlKitOcrMapper.toDocument(text!!, bitmap.width, bitmap.height)
        } finally {
            recognizer.close()
        }
    }

    @Test
    fun confirmingACropIsFarCheaperThanRecognisingAgain() {
        val bitmap = assetBitmap("kinder_multicolumn_piece.jpg")
        try {
            // Time one real recognition, for the comparison that gives the number meaning.
            val recognitionStart = System.nanoTime()
            val document = documentFor(bitmap)
            val recognitionMs = (System.nanoTime() - recognitionStart) / 1_000_000

            val wholeFrame = NutritionTableParser.parseWithDiagnostics(document)
            val region = ScanRegionMapper.expand(NormalizedRegion(0.05, 0.05, 0.95, 0.95))

            // Warm the JIT so the measurement is of steady-state work, not first-call overhead.
            repeat(3) { SelectedTableReader.read(document, wholeFrame, region) }

            val reparseStart = System.nanoTime()
            repeat(REPEATS) { SelectedTableReader.read(document, wholeFrame, region) }
            val reparseMs = (System.nanoTime() - reparseStart) / 1_000_000 / REPEATS

            assertTrue(
                "re-parse (${reparseMs}ms) should be well under a recognition pass (${recognitionMs}ms); " +
                    "if this fails, something has reintroduced OCR behind the crop confirmation",
                reparseMs * 2 < recognitionMs.coerceAtLeast(1),
            )
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val REPEATS = 5
    }
}

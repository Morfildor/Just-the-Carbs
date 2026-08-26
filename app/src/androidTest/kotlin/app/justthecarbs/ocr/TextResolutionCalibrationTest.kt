package app.justthecarbs.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import java.io.IOException

/**
 * CALIBRATION — asserts nothing about a threshold, measures what one should be.
 *
 * [TextResolutionGuidance]'s cutoff must come from the corpus rather than from a plausible-looking
 * constant. This composites each fixture at the ratios the sweep used, recognises each composite with
 * the real recognizer, and prints the median text-height fraction alongside the production path's
 * actual verdict at that ratio. The threshold is then read off the point where verdicts change.
 *
 * Read the tag `JustTheCarbsCalib`.
 */
class TextResolutionCalibrationTest {

    private val testContext get() = InstrumentationRegistry.getInstrumentation().context

    private fun assetBitmap(name: String): Bitmap {
        val stream = try {
            testContext.assets.open("ocr_real/$name")
        } catch (e: IOException) {
            throw AssertionError("ocr_real/$name missing", e)
        }
        return stream.use { requireNotNull(BitmapFactory.decodeStream(it)) }
    }

    /** Same composite the framing sweep uses, so the two measurements describe the same images. */
    private fun framed(name: String, tableFraction: Double): Bitmap {
        val table = assetBitmap(name)
        val frameWidth = 2048
        val frameHeight = 1536
        val canvasBitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        canvas.drawColor(Color.rgb(238, 234, 226))

        val targetWidth = (frameWidth * tableFraction).toInt()
        val targetHeight = (targetWidth.toDouble() / table.width * table.height).toInt()
        val scaled = Bitmap.createScaledBitmap(table, targetWidth, targetHeight, true)
        canvas.drawBitmap(
            scaled,
            ((frameWidth - targetWidth) / 2).toFloat(),
            ((frameHeight - targetHeight) / 2).toFloat(),
            null,
        )
        val paint = Paint().apply {
            color = Color.rgb(40, 40, 40)
            textSize = frameHeight * 0.018f
            isAntiAlias = true
        }
        listOf(
            "INGREDIENTEN: tarwebloem, suiker, plantaardige olie (palm),",
            "cacaopoeder 4,5%, magere melkpoeder, glucosestroop, zout,",
            "rijsmiddel (natriumcarbonaat), emulgator (sojalecithine),",
        ).forEachIndexed { i, line ->
            canvas.drawText(line, frameWidth * 0.04f, frameHeight * 0.055f + i * paint.textSize * 1.35f, paint)
        }
        scaled.recycle()
        table.recycle()
        return canvasBitmap
    }

    private fun documentOf(bitmap: Bitmap): OcrDocument {
        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
            com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS,
        )
        return try {
            val task = recognizer.process(
                com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0),
            )
            val text = com.google.android.gms.tasks.Tasks
                .await(task, 60, java.util.concurrent.TimeUnit.SECONDS)
            MlKitOcrMapper.toDocument(text, bitmap.width, bitmap.height)
        } finally {
            recognizer.close()
        }
    }

    private fun verdict(reading: LabelReading): String = when (reading) {
        is LabelReading.Confident -> "Confident ${reading.candidate.value.toPlainString()}"
        is LabelReading.Ambiguous -> "Ambiguous(${reading.candidates.size})"
        LabelReading.NotFound -> "NotFound"
    }

    @Test
    fun measureTextHeightFractionAgainstOutcomeAtEveryRatio() {
        val fixtures = listOf(
            "kinder_multicolumn_piece.jpg",
            "sondey_multilingual_100g.jpg",
            "real_yoghurt_serving_column_07.jpg",
            "real_stokbrood_prose_dense_06.jpg",
        )
        val fractions = listOf(0.30, 0.45, 0.60, 0.80, 1.00)

        Log.i(TAG, "=".repeat(88))
        Log.i(TAG, "TEXT-HEIGHT CALIBRATION — median height fraction vs production verdict")
        Log.i(TAG, "The threshold belongs where verdicts flip, not where a round number sits.")
        Log.i(TAG, "=".repeat(88))
        Log.i(TAG, "%-34s %6s %10s %8s  %s".format("fixture", "ratio", "medianFrac", "elems", "verdict"))

        fixtures.forEach { name ->
            fractions.forEach { fraction ->
                val bitmap = if (fraction >= 1.0) assetBitmap(name) else framed(name, fraction)
                try {
                    val document = documentOf(bitmap)
                    val estimate = TextResolutionGuidance.estimate(document)
                    val reading = NutritionTableParser.parseWithDiagnostics(document).reading
                    Log.i(
                        TAG,
                        "%-34s %6.2f %10.5f %8d  %s  [%s]".format(
                            name.removeSuffix(".jpg").take(34),
                            fraction,
                            estimate.medianHeightFraction,
                            estimate.recognizedElements,
                            verdict(reading),
                            estimate.readiness,
                        ),
                    )
                } finally {
                    bitmap.recycle()
                }
            }
        }
        Log.i(TAG, "=".repeat(88))
    }

    private companion object {
        const val TAG = "JustTheCarbsCalib"
    }
}

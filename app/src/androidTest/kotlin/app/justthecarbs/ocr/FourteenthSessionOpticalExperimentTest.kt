package app.justthecarbs.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.Paint
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

/**
 * Measures additional OCR views on the seven 2026-09-04 optical failures.
 *
 * Each variant remains a separately named recognition result. This experiment never merges,
 * repairs, or promotes its output, so a transformed view cannot override the existing resolver,
 * scale, basis, or child-nutrient safety rules. A production view may only be proposed after this
 * complete physical corpus shows a useful gain without confident-wrong readings.
 */
class FourteenthSessionOpticalExperimentTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    // Internal storage avoids scoped-storage mount differences on newer Android emulators. Inputs
    // are staged with `adb shell run-as`; they remain local and are never packaged into the app.
    private val root get() = File(context.filesDir, "replay14")

    private data class Truth(val bundle: String, val value: BigDecimal, val basis: String)
    private data class Variant(val name: String, val transform: (Bitmap) -> Bitmap)

    private val truth = listOf(
        Truth("20260904-094650-658", BigDecimal("7.2"), "PER_100_G"),
        Truth("20260904-094723-365", BigDecimal("4.5"), "PER_100_G"),
        Truth("20260904-094800-347", BigDecimal("1.3"), "PER_100_ML"),
        Truth("20260904-094819-938", BigDecimal("72"), "PER_100_G"),
        Truth("20260904-094946-883", BigDecimal("52.4"), "PER_100_ML"),
        Truth("20260904-095105-084", BigDecimal("6.6"), "PER_100_ML"),
        Truth("20260904-095122-233", BigDecimal("75"), "PER_100_G"),
    )

    private val variants = listOf(
        Variant("baseline") { it },
        Variant("grayscale") { grayscale(it) },
        Variant("contrast-1.4x") { contrast(it, 1.4f) },
        Variant("grayscale-contrast-1.6x") {
            val gray = grayscale(it)
            contrast(gray, 1.6f).also { gray.recycle() }
        },
        Variant("upscale-1.5x") {
            Bitmap.createScaledBitmap(it, (it.width * 1.5f).toInt(), (it.height * 1.5f).toInt(), true)
        },
    )

    @Test
    fun measureIndependentViewsOnAllSevenPhysicalCaptures() {
        val files = truth.associateWith { item -> File(root, "${item.bundle}/capture.jpg") }
        assertTrue(
            "Push docs/Scan evidence 04-09 into ${root.absolutePath}; an absent corpus is not a pass",
            files.values.all(File::isFile),
        )

        val counts = variants.associate { it.name to intArrayOf(0, 0) }.toMutableMap()
        var measured = 0
        files.forEach { (expected, file) ->
            variants.forEach { variant ->
                val loaded = requireNotNull(StillImageLoader.loadWithRotation(file).bitmap)
                val transformed = variant.transform(loaded)
                val document = recognise(transformed)
                val report = NutritionTableParser.parseWithDiagnostics(document)
                measured++
                val candidate = (report.reading as? LabelReading.Confident)?.candidate
                val correct = candidate != null &&
                    candidate.value.compareTo(expected.value) == 0 &&
                    candidate.basis?.name == expected.basis
                val wrong = candidate != null && !correct
                if (correct) counts.getValue(variant.name)[0]++
                if (wrong) counts.getValue(variant.name)[1]++
                Log.i(
                    TAG,
                    "${expected.bundle} | ${variant.name} | ${describe(report)} | " +
                        "truth=${expected.value}/${expected.basis} | correct=$correct | wrong=$wrong",
                )
                if (transformed !== loaded && !transformed.isRecycled) transformed.recycle()
                if (!loaded.isRecycled) loaded.recycle()
            }
        }

        Log.i(TAG, "=== seven-capture optical summary: correct / confident-wrong ===")
        variants.forEach { variant ->
            val count = counts.getValue(variant.name)
            Log.i(TAG, "${variant.name}: ${count[0]} / ${count[1]}")
        }
        assertEquals("every physical capture and variant was measured", truth.size * variants.size, measured)
    }

    private fun recognise(bitmap: Bitmap): OcrDocument {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val text = Tasks.await(
                recognizer.process(InputImage.fromBitmap(bitmap, 0)),
                60,
                TimeUnit.SECONDS,
            )
            MlKitOcrMapper.toDocument(text, bitmap.width, bitmap.height)
        } finally {
            recognizer.close()
        }
    }

    private fun describe(report: NutritionParseReport): String = when (val reading = report.reading) {
        is LabelReading.Confident ->
            "Confident ${reading.candidate.value}/${reading.candidate.basis} " +
                "via ${report.provenance?.let { it::class.simpleName }}"
        is LabelReading.Ambiguous -> "Ambiguous ${reading.candidates.map { it.value }}"
        LabelReading.NotFound -> "NotFound ${report.failureReason}"
    }

    private fun grayscale(source: Bitmap): Bitmap = Bitmap.createBitmap(
        source.width,
        source.height,
        Bitmap.Config.ARGB_8888,
    ).also { output ->
        val paint = Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(
                ColorMatrix().apply { setSaturation(0f) },
            )
        }
        Canvas(output).drawBitmap(source, 0f, 0f, paint)
    }

    private fun contrast(source: Bitmap, amount: Float): Bitmap = Bitmap.createBitmap(
        source.width,
        source.height,
        Bitmap.Config.ARGB_8888,
    ).also { output ->
        val translate = (-.5f * amount + .5f) * 255f
        val matrix = ColorMatrix(
            floatArrayOf(
                amount, 0f, 0f, 0f, translate,
                0f, amount, 0f, 0f, translate,
                0f, 0f, amount, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        val paint = Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(matrix) }
        Canvas(output).drawBitmap(source, 0f, 0f, paint)
    }

    private companion object {
        const val TAG = "JustTheCarbsOptical14"
    }
}

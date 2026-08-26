package app.justthecarbs.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * DIAGNOSTIC ONLY — asserts nothing. Reproduces the PHONE condition offline.
 *
 * ## The discrepancy this exists to explain
 *
 * The physical device fails on Kinder while `ProductionStillPipelineTest` passes Kinder through the
 * same `analyzeStill` entry point. Both cannot be measuring the same thing.
 *
 * They are not. Every committed fixture is a **pre-cropped nutrition panel** — the table fills the
 * frame. A phone capture is a **whole package**: the table occupies a fraction of a 3264x2448 frame
 * and is surrounded by an ingredient list, marketing copy, a barcode, a date and other languages.
 *
 * So "recognise the whole capture, uncropped" — correct for a fixture, where whole image == table —
 * means something entirely different on a phone, where it also means "recognise everything else on
 * the package". No fixture can show that difference, because no fixture contains anything else.
 *
 * This test synthesizes the missing condition: it composites each fixture into a larger canvas at a
 * realistic scale, surrounded by the kind of text a real package carries, and runs the result through
 * the production entry point at several table-to-frame ratios.
 *
 * Read the logcat tag `JustTheCarbsFraming`.
 */
class FramedCaptureDiagnosticTest {

    private val testContext get() = InstrumentationRegistry.getInstrumentation().context
    private val appContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun assetBitmap(name: String): Bitmap {
        val stream = try {
            testContext.assets.open("ocr_real/$name")
        } catch (e: IOException) {
            throw AssertionError("ocr_real/$name is missing", e)
        }
        return stream.use { requireNotNull(BitmapFactory.decodeStream(it)) }
    }

    /**
     * The fixture placed inside a larger frame, occupying [tableFraction] of the frame's width.
     *
     * The surrounding text is deliberately the kind that competes: nutrient words, "100 g" strings and
     * numbers, in prose. That is not a stacked deck — it is what the back of a package actually says,
     * and it is exactly what the scan overlay used to remove before recognition.
     */
    private fun framedCapture(name: String, tableFraction: Double): File {
        val table = assetBitmap(name)

        // A 4:3 frame in the capture's own proportions. Downscaled from 3264x2448 to keep the test
        // quick; what matters is the RATIO of table to frame, not the absolute pixel count.
        val frameWidth = 2048
        val frameHeight = 1536
        val canvasBitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        canvas.drawColor(Color.rgb(238, 234, 226))

        val targetWidth = (frameWidth * tableFraction).toInt()
        val targetHeight = (targetWidth.toDouble() / table.width * table.height).toInt()
        val scaled = Bitmap.createScaledBitmap(table, targetWidth, targetHeight, true)
        val left = (frameWidth - targetWidth) / 2
        val top = (frameHeight - targetHeight) / 2
        canvas.drawBitmap(scaled, left.toFloat(), top.toFloat(), null)

        val paint = Paint().apply {
            color = Color.rgb(40, 40, 40)
            textSize = frameHeight * 0.018f
            isAntiAlias = true
        }
        val clutter = listOf(
            "INGREDIENTEN: tarwebloem, suiker, plantaardige olie (palm),",
            "cacaopoeder 4,5%, magere melkpoeder, glucosestroop, zout,",
            "rijsmiddel (natriumcarbonaat), emulgator (sojalecithine),",
            "aroma. Kan sporen van noten en ei bevatten. 100 g bevat",
            "een gemiddelde portie. Bewaren op een droge plaats.",
        )
        clutter.forEachIndexed { index, line ->
            canvas.drawText(line, frameWidth * 0.04f, frameHeight * 0.055f + index * paint.textSize * 1.35f, paint)
        }
        val footer = listOf(
            "Ten minste houdbaar tot: zie zijkant  •  Lot 24B117",
            "Netto 220 g  •  Distributeur: Voorbeeld B.V., Postbus 100",
            "Bewaaradvies: koel en droog  •  www.voorbeeld.nl",
        )
        footer.forEachIndexed { index, line ->
            canvas.drawText(line, frameWidth * 0.04f, frameHeight * 0.90f + index * paint.textSize * 1.35f, paint)
        }

        scaled.recycle()
        table.recycle()

        val dir = appContext.cacheDir.also { it.mkdirs() }
        val file = File.createTempFile("framed-", ".jpg", dir)
        file.outputStream().use { canvasBitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        canvasBitmap.recycle()
        return file
    }

    private fun analyse(file: File, region: NormalizedRegion?): NutritionParseReport {
        val analyzer = LabelAnalyzer(onReading = { })
        try {
            val latch = CountDownLatch(1)
            var report: NutritionParseReport? = null
            analyzer.analyzeStill(appContext, file, region) { report = it; latch.countDown() }
            latch.await(90, TimeUnit.SECONDS)
            return report ?: NutritionParseReport(LabelReading.NotFound, emptyList())
        } finally {
            analyzer.close()
        }
    }

    private fun verdict(report: NutritionParseReport): String = when (val r = report.reading) {
        is LabelReading.Confident ->
            "Confident ${r.candidate.value.toPlainString()} basis=${r.candidate.basis}"
        is LabelReading.Ambiguous ->
            "Ambiguous(${r.candidates.size}) [${r.candidates.joinToString { it.value.toPlainString() }}]"
        LabelReading.NotFound -> "NotFound"
    }

    @Test
    fun measureCanariesAsTheyArriveFromAPhoneRatherThanAsPreCroppedPanels() {
        val fixtures = listOf(
            "kinder_multicolumn_piece.jpg" to "53.5",
            "sondey_multilingual_100g.jpg" to "61.9",
            "real_yoghurt_serving_column_07.jpg" to "5",
            "real_stokbrood_prose_dense_06.jpg" to "46",
        )
        // How much of the frame's width the nutrition table occupies. 1.0 is the fixture as committed
        // (a pre-cropped panel). The realistic phone range is the smaller values.
        val fractions = listOf(0.30, 0.45, 0.60, 0.80)

        Log.i(TAG, "=".repeat(78))
        Log.i(TAG, "TABLE-TO-FRAME RATIO SWEEP — the condition no fixture reproduces")
        Log.i(TAG, "Committed fixtures are pre-cropped panels (ratio ~1.0).")
        Log.i(TAG, "A phone capture puts the same table at roughly 0.30-0.60 of the frame.")
        Log.i(TAG, "")
        Log.i(TAG, "Correct values: kinder 53.5 | sondey 61.9 | yoghurt 5 | stokbrood 46")
        Log.i(TAG, "ANY confident value other than those is a CONFIDENT-WRONG and a hard failure.")
        Log.i(TAG, "=".repeat(78))

        val summary = mutableListOf<String>()

        fixtures.forEach { (name, expected) ->
            Log.i(TAG, "")
            Log.i(TAG, "--- $name (package prints $expected) ---")

            val baseline = run {
                val f = assetFile(name)
                try { verdict(analyse(f, OVERLAY)) } finally { f.delete() }
            }
            Log.i(TAG, "  ratio=1.00 (fixture as committed, what the suite measures) -> $baseline")

            fractions.forEach { fraction ->
                val file = framedCapture(name, fraction)
                val result = try { analyse(file, OVERLAY) } finally { file.delete() }
                val text = verdict(result)
                Log.i(TAG, "  ratio=%.2f -> %s".format(fraction, text))

                val confidentValue = (result.reading as? LabelReading.Confident)
                    ?.candidate?.value?.stripTrailingZeros()
                if (confidentValue != null &&
                    confidentValue.compareTo(java.math.BigDecimal(expected)) != 0
                ) {
                    summary += "CONFIDENT-WRONG $name @%.2f: got $confidentValue, printed $expected"
                        .format(fraction)
                }
            }
        }

        Log.i(TAG, "")
        Log.i(TAG, "=".repeat(78))
        if (summary.isEmpty()) {
            Log.i(TAG, "CONFIDENT-WRONG: none")
        } else {
            summary.forEach { Log.i(TAG, it) }
        }
        Log.i(TAG, "=".repeat(78))
    }

    private fun assetFile(name: String): File {
        val bitmap = assetBitmap(name)
        val dir = appContext.cacheDir.also { it.mkdirs() }
        val file = File.createTempFile("fixture-", ".jpg", dir)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        return file
    }

    private companion object {
        const val TAG = "JustTheCarbsFraming"
        val OVERLAY = NormalizedRegion(left = 0.08, top = 0.20, right = 0.92, bottom = 0.80)
    }
}

package app.justthecarbs.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.Paint
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * **P1: does any acquisition variant recover the printed `7,2 g`?** A measurement, not a change.
 *
 * ## The question, and why it must be answered before any preprocessing ships
 *
 * The eighth session photographed one red Lidl label four times. It prints `7,2 g / 100 g`, and ML
 * Kit returned `12g`, `724`, and twice `carbono2g` — the `7,` fused into the Spanish nutrient word.
 * The printed value survives in **none** of the four recognitions; the only `7` tokens anywhere in
 * those documents are the postcode `DE-74167` and a batch code.
 *
 * So no parser rule can recover `7.2` honestly, and any that produced it would be fabricating it.
 * The only legitimate route to a better answer is a better *image*, which is what this measures.
 *
 * ## Why it asserts almost nothing
 *
 * The four captures are the owner's photographs of their own shopping and are **not committed** —
 * the repo is public (see the `.gitignore` policy for `docs/Scan Evidence*`). A test that asserted
 * on them would fail for everyone who does not have them. It prints, and the pass notes record what
 * it printed. Push the bundles first:
 *
 * ```
 * adb push "docs/Scan Evidence 5th test/." \
 *   /sdcard/Android/data/app.justthecarbs.debug/files/replay/
 * ```
 *
 * ## What would justify shipping a variant
 *
 * All five, together: it materially increases exact `7.2` recognition; it never *manufactures* `7.2`
 * where the pixels do not say so; it never turns the sugars `6,1`, fibre `0,8`, protein `1,8`, salt
 * `0,25`, a percentage or a unit glyph into a carbohydrate total; it regresses none of the committed
 * corpus; and it stays inside the warm p95 budget. Anything less and the honest outcome is to keep
 * the P0 safety fix, report the limitation, and let focused entry be the fallback — which is what
 * the eighth-session notes do.
 *
 * ## MEASURED 2026-09-02, and the answer is NO. Do not ship preprocessing.
 *
 * Seven variants x four captures = 28 measurements, run on the `carbscan` AVD through the production
 * [StillImageLoader] and the real ML Kit recognizer. **`7.2` was recovered zero times.** Every
 * variant that changed an outcome changed it from an honest `NotFound` into a *confident wrong
 * answer*:
 *
 * ```
 * variant               capture      outcome
 * baseline              all four     NotFound                       (correct: the value is not there)
 * upscale-1.5x          213014-298   Confident 72.0 / PER_100_G     <- 10x the printed 7,2
 * upscale-2x            213026-546   Confident 72.0 / PER_100_G     <- 10x
 * grayscale             213014-298   Confident 12.0 / PER_100_G     <- wrong
 * grayscale-upscale-2x  213014-298   Confident 29.0 / PER_100_G     <- wrong
 * grayscale-upscale-2x  213026-546   Confident 72.0 / PER_100_G     <- 10x
 * grayscale-contrast    all four     NotFound
 * contrast-1.4x         all four     NotFound
 * ```
 *
 * The `72.0` results are the decimal separator being resampled away: upscaling makes the glyphs
 * cleaner *and* makes `7,2` read as `72`, which is precisely the `(g)` -> `(9)` mechanism
 * [SelectedRegionRecognizer] already documents and refuses to rely on. A tenfold carbohydrate error
 * presented confidently is the worst outcome this app can produce, and three of the seven variants
 * produce it.
 *
 * So the acquisition route is measured, closed, and recorded. The shipped answer for this label is
 * the P0 safety fix plus focused entry: the app declines to propose a figure it cannot support, and
 * the user types the `7,2` they can read. **Re-run this class before proposing preprocessing again;
 * do not reason about it from the source.**
 */
class RedLabelAcquisitionExperimentTest {

    private val appContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun replayDirectory(): File =
        File(appContext.getExternalFilesDir(null), "replay").also { it.mkdirs() }

    /** One acquisition variant: a name and the transform it applies to the upright capture. */
    private data class Variant(val name: String, val transform: (Bitmap) -> Bitmap)

    private fun variants(): List<Variant> = listOf(
        Variant("baseline") { it },
        // Higher-resolution resampling. The historic `(g)` -> `(9)` confident-wrong came from a
        // rescaled re-recognition, so this is measured with suspicion rather than hope.
        Variant("upscale-1.5x") { scale(it, 1.5f) },
        Variant("upscale-2x") { scale(it, 2.0f) },
        // Grayscale: the red label prints dark text on a textured pink ground, and the texture is a
        // plausible cause of the lost separator.
        Variant("grayscale") { grayscale(it) },
        Variant("grayscale-contrast") { contrast(grayscale(it), 1.6f) },
        Variant("grayscale-upscale-2x") { scale(grayscale(it), 2.0f) },
        Variant("contrast-1.4x") { contrast(it, 1.4f) },
    )

    @Test
    fun measureAcquisitionVariantsOnTheRedLabel() {
        val captures = replayDirectory().walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg") }
            .sortedBy { it.absolutePath }
            .toList()

        // **A replay test with no inputs must not report success.**
        //
        // This used to log a warning and `return`, so the class passed in ~0.09 s having measured
        // nothing at all — indistinguishable, from the outside, from 28 measurements that found no
        // recovery. That is the same shape as the `assumeTrue`-skipped OCR corpus this repo already
        // recorded: a green result standing in for an absent one.
        //
        // `assumeTrue` would be the other honest option and is deliberately not used: the captures
        // are a committed prerequisite for this experiment, so their absence is a setup error to be
        // fixed, not a condition to be tolerated. The message says exactly how to fix it.
        assertTrue(
            "No captures in ${replayDirectory().absolutePath}. This experiment measures real ML Kit " +
                "against the archived red-label photographs and cannot run without them — push the " +
                "four captures from docs/Scan Evidence 5th test/ before running it. Passing with no " +
                "inputs would report 'no variant recovered 7.2' without having looked.",
            captures.isNotEmpty(),
        )

        Log.i(TAG, "=== acquisition variant experiment: ${captures.size} captures ===")
        variants().forEach { variant ->
            Log.i(TAG, "########## variant: ${variant.name}")
            captures.forEach { file ->
                // The file's own name, not its parent's: loose JPEGs all share one parent directory,
                // and labelling every row "replay" makes the table unattributable.
                val label = file.nameWithoutExtension
                val source = decodeUpright(file)
                if (source == null) {
                    Log.w(TAG, "  $label: could not decode")
                    return@forEach
                }
                val transformed = runCatching { variant.transform(source) }.getOrNull()
                if (transformed == null) {
                    Log.w(TAG, "  $label: transform failed (likely OOM at this scale)")
                    if (!source.isRecycled) source.recycle()
                    return@forEach
                }

                val started = System.currentTimeMillis()
                val document = recognise(transformed)
                val elapsed = System.currentTimeMillis() - started

                if (document == null) {
                    Log.w(TAG, "  $label: recognition failed")
                } else {
                    val report = NutritionTableInterpreter.interpret(document)
                    val carbTokens = carbohydrateRowTokens(document)
                    Log.i(
                        TAG,
                        "  $label: reading=${describe(report.reading)} | ${elapsed}ms | " +
                            "carb-row tokens=$carbTokens",
                    )
                }
                if (transformed !== source && !transformed.isRecycled) transformed.recycle()
                if (!source.isRecycled) source.recycle()
            }
        }
        Log.i(TAG, "=== end ===")
    }

    /**
     * The value-shaped tokens on whatever row names carbohydrate — where `7,2` would appear.
     *
     * Falls back to *any* row naming a carbohydrate term when none classifies as a total row, so a
     * variant that damaged the classification still reports what it did to the pixels. Without the
     * fallback every failure reads identically as "(no total-carbohydrate row)" and the experiment
     * cannot distinguish "the value was lost" from "the row was".
     */
    private fun carbohydrateRowTokens(document: OcrDocument): String {
        val rows = LogicalRowBuilder.build(document)
        val kinds = RowClassifier.classifyAll(rows)
        val totals = rows.withIndex()
            .filter { (index, _) -> kinds[index] == NutritionRowKind.TOTAL_CARBOHYDRATE }
            .map { it.value }
        val naming = totals.ifEmpty {
            rows.filter { row ->
                val normalized = NutritionTerminology.normalize(row.text)
                NutritionTerminology.carbohydrateTerms.any {
                    NutritionTerminology.containsTerm(normalized, it)
                } || DamagedCarbohydrateLabel.statesADamagedCarbohydrateWord(normalized)
            }
        }
        val marker = if (totals.isEmpty()) "[no total row] " else ""
        val tokens = naming
            .flatMap { row -> row.elements.map { it.text.trim() } }
            .filter { it.any(Char::isDigit) }
            .joinToString(" | ")
        return marker + tokens.ifEmpty { "(no numeric token on any carbohydrate row)" }
    }

    private fun describe(reading: LabelReading): String = when (reading) {
        is LabelReading.Confident ->
            "Confident ${reading.candidate.value} / ${reading.candidate.basis}"
        is LabelReading.Ambiguous ->
            "Ambiguous " + reading.candidates.joinToString { it.value.toPlainString() }
        LabelReading.NotFound -> "NotFound"
    }

    // The bundles store the sensor-orientation JPEG; production applies the EXIF rotation before
    // recognition, so the experiment goes through the production loader rather than a decode of its
    // own — otherwise it would measure a sideways label and blame the variant.
    private fun decodeUpright(file: File): Bitmap? = StillImageLoader.loadWithRotation(file).bitmap

    private fun scale(source: Bitmap, factor: Float): Bitmap = Bitmap.createScaledBitmap(
        source,
        (source.width * factor).toInt(),
        (source.height * factor).toInt(),
        true,
    )

    private fun grayscale(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(
                ColorMatrix().apply { setSaturation(0f) },
            )
        }
        Canvas(output).drawBitmap(source, 0f, 0f, paint)
        return output
    }

    private fun contrast(source: Bitmap, amount: Float): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val translate = (-.5f * amount + .5f) * 255f
        val matrix = ColorMatrix(
            floatArrayOf(
                amount, 0f, 0f, 0f, translate,
                0f, amount, 0f, 0f, translate,
                0f, 0f, amount, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        val paint = Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
        }
        Canvas(output).drawBitmap(source, 0f, 0f, paint)
        return output
    }

    private fun recognise(bitmap: Bitmap): OcrDocument? {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var document: OcrDocument? = null
        val latch = CountDownLatch(1)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener {
                document = MlKitOcrMapper.toDocument(it, bitmap.width, bitmap.height)
                latch.countDown()
            }
            .addOnFailureListener { latch.countDown() }
        latch.await(30, TimeUnit.SECONDS)
        recognizer.close()
        return document
    }

    private companion object {
        const val TAG = "JustTheCarbsAcq"
    }
}

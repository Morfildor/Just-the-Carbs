package app.justthecarbs.ocr

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Batch-replays REAL captures taken on a physical phone through the production still path.
 *
 * ## Why
 *
 * A device scan failed on a package the instrumented suite reads correctly. Every committed fixture is
 * a pre-cropped nutrition panel; a phone capture is a whole package at a fraction of the frame. That
 * difference alone reproduced the failure synthetically — but a synthetic composite is a
 * reconstruction, and a reconstruction can be faithful in the way that matters and wrong in another.
 *
 * This closes that gap. It takes the exact JPEGs the phone wrote and runs each through the same
 * `analyzeStill` the scanner calls. **If a capture fails here, the failure is fully reproducible
 * offline and the camera is exonerated. If it passes here while failing in the app, the fault is in
 * the camera path** — acquisition, focus, or timing — and not in the parser.
 *
 * ## Usage
 *
 * In the app: Settings → Export scan evidence, share the zip to yourself, unzip. Then push **either**
 * whole evidence folders or loose JPEGs — both are found:
 *
 * ```
 * adb push <unzipped>/. /sdcard/Android/data/app.justthecarbs.debug/files/replay/
 * ```
 *
 * Optionally state what the package prints, so the report can mark a reading Correct or WRONG rather
 * than leaving you to check by eye. Name the file, or its folder, with the value after a `~`:
 *
 * ```
 * kinder-close~53.5.jpg        stroopwafel-far~64.9.jpg
 * ```
 *
 * Then run the class. It asserts nothing — it is a measurement, and the images are deliberately not
 * committed, so an assertion would fail for anyone without them. Read the tag `JustTheCarbsReplay`.
 */
class DeviceCaptureReplayTest {

    private val appContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun replayDirectory(): File =
        File(appContext.getExternalFilesDir(null), "replay").also { it.mkdirs() }

    /** One capture and what, if anything, the user said the package prints. */
    private data class Capture(val file: File, val label: String, val expected: String?)

    /**
     * Every JPEG under the replay directory, at any depth.
     *
     * Recursive so a whole unzipped evidence export can be pushed as-is: those arrive as one folder
     * per capture, each containing `capture.jpg`. Requiring the user to flatten them by hand is the
     * kind of friction that stops evidence being gathered at all.
     */
    private fun captures(): List<Capture> {
        val root = replayDirectory()
        return root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png") }
            .map { file ->
                // `capture.jpg` is the evidence recorder's own fixed name, so the folder is what
                // identifies it; a loose file identifies itself.
                val naming = if (file.nameWithoutExtension == "capture") {
                    file.parentFile?.name ?: file.nameWithoutExtension
                } else {
                    file.nameWithoutExtension
                }
                Capture(
                    file = file,
                    label = file.relativeToOrSelf(root).path.replace('\\', '/'),
                    expected = naming.substringAfter('~', "").takeIf { it.isNotBlank() },
                )
            }
            .sortedBy { it.label }
            .toList()
    }

    @Test
    fun replayEveryPushedDeviceCapture() {
        val dir = replayDirectory()
        val found = captures()

        Log.i(TAG, "=".repeat(96))
        Log.i(TAG, "DEVICE CAPTURE REPLAY — the shipped analyzeStill path")
        Log.i(TAG, "looking in: ${dir.absolutePath}")

        if (found.isEmpty()) {
            Log.i(TAG, "")
            Log.i(TAG, "No captures found. This is not a failure — the images are deliberately not")
            Log.i(TAG, "committed (they are photographs taken in someone's home, and the repo is public).")
            Log.i(TAG, "")
            Log.i(TAG, "To use: Settings -> Export scan evidence, unzip, then")
            Log.i(TAG, "  adb push <unzipped>/. ${dir.absolutePath}/")
            Log.i(TAG, "Name a file or folder with ~<printed value> to have it checked, e.g. kinder~53.5.jpg")
            Log.i(TAG, "=".repeat(96))
            return
        }

        Log.i(TAG, "${found.size} capture(s)")
        val summary = mutableListOf<String>()

        found.forEach { capture ->
            Log.i(TAG, "")
            Log.i(TAG, "--- ${capture.label} ---")

            val bounds = BitmapFactory.Options().also {
                it.inJustDecodeBounds = true
                BitmapFactory.decodeFile(capture.file.absolutePath, it)
            }
            val megapixels = bounds.outWidth.toLong() * bounds.outHeight / 1_000_000.0
            Log.i(
                TAG,
                "  capture      : ${bounds.outWidth}x${bounds.outHeight} " +
                    "(%.2f MP, ${capture.file.length() / 1024} KB)".format(megapixels),
            )
            capture.expected?.let { Log.i(TAG, "  package prints: $it") }

            // Text size, which separates "too far away" from "close enough but still unreadable".
            // Measured on the capture itself rather than on a live frame, which is the closest this
            // harness can get to what the user was pointing at.
            recognizeMetrics(capture.file)?.let { (estimate, elements) ->
                Log.i(
                    TAG,
                    "  text size    : median %.5f of frame height, %d elements [%s]"
                        .format(estimate.medianHeightFraction, elements, estimate.readiness),
                )
            }

            // Replayed BOTH ways because `relevance` is the only thing the scan overlay still does,
            // and knowing whether it changed the outcome separates "the framing filter is wrong"
            // from "recognition or parsing is wrong".
            listOf<Pair<String, NormalizedRegion?>>(
                "relevance=none   " to null,
                "relevance=OVERLAY" to OVERLAY,
            ).forEach { (label, region) ->
                // Copied per run: analyzeStill deletes the file it is given, which is correct for a
                // real capture and would otherwise consume the replay corpus on first use.
                val working = File.createTempFile("replay-", ".jpg", appContext.cacheDir)
                capture.file.copyTo(working, overwrite = true)

                val analyzer = LabelAnalyzer(onReading = { })
                try {
                    val latch = CountDownLatch(1)
                    var report: NutritionParseReport? = null
                    val started = System.nanoTime()
                    analyzer.analyzeStill(appContext, working, region) { report = it; latch.countDown() }
                    val answered = latch.await(120, TimeUnit.SECONDS)
                    val elapsed = (System.nanoTime() - started) / 1_000_000
                    val result = report

                    if (!answered || result == null) {
                        Log.i(TAG, "  $label -> NO ANSWER within 120s")
                        return@forEach
                    }

                    val verdict = verdict(result)
                    val judgement = judge(result, capture.expected)
                    Log.i(TAG, "  $label -> $verdict  ${elapsed}ms  $judgement")
                    result.diagnostics
                        .filter { it.stage in INTERESTING_STAGES }
                        .takeLast(6)
                        .forEach { Log.i(TAG, "        ${it.stage}: ${it.message}") }

                    if (judgement == "WRONG") {
                        summary += "CONFIDENT-WRONG ${capture.label} [$label]: " +
                            "$verdict but the package prints ${capture.expected}"
                    }
                } finally {
                    analyzer.close()
                    working.delete()
                }
            }
        }

        Log.i(TAG, "")
        Log.i(TAG, "=".repeat(96))
        if (summary.isEmpty()) {
            Log.i(TAG, "CONFIDENT-WRONG: none across ${found.size} capture(s)")
        } else {
            Log.i(TAG, "CONFIDENT-WRONG FOUND — this is the one outcome that blocks release:")
            summary.forEach { Log.i(TAG, "  $it") }
        }
        Log.i(TAG, "=".repeat(96))
    }

    /** Text-size metrics for one capture, or null when it could not be decoded or recognized. */
    private fun recognizeMetrics(file: File): Pair<TextResolutionGuidance.Estimate, Int>? {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
            com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS,
        )
        return try {
            val text = com.google.android.gms.tasks.Tasks.await(
                recognizer.process(com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)),
                60,
                TimeUnit.SECONDS,
            )
            val document = MlKitOcrMapper.toDocument(text, bitmap.width, bitmap.height)
            TextResolutionGuidance.estimate(document) to document.elements.size
        } catch (error: Exception) {
            Log.i(TAG, "  text size    : unavailable (${error.javaClass.simpleName})")
            null
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    /**
     * Correct / WRONG / other, against the value the user said the package prints.
     *
     * Only a **confident** reading can be WRONG. An ambiguity or a refusal is not a wrong answer —
     * it is the app declining to give one, which is the designed behaviour when evidence is thin.
     */
    private fun judge(report: NutritionParseReport, expected: String?): String {
        if (expected == null) return ""
        val reading = report.reading as? LabelReading.Confident ?: return "(no confident value)"
        val printed = expected.replace(',', '.').toBigDecimalOrNull() ?: return "(unparseable expected)"
        return if (reading.candidate.value.compareTo(printed) == 0) "CORRECT" else "WRONG"
    }

    private fun verdict(report: NutritionParseReport): String = when (val r = report.reading) {
        is LabelReading.Confident ->
            "Confident ${r.candidate.value.toPlainString()} ${r.candidate.basis} " +
                "via ${report.provenance?.let { it::class.simpleName } ?: "?"}"
        is LabelReading.Ambiguous ->
            "Ambiguous(${r.candidates.size}) [${r.candidates.joinToString { it.value.toPlainString() }}]"
        LabelReading.NotFound -> "NotFound"
    }

    private companion object {
        const val TAG = "JustTheCarbsReplay"
        val OVERLAY = NormalizedRegion(left = 0.08, top = 0.20, right = 0.92, bottom = 0.80)

        /** Stages that say why an answer was or was not produced; the rest is row-by-row noise. */
        val INTERESTING_STAGES = setOf(
            "rejected", "result", "selected", "ambiguous", "prose", "column",
            "unit-marker", "serving-weight",
        )
    }
}

package app.justthecarbs.ocr

import android.content.Context
import android.graphics.Bitmap
import app.justthecarbs.BuildConfig
import com.google.mlkit.vision.text.Text
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug-only recorder for everything one still capture handed the parser.
 *
 * ## Why this exists
 *
 * A physical-device scan failed on a package (Kinder) that passes the same `analyzeStill` entry point
 * in the instrumented suite. Two things claiming to be the same path disagreed, and nothing on the
 * device could say which of five candidate causes was responsible: a soft or downscaled capture, a
 * rotation/decode fault, focus never settling, ML Kit recognising materially different text, or the
 * parser correctly refusing good text.
 *
 * Answering that needs the **exact bytes the phone fed ML Kit**, not a reconstruction. This writes:
 *
 * - `capture.jpg` — the original file straight off `ImageCapture`, untouched
 * - `passA.png` — the decoded, EXIF-corrected bitmap actually handed to Pass A
 * - `recognized.txt` — ML Kit's complete recognised text with per-element geometry
 * - `diagnostics.txt` — the full stage trace and the refusal reason
 * - `meta.txt` — dimensions, EXIF rotation, decode scale, timings
 *
 * Replaying `capture.jpg` through `analyzeStill` offline is then an exact reproduction rather than an
 * approximation, which is the only thing that can settle where the device and the suite diverge.
 *
 * ## Safety and privacy
 *
 * Everything here is behind `BuildConfig.DEBUG`, a compile-time constant, so R8 removes the bodies and
 * the calls from a minified release. **Release builds never write an image anywhere.** That matters
 * more than usual here: the ordinary still path deliberately deletes its capture immediately and never
 * inserts it into MediaStore, because a photograph taken in someone's kitchen is their data. This
 * deliberately retains images, so it must never exist in a shipped build.
 *
 * Files live in the app's own `cacheDir`, are capped at [MAX_RETAINED] captures, and are exported only
 * by explicit user action.
 */
object ScanEvidenceRecorder {

    /** Debug builds only. Keeps release behaviour — capture, read, delete — completely unchanged. */
    val enabled: Boolean get() = BuildConfig.DEBUG

    private const val MAX_RETAINED = 12

    /**
     * What CameraX negotiated at the last bind (§23), for `meta.txt`.
     *
     * Held here rather than threaded through `analyzeStill` because it is a property of the *camera
     * session*, not of any one capture, and it is established at bind time — long before a shutter
     * press. Null until a camera has been bound, which is exactly the case in fixture-replay tests,
     * and `meta.txt` says so explicitly rather than printing a resolution that no camera produced.
     */
    @Volatile
    private var lastCaptureConfiguration: String? = null

    /** Called once per camera bind. No-op in release, like everything else here. */
    fun recordCaptureConfiguration(requested: String, selected: String, cropRect: String, rotation: Int) {
        if (!enabled) return
        lastCaptureConfiguration =
            "requested=$requested selected=$selected viewportCrop=$cropRect rotation=$rotation"
    }
    private const val DIRECTORY = "scan-evidence"

    fun directory(context: Context): File =
        File(context.cacheDir, DIRECTORY).also { it.mkdirs() }

    /**
     * Opens a folder for one capture and prunes old ones.
     *
     * Returns null when disabled, which is what makes every call site a no-op in release.
     */
    fun begin(context: Context): File? {
        if (!enabled) return null
        val root = directory(context)
        prune(root)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        return File(root, stamp).also { it.mkdirs() }
    }

    @Volatile
    private var writerOrNull: java.util.concurrent.ExecutorService? = null

    /**
     * Writes the heavy artefact — a multi-megabyte PNG encode — off the scan's critical path.
     *
     * Single-threaded and daemon, so evidence for successive scans is written in order, at most one
     * encode is in flight at a time, and a pending write can never hold the process open.
     *
     * ### Why this is not `by lazy`
     *
     * It was, and that measurably broke the release build's privacy guarantee. A `by lazy` property
     * on an `object` is a field initialised in the class's static initialiser, so R8 has to keep
     * `<clinit>` — and with it the class and its `ThreadFactory` lambda — even after correctly
     * proving every *method* here dead. `mapping.txt` went from having no entry for this class at all
     * to having one, which is the exact check this repo uses to verify that a build shipping to users
     * contains no evidence recorder.
     *
     * A plain null-initialised field folds away instead: nothing is constructed until
     * [recordPassAImageAsync] asks, and that call sits behind the `enabled` constant, so in release
     * the branch is removed and the class goes with it. Verified by grepping the release
     * `mapping.txt` for `^app\.justthecarbs\.ocr\.ScanEvidenceRecorder ->`.
     */
    private fun writer(): java.util.concurrent.ExecutorService =
        writerOrNull ?: synchronized(this) {
            writerOrNull ?: java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "scan-evidence").apply {
                    isDaemon = true
                    priority = Thread.MIN_PRIORITY
                }
            }.also { writerOrNull = it }
        }

    /**
     * Takes ownership of the capture JPEG, returning true when the caller must no longer delete it.
     *
     * A **move**, not a copy. The still path deletes this file immediately after reading it, so
     * copying it first meant every debug scan paid a ~3.5 MB read-and-write before the user saw
     * anything, purely to end up with the same bytes in a different directory. `renameTo` within
     * `cacheDir` is a directory-entry update and costs nothing measurable.
     *
     * Returns false in release, where this is a no-op and the caller's own delete runs exactly as it
     * did before — the shipped capture-read-delete behaviour is unchanged.
     */
    fun consumeCapture(folder: File?, source: File): Boolean {
        if (!enabled || folder == null) return false
        val target = File(folder, "capture.jpg")
        runCatching { target.delete() }
        val moved = runCatching { source.renameTo(target) }.getOrDefault(false)
        if (moved) return true
        // Different filesystem, or the rename raced something. Fall back to the copy and let the
        // caller delete as usual, so evidence is never lost merely because a rename was refused.
        runCatching { source.copyTo(target, overwrite = true) }
            .onFailure { OcrDiagnosticsLogger.failure("Could not record capture", it) }
        return false
    }

    /**
     * Renders `passA.png` in the background, from the recorded capture rather than the live bitmap.
     *
     * ### Why it re-decodes instead of encoding the bitmap it was handed
     *
     * Two reasons, and the first is a correctness one. The decoded bitmap's ownership transfers to
     * the crop screen the instant the result is delivered, and that screen recycles it when the
     * capture session ends. Handing the same bitmap to a background encoder creates a window in which
     * a user tapping Retake recycles a bitmap mid-`compress` — a native crash, in debug, caused
     * entirely by diagnostics.
     *
     * Second, it removes the encode from the scan. A ~24 MB ARGB_8888 bitmap PNG-encodes in seconds,
     * and it used to run before the result was handed over, which is a large part of why a measured
     * device spent ~3.3 s of a 3.55 s scan outside the recognizer.
     *
     * Re-decoding [StillImageLoader.loadWithRotation] from the moved `capture.jpg` reproduces the
     * Pass A bitmap through the production code path, so the artefact is if anything a better
     * reproduction than the one this replaces.
     */
    fun recordPassAImageAsync(folder: File?) {
        if (!enabled || folder == null) return
        val capture = File(folder, "capture.jpg")
        writer().execute {
            runCatching {
                if (!capture.isFile) return@runCatching
                val bitmap = StillImageLoader.loadWithRotation(capture, region = null).bitmap
                    ?: return@runCatching
                try {
                    File(folder, "passA.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } finally {
                    bitmap.recycle()
                }
            }.onFailure { OcrDiagnosticsLogger.failure("Could not record pass A bitmap", it) }
        }
    }

    /** ML Kit's complete output, so "did the recognizer see it?" is answerable without the phone. */
    fun recordRecognizedText(folder: File?, text: Text) {
        if (!enabled || folder == null) return
        runCatching {
            File(folder, "recognized.txt").writeText(
                buildString {
                    appendLine("=== ML Kit recognized text (verbatim) ===")
                    appendLine(text.text)
                    appendLine()
                    appendLine("=== elements with geometry ===")
                    text.textBlocks.forEachIndexed { b, block ->
                        block.lines.forEachIndexed { l, line ->
                            appendLine("block $b line $l  angle=${line.angle}  '${line.text}'")
                            line.elements.forEach { element ->
                                val box = element.boundingBox
                                appendLine(
                                    "    '${element.text}' [${box?.left},${box?.top}," +
                                        "${box?.right},${box?.bottom}]",
                                )
                            }
                        }
                    }
                },
            )
        }.onFailure { OcrDiagnosticsLogger.failure("Could not record recognized text", it) }
    }

    /** The stage trace, including the refusal reason when the parser declined to answer. */
    fun recordDiagnostics(folder: File?, document: OcrDocument, report: NutritionParseReport) {
        if (!enabled || folder == null) return
        runCatching {
            File(folder, "diagnostics.txt").writeText(OcrDiagnosticsReport.render(document, report))
        }.onFailure { OcrDiagnosticsLogger.failure("Could not record diagnostics", it) }
    }

    /**
     * Acquisition facts the images alone cannot show.
     *
     * [exifRotationDegrees] and the two sizes are here because "rotation/decode damages it" is one of
     * the hypotheses, and a rotated bitmap looks perfectly fine on its own — it is only wrong relative
     * to what was captured.
     */
    fun recordMeta(
        folder: File?,
        captureBytes: Long,
        jpegWidth: Int,
        jpegHeight: Int,
        passAWidth: Int,
        passAHeight: Int,
        exifRotationDegrees: Int,
        decodedViaFallback: Boolean,
        region: NormalizedRegion?,
        totalMs: Long,
        outcome: String,
        /** ML Kit recognition time alone, separated from [totalMs] so decode and parse are visible. */
        recognitionMs: Long,
        /** Per-stage breakdown from [ScanTrace], so a slow scan names its own bottleneck. */
        timingBreakdown: String = "",
    ) {
        val captureConfiguration = lastCaptureConfiguration
        if (!enabled || folder == null) return
        runCatching {
            File(folder, "meta.txt").writeText(
                buildString {
                    appendLine("captured JPEG   : ${jpegWidth}x$jpegHeight  ($captureBytes bytes)")
                    appendLine("pass A bitmap   : ${passAWidth}x$passAHeight")
                    appendLine("EXIF rotation   : $exifRotationDegrees deg")
                    appendLine("decode fallback : $decodedViaFallback (true = bitmap decode failed)")
                    appendLine(
                        "scan region     : " + (
                            region?.let {
                                "[%.3f,%.3f,%.3f,%.3f] (relevance only, never a crop)"
                                    .format(it.left, it.top, it.right, it.bottom)
                            } ?: "none"
                            ),
                    )
                    appendLine("recognition     : ${recognitionMs}ms (ML Kit alone)")
                    appendLine("total           : ${totalMs}ms (decode + recognition + parse)")
                    if (timingBreakdown.isNotEmpty()) {
                        appendLine("stage breakdown : $timingBreakdown")
                    }
                    appendLine("outcome         : $outcome")
                    appendLine()
                    // §23: what the camera was ASKED for versus what it negotiated. Recorded from
                    // the bind site because `ImageCapture.resolutionInfo` only exists after binding.
                    // Without this a bundle cannot answer "did this device honour 8 MP", and a
                    // fixture replay's dimensions were once mistaken for a camera result.
                    appendLine("capture config  : ${captureConfiguration ?: "not recorded (no camera bind)"}")
                    appendLine("device          : ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("android         : API ${android.os.Build.VERSION.SDK_INT}")
                    appendLine("app             : ${BuildConfig.VERSION_NAME}")
                },
            )
        }.onFailure { OcrDiagnosticsLogger.failure("Could not record meta", it) }
    }

    /**
     * What the user's confirmed crop did to the recognised document.
     *
     * The single most useful artefact when a manually cropped table still fails, because it separates
     * three otherwise indistinguishable causes: the crop removed nothing (selection too generous),
     * the crop removed the wrong things (header or value column in the rejected list), or the crop was
     * correct and the parser refused on its own terms (retained list looks like a clean table, and
     * `diagnostics.txt` names the stage that ran out of evidence).
     *
     * Elements are written with their geometry and their retained/rejected verdict, so the selection
     * can be reasoned about without the original photograph — which matters because the capture is
     * deleted and the originals are deliberately never committed.
     */
    fun recordSelection(
        folder: File?,
        region: NormalizedRegion,
        document: OcrDocument?,
        outcome: String,
        elementsBefore: Int,
        elementsAfter: Int,
        report: NutritionParseReport,
    ) {
        if (!enabled || folder == null) return
        runCatching {
            val retained = document?.let { ElementRegionFilter.filter(it, region)?.elements }.orEmpty()
            val rejected = document?.let { ElementRegionFilter.rejected(it, region) }.orEmpty()
            File(folder, "selection.txt").writeText(
                buildString {
                    appendLine("=== user-confirmed crop ===")
                    appendLine(
                        "region          : [%.4f,%.4f,%.4f,%.4f] (fractions of the upright capture)"
                            .format(region.left, region.top, region.right, region.bottom),
                    )
                    appendLine("capture size    : ${document?.width}x${document?.height}")
                    appendLine("outcome         : $outcome")
                    appendLine("elements        : $elementsBefore -> $elementsAfter")
                    appendLine("reading         : ${report.reading::class.simpleName}")
                    appendLine("provenance      : ${report.provenance}")
                    appendLine("second OCR pass : no (Pass A elements reused)")
                    appendLine()
                    appendLine("=== retained (${retained.size}) ===")
                    retained.forEach {
                        appendLine(
                            "  '${it.text}' [${it.box.left},${it.box.top},${it.box.right},${it.box.bottom}]",
                        )
                    }
                    appendLine()
                    appendLine("=== rejected as outside the selection (${rejected.size}) ===")
                    rejected.forEach {
                        appendLine(
                            "  '${it.text}' [${it.box.left},${it.box.top},${it.box.right},${it.box.bottom}]",
                        )
                    }
                    appendLine()
                    appendLine("=== parser diagnostics after filtering ===")
                    report.diagnostics.forEach { appendLine("  ${it.stage}: ${it.message}") }
                },
            )
        }.onFailure { OcrDiagnosticsLogger.failure("Could not record selection", it) }
    }

    /** Every retained capture folder, newest first. */
    fun captures(context: Context): List<File> =
        if (!enabled) emptyList()
        else directory(context).listFiles()?.filter { it.isDirectory }?.sortedDescending() ?: emptyList()

    fun clear(context: Context) {
        if (!enabled) return
        runCatching { directory(context).deleteRecursively() }
    }

    private fun prune(root: File) {
        val folders = root.listFiles()?.filter { it.isDirectory }?.sortedDescending() ?: return
        folders.drop(MAX_RETAINED - 1).forEach { runCatching { it.deleteRecursively() } }
    }
}

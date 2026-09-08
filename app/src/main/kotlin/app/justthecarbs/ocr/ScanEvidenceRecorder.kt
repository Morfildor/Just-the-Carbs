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

    /**
     * How many captures the recorder keeps before pruning the oldest.
     *
     * Raised from 12 to 35 (2026-09-04, owner request). A physical QA session runs three captures
     * each across several packages — the §32 gate alone asks for nine — and at 12 the earliest
     * bundles were being pruned before the session ended, so the export arrived missing exactly the
     * captures that had motivated it. That is not hypothetical: the ninth session's first archive
     * was exported before the new captures existed, and a short retention window makes the same
     * class of mistake easy to repeat.
     *
     * **The cost is disk.** Each capture keeps `capture.jpg` (an untouched 8 MP JPEG, ~3.5 MB) plus
     * `passA.png` (the decoded bitmap, larger still), so 35 captures is on the order of 350 MB in
     * `cacheDir`. That is acceptable only because this is debug-only: `enabled` is
     * `BuildConfig.DEBUG`, R8 strips the whole object from release, and the directory is the app's
     * own cache, which the system may reclaim under pressure. **Do not raise this without
     * re-reading that paragraph** — the same number in a shipped build would be a privacy defect,
     * not a disk one.
     *
     * [prune] keeps `MAX_RETAINED - 1` existing folders and then adds the new one, so the steady
     * state is exactly this many.
     */
    private const val MAX_RETAINED = 35

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
     * Blocks until everything already queued on the writer thread has been written.
     *
     * ### The only place in the app that waits for the writer
     *
     * Every other caller queues and returns, which is the point: evidence I/O was measured at 5–9
     * seconds on the scan path and moving it off was a release-blocking fix. Nothing here weakens
     * that — this is called from [ScanEvidenceExport.share] alone, a deliberate user action with no
     * latency budget, and it is the one moment where a half-written `passA.png` would be zipped as
     * though it were complete.
     *
     * ### Why a queued marker rather than a shutdown
     *
     * The writer is single-threaded and FIFO, so a task submitted now runs after everything already
     * queued. Waiting on that marker therefore waits for exactly the backlog and no more, and leaves
     * the executor alive for the next capture. Shutting it down would make the *next* scan
     * reconstruct it, which is the per-call construction cost this executor exists to avoid.
     *
     * A timeout bounds the wait. False means the exporter must refuse the incomplete snapshot.
     *
     * Returns immediately when nothing has ever been written (the executor does not exist) or in
     * release, where [enabled] is false and there is no writer at all.
     */
    fun drain(): Boolean {
        if (!enabled) return true
        val executor = writerOrNull ?: return true
        val done = java.util.concurrent.CountDownLatch(1)
        runCatching { executor.execute { done.countDown() } }
            .onFailure { return false }
        return runCatching { done.await(DRAIN_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS) }.getOrDefault(false)
    }

    /**
     * How long [drain] waits for the writer's backlog.
     *
     * Generous: the backlog is at most a few PNG encodes of an 8 MP bitmap, and the alternative to
     * waiting is exporting an archive that is missing them. Bounded at all so a stuck writer cannot
     * hang the share action indefinitely.
     */
    private const val DRAIN_TIMEOUT_SECONDS = 30L

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
     * Takes ownership of the capture JPEG on the writer thread, and deletes the temporary file if
     * ownership could not be taken.
     *
     * ### Why the asynchronous form exists (2026-09-01, P0-2)
     *
     * [consumeCapture] is a rename *when the rename succeeds*. When it does not — different
     * filesystem, or a raced target — it falls back to `copyTo`, and that copy is unbounded
     * synchronous file I/O proportional to the capture size. The device evidence measured this at
     * **9487 ms** and **5232 ms**, on the scan's critical path, because the call sat at the top of
     * `LabelAnalyzer`'s `finish` before the result was delivered.
     *
     * Marking the stage off-path in [ScanTrace] did not help and could not: that changes which
     * total is printed, not when the work runs.
     *
     * ### Why the delete moved here too
     *
     * The delete is the other half of the same decision — the caller must delete the temporary file
     * exactly when this did *not* take it over. Splitting them left the caller holding a boolean it
     * could only get by waiting. Both halves now happen together, off the path, so the caller has no
     * result to wait for.
     *
     * ### Ordering
     *
     * The writer is single-threaded and FIFO, so a subsequent [recordPassAImageAsync] is guaranteed
     * to see `capture.jpg` already in place. That ordering is why this must be queued on the same
     * executor rather than on an arbitrary background thread.
     *
     * In release [enabled] is false, so this deletes the temporary file directly on the calling
     * thread — exactly the behaviour the shipped build has always had, with no executor constructed
     * and no thread started.
     */
    fun consumeCaptureAsync(folder: File?, source: File) {
        if (!enabled || folder == null) {
            if (!source.delete()) {
                OcrDiagnosticsLogger.failure("Temporary label image was already absent")
            }
            return
        }
        writer().execute {
            if (!consumeCapture(folder, source) && !source.delete()) {
                OcrDiagnosticsLogger.failure("Temporary label image was already absent")
            }
        }
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
                val bitmap = StillImageLoader.loadWithRotation(capture).bitmap
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

    /**
     * The verbatim recognizer dump, as a string.
     *
     * Split out so the synchronous and deferred writers cannot produce different files — the async
     * path must render on the calling thread (the [Text] belongs to the recognition) and write
     * later, and duplicating this formatting is how the two would drift.
     */
    private fun renderRecognizedText(text: Text): String = buildString {
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
    }

    /**
     * Renders the diagnostics now and writes them **after** the caller has handed its result to the
     * UI (§ latency).
     *
     * ### The measurement this exists for
     *
     * Every bundle in `docs/Scan Evidence 01-09-26/` records `evidence-diagnostics` between 582 ms
     * and 1106 ms, and it ran *synchronously between the parse finishing and the result reaching the
     * screen*. `ScanTrace` marked it off-path, so the printed `user-visible` figure subtracted it —
     * which made the trace honest about what the shipped build costs while the debug build the
     * measurements were taken on still made the user wait for it.
     *
     * ### Why the render is synchronous and only the write is deferred
     *
     * [OcrDiagnosticsReport.render] reads the document and the report, both of which are immutable
     * and outlive this call. The *file write* is what costs; rendering is cheap. Taking an immutable
     * snapshot on the calling thread and handing a plain string to the writer is what makes deferral
     * safe — the same discipline `recordPassAImageAsync` uses when it re-decodes from the moved
     * capture rather than touching a bitmap whose ownership has transferred.
     *
     * Export waits for queued writes with [drain] on a background thread.
     */
    fun recordDiagnosticsAsync(folder: File?, document: OcrDocument, report: NutritionParseReport) {
        if (!enabled || folder == null) return
        val rendered = runCatching { OcrDiagnosticsReport.render(document, report) }
            .onFailure { OcrDiagnosticsLogger.failure("Could not render diagnostics", it) }
            .getOrNull() ?: return
        writer().execute {
            runCatching { File(folder, "diagnostics.txt").writeText(rendered) }
                .onFailure { OcrDiagnosticsLogger.failure("Could not record diagnostics", it) }
        }
    }

    /**
     * Serialises the recognizer's output now and writes it after the result handover.
     *
     * The [Text] object belongs to the recognition that produced it, so it is flattened to a string
     * on the calling thread rather than captured — deferring a read of recognizer-owned state is the
     * race this repo already hit once by encoding a bitmap whose owner recycled it.
     */
    fun recordRecognizedTextAsync(folder: File?, text: Text) {
        if (!enabled || folder == null) return
        val rendered = runCatching { renderRecognizedText(text) }
            .onFailure { OcrDiagnosticsLogger.failure("Could not render recognized text", it) }
            .getOrNull() ?: return
        writer().execute {
            runCatching { File(folder, "recognized.txt").writeText(rendered) }
                .onFailure { OcrDiagnosticsLogger.failure("Could not record recognized text", it) }
        }
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
        /**
         * Pass A's own [LabelReading] class name — **not** the resolver's verdict.
         *
         * Named for what it is, because the previous name (`outcome`) produced a wrong diagnosis
         * from correct data: a device bundle reporting `Confident` was read as "the fast path
         * should have advanced", when the gate is
         * `AutomaticScanAdvance.mayAdvance(EvidenceResolver.Outcome)` over *every* pass and this
         * value is only one input to it. A reading is not an outcome. See [resolverVerdict].
         */
        passAReading: String,
        /**
         * What [EvidenceResolver] concluded once every pass reported, or null when this capture
         * never reached resolution (pass A is recorded before any crop is confirmed).
         *
         * Separate from [passAReading] because the two answer different questions and were
         * conflated: only this one decides whether the crop step is skipped.
         */
        resolverVerdict: String? = null,
        /** ML Kit recognition time alone, separated from [totalMs] so decode and parse are visible. */
        recognitionMs: Long,
        /** Per-stage breakdown from [ScanTrace], so a slow scan names its own bottleneck. */
        timingBreakdown: String = "",
    ) {
        val captureConfiguration = lastCaptureConfiguration
        if (!enabled || folder == null) return
        // Rendered here, written on the writer thread.
        //
        // This runs after the result handover, so it never delayed the user's answer — but it did
        // delay `startPendingStillIfPossible()`, the call that begins the *next* capture, by a file
        // write. Deferring it costs nothing and keeps every file write in this class on one thread,
        // which is also what guarantees `meta.txt` is written after `capture.jpg` is in place.
        val rendered = runCatching {
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
                    // Two lines, deliberately, because one field answering to both names produced a
                    // wrong diagnosis from correct data. `passA.reading` is one pass's reading;
                    // `resolver.verdict` is what actually gates the crop-skip. See recordMeta's KDoc.
                    appendLine("passA.reading   : $passAReading (one pass's reading, NOT the gate)")
                    // When the verdict is not yet known, say only that — and do NOT append the
                    // "this is what AutomaticScanAdvance reads" pointer, which then labels a
                    // placeholder as the gate's input.
                    //
                    // `meta.txt` is written at capture time, before any crop resolution exists, so
                    // this branch is the ordinary case rather than an error. The final verdict is
                    // recorded in `selection.txt`, and the reader is sent there instead of being
                    // told that "not reached" is what the gate saw.
                    if (resolverVerdict != null) {
                        appendLine(
                            "resolver.verdict: $resolverVerdict  <- this is what AutomaticScanAdvance reads",
                        )
                    } else {
                        appendLine(
                            "resolver.verdict: not reached at this point (meta.txt is written at " +
                                "capture time, before crop resolution) — see selection.txt for the " +
                                "final verdict the gate actually read",
                        )
                    }
                    appendLine()
                    // §23: what the camera was ASKED for versus what it negotiated. Recorded from
                    // the bind site because `ImageCapture.resolutionInfo` only exists after binding.
                    // Without this a bundle cannot answer "did this device honour 8 MP", and a
                    // fixture replay's dimensions were once mistaken for a camera result.
                    appendLine("capture config  : ${captureConfiguration ?: "not recorded (no camera bind)"}")
                    appendLine("device          : ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("android         : API ${android.os.Build.VERSION.SDK_INT}")
                    appendLine("app             : ${BuildConfig.VERSION_NAME}")
            }
        }.onFailure { OcrDiagnosticsLogger.failure("Could not render meta", it) }
            .getOrNull() ?: return

        writer().execute {
            runCatching { File(folder, "meta.txt").writeText(rendered) }
                .onFailure { OcrDiagnosticsLogger.failure("Could not record meta", it) }
        }
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
        /**
         * [EvidenceResolver]'s verdict over every pass — the thing the fast-path gate reads.
         *
         * Null only when a caller has not been updated to supply it. It is the single most valuable
         * line in the bundle when asking why a capture did or did not skip the crop step.
         */
        resolverVerdict: String? = null,
        /**
         * One line per pass that actually contributed evidence, and whether Strategy B ran.
         *
         * This replaced a **hardcoded** `second OCR pass : no (Pass A elements reused)` that was
         * printed unconditionally and measured nothing. Strategy B *is* invoked on the automatic
         * attempt whenever independent runs do not already agree, and its result is dropped silently
         * when it returns null (recycled bitmap, degenerate crop, caught exception) — so the old line
         * asserted the opposite of what the code does and made a duplicate-recognition claim
         * unfalsifiable.
         */
        passesRan: List<String> = emptyList(),
        /** Whether Strategy B was attempted, and what it returned. Null when the caller cannot say. */
        strategyBStatus: String? = null,
        /**
         * How the reading was **independently verified**, and the figures behind that judgement.
         *
         * Distinct from [resolverVerdict], and reading a bundle without both is how the
         * `085542-213` confident-wrong went unexplained for a session: that capture's verdict was
         * `Resolved` and its reading was `Confident`, and both were true — it advanced anyway
         * because nothing had checked the *digits* against anything. This line is what says whether
         * that check happened and what it found.
         */
        verification: String? = null,
        /**
         * What the final UI decision actually was.
         *
         * The bundle previously recorded what the gate *read* but never what the app *did*, so a
         * recording and a bundle had to be watched side by side to tell an automatic advance from a
         * one-tap confirmation.
         */
        uiAction: String? = null,
        /**
         * Which candidates a distinct recognition run contradicted, and which run read what.
         *
         * Without this a bundle could show `resolver.verdict: Conflicted` beside a recovery list
         * containing the very value that was refused, with nothing to connect the two — which is
         * exactly how `131511` had to be diagnosed from a screen recording.
         */
        disputed: DisputedCandidates = DisputedCandidates.NONE,
        /**
         * The surviving punctuation evidence behind a scale-ambiguity refusal.
         *
         * Names the candidate token, the paired column token and the declared serving quantity the
         * check used, so the decision can be reconstructed from the files alone.
         */
        scaleVerdict: ScaleAmbiguity.Verdict? = null,
        /** The basis handed to Edit or focused entry, and whether the amount was prefilled. */
        correctionHandoff: String? = null,
        /** Final first-failing layer after parser, resolver, scale, and presentation decisions. */
        failureReason: CarbFailureReason? = report.failureReason,
        /**
         * The document Strategy B recognised, when it ran and returned one.
         *
         * ## Why this was missing and why it mattered
         *
         * The bundle recorded Strategy B's **verdict** (`Confident 2.8/PER_100_G`) and never the
         * document behind it, while `diagnostics.txt` records Pass A's document alone. So a session
         * where the two passes *disagreed* — which is the interesting case, and exactly the ninth
         * session's — could not be replayed: the evidence proved a correct reading had existed and
         * gave nothing to reproduce it from.
         *
         * Diagnosing 2026-09-03 therefore required deriving Strategy B's document from Pass A's plus
         * the one difference the verdict implied. That derivation is sound and is asserted against
         * the real interpreter, but it is reconstruction rather than replay, and it should not be
         * needed twice.
         *
         * Written to its own file rather than into `selection.txt`, because a full element dump is
         * long and `selection.txt` is the file a human reads first.
         */
        strategyBDocument: OcrDocument? = null,
        /**
         * Where [strategyBDocument] sits inside the capture, when Strategy B ran.
         *
         * Without it the dump is a set of coordinates in an unstated space. Strategy B recognises a
         * *crop*, so its boxes are measured from the crop's own origin and its width and height are
         * the crop's — and a reader comparing them against `diagnostics.txt`'s full-frame boxes will
         * silently conclude the two passes disagree about where the row is. That confusion is the
         * same one that let crop-local geometry be drawn as if it were full-frame.
         */
        strategyBCrop: SelectedRegionCrop.PixelRect? = null,
    ) {
        if (!enabled || folder == null) return
        writer().execute {
            runCatching {
                // Rendered in the same format as `diagnostics.txt`, so a Strategy B document replays
                // through exactly the tooling that already reads a Pass A one — no new parser, and the
                // element count is self-checking against the header it prints.
                strategyBDocument?.let {
                    File(folder, "strategyB.txt").writeText(
                        buildString {
                            appendLine("=== strategy B coordinate space ===")
                            appendLine("native size     : ${it.width}x${it.height} (the crop, not the capture)")
                            appendLine(
                                "crop origin     : " + (
                                    strategyBCrop?.let { crop ->
                                        "(${crop.left}, ${crop.top}) ${crop.width}x${crop.height} " +
                                            "— add this to every box below to reach capture coordinates"
                                    } ?: "unrecorded"
                                    ),
                            )
                            appendLine()
                            append(OcrDiagnosticsReport.render(it, NutritionTableInterpreter.interpret(it)))
                        },
                    )
                }
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
                        appendLine("reading         : ${report.reading::class.simpleName} (Strategy A re-parse)")
                        appendLine("provenance      : ${report.provenance}")
                        appendLine("failure reason  : ${failureReason?.name ?: "none"}")
                        appendLine(
                            "resolver.verdict: " + (resolverVerdict ?: "not supplied by caller") +
                                "  <- what AutomaticScanAdvance reads",
                        )
                        appendLine(
                            "strategy B      : " + (strategyBStatus ?: "not recorded by caller"),
                        )
                        appendLine(
                            "automatic-verification: " + (verification ?: "not recorded by caller") +
                                "  <- CROSS_COLUMN | DISTINCT_OCR_AGREEMENT | NONE",
                        )
                        appendLine(
                            // CONFIRM_ON_CAPTURE is the eighth session's addition: an unverified reading
                            // held on the frozen photograph. Distinct from CONFIRM, which was the
                            // live-preview card that made `12 g / 100 g` unanswerable on `213005-691`.
                            "final UI action : " + (uiAction ?: "not recorded by caller") +
                                "  <- AUTO_ADVANCE | CONFIRM_ON_CAPTURE | CONFIRM | RECOVERY",
                        )
                        appendLine(
                            "passes contributing evidence: " + (
                                if (passesRan.isEmpty()) "not recorded by caller" else passesRan.joinToString(", ")
                                ),
                        )
                        appendLine("cross-run dispute: ${disputed.describe()}")
                        appendLine(
                            "scale evidence  : " + when (scaleVerdict) {
                                is ScaleAmbiguity.Verdict.Ambiguous ->
                                    "AMBIGUOUS — candidate '${scaleVerdict.candidateText}' paired with " +
                                        "'${scaleVerdict.pairedText}'; ${scaleVerdict.reason}"
                                is ScaleAmbiguity.Verdict.Established -> "established (${scaleVerdict.reason})"
                                // Distinct from "established" on purpose: this line previously read
                                // `established (no paired value ...)`, a sentence that described an
                                // absence of evidence while claiming its presence. That wording is what
                                // made `20260902-213005-691` hard to attribute.
                                is ScaleAmbiguity.Verdict.Unsupported ->
                                    "UNSUPPORTED — candidate '${scaleVerdict.candidateText}'; " +
                                        "${scaleVerdict.reason}. Not evidence of a sound scale; an " +
                                        "unverified reading is not offered for confirmation on this"
                                null -> "not evaluated (no confident reading, or no document)"
                            },
                        )
                        appendLine(
                            "correction hand-off: " + (correctionHandoff ?: "not recorded by caller"),
                        )
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
                        appendLine()
                        // What the recovery screen would offer and, for every value-shaped number it
                        // would not, the rule that removed it.
                        //
                        // Added because a bundle could say `serving: … weight=none` beside a screen
                        // reading "From 6 g per 18 g serving" and give a reader no way to reconcile
                        // them. They are two different objects: that line reports the *parser's*
                        // ServingCarbCandidate.descriptor, which really is absent on a US linear panel
                        // because there is no column header to carry it, while the `18 g` the user sees
                        // comes from ServingDeclaration reading `Serv. size: 1 Tbsp (18 g)` off the
                        // panel itself. Both were true; only one was printed.
                        //
                        // A suppressed number previously left no trace at all, which is what made the
                        // fabricated `72 g / serving` hard to attribute — the bundle showed the value
                        // and the columns but never which rule had bound them together.
                        appendLine("=== recovery proposal ===")
                        val explained = RecoveryCandidates.explain(document, disputed)
                        if (explained.isEmpty()) appendLine("  (no rows contribute candidates)")
                        explained.forEach { appendLine("  $it") }
                    },
                )
            }.onFailure { OcrDiagnosticsLogger.failure("Could not record selection", it) }
        }
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

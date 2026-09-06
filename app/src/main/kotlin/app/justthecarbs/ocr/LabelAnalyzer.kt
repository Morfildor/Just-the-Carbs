package app.justthecarbs.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** On-device structured nutrition-label OCR for live frames and temporary still captures. */
class LabelAnalyzer(
    private val onReading: (LabelReading) -> Unit,
    /**
     * Framing advice from live frames, for the preview's guidance line only.
     *
     * A **separate** callback from [onReading] on purpose. Merging them would put a value-bearing
     * result and a piece of camera advice on one channel, and the single most important property of
     * the capture-first design is that a live frame can never produce the answer. Keeping the
     * channels apart makes that structural rather than a convention someone must remember. Defaults
     * to a no-op so every existing construction site is unaffected.
     */
    private val onFraming: (TextResolutionGuidance.Estimate) -> Unit = {},
    /** Every raw interpretation, before UI stability filtering, stamped when analysis starts. */
    private val onObservation: (LiveEvidenceBuffer.Observation) -> Unit = {},
    private val aimEpoch: () -> Long = { 0L },
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * The thread ML Kit's completion listeners run on, and therefore the thread the parse runs on.
     *
     * ## Why this had to be supplied explicitly (thirteenth pass)
     *
     * `Task.addOnSuccessListener(listener)` — the no-executor overload — dispatches on the
     * **Android main thread**. The still path's listener does the whole post-recognition pipeline
     * before handing the result over:
     *
     * ```
     * MlKitOcrMapper.toDocument(...)          "to-domain"
     * NutritionTableParser.parseWithDiagnostics(...)   "parse"
     * ScanRegionRelevance.apply(...)          "relevance"
     * ```
     *
     * The thirteenth session measured `parse` at 26–198 ms across nineteen captures, on documents of
     * 36–341 elements. That is main-thread time during which Compose cannot draw a frame, on the
     * screen showing a progress indicator — so the indicator itself stutters exactly when it is
     * being watched.
     *
     * The surrounding code was already careful about this: `analyzeStillRetaining` is *called* from
     * a background executor and its callback explicitly hops to `mainExecutor`. Only the listener
     * dispatch was left implicit, which quietly pulled the heaviest stage back onto main.
     *
     * ## Why a dedicated single thread
     *
     * Serial by construction, so two captures cannot parse concurrently and contend — `inFlight`
     * already serialises the still path, and this keeps the live path's parses in the same queue
     * rather than racing them. A daemon thread so it can never hold the process alive, matching the
     * evidence writer's convention.
     */
    private val parseExecutor: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "jtc-ocr-parse").apply { isDaemon = true }
        }

    private val inFlight = AtomicBoolean(false)
    private val pendingStill = AtomicReference<StillRequest?>(null)
    private val closed = AtomicBoolean(false)
    private val stability = AmbiguityStabilityTracker()

    @Volatile
    private var paused = false

    @Volatile
    private var lastResolution: Pair<Int, Int>? = null

    /**
     * Runs one throwaway recognition so the native detector is loaded before the user's first
     * capture, not during it.
     *
     * ### The measured cost this addresses
     *
     * On the third phone session (`docs/Scan evidence 01-09-26 3rd testr/`) the **first** capture of
     * the session spent **2789 ms** inside ML Kit; the eight that followed spent 447–1838 ms, most
     * of them under 700. The difference is one-time initialisation — loading the model and building
     * the native detector — and it is charged to whichever call happens first.
     *
     * Live analysis frames use the same client and would warm it eventually, but only once frames
     * start arriving *and* are not [paused]; a user who frames and taps quickly can reach the
     * shutter first. This makes the warm-up deliberate rather than a side effect of how long they
     * took to aim.
     *
     * ### Why a 1x1 bitmap
     *
     * It carries no text, so nothing can be recognised from it and no callback consumes the result —
     * the point is only that the detector is constructed. It is the smallest input ML Kit accepts,
     * so the work is initialisation and nothing else.
     *
     * Failure is ignored on purpose. A warm-up that cannot run leaves the app in exactly the state
     * it was in before this existed: the first real recognition pays the cost. There is nothing to
     * report and nothing for the user to do.
     */
    fun warmUp() {
        if (closed.get()) return
        runCatching {
            val blank = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
            recognizer.process(InputImage.fromBitmap(blank, 0))
                .addOnCompleteListener { blank.recycle() }
        }
    }

    /** Stops emitting once the user is considering a candidate. */
    fun pause() {
        paused = true
    }

    fun resume() {
        synchronized(stability) {
            stability.reset()
            paused = false
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (paused || mediaImage == null || !inFlight.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val resolution = imageProxy.width to imageProxy.height
        if (resolution != lastResolution) {
            lastResolution = resolution
            OcrDiagnosticsLogger.analysisResolution(resolution.first, resolution.second)
        }

        val started = System.nanoTime()
        val frameTimeMs = android.os.SystemClock.elapsedRealtime()
        val frameEpoch = aimEpoch()
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        // ## Why the listeners are guarded rather than merely attached
        //
        // They are dispatched on [parseExecutor], which [close] shuts down. A task submitted to a
        // shut-down executor is **rejected**, and `addOnCompleteListener` is where `imageProxy` is
        // released — so a frame in flight across a screen disposal would leak a camera buffer and,
        // with enough of them, stall the analyzer.
        //
        // `runCatching` around the whole chain converts that into the ordinary teardown path: the
        // proxy is closed here instead. `close()` is idempotent on an `ImageProxy`, so the normal
        // case (no rejection) is unaffected by the fallback existing.
        runCatching {
            attachAnalysisListeners(recognizer.process(input), imageProxy, started, frameTimeMs, frameEpoch)
        }.onFailure {
            // The executor is gone; nobody else will release this frame.
            inFlight.set(false)
            imageProxy.close()
        }
    }

    /** The live-frame listener chain, extracted so its rejection path has one home. */
    private fun attachAnalysisListeners(
        task: com.google.android.gms.tasks.Task<Text>,
        imageProxy: ImageProxy,
        started: Long,
        frameTimeMs: Long,
        frameEpoch: Long,
    ) {
        task
            // [parseExecutor], not the implicit main-thread overload. The block below maps every
            // recognised element and runs the full geometry-first parse; on the main thread that is
            // a frame the preview cannot draw. The comment further down already assumed "this
            // listener runs on ML Kit's callback thread" — that was true only of the *recognition*,
            // not of this dispatch, and supplying the executor makes the assumption correct.
            .addOnSuccessListener(parseExecutor) { text ->
                // Bail before the parse, not after it.
                //
                // `paused` is set the instant the user taps capture, and the two `!paused` checks
                // below already discarded everything this frame produced — but only *after* the
                // full geometry-first pipeline (rows, classifiers, interpreter) had run on it. That
                // work sits directly between the shutter and `startPendingStillIfPossible()` in
                // `addOnCompleteListener`, which is what begins recognising the 8 MP still the user
                // is now waiting on. So the discarded frame was not merely wasted: it delayed the
                // capture it was discarded for.
                //
                // Reading `paused` once keeps the decision consistent within this frame; a pause
                // arriving mid-parse is handled the same way it always was, by the checks below.
                if (paused) return@addOnSuccessListener

                val document = MlKitOcrMapper.toDocument(text, imageProxy.width, imageProxy.height)

                // Re-check after the mapping, before anything is emitted.
                //
                // `paused` is set from the main thread the instant the user taps capture, while this
                // listener runs on ML Kit's callback thread — so it can flip at any point in here.
                // The check above only covers the moment the listener started. Without this one,
                // a pause landing during `toDocument` still let `onFraming` fire, and the framing
                // callback drives the preview's guidance line: the user taps capture, the frozen
                // capture UI comes up, and a "Move closer" from a discarded live frame arrives on
                // top of it. Returning here also skips the parse, which is the same work the pause
                // exists to stop.
                if (paused) return@addOnSuccessListener
                onFraming(TextResolutionGuidance.estimate(document))

                val report = parse(document, started)
                // Record raw evidence before UI filtering, unless this frame's aim has ended.
                publishLiveReading(report.reading, frameTimeMs, frameEpoch)
            }
            .addOnFailureListener(parseExecutor) { OcrDiagnosticsLogger.failure("Live OCR failed", it) }
            // Also on the parse thread, and this one is ordering-critical rather than cost-driven:
            // it must run *after* the success listener, and both being on the same single-threaded
            // executor guarantees that. `startPendingStillIfPossible` is what begins recognising the
            // still the user is waiting on, so it must not be able to overtake the frame's own
            // teardown. `imageProxy.close()` is safe off-main — CameraX documents it as callable
            // from any thread, and the analyzer is already invoked on a background executor.
            .addOnCompleteListener(parseExecutor) {
                inFlight.set(false)
                imageProxy.close()
                startPendingStillIfPossible()
            }
    }

    /** Raw evidence and UI readiness have different delivery rules; neither may cross a retake. */
    internal fun publishLiveReading(reading: LabelReading, frameTimeMs: Long, frameEpoch: Long) {
        if (paused || closed.get() || frameEpoch != aimEpoch()) return
        onObservation(LiveEvidenceBuffer.Observation(reading, frameTimeMs, frameEpoch))
        // Only stability's own read-modify-write needs the lock, to serialize against resume()'s
        // reset() on the main thread. The staleness checks and the caller-supplied callbacks above
        // and below touch no shared mutable state of stability's, so holding the lock across them
        // would only ever narrow to a single-threaded rendezvous with resume() for no reason --
        // widening the window in which a paused/disposed frame's callback could interleave with a
        // fresh aim's reset() without changing what either one observes.
        val toSurface = synchronized(stability) { stability.onFrame(reading, System.nanoTime()) }
        if (!paused && frameEpoch == aimEpoch() && toSurface != null) onReading(toSurface)
    }

    /**
     * Reads a high-resolution cache file, reports even `NotFound`, then deletes the file.
     * The file is never inserted into MediaStore and never survives completion or setup failure.
     *
     * Reports the whole [NutritionParseReport] rather than just its reading, so a still capture can
     * also surface a per-serving figure the user may save as a countable portion (spec §17). The
     * live-frame path below is deliberately unchanged and still deals only in [LabelReading] — a
     * camera frame must never be able to persist anything.
     */
    fun analyzeStill(
        context: Context,
        file: File,
        /**
         * What the user framed, as fractions of the visible preview (§10). Null means "read the
         * whole frame", which is what happens before the overlay has been measured and is the
         * behaviour that shipped before this pass.
         */
        region: NormalizedRegion?,
        onComplete: (NutritionParseReport) -> Unit,
    ) {
        val request = StillRequest(context.applicationContext, file, region, onComplete)
        val replaced = pendingStill.getAndSet(request)
        if (replaced != null) {
            replaced.file.delete()
            replaced.fail()
        }
        startPendingStillIfPossible()
    }

    /**
     * Recognises a capture and **retains** the recognised document for later re-parsing.
     *
     * This is the capture-then-confirm entry point. Recognition starts the moment the shutter fires,
     * so ML Kit works while the user is adjusting the crop rectangle rather than after they commit —
     * on a successful read the "Read table" tap then costs only a re-parse of elements already in
     * memory, with no second recognition pass.
     *
     * The capture file is deleted as before, but the decoded bitmap is **kept** so the crop screen has
     * something to display and a Strategy B fallback has a full-resolution source to work from. The
     * caller owns that bitmap from the moment [onComplete] fires and must release it via
     * [PassAResult.recycle].
     *
     * [sessionId] is echoed back untouched. It is the caller's only reliable way to discard a result
     * belonging to a capture the user has already retaken — see [PassAResult.sessionId].
     */
    fun analyzeStillRetaining(
        context: Context,
        file: File,
        sessionId: Long,
        onComplete: (PassAResult) -> Unit,
    ) {
        val request = StillRequest(
            context = context.applicationContext,
            file = file,
            region = null,
            onComplete = {},
            retaining = onComplete,
            sessionId = sessionId,
        )
        val replaced = pendingStill.getAndSet(request)
        if (replaced != null) {
            replaced.file.delete()
            replaced.fail()
        }
        startPendingStillIfPossible()
    }

    private fun startPendingStillIfPossible() {
        if (closed.get() || !inFlight.compareAndSet(false, true)) return
        val request = pendingStill.getAndSet(null)
        if (request == null) {
            inFlight.set(false)
            if (pendingStill.get() != null) startPendingStillIfPossible()
            return
        }

        try {
            runStill(request)
        } catch (error: Exception) {
            OcrDiagnosticsLogger.failure("Could not prepare still image", error)
            request.file.delete()
            request.fail()
            inFlight.set(false)
            startPendingStillIfPossible()
        }
    }

    /**
     * The single-pass still read: recognise the whole capture, parse it, apply framing as relevance.
     *
     * ## Why there is no second recognition pass here
     *
     * A two-pass design was built and measured: Pass A locates a table by geometry, the table is
     * cropped out, and a second recognition of that crop becomes the answer. It was **reverted**, on
     * evidence, and the reasons are recorded here because the idea is attractive enough to be
     * re-proposed.
     *
     * Re-recognising a crop is not a neutral clean-up. Rescaling changes how ML Kit tokenises, and on
     * the Kinder canary it turned the printed unit marker `(g)` into `(9)` — a well-formed
     * single-digit carbohydrate value on the correct row, which then beat the real `53,5`. That is a
     * **confident-wrong**, the worst output this app can produce, manufactured by the isolation step
     * itself. Isolation also dropped 12 rows including the basis header band, reproducing exactly the
     * defect the removal of the overlay crop had fixed.
     *
     * The unit-marker hazard is now guarded structurally by [UnitMarkerFilter], so it is no longer a
     * blocker on its own. The header loss and the fact that vertical banding cannot separate
     * horizontally adjacent panels are unsolved, so isolation stays out of the answer path until a
     * localisation approach is measured to beat this baseline. [NutritionTableLocator] is retained,
     * fully tested and **unwired**, as the starting point for that work.
     */
    private fun runStill(request: StillRequest) {
        val started = System.nanoTime()
        val trace = ScanTrace()

        // Uncropped. Cropping before recognition removed the basis header band on tall labels and
        // cost both canaries — sondey reported `rejected: 61.9: REFERENCE_PERCENT column`, the right
        // value on the right row made unplaceable because its header had been cropped away. A wider
        // fixed margin cannot fix that: the header's offset varies per package, so any margin is a
        // guess that is wrong on some label with nothing on screen to show it.
        val loaded = StillImageLoader.loadWithRotation(request.file, trace = trace)
        val upright = loaded.bitmap
        val input = trace.time("mlkit-input") {
            if (upright != null) {
                InputImage.fromBitmap(upright, 0)
            } else {
                InputImage.fromFilePath(request.context, Uri.fromFile(request.file))
            }
        }

        // The decoded bitmap already knows its own size, so the separate `inJustDecodeBounds` pass
        // this used to make — a second read of the same multi-megabyte file, purely for two integers
        // — is gone. Only the fallback path, where no bitmap exists, still has to ask the file.
        val fallbackBounds = if (upright == null) boundsOf(request.file) else null
        val ocrWidth = upright?.width ?: fallbackBounds!!.outWidth
        val ocrHeight = upright?.height ?: fallbackBounds!!.outHeight
        OcrDiagnosticsLogger.stillResolution(ocrWidth, ocrHeight)
        OcrDiagnosticsLogger.passA(ocrWidth, ocrHeight, request.region)

        // Debug-only; every call is a no-op in release, where R8 removes the recorder entirely.
        val evidence = ScanEvidenceRecorder.begin(request.context)
        val captureBytes = request.file.length()
        trace.markOffPath("evidence-begin")

        // One place where the still read ends, whichever path reaches it, so `upright` is recycled
        // exactly once including on the failure paths.
        val finished = AtomicBoolean(false)
        fun finish(
            report: NutritionParseReport,
            recognitionMs: Long,
            document: OcrDocument? = null,
        ) {
            if (!finished.compareAndSet(false, true)) return

            // Evidence capture is now a **move**, not a copy, and the PNG encode does not happen here
            // at all (2026-08-25).
            //
            // Previously this copied a ~3.5 MB JPEG and PNG-encoded a ~24 MB bitmap, synchronously,
            // between the parse finishing and the user seeing anything. That is seconds of file I/O
            // on the critical path of every debug scan, for artefacts nothing about the reading
            // depends on — a large part of why a measured device spent ~3.3 s of a 3.55 s scan
            // outside the recognizer.
            //
            // The move has to happen before the delete below, because it *replaces* the delete: it
            // takes ownership of the same file. The encode is scheduled after the result is
            // delivered, from the moved file rather than from `upright`, whose ownership passes to
            // the crop screen — see ScanEvidenceRecorder.recordPassAImageAsync.
            //
            val retaining = request.retaining
            if (retaining != null) {
                // Ownership of the bitmap transfers to the caller, which displays it on the crop
                // screen. Recycling it here would blank that screen — and the receiver cannot
                // re-decode it, because the capture file has just been deleted.
                retaining(
                    PassAResult(
                        sessionId = request.sessionId,
                        document = document,
                        report = report,
                        bitmap = upright,
                        evidence = evidence,
                        recognitionMs = recognitionMs,
                    ),
                )
            } else {
                upright?.recycle()
                request.onComplete(report)
            }

            // The user has the result. Everything from here is off the path they waited on, which is
            // why the trace records it as such rather than folding it into one total — the only build
            // that writes evidence is a debug build, so an undifferentiated figure measured on a phone
            // describes a build nobody ships. See ScanTrace.markOffPath.
            trace.mark("handoff")

            // The capture file is taken over **after** the handover, not before it.
            //
            // ### Why this moved (2026-09-01, P0-2)
            //
            // `consumeCapture` renames the temporary JPEG into the evidence folder, and falls back to
            // a byte copy when the rename is refused — which is what happens whenever the two paths
            // are on different filesystems, i.e. routinely. That copy is ~3 MB of synchronous file
            // I/O, and it used to run at the top of `finish`, *before* `retaining`/`onComplete`
            // delivered the result. The device evidence measured it at **9487 ms and 5232 ms** inside
            // scans of 20195 ms and 11189 ms.
            //
            // It was already flagged `markOffPath`, and that is precisely the trap this ordering
            // fixes: `markOffPath` changes which number gets *printed*, not when the work *runs*. The
            // stage was excluded from `user-visible` while the user was still sitting behind it. Work
            // is off the path when it happens after delivery — nothing else makes it so.
            //
            // Deferred wholesale to the writer thread rather than merely moved a few lines down: the
            // copy fallback is unbounded, so leaving it on this thread would only shrink the stall.
            ScanEvidenceRecorder.consumeCaptureAsync(evidence, request.file)
            trace.markOffPath("evidence-capture")

            // After the handover, on a background thread, from the moved capture file. Nothing the
            // user is waiting for depends on it, and a failure here cannot affect the scan.
            ScanEvidenceRecorder.recordPassAImageAsync(evidence)

            // Recording meta is itself a debug-only file write, and it used to run *before* the
            // handover — i.e. the diagnostics that exist to measure the scan were part of what they
            // measured. It is written from here so the breakdown it carries is complete (it now
            // includes the capture move and the handover) and costs the user nothing.
            ScanEvidenceRecorder.recordMeta(
                folder = evidence,
                captureBytes = captureBytes,
                jpegWidth = ocrWidth,
                jpegHeight = ocrHeight,
                passAWidth = ocrWidth,
                passAHeight = ocrHeight,
                exifRotationDegrees = loaded.rotationDegrees,
                decodedViaFallback = upright == null,
                region = request.region,
                totalMs = (System.nanoTime() - started) / 1_000_000,
                // Pass A's reading only. Resolution happens later, in the scanner, once a region is
                // known — so there is no verdict to record here and the field says so rather than
                // leaving a reader to assume this line is one.
                passAReading = report.reading::class.simpleName ?: "unknown",
                recognitionMs = recognitionMs,
                timingBreakdown = trace.summary(),
            )
            trace.markOffPath("evidence-meta")
            OcrDiagnosticsLogger.timing(trace.summary())

            inFlight.set(false)
            startPendingStillIfPossible()
        }

        val passAStarted = System.nanoTime()
        // ## The rejection guard, for the same reason the live path has one
        //
        // These listeners are dispatched on [parseExecutor], and [close] shuts it down. A task
        // submitted to a shut-down executor is **rejected at submission** — so on the
        // "capture, then immediately leave the scanner" path neither listener would ever run, and
        // two things would be left behind: the decoded full-resolution `upright` bitmap (~24 MB on
        // an 8 MP capture) would never be recycled, and `inFlight` would stay `true`.
        //
        // Bounded rather than cumulative — it needs a disposal to happen inside the recognition
        // window — but it is a real leak on a real gesture, so it is closed here rather than
        // reasoned away.
        //
        // The handler deliberately does **not** call `request.fail()`. A rejection only happens
        // during [close], i.e. the screen is gone: the scanner's stale-session guard would discard
        // the result anyway, and invoking the callback there would deliver a `NotFound` into a
        // disposed composition. Releasing the resources and staying silent is the honest teardown.
        //
        // The chain stays inline here, unlike the live path's, because this listener body closes
        // over `trace`, `ocrWidth`, `ocrHeight`, `evidence`, `loaded` and `finish` — extracting it
        // would mean threading six parameters through a helper purely to gain a rejection handler.
        runCatching {
            recognizer.process(input)
            // The still path's parse is the expensive one — the thirteenth session measured it at
            // 26-198 ms over documents of 36-341 elements — and it runs entirely inside this
            // listener, before `finish` hands the result over. Dispatched on [parseExecutor] so none
            // of it occupies the main thread while the user is watching a progress indicator.
            .addOnSuccessListener(parseExecutor) { text ->
                val passAMs = (System.nanoTime() - passAStarted) / 1_000_000
                trace.mark("mlkit")
                val document = trace.time("to-domain") {
                    MlKitOcrMapper.toDocument(text, ocrWidth, ocrHeight)
                }
                val parsed = trace.time("parse") {
                    NutritionTableParser.parseWithDiagnostics(document)
                }
                // Framing narrows an existing reading and can never create or promote one.
                val report = trace.time("relevance") {
                    ScanRegionRelevance.apply(parsed, request.region, ocrWidth, ocrHeight)
                }
                // Both evidence writes are queued, not performed, and both are queued only AFTER the
                // result has been handed over — the ordering is the fix, and the `Async` suffix alone
                // would not be enough if these still ran before `finish`.
                //
                // Measured on the recorded device session: `evidence-diagnostics` cost 582–1106 ms in
                // every bundle, and it ran between the parse completing and the result reaching the
                // screen. `ScanTrace` flagged it off-path so the printed `user-visible` total excluded
                // it, which made the trace describe the shipped build correctly while the debug build
                // the measurements came from still made the user wait.
                //
                // Neither call can affect the reading: both take an immutable snapshot on this thread
                // (the report and document are values; the recognizer's `Text` is flattened to a
                // string before the hand-off) and only the file write is deferred.
                finish(report, passAMs, document)

                // ## The logcat diagnostics are written AFTER the hand-over, for the same reason the
                // evidence files are (twelfth session)
                //
                // These two calls used to sit immediately above `finish`, so every debug scan
                // rendered the whole diagnostics report and emitted it to logcat *line by line*
                // before the result reached the screen. `OcrDiagnosticsReport.render` walks every
                // element and every reconstructed row, and each resulting line is its own `Log.d`.
                //
                // Measured on the twelfth session's own `meta.txt` stage breakdowns, where this work
                // fell inside the `handoff` window:
                //
                // ```
                //   77 elements -> handoff  26 ms       160 elements -> handoff  58 ms
                //  134 elements -> handoff  67 ms       290 elements -> handoff 123 ms
                //  546 elements -> handoff 281 ms
                // ```
                //
                // A hand-over is a callback invocation; it does not scale with document size. What
                // scaled was the logging, and on the 546-element tortilla it was **281 ms of the
                // 1341 ms the user waited** — for output only a developer reads, in a build only a
                // developer runs.
                //
                // This is the third time this repo has found debug-only work on the answer path
                // (2026-08-25 `ScanEvidenceRecorder`, 2026-09-01 `evidence-diagnostics`). It came
                // back through a different door because the earlier fixes moved *the evidence
                // recorder*, and this is the *logger*. The rule is the one already written there:
                // work is off the path when it happens after delivery, and nothing else makes it so.
                //
                // Safe to move: `report` and `document` are immutable values, `finish` does not
                // touch either, and the bitmap — the one thing `finish` can recycle — is not
                // referenced here.
                //
                // Deliberately **not** given a `ScanTrace` stage. `finish` takes `trace.summary()`
                // as its last act, so a mark recorded here would appear in no log at all — the same
                // dead instrumentation the `evidence-capture` mark once was. The measurement that
                // shows this worked is `handoff` collapsing in the next device bundle, which is
                // where the cost was being counted.
                OcrDiagnosticsLogger.report(passAMs, report)
                OcrDiagnosticsLogger.stillDiagnostics(document, report)

                ScanEvidenceRecorder.recordRecognizedTextAsync(evidence, text)
                ScanEvidenceRecorder.recordDiagnosticsAsync(evidence, document, report)
            }
            .addOnFailureListener(parseExecutor) { error ->
                OcrDiagnosticsLogger.failure("Still OCR failed", error)
                finish(
                    NutritionParseReport(LabelReading.NotFound, emptyList()),
                    recognitionMs = 0,
                    document = null,
                )
            }
        }.onFailure {
            // The executor is gone; neither listener will run, so nobody else will release these.
            upright?.recycle()
            inFlight.set(false)
            request.file.delete()
        }
    }

    /**
     * [full] asks for the complete pipeline trace (§9), which only a deliberate still capture gets:
     * a live frame arrives many times a second and would bury the capture that matters.
     */
    /**
     * The capture's pixel dimensions without decoding it, for the fallback path only.
     *
     * The ordinary path reads them off the decoded bitmap instead. This used to run unconditionally
     * before the real decode, which meant every scan read the same multi-megabyte file twice — once
     * for two integers the next call was about to produce anyway.
     */
    private fun boundsOf(file: File): BitmapFactory.Options =
        BitmapFactory.Options().also { options ->
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.absolutePath, options)
        }

    private fun parse(
        document: OcrDocument,
        started: Long,
        full: Boolean = false,
        /**
         * What the user framed, when known. Applied only to choose between candidates the parser has
         * already accepted — never to crop, never to promote a refused reading, never to supply a
         * basis. See [ScanRegionRelevance].
         */
        relevance: NormalizedRegion? = null,
    ): NutritionParseReport {
        val parsed = NutritionTableParser.parseWithDiagnostics(document)
        val report =
            ScanRegionRelevance.apply(parsed, relevance, document.width, document.height)
        OcrDiagnosticsLogger.report((System.nanoTime() - started) / 1_000_000, report)
        if (full) OcrDiagnosticsLogger.stillDiagnostics(document, report)
        return report
    }

    fun close() {
        closed.set(true)
        paused = true
        pendingStill.getAndSet(null)?.file?.delete()
        recognizer.close()
        // The parse thread belongs to this analyzer, which belongs to a camera session, so it is
        // released with it. `shutdown()` rather than `shutdownNow()`: a listener already dispatched
        // is finishing a parse whose result the session guard will discard anyway, and interrupting
        // it mid-way buys nothing while risking a half-applied teardown. The thread is a daemon, so
        // even a task that outlives the process's interest in it cannot keep the JVM alive.
        parseExecutor.shutdown()
    }

    private data class StillRequest(
        val context: Context,
        val file: File,
        val region: NormalizedRegion?,
        val onComplete: (NutritionParseReport) -> Unit,
        /** Set for the capture-then-confirm path, which needs the document and bitmap kept. */
        val retaining: ((PassAResult) -> Unit)? = null,
        val sessionId: Long = 0L,
    ) {
        /** Reports failure on whichever channel this request was made through. */
        fun fail() {
            val empty = NutritionParseReport(LabelReading.NotFound, emptyList())
            retaining?.invoke(
                PassAResult(
                    sessionId = sessionId,
                    document = null,
                    report = empty,
                    bitmap = null,
                    evidence = null,
                    recognitionMs = 0,
                ),
            ) ?: onComplete(empty)
        }
    }
}

/**
 * A completed first recognition, retained so the user's crop can be applied without recognising again.
 *
 * ## Why the document is kept rather than the image alone
 *
 * The whole point of the preferred architecture is that applying the user's rectangle costs no second
 * ML Kit pass. Re-recognising a crop is not a neutral clean-up: rescaling changes tokenisation, and on
 * the Kinder canary it turned the printed `(g)` into `(9)` — a well-formed single-digit carbohydrate
 * value that beat the real 53,5. Keeping [document] means the crop is applied to *the very elements
 * Pass A produced*, so no character can change between the whole-frame read and the cropped one.
 *
 * ## Lifetime
 *
 * [bitmap] is the decoded, EXIF-corrected capture, owned by the receiver from the moment it arrives.
 * It is deliberately not recycled by the analyzer: the crop screen displays it, and a Strategy B
 * fallback would crop it at full resolution. Call [recycle] when the capture session ends.
 */
data class PassAResult(
    /**
     * Echoes the id supplied at capture time.
     *
     * The state machine's guard against a race that is otherwise invisible: recognition takes
     * seconds, so a user who taps Retake mid-recognition would otherwise have the *previous*
     * capture's result arrive and overwrite the new one. Comparing this against the current session
     * makes a stale result discardable rather than merely unlikely.
     */
    val sessionId: Long,
    /** Null when recognition failed outright; [report] then carries `NotFound`. */
    val document: OcrDocument?,
    /** The whole-frame reading, before any crop is applied. */
    val report: NutritionParseReport,
    val bitmap: android.graphics.Bitmap?,
    /** The debug evidence folder for this capture, or null in release. */
    val evidence: File?,
    val recognitionMs: Long,
) {
    fun recycle() {
        bitmap?.takeIf { !it.isRecycled }?.recycle()
    }
}

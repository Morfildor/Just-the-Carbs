package app.justthecarbs.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Strategy B: a fresh recognition of the user-selected region, at native resolution (spec §3).
 *
 * ## Why a second recognition exists at all
 *
 * [SelectedTableReader] (Strategy A) filters Pass A's *elements*. That fixes interference — a prose
 * line merging into a table row — and it structurally cannot fix a recognition failure, because it
 * only ever removes elements Pass A already produced. If ML Kit never read the carbohydrate word or
 * value, no rectangle makes those characters appear.
 *
 * This class exists for that second case, and it was justified by measurement rather than by
 * argument. On the real corpus a native-resolution crop:
 *
 * - recovered **witte kaas** from `NotFound` to the correct `2.3`, and
 * - recovered **grated cheese** from the known-wrong `2.09` to the printed `2`.
 *
 * ## Why it is never the answer
 *
 * The same measurement showed it destroying canaries at other tightnesses — kinder at 5% inset,
 * sondey and yoghurt at 10%, and stokbrood behaving *non-monotonically* (readable at 0%, lost at 5%,
 * readable again at 10%). No property of the rectangle predicts which. So this produces
 * [RecognitionEvidence] and hands it to [EvidenceResolver], which may only corroborate or refuse.
 *
 * ## No resize, and why that is a hard rule
 *
 * The crop is taken with `Bitmap.createBitmap` over a sub-rectangle, which copies the source pixels
 * verbatim: no scaling, no filtering, no JPEG round-trip. The historic `(g)` -> `(9)` confident-wrong
 * came from a *rescaled* re-recognition, where resampling changed how ML Kit tokenised a glyph.
 * Removing the resize removes that specific mechanism. It does not make re-recognition trustworthy —
 * the measurements above prove damage without any rescaling — which is why the resolver still treats
 * this as evidence rather than truth.
 */
object SelectedRegionRecognizer {

    /**
     * Recognises [region] of [source] and parses it, or returns null when no second pass is warranted.
     *
     * Null means "nothing was attempted" — a degenerate, tiny or whole-frame selection — and is
     * distinct from a pass that ran and found nothing. The caller must not treat it as a refusal.
     *
     * Synchronous by design: it is called from the still-analysis worker thread, where the caller is
     * already off the main thread and wants the result before resolving evidence.
     */
    fun recognise(
        source: Bitmap?,
        region: NormalizedRegion?,
        observationId: PhysicalObservationId,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        /**
         * Which [EvidenceSource] to tag the result with.
         *
         * Defaults to [EvidenceSource.SELECTED_REGION_OCR] so every existing caller is unaffected.
         * [TargetedRereadRecognizer] passes [EvidenceSource.TARGETED_REREAD] for a second, narrower
         * pass over the same bitmap — same crop-and-recognise mechanics, same bitmap-ownership and
         * timeout guarantees, different evidence label because it answers a different question (a
         * targeted second look, not the user's own confirmed rectangle).
         */
        evidenceSource: EvidenceSource = EvidenceSource.SELECTED_REGION_OCR,
    ): RecognitionEvidence? {
        if (source == null || source.isRecycled) return null
        val crop = SelectedRegionCrop.toPixels(region, source.width, source.height) ?: return null

        val started = System.nanoTime()
        val trace = ScanTrace()
        var cropped: Bitmap? = null
        // Who frees the crop, decided by [CropRecycleOwnership] rather than by which `return` ran.
        // Starts as the caller so an exception before the task is even submitted still releases.
        var owner = CropRecycleOwnership.Owner.CALLER
        try {
            // Native pixels. createBitmap over a sub-rectangle is a copy, not a scale.
            cropped = Bitmap.createBitmap(source, crop.left, crop.top, crop.width, crop.height)
            trace.mark("crop")

            val latch = java.util.concurrent.CountDownLatch(1)
            // @Volatile in spirit: written on ML Kit's callback thread, read on this one. The latch
            // already establishes happens-before, so this is safe today — but `LabelAnalyzer` marks
            // its cross-thread fields explicitly and an unannotated `var` is one refactor away from
            // being read without the latch. An array cell is the local equivalent of that marker.
            val recognized = arrayOfNulls<com.google.mlkit.vision.text.Text>(1)
            val input = InputImage.fromBitmap(cropped, 0)
            trace.mark("mlkit-input")
            val task = recognizer.process(input)
                .addOnSuccessListener { recognized[0] = it; latch.countDown() }
                .addOnFailureListener {
                    OcrDiagnosticsLogger.failure("Selected-region OCR failed", it)
                    latch.countDown()
                }

            if (!latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                OcrDiagnosticsLogger.failure("Selected-region OCR timed out after ${timeoutMs}ms")
                // **The crash this avoids.** ML Kit is still reading `input`, which wraps `cropped`.
                // Recycling it here — which the `finally` below used to do unconditionally — frees
                // pixels out from under a running native detector. Hand the release to the task, the
                // same way [LabelAnalyzer.warmUp] releases its probe bitmap.
                val abandoned = cropped
                // Ownership transfers only if the listener is actually attached. If `addOnComplete‑
                // Listener` throws, `owner` stays CALLER and the `finally` releases as before —
                // a leaked 8 MP crop is worse than the synchronous release, and the alternative to
                // both is a crash.
                owner = runCatching {
                    task.addOnCompleteListener { abandoned?.takeIf { b -> !b.isRecycled }?.recycle() }
                    CropRecycleOwnership.of(CropRecycleOwnership.Completion.TIMED_OUT)
                }.getOrDefault(CropRecycleOwnership.Owner.CALLER)
                return null
            }
            trace.mark("mlkit")
            val text = recognized[0] ?: return null

            val document = trace.time("to-domain") {
                MlKitOcrMapper.toDocument(text, crop.width, crop.height)
            }
            val report = trace.time("parse") { NutritionTableParser.parseWithDiagnostics(document) }
            val label = if (evidenceSource == EvidenceSource.TARGETED_REREAD) "targeted-reread" else "strategy-B"
            OcrDiagnosticsLogger.timing("$label ${trace.summary()}")

            return RecognitionEvidence(
                source = evidenceSource,
                report = report,
                document = document,
                elapsedMs = (System.nanoTime() - started) / 1_000_000,
                // Where these pixels came from. `document` is measured in crop coordinates — its
                // width and height are the crop's, and every box is relative to the crop's own
                // origin — so anything drawing this pass's geometry over the full photograph, or
                // comparing it against Pass A's boxes, needs this to translate with. Carried rather
                // than left for the caller to re-derive: see [RecognitionEvidence.crop].
                crop = crop,
                physicalObservation = observationId,
            )
        } catch (error: Exception) {
            // A second opinion is a bonus, never a dependency: any failure here must leave the
            // scanner exactly as capable as it was with Pass A alone.
            OcrDiagnosticsLogger.failure("Selected-region OCR could not run", error)
            return null
        } finally {
            // The crop is a full copy of a region of an 8 MP bitmap and is useless after parsing.
            // Releasing it here bounds peak memory to source + one crop (§29).
            //
            // **Unless ML Kit still holds it.** On a timeout the task is still reading these pixels
            // and has taken responsibility for freeing them; recycling here as well would be the
            // use-after-free this guard exists to prevent. [CropRecycleOwnership] is the one place
            // that decides, so the `finally` cannot drift back to releasing unconditionally.
            if (owner == CropRecycleOwnership.Owner.CALLER) {
                cropped?.takeIf { !it.isRecycled }?.recycle()
            }
        }
    }

    /**
     * One recognizer for the whole process, created on first use and never closed.
     *
     * This used to be `getClient(...)` per call with a matching `close()` in the `finally`. Building
     * a text recognizer loads and initialises a native detector; doing that once per "Read table" tap
     * charges the user for setup on the one interaction where they are already waiting, and closing it
     * immediately guarantees the next tap pays it again. A recognizer is stateless between calls, so
     * there is nothing to share incorrectly.
     *
     * Deliberately not closed. Its lifetime is the process, like a thread pool's — and unlike
     * [LabelAnalyzer]'s own client, which belongs to one camera session and is correctly closed with
     * it. `close()`ing this one from any single screen would break every other caller.
     */
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Bound on the second pass.
     *
     * Reduced from 8 s (2026-08-25). Device measurement put ML Kit at ~0.29 s median and ~1.1 s worst
     * over a full 8 MP frame, and this pass reads a *sub-rectangle* of that frame.
     *
     * 5 s rather than the ~2 s those numbers alone would suggest, deliberately. The measurements come
     * from one flagship (SM-S928B); a budget device several times slower is entirely ordinary, and
     * this timeout's only effect in the normal case is nothing at all — the latch releases the moment
     * ML Kit returns. It is a hang guard. Setting it near the expected duration would convert "slow
     * phone" into "second opinion silently unavailable", which trades a *quality* regression for a
     * latency win the user does not experience, and quality is the higher priority here.
     *
     * What the reduction from 8 s buys is the pathological case: a genuinely stuck recognizer now
     * strands the "Read table" tap for 5 s rather than 8. The fallback is Strategy A alone — the
     * pre-pass behaviour, safe but occasionally less capable.
     *
     * The lower bound is unverified on low-end hardware. See the physical-device QA gate.
     */
    private const val DEFAULT_TIMEOUT_MS = 5_000L
}

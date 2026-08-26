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
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): RecognitionEvidence? {
        if (source == null || source.isRecycled) return null
        val crop = SelectedRegionCrop.toPixels(region, source.width, source.height) ?: return null

        val started = System.nanoTime()
        val trace = ScanTrace()
        var cropped: Bitmap? = null
        try {
            // Native pixels. createBitmap over a sub-rectangle is a copy, not a scale.
            cropped = Bitmap.createBitmap(source, crop.left, crop.top, crop.width, crop.height)
            trace.mark("crop")

            val latch = java.util.concurrent.CountDownLatch(1)
            var recognized: com.google.mlkit.vision.text.Text? = null
            val input = InputImage.fromBitmap(cropped, 0)
            trace.mark("mlkit-input")
            recognizer.process(input)
                .addOnSuccessListener { recognized = it; latch.countDown() }
                .addOnFailureListener {
                    OcrDiagnosticsLogger.failure("Selected-region OCR failed", it)
                    latch.countDown()
                }

            if (!latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                OcrDiagnosticsLogger.failure("Selected-region OCR timed out after ${timeoutMs}ms")
                return null
            }
            trace.mark("mlkit")
            val text = recognized ?: return null

            val document = trace.time("to-domain") {
                MlKitOcrMapper.toDocument(text, crop.width, crop.height)
            }
            val report = trace.time("parse") { NutritionTableParser.parseWithDiagnostics(document) }
            OcrDiagnosticsLogger.timing("strategy-B ${trace.summary()}")

            return RecognitionEvidence(
                source = EvidenceSource.SELECTED_REGION_OCR,
                report = report,
                document = document,
                elapsedMs = (System.nanoTime() - started) / 1_000_000,
            )
        } catch (error: Exception) {
            // A second opinion is a bonus, never a dependency: any failure here must leave the
            // scanner exactly as capable as it was with Pass A alone.
            OcrDiagnosticsLogger.failure("Selected-region OCR could not run", error)
            return null
        } finally {
            // The crop is a full copy of a region of an 8 MP bitmap and is useless after parsing.
            // Releasing it here bounds peak memory to source + one crop (§29).
            cropped?.takeIf { !it.isRecycled }?.recycle()
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

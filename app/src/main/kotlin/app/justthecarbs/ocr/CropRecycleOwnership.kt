package app.justthecarbs.ocr

/**
 * Who releases Strategy B's cropped bitmap, once the second recognition has stopped being waited on.
 *
 * ## The defect this exists for
 *
 * [SelectedRegionRecognizer.recognise] is synchronous: it hands ML Kit an
 * [com.google.mlkit.vision.common.InputImage] wrapping a freshly cropped bitmap, waits on a
 * [java.util.concurrent.CountDownLatch], and releases the crop in a `finally` block so peak memory
 * stays bounded to source + one crop.
 *
 * That is correct on every path except one. **Giving up waiting is not the same as the work
 * stopping.** On timeout the function returned `null` straight into the `finally`, recycling the
 * pixels while `recognizer.process(input)` was still reading them — a use-after-free in a native
 * detector, on ML Kit's own callback thread rather than the worker's, so the `catch (Exception)`
 * around the body could not see it either.
 *
 * It is the same mechanism this repo already fixed once, for the async evidence writer: "the
 * bitmap's ownership transfers to the crop screen, which recycles it on Retake, so encoding it in
 * the background races a recycle into a native crash."
 *
 * ## The rule
 *
 * > The crop is released by whoever is last to touch it. When ML Kit has finished — successfully or
 * > not — that is the caller, synchronously. When the caller stopped waiting but ML Kit did not stop
 * > working, that is the recognizer, through a completion listener.
 *
 * ## Why this is a type rather than a `finally` clause
 *
 * The decision used to live inside a function needing a real [android.graphics.Bitmap] and a real
 * recognizer, so **no JVM test could reach it** — the same shape as the eighth session's P0 (an
 * anonymous `else` inside a composable that binds a camera) and the ninth's (a local `val` in the
 * same file). This repo's standing conclusion from both is that a rule no test can reach is a rule
 * that can be silently reverted.
 *
 * The correct pattern already existed one file away: [LabelAnalyzer.warmUp] recycles its 1x1 probe
 * from `addOnCompleteListener`, never from the calling frame. This states that pattern as a rule
 * instead of leaving it as a habit.
 *
 * ## What this deliberately does not do
 *
 * It does not cancel the recognition, and it must not start doing so. ML Kit's task has no
 * cancellation contract this app can rely on, and a timeout is already the pathological case — the
 * honest response is to stop waiting for the answer while letting the work finish and clean up after
 * itself, not to add a second race about who tears it down.
 */
internal object CropRecycleOwnership {

    /** How the wait for the second recognition ended. */
    enum class Completion(
        /**
         * Whether ML Kit has finished with the input image.
         *
         * The premise of the whole rule: only a finished task has handed the pixels back, and only
         * then may the caller free them.
         */
        val taskFinished: Boolean,
    ) {
        /** ML Kit returned text. The task is done and the pixels are the caller's again. */
        SUCCEEDED(taskFinished = true),

        /** ML Kit's failure listener fired. Also done — a failed task is a finished task. */
        FAILED(taskFinished = true),

        /**
         * The latch expired first. ML Kit is **still running** and still holds the input image.
         *
         * Measured as reachable rather than hypothetical: [SelectedRegionRecognizer]'s own KDoc
         * documents the 8 s -> 5 s reduction in terms of "a genuinely stuck recognizer", which is
         * precisely this state.
         */
        TIMED_OUT(taskFinished = false),
    }

    /** Who must call `recycle()`. */
    enum class Owner {
        /** The worker thread, synchronously, in its `finally`. The ordinary path. */
        CALLER,

        /**
         * ML Kit, through a completion listener attached to the task.
         *
         * The crop outlives the call that made it, which is the point: it is freed when the detector
         * is genuinely done with it rather than when the caller lost patience.
         */
        RECOGNIZER,
    }

    /**
     * Who releases the crop, given how the wait ended.
     *
     * Total over [Completion], so a new completion state fails to compile rather than silently
     * inheriting the synchronous release — which is the direction that crashes.
     */
    fun of(completion: Completion): Owner = when (completion) {
        Completion.SUCCEEDED, Completion.FAILED -> Owner.CALLER
        Completion.TIMED_OUT -> Owner.RECOGNIZER
    }
}

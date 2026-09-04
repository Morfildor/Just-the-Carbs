package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who releases Strategy B's cropped bitmap, and when.
 *
 * ## The defect this pins
 *
 * [SelectedRegionRecognizer.recognise] waits on a [java.util.concurrent.CountDownLatch] for ML Kit
 * and gives up after [SelectedRegionRecognizer.DEFAULT_TIMEOUT_MS]. On that path it used to `return
 * null` straight into a `finally` block that recycled the crop — **while `recognizer.process(input)`
 * was still running against an [com.google.mlkit.vision.common.InputImage] wrapping those exact
 * pixels.**
 *
 * A native detector reading a recycled bitmap is a use-after-free. It is the same mechanism this
 * repo already fixed for the async evidence writer, where "the bitmap's ownership transfers to the
 * crop screen, which recycles it on Retake, so encoding it in the background races a recycle into a
 * native crash".
 *
 * The `catch (Exception)` around the body does **not** cover it: the crash happens on ML Kit's own
 * callback thread, not on the worker thread that is inside the `try`.
 *
 * ## Why this is a pure test of a pure object
 *
 * The recycle decision used to be a `finally` clause inside a function that needs a real
 * [android.graphics.Bitmap] and a real recognizer, so no JVM test could reach it — the same shape as
 * the eighth session's P0 (an anonymous `else` in a composable) and the ninth's (a local `val` in
 * one). [CropRecycleOwnership] is that decision as a value, so the rule is reachable from the JVM
 * and cannot be silently reverted.
 *
 * The correct pattern already exists in this codebase and is what this asserts:
 * [LabelAnalyzer.warmUp] recycles from `addOnCompleteListener`, i.e. when ML Kit says it is done,
 * never when the caller stops waiting.
 */
class CropRecycleOwnershipTest {

    @Test
    fun `a completed recognition is recycled by the caller`() {
        // ML Kit answered and is no longer touching the pixels, so the worker owns them and the
        // synchronous release keeps peak memory bounded to source + one crop.
        assertEquals(
            CropRecycleOwnership.Owner.CALLER,
            CropRecycleOwnership.of(CropRecycleOwnership.Completion.SUCCEEDED),
        )
    }

    @Test
    fun `a failed recognition is recycled by the caller`() {
        // A failure listener fired, which means the task is finished. Same ownership as success.
        assertEquals(
            CropRecycleOwnership.Owner.CALLER,
            CropRecycleOwnership.of(CropRecycleOwnership.Completion.FAILED),
        )
    }

    /** The defect. This is the case that used to recycle underneath a running detector. */
    @Test
    fun `a timed out recognition is NOT recycled by the caller`() {
        assertEquals(
            CropRecycleOwnership.Owner.RECOGNIZER,
            CropRecycleOwnership.of(CropRecycleOwnership.Completion.TIMED_OUT),
        )
    }

    @Test
    fun `only a timeout defers the release`() {
        // Stated as a property over the whole enum rather than as three cases, so a new completion
        // state has to decide this question rather than inheriting an answer by omission.
        CropRecycleOwnership.Completion.entries.forEach { completion ->
            val deferred = CropRecycleOwnership.of(completion) == CropRecycleOwnership.Owner.RECOGNIZER
            assertEquals(
                "$completion must defer the release exactly when ML Kit may still hold the bitmap",
                completion == CropRecycleOwnership.Completion.TIMED_OUT,
                deferred,
            )
        }
    }

    @Test
    fun `the crop is never abandoned - every completion has an owner`() {
        // A crop is a full copy of a region of an 8 MP bitmap. "Nobody recycles it" would be a leak
        // rather than a crash, which is quieter and therefore worse to discover.
        CropRecycleOwnership.Completion.entries.forEach { completion ->
            assertTrue(
                "$completion must name an owner for the crop",
                CropRecycleOwnership.of(completion) in CropRecycleOwnership.Owner.entries,
            )
        }
    }

    @Test
    fun `the caller releases synchronously only when the task is finished`() {
        // The invariant in the direction that matters for the crash: if the caller recycles, ML Kit
        // must already have handed the pixels back.
        CropRecycleOwnership.Completion.entries
            .filter { CropRecycleOwnership.of(it) == CropRecycleOwnership.Owner.CALLER }
            .forEach { completion ->
                assertTrue(
                    "$completion lets the caller recycle, so the task must be finished",
                    completion.taskFinished,
                )
            }
    }

    @Test
    fun `a timeout leaves the task unfinished`() {
        // The premise of the whole rule, asserted rather than assumed: giving up waiting is not the
        // same as the work stopping.
        assertFalse(CropRecycleOwnership.Completion.TIMED_OUT.taskFinished)
    }
}

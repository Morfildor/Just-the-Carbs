package app.justthecarbs.ocr

import kotlin.math.abs

/**
 * Whether a confirmed crop differs enough from the last one to be worth recognising (1.0.3 P2).
 *
 * ## The waste
 *
 * When the automatic post-capture attempt declines, the crop screen opens on **the same rectangle
 * the attempt used** — `cropSelection` is assigned that region on the line above the automatic
 * call. A user who judges the proposed box already correct and taps *Read table* therefore runs
 * `SelectedTableResolution.resolve` over an identical region: the same Strategy A re-parse of the
 * same retained elements, and the same Strategy B ML Kit pass (measured at ~400 ms) over the same
 * pixels of the same bitmap.
 *
 * Recognition is deterministic over identical input, so the result is necessarily the outcome that
 * already declined. The user waits a second time to be told the same thing, and the app then shows
 * the assisted path it could have shown immediately.
 *
 * ## What is deliberately NOT changed
 *
 * A crop the user genuinely moved is new information and is always recognised. This never skips a
 * pass that could produce a different answer — that would be trading correctness for latency, and
 * the crop screen exists precisely so the user can supply that information.
 *
 * ## Why a tolerance
 *
 * The rectangle survives drag gestures and float arithmetic, so a corner touched and returned does
 * not necessarily produce the bit-identical double it started from. Exact equality would call that
 * a change and rerun the pass — the very duplicate this removes. The tolerance is a fraction of the
 * frame because [NormalizedRegion] is expressed in fractions, and it is set far below what a finger
 * can deliberately place, so no intentional adjustment can hide inside it.
 */
internal object CropChange {

    /**
     * How far an edge may move and still count as unchanged, as a fraction of the frame.
     *
     * 0.2% is a couple of pixels on a phone preview: comfortably above float round-trip noise and
     * far below the smallest drag a user can make on purpose. Both bounds are pinned by
     * `CropChangeTest.the tolerance is far below any deliberate drag`.
     */
    const val TOLERANCE = 0.002

    /**
     * True when [confirmed] differs from [previous] enough that recognising it could tell the app
     * something new.
     *
     * A null [previous] is always material: with nothing to compare against there is no evidence of
     * duplication, so the pass runs. That is what keeps a fresh capture — and any *Read table* that
     * follows no automatic attempt — behaving exactly as before.
     */
    fun isMaterial(previous: NormalizedRegion?, confirmed: NormalizedRegion): Boolean {
        if (previous == null) return true
        return abs(previous.left - confirmed.left) > TOLERANCE ||
            abs(previous.top - confirmed.top) > TOLERANCE ||
            abs(previous.right - confirmed.right) > TOLERANCE ||
            abs(previous.bottom - confirmed.bottom) > TOLERANCE
    }
}

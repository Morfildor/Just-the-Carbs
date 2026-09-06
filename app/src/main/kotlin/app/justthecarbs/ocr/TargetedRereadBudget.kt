package app.justthecarbs.ocr

/**
 * Bounds how many targeted rereads (§4) may run for one capture, and for how long, across every
 * call to [SelectedTableResolution.resolve] that capture makes.
 *
 * ## Why this cannot live inside [SelectedTableResolution.resolve] itself
 *
 * `resolve` is called once for the automatic post-capture attempt and again for every subsequent
 * crop adjustment the user confirms on the **same** photograph — each call is otherwise stateless and
 * knows nothing about the calls before it. Without a budget owned across those calls, a user dragging
 * the crop rectangle back and forth (or the same automatic-then-confirmed sequence every capture
 * already makes) would pay for a fresh native-resolution reread on every single call, unbounded.
 *
 * This is deliberately a plain mutable holder, not a pure function: it is capture-scoped state, owned
 * and reset by the caller exactly like [PhysicalObservationId] and `captureSession` already are in
 * `LabelScannerScreen` — a new capture gets a fresh budget, the same capture's crop retries share one.
 */
class TargetedRereadBudget(
    /** How many targeted rereads one capture may spend. */
    private val maxAttempts: Int = MAX_ATTEMPTS_PER_CAPTURE,
    /** The shared wall-clock ceiling, in milliseconds, one capture's rereads may spend in total. */
    private val maxElapsedMs: Long = MAX_ELAPSED_MS_PER_CAPTURE,
) {
    private var attemptsSpent = 0
    private var elapsedMsSpent = 0L

    /** Regions already attempted this capture, so an unchanged crop cannot re-spend the budget. */
    private val attemptedRegions = mutableListOf<NormalizedRegion>()

    /**
     * Whether a reread of [region] is worth attempting: the attempt count and elapsed-time ceilings
     * both have room, and [region] is not equivalent to one already attempted this capture.
     *
     * Equivalence uses [CropChange.isMaterial]'s own tolerance, so a user re-confirming an
     * imperceptibly different crop cannot manufacture a "new" region and re-spend the budget — the
     * same reasoning [CropChange] already applies to deciding whether a crop is worth re-recognising
     * at all.
     */
    fun mayAttempt(region: NormalizedRegion): Boolean {
        if (attemptsSpent >= maxAttempts) return false
        if (elapsedMsSpent >= maxElapsedMs) return false
        if (attemptedRegions.any { !CropChange.isMaterial(it, region) }) return false
        return true
    }

    /** Records that a reread of [region] ran and cost [elapsedMs]. */
    fun record(region: NormalizedRegion, elapsedMs: Long) {
        attemptsSpent++
        elapsedMsSpent += elapsedMs
        attemptedRegions += region
    }

    companion object {
        /**
         * At most one reread per capture attempt in the ordinary case, with headroom for a second
         * only because a user may confirm several different crops of one photograph in one session
         * and each is a materially different question. Not unbounded: a dosing-input screen must not
         * let repeated crop fiddling spend unlimited native-resolution recognitions.
         */
        const val MAX_ATTEMPTS_PER_CAPTURE = 3

        /**
         * A native-resolution ML Kit pass is measured at roughly 300-500ms elsewhere in this
         * pipeline (see [SelectedRegionRecognizer]'s own timeout). Three seconds is comfortably above
         * three ordinary attempts and still a hard ceiling against a pathological sequence of very
         * slow recognitions on a low-end device.
         */
        const val MAX_ELAPSED_MS_PER_CAPTURE = 3_000L
    }
}

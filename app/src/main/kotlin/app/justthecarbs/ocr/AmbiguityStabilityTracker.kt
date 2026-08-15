package app.justthecarbs.ocr

/**
 * Decides when a live-frame [LabelReading] should reach the UI.
 *
 * `Confident` always surfaces immediately. `NotFound` never surfaces and resets tracking.
 * `Ambiguous` is the one outcome that must NOT surface on the first frame that sees it: a single
 * incomplete frame (e.g. the carbohydrate row visible but the per-100 header not yet in view) is
 * indistinguishable from genuine ambiguity, and pausing live scanning on it would strand the user
 * on a result the very next frame might have resolved. It only surfaces once the same
 * interpretation has repeated for [stableFrameCount] consecutive frames, or [stableDurationNanos]
 * has elapsed while an ambiguous reading kept recurring — whichever comes first.
 *
 * A still-capture result has no future frame to wait for, so callers analyzing a still image
 * should not route it through this tracker at all and instead surface it immediately.
 */
class AmbiguityStabilityTracker(
    private val stableFrameCount: Int = 3,
    private val stableDurationNanos: Long = 800_000_000L,
) {
    private var signature: List<CandidateSignature>? = null
    private var firstSeenNanos: Long = 0L
    private var consecutiveFrames: Int = 0

    /** Returns the reading to surface now, or null if live scanning should continue. */
    fun onFrame(reading: LabelReading, nowNanos: Long): LabelReading? = when (reading) {
        is LabelReading.Confident -> {
            reset()
            reading
        }
        LabelReading.NotFound -> {
            reset()
            null
        }
        is LabelReading.Ambiguous -> onAmbiguous(reading, nowNanos)
    }

    private fun onAmbiguous(reading: LabelReading.Ambiguous, nowNanos: Long): LabelReading? {
        val newSignature = reading.candidates.map { it.signature() }
        if (newSignature == signature) {
            consecutiveFrames += 1
        } else {
            signature = newSignature
            firstSeenNanos = nowNanos
            consecutiveFrames = 1
        }

        val stableByCount = consecutiveFrames >= stableFrameCount
        val stableByDuration = nowNanos - firstSeenNanos >= stableDurationNanos
        return if (stableByCount || stableByDuration) reading else null
    }

    /** Clears tracking state, e.g. after the user dismisses a surfaced result and resumes scanning. */
    fun reset() {
        signature = null
        firstSeenNanos = 0L
        consecutiveFrames = 0
    }

    private fun CarbCandidate.signature() = CandidateSignature(value.stripTrailingZeros(), basis)

    private data class CandidateSignature(
        val value: java.math.BigDecimal,
        val basis: app.justthecarbs.domain.NutritionBasis?,
    )
}

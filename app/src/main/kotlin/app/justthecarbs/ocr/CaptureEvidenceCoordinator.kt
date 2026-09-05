package app.justthecarbs.ocr

import java.util.concurrent.atomic.AtomicLong

/**
 * Separates two identities [LabelScannerScreen] previously conflated into one `AtomicLong`
 * (`captureSession`):
 *
 * - **Work generation** answers "is this in-flight async callback still current?" It must bump on
 *   every new capture attempt, so a still-recognition result from an abandoned attempt cannot land.
 * - **Aim epoch** answers "which pre-shutter live-camera stream does this frame belong to?" It must
 *   NOT bump when a shutter is pressed — a frame recorded while the user was aiming, before the tap,
 *   belongs to the same aim as the still that tap produces. It bumps only when the user starts a
 *   genuinely new aim: retake, resume-after-dispose, or leaving and re-entering the screen.
 *
 * ## Why one counter was wrong
 *
 * `LabelScannerScreen.captureSession` bumped at the top of `captureLabel()`, before the still image
 * was even captured. Live frames recorded while framing the shot (necessarily before the tap) were
 * stamped with the pre-tap value. The moment the tap incremented the counter,
 * `LiveEvidenceBuffer.asEvidence(now, session)` filtered on `sessionId == session` using the
 * post-tap value — excluding every one of those pre-tap frames. The buffer's own KDoc documented
 * this exclusion as intentional, which means the conflation was structural, not a wiring slip.
 */
class CaptureEvidenceCoordinator {
    private val aimEpochCounter = AtomicLong(0L)
    private val workGenerationCounter = AtomicLong(0L)

    val aimEpoch: Long get() = aimEpochCounter.get()
    val workGeneration: Long get() = workGenerationCounter.get()

    /** Call on retake, resume-after-dispose, or leaving/re-entering the screen. */
    fun beginNewAim(): Long = aimEpochCounter.incrementAndGet()

    /** Call at the start of every new capture attempt, to invalidate stale in-flight work. */
    fun beginNewWork(): Long = workGenerationCounter.incrementAndGet()

    /** True iff [generation] is still the current work generation. */
    fun isCurrentWork(generation: Long): Boolean = generation == workGenerationCounter.get()

    /**
     * One frozen answer to "what did the live camera see, right before the shutter fired?" — taken
     * atomically, before any other shutter-handling side effect (work-generation bump, analyzer
     * pause, autofocus, image capture) runs.
     *
     * Freezing here rather than reading the buffer later is what stops OCR latency from silently
     * expiring valid evidence: the review measured 477-2458ms for the still pipeline on real
     * hardware, comfortably longer than [LiveEvidenceBuffer]'s 1500ms window, so a query issued
     * after the still pipeline completes can find nothing left even when the camera saw a stable
     * reading seconds ago relative to when it actually mattered — the shutter press.
     */
    data class LiveEvidenceSnapshot(
        val aimEpoch: Long,
        val candidate: CarbCandidate?,
        val newestFrameAgeMs: Long?,
        val observationCount: Int,
        val rejectionReason: String?,
    )

    /**
     * [nowElapsed] MUST be the same monotonic clock source (`SystemClock.elapsedRealtime()` on
     * Android) used to record every observation in [buffer] — see [LiveEvidenceBuffer.record]'s
     * KDoc. A wall-clock read here would silently corrupt every age/window comparison against
     * observations timestamped with elapsed time.
     */
    fun freezeAtShutter(buffer: LiveEvidenceBuffer, nowElapsed: Long): LiveEvidenceSnapshot {
        val epoch = aimEpoch
        val snapshotList = buffer.snapshot().filter { it.aimEpoch == epoch }
        val candidate = buffer.stableConsensus(nowElapsed, epoch)
        val newestAge = snapshotList.maxOfOrNull { it.timestampMs }?.let { nowElapsed - it }
        val rejection = when {
            snapshotList.isEmpty() -> "no observations recorded for this aim epoch"
            candidate == null -> "recent observations did not reach stable agreement"
            else -> null
        }
        return LiveEvidenceSnapshot(
            aimEpoch = epoch,
            candidate = candidate,
            newestFrameAgeMs = newestAge,
            observationCount = snapshotList.size,
            rejectionReason = rejection,
        )
    }
}

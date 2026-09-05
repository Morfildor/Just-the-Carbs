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
}

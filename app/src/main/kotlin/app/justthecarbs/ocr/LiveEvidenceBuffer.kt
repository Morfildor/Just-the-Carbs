package app.justthecarbs.ocr

import java.math.BigDecimal

/**
 * A bounded history of pre-shutter live interpretations (spec §5, §9).
 *
 * ## The failure this exists to fix
 *
 * On a physical device the Stroopwafel label reaches *"Table in view — tap to capture"* — a state
 * only produced when a live frame yielded a usable interpretation — and the still capture the user
 * then deliberately takes returns *"Couldn't confidently find carbohydrates"*. The live reading was
 * written into a field read solely to choose that one string, and then discarded. The app had the
 * answer and threw it away.
 *
 * ## What this must NOT become
 *
 * The capture-first decision stands and is not weakened here. A live frame still cannot:
 *
 * - navigate, finish the scan, or become the result on its own;
 * - stop the user before capture;
 * - be accepted without a deliberate shutter press.
 *
 * This only *retains* what the live path already computed, so that after the user has committed to a
 * capture, a still-path failure does not silently discard corroborating evidence. Anything this
 * buffer offers arrives through [EvidenceResolver], which never lets a lone live frame resolve a
 * scan — it can only corroborate a still reading or be proposed for explicit verification.
 *
 * ## Why consensus rather than "the last good frame"
 *
 * A single frame that happened to interpret is exactly the unstable signal the capture-first change
 * removed from the answer path. Requiring several *recent, agreeing* frames means the retained value
 * is one the camera saw repeatedly while the user was aiming — which is the property that makes it
 * worth anything at all.
 *
 * ## Concurrency and session binding (§9, startup-hardening pass)
 *
 * [record] is called from the analyzer's frame callback (marshalled onto the main thread by its
 * caller) on essentially every analysed frame; [clear] is called from Compose on retake and on
 * leaving the screen; [stableConsensus]/[asEvidence] are read from a background dispatcher inside
 * the still-recognition coroutine ([app.justthecarbs.ui.scan.LabelScannerScreen]'s `saveScope.launch`
 * switches to `Dispatchers.IO` around exactly this call). Three call sites, two different threads,
 * one plain [ArrayDeque] with no synchronization at all — a `ConcurrentModificationException` or a
 * torn read was always reachable, it simply needed an analysed frame to land at the wrong instant
 * relative to the still-recognition coroutine reading the buffer. `synchronized` around every method
 * that touches [observations] closes that: cheap (record/clear/read are all short, uncontended almost
 * always — a live frame every 30-100 ms against one still-recognition read per capture) and correct
 * regardless of which thread calls what.
 *
 * Synchronization alone is not enough, though: it stops the deque from corrupting itself, but it does
 * nothing to stop a frame from a *different* scan — a different package the user swept the camera
 * past, or a recognition still in flight after a Retake — from silently corroborating the capture
 * being evaluated now. [record] now takes the [sessionId] the frame belongs to (the caller's own
 * generation counter — see `LabelScannerScreen.captureSession`, already bumped on dispose, retake and
 * every new capture), and [stableConsensus]/[asEvidence] only ever consider observations whose
 * `sessionId` matches the one being asked about. A live frame from session 3 can never corroborate
 * session 4's still capture, even if it is still inside [windowMs] when read.
 */
class LiveEvidenceBuffer(
    /**
     * How many observations to retain.
     *
     * Sized from measured camera cadence rather than picked: analysis runs at roughly 10–30 fps and
     * `STRATEGY_KEEP_ONLY_LATEST` drops frames under load, so ~12 entries is on the order of a second
     * of aiming. Large enough to span the shutter press, small enough that a reading from a different
     * package the user swept past has aged out.
     */
    private val capacity: Int = DEFAULT_CAPACITY,
    /**
     * How old an observation may be, in milliseconds, and still count toward consensus.
     *
     * The user may have moved the phone to a different part of the package. Time-bounding is what
     * keeps this "what the camera was seeing as the shutter was pressed" rather than "anything it
     * ever saw".
     */
    private val windowMs: Long = DEFAULT_WINDOW_MS,
) {

    /**
     * One live interpretation, when it happened, and which capture session it belongs to.
     *
     * [sessionId] defaults to 0 so every pre-existing caller and test — none of which knows or cares
     * about session scoping — keeps behaving exactly as before: a buffer used with the default id on
     * every call is equivalent to the pre-§9 buffer, one undivided stream of observations.
     */
    data class Observation(
        val reading: LabelReading,
        val timestampMs: Long,
        val sessionId: Long = 0L,
    )

    // Guarded by `lock` — see the class KDoc's "Concurrency and session binding" section. A plain
    // ArrayDeque has no thread-safety of its own, and this buffer is genuinely written from the main
    // thread (record/clear) and read from a background dispatcher (stableConsensus/asEvidence,
    // called from inside `withContext(Dispatchers.IO)` in LabelScannerScreen) — not a hypothetical.
    private val lock = Any()
    private val observations = ArrayDeque<Observation>()

    /**
     * Records a live-frame interpretation. Cheap; called on every analysed frame.
     *
     * [sessionId] is the caller's own generation counter for the current capture attempt (see the
     * class KDoc) — stamped on the observation so a later read for a *different* session can never
     * count this frame toward its consensus, however recent it is.
     */
    fun record(reading: LabelReading, timestampMs: Long, sessionId: Long = 0L) {
        // NotFound carries no evidence and would only dilute the window.
        if (reading is LabelReading.NotFound) return
        synchronized(lock) {
            observations.addLast(Observation(reading, timestampMs, sessionId))
            while (observations.size > capacity) observations.removeFirst()
        }
    }

    /** Forgets everything. Called on retake and on leaving the screen, so state cannot cross sessions. */
    fun clear() = synchronized(lock) { observations.clear() }

    /** Snapshot for the evidence bundle; ordering is oldest-first. */
    fun snapshot(): List<Observation> = synchronized(lock) { observations.toList() }

    /**
     * The value a stable majority of recent frames from [sessionId] agreed on, or null.
     *
     * Requires [MIN_AGREEING_FRAMES] confident observations inside [windowMs] that agree on both
     * value and basis, **and** were recorded under the same [sessionId] being asked about — a frame
     * from an abandoned or different capture attempt cannot corroborate this one, however recent.
     * Comparison is numeric (`compareTo`), because `BigDecimal.equals` is scale-sensitive and would
     * treat `5` and `5.0` as disagreement.
     *
     * Returns null when frames disagreed — a camera that saw two different values while being aimed
     * has demonstrated instability, which is a reason to stay quiet rather than to pick one.
     */
    fun stableConsensus(nowMs: Long, sessionId: Long = 0L): CarbCandidate? {
        val recent = synchronized(lock) { observations.toList() }
            .filter { it.sessionId == sessionId && nowMs - it.timestampMs <= windowMs }
            .mapNotNull { (it.reading as? LabelReading.Confident)?.candidate }
        if (recent.size < MIN_AGREEING_FRAMES) return null

        val first = recent.first()
        val allAgree = recent.all { candidate ->
            candidate.basis != null &&
                candidate.basis == first.basis &&
                candidate.value.compareTo(first.value) == 0
        }
        return if (allAgree) first else null
    }

    /**
     * Consensus wrapped as resolver evidence, or null.
     *
     * [documentFor] supplies the document the winning candidate came from when one is available; it
     * is optional because live frames are transient and the buffer deliberately does not retain
     * whole documents (that would pin several megabytes of geometry per second of aiming).
     */
    fun asEvidence(
        nowMs: Long,
        sessionId: Long = 0L,
        documentFor: (CarbCandidate) -> OcrDocument? = { null },
    ): RecognitionEvidence? {
        val candidate = stableConsensus(nowMs, sessionId) ?: return null
        return RecognitionEvidence(
            source = EvidenceSource.LIVE_STABLE_FRAME,
            report = NutritionParseReport(LabelReading.Confident(candidate), emptyList()),
            document = documentFor(candidate),
        )
    }

    private companion object {
        const val DEFAULT_CAPACITY = 12

        /**
         * Roughly a second and a half of aiming.
         *
         * Long enough to span the moment between the last analysed frame and the shutter firing
         * (capture involves a focus sweep bounded at 1200 ms), short enough that a reading from a
         * package the user swept past cannot corroborate the one they actually photographed.
         */
        const val DEFAULT_WINDOW_MS = 1_500L

        /**
         * Three, matching [AmbiguityStabilityTracker]'s existing stability requirement.
         *
         * Deliberately the same number as the app's other live-stability rule: two different
         * definitions of "stable" on the same camera stream would be a source of confusing,
         * inconsistent behaviour that nobody could reason about from the UI.
         */
        const val MIN_AGREEING_FRAMES = 3
    }
}

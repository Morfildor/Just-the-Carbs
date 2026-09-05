package app.justthecarbs.ocr

import java.math.BigDecimal

/**
 * A bounded history of pre-shutter live interpretations (spec §5, §9), scoped by aim epoch rather
 * than by a single conflated session counter.
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
 * ## Aim epoch, not "session"
 *
 * The parameter previously named `sessionId` is renamed [Observation.aimEpoch] to make the call site
 * self-documenting: it identifies which pre-shutter live-camera stream an observation belongs to,
 * and it must be supplied by [CaptureEvidenceCoordinator.aimEpoch] — which does NOT change on a
 * shutter press — never by a per-capture work-generation counter. See
 * [CaptureEvidenceCoordinator]'s KDoc for why conflating the two excluded almost all pre-shutter
 * evidence in the previous design.
 *
 * ## Contiguous-suffix consensus, not "any 3 agreeing in the window"
 *
 * The previous rule filtered the whole time window to confident observations and required 3 to
 * agree, which let three *old* agreeing frames remain "stable" even if the camera had since moved
 * to an ambiguous or conflicting reading. Consensus must reflect what the camera was seeing *right
 * before the shutter*, so a recent disagreement or non-confident reading must invalidate an older
 * agreeing run: see [stableConsensus]. This is also why [LabelReading.NotFound] and
 * [LabelReading.Ambiguous] are now stored by [record] (previously `NotFound` was dropped there) —
 * the suffix rule needs to see a *recent* non-confident reading in order to be invalidated by it.
 *
 * ## Concurrency (§9, startup-hardening pass)
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
     * One live interpretation, when it happened, and which aim epoch it belongs to.
     *
     * [reading] may now be [LabelReading.NotFound] or [LabelReading.Ambiguous] — both are stored
     * (previously `NotFound` was dropped at [record]) because the suffix-consensus rule in
     * [stableConsensus] needs to see a *recent* non-confident reading to invalidate an older
     * agreeing run, which it cannot do if that reading was never recorded at all.
     *
     * [aimEpoch] defaults to 0 so every pre-existing caller and test keeps behaving exactly as
     * before when it does not care about epoch scoping.
     */
    data class Observation(
        val reading: LabelReading,
        val timestampMs: Long,
        val aimEpoch: Long = 0L,
    )

    // Guarded by `lock` — see the class KDoc's "Concurrency" section. A plain ArrayDeque has no
    // thread-safety of its own, and this buffer is genuinely written from the main thread
    // (record/clear) and read from a background dispatcher (stableConsensus/asEvidence, called from
    // inside `withContext(Dispatchers.IO)` in LabelScannerScreen) — not a hypothetical.
    private val lock = Any()
    private val observations = ArrayDeque<Observation>()

    /**
     * Records a live-frame interpretation, including NotFound and Ambiguous. Cheap; called on every
     * analysed frame.
     *
     * [timestampMs] MUST be monotonic elapsed time (`SystemClock.elapsedRealtime()` on Android),
     * never wall-clock time — a wall-clock adjustment (NTP sync, timezone/DST change) must not be
     * able to make an old observation appear fresh or a fresh one appear stale.
     * [CaptureEvidenceCoordinator.freezeAtShutter] and every production caller must use the same
     * clock source.
     *
     * [aimEpoch] is the caller's own [CaptureEvidenceCoordinator.aimEpoch] for the current live
     * stream (see the class KDoc) — stamped on the observation so a later read for a *different*
     * aim epoch can never count this frame toward its consensus, however recent it is.
     */
    fun record(reading: LabelReading, timestampMs: Long, aimEpoch: Long = 0L) {
        synchronized(lock) {
            observations.addLast(Observation(reading, timestampMs, aimEpoch))
            while (observations.size > capacity) observations.removeFirst()
        }
    }

    /** Forgets everything. Called on retake and on leaving the screen, so state cannot cross aims. */
    fun clear() = synchronized(lock) { observations.clear() }

    /** Snapshot for the evidence bundle; ordering is oldest-first. */
    fun snapshot(): List<Observation> = synchronized(lock) { observations.toList() }

    /**
     * The value the most recent contiguous run of observations from [aimEpoch] agreed on, or null.
     *
     * [nowMs] MUST use the same monotonic clock source as [record]'s `timestampMs` — see that
     * method's KDoc.
     *
     * Examines at most the final [MAX_SUFFIX_LENGTH] observations recorded under [aimEpoch] (older
     * ones, however they read, cannot suppress or supply consensus — a disagreement that fell off
     * the back of that window cannot invalidate a run that has since re-stabilised). Within that
     * suffix, walking backward from the newest observation:
     *
     * - the newest observation must be inside [windowMs] of [nowMs];
     * - **the newest observation in the suffix must itself be a [LabelReading.Confident]** — a
     *   `NotFound` or an [LabelReading.Ambiguous] as the very latest thing the camera saw means the
     *   most recent view is not confident, and that must not be papered over by an older agreeing
     *   run, however long;
     * - walking further backward, a [LabelReading.Confident] observation joins the run if it
     *   agrees on value and basis with the newest one (numeric `compareTo`, since
     *   `BigDecimal.equals` is scale-sensitive); a disagreeing one ends the run;
     * - at most one older non-confident observation (`NotFound`, or an `Ambiguous` naming only the
     *   agreed value) is tolerated in between agreeing confident observations, and the walk
     *   continues past it looking for more agreement further back; a *second* such observation, or
     *   an `Ambiguous` naming a value other than the agreed one anywhere in the suffix, ends the run
     *   (and, for a competing `Ambiguous`, refuses consensus outright rather than merely stopping
     *   the walk — an ambiguity naming the agreed value's competitor is evidence the camera saw
     *   something else, wherever in the suffix it falls).
     *
     * The run is accepted only if it contains at least [MIN_AGREEING_FRAMES] confident, agreeing
     * observations.
     */
    fun stableConsensus(nowMs: Long, aimEpoch: Long = 0L): CarbCandidate? {
        val suffix = synchronized(lock) { observations.toList() }
            .filter { it.aimEpoch == aimEpoch }
            .takeLast(MAX_SUFFIX_LENGTH)
        if (suffix.isEmpty()) return null
        if (nowMs - suffix.last().timestampMs > windowMs) return null

        // The most recent observation must itself be a confident reading — a trailing NotFound or
        // Ambiguous means the camera's latest view is not confident, however good an older run was.
        val agreedValue = (suffix.last().reading as? LabelReading.Confident)?.candidate ?: return null

        var agreeingCount = 1
        var toleratedNonConfident = 0

        for (obs in suffix.dropLast(1).asReversed()) {
            val confidentCandidate = (obs.reading as? LabelReading.Confident)?.candidate
            if (confidentCandidate != null) {
                val agrees = confidentCandidate.basis != null &&
                    confidentCandidate.basis == agreedValue.basis &&
                    confidentCandidate.value.compareTo(agreedValue.value) == 0
                if (!agrees) break
                agreeingCount++
                continue
            }

            // Non-confident (Ambiguous or NotFound), older than the newest confident observation.
            val ambiguous = obs.reading as? LabelReading.Ambiguous
            if (ambiguous != null) {
                val competes = ambiguous.candidates.any { it.value.compareTo(agreedValue.value) != 0 }
                if (competes) return null
            }
            if (toleratedNonConfident >= 1) break
            toleratedNonConfident++
        }

        if (agreeingCount < MIN_AGREEING_FRAMES) return null
        return agreedValue
    }

    /**
     * Consensus wrapped as resolver evidence, or null.
     *
     * [nowMs] MUST use the same monotonic clock source as [record]'s `timestampMs` — see that
     * method's KDoc.
     *
     * [documentFor] supplies the document the winning candidate came from when one is available; it
     * is optional because live frames are transient and the buffer deliberately does not retain
     * whole documents (that would pin several megabytes of geometry per second of aiming).
     */
    fun asEvidence(
        nowMs: Long,
        aimEpoch: Long = 0L,
        documentFor: (CarbCandidate) -> OcrDocument? = { null },
    ): RecognitionEvidence? {
        val candidate = stableConsensus(nowMs, aimEpoch) ?: return null
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

        /** At most the final 5 observations from one aim epoch are examined for consensus. */
        const val MAX_SUFFIX_LENGTH = 5
    }
}

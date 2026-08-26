package app.justthecarbs.ocr

import android.os.SystemClock

/**
 * A monotonic stage-by-stage timing trace for one still scan, logged as a single compact line.
 *
 * ### Why this exists
 *
 * Device evidence (2026-08-25, Samsung SM-S928B, 11 scans) put the median total scan at **3.55 s**
 * while ML Kit itself took **0.29 s** — so roughly **3.27 s of every scan was spent outside the
 * recognizer**, and the existing instrumentation measured only the two ends. `meta.txt` could say a
 * scan took 8.1 s with 1.1 s of recognition and offer nothing about the other 7. Optimising ML Kit
 * would have been optimising 8% of the problem.
 *
 * Each stage below is a place the pipeline can plausibly spend hundreds of milliseconds on an 8 MP
 * capture — decoding ~3.5 MB of JPEG, allocating and rotating a ~24 MB bitmap, re-encoding it — and
 * the point of the trace is that nobody has to guess which.
 *
 * ### Monotonic
 *
 * [SystemClock.elapsedRealtimeNanos] rather than wall-clock time: it cannot jump backwards when the
 * clock is corrected or the device changes timezone mid-scan, and it keeps counting in deep sleep,
 * so a stage that genuinely blocked is reported as having blocked.
 *
 * ### On-path versus off-path stages
 *
 * The only build that can record evidence is a **debug** build, so the only build anyone can take a
 * device measurement from is the one carrying work that will not exist for a user. Reporting a single
 * total therefore overstates the shipped experience with no way to subtract the difference, which is
 * how "3.55 s" gets quoted about a release build that never wrote a PNG.
 *
 * [markOffPath] flags a stage as *not part of what the user waited for* — either debug-only work, or
 * anything that runs after the result has already been handed to the UI. [userVisibleMs] is the total
 * with those stages removed, and it is the number the ≤2 s acceptance target is about. Off-path stages
 * are still reported, marked with a trailing `*`, because "the debug build spent 2.1 s writing a PNG"
 * is worth knowing — it just is not latency.
 *
 * ### Cost
 *
 * A `nanoTime` read and a list append per stage — nanoseconds against the milliseconds being
 * measured, so the trace cannot meaningfully distort what it reports. It is always built (the
 * timings are useful in any build) but only *logged* through [OcrDiagnosticsLogger], which is already
 * debug-gated.
 */
internal class ScanTrace {

    private val startedNanos = SystemClock.elapsedRealtimeNanos()
    private var lastNanos = startedNanos
    private val stages = mutableListOf<Stage>()

    private data class Stage(val name: String, val ms: Long, val offPath: Boolean)

    /** Records the time since the previous mark (or since construction) against [stage]. */
    fun mark(stage: String) = record(stage, offPath = false)

    /**
     * Like [mark], but excludes the stage from [userVisibleMs].
     *
     * For work the user is provably not waiting on: debug-only evidence writes, and anything after
     * the result has been handed over. Never use it merely because a stage is fast or uninteresting —
     * the whole value of the split is that it can be trusted as an answer to "what did the user wait
     * for", and a stage wrongly excluded makes the reported latency a fiction.
     */
    fun markOffPath(stage: String) = record(stage, offPath = true)

    private fun record(stage: String, offPath: Boolean) {
        val now = SystemClock.elapsedRealtimeNanos()
        stages += Stage(stage, (now - lastNanos) / 1_000_000, offPath)
        lastNanos = now
    }

    /** Runs [block], recording how long it took as [stage]. */
    inline fun <T> time(stage: String, block: () -> T): T {
        val result = block()
        mark(stage)
        return result
    }

    /** Runs [block], recording it as an off-path stage. See [markOffPath]. */
    inline fun <T> timeOffPath(stage: String, block: () -> T): T {
        val result = block()
        markOffPath(stage)
        return result
    }

    /** Total elapsed milliseconds since the trace began, off-path work included. */
    fun totalMs(): Long = (SystemClock.elapsedRealtimeNanos() - startedNanos) / 1_000_000

    /**
     * What the user actually waited for: [totalMs] less every stage flagged by [markOffPath].
     *
     * Read this after the `handoff` mark, not before — stages recorded later are simply not in it yet.
     */
    fun userVisibleMs(): Long = totalMs() - stages.filter { it.offPath }.sumOf { it.ms }

    /**
     * One line, longest stage first, e.g.
     * `scan 3551ms (user-visible 1401ms) | evidence-png 2104* · decode 780 · mlkit 280 · parse 24 · …`
     *
     * Ordered by cost rather than by pipeline position on purpose: the question this log exists to
     * answer is "where did the time go", and that answer should be the first thing on the line rather
     * than something to be found by scanning ten numbers. A trailing `*` marks an off-path stage, so
     * the two totals can be reconciled against the breakdown by hand.
     */
    fun summary(): String {
        val breakdown = stages
            .filter { it.ms > 0 }
            .sortedByDescending { it.ms }
            .joinToString(" · ") { "${it.name} ${it.ms}${if (it.offPath) "*" else ""}" }
        return "scan ${totalMs()}ms (user-visible ${userVisibleMs()}ms) | $breakdown"
    }
}

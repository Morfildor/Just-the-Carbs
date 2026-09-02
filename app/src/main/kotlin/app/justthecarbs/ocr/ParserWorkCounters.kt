package app.justthecarbs.ocr

/**
 * Deterministic counters for the work the parser does, used to pin performance as *work done* rather
 * than as wall-clock time.
 *
 * ## Why counters rather than timings
 *
 * A parser regression measured on a phone is a duration, but a duration is the wrong thing to assert
 * in the standard suite: it varies with the machine, the JIT and whatever else is running, so a
 * timing assertion is either so loose it catches nothing or so tight it flakes. The 2026-09-01
 * regression was not the same work running slowly — it was a quadratic blow-up in how many times the
 * vocabulary was normalized and how many times each row was classified. That is exactly what a
 * counter measures, and it measures it identically on every machine.
 *
 * ## Cost when disabled
 *
 * [enabled] is false in every configuration except a test that turns it on. Each increment site is a
 * single boolean read on a static field, which the JIT hoists out of the enclosing loop; the counters
 * are plain `Long`s and no object is allocated. R8 cannot strip the class (the increment calls are
 * real code on the parse path) and it does not need to — the whole mechanism is one predictable
 * branch per call site.
 *
 * ## Not thread-safe, deliberately
 *
 * One interpretation runs on one thread and the counters are read after it returns. Making them
 * atomic would add a memory barrier to the hottest loop in the parser to protect a diagnostic that
 * has no concurrent reader.
 */
internal object ParserWorkCounters {

    /** Off unless a test switches it on. See the class comment for why this is cheap. */
    @JvmField
    var enabled: Boolean = false

    /** Calls to [NutritionTerminology.normalize], the parser's hottest function. */
    @JvmField
    var normalizeCalls: Long = 0

    /**
     * Vocabulary terms normalized during a parse.
     *
     * Should be zero once the term cache is warm: the vocabulary is a compile-time constant, so
     * normalizing one of its entries during a parse means the cache was missed.
     */
    @JvmField
    var termNormalizeMisses: Long = 0

    /** Calls to [RowClassifier.classify]. */
    @JvmField
    var rowClassifyCalls: Long = 0

    /** Calls to [NutrientRowSegments.segmentsOf]. */
    @JvmField
    var segmentationCalls: Long = 0

    /** Calls to [InlineBasisSpans.find]. */
    @JvmField
    var inlineBasisCalls: Long = 0

    fun reset() {
        normalizeCalls = 0
        termNormalizeMisses = 0
        rowClassifyCalls = 0
        segmentationCalls = 0
        inlineBasisCalls = 0
    }

    fun snapshot(): List<Pair<String, Long>> = listOf(
        "normalize" to normalizeCalls,
        "term-cache miss" to termNormalizeMisses,
        "row classify" to rowClassifyCalls,
        "segmentation" to segmentationCalls,
        "inline basis" to inlineBasisCalls,
    )
}

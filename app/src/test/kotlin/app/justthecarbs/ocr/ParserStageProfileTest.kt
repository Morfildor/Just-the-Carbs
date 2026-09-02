package app.justthecarbs.ocr

import org.junit.Test

/**
 * Measures where the interpreter spends its time, stage by stage, on the two captures that took
 * 20195 ms and 11189 ms on a Samsung SM-S928B.
 *
 * ## Why this exists
 *
 * The device evidence recorded `parse 9278ms` and `parse 5331ms` against a previous measurement of
 * 341–505 ms for the same drink. That is a 10–27x regression, and the brief's instruction was
 * explicit: do not guess at the cause, identify exactly where the additional seconds are spent.
 *
 * ## What it asserts, and what it deliberately does not
 *
 * It prints wall-clock timings for information and asserts only on **invocation counts**, which are
 * deterministic. A wall-clock assertion in the standard suite is the flaky test people learn to
 * ignore; a call-count assertion measures the same regression — the parser was doing quadratically
 * more work, not the same work more slowly — and cannot flake.
 *
 * @see ParserWorkCounters for what is counted and why those counters are the right proxy.
 */
class ParserStageProfileTest {

    private fun profile(name: String, document: OcrDocument) {
        ParserWorkCounters.reset()
        ParserWorkCounters.enabled = true

        // One warm-up so JIT and the terminology object's lazy initialisation are not attributed to
        // the measured run. Counters are reset afterwards.
        NutritionTableInterpreter.interpret(document)
        ParserWorkCounters.reset()

        val started = System.nanoTime()
        val report = NutritionTableInterpreter.interpret(document)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000.0

        val counts = ParserWorkCounters.snapshot()
        ParserWorkCounters.enabled = false

        println("=== $name ===")
        println("  elements        : ${document.elements.size}")
        println("  parse wall clock: %.1f ms".format(elapsedMs))
        println("  reading         : ${report.reading}")
        counts.forEach { (label, value) -> println("  %-16s: %d".format(label, value)) }
        println()
    }

    @Test
    fun `stage profile for both new hardware captures`() {
        profile("222212-563 green drink, damaged label", HardwareLabelFixtures.greenDrinkSecondCapture())
        profile("222300-297 green drink, clipped label", HardwareLabelFixtures.greenDrinkClippedLabel())
        profile("211417-935 green drink, first session", HardwareLabelFixtures.greenDrink())
        profile("211518-862 cracker bag", HardwareLabelFixtures.crackerBag())
        profile("211550-678 korean sauce", HardwareLabelFixtures.koreanSauce())
        profile("211619-534 multilingual table", HardwareLabelFixtures.multilingualTable())
    }

    /**
     * The regression guard, expressed as work done rather than time taken.
     *
     * `normalize` is the single hottest function in the parser: it runs a Unicode NFD decomposition
     * plus five regex replacements, and every vocabulary comparison used to call it on **both** sides.
     * With ~230 vocabulary terms consulted per span, per span length, per element position, per row,
     * the count ran to millions on a document this size — which is the whole of the 5–9 second
     * regression.
     *
     * The bound below is generous against the fixed post-fix cost and still an order of magnitude
     * under the pre-fix figure, so it catches a reintroduction without becoming a maintenance burden
     * for an ordinary vocabulary addition.
     */
    @Test
    fun `interpreting a full page does not normalize text a pathological number of times`() {
        val document = HardwareLabelFixtures.greenDrinkSecondCapture()

        ParserWorkCounters.reset()
        ParserWorkCounters.enabled = true
        NutritionTableInterpreter.interpret(document)
        val normalizations = ParserWorkCounters.normalizeCalls
        ParserWorkCounters.enabled = false

        println("normalize() calls for an ${document.elements.size}-element page: $normalizations")

        // Measured at 1,190,000+ before the fix on this exact document.
        check(normalizations < 50_000) {
            "normalize() ran $normalizations times for ${document.elements.size} elements — " +
                "the per-call vocabulary normalization has been reintroduced"
        }
    }

    /**
     * Term normalization must be memoized, not recomputed.
     *
     * `containsTerm(text, term)` normalized `term` on every call even though the vocabulary is a
     * compile-time constant. This asserts the constant work stays constant: after the vocabulary has
     * been consulted once, consulting it again costs no further term normalizations.
     */
    @Test
    fun `vocabulary terms are normalized once, not per comparison`() {
        val document = HardwareLabelFixtures.greenDrinkSecondCapture()

        // Warm on the same document. The cache fills on first sight of each term, so warming on a
        // *different* label would leave this one's first-seen terms to be normalized here and the
        // assertion would measure fixture overlap rather than caching. Parsing the same document
        // twice is the exact question being asked: does repeating identical work cost anything?
        NutritionTableInterpreter.interpret(document)

        ParserWorkCounters.reset()
        ParserWorkCounters.enabled = true
        NutritionTableInterpreter.interpret(document)
        val termNormalizations = ParserWorkCounters.termNormalizeMisses
        ParserWorkCounters.enabled = false

        check(termNormalizations == 0L) {
            "$termNormalizations vocabulary terms were normalized during a repeat parse — the term " +
                "cache is not being used, so every comparison pays for a full NFD decomposition"
        }
    }

    /**
     * Each row is classified once per interpretation, not repeatedly.
     *
     * [UnitAccompanimentPolicy] called `RowClassifier.classify` per row, and the interpreter called
     * it again for the same rows, and `RowClassifier` in turn ran the whole segmentation pass. The
     * bound here is "a small multiple of the row count", which permits the legitimate two or three
     * distinct call sites while refusing a nested loop.
     */
    @Test
    fun `rows are classified a bounded number of times`() {
        val document = HardwareLabelFixtures.greenDrinkSecondCapture()

        ParserWorkCounters.reset()
        ParserWorkCounters.enabled = true
        NutritionTableInterpreter.interpret(document)
        val classifications = ParserWorkCounters.rowClassifyCalls
        val segmentations = ParserWorkCounters.segmentationCalls
        ParserWorkCounters.enabled = false

        val rows = LogicalRowBuilder.build(document).size
        println("rows=$rows classify=$classifications segment=$segmentations")

        check(classifications <= rows * 4) {
            "RowClassifier.classify ran $classifications times for $rows rows"
        }
        check(segmentations <= rows * 4) {
            "NutrientRowSegments ran $segmentations times for $rows rows"
        }
    }
}

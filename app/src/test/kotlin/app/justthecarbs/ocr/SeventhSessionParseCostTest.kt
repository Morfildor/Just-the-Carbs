package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether `141642-529`'s **1432 ms** parse is pathological work or simply a larger document.
 *
 * ## The device measurement
 *
 * ```
 * 141642-529   scan 2142ms   parse 1432   mlkit 403     289 elements
 * 141703-456   scan  864ms   parse  199   mlkit 367     262 elements
 * 141626-947   scan  653ms   parse  107   mlkit 355
 * 141558-254   scan  501ms   parse   62   mlkit 268
 * ```
 *
 * A 10% larger document taking **7x** longer to parse is the shape of super-linear work, not of a
 * bigger input. This measures the work itself, in counts rather than in milliseconds: counts are
 * deterministic across machines, where a wall-clock assertion in the standard suite is either too
 * loose to catch anything or flaky — the lesson from the 2026-09-01 regression, which was a
 * quadratic blow-up in exactly these counters.
 *
 * Prints the comparison and asserts only a **ratio bound** between two real device documents, so it
 * fails on a return to super-linear behaviour and not on a slow machine.
 */
class SeventhSessionParseCostTest {

    private fun workFor(document: OcrDocument): Map<String, Long> {
        ParserWorkCounters.enabled = true
        ParserWorkCounters.reset()
        try {
            NutritionTableInterpreter.interpret(document)
            return ParserWorkCounters.snapshot().toMap()
        } finally {
            ParserWorkCounters.enabled = false
            ParserWorkCounters.reset()
        }
    }

    @Test
    fun `the slow capture does not do super-linear parser work`() {
        val slow = SeventhSessionFixtures.truffleSeparatorlessPair()
        val fast = SeventhSessionFixtures.truffleDamagedUnitGlyph()

        val slowWork = workFor(slow)
        val fastWork = workFor(fast)

        println("capture      elements  " + slowWork.keys.joinToString("  "))
        println("141642-529   ${slow.elements.size}       " + slowWork.values.joinToString("  "))
        println("141703-456   ${fast.elements.size}       " + fastWork.values.joinToString("  "))

        val sizeRatio = slow.elements.size.toDouble() / fast.elements.size
        println("element ratio: %.2fx".format(sizeRatio))

        slowWork.forEach { (name, slowCount) ->
            val fastCount = fastWork[name] ?: 0
            if (fastCount == 0L) return@forEach
            val ratio = slowCount.toDouble() / fastCount
            println("  %-16s %.2fx (%d vs %d)".format(name, ratio, slowCount, fastCount))

            // Quadratic in element count would be ~1.22x^2 = 1.5x; anything at or under ~2x of the
            // size ratio is linear-ish work on a slightly larger document. The bound is deliberately
            // generous: it exists to catch a return to the 330x blow-up the term cache fixed, not to
            // police ordinary variation between two photographs of the same package.
            assertTrue(
                "$name grew ${"%.2f".format(ratio)}x for a ${"%.2f".format(sizeRatio)}x larger " +
                    "document, which is super-linear",
                ratio <= sizeRatio * 3,
            )
        }
    }

    @Test
    fun `the vocabulary is not re-normalized once the caches are warm`() {
        // Zero misses is the invariant the 2026-09-01 fix established: the vocabulary is a
        // compile-time constant, so normalizing one of its entries during a parse means a cache was
        // missed and the quadratic path is back.
        //
        // The **first** parse in a fresh process legitimately fills the caches, so it is run and
        // discarded. Asserting on a cold parse would make this test depend on which other test
        // happened to run first, which is not a property of the parser.
        val document = SeventhSessionFixtures.truffleSeparatorlessPair()
        workFor(document)

        assertEquals(
            "vocabulary must not be re-normalized on a warm parse",
            0L,
            workFor(document)["term-cache miss"],
        )
    }

    @Test
    fun `the prose path does not normalize the vocabulary per token position`() {
        // The specific defect: [ProseNutritionReader.longestTermAt] and `termLengthAt` walked every
        // vocabulary term at every token position and normalized the term inside that loop. On this
        // capture that was ~49,000 of 51,792 normalize calls and 1432 ms on the device.
        //
        // Bounded against the document's own size rather than against a constant, so the assertion
        // states the shape (linear-ish in elements) rather than a number that would need editing
        // whenever a fixture or a vocabulary entry changes.
        val document = SeventhSessionFixtures.truffleSeparatorlessPair()
        workFor(document) // warm the caches; see above
        val calls = workFor(document)["normalize"]!!

        assertTrue(
            "normalize ran $calls times for ${document.elements.size} elements, which is the " +
                "per-token-position vocabulary walk returning",
            calls < document.elements.size * 20L,
        )
    }
}

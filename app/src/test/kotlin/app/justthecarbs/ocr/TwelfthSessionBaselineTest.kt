package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The measured JVM baseline for the twelfth phone session, and the non-vacuity guard for
 * [TwelfthSessionFixtures].
 *
 * Every case asserts the **element count** the bundle recorded and the **reading** its `meta.txt`
 * recorded for pass A. Both matter for different reasons: the count says the fixture is the whole
 * document the device saw, and the reading says the JVM pipeline reaches the same state the phone
 * did, so a change measured here is a change the phone would show.
 *
 * The readings below are what the *device* produced. Where this pass changes one, the change is
 * recorded in [TwelfthSessionRegressionTest] with the reason; this file keeps the counts and the
 * cases the pass does not move.
 */
class TwelfthSessionBaselineTest {

    private fun readingName(reading: LabelReading): String = when (reading) {
        is LabelReading.Confident ->
            "Confident ${reading.candidate.value.toPlainString()}/${reading.candidate.basis}"
        is LabelReading.Ambiguous -> "Ambiguous(${reading.candidates.size})"
        LabelReading.NotFound -> "NotFound"
    }

    @Test
    fun `every fixture carries its bundle's full element count`() {
        assertEquals(77, TwelfthSessionFixtures.crackersServingColumnOnly().elements.size)
        assertEquals(58, TwelfthSessionFixtures.crackersPerHundredOnly().elements.size)
        assertEquals(93, TwelfthSessionFixtures.redLabelDegradedTwo().elements.size)
        assertEquals(100, TwelfthSessionFixtures.multilingualDamagedCarbTerms().elements.size)
        assertEquals(160, TwelfthSessionFixtures.gelSixtySeven().elements.size)
        assertEquals(79, TwelfthSessionFixtures.proseThreePointThree().elements.size)
        assertEquals(116, TwelfthSessionFixtures.unresolvedAmbiguousPair().elements.size)
        assertEquals(134, TwelfthSessionFixtures.pickle().elements.size)
        assertEquals(98, TwelfthSessionFixtures.bilingualThreePointTwo().elements.size)
        assertEquals(145, TwelfthSessionFixtures.drinkFourPointSix().elements.size)
        assertEquals(290, TwelfthSessionFixtures.rotatedNinety().elements.size)
        assertEquals(546, TwelfthSessionFixtures.balticTortilla().elements.size)
    }

    /**
     * The four captures the device read correctly and acted on. These are the positive-preservation
     * set: nothing in this pass may move any of them.
     */
    @Test
    fun `the four correct device readings are reproduced`() {
        assertEquals(
            "Confident 67.0/PER_100_G",
            readingName(NutritionTableParser.parse(TwelfthSessionFixtures.gelSixtySeven())),
        )
        assertEquals(
            "Confident 3.3/PER_100_G",
            readingName(NutritionTableParser.parse(TwelfthSessionFixtures.proseThreePointThree())),
        )
        assertEquals(
            "Confident 3.2/PER_100_G",
            readingName(NutritionTableParser.parse(TwelfthSessionFixtures.bilingualThreePointTwo())),
        )
        assertEquals(
            "Confident 4.6/PER_100_ML",
            readingName(NutritionTableParser.parse(TwelfthSessionFixtures.drinkFourPointSix())),
        )
    }

    /**
     * Prints the whole session through the real interpreter. Asserts almost nothing on purpose —
     * this is the measuring instrument, and what it measures is written into the pass report.
     */
    @Test
    fun `session outcome table`() {
        val fixtures = listOf(
            "212442 crackers serving-col" to TwelfthSessionFixtures.crackersServingColumnOnly(),
            "212501 crackers per-100" to TwelfthSessionFixtures.crackersPerHundredOnly(),
            "212524 red label 7,2->2" to TwelfthSessionFixtures.redLabelDegradedTwo(),
            "212540 damaged carb terms" to TwelfthSessionFixtures.multilingualDamagedCarbTerms(),
            "212618 gel 67" to TwelfthSessionFixtures.gelSixtySeven(),
            "212637 prose 3,3" to TwelfthSessionFixtures.proseThreePointThree(),
            "212649 unresolved" to TwelfthSessionFixtures.unresolvedAmbiguousPair(),
            "212700 PICKLE" to TwelfthSessionFixtures.pickle(),
            "212711 bilingual 3,2" to TwelfthSessionFixtures.bilingualThreePointTwo(),
            "212727 drink 4,6" to TwelfthSessionFixtures.drinkFourPointSix(),
            "212804 rotated 90" to TwelfthSessionFixtures.rotatedNinety(),
            "212828 baltic tortilla" to TwelfthSessionFixtures.balticTortilla(),
        )
        println("=== twelfth session, full-frame pass A through the real interpreter ===")
        fixtures.forEach { (name, document) ->
            val report = NutritionTableParser.parseWithDiagnostics(document)
            val rows = LogicalRowBuilder.build(document)
            val columns = ColumnClassifier.classify(rows, document.width)
            println("%-28s %-26s rows=%d columns=%s".format(
                name,
                readingName(report.reading),
                rows.size,
                columns.joinToString(", ") { "${it.kind}@${it.centerX.toInt()}" }.ifEmpty { "none" },
            ))
        }

        // What the user can reach when the automatic path does not answer. These two lines are the
        // difference between a refusal and a dead end, and the twelfth session's complaint was about
        // the dead ends rather than about the refusals.
        println("=== what recovery offers, and whether focused entry is reachable ===")
        fixtures.forEach { (name, document) ->
            val offers = RecoveryCandidates.of(document).map { it.label }
            val focused = FocusedAmountEntry.of(document)?.basis?.name ?: "-"
            val sideways = TextResolutionGuidance.estimate(document).readiness
            println("%-28s offers=%-18s focusedEntry=%-12s framing=%s".format(
                name, offers.ifEmpty { "none" }, focused, sideways,
            ))
        }
    }
}

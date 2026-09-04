package app.justthecarbs.ocr

import org.junit.Test

/**
 * The whole fifth session in one table: reading, verification route, and what recovery offers.
 *
 * Prints rather than asserts — [FifthSessionRegressionTest] carries the assertions. This exists so
 * the before/after recorded in `CLAUDE.md` is a measurement anyone can reproduce in one command,
 * and so a future change that shifts a capture between automatic, confirmation and recovery is
 * visible rather than merely still-green.
 */
class FifthSessionOutcomeTest {

    private fun row(name: String, document: OcrDocument) {
        val report = NutritionTableInterpreter.interpret(document)
        val verdict = AutomaticVerification.verify(document, report)
        val value = (report.reading as? LabelReading.Confident)?.candidate
        val reading = value?.let { "${it.value.toPlainString()}/${it.basis?.name}" }
            ?: report.reading::class.simpleName!!

        val action = when {
            value != null && verdict.mayAdvanceAutomatically -> "AUTO_ADVANCE"
            value != null -> "CONFIRM"
            else -> "RECOVERY"
        }

        val offers = RecoveryCandidates.of(document).joinToString(" | ") { it.label }

        println(
            "%-14s %-22s %-24s %-13s %s".format(
                name,
                reading,
                verdict.route.name +
                    if (verdict.supportingRows > 0) "(${verdict.supportingRows})" else "",
                action,
                offers.ifEmpty { "—" },
            ),
        )
    }

    @Test
    fun `the fifth session's six captures, end to end`() {
        println(
            "%-14s %-22s %-24s %-13s %s".format(
                "capture", "reading", "verification", "action", "recovery offers",
            ),
        )
        row("103854-549", FifthSessionFixtures.drinkConfirmFirst())
        row("103906-452", FifthSessionFixtures.drinkConfirmSecond())
        row("103926-226", FifthSessionFixtures.crackerCrossColumnVerified())
        row("103936-423", FifthSessionFixtures.crackerDamagedPerHundredHeader())
        row("103949-880", FifthSessionFixtures.sauceLinearPanelFirst())
        row("104006-838", FifthSessionFixtures.sauceLinearPanelSecond())
    }

    /**
     * `103936` again, this time through the whole evidence set rather than Pass A alone.
     *
     * The row above shows what the *automatic* pass sees — NotFound, because the header is damaged.
     * This shows what the app now does once Strategy B's independent recognition is in hand, which
     * is the release-blocking difference.
     */
    @Test
    fun `the damaged capture, resolved across both recognitions`() {
        val passA = FifthSessionFixtures.crackerDamagedPerHundredHeader()
        val strategyBDocument = FifthSessionFixtures.crackerCrossColumnVerified()
        val evidence = listOf(
            RecognitionEvidence(
                EvidenceSource.FULL_FRAME_PASS_A,
                NutritionTableInterpreter.interpret(passA),
                passA,
            ),
            RecognitionEvidence(
                EvidenceSource.SELECTED_REGION_OCR,
                NutritionTableInterpreter.interpret(strategyBDocument),
                strategyBDocument,
            ),
        )

        val outcome = EvidenceResolver.resolve(evidence)
        val verdict = AutomaticVerification.verify(evidence)
        val reading = (outcome as? EvidenceResolver.Outcome.Resolved)?.reading as? LabelReading.Confident

        println("103936 across both runs:")
        println("  resolver.verdict      : ${outcome::class.simpleName}")
        println("  reading               : ${reading?.candidate?.value}/${reading?.candidate?.basis}")
        println("  automatic-verification: ${verdict.route} (support=${verdict.supportingRows})")
        println("  final UI action       : " + if (AutomaticScanAdvance.mayAdvanceVerified(outcome, verdict, strategyBDocument)) "AUTO_ADVANCE" else "CONFIRM/RECOVERY")
    }
}

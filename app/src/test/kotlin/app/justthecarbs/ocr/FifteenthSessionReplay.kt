package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal

/**
 * Replays the 21 physical captures of `docs/Scan Evidence new structure` through the production path.
 *
 * Deliberately mirrors [FourteenthSessionReplay] rather than inventing a second harness: the two
 * corpora must be scored by the same rules, or a change that improves one and damages the other would
 * be invisible.
 */
internal object FifteenthSessionReplay {
    /** The crop the device actually confirmed, from every bundle's `selection.txt`. */
    private val selectedRegion = NormalizedRegion(0.0, 0.1840, 1.0, 0.8160)
    private val selectedCrop = SelectedRegionCrop.PixelRect(0, 671, 1684, 2305)

    enum class Classification {
        CORRECT_AUTO,
        CORRECT_CONFIRM,
        CORRECT_FOCUSED_ENTRY,
        UNNECESSARY_RECOVERY,
        OCR_NO_CORRECT_VALUE,
        WRONG_AUTO,
        WRONG_PROPOSAL,
        GROUND_TRUTH_UNKNOWN,
    }

    data class Result(
        val capture: FifteenthSessionCorpus.Capture,
        val outcome: EvidenceResolver.Outcome,
        val verification: AutomaticVerification.Verdict,
        val action: ScanPresentationDecision.Action,
        val failureReason: CarbFailureReason?,
        val offeredValue: BigDecimal?,
        val offeredBasis: NutritionBasis?,
        val focusedTarget: FocusedAmountEntry.Target?,
    ) {
        /** True when the user is shown a carbohydrate figure, whether or not they must confirm it. */
        val presentsValue: Boolean
            get() = action == ScanPresentationDecision.Action.AUTO_ADVANCE ||
                action == ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE ||
                action == ScanPresentationDecision.Action.CONFIRM

        val classification: Classification
            get() {
                val truth = capture.printedCarbs ?: return Classification.GROUND_TRUTH_UNKNOWN
                val offered = offeredValue
                if (offered != null) {
                    val correct = offered.compareTo(truth) == 0 && offeredBasis == capture.printedBasis
                    return when {
                        correct && action == ScanPresentationDecision.Action.AUTO_ADVANCE ->
                            Classification.CORRECT_AUTO
                        correct -> Classification.CORRECT_CONFIRM
                        action == ScanPresentationDecision.Action.AUTO_ADVANCE ->
                            Classification.WRONG_AUTO
                        else -> Classification.WRONG_PROPOSAL
                    }
                }
                if (action == ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY &&
                    focusedTarget?.basis == capture.printedBasis
                ) {
                    return Classification.CORRECT_FOCUSED_ENTRY
                }
                return if (capture.correctValueInEvidence) {
                    Classification.UNNECESSARY_RECOVERY
                } else {
                    Classification.OCR_NO_CORRECT_VALUE
                }
            }
    }

    fun replay(capture: FifteenthSessionCorpus.Capture): Result {
        val passA = capture.passA()
        val passAReport = NutritionTableParser.parseWithDiagnostics(passA)
        val filtered = ElementRegionFilter.filter(passA, selectedRegion) ?: passA
        val filteredReport = NutritionTableParser.parseWithDiagnostics(filtered)
        val strategyB = capture.strategyB()
        val strategyBReport = NutritionTableParser.parseWithDiagnostics(strategyB)

        // Every one of these three views comes from the single JPEG this bundle recorded, so they
        // share one physical observation. That is the whole point of the corpus.
        val observation = PhysicalObservationId(capture.bundle)
        val evidence = listOf(
            RecognitionEvidence(
                EvidenceSource.FULL_FRAME_PASS_A, passAReport, passA,
                physicalObservation = observation,
            ),
            RecognitionEvidence(
                EvidenceSource.FILTERED_PASS_A, filteredReport, filtered,
                physicalObservation = observation,
            ),
            RecognitionEvidence(
                EvidenceSource.SELECTED_REGION_OCR, strategyBReport, strategyB,
                crop = selectedCrop,
                physicalObservation = observation,
            ),
        )

        val outcome = EvidenceResolver.resolve(evidence)
        val verification = AutomaticVerification.verify(evidence)
        val evaluationDocument = outcome.winningEvidence?.document ?: passA
        val action =
            ScanPresentationDecision.decide(outcome, verification, evaluationDocument, automatic = true)
        val confident = AutomaticScanAdvance.confidentReading(outcome)
        val scaleVerdict = confident?.let { ScaleAmbiguity.check(evaluationDocument, it.candidate) }
        val presents = action == ScanPresentationDecision.Action.AUTO_ADVANCE ||
            action == ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE ||
            action == ScanPresentationDecision.Action.CONFIRM
        return Result(
            capture = capture,
            outcome = outcome,
            verification = verification,
            action = action,
            failureReason = CarbFailureDiagnosis.classify(
                report = outcome.winningEvidence?.report ?: filteredReport,
                outcome = outcome,
                scaleVerdict = scaleVerdict,
                action = action,
            ),
            offeredValue = confident?.candidate?.value?.takeIf { presents },
            offeredBasis = confident?.candidate?.basis?.takeIf { presents },
            focusedTarget = FocusedAmountEntry.of(evaluationDocument),
        )
    }

    fun replayAll() = FifteenthSessionCorpus.captures.map(::replay)

    fun table(results: List<Result>) = buildString {
        appendLine("session | truth | offered | device action | now | classification | verification")
        results.forEach { result ->
            appendLine(
                listOf(
                    result.capture.bundle.removePrefix("20260904-"),
                    result.capture.printedCarbs?.toPlainString() ?: "?",
                    result.offeredValue?.toPlainString() ?: "-",
                    result.capture.deviceAction,
                    result.action.name,
                    result.classification.name,
                    result.verification.route.name,
                ).joinToString(" | "),
            )
        }
        appendLine()
        Classification.entries.forEach { classification ->
            val count = results.count { it.classification == classification }
            if (count > 0) appendLine("${classification.name}: $count")
        }
    }
}

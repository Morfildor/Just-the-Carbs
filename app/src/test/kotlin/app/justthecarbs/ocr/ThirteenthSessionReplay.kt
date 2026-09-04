package app.justthecarbs.ocr

import app.justthecarbs.ocr.ThirteenthSessionCorpus.Capture
import app.justthecarbs.ocr.ThirteenthSessionCorpus.Outcome

/**
 * Replays a [Capture] through the **real** production stages and classifies what the user gets.
 *
 * ## What this models, and what it deliberately does not
 *
 * It runs the parser, the resolver, verification and [ScanPresentationDecision] — the same objects
 * the scanner calls, in the same order — over Pass A's document. It does **not** simulate Strategy
 * B: a second ML Kit recognition cannot be reproduced in the JVM, and inventing one would make the
 * harness measure a fiction. So the replay answers *"what does the app do with the evidence Pass A
 * actually produced?"*, which is the question every recall finding in this pass turns on.
 *
 * Where a bundle's device outcome depended on Strategy B, [Capture.deviceAction] records what the
 * phone did and the replay's own verdict is reported beside it rather than instead of it.
 */
internal object ThirteenthSessionReplay {

    data class Result(
        val capture: Capture,
        val reading: LabelReading,
        val outcome: EvidenceResolver.Outcome,
        val verification: AutomaticVerification.Verdict,
        val action: ScanPresentationDecision.Action,
        /** The figure the app would put in front of the user, or null when it offers none. */
        val offeredValue: java.math.BigDecimal?,
        val offeredBasis: app.justthecarbs.domain.NutritionBasis?,
        /** Values a tap on the recovery screen could select. */
        val recoveryOffers: List<String>,
    ) {
        val classification: Outcome
            get() {
                val printed = capture.printedCarbs ?: return Outcome.GROUND_TRUTH_UNKNOWN
                val offered = offeredValue
                val advancing = action == ScanPresentationDecision.Action.AUTO_ADVANCE
                val confirming = action == ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE ||
                    action == ScanPresentationDecision.Action.CONFIRM
                if (offered != null && (advancing || confirming)) {
                    val correct = offered.compareTo(printed) == 0 &&
                        offeredBasis == capture.printedBasis
                    return when {
                        correct && advancing -> Outcome.CORRECT_AUTO
                        correct -> Outcome.CORRECT_CONFIRM
                        advancing -> Outcome.WRONG_AUTO
                        else -> Outcome.WRONG_CONFIRM
                    }
                }
                // Nothing was offered. Whether that is a defect depends on whether the evidence
                // held the answer — the distinction the brief insists on.
                return if (capture.correctValueInEvidence) {
                    Outcome.UNNECESSARY_RECOVERY
                } else {
                    Outcome.OCR_NO_EVIDENCE
                }
            }
    }

    fun replay(capture: Capture): Result {
        val document = capture.document()
        val report = NutritionTableParser.parseWithDiagnostics(document)
        // The device's evidence *set*, reproduced. A second ML Kit run cannot be executed in the
        // JVM, so where the bundle recorded Strategy B reading the same value as Pass A, the same
        // parse is added under that pass's identity — which is exactly the corroboration the
        // resolver and [AutomaticVerification] saw on the phone. Where Strategy B contributed
        // nothing (the majority), only Pass A is present, and the replay is Pass-A-only.
        //
        // Faithfulness matters here rather than being a nicety: `DISTINCT_OCR_AGREEMENT` is what
        // decides whether a reading may be shown at all, so a replay without it under-reports what
        // the user actually gets.
        val strategyBAgreed = capture.strategyBValue
            ?.let { java.math.BigDecimal(it) }
            ?.let { sb ->
                (report.reading as? LabelReading.Confident)?.candidate?.value?.compareTo(sb) == 0
            } == true
        val evidence = buildList {
            add(
                RecognitionEvidence(
                    source = EvidenceSource.FULL_FRAME_PASS_A,
                    report = report,
                    document = document,
                ),
            )
            if (strategyBAgreed) {
                add(
                    RecognitionEvidence(
                        source = EvidenceSource.SELECTED_REGION_OCR,
                        report = report,
                        document = document,
                    ),
                )
            }
        }
        val outcome = EvidenceResolver.resolve(evidence)
        val verification = AutomaticVerification.verify(evidence)
        val action = ScanPresentationDecision.decide(
            outcome = outcome,
            verification = verification,
            document = document,
            automatic = true,
        )
        val confident = AutomaticScanAdvance.confidentReading(outcome)
        val offers = action == ScanPresentationDecision.Action.AUTO_ADVANCE ||
            action == ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE ||
            action == ScanPresentationDecision.Action.CONFIRM
        val recovery = RecoveryCandidates.of(document).map {
            "${it.reading.amount} ${it.reading.basis}"
        }
        return Result(
            capture = capture,
            reading = report.reading,
            outcome = outcome,
            verification = verification,
            action = action,
            offeredValue = confident?.candidate?.value?.takeIf { offers },
            offeredBasis = confident?.candidate?.basis?.takeIf { offers },
            recoveryOffers = recovery,
        )
    }

    fun replayAll(): List<Result> = ThirteenthSessionCorpus.captures.map(::replay)

    /** A printable table, so a run says what changed rather than only that something did. */
    fun table(results: List<Result>): String = buildString {
        appendLine(
            "bundle              | product                | printed | offered | action              | class",
        )
        results.forEach { r ->
            val printed = r.capture.printedCarbs?.toPlainString() ?: "?"
            val offered = r.offeredValue?.toPlainString() ?: "-"
            appendLine(
                "%-19s | %-22s | %7s | %7s | %-19s | %s".format(
                    r.capture.bundle.removePrefix("20260904-"),
                    r.capture.product.take(22),
                    printed,
                    offered,
                    r.action,
                    r.classification,
                ),
            )
        }
        appendLine()
        Outcome.entries.forEach { o ->
            val n = results.count { it.classification == o }
            if (n > 0) appendLine("%-22s %d".format(o.name, n))
        }
    }
}

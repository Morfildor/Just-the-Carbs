package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Test
import java.math.BigDecimal

/**
 * The nineteenth session's full 23-capture corpus (`docs/Scan evidence 06-09/`), replayed through
 * the REAL production entry point — [SelectedTableResolution.resolve] and
 * [ScanPresentationDecision.decide] — with the fixes from this session applied.
 *
 * ## What this measures, and what it does not
 *
 * Every capture used the identical scan-guide region `[0.0000,0.1840,1.0000,0.8160]` (confirmed
 * against every bundle's own `selection.txt`), so one shared harness replays all 23. Live and
 * Strategy-B evidence are injected exactly as the bundles recorded them (their own confident
 * value/basis), because this JVM test cannot re-run a real ML Kit recognition — this is a
 * **text/geometry replay**, not a re-verification of optical recognition. [RealImageOcrTest] and its
 * siblings are what test actual ML Kit output against the real image bytes; this file tests the
 * *decision logic* against faithfully-recorded evidence.
 *
 * This is not a strict test with hard-coded expected actions for all 23 — it is a MEASUREMENT
 * harness that prints the resolved action for every capture, matching the format the task's
 * before/after table needs. The two captures with dedicated behaviour-changing fixes
 * ([NineteenthSessionBaselineTest], [NineteenthSessionServingBasisRoutingTest]) have their
 * hard assertions there; this file's job is comprehensive coverage and an honest printout.
 */
class NineteenthSessionFullCorpusReplayTest {

    private val guideRegion = NormalizedRegion(0.0, 0.1840, 1.0, 0.8160)

    private data class CaptureCase(
        val timestamp: String,
        val stillDocument: OcrDocument,
        val recordedAction: String,
        val liveValue: String? = null,
        val liveBasis: NutritionBasis? = null,
        val selectedRegionDocument: OcrDocument? = null,
    )

    private val cases = listOf(
        CaptureCase(
            "123154-852",
            NineteenthSessionFixtures.redLidl123154(),
            recordedAction = "RECOVERY",
        ),
        CaptureCase(
            "123207-354",
            NineteenthSessionFixtures.redLidl123207(),
            recordedAction = "FOCUSED_AMOUNT_ENTRY",
        ),
        CaptureCase(
            "123218-330",
            NineteenthSessionFixtures.lidlDrink123218(),
            recordedAction = "CONFIRM_ON_CAPTURE",
            selectedRegionDocument = NineteenthSessionStrategyBFixtures2.lidlDrink123218StrategyB(),
        ),
        CaptureCase(
            "123227-819",
            NineteenthSessionFixtures.lidlDrink123227(),
            recordedAction = "AUTO_ADVANCE",
            liveValue = "6.2",
            liveBasis = NutritionBasis.PER_100_ML,
        ),
        CaptureCase(
            "123238-798",
            NineteenthSessionFixtures.gherkins123238(),
            recordedAction = "FOCUSED_AMOUNT_ENTRY",
        ),
        CaptureCase(
            "123247-267",
            NineteenthSessionFixtures.gherkins123247(),
            recordedAction = "FOCUSED_AMOUNT_ENTRY",
        ),
        CaptureCase(
            "123258-822",
            NineteenthSessionFixtures.vegaMayo123258(),
            recordedAction = "CONFIRM_ON_CAPTURE",
            selectedRegionDocument = NineteenthSessionStrategyBFixtures2.vegaMayo123258StrategyB(),
        ),
        CaptureCase(
            "123311-639",
            NineteenthSessionFixtures.hellmanns123311(),
            recordedAction = "RECOVERY",
            selectedRegionDocument = NineteenthSessionStrategyBFixtures.hellmanns123311StrategyB(),
        ),
        CaptureCase(
            "123324-685",
            NineteenthSessionFixtures.crackers123324(),
            recordedAction = "RECOVERY",
        ),
        CaptureCase(
            "123332-788",
            NineteenthSessionFixtures.crackers123332(),
            recordedAction = "RECOVERY",
            selectedRegionDocument = NineteenthSessionStrategyBFixtures.crackers123332StrategyB(),
        ),
        CaptureCase(
            "123339-223",
            NineteenthSessionFixtures.crackers123339(),
            recordedAction = "RECOVERY",
        ),
        CaptureCase(
            "123352-975",
            NineteenthSessionFixtures.redLidl123352(),
            recordedAction = "CONFIRM_ON_CAPTURE",
            liveValue = "12.0",
            liveBasis = NutritionBasis.PER_100_G,
            selectedRegionDocument = NineteenthSessionStrategyBFixtures.redLidl123352StrategyB(),
        ),
        CaptureCase(
            "123415-612",
            NineteenthSessionFixtures.oliveOil123415(),
            recordedAction = "FOCUSED_AMOUNT_ENTRY",
        ),
        CaptureCase(
            "123449-570",
            NineteenthSessionFixtures.quark123449(),
            recordedAction = "CROP_FALLBACK",
        ),
        CaptureCase(
            "123457-722",
            NineteenthSessionFixtures.quark123457(),
            recordedAction = "AUTO_ADVANCE",
            liveValue = "2.0",
            liveBasis = NutritionBasis.PER_100_G,
        ),
        CaptureCase(
            "123509-821",
            NineteenthSessionFixtures.yogurt123509(),
            recordedAction = "CROP_FALLBACK",
        ),
        CaptureCase(
            "123519-116",
            NineteenthSessionFixtures.yogurt123519(),
            recordedAction = "FOCUSED_AMOUNT_ENTRY",
        ),
        CaptureCase(
            "123544-918",
            NineteenthSessionFixtures.koreanSauce123544(),
            recordedAction = "CROP_FALLBACK",
        ),
        CaptureCase(
            "123605-862",
            NineteenthSessionFixtures.peanutProduct123605(),
            recordedAction = "AUTO_ADVANCE",
            liveValue = "9.6",
            liveBasis = NutritionBasis.PER_100_G,
        ),
        CaptureCase(
            "123627-539",
            NineteenthSessionFixtures.chocolateBar123627(),
            recordedAction = "RECOVERY",
            selectedRegionDocument = NineteenthSessionStrategyBFixtures.chocolateBar123627StrategyB(),
        ),
        CaptureCase(
            "123636-366",
            NineteenthSessionFixtures.chocolateBar123636(),
            recordedAction = "FOCUSED_AMOUNT_ENTRY",
        ),
        CaptureCase(
            "123650-430",
            NineteenthSessionFixtures.proteinBar123650(),
            recordedAction = "RECOVERY",
            liveValue = "40.0",
            liveBasis = NutritionBasis.PER_100_G,
        ),
        CaptureCase(
            "123657-089",
            NineteenthSessionFixtures.proteinBar123657(),
            recordedAction = "RECOVERY",
            liveValue = "40.0",
            liveBasis = NutritionBasis.PER_100_G,
            selectedRegionDocument = NineteenthSessionStrategyBFixtures.proteinBar123657StrategyB(),
        ),
    )

    private fun passA(document: OcrDocument): PassAResult {
        val report = NutritionTableInterpreter.interpret(document)
        return PassAResult(
            sessionId = 1L,
            document = document,
            report = report,
            bitmap = null,
            evidence = null,
            recognitionMs = 0L,
        )
    }

    private fun liveEvidenceFor(case: CaptureCase, stillId: PhysicalObservationId): RecognitionEvidence? {
        val value = case.liveValue ?: return null
        val basis = case.liveBasis ?: return null
        return RecognitionEvidence(
            source = EvidenceSource.LIVE_STABLE_FRAME,
            report = NutritionParseReport(
                LabelReading.Confident(
                    CarbCandidate(
                        sourceLine = "live",
                        label = "live",
                        value = BigDecimal(value),
                        basis = basis,
                        score = 0,
                        geometry = OcrBox(0, 0, 1, 1),
                        evidence = emptyList(),
                        column = null,
                    ),
                ),
                emptyList(),
            ),
            document = null,
            physicalObservation = PhysicalObservationId.forLiveSnapshot(0L, stillId.value),
        )
    }

    /**
     * The full corpus, replayed and printed. This is a measurement, not a strict assertion suite —
     * see the class KDoc. It fails only if the replay throws, which would indicate a wiring defect
     * rather than a disagreement about which action is "right".
     */
    @Test
    fun `replay all 23 captures through the real production decision path and report`() {
        val report = StringBuilder()
        report.append("timestamp | recorded action | replayed action | value/basis\n")

        cases.forEach { case ->
            val stillId = PhysicalObservationId.forStill("${case.timestamp}.jpg")
            val crop = SelectedRegionCrop.PixelRect(left = 0, top = 671, width = 1684, height = 2305)

            val result = SelectedTableResolution.resolve(
                passA = passA(case.stillDocument),
                region = guideRegion,
                bitmap = null,
                stillObservationId = stillId,
                liveEvidence = liveEvidenceFor(case, stillId),
                recogniseRegion = { _, _ ->
                    case.selectedRegionDocument?.let { doc ->
                        RecognitionEvidence(
                            source = EvidenceSource.SELECTED_REGION_OCR,
                            report = NutritionTableInterpreter.interpret(doc),
                            document = doc,
                            crop = crop,
                            physicalObservation = stillId,
                        )
                    }
                },
                // No real ML Kit available in a JVM test -- the targeted reread cannot recover
                // anything this replay didn't already record, so it degrades safely (returns null),
                // exactly as it does on-device when a reread genuinely finds nothing new.
                recogniseTargetedReread = { _, _ -> null },
            )

            val verification = AutomaticVerification.verify(result.evidence)
            // Mirrors LabelScannerScreen's own fallback exactly: an outcome with no winning evidence
            // (Outcome.Nothing, Outcome.Conflicted) falls back to the whole capture's own document,
            // never null -- see readSelectedTable's `evaluationDocument` computation.
            val winner = result.outcome.winningEvidence
            val document = if (winner != null) winner.document else case.stillDocument
            val action = ScanPresentationDecision.decide(
                outcome = result.outcome,
                verification = verification,
                document = document,
                automatic = true,
            )

            val valueDescription = when (val reading = AutomaticScanAdvance.confidentReading(result.outcome)) {
                null -> "-"
                else -> "${reading.candidate.value.toPlainString()}/${reading.candidate.basis}"
            }

            report.append(
                "${case.timestamp} | ${case.recordedAction} | ${action.name} | $valueDescription\n",
            )
        }

        println(report)
        // The measurement is the printed table; a thrown exception is the only failure mode here.
    }
}

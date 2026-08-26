package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal

/**
 * Which recognition produced a piece of evidence (spec §6).
 *
 * The ordering is deliberate and is **not** a priority ranking — the resolver never picks by source.
 * It exists so diagnostics and evidence bundles list passes in the order they happen.
 */
enum class EvidenceSource {
    /** ML Kit over the whole uncropped capture. Always present when recognition succeeded. */
    FULL_FRAME_PASS_A,

    /** Pass A's elements restricted to the user's rectangle, re-parsed. No new recognition. */
    FILTERED_PASS_A,

    /**
     * A fresh recognition of the user-selected region, cropped from the upright source bitmap at
     * native resolution.
     *
     * Measured to genuinely recover labels the full frame cannot read (witte kaas `NotFound` ->
     * `2.3`; grated cheese's known-wrong `2.09` -> the printed `2`), and equally measured to destroy
     * canaries at other tightnesses (kinder, sondey, yoghurt, stokbrood all fall to `NotFound` at
     * some inset). It is therefore evidence, never an answer.
     */
    SELECTED_REGION_OCR,

    /**
     * A stable pre-shutter live frame.
     *
     * Retained because the device recording shows the live path reaching a usable interpretation
     * ("Table in view") immediately before a capture whose still path then returns `NotFound`. It can
     * corroborate and it can be offered for verification; it can never finish the scan on its own.
     */
    LIVE_STABLE_FRAME,
    ;

    /**
     * Which recognition run this evidence came from.
     *
     * **Load-bearing for consensus, and found by measurement rather than inspection.**
     * [FULL_FRAME_PASS_A] and [FILTERED_PASS_A] are two *parses* of one *recognition*: filtered is
     * literally a subset of the same elements, carrying the same characters. Their agreeing proves
     * nothing beyond "the filter kept the winning row".
     *
     * Treating that as corroboration counts one opinion twice — and it is not hypothetical. Grated
     * cheese resolved to the known-wrong `2.09` precisely because the two Pass A views agreed, which
     * satisfied the consensus rule and suppressed the independent recognition that would have
     * disagreed with them. Same trap as re-recognising the whole frame, by a different route.
     */
    val recognitionRun: RecognitionRun
        get() = when (this) {
            FULL_FRAME_PASS_A, FILTERED_PASS_A -> RecognitionRun.PASS_A
            SELECTED_REGION_OCR -> RecognitionRun.SELECTED_REGION
            LIVE_STABLE_FRAME -> RecognitionRun.LIVE
        }
}

/**
 * A distinct execution of a recognizer.
 *
 * Consensus is counted over *runs*, never over sources: only separate runs can independently confirm
 * a value, because only they can independently get it wrong.
 */
enum class RecognitionRun { PASS_A, SELECTED_REGION, LIVE }

/**
 * One recognition pass's contribution, with everything the resolver needs to compare it to another.
 *
 * ## Why this type exists
 *
 * Previously each pass produced a bare [NutritionParseReport] and the code picked one implicitly —
 * the crop's result simply replaced the whole frame's. With more than one recognition in play that is
 * how a confident-wrong gets manufactured: the measurements for this pass contain a case where the
 * full frame says `2.09`, a 5% crop says `2`, and a 10% crop of a different rectangle says `2.04`.
 * Silently preferring any one of those is indefensible.
 *
 * An evidence object keeps *who said what*, so disagreement is detectable rather than invisible.
 */
data class RecognitionEvidence(
    val source: EvidenceSource,
    val report: NutritionParseReport,
    /** Recognized document backing [report], when one exists. Null when recognition itself failed. */
    val document: OcrDocument?,
    /** Wall-clock cost of this pass, for the latency budget (§28). */
    val elapsedMs: Long = 0,
) {
    val reading: LabelReading get() = report.reading

    /** The single accepted value, or null for `Ambiguous`/`NotFound`. */
    val value: BigDecimal? get() = (reading as? LabelReading.Confident)?.candidate?.value

    val basis: NutritionBasis? get() = (reading as? LabelReading.Confident)?.candidate?.basis

    val provenance: CandidateProvenance? get() = report.provenance

    /** True when this pass produced one accepted value. */
    val isConfident: Boolean get() = reading is LabelReading.Confident

    /**
     * Mean recognizer confidence of the elements making up the accepted value's row/span.
     *
     * Null when unknown — either the engine reported none, or there is no accepted value. Used only
     * to *withhold* trust (§8), never to choose which nutrient a number belongs to.
     */
    val valueConfidence: Float?
        get() {
            val candidate = (reading as? LabelReading.Confident)?.candidate ?: return null
            val elements = document?.elements ?: return null
            val box = candidate.geometry
            val overlapping = elements.filter { it.box.verticalOverlapRatio(box) > 0.5 }
            val scores = overlapping.mapNotNull { it.confidence }
            return scores.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        }

    /**
     * Numerically equal to [other]'s value, ignoring scale.
     *
     * `BigDecimal.equals` compares scale, so `53.5` and `53.50` would read as disagreement and the
     * resolver would refuse a genuine agreement. This repo has hit that trap twice already.
     */
    fun agreesOnValueWith(other: RecognitionEvidence): Boolean {
        val a = value ?: return false
        val b = other.value ?: return false
        return a.compareTo(b) == 0
    }

    /** Same value AND same basis. Basis disagreement is a real conflict, not a rounding artefact. */
    fun fullyAgreesWith(other: RecognitionEvidence): Boolean =
        agreesOnValueWith(other) && basis != null && basis == other.basis
}

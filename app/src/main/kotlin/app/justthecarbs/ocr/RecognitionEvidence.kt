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
    /**
     * Recognized document backing [report], when one exists. Null when recognition itself failed.
     *
     * **This document's coordinate space is the pass's own**, which is not always the capture's. A
     * [EvidenceSource.SELECTED_REGION_OCR] pass recognises a crop of the source bitmap, so its
     * element boxes and its `width`/`height` are crop-local. [crop] is what relates the two, and
     * every consumer must be explicit about which space it wants — see [sourceSpaceGeometry].
     */
    val document: OcrDocument?,
    /** Wall-clock cost of this pass, for the latency budget (§28). */
    val elapsedMs: Long = 0,
    /**
     * Where [document] sits inside the source bitmap, for a pass that recognised a sub-rectangle.
     *
     * Null means [document] is already in source-image coordinates — every pass except Strategy B,
     * which is why the field defaults to null and Pass A is untouched by its introduction.
     *
     * ## Why this has to be carried rather than recomputed
     *
     * [SelectedRegionCrop.toSourceSpace] has existed, with tests, since the crop pass was written,
     * and its KDoc states that "evidence and assisted-mode tapping" need it. It had **no production
     * caller at all** — so the translation was available and never applied, and Strategy B's
     * crop-local geometry travelled into a full-frame world unchanged. Recomputing the rectangle at
     * the point of use would mean re-deriving it from the region and the bitmap dimensions at every
     * consumer, which is three places to get subtly different and no way to notice.
     */
    val crop: SelectedRegionCrop.PixelRect? = null,
) {
    val reading: LabelReading get() = report.reading

    /** The single accepted value, or null for `Ambiguous`/`NotFound`. */
    val value: BigDecimal? get() = (reading as? LabelReading.Confident)?.candidate?.value

    val basis: NutritionBasis? get() = (reading as? LabelReading.Confident)?.candidate?.basis

    val provenance: CandidateProvenance? get() = report.provenance

    /** True when this pass produced one accepted value. */
    val isConfident: Boolean get() = reading is LabelReading.Confident

    /**
     * Mean recognizer confidence of the elements making up the accepted value's **own clause**.
     *
     * Null when unknown — either the engine reported none, or there is no accepted value. Used only
     * to *withhold* trust (§8), never to choose which nutrient a number belongs to.
     *
     * ## Why the clause bound is here
     *
     * This used to select purely on `verticalOverlapRatio > 0.5`, with no horizontal bound, so on a
     * **merged row** it averaged the candidate's clause together with the child nutrient's. ML Kit
     * routinely merges the two: the seventh session measured one reconstructed row carrying
     * `Koolhydraten/Glucides 8,9 g` and `waarvan suikers/dont sucres 1,3 g` together.
     *
     * The consequence is measurable rather than theoretical. With a cleanly recognised carbohydrate
     * clause (0.90) beside a damaged sugars clause (0.10), the unbounded average is **0.443** —
     * under [EvidenceResolver.MIN_PROPOSAL_CONFIDENCE], so [EvidenceResolver] rule 4 dropped a
     * correct lone re-recognition *in silence* because the neighbouring clause read badly. Bounded,
     * the same fixture measures **0.70** and is proposed.
     *
     * Note the bound is [NutrientRowSegments]'s, not a tighter one invented here: its greedy span
     * walk puts the connective `waarvan` in the *total's* clause, so a damaged connective still
     * counts against the candidate. That is the intended reading of "this nutrient's own clause",
     * and deliberately not re-litigated at this call site — a second, differently-drawn boundary is
     * exactly the drift this bound exists to prevent.
     *
     * [NutrientRowSegments] is the same bound [ScaleAmbiguity], [RecoveryCandidates] and the
     * automatic path already use, so the four cannot disagree about where the clause ends. On an
     * ordinary single-nutrient row it returns null, the whole row is the clause, and the average is
     * exactly what it was before — pinned by `an ordinary row is unaffected`.
     *
     * **This can only ever withhold a proposal, never create one.** A wrong average cannot
     * manufacture a value; the bound simply stops it losing a good one for the wrong reason.
     */
    val valueConfidence: Float?
        get() {
            val candidate = (reading as? LabelReading.Confident)?.candidate ?: return null
            val doc = document ?: return null
            val box = candidate.geometry
            val overlapping = doc.elements.filter { it.box.verticalOverlapRatio(box) > 0.5 }

            // The candidate's printed clause, when the row carries more than one. Located from the
            // reconstructed row rather than from the raw element list, because a clause boundary is
            // a property of the row's nutrient names and their positions.
            val clause = LogicalRowBuilder.build(doc)
                .firstOrNull { row -> row.elements.any { it.box == box || it.box.verticalOverlapRatio(box) > 0.5 } }
                ?.let { NutrientRowSegments.totalCarbohydrateSegment(it) }

            val inClause = if (clause == null) overlapping else overlapping.filter { clause.contains(it.box) }
            val scores = inClause.mapNotNull { it.confidence }
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

    /**
     * Whether this pass placed its value on the label — i.e. established what it is measured per.
     *
     * A confident reading with a null basis is a value the parser found and could not place, which
     * this app never advances on. Stated as its own property rather than left implicit inside
     * [fullyAgreesWith] because the strong-path skip in [SelectedTableResolution] asks it directly,
     * and a condition that reads "and it states a basis" should not have to be inferred from an
     * agreement helper.
     */
    val statesABasis: Boolean get() = basis != null

    /**
     * The accepted candidate's box **in source-image coordinates**, or null when there is no
     * candidate.
     *
     * ## The one translation boundary
     *
     * Geometry is translated **exactly once**, here, and only for presentation, tapping,
     * highlighting and evidence export. It is deliberately *not* applied before parsing: the parser,
     * the row and column classifiers and [ScaleAmbiguity] all reason about a document in its own
     * space, and moving the boxes without moving the document they are compared against would break
     * every one of them. That is why [document] stays crop-local and this is a separate accessor
     * rather than a normalisation applied at construction.
     *
     * For every pass but Strategy B [crop] is null and this returns the geometry unchanged, so no
     * existing coordinate is disturbed.
     */
    val sourceSpaceGeometry: OcrBox?
        get() {
            val box = (reading as? LabelReading.Confident)?.candidate?.geometry ?: return null
            val origin = crop ?: return box
            return SelectedRegionCrop.toSourceSpace(box, origin)
        }
}

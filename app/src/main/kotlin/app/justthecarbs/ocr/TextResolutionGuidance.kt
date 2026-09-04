package app.justthecarbs.ocr

/**
 * Estimates whether the nutrition text in a frame is physically large enough to be recognised.
 *
 * ### Why this exists, and the very limited thing it claims
 *
 * A measured ratio sweep composited this repo's fixtures into realistic full-package frames and ran
 * the production path over them. At the smallest ratio most tables stopped reading, and the cause is
 * physical: the printed characters no longer carry enough pixels for the recognizer to resolve them.
 * No parser change addresses that, so the correct response is to ask the user to move closer *before*
 * they capture.
 *
 * **Text size explains only part of the failures, and this is the honest limit of the signal.** The
 * same calibration shows the largest-text cases failing too — kinder and yoghurt both fail at their
 * *biggest* rendering, where surrounding package prose is best recognised and competes hardest. That
 * is a completely different cause, it is invisible to any measure of character size, and this class
 * cannot detect it. A [Readiness.READY] frame may still fail for that reason or for focus, glare,
 * curvature, or a label shape the parser does not support.
 *
 * So: **advisory only, and one-directional.** It never produces, promotes, or blocks a carbohydrate
 * value, and it never gates the shutter. It reads live analysis frames — 1280x720 guidance-only frames
 * that can never become a result — and rules out the single failure mode that is knowable before the
 * shutter fires. [Readiness.READY] means "size is not the problem", never "this will work".
 *
 * ### The measurement
 *
 * Median recognized text height in pixels, expressed as a fraction of frame height. Median rather than
 * mean because a package frame contains a large brand name and a lot of tiny legal print, and either
 * tail would move a mean without telling us anything about the nutrition table.
 *
 * Text height rather than a table bounding box, because the table's extent conflates two different
 * things: a table can fill the frame and still be unreadable if it is a dense one photographed far
 * away. Character size is what recognition actually depends on.
 */
object TextResolutionGuidance {

    enum class Readiness {
        /** No text recognized at all — nothing to judge yet, not a complaint about the framing. */
        NO_TEXT,

        /** Text is present but too small to expect reliable recognition. */
        TOO_SMALL,

        /**
         * The text is running up or down the frame rather than across it — the package is sideways.
         *
         * Checked **before** size, because on a sideways frame the size measure is meaningless: it
         * reports the width of a rotated word as its height.
         */
        SIDEWAYS,

        /** Text is large enough that size is not the limiting factor. Not a guarantee of success. */
        READY,
    }

    data class Estimate(
        val readiness: Readiness,
        /** Median recognized text height as a fraction of frame height. */
        val medianHeightFraction: Double,
        val recognizedElements: Int,
    )

    /**
     * Below this fraction of frame height, recognition of nutrition-table print is unreliable.
     *
     * ### Calibrated, and deliberately set below the apparent boundary
     *
     * Measured with `TextResolutionCalibrationTest`, which composites the corpus at five ratios and
     * records median height fraction against the production path's actual verdict:
     *
     * ```
     * fixture          ratio   medianFrac   verdict
     * kinder            0.30      0.00716    NotFound
     * stokbrood         0.30      0.00911    NotFound
     * kinder            1.00      0.00938    Confident      <-- succeeds below 0.010
     * sondey            0.30      0.00977    Confident      <-- succeeds below 0.010
     * kinder            0.45      0.01042    NotFound       <-- fails above 0.010
     * yoghurt           0.30      0.01172    Ambiguous
     * stokbrood         0.45      0.01432    Confident
     * ```
     *
     * There is no clean separating value. Both known failures below 0.0092 are genuine size failures,
     * but two successes sit at 0.0094 and 0.0098, and failures continue well above any candidate
     * cutoff. So the threshold is placed **below every observed success** rather than at the middle of
     * the overlap: 0.0090 warns only where every measured case failed.
     *
     * That asymmetry is deliberate. A false TOO_SMALL contradicts the user — it tells someone whose
     * framing is fine to move closer, and being told that when the scan would have worked is exactly
     * how advisory guidance gets ignored. A false READY costs nothing beyond the retry the user was
     * going to make anyway. When the evidence is ambiguous, this stays quiet.
     */
    private const val MIN_MEDIAN_HEIGHT_FRACTION = 0.0090

    /**
     * Too few elements to trust a median. A frame with two recognized words is one the camera has
     * barely started on, not evidence about text size.
     */
    private const val MIN_ELEMENTS = 6

    /**
     * Below this median width-to-height ratio, the recognised words are standing on end.
     *
     * ### Measured, on the twelfth session's twelve captures
     *
     * A word is a wide, short box when its line runs across the frame, and a tall, narrow one when
     * the package is sideways. Measured over words of three characters or more — a one-glyph element
     * is roughly square whatever the rotation, so short tokens only blur the signal:
     *
     * ```
     * eleven upright captures    portrait words  0-9%     median W/H  1.82 - 2.65
     * 20260903-212804-751        portrait words  95%      median W/H  0.40
     * ```
     *
     * There is no overlap and nothing near the boundary: the nearest upright capture is 4.5x above
     * this threshold and the rotated one is 2.5x below it. That is why one is placed at 1.0 —
     * "the average recognised word is taller than it is wide", which is a statement about the frame
     * rather than a tuned constant.
     *
     * ### What this claims, and what it must not
     *
     * It claims the *frame* is sideways. It does not rotate anything, transpose any coordinate, or
     * change a single character, and it never gates the shutter — it reaches the user through the
     * same advisory channel [Readiness.TOO_SMALL] does.
     *
     * Rotating the geometry and re-parsing was considered and is **not** done here. It would put a
     * safety-critical assumption — which image direction printed reading order runs in, on which
     * [CarbohydrateTermAnchor]'s "nearest nutrient name to the left" depends — on the evidence of a
     * single photograph. Getting that backwards silently reverses which nutrient owns a number,
     * which is the failure class this parser exists to prevent. Asking the user to turn the package
     * costs one gesture and assumes nothing.
     */
    private const val MIN_MEDIAN_WIDTH_TO_HEIGHT = 1.0

    /** Words shorter than this are near-square whatever the rotation, so they carry no orientation. */
    private const val MIN_ORIENTATION_WORD_LENGTH = 3

    /** Below this many measurable words, the orientation estimate is noise rather than evidence. */
    private const val MIN_ORIENTATION_WORDS = 6

    fun estimate(document: OcrDocument): Estimate {
        // OcrDocument already guarantees a positive height, so only the element count is checked.
        if (document.elements.size < MIN_ELEMENTS) {
            return Estimate(Readiness.NO_TEXT, 0.0, document.elements.size)
        }

        // Orientation before size: a sideways frame's "text height" is really a word's width, so the
        // size verdict computed from it describes nothing.
        if (isSideways(document)) {
            return Estimate(Readiness.SIDEWAYS, 0.0, document.elements.size)
        }

        val heights = document.elements.map { it.box.height }.sorted()
        val median = heights[heights.size / 2].toDouble()
        val fraction = median / document.height
        val readiness =
            if (fraction >= MIN_MEDIAN_HEIGHT_FRACTION) Readiness.READY else Readiness.TOO_SMALL
        return Estimate(readiness, fraction, document.elements.size)
    }

    /** Whether the recognised words in [document] are standing on end. */
    internal fun isSideways(document: OcrDocument): Boolean {
        val words = document.elements.filter { it.text.trim().length >= MIN_ORIENTATION_WORD_LENGTH }
        if (words.size < MIN_ORIENTATION_WORDS) return false
        val ratios = words
            .map { it.box.width.toDouble() / it.box.height.coerceAtLeast(1) }
            .sorted()
        return ratios[ratios.size / 2] < MIN_MEDIAN_WIDTH_TO_HEIGHT
    }
}

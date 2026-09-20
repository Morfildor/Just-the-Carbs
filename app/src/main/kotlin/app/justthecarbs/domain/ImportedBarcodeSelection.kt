package app.justthecarbs.domain

/**
 * Decides what an imported photograph's detections amount to — as a value, not as a branch inside a
 * composable.
 *
 * ## Why this is its own unit
 *
 * The same reasoning [app.justthecarbs.ocr.ImportedPhotoIntake] records: this repo has twice shipped
 * a safety rule as a local `val` or an anonymous `else` inside a composable that binds a real camera
 * and is unreachable from the JVM, and both times the rule could be reverted without failing a
 * single test. **A rule no test can reach is a rule that can be silently reverted.**
 *
 * The rule here is the one the whole feature rests on: **a photograph containing two different
 * products must never resolve to one of them silently.** A shelf photo, a receipt beside a packet,
 * a multipack whose outer and inner codes both show — each is an ordinary photograph and each puts
 * two valid GTINs in frame. Picking either one produces a confident lookup for a product the user
 * did not choose, which is §36's failure: indistinguishable, afterwards, from having scanned the
 * right thing.
 *
 * The live scanner answers the same question with [BarcodeStabilityTracker], which infers intent
 * from *time* — which code did the user hold the camera on. A still photograph has no time axis, so
 * that evidence does not exist and cannot be approximated. Asking is the only honest answer.
 *
 * ## What this deliberately does not do
 *
 * No ranking, no "largest box wins", no "first in reading order", no confidence score. Every such
 * rule is a guess dressed as a decision, and the one it gets wrong is invisible — the user sees a
 * product page and has no reason to doubt it. [Outcome.Choice] preserves every distinct code and
 * makes the user the tie-break, which is the only tie-break that knows what they were photographing.
 */
object ImportedBarcodeSelection {

    /** What a photograph's detections amount to. */
    sealed interface Outcome {
        /** No supported, validly-checksummed barcode was found. */
        data object None : Outcome

        /**
         * Exactly one distinct product code. Continues immediately: choosing the photograph *is*
         * the deliberate act the live path's geometry and hold gates exist to infer, so re-asking
         * would add a tap that answers nothing.
         */
        data class Single(val value: String) : Outcome

        /**
         * Several distinct product codes, in first-seen order. The caller must ask; it must never
         * pick.
         */
        data class Choice(val values: List<String>) : Outcome
    }

    /**
     * Collapses [detections] to an outcome.
     *
     * Every element is already a validated, normalised barcode — [BarcodeFrameReader] is the sole
     * boundary where a raw ML Kit string becomes one, for the camera and for a photograph alike, so
     * an invalid or unsupported detection has been refused before it can reach here. That is what
     * lets this function be about *choice* alone and hold no validation rule of its own; a second
     * copy of the validation would be a second thing to drift.
     *
     * De-duplication is on the **normalised value**, which is the identity the rest of the app uses
     * as its database key. Two reads of one printed barcode collapse, and so do a UPC-A and the
     * EAN-13 of the same article, because they are the same product and offering both would ask the
     * user to choose between two spellings of one answer. Order is first-seen, so the list the user
     * reads is stable and matches the order the detections arrived in rather than a sort that would
     * reorder on re-recognition.
     */
    fun of(detections: List<NormalizedBarcode>): Outcome {
        val distinct = LinkedHashSet<String>()
        for (detection in detections) distinct += detection.value

        return when (distinct.size) {
            0 -> Outcome.None
            1 -> Outcome.Single(distinct.first())
            else -> Outcome.Choice(distinct.toList())
        }
    }
}

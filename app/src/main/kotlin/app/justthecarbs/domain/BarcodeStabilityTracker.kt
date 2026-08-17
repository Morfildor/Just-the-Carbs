package app.justthecarbs.domain

/**
 * A box in normalized image coordinates, 0..1 on both axes.
 *
 * Normalized rather than pixel-valued so the acceptance rules read the same on every sensor, preview
 * size and rotation. A pixel threshold tuned on one device is a different rule on the next.
 */
data class NormalizedBox(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    val centerX: Double get() = (left + right) / 2.0
    val centerY: Double get() = (top + bottom) / 2.0
    val width: Double get() = (right - left).coerceAtLeast(0.0)
    val height: Double get() = (bottom - top).coerceAtLeast(0.0)

    /**
     * The longer side. A barcode photographed with the phone turned is tall rather than wide, and
     * judging its size by width alone would refuse a perfectly framed code.
     */
    val longestSide: Double get() = maxOf(width, height)
}

/** One decoded, already **validated** barcode as it appeared in a single analyzed frame. */
data class NormalizedBarcode(
    /** Normalized by [BarcodeValidator]. A raw ML Kit value never reaches this type. */
    val value: String,
    val format: BarcodeFormat,
    val box: NormalizedBox,
    val timestampNanos: Long,
)

/** What the scanner should do, and show, after a frame. */
sealed interface BarcodeAcceptance {
    /** Nothing decodable, or nothing decodable that is framed and close enough yet. */
    data object Searching : BarcodeAcceptance

    /** A qualified barcode is being held. One more moment of steadiness and it fires. */
    data object Stabilizing : BarcodeAcceptance

    /** Emitted exactly once per [BarcodeStabilityTracker.reset]. */
    data class Accepted(val value: String) : BarcodeAcceptance
}

/**
 * Centralized, reviewable acceptance geometry and timing.
 *
 * All fractions, never pixels. The region is deliberately more generous than the frame drawn on
 * screen: the drawn frame is a target, and the exact preview-to-buffer mapping depends on the
 * preview's crop, which a pure tracker cannot see. Requiring the *centre* inside a generous region
 * is the rule that survives that uncertainty — demanding the whole box fit a tight rectangle would
 * refuse well-aimed scans (§2).
 */
object BarcodeAcceptanceThresholds {
    /** How far from each edge the barcode's centre must stay, as a fraction of the frame. */
    const val REGION_INSET = 0.15

    /**
     * The barcode's longer side, as a fraction of that dimension, before it counts as "close enough".
     *
     * A code the user is still walking toward decodes long before it is the one they mean. 0.20 is
     * roughly the point at which a barcode is deliberately presented rather than merely visible.
     */
    const val MIN_LONGEST_SIDE = 0.20

    /** Consecutive qualified frames carrying the same value. */
    const val REQUIRED_FRAMES = 3

    /** A never-below floor, so a single lucky frame can never be enough on any frame rate. */
    const val MINIMUM_FRAMES = 2

    /**
     * How long a qualified barcode must persist when frames arrive too slowly to reach
     * [REQUIRED_FRAMES] quickly.
     *
     * At 30 fps the frame count fires first, at about 100 ms — the "tiny stabilization moment" rather
     * than a wait. On a device delivering 6 fps the count would take half a second, so this releases
     * it earlier while [MINIMUM_FRAMES] still holds.
     */
    const val STABLE_DURATION_NANOS = 250_000_000L
}

/**
 * Decides when a decoded barcode is the one the user meant (§1-§3).
 *
 * The scanner previously fired on the first frame that decoded anything: a one-way latch, no
 * geometry, no time. Raising the phone toward a shelf is enough to decode a neighbouring product for
 * one frame, and the app would then commit to a lookup, land on *Product not found*, and — before
 * this pass — offer no way back to the camera. The decode was real; it just was not the user's
 * intent.
 *
 * A barcode must clear every gate to be accepted:
 *
 * ```
 * supported format -> valid check digit -> inside the scan region -> large enough
 *   -> temporally stable -> not already submitted
 * ```
 *
 * The first two happen before this class sees anything: it takes [NormalizedBarcode], whose value is
 * by construction already through [BarcodeValidator], so a misread digit cannot even begin to
 * accumulate stability. The last is the [accepted] latch.
 *
 * Pure, so the whole acceptance policy is JVM-testable with no camera and no emulator.
 */
class BarcodeStabilityTracker(
    private val regionInset: Double = BarcodeAcceptanceThresholds.REGION_INSET,
    private val minLongestSide: Double = BarcodeAcceptanceThresholds.MIN_LONGEST_SIDE,
    private val requiredFrames: Int = BarcodeAcceptanceThresholds.REQUIRED_FRAMES,
    private val minimumFrames: Int = BarcodeAcceptanceThresholds.MINIMUM_FRAMES,
    private val stableDurationNanos: Long = BarcodeAcceptanceThresholds.STABLE_DURATION_NANOS,
) {
    private var trackedValue: String? = null
    private var firstSeenNanos: Long = 0L
    private var consecutiveFrames: Int = 0
    private var accepted = false

    /**
     * Feeds one frame's decoded barcodes.
     *
     * [candidates] is every validated barcode in the frame, in no particular order. The largest
     * qualifying one wins: when two products are in shot, the one the user has moved closer to is
     * the one they are pointing at.
     */
    fun onFrame(candidates: List<NormalizedBarcode>, nowNanos: Long): BarcodeAcceptance {
        if (accepted) return BarcodeAcceptance.Searching

        val dominant = candidates
            .filter { qualifies(it.box) }
            .maxByOrNull { it.box.longestSide }

        if (dominant == null) {
            // Losing sight of the code — or seeing only an unqualified one — restarts the count.
            // Stability has to mean "held", not "seen this often in total".
            clearTracking()
            return BarcodeAcceptance.Searching
        }

        if (dominant.value != trackedValue) {
            // A different code became dominant. Its predecessor's frames say nothing about this one.
            trackedValue = dominant.value
            firstSeenNanos = nowNanos
            consecutiveFrames = 1
        } else {
            consecutiveFrames += 1
        }

        val heldLongEnough = consecutiveFrames >= requiredFrames ||
            (consecutiveFrames >= minimumFrames && nowNanos - firstSeenNanos >= stableDurationNanos)

        if (!heldLongEnough) return BarcodeAcceptance.Stabilizing

        accepted = true
        return BarcodeAcceptance.Accepted(dominant.value)
    }

    /**
     * Clears tracking **and** the one-shot latch, so the next stable barcode is accepted afresh.
     *
     * Called when the scanner resumes, when the user returns from *Product not found*, and whenever
     * the analyzer is rebuilt — the three moments at which a previous acceptance stops being
     * relevant.
     */
    fun reset() {
        clearTracking()
        accepted = false
    }

    private fun clearTracking() {
        trackedValue = null
        firstSeenNanos = 0L
        consecutiveFrames = 0
    }

    /**
     * Centre inside the region and long enough to be deliberate.
     *
     * The whole box is deliberately not required to fit: a barcode wrapped around a jar is wider than
     * any frame it is aimed at, and demanding containment would refuse exactly the packages that are
     * hardest to scan (§2).
     */
    private fun qualifies(box: NormalizedBox): Boolean {
        if (box.longestSide < minLongestSide) return false
        val low = regionInset
        val high = 1.0 - regionInset
        return box.centerX in low..high && box.centerY in low..high
    }
}

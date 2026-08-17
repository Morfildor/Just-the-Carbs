package app.justthecarbs.domain

/**
 * Turns one raw detection into a [NormalizedBarcode], or refuses it.
 *
 * The whole "supported format -> valid check digit -> normalized coordinates" front of the
 * acceptance pipeline (§1), kept pure so it is JVM-testable and so [BarcodeStabilityTracker] can
 * state a real invariant: everything it receives has already passed validation, therefore a misread
 * digit can never accumulate stability toward a lookup for a different product.
 *
 * The caller supplies the **upright** image dimensions. ML Kit reports bounding boxes in the
 * coordinate space of the image as rotated for recognition, so for a 90/270-degree rotation the
 * buffer's width and height are swapped before they get here. Getting that backwards would put every
 * box's centre in the wrong half of the frame and quietly invert the region gate.
 */
object BarcodeFrameReader {

    fun read(
        rawValue: String?,
        format: BarcodeFormat?,
        boxLeft: Int,
        boxTop: Int,
        boxRight: Int,
        boxBottom: Int,
        uprightWidth: Int,
        uprightHeight: Int,
        timestampNanos: Long,
    ): NormalizedBarcode? {
        if (rawValue == null || format == null) return null
        if (uprightWidth <= 0 || uprightHeight <= 0) return null

        val value = BarcodeValidator.validate(rawValue, format) ?: return null

        val left = boxLeft.toDouble() / uprightWidth
        val right = boxRight.toDouble() / uprightWidth
        val top = boxTop.toDouble() / uprightHeight
        val bottom = boxBottom.toDouble() / uprightHeight
        if (right < left || bottom < top) return null

        return NormalizedBarcode(
            value = value,
            format = format,
            // Clamped, not rejected: ML Kit can report a box edge a pixel or two outside the frame
            // for a code running off the edge, and that is an ordinary detection, not a malformed one.
            box = NormalizedBox(
                left = left.coerceIn(0.0, 1.0),
                top = top.coerceIn(0.0, 1.0),
                right = right.coerceIn(0.0, 1.0),
                bottom = bottom.coerceIn(0.0, 1.0),
            ),
            timestampNanos = timestampNanos,
        )
    }
}

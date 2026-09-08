package app.justthecarbs.ui.scan

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.justthecarbs.domain.BarcodeAcceptance
import app.justthecarbs.domain.BarcodeFormat
import app.justthecarbs.domain.BarcodeFrameReader
import app.justthecarbs.domain.BarcodeStabilityTracker
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Maps ML Kit's format constant to the domain-level [BarcodeFormat] so `domain/` never imports ML
 * Kit. Any detected format outside the four the scanner is configured for (§8) has no mapping and
 * is treated as unreadable rather than guessed at.
 */
private fun Int.toBarcodeFormat(): BarcodeFormat? = when (this) {
    Barcode.FORMAT_EAN_13 -> BarcodeFormat.EAN_13
    Barcode.FORMAT_EAN_8 -> BarcodeFormat.EAN_8
    Barcode.FORMAT_UPC_A -> BarcodeFormat.UPC_A
    Barcode.FORMAT_UPC_E -> BarcodeFormat.UPC_E
    else -> null
}

/**
 * Continuous barcode detection (§8), gated by [BarcodeStabilityTracker].
 *
 * This class deliberately holds no acceptance policy of its own. It is the ML Kit boundary: decode,
 * convert to normalized coordinates, hand the frame to the pure tracker, report what it decided.
 * The policy — framed, large enough, held, not already submitted — is all in `domain/`, where it is
 * testable without a camera.
 *
 * It previously accepted the first frame that decoded anything, latched by an `AtomicBoolean`. That
 * is why raising the phone toward a shelf could commit the app to a lookup for a neighbouring
 * product before the user had aimed at anything.
 */
class BarcodeAnalyzer(
    private val onAcceptance: (BarcodeAcceptance) -> Unit,
) : ImageAnalysis.Analyzer {

    private val tracker = BarcodeStabilityTracker()
    private var generation = 0L
    private var paused = false
    private var closed = false

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            // European supermarket products (§8). Restricting the format list measurably speeds up
            // detection, and every extra format is one more thing that can misread.
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
            )
            .build(),
    )

    /** Re-arms the scanner: clears both the held-barcode count and the one-shot latch. */
    @Synchronized
    fun setPaused(value: Boolean) {
        paused = value
        generation++
        tracker.reset()
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val frameGeneration = synchronized(this) {
            if (paused || closed) { imageProxy.close(); return }
            generation
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        // ML Kit reports bounding boxes in the coordinate space of the image *as rotated* for
        // recognition, so the upright frame's width and height are swapped for a quarter turn.
        // Reading these the wrong way round would mirror every centre into the wrong half of the
        // frame and silently invert the region gate.
        val uprightWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
        val uprightHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height
        val now = System.nanoTime()

        val input = InputImage.fromMediaImage(mediaImage, rotation)
        try {
            scanner.process(input)
                .addOnSuccessListener { barcodes ->
                    val candidates = barcodes.mapNotNull { barcode ->
                        val box = barcode.boundingBox ?: return@mapNotNull null
                        // A misread digit must not become a lookup for a different product (§36). UPC-E
                        // is format-aware here rather than falling through to EAN-8's plain length
                        // rules, since an 8-digit UPC-E raw value is a compressed UPC-A, not an EAN-8.
                        BarcodeFrameReader.read(
                            rawValue = barcode.rawValue,
                            format = barcode.format.toBarcodeFormat(),
                            boxLeft = box.left,
                            boxTop = box.top,
                            boxRight = box.right,
                            boxBottom = box.bottom,
                            uprightWidth = uprightWidth,
                            uprightHeight = uprightHeight,
                            timestampNanos = now,
                        )
                    }
                    synchronized(this) {
                        if (!closed && !paused && generation == frameGeneration) {
                            onAcceptance(tracker.onFrame(candidates, now))
                        }
                    }
                }
                // A failed frame is not worth reporting: the next one arrives in milliseconds.
                .addOnCompleteListener { imageProxy.close() }
        } catch (_: Exception) {
            imageProxy.close()
        }
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        generation++
        scanner.close()
    }
}

package app.justthecarbs.ui.scan

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.justthecarbs.domain.BarcodeFormat
import app.justthecarbs.domain.BarcodeValidator
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

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
 * Continuous barcode detection (§8).
 *
 * The camera streams; the user never has to press a shutter. Detection is debounced by a one-way
 * latch rather than a timer: once a valid code is accepted the analyzer stops emitting entirely,
 * which is the only way to guarantee a single navigation from a stream that can deliver the same
 * frame's result several times in a row (§8, §36 "duplicate scans").
 */
class BarcodeAnalyzer(
    private val onBarcode: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val consumed = AtomicBoolean(false)

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

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (consumed.get()) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val code = barcodes.asSequence()
                    .mapNotNull { barcode ->
                        val raw = barcode.rawValue ?: return@mapNotNull null
                        val format = barcode.format.toBarcodeFormat() ?: return@mapNotNull null
                        raw to format
                    }
                    // A misread digit must not become a lookup for a different product (§36). UPC-E
                    // is format-aware here rather than falling through to EAN-8's plain length rules,
                    // since an 8-digit UPC-E raw value is a compressed UPC-A, not an EAN-8.
                    .mapNotNull { (raw, format) -> BarcodeValidator.validate(raw, format) }
                    .firstOrNull()

                if (code != null && consumed.compareAndSet(false, true)) {
                    onBarcode(code)
                }
            }
            // A failed frame is not worth reporting: the next one arrives in milliseconds.
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() = scanner.close()
}

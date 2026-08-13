package app.carbscan.ui.scan

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.carbscan.domain.BarcodeValidator
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

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
                    .mapNotNull { it.rawValue }
                    // A misread digit must not become a lookup for a different product (§36).
                    .mapNotNull(BarcodeValidator::normalize)
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

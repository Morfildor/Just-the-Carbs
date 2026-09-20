package app.justthecarbs.ui.scan

import android.content.Context
import android.net.Uri
import app.justthecarbs.domain.BarcodeFrameReader
import app.justthecarbs.domain.ImportedBarcodeSelection
import app.justthecarbs.domain.NormalizedBarcode
import app.justthecarbs.ocr.OcrDiagnosticsLogger
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Reads barcodes out of a staged photograph — the ML Kit boundary for the imported-photo path, the
 * exact counterpart of [BarcodeAnalyzer] for the live one.
 *
 * ## How this converges with the live scanner
 *
 * Every detection goes through [BarcodeFrameReader.read] with the same arguments the analyzer
 * passes, so a raw ML Kit string becomes a validated, normalised [NormalizedBarcode] by **one**
 * route whichever way the photograph arrived. That function is where the supported-format check and
 * [app.justthecarbs.domain.BarcodeValidator] live, so an unsupported symbology or a failed check
 * digit is refused here identically to a camera frame — and refused *before* selection, so an
 * invalid candidate can never appear in a choice list or navigate.
 *
 * Downstream of the value there is likewise nothing new: the screen hands it to the same
 * `onBarcode` callback the analyzer fires, which is the same `Routes.product(barcode)` navigation
 * a live scan takes. There is no second lookup path, and could not be one without adding it.
 *
 * ## What is deliberately NOT reused: the stability tracker
 *
 * [app.justthecarbs.domain.BarcodeStabilityTracker] is skipped, and this is the one place the two
 * paths genuinely differ. It answers *"is the user pointing at this on purpose?"* from evidence a
 * still photograph does not contain — the same code held across several frames, centred, and large
 * enough in the viewport. Those gates exist because a camera sweeping toward a shelf decodes
 * whatever passes through the frame, so intent has to be inferred from time and aim.
 *
 * Choosing a photograph out of the picker *is* that intent, stated directly. Applying the geometry
 * gate to an import would refuse exactly the cases the feature exists for — a barcode small in the
 * corner of a screenshot, or off-centre in a photo of a whole packet — for failing to be aimed at,
 * in an image nobody was aiming. The question it cannot answer alone is *which* code was meant when
 * several are present, and that is [ImportedBarcodeSelection]'s, answered by asking rather than by
 * a weaker inference.
 *
 * ## Orientation
 *
 * [InputImage.fromFilePath] reads the file's own EXIF orientation tag, which is why staging copies
 * bytes verbatim and never re-encodes: a rotated photograph arrives upright here without this class
 * knowing anything about rotation. The upright dimensions handed to [BarcodeFrameReader] therefore
 * come from the `InputImage` itself rather than being derived, as the analyzer must derive them
 * from a rotation it is told about separately.
 */
internal object ImportedBarcodeReader {

    /**
     * Recognises [file] and returns what its detections amount to.
     *
     * Suspending and cancellable: a caller abandoning this import (a newer photo chosen, the screen
     * closed) stops awaiting rather than holding the coroutine open, and the result of an abandoned
     * read is simply never returned to it. Cancellation does not, and need not, interrupt ML Kit
     * itself — the client is closed in the same `finally`, and the decisive guard against a stale
     * result reaching the screen is the caller's generation check at the single point a result
     * becomes state.
     *
     * A recognition failure returns [ImportedBarcodeSelection.Outcome.None] rather than throwing.
     * To the user there is one statement to make — no usable barcode was found in that photo — and
     * the recovery it leads to is identical, so distinguishing "ML Kit declined the image" from
     * "the image has no barcode in it" would name a cause they cannot act on differently.
     */
    suspend fun read(context: Context, file: File): ImportedBarcodeSelection.Outcome {
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                // The same four formats the live scanner is configured for, from the same constant.
                .setBarcodeFormats(PRIMARY_BARCODE_FORMAT, *OTHER_BARCODE_FORMATS)
                .build(),
        )

        return try {
            val image = runCatching { InputImage.fromFilePath(context, Uri.fromFile(file)) }
                .getOrElse { error ->
                    OcrDiagnosticsLogger.failure("Could not decode the imported barcode photo", error)
                    return ImportedBarcodeSelection.Outcome.None
                }

            val detections = suspendCancellableCoroutine { continuation ->
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val now = System.nanoTime()
                        val read = barcodes.mapNotNull { barcode ->
                            val box = barcode.boundingBox ?: return@mapNotNull null
                            BarcodeFrameReader.read(
                                rawValue = barcode.rawValue,
                                format = barcode.format.toBarcodeFormat(),
                                boxLeft = box.left,
                                boxTop = box.top,
                                boxRight = box.right,
                                boxBottom = box.bottom,
                                // The InputImage's own dimensions, already upright:
                                // `fromFilePath` applies the EXIF tag itself.
                                uprightWidth = image.width,
                                uprightHeight = image.height,
                                timestampNanos = now,
                            )
                        }
                        if (continuation.isActive) continuation.resume(read)
                    }
                    .addOnFailureListener { error ->
                        OcrDiagnosticsLogger.failure("Could not read barcodes from the photo", error)
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
            }

            ImportedBarcodeSelection.of(detections)
        } finally {
            // A client per read, unlike SelectedRegionRecognizer's process-lifetime one: an import
            // is an occasional, user-initiated act rather than something happening on every tap, so
            // holding a native detector open between photographs would cost memory for a latency
            // win nobody is waiting on.
            scanner.close()
        }
    }
}

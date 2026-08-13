package app.carbscan.ocr

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * On-device nutrition-label OCR (§29, §30).
 *
 * Recognition runs entirely on the device and the frames are never written to disk or uploaded —
 * each [ImageProxy] is closed as soon as it has been read, and only the extracted *text* leaves
 * this class. That is the whole of §30's privacy requirement, enforced structurally rather than by
 * a promise: there is no code path here that could persist an image.
 *
 * Unlike the barcode analyzer this does not latch after the first hit. Label text arrives
 * progressively as the user steadies the camera, and re-reading lets a partial first pass become a
 * confident one. The user still confirms every value.
 */
class LabelAnalyzer(
    private val onReading: (LabelReading) -> Unit,
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @Volatile
    private var paused = false

    /** Stops emitting once the user is looking at a candidate, so the card cannot change under them. */
    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (paused || mediaImage == null) {
            imageProxy.close()
            return
        }

        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(input)
            .addOnSuccessListener { text ->
                val reading = NutritionLabelParser.parse(text.text)
                // Silence until there is something worth showing: reporting NotFound on every
                // frame would flicker the UI while the user is still framing the label.
                if (reading !is LabelReading.NotFound) onReading(reading)
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() = recognizer.close()
}

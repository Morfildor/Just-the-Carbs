package app.carbscan.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** On-device structured nutrition-label OCR for live frames and temporary still captures. */
class LabelAnalyzer(
    private val onReading: (LabelReading) -> Unit,
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val inFlight = AtomicBoolean(false)
    private val pendingStill = AtomicReference<StillRequest?>(null)
    private val closed = AtomicBoolean(false)

    @Volatile
    private var paused = false

    @Volatile
    private var lastResolution: Pair<Int, Int>? = null

    /** Stops emitting once the user is considering a candidate. */
    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (paused || mediaImage == null || !inFlight.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val resolution = imageProxy.width to imageProxy.height
        if (resolution != lastResolution) {
            lastResolution = resolution
            OcrDiagnosticsLogger.analysisResolution(resolution.first, resolution.second)
        }

        val started = System.nanoTime()
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(input)
            .addOnSuccessListener { text ->
                val report = parse(text, imageProxy.width, imageProxy.height, started)
                if (!paused && report.reading !is LabelReading.NotFound) onReading(report.reading)
            }
            .addOnFailureListener { OcrDiagnosticsLogger.failure("Live OCR failed", it) }
            .addOnCompleteListener {
                inFlight.set(false)
                imageProxy.close()
                startPendingStillIfPossible()
            }
    }

    /**
     * Reads a high-resolution cache file, reports even `NotFound`, then deletes the file.
     * The file is never inserted into MediaStore and never survives completion or setup failure.
     */
    fun analyzeStill(context: Context, file: File, onComplete: (LabelReading) -> Unit) {
        val request = StillRequest(context.applicationContext, file, onComplete)
        val replaced = pendingStill.getAndSet(request)
        if (replaced != null) {
            replaced.file.delete()
            replaced.onComplete(LabelReading.NotFound)
        }
        startPendingStillIfPossible()
    }

    private fun startPendingStillIfPossible() {
        if (closed.get() || !inFlight.compareAndSet(false, true)) return
        val request = pendingStill.getAndSet(null)
        if (request == null) {
            inFlight.set(false)
            if (pendingStill.get() != null) startPendingStillIfPossible()
            return
        }

        val started = System.nanoTime()
        try {
            val dimensions = BitmapFactory.Options().also { options ->
                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(request.file.absolutePath, options)
            }
            OcrDiagnosticsLogger.stillResolution(dimensions.outWidth, dimensions.outHeight)
            val input = InputImage.fromFilePath(request.context, Uri.fromFile(request.file))
            recognizer.process(input)
                .addOnSuccessListener { text ->
                    request.onComplete(parse(text, dimensions.outWidth, dimensions.outHeight, started).reading)
                }
                .addOnFailureListener {
                    OcrDiagnosticsLogger.failure("Still OCR failed", it)
                    request.onComplete(LabelReading.NotFound)
                }
                .addOnCompleteListener {
                    if (!request.file.delete()) {
                        OcrDiagnosticsLogger.failure("Temporary label image was already absent")
                    }
                    inFlight.set(false)
                    startPendingStillIfPossible()
                }
        } catch (error: Exception) {
            OcrDiagnosticsLogger.failure("Could not prepare still image", error)
            request.file.delete()
            request.onComplete(LabelReading.NotFound)
            inFlight.set(false)
            startPendingStillIfPossible()
        }
    }

    private fun parse(text: Text, width: Int, height: Int, started: Long): NutritionParseReport {
        val report = NutritionTableParser.parseWithDiagnostics(MlKitOcrMapper.toDocument(text, width, height))
        OcrDiagnosticsLogger.report((System.nanoTime() - started) / 1_000_000, report)
        return report
    }

    fun close() {
        closed.set(true)
        paused = true
        pendingStill.getAndSet(null)?.file?.delete()
        recognizer.close()
    }

    private data class StillRequest(
        val context: Context,
        val file: File,
        val onComplete: (LabelReading) -> Unit,
    )
}

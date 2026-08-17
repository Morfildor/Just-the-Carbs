package app.justthecarbs.ocr

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
    private val stability = AmbiguityStabilityTracker()

    @Volatile
    private var paused = false

    @Volatile
    private var lastResolution: Pair<Int, Int>? = null

    /** Stops emitting once the user is considering a candidate. */
    fun pause() {
        paused = true
    }

    fun resume() {
        stability.reset()
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
                val toSurface = stability.onFrame(report.reading, System.nanoTime())
                if (!paused && toSurface != null) onReading(toSurface)
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
     *
     * Reports the whole [NutritionParseReport] rather than just its reading, so a still capture can
     * also surface a per-serving figure the user may save as a countable portion (spec §17). The
     * live-frame path below is deliberately unchanged and still deals only in [LabelReading] — a
     * camera frame must never be able to persist anything.
     */
    fun analyzeStill(
        context: Context,
        file: File,
        /**
         * What the user framed, as fractions of the visible preview (§10). Null means "read the
         * whole frame", which is what happens before the overlay has been measured and is the
         * behaviour that shipped before this pass.
         */
        region: NormalizedRegion?,
        onComplete: (NutritionParseReport) -> Unit,
    ) {
        val request = StillRequest(context.applicationContext, file, region, onComplete)
        val replaced = pendingStill.getAndSet(request)
        if (replaced != null) {
            replaced.file.delete()
            replaced.onComplete(NutritionParseReport(LabelReading.NotFound, emptyList()))
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

            // Crop to what the user framed. Falling back to the whole file — via ML Kit's own
            // EXIF-aware loader — whenever there is no region or the bitmap could not be decoded,
            // so the worst case is the behaviour that shipped before this pass rather than a
            // failed capture.
            val cropped = request.region?.let { region ->
                StillImageLoader.load(request.file, ScanRegionMapper.expand(region))
            }
            val input = if (cropped != null) {
                OcrDiagnosticsLogger.stillCropped(cropped.width, cropped.height)
                InputImage.fromBitmap(cropped, 0)
            } else {
                InputImage.fromFilePath(request.context, Uri.fromFile(request.file))
            }
            val ocrWidth = cropped?.width ?: dimensions.outWidth
            val ocrHeight = cropped?.height ?: dimensions.outHeight

            recognizer.process(input)
                .addOnSuccessListener { text ->
                    request.onComplete(parse(text, ocrWidth, ocrHeight, started, full = true))
                }
                .addOnFailureListener {
                    OcrDiagnosticsLogger.failure("Still OCR failed", it)
                    request.onComplete(NutritionParseReport(LabelReading.NotFound, emptyList()))
                }
                .addOnCompleteListener {
                    cropped?.recycle()
                    if (!request.file.delete()) {
                        OcrDiagnosticsLogger.failure("Temporary label image was already absent")
                    }
                    inFlight.set(false)
                    startPendingStillIfPossible()
                }
        } catch (error: Exception) {
            OcrDiagnosticsLogger.failure("Could not prepare still image", error)
            request.file.delete()
            request.onComplete(NutritionParseReport(LabelReading.NotFound, emptyList()))
            inFlight.set(false)
            startPendingStillIfPossible()
        }
    }

    /**
     * [full] asks for the complete pipeline trace (§9), which only a deliberate still capture gets:
     * a live frame arrives many times a second and would bury the capture that matters.
     */
    private fun parse(
        text: Text,
        width: Int,
        height: Int,
        started: Long,
        full: Boolean = false,
    ): NutritionParseReport {
        val document = MlKitOcrMapper.toDocument(text, width, height)
        val report = NutritionTableParser.parseWithDiagnostics(document)
        OcrDiagnosticsLogger.report((System.nanoTime() - started) / 1_000_000, report)
        if (full) OcrDiagnosticsLogger.stillDiagnostics(document, report)
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
        val region: NormalizedRegion?,
        val onComplete: (NutritionParseReport) -> Unit,
    )
}

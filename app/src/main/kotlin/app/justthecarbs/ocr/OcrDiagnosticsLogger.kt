package app.justthecarbs.ocr

import android.util.Log
import app.justthecarbs.BuildConfig

/** Debug-only OCR trace. The static BuildConfig branch is removed from minified release builds. */
object OcrDiagnosticsLogger {
    private const val TAG = "JustTheCarbsOCR"

    fun analysisResolution(width: Int, height: Int) {
        if (BuildConfig.DEBUG) Log.d(TAG, "analyzer resolution=${width}x$height")
    }

    fun stillResolution(width: Int, height: Int) {
        if (BuildConfig.DEBUG) Log.d(TAG, "still resolution=${width}x$height")
    }

    /**
     * One line per still scan giving the whole pipeline's cost, longest stage first — see [ScanTrace].
     *
     * Kept separate from [report], which times ML Kit alone. Device evidence had the recognizer at
     * ~8% of a median scan, so a log that reported only recognition described almost none of the wait
     * and made the remaining ~3.3 s a matter of guesswork.
     */
    fun timing(summary: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, summary)
    }

    /**
     * What CameraX actually negotiated for the still capture, versus what was requested (§23).
     *
     * Recorded because an earlier report treated a `900x1600` figure as proof that the camera had
     * produced a 1.4 MP still — it was the size of a **committed test fixture** being replayed, not a
     * camera output at all. `ImageCapture` asks for 3264x2448, but CameraX is free to negotiate
     * something else per device, and nothing in the app previously recorded what it settled on.
     *
     * The requested value is a *hint*; only [selected] is a fact about this device. Logging both
     * together is what makes a shortfall visible rather than assumed away.
     */
    fun captureConfiguration(
        requested: String,
        selected: String,
        cropRect: String,
        rotationDegrees: Int,
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "ImageCapture requested=$requested selected=$selected " +
                "viewportCrop=$cropRect rotation=$rotationDegrees",
        )
    }

    /**
     * What Pass A actually recognised, and how the scan region was used.
     *
     * The region is no longer a crop: recognition runs on the whole capture and the framing is
     * applied afterwards as relevance. Logging that explicitly matters because the previous, cropping
     * behaviour was invisible in the field — it silently removed the basis header band and cost both
     * canaries, and nothing on screen or in the log said the image had been cut down.
     */
    fun passA(width: Int, height: Int, region: NormalizedRegion?) {
        if (!BuildConfig.DEBUG) return
        val framing = region?.let {
            "relevance=[%.2f,%.2f,%.2f,%.2f]".format(it.left, it.top, it.right, it.bottom)
        } ?: "relevance=none"
        Log.d(TAG, "pass A (uncropped) recognised=${width}x$height $framing")
    }

    fun report(latencyMs: Long, report: NutritionParseReport) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "OCR latency=${latencyMs}ms outcome=${report.reading::class.simpleName}")
        report.diagnostics.forEach { Log.d(TAG, "${it.stage}: ${it.message}") }
    }

    /**
     * The full pipeline trace for a deliberate **still** capture (§9).
     *
     * Only for stills. A live frame arrives many times a second and dumping this for each one would
     * bury the capture that matters. Emitted line by line because logcat truncates a single long
     * message, and this is meant to be copied out whole.
     *
     * `BuildConfig.DEBUG` is a compile-time constant, so R8 removes this body and the call to it
     * from a minified release — the diagnostics cannot reach a shipped build.
     */
    fun stillDiagnostics(document: OcrDocument, report: NutritionParseReport) {
        if (!BuildConfig.DEBUG) return
        OcrDiagnosticsReport.render(document, report).lineSequence().forEach { Log.d(TAG, it) }
    }

    /**
     * The result of applying the user's confirmed crop to the retained recognition.
     *
     * [elementsBefore] and [elementsAfter] are the measurement that says whether the crop did
     * anything: identical counts mean the selection removed no interference, which is the first thing
     * to check when a confirmed crop still fails. [elapsedMs] is the re-parse alone — it excludes
     * recognition entirely, which is the point of reusing Pass A.
     */
    fun selectedTable(
        outcome: String,
        elementsBefore: Int,
        elementsAfter: Int,
        elapsedMs: Long,
        reading: LabelReading,
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "selected table: $outcome elements=$elementsBefore->$elementsAfter " +
                "reparse=${elapsedMs}ms outcome=${reading::class.simpleName}",
        )
    }

    fun failure(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.d(TAG, message, throwable)
    }
}

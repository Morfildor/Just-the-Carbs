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

    /** What OCR actually saw after the scan region was applied (§10, §12). */
    fun stillCropped(width: Int, height: Int) {
        if (BuildConfig.DEBUG) Log.d(TAG, "still cropped to=${width}x$height")
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

    fun failure(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.d(TAG, message, throwable)
    }
}

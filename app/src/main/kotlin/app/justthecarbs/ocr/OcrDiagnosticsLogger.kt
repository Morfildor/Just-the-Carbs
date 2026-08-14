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

    fun report(latencyMs: Long, report: NutritionParseReport) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "OCR latency=${latencyMs}ms outcome=${report.reading::class.simpleName}")
        report.diagnostics.forEach { Log.d(TAG, "${it.stage}: ${it.message}") }
    }

    fun failure(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.d(TAG, message, throwable)
    }
}

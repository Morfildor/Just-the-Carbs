package app.justthecarbs.ocr

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Bundles retained scan evidence into one zip and hands it to the system share sheet.
 *
 * Debug builds only. The `FileProvider` this depends on is declared in `src/debug/AndroidManifest.xml`
 * and does not exist in a release build at all, so [share] cannot resolve a URI there even if the
 * `BuildConfig.DEBUG` guards were somehow bypassed. Two independent barriers, because what this
 * exports is photographs the user took.
 */
object ScanEvidenceExport {

    /**
     * Writes every retained capture folder into a single zip and returns a share intent.
     *
     * Null when there is nothing to export or the build is not a debug build — the caller shows the
     * "nothing recorded yet" state rather than an empty share sheet.
     */
    fun share(context: Context): Intent? {
        if (!ScanEvidenceRecorder.enabled) return null
        val captures = ScanEvidenceRecorder.captures(context)
        if (captures.isEmpty()) return null

        val archive = File(ScanEvidenceRecorder.directory(context), ARCHIVE_NAME)
        val written = runCatching {
            ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                captures.forEach { folder ->
                    folder.walkTopDown().filter { it.isFile }.forEach { file ->
                        // The capture folder's own name (a timestamp) is kept as the zip path so
                        // several captures stay distinguishable once extracted.
                        zip.putNextEntry(ZipEntry("${folder.name}/${file.name}"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }.isSuccess

        if (!written || !archive.exists()) {
            OcrDiagnosticsLogger.failure("Could not build scan evidence archive")
            return null
        }

        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.evidence", archive)
        }.getOrElse {
            OcrDiagnosticsLogger.failure("Could not expose scan evidence archive", it)
            return null
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Just the Carbs — scan evidence")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let { Intent.createChooser(it, "Export scan evidence") }
    }

    private const val ARCHIVE_NAME = "scan-evidence.zip"
}

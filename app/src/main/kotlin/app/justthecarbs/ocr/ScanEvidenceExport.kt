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

        // Wait for the background writer to finish before listing anything.
        //
        // Evidence files are written off the scan path on purpose — that is the whole point of
        // `consumeCaptureAsync` and `recordPassAImageAsync`, and it must not change. But *export* is
        // a deliberate user action with no latency budget, and it is the one moment where a
        // half-written `passA.png` would be zipped as though it were complete. This is the only
        // place in the app that waits for the writer.
        ScanEvidenceRecorder.drain()

        val captures = ScanEvidenceRecorder.captures(context)
        if (captures.isEmpty()) return null

        // Written under a temporary name and renamed only after the archive is closed and verified.
        //
        // ### The measured failure
        //
        // The first evidence bundle uploaded from the third phone session was 58 MB, truncated
        // mid-entry, and had no central directory — an unreadable zip that nonetheless sat at the
        // shareable filename and was successfully shared. Writing in place means every intermediate
        // state of the file is visible under the name the share sheet hands out, so an interrupted
        // write is indistinguishable from a finished one.
        //
        // A temporary name plus an atomic rename makes the shareable name only ever refer to an
        // archive that was closed and read back successfully. A failure leaves the previous
        // archive — or nothing — rather than a partial one.
        val directory = ScanEvidenceRecorder.directory(context)
        val staging = File(directory, "$ARCHIVE_NAME.part")
        val archive = File(directory, ARCHIVE_NAME)
        staging.delete()

        val written = runCatching {
            ZipOutputStream(staging.outputStream().buffered()).use { zip ->
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

        if (!written || !staging.exists() || !ZipIntegrity.isComplete(staging)) {
            OcrDiagnosticsLogger.failure("Could not build a complete scan evidence archive")
            staging.delete()
            return null
        }

        archive.delete()
        if (!staging.renameTo(archive)) {
            OcrDiagnosticsLogger.failure("Could not finalise the scan evidence archive")
            staging.delete()
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

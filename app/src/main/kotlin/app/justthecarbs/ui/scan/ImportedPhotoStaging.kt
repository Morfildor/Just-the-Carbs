package app.justthecarbs.ui.scan

import android.content.Context
import android.net.Uri
import app.justthecarbs.ocr.ImportedImageStaging
import app.justthecarbs.ocr.OcrDiagnosticsLogger
import java.io.File

/**
 * The Android half of photo import: resolves a picked [Uri] to the same kind of cache file a shutter
 * press produces.
 *
 * Kept apart from [ImportedImageStaging] so the rules (size ceiling, truncation, failure vocabulary)
 * stay plain-JVM testable, and only the `ContentResolver` plumbing lives here. This file holds no
 * decisions — every refusal it reports comes from the tested object.
 *
 * ## The URI grant, and why nothing is held
 *
 * `PickVisualMedia` returns a URI carrying a **temporary** read grant, scoped to this process and
 * revoked when the activity that received it goes away. Nothing here takes a persistable grant
 * (`takePersistableUriPermission`), because nothing needs one: the bytes are copied immediately and
 * the URI is never referenced again. Persisting would hand the app a standing claim on a photograph
 * in the user's library for a single read — exactly the over-reach the system picker exists to
 * avoid, and the reason no storage permission appears in this app's manifest.
 *
 * The grant is also why the copy happens now rather than later: the URI may be dead by the time a
 * user finishes cropping, and a pipeline holding a URI would discover that halfway through.
 */
internal object ImportedPhotoStaging {

    /**
     * The cache-file prefix a nutrition-label import uses, and this function's default.
     *
     * Named rather than inlined because it is the one label-specific detail in an otherwise
     * input-agnostic object: the debug evidence recorder, the crop screen's preview and the
     * analyzer's own deletion all key on a capture-shaped file, and a label import must be
     * indistinguishable from a shutter press to all three.
     */
    const val LABEL_PREFIX: String = "justthecarbs-label-"

    /**
     * The prefix a barcode import uses.
     *
     * Deliberately different from [LABEL_PREFIX], and not merely for tidiness: a label import's
     * file is handed to the evidence recorder, which **renames** it into an evidence folder as
     * though it were a capture of a nutrition table. A barcode import's file is read once and
     * deleted. Sharing a prefix would make a barcode photograph indistinguishable from a label
     * capture in the one place that keeps files around to be read later by a person.
     */
    const val BARCODE_PREFIX: String = "justthecarbs-barcode-"

    sealed interface Result {
        /** A capture-shaped JPEG in `cacheDir`. The caller owns it exactly as it owns a capture. */
        data class Staged(val file: File, val bytes: Long) : Result
        data class Failed(val reason: ImportedImageStaging.Failure) : Result
    }

    /**
     * Copies [uri]'s bytes into a fresh cache file and returns it.
     *
     * Blocking, and must be called off the main thread — a large photograph is tens of megabytes of
     * I/O. The caller supplies [cancelled] so a result abandoned mid-copy (screen closed, newer
     * photo chosen) stops rather than finishing work nobody is waiting for.
     *
     * The file is named with the same [LABEL_PREFIX] a capture uses, deliberately: the debug
     * evidence recorder, the crop screen's preview and the analyzer's own deletion all treat it
     * identically, and a different prefix would be the first place the two inputs diverged.
     *
     * [prefix] defaults to exactly that, so the label path's behaviour is unchanged to the byte.
     * The barcode scanner passes [BARCODE_PREFIX] — the only generalisation this object needed to
     * serve a second caller, and deliberately the smallest one: every rule above (the ceiling, the
     * cancellation poll, the partial-file delete, the absence of a persistable grant) is shared
     * rather than reimplemented, which is what makes "one staging implementation" a property of the
     * code rather than a claim about two that happen to agree.
     */
    fun stage(
        context: Context,
        uri: Uri,
        cancelled: () -> Boolean = { false },
        prefix: String = LABEL_PREFIX,
    ): Result {
        val file = runCatching {
            File.createTempFile(prefix, ".jpg", context.cacheDir)
        }.getOrElse {
            OcrDiagnosticsLogger.failure("Could not create a file for the imported photo", it)
            return Result.Failed(ImportedImageStaging.Failure.INCOMPLETE)
        }

        val outcome = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    ImportedImageStaging.copy(input, output, cancelled)
                }
            } ?: ImportedImageStaging.Result.Failed(ImportedImageStaging.Failure.UNREADABLE)
        }.getOrElse { error ->
            // A revoked grant surfaces here as SecurityException, a deleted item as
            // FileNotFoundException. Both mean the same thing to the user — that photo cannot be
            // read — so both are reported as one, rather than inventing copy per exception type.
            OcrDiagnosticsLogger.failure("Could not read the imported photo", error)
            ImportedImageStaging.Result.Failed(ImportedImageStaging.Failure.UNREADABLE)
        }

        return when (outcome) {
            is ImportedImageStaging.Result.Staged -> Result.Staged(file, outcome.bytes)
            is ImportedImageStaging.Result.Failed -> {
                // A partial file is worse than none: it decodes to a torn image, and a torn image is
                // the input that produces a confidently wrong reading. It goes now, not at teardown.
                file.delete()
                Result.Failed(outcome.reason)
            }
        }
    }
}

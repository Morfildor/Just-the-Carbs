package app.justthecarbs.ui.scan

import android.content.Context
import android.net.Uri
import app.justthecarbs.ocr.ImportedImageStaging
import app.justthecarbs.ocr.OcrDiagnosticsLogger
import java.io.File

/**
 * Turns a shared content URI into a cache file this app owns, at the earliest safe moment.
 *
 * ## Why a share is copied sooner than a picked photo
 *
 * [ImportedPhotoStaging] already answers "how do I turn a URI into a capture-shaped file", and
 * this delegates to it entirely — the size ceiling, the cancellation poll, the partial-file
 * delete and the absence of any persistable grant are inherited, not restated. What differs is
 * *when*.
 *
 * A picked URI is staged when the import runs, which is moments after the picker returns and on
 * the same screen. A **shared** URI is different in a way that decides this design: the grant
 * rides on the delivered `Intent`, and the sending app may be killed, may revoke it, or may have
 * handed over a URI backed by a file it is about to delete. Between arriving and being used, a
 * share must survive the welcome carousel, a chooser the user reads at their own pace, and quite
 * possibly a rotation. Holding a URI across all that and discovering at the end that it is dead
 * would fail after the user had already answered a question about it.
 *
 * So the bytes are taken **once, immediately, while the grant is certainly live**, and every
 * screen downstream operates on the same `File` contract a camera capture produces. The URI is
 * never referenced again, and no `takePersistableUriPermission` is taken: nothing needs a standing
 * claim on a photograph in order to read it once.
 *
 * ## Ownership
 *
 * The file belongs to whoever receives it. The label path hands it to the analyzer exactly as it
 * hands on a picked photo's file; the barcode path reads and deletes it. A share that is never
 * acted on — dismissed at the chooser, or superseded — is deleted by the caller, which is why
 * [discard] exists rather than leaving cleanup to the cache eviction that may not come for days.
 */
internal object SharedImageIntake {

    /**
     * The cache-file prefix a shared image uses.
     *
     * Distinct from the picker's two prefixes deliberately. A shared file is created before the
     * user has said what it is, so at creation time it is neither a label capture nor a barcode
     * photo; naming it as either would mislabel it in the one place a person later reads files by
     * name — the debug evidence folder.
     */
    const val PREFIX: String = "justthecarbs-shared-"

    sealed interface Result {
        /** A capture-shaped file in `cacheDir`. The caller owns it. */
        data class Staged(val file: File, val bytes: Long) : Result
        data class Failed(val reason: ImportedImageStaging.Failure) : Result
    }

    /**
     * Copies [uri]'s bytes into a cache file.
     *
     * Blocking; call off the main thread. Straight through [ImportedPhotoStaging.stage] with a
     * share-specific prefix — the one generalisation that object already supports, and deliberately
     * the only one, so "one staging implementation" stays a property of the code rather than a
     * claim about two that happen to agree.
     */
    fun stage(context: Context, uri: Uri, cancelled: () -> Boolean = { false }): Result =
        when (val staged = ImportedPhotoStaging.stage(context, uri, cancelled, PREFIX)) {
            is ImportedPhotoStaging.Result.Staged -> Result.Staged(staged.file, staged.bytes)
            is ImportedPhotoStaging.Result.Failed -> Result.Failed(staged.reason)
        }

    /**
     * Deletes a staged share that will not be used.
     *
     * Called when the chooser is dismissed, when a newer share supersedes this one, and when a
     * downstream screen has finished with it. Failure is ignored and logged rather than reported:
     * a leftover file in `cacheDir` is a disk-space footnote the system will eventually reclaim,
     * and there is nothing the user could do about it.
     */
    fun discard(file: File?) {
        if (file == null) return
        runCatching { file.delete() }
            .onFailure { OcrDiagnosticsLogger.failure("Could not delete the staged share", it) }
    }
}

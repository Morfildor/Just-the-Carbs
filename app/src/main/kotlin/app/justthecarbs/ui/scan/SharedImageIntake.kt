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

/**
 * Deletes staged import files left behind by a previous process (1.0.8).
 *
 * ## The one window no handoff can close
 *
 * Ownership of a staged share passes from the Activity to a scanner in a single main-thread turn:
 * the chooser records the file, reports the share consumed, and navigates. Nothing can interleave
 * there. What *can* happen is that the process dies — killed in the background, crashed, or
 * stopped from the recents list — between the bytes being copied and the scanner disposing of
 * them. The in-flight handle is deliberately **not** `rememberSaveable` (restoring it would
 * re-import the same photograph on every rotation), so after such a death nothing in the app knows
 * the file exists.
 *
 * That is a genuine gap and it is not closeable by making the handoff more careful, because the
 * handoff is not where it happens. It is closed the way this class of orphan is normally closed:
 * on the next launch, anything still lying around is by definition from a process that is gone, so
 * it is swept.
 *
 * ## Why this is safe to run at startup
 *
 * A file matching one of these prefixes is only ever created by an import that is in progress. A
 * *live* import's file belongs to the current process, which has not reached this code — this runs
 * once, from `onCreate`, before any share or pick can have been started. So "exists at startup"
 * and "abandoned" are the same statement.
 *
 * Only this app's own import prefixes are considered, never `cacheDir` wholesale: Coil's image
 * cache, OkHttp's response cache and the debug evidence bundles all live there too, and deleting
 * those would be throwing away other components' working state to solve a problem they do not have.
 */
internal object StagedImageSweeper {

    /** The three prefixes an import can create, and nothing else in `cacheDir`. */
    private val PREFIXES = listOf(
        SharedImageIntake.PREFIX,
        ImportedPhotoStaging.LABEL_PREFIX,
        ImportedPhotoStaging.BARCODE_PREFIX,
    )

    /**
     * Deletes every orphaned staging file directly in [cacheDir], returning how many went.
     *
     * Failure is swallowed per file: a cache file that will not delete is a disk-space footnote,
     * and one stubborn file must not stop the rest being cleaned. Only the top level is scanned —
     * an evidence folder is a directory and is the recorder's to prune.
     */
    fun sweep(cacheDir: File): Int {
        val orphans = runCatching {
            cacheDir.listFiles { file: File ->
                file.isFile && PREFIXES.any { file.name.startsWith(it) }
            }
        }.getOrNull().orEmpty()

        var deleted = 0
        orphans.forEach { file ->
            if (runCatching { file.delete() }.getOrDefault(false)) deleted++
        }
        if (deleted > 0) {
            OcrDiagnosticsLogger.timing("swept $deleted abandoned import file(s) from a previous run")
        }
        return deleted
    }
}

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
 * on the next launch, anything already lying around is by definition from a process that is gone,
 * so it is swept.
 *
 * ## Why the sweep is a snapshot and not a timestamp comparison
 *
 * Two earlier shapes of this were wrong, in instructive ways.
 *
 * The first swept everything matching a prefix, on the argument that the sweep ran before any
 * import could have started. That argument rested entirely on *where* it was called from — and the
 * call site was `MainActivity.onCreate` under `savedInstanceState == null`, which is not a process
 * boundary at all. A backgrounded process that Android kills is relaunched with the Activity
 * recreated **from saved state**, so `savedInstanceState` is non-null on precisely the new process
 * whose predecessor left the orphans. The sweep skipped the case it exists for.
 *
 * Moving the call to [app.justthecarbs.JustTheCarbsApplication.onCreate] fixed which processes
 * sweep, and raised a second question: deletion runs off the main thread, so it can still be
 * working while a cold `ACTION_SEND` stages its photograph. The second shape answered that by
 * capturing the process-start instant and deleting only files reporting an older `lastModified()`.
 *
 * **That is not a sound ownership proof.** It assumes the filesystem records modification times
 * finely enough, and granularly enough, to order two events milliseconds apart. Several filesystems
 * Android runs on do not: `lastModified` may be truncated to whole seconds, so a file genuinely
 * created *after* the process started can be stamped at the start of that second — earlier than the
 * captured instant — and be deleted as an orphan while the user is looking at it. A rule whose
 * correctness depends on clock resolution is a rule that fails silently on some devices and passes
 * every test on others.
 *
 * So ownership is established **by enumeration order instead of by clock**. [snapshot] lists
 * `cacheDir` synchronously in `Application.onCreate`, before the container is built and before any
 * Activity — and therefore any pick or share — can exist, and freezes the matching files into an
 * immutable list. [sweep] deletes only members of that list. A file created afterwards was not in
 * the directory when it was read, so it is not in the snapshot, so the sweep cannot name it: the
 * race is not narrowed, it is structurally unreachable, and no timestamp is consulted at any point.
 * That also means no minimum age is applied and none is needed — an abandoned file is reclaimed on
 * the very next launch rather than an hour later.
 *
 * Only this app's own import prefixes are considered, and only regular files directly in
 * `cacheDir`, never the directory wholesale: Coil's image cache, OkHttp's response cache and the
 * debug evidence bundles all live there too, and deleting those would be throwing away other
 * components' working state to solve a problem they do not have.
 */
internal object StagedImageSweeper {

    /** The three prefixes an import can create, and nothing else in `cacheDir`. */
    private val PREFIXES = listOf(
        SharedImageIntake.PREFIX,
        ImportedPhotoStaging.LABEL_PREFIX,
        ImportedPhotoStaging.BARCODE_PREFIX,
    )

    /**
     * The staging files that existed at the instant this process began.
     *
     * An opaque, immutable list of exactly the files [sweep] may delete. It is a type rather than a
     * bare `List<File>` so the two halves cannot be accidentally decoupled: the only way to obtain
     * one is [snapshot], which is called from `Application.onCreate`, so a caller cannot hand the
     * sweep a directory listing taken at some later and therefore unsafe moment.
     */
    @JvmInline
    value class Snapshot internal constructor(internal val files: List<File>) {
        val size: Int get() = files.size
    }

    /**
     * Freezes the staging files already present in [cacheDir]. **Call synchronously, at process
     * start, before anything can stage a file.**
     *
     * This single `listFiles` call is the whole ownership proof. Everything it returns was on disk
     * before the current process could create anything, so everything it returns belongs to a
     * process that is gone. Everything staged from now on is absent from the result and therefore
     * unreachable by [sweep], whenever that eventually runs.
     *
     * Deliberately cheap: one directory listing, no `delete`, no timestamp read, no I/O beyond the
     * enumeration itself — it runs on the main thread during startup, so the work that can block is
     * left to [sweep]. A directory that cannot be read yields an empty snapshot, because failing to
     * reclaim disk space must never be the reason an app fails to open.
     */
    fun snapshot(cacheDir: File): Snapshot {
        val existing = runCatching {
            cacheDir.listFiles { file: File ->
                file.isFile && PREFIXES.any { file.name.startsWith(it) }
            }
        }.getOrNull().orEmpty()

        return Snapshot(existing.toList())
    }

    /**
     * Deletes the files in [snapshot], returning how many went. Blocking; call off the main thread.
     *
     * Every file here was named by [snapshot] before this process could stage anything, so no
     * current-process import can be among them however long the call is delayed. The prefix and
     * top-level checks were applied at snapshot time and are not re-applied: the list is the
     * authority, and re-deriving its membership would be a second place for the rule to live.
     *
     * A file that has vanished in between (the system trimmed the cache, the user cleared it) is
     * simply not deleted and does not count. Failure is swallowed per file: a cache file that will
     * not delete is a disk-space footnote, and one stubborn file must not stop the rest being
     * cleaned.
     */
    fun sweep(snapshot: Snapshot): Int {
        var deleted = 0
        snapshot.files.forEach { file ->
            if (runCatching { file.delete() }.getOrDefault(false)) deleted++
        }
        if (deleted > 0) {
            OcrDiagnosticsLogger.timing("swept $deleted abandoned import file(s) from a previous run")
        }
        return deleted
    }
}

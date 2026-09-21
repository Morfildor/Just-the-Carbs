package app.justthecarbs.ui.scan

import android.content.Context
import android.net.Uri
import app.justthecarbs.ocr.ImportedImageStaging
import java.io.File

/**
 * Where an imported image's bytes are, and therefore who owns them (1.0.8).
 *
 * ## The defect this type exists to make unrepresentable
 *
 * Both import paths — the photo picker and `ACTION_SEND` — end at the same recognition pipeline,
 * and that convergence is deliberate and load-bearing. What they do **not** share is the state the
 * image arrives in:
 *
 * - A **picked** URI carries a temporary read grant and is somebody else's bytes. It must be copied
 *   into this app's cache before anything can be done with it.
 * - A **shared** URI is copied the moment it arrives ([SharedImageIntake]), because the grant rides
 *   on the delivered `Intent` and may die while the user reads the chooser. By the time a scanner
 *   sees it, the app already owns a cache file.
 *
 * The first implementation flattened both into `Uri?` and handed the scanners a `file://` URI for
 * the shared case. The scanners could not tell the two apart, so they did the only safe thing they
 * knew — stage it — which **copied an already-owned cache file into a second cache file**. The
 * consequences were all real:
 *
 * 1. the original `justthecarbs-shared-*` was abandoned, because the Activity had already reported
 *    the share consumed with `deleteFile = false` on the (correct) understanding that a downstream
 *    screen had taken ownership;
 * 2. a full extra read-and-write of a photograph that can be tens of megabytes, on the one path
 *    where the user is already waiting;
 * 3. peak cache use of roughly twice the image size, for no gain.
 *
 * Making the two states different *types* is what stops that recurring: a caller holding a
 * [Staged] cannot accidentally stage it, because there is no URI in it to stage.
 *
 * ## Ownership, stated once
 *
 * **Whoever holds an [ImportedImageSource] owns whatever file it resolves to**, and is responsible
 * for exactly one disposal of it. That is true of both cases, which is the point:
 *
 * - [Picked] owns nothing yet. Staging it produces a file the stager owns.
 * - [Staged] **already** owns its file. The screen that receives it must dispose of it on failure,
 *   on supersession and on abandonment, and hand it on — once — where the existing pipeline takes
 *   ownership (the label analyzer's evidence path) or delete it after a single read (barcode).
 *
 * There is no third case and no "maybe owned": every path through the scanners ends in exactly one
 * of *deleted here*, *renamed into evidence*, or *handed to an analyzer that deletes it*.
 *
 * Public only because it appears in the signature of the public `ScannerScreen` and
 * `LabelScannerScreen` composables; [ImportedImageResolver], which does the work, stays internal.
 */
sealed interface ImportedImageSource {

    /**
     * A stable identity for this delivery, used for the once-per-delivery replay guards.
     *
     * The URI string for a [Picked], the absolute path for a [Staged]. Both are compared by value
     * and both are distinct per delivery — a second share stages to its own cache file, so it
     * presents a different path and is correctly treated as new.
     */
    val token: String

    /**
     * Somebody else's bytes, behind a temporary read grant. Must be staged before use.
     *
     * This is the photo picker's case, and it is unchanged from before this type existed.
     */
    data class Picked(val uri: Uri) : ImportedImageSource {
        override val token: String get() = uri.toString()
    }

    /**
     * Bytes this app has already copied into its own cache. **Not** to be staged again.
     *
     * This is the `ACTION_SEND` case. The copy happened at arrival, while the sender's grant was
     * certainly live, which is the whole reason a share is handled differently from a pick.
     */
    data class Staged(val file: File) : ImportedImageSource {
        override val token: String get() = file.absolutePath
    }
}

/**
 * Turns an [ImportedImageSource] into a file this app owns, staging only when there is something
 * to stage.
 *
 * The one place the "a share is already ours" rule is applied, so neither scanner can forget it and
 * the two cannot drift. A [ImportedImageSource.Picked] goes through [ImportedPhotoStaging.stage]
 * exactly as before — same ceiling, same cancellation poll, same partial-file delete, same absence
 * of a persistable grant. A [ImportedImageSource.Staged] is returned as it is, having been copied
 * once already at arrival.
 *
 * Blocking; call off the main thread, because the picked case is file I/O measured in tens of
 * megabytes. The shared case does no I/O at all, which is the saving.
 *
 * [prefix] applies to the picked case only — a staged file already has the share prefix it was
 * created with, and renaming it here would buy nothing and risk breaking the caller's own handle.
 */
internal object ImportedImageResolver {

    sealed interface Result {
        /** A capture-shaped JPEG in `cacheDir`, owned by the caller. */
        data class Ready(val file: File) : Result
        data class Failed(val reason: ImportedImageStaging.Failure) : Result
    }

    /**
     * [context] is a **lambda**, not a value, and that is deliberate rather than stylistic: it makes
     * "a share touches no Android at all" a property the compiler enforces and a plain-JVM test can
     * observe. The staged branch never invokes it. A future edit that reached for a `Context` on
     * that branch would be an edit that had started doing I/O on a file this app already owns —
     * exactly the defect this object exists to have fixed — and the ownership test fails on it.
     */
    fun resolve(
        context: () -> Context,
        source: ImportedImageSource,
        cancelled: () -> Boolean,
        prefix: String,
    ): Result = when (source) {
        // Already ours. Staging it again would copy a cache file into a second cache file and
        // orphan the first — see [ImportedImageSource]'s note; this branch is the fix.
        is ImportedImageSource.Staged -> Result.Ready(source.file)

        is ImportedImageSource.Picked ->
            when (val staged = ImportedPhotoStaging.stage(context(), source.uri, cancelled, prefix)) {
                is ImportedPhotoStaging.Result.Staged -> Result.Ready(staged.file)
                is ImportedPhotoStaging.Result.Failed -> Result.Failed(staged.reason)
            }
    }
}

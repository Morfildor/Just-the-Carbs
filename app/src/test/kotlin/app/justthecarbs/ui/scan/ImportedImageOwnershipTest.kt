package app.justthecarbs.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * One owner, one file, one disposal — for both ways an image enters (1.0.8).
 *
 * ## The defect
 *
 * A shared image is copied into this app's cache the moment it arrives, because the sender's URI
 * grant rides on the delivered `Intent` and may be dead by the time the user has answered the
 * chooser. That part was right. What was wrong is what happened next: the staged file was handed
 * downstream **as a `file://` URI**, and the scanners — which could not tell it from a picked
 * photo — staged it a second time.
 *
 * ```
 *   share arrives  ->  justthecarbs-shared-123.jpg     (copied at arrival, correct)
 *   chooser        ->  handed on as file:///.../justthecarbs-shared-123.jpg
 *   scanner        ->  justthecarbs-barcode-456.jpg    (a COPY of a file we already owned)
 *   scanner        ->  deletes justthecarbs-barcode-456.jpg
 *                      justthecarbs-shared-123.jpg is never deleted by anyone
 * ```
 *
 * Three costs, all real: an abandoned cache file per successful share, a full extra read-and-write
 * of a photograph that may be tens of megabytes on the one path where the user is already waiting,
 * and roughly double the peak staging space.
 *
 * ## What these tests pin
 *
 * That [ImportedImageResolver] performs **no copy** for an already-owned file, returns the very same
 * `File` it was given, and still stages a picked URI. The tests exercise the resolver rather than a
 * composable because that is where the decision lives — the `Context` is only ever touched on the
 * picked branch, which is exactly the property under test, so the shared branch needs no Android at
 * all. The screens' own end-to-end cleanup is covered instrumented, where a real `cacheDir` exists.
 */
class ImportedImageOwnershipTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun sharedFile(name: String = "justthecarbs-shared-1.jpg", body: String = "jpeg-bytes"): File =
        temp.newFile(name).apply { writeText(body) }

    // ---- the fix -------------------------------------------------------------------------------

    @Test
    fun `an already staged share is not copied again`() {
        val original = sharedFile()
        val before = temp.root.listFiles()!!.size

        val result = ImportedImageResolver.resolve(
            // A `Context` supplier that throws, which is itself the assertion: the staged branch
            // must never invoke it. Reaching for a `Context` here would mean this branch had begun
            // doing I/O on a file the app already owns — the double-stage — and the test fails
            // rather than the cost going unnoticed in production.
            context = { error("a share must never be staged again") },
            source = ImportedImageSource.Staged(original),
            cancelled = { false },
            prefix = ImportedPhotoStaging.BARCODE_PREFIX,
        )

        val ready = result as ImportedImageResolver.Result.Ready
        assertSame("the resolver must hand back the very same file, not a copy", original, ready.file)
        assertEquals("no second file may appear in the cache", before, temp.root.listFiles()!!.size)
    }

    @Test
    fun `a staged share keeps its own contents and its own name`() {
        // The share prefix is deliberately preserved rather than renamed to the label or barcode
        // one: renaming buys nothing and would invalidate the caller's existing handle.
        val original = sharedFile(body = "the original photograph")

        val ready = ImportedImageResolver.resolve(
            context = { error("a share must never be staged again") },
            source = ImportedImageSource.Staged(original),
            cancelled = { false },
            prefix = ImportedPhotoStaging.LABEL_PREFIX,
        ) as ImportedImageResolver.Result.Ready

        assertEquals("the original photograph", ready.file.readText())
        assertTrue(ready.file.name.startsWith(SharedImageIntake.PREFIX))
    }

    @Test
    fun `the cancellation poll is never consulted for a file we already own`() {
        // There is nothing to cancel: no bytes move. A resolver that asked would be one that was
        // about to do I/O.
        var asked = false
        ImportedImageResolver.resolve(
            context = { error("a share must never be staged again") },
            source = ImportedImageSource.Staged(sharedFile()),
            cancelled = { asked = true; false },
            prefix = ImportedPhotoStaging.BARCODE_PREFIX,
        )
        assertFalse("resolving an owned file must do no work worth cancelling", asked)
    }

    // ---- identity, which the replay guards compare by value --------------------------------------

    @Test
    fun `a staged source is identified by its path`() {
        val file = sharedFile()
        assertEquals(file.absolutePath, ImportedImageSource.Staged(file).token)
    }

    @Test
    fun `two different shares present two different tokens`() {
        // The once-per-share guards (`deliveredShare`, `importedShare`) compare by value, so a
        // genuinely new share must look new. Each stages to its own cache file, so it does.
        val first = ImportedImageSource.Staged(sharedFile("justthecarbs-shared-1.jpg"))
        val second = ImportedImageSource.Staged(sharedFile("justthecarbs-shared-2.jpg"))
        assertNotEquals(first.token, second.token)
    }

    @Test
    fun `the same share presents a stable token across reads`() {
        // A rotation re-reads the same value; it must compare equal, or the guard would let the
        // same photograph import twice.
        val file = sharedFile()
        assertEquals(
            ImportedImageSource.Staged(file).token,
            ImportedImageSource.Staged(File(file.absolutePath)).token,
        )
    }

    // ---- repeated use, which is where a per-share leak would accumulate ---------------------------

    @Test
    fun `ten shares resolved and disposed leave nothing behind`() {
        // The leak was one abandoned file per successful share, so it is invisible in a single run
        // and obvious in ten. Each iteration is the production sequence in miniature: stage at
        // arrival, resolve at the scanner, dispose once after the read.
        repeat(10) { i ->
            val staged = sharedFile("justthecarbs-shared-$i.jpg")
            val ready = ImportedImageResolver.resolve(
                context = { error("a share must never be staged again") },
                source = ImportedImageSource.Staged(staged),
                cancelled = { false },
                prefix = ImportedPhotoStaging.BARCODE_PREFIX,
            ) as ImportedImageResolver.Result.Ready
            // The single disposal the barcode path owes, in its `finally`.
            ready.file.delete()
        }

        val leftovers = temp.root.listFiles()!!.filter { it.name.startsWith(SharedImageIntake.PREFIX) }
        assertEquals("no abandoned shared staging files: $leftovers", emptyList<File>(), leftovers)
    }

    @Test
    fun `an abandoned share is disposed by the same single delete`() {
        // Supersession and departure both take this route in the screens: resolve, then find the
        // work is no longer current, then delete. The file being the share's own original (rather
        // than a copy of it) is what makes that one delete sufficient.
        val staged = sharedFile()
        val ready = ImportedImageResolver.resolve(
            context = { error("a share must never be staged again") },
            source = ImportedImageSource.Staged(staged),
            cancelled = { false },
            prefix = ImportedPhotoStaging.BARCODE_PREFIX,
        ) as ImportedImageResolver.Result.Ready

        ready.file.delete()

        assertFalse("the original share file is what gets deleted", staged.exists())
    }
}

/**
 * The startup sweep, which closes the one orphan window a handoff cannot (1.0.8).
 *
 * Ownership moves from the Activity to a scanner in a single main-thread turn, so there is no
 * interleaving to guard against. What remains is process death between the copy and the disposal:
 * the in-flight handle is deliberately not saved across recreation, so nothing alive afterwards
 * knows the file is there. On the next genuine launch it is swept.
 */
class StagedImageSweeperTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `an abandoned share from a dead process is swept`() {
        val orphan = temp.newFile("${SharedImageIntake.PREFIX}1.jpg")

        assertEquals(1, StagedImageSweeper.sweep(temp.root))
        assertFalse(orphan.exists())
    }

    @Test
    fun `an abandoned pick of either kind is swept too`() {
        // A picked photo can be orphaned the same way — the screen is gone before the `finally`
        // runs — so the sweep covers all three prefixes rather than only the share's.
        temp.newFile("${ImportedPhotoStaging.LABEL_PREFIX}1.jpg")
        temp.newFile("${ImportedPhotoStaging.BARCODE_PREFIX}1.jpg")

        assertEquals(2, StagedImageSweeper.sweep(temp.root))
        assertEquals(emptyList<File>(), temp.root.listFiles()!!.toList())
    }

    @Test
    fun `nothing else in the cache is touched`() {
        // `cacheDir` is shared. Coil's image cache, OkHttp's response cache and the debug evidence
        // bundles all live there, and sweeping wholesale would throw away other components'
        // working state to solve a problem they do not have.
        val coil = temp.newFile("image_cache_0001.0")
        val okhttp = temp.newFile("journal")
        val evidence = temp.newFolder("scan-evidence-20260921-120000")

        assertEquals(0, StagedImageSweeper.sweep(temp.root))
        assertTrue(coil.exists())
        assertTrue(okhttp.exists())
        assertTrue(evidence.exists())
    }

    @Test
    fun `a directory sharing an import prefix is left alone`() {
        // Only files are swept: an evidence folder is a directory and is the recorder's to prune.
        val folder = temp.newFolder("${SharedImageIntake.PREFIX}folder")

        assertEquals(0, StagedImageSweeper.sweep(temp.root))
        assertTrue(folder.exists())
    }

    @Test
    fun `an empty cache sweeps nothing and does not fail`() {
        assertEquals(0, StagedImageSweeper.sweep(temp.root))
    }

    @Test
    fun `a missing cache directory is survivable`() {
        // `listFiles` returns null for a path that is not a directory. The sweep is best-effort
        // housekeeping and must never be the reason a launch fails.
        assertEquals(0, StagedImageSweeper.sweep(File(temp.root, "does-not-exist")))
    }

    @Test
    fun `ten abandoned shares are all swept`() {
        repeat(10) { temp.newFile("${SharedImageIntake.PREFIX}$it.jpg") }

        assertEquals(10, StagedImageSweeper.sweep(temp.root))
        assertEquals(emptyList<File>(), temp.root.listFiles()!!.toList())
    }
}

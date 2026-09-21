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
 * The process-start sweep, which closes the one orphan window a handoff cannot (1.0.8).
 *
 * Ownership moves from the Activity to a scanner in a single main-thread turn, so there is no
 * interleaving to guard against. What remains is process death between the copy and the disposal:
 * the in-flight handle is deliberately not saved across recreation, so nothing alive afterwards
 * knows the file is there. On the next process start it is swept.
 *
 * Every case here is written against the **snapshot**, because that is what makes the sweep safe to
 * run asynchronously. `snapshot` lists the directory synchronously at process start; `sweep`
 * deletes only what that listing named. A file created afterwards is absent from the list and
 * therefore unreachable, whatever its timestamp says and however long the deletion is delayed. The
 * sibling [StagedImageSweepLifetimeTest] pins where the snapshot is taken.
 */
class StagedImageSweeperTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A staging file, optionally stamped [modified] to prove timestamps play no part. */
    private fun stagedFile(name: String, modified: Long? = null): File =
        temp.newFile(name).also {
            if (modified != null) assertTrue("could not stamp " + name, it.setLastModified(modified))
        }

    @Test
    fun `an abandoned share from a dead process is swept`() {
        val orphan = stagedFile("${SharedImageIntake.PREFIX}1.jpg")

        assertEquals(1, StagedImageSweeper.sweep(StagedImageSweeper.snapshot(temp.root)))
        assertFalse(orphan.exists())
    }

    @Test
    fun `an abandoned label pick from a dead process is swept`() {
        // A picked photo can be orphaned the same way — the screen is gone before the `finally`
        // runs — so the sweep covers all three prefixes rather than only the share's.
        val orphan = stagedFile("${ImportedPhotoStaging.LABEL_PREFIX}1.jpg")

        assertEquals(1, StagedImageSweeper.sweep(StagedImageSweeper.snapshot(temp.root)))
        assertFalse(orphan.exists())
    }

    @Test
    fun `an abandoned barcode pick from a dead process is swept`() {
        val orphan = stagedFile("${ImportedPhotoStaging.BARCODE_PREFIX}1.jpg")

        assertEquals(1, StagedImageSweeper.sweep(StagedImageSweeper.snapshot(temp.root)))
        assertFalse(orphan.exists())
    }

    @Test
    fun `nothing else in the cache is touched`() {
        // `cacheDir` is shared. Coil's image cache, OkHttp's response cache and the debug evidence
        // bundles all live there, and sweeping wholesale would throw away other components'
        // working state to solve a problem they do not have. Each of these predates the snapshot,
        // so only the prefix check can be what saves them.
        val coil = stagedFile("image_cache_0001.0")
        val okhttp = stagedFile("journal")
        val evidence = temp.newFolder("scan-evidence-20260921-120000")

        assertEquals(0, StagedImageSweeper.sweep(StagedImageSweeper.snapshot(temp.root)))
        assertTrue(coil.exists())
        assertTrue(okhttp.exists())
        assertTrue(evidence.exists())
    }

    @Test
    fun `a directory sharing an import prefix is left alone`() {
        // Only files are swept: an evidence folder is a directory and is the recorder's to prune.
        val folder = temp.newFolder("${SharedImageIntake.PREFIX}folder")

        assertEquals(0, StagedImageSweeper.sweep(StagedImageSweeper.snapshot(temp.root)))
        assertTrue(folder.exists())
    }

    @Test
    fun `a staging file nested below the cache root is left alone`() {
        // Only the top level is enumerated. A prefixed file inside a subdirectory belongs to
        // whoever made that directory, and recursing would put the sweep inside other components'
        // cache structures.
        val nested = temp.newFolder("subdir")
        val file = File(nested, "${SharedImageIntake.PREFIX}nested.jpg")
        assertTrue(file.createNewFile())

        assertEquals(0, StagedImageSweeper.sweep(StagedImageSweeper.snapshot(temp.root)))
        assertTrue("a file below the cache root is not this sweep's to delete", file.exists())
    }

    @Test
    fun `a staging file created after the snapshot is never swept`() {
        // The asynchronous-cleanup race, as a test. The sweep runs on a background thread, so a
        // cold ACTION_SEND can be staging its photograph long after the snapshot was taken. That
        // file was not in the directory when it was listed, so the sweep has no name for it — which
        // is what lets the deletion stay off the main thread at all.
        val snapshot = StagedImageSweeper.snapshot(temp.root)

        val live = stagedFile("${SharedImageIntake.PREFIX}live.jpg")

        assertEquals(0, StagedImageSweeper.sweep(snapshot))
        assertTrue("a live import's file was deleted by the startup sweep", live.exists())
    }

    @Test
    fun `a file created after the snapshot survives even when stamped before process start`() {
        // The defect the snapshot exists to remove. A timestamp rule would compare this file's
        // `lastModified` against the process-start instant and conclude it was abandoned — which is
        // exactly what a coarse filesystem clock can produce for a genuinely new file, by
        // truncating its stamp back to the start of the current second. Enumeration order does not
        // care: the file was not there when the directory was read.
        val snapshot = StagedImageSweeper.snapshot(temp.root)

        val live = stagedFile("${SharedImageIntake.PREFIX}live.jpg", modified = 1L)

        assertEquals(0, StagedImageSweeper.sweep(snapshot))
        assertTrue(
            "a current-process file must survive whatever its recorded timestamp says",
            live.exists(),
        )
    }

    @Test
    fun `coarse timestamps cannot affect ownership either way`() {
        // Both files carry the *same* modification time, which is what a whole-second filesystem
        // clock produces for two writes in the same second. One was there at the snapshot and one
        // was not, and that — not the indistinguishable stamps — is what decides each one's fate.
        val sameSecond = 1_600_000_000_000L
        val orphan = stagedFile("${SharedImageIntake.PREFIX}orphan.jpg", modified = sameSecond)

        val snapshot = StagedImageSweeper.snapshot(temp.root)

        val live = stagedFile("${SharedImageIntake.PREFIX}live.jpg", modified = sameSecond)

        assertEquals(1, StagedImageSweeper.sweep(snapshot))
        assertFalse("the pre-existing file is the abandoned one", orphan.exists())
        assertTrue("the file staged after the snapshot is the live one", live.exists())
    }

    @Test
    fun `a deletion running very late cannot touch anything staged since`() {
        // The snapshot is a value, so delaying the sweep arbitrarily cannot widen what it may
        // delete. Several imports come and go between the two calls; none is reachable.
        val orphan = stagedFile("${SharedImageIntake.PREFIX}orphan.jpg")

        val snapshot = StagedImageSweeper.snapshot(temp.root)

        val later = (1..5).map { stagedFile("${ImportedPhotoStaging.LABEL_PREFIX}$it.jpg") }

        assertEquals(1, StagedImageSweeper.sweep(snapshot))
        assertFalse(orphan.exists())
        assertTrue("no file staged after the snapshot may be swept", later.all { it.exists() })
    }

    @Test
    fun `an orphan and a live import in the same directory are told apart`() {
        // Both prefixes, both present at deletion time, and only the one the snapshot named goes.
        val orphan = stagedFile("${SharedImageIntake.PREFIX}old.jpg")

        val snapshot = StagedImageSweeper.snapshot(temp.root)

        val live = stagedFile("${ImportedPhotoStaging.LABEL_PREFIX}new.jpg")

        assertEquals(1, StagedImageSweeper.sweep(snapshot))
        assertFalse(orphan.exists())
        assertTrue(live.exists())
    }

    @Test
    fun `a snapshot names exactly the staging files present when it was taken`() {
        // The snapshot is the whole ownership proof, so its membership is asserted directly rather
        // than only through what a later deletion happens to remove.
        stagedFile("${SharedImageIntake.PREFIX}a.jpg")
        stagedFile("${ImportedPhotoStaging.BARCODE_PREFIX}b.jpg")
        stagedFile("unrelated.txt")
        temp.newFolder("${SharedImageIntake.PREFIX}dir")

        val snapshot = StagedImageSweeper.snapshot(temp.root)

        stagedFile("${SharedImageIntake.PREFIX}c.jpg")

        assertEquals("only the two prefixed files present at snapshot time", 2, snapshot.size)
    }

    @Test
    fun `a file deleted between the snapshot and the sweep is simply not counted`() {
        // The system can trim the cache, or the user can clear it, between the two calls. A file
        // that has already gone is not an error and is not a deletion.
        val orphan = stagedFile("${SharedImageIntake.PREFIX}vanishes.jpg")
        val survivor = stagedFile("${SharedImageIntake.PREFIX}remains.jpg")

        val snapshot = StagedImageSweeper.snapshot(temp.root)
        assertTrue(orphan.delete())

        assertEquals(1, StagedImageSweeper.sweep(snapshot))
        assertFalse(survivor.exists())
    }

    @Test
    fun `an empty cache sweeps nothing and does not fail`() {
        assertEquals(0, StagedImageSweeper.sweep(StagedImageSweeper.snapshot(temp.root)))
    }

    @Test
    fun `a missing cache directory is survivable`() {
        // `listFiles` returns null for a path that is not a directory. The sweep is best-effort
        // housekeeping and must never be the reason a launch fails.
        val snapshot = StagedImageSweeper.snapshot(File(temp.root, "does-not-exist"))

        assertEquals(0, snapshot.size)
        assertEquals(0, StagedImageSweeper.sweep(snapshot))
    }

    @Test
    fun `ten abandoned shares are all swept`() {
        repeat(10) { stagedFile("${SharedImageIntake.PREFIX}$it.jpg") }

        assertEquals(10, StagedImageSweeper.sweep(StagedImageSweeper.snapshot(temp.root)))
        assertEquals(emptyList<File>(), temp.root.listFiles()!!.toList())
    }

    @Test
    fun `a relaunch immediately after the previous process reclaims its orphan`() {
        // No arbitrary minimum age, and none is needed: an abandoned file is reclaimable on the
        // very next process start rather than an hour later, so a crash-and-immediate-relaunch
        // leaves nothing behind. Modelled as the next process reading the directory the dead one
        // left, with no time allowed to pass.
        val orphan = stagedFile("${SharedImageIntake.PREFIX}crashed.jpg")

        assertEquals(1, StagedImageSweeper.sweep(StagedImageSweeper.snapshot(temp.root)))
        assertFalse(orphan.exists())
    }
}

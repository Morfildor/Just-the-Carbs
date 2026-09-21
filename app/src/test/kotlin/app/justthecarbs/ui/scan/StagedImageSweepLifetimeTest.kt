package app.justthecarbs.ui.scan

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The staging sweep belongs to the process, not to an Activity.** (1.0.8)
 *
 * ## The defect this pins
 *
 * The sweep used to be called from `MainActivity.onCreate`, guarded by `savedInstanceState ==
 * null` and commented as running "once per process start". That reading of the flag is wrong, and
 * wrong in the direction that matters:
 *
 * - **A new process can have a non-null `savedInstanceState`.** When Android kills a backgrounded
 *   process it saves the Activity's state, and on relaunch recreates the Activity *from* that
 *   state in a brand-new process. So the launch that certainly follows a dead process — the one
 *   holding that process's orphaned staging files — took the `else` branch and swept nothing. The
 *   sweep missed precisely the case it exists for.
 * - **The case it did guard against does not arise here.** A rotation recreates the Activity
 *   within the same process; `Application.onCreate` does not run again, so there is no second
 *   sweep to suppress in the first place.
 *
 * ## And why the snapshot is taken where it is
 *
 * Moving the call fixed *which processes* sweep and left the asynchronous deletion free to race a
 * cold share staging its photograph. The answer is not a timestamp comparison — that would rest on
 * the filesystem recording modification times finely enough to order two events milliseconds
 * apart, which several filesystems Android runs on do not do — but a directory listing taken
 * synchronously in `onCreate`, before any Activity exists. A file staged afterwards is absent from
 * that list and therefore unreachable. **The ordering is the entire guarantee**, so it is asserted
 * here, structurally, alongside the call site.
 *
 * No behavioural test can establish the absence of a call site or the order of two statements, and
 * the sibling [StagedImageSweeperTest] already covers what the sweep does with a snapshot once it
 * has one. These tests read the production sources, following
 * [app.justthecarbs.ui.scan.ImportedBarcodePipelineConvergenceTest]'s reasoning.
 */
class StagedImageSweepLifetimeTest {

    private val application =
        sourceFile("app/src/main/kotlin/app/justthecarbs/JustTheCarbsApplication.kt")
    private val activity = sourceFile("app/src/main/kotlin/app/justthecarbs/MainActivity.kt")
    private val sweeper =
        sourceFile("app/src/main/kotlin/app/justthecarbs/ui/scan/SharedImageIntake.kt")

    /**
     * The source with comments stripped.
     *
     * Every assertion here is about **code**. A KDoc that merely discusses `savedInstanceState`
     * must not read as a call site — and this file's own prose above discusses it at length, which
     * is exactly the trap.
     */
    private fun codeOf(source: String): String = source.lineSequence()
        .map { it.substringBefore("//") }
        .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }
        .joinToString("\n")

    private fun sourceFile(path: String): String {
        val candidates = listOf(File(path), File("../" + path), File(path.removePrefix("app/")))
        val found = candidates.firstOrNull { it.isFile }
        requireNotNull(found) { "could not locate " + path + " from " + File(".").absolutePath }
        return found.readText()
    }

    @Test
    fun `the snapshot is taken in the Application, once`() {
        val snapshots = Regex("""StagedImageSweeper\.snapshot\(""").findAll(codeOf(application)).count()

        assertEquals(
            "JustTheCarbsApplication must take exactly one staging snapshot. " +
                "Application.onCreate is the app's only true new-process boundary: it runs for " +
                "every fresh process and never again within one, whatever happens to the Activity.",
            1,
            snapshots,
        )
    }

    @Test
    fun `the sweep is not invoked from the Activity at all`() {
        // The regression guard. Moving this back under MainActivity — with or without the
        // savedInstanceState gate — fails here by name.
        val activityCode = codeOf(activity)

        assertTrue(
            "MainActivity must not call StagedImageSweeper. Restoring it there reintroduces " +
                "the defect: a process killed in the background is relaunched with the Activity " +
                "recreated from saved state, so a savedInstanceState == null gate skips the sweep " +
                "on exactly the launch that inherits the dead process's orphans.",
            !activityCode.contains("StagedImageSweeper"),
        )
    }

    @Test
    fun `the Activity does not import the sweeper`() {
        // An unused import is what a half-finished revert looks like, and it is the cheapest
        // possible early warning that someone is on the way back to the old call site.
        assertTrue(
            "MainActivity must not import StagedImageSweeper.",
            !activity.contains("import app.justthecarbs.ui.scan.StagedImageSweeper"),
        )
    }

    @Test
    fun `the snapshot is taken before the sweep is scheduled`() {
        // Ordering is the entire guarantee that asynchronous cleanup cannot race a live import:
        // the directory must be enumerated in onCreate, before anything can stage a file, and the
        // resulting list handed to the sweep. Enumerating inside the background thread instead
        // would list a directory that current-process imports may already be in.
        val code = codeOf(application)

        val snapshotAt = code.indexOf("val abandoned = StagedImageSweeper.snapshot(cacheDir)")
        val sweepAt = code.indexOf("sweepAbandonedStagingFiles(")

        assertTrue("onCreate must enumerate the staging files into a local", snapshotAt >= 0)
        assertTrue("onCreate must schedule the sweep", sweepAt >= 0)
        assertTrue(
            "the snapshot must be taken BEFORE the sweep is scheduled, or a file staged in " +
                "between could appear in a listing that had not yet been read.",
            snapshotAt < sweepAt,
        )
    }

    @Test
    fun `the snapshot is taken before anything else can run`() {
        // Specifically before the container: building it touches the database, the HTTP client and
        // DataStore, any of which may run code, and the snapshot's claim is that NOTHING has had an
        // opportunity to stage a file yet. It also precedes the first Activity by construction,
        // since Application.onCreate returns before one is created.
        val onCreate = codeOf(application)
            .substringAfter("override fun onCreate() {")
            .substringBefore("private fun")

        val snapshotAt = onCreate.indexOf("StagedImageSweeper.snapshot(")
        val containerAt = onCreate.indexOf("container = AppContainer(")

        assertTrue("onCreate must take the snapshot", snapshotAt >= 0)
        assertTrue("onCreate must build the container", containerAt >= 0)
        assertTrue(
            "the snapshot must be taken before the container is constructed, so that no app " +
                "component has had a chance to run before the directory is read.",
            snapshotAt < containerAt,
        )
    }

    @Test
    fun `the background worker is handed a snapshot, never a directory`() {
        // Structural rather than conventional: sweep takes the frozen list and has no way to
        // enumerate anything, so a caller cannot accidentally give it a live directory to search.
        // This is the negative control's target — reverting to enumeration inside the thread fails
        // here as well as in the live-file race test.
        val sweeperCode = codeOf(sweeper)

        assertTrue(
            "sweep must take the frozen snapshot as its only parameter.",
            sweeperCode.contains("fun sweep(snapshot: Snapshot): Int"),
        )
        assertTrue(
            "sweep must not enumerate a directory itself — the snapshot is the authority on what " +
                "may be deleted, and re-listing would readmit files staged since it was taken.",
            !sweeperCode.substringAfter("fun sweep(snapshot: Snapshot)").contains("listFiles"),
        )
        assertTrue(
            "the Application must hand the snapshot to the worker, not the cache directory.",
            codeOf(application).contains("StagedImageSweeper.sweep(snapshot)"),
        )
    }

    @Test
    fun `ownership is decided by enumeration, never by a timestamp`() {
        // The rule this replaced. A modification time cannot prove ownership: `lastModified` may be
        // truncated to whole seconds, so a file created after the process started can be stamped
        // before it and be deleted as an orphan while the user is importing it.
        val sweeperCode = codeOf(sweeper)

        assertTrue(
            "the sweeper must not consult lastModified — file timestamps have insufficient " +
                "resolution to establish which process created a file.",
            !sweeperCode.contains("lastModified"),
        )
        assertTrue(
            "the sweeper must not read the clock: there is no instant to compare against.",
            !sweeperCode.contains("currentTimeMillis"),
        )
    }

    @Test
    fun `no minimum age is applied`() {
        // An orphan must be reclaimable on the very next process start rather than after an
        // arbitrary delay, so nothing here may subtract a grace period from anything.
        val sweeperCode = codeOf(sweeper)

        assertTrue(
            "the sweeper must apply no minimum-age delay.",
            !sweeperCode.contains("MIN_AGE") &&
                !sweeperCode.contains("MAX_AGE") &&
                !sweeperCode.contains("HOUR") &&
                !sweeperCode.contains("TimeUnit"),
        )
    }

    @Test
    fun `only the app's own staging prefixes are eligible`() {
        // cacheDir is shared with Coil, OkHttp and the debug evidence bundles, and the snapshot is
        // now the single place the scope is decided — so the prefix and top-level checks must be
        // there, applied while the directory is read.
        val snapshotFn = codeOf(sweeper)
            .substringAfter("fun snapshot(cacheDir: File)")
            .substringBefore("fun sweep(")

        assertTrue(
            "the snapshot must restrict itself to the three import prefixes.",
            snapshotFn.contains("PREFIXES.any { file.name.startsWith(it) }"),
        )
        assertTrue(
            "the snapshot must take regular files only, never directories.",
            snapshotFn.contains("file.isFile"),
        )
    }

    @Test
    fun `cleanup failure cannot stop application initialization`() {
        // The sweep is disk housekeeping. It must never be the reason the app fails to open, so
        // both the scheduling and the work itself are wrapped, and neither is awaited.
        val code = codeOf(application)
        val scheduler = code.substringAfter("private fun sweepAbandonedStagingFiles")

        assertTrue(
            "scheduling the sweep must be wrapped, so a thread that will not start is logged " +
                "rather than thrown out of Application.onCreate.",
            scheduler.contains("runCatching {"),
        )
        assertTrue(
            "the sweep itself must be wrapped, so a failure on the background thread cannot " +
                "become an uncaught exception.",
            scheduler.contains("runCatching { StagedImageSweeper.sweep("),
        )
        assertTrue(
            "the sweep must not be awaited during startup.",
            !scheduler.contains("join()") && !scheduler.contains("runBlocking"),
        )
        assertTrue(
            "reading the cache directory must be tolerant too — an unreadable directory yields " +
                "an empty snapshot rather than throwing out of onCreate.",
            codeOf(sweeper).substringAfter("fun snapshot(").contains("runCatching {"),
        )
    }

    @Test
    fun `the launch counter keeps its Activity gate and gains no sweep`() {
        // The two used to share one gate, which is how the sweep inherited a flag that was only
        // ever right for the counter. savedInstanceState == null genuinely does distinguish a
        // fresh Activity from a recreated one, so the counter is correct where it is; what must
        // not happen is anything process-scoped joining it there.
        val gate = codeOf(activity).substringAfter("if (savedInstanceState == null) {")
            .substringBefore("}")

        assertTrue(
            "the launch counter must still be recorded under the Activity gate.",
            gate.contains("recordLaunch()"),
        )
        assertTrue(
            "nothing process-scoped may live under savedInstanceState == null.",
            !gate.contains("Sweeper") && !gate.contains("cacheDir"),
        )
    }
}

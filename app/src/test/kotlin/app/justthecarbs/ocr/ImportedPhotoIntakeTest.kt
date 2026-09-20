package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three intake rules, pinned where a test can reach them.
 *
 * These are the rules that decide whether a stale photograph's carbohydrate figure can land on
 * screen. They live in [ImportedPhotoIntake] rather than inside the scanner composable precisely so
 * this file can exist — see that class's KDoc for the two occasions this repo shipped a safety rule
 * somewhere no test could reach it.
 */
class ImportedPhotoIntakeTest {

    private fun counter(start: Long = 0L): Pair<() -> Long, () -> Long> {
        var value = start
        return ({ ++value }) to ({ value })
    }

    @Test
    fun `a fresh selection is imported under a new work generation`() {
        val (begin, read) = counter()

        val decision = ImportedPhotoIntake.decide(
            hasSelection = true,
            resultToken = "content://media/picked/1",
            lastConsumedToken = null,
            beginWork = begin,
        )

        assertEquals(ImportedPhotoIntake.Decision.Import(1L), decision)
        assertEquals("the generation must actually have been taken", 1L, read())
    }

    @Test
    fun `a cancelled picker changes nothing and spends no work generation`() {
        val (begin, read) = counter(start = 7L)

        val decision = ImportedPhotoIntake.decide(
            hasSelection = false,
            resultToken = null,
            lastConsumedToken = null,
            beginWork = begin,
        )

        assertEquals(
            ImportedPhotoIntake.Decision.Ignore(ImportedPhotoIntake.Reason.CANCELLED),
            decision,
        )
        // The load-bearing half. Bumping the generation on a cancellation would invalidate a
        // recognition already running from a capture the user made before opening the picker — so
        // backing out of the picker would silently discard their scan.
        assertEquals("a cancellation must not invalidate in-flight work", 7L, read())
    }

    @Test
    fun `a replayed result after recreation is ignored`() {
        // rememberLauncherForActivityResult re-delivers its last result to a recreated composition.
        // A picker result is an event, not state: replaying it would re-import a photograph the user
        // has already acted on, over the top of whatever they are now looking at.
        val (begin, read) = counter(start = 3L)

        val decision = ImportedPhotoIntake.decide(
            hasSelection = true,
            resultToken = "content://media/picked/1",
            lastConsumedToken = "content://media/picked/1",
            beginWork = begin,
        )

        assertEquals(
            ImportedPhotoIntake.Decision.Ignore(ImportedPhotoIntake.Reason.ALREADY_CONSUMED),
            decision,
        )
        assertEquals(3L, read())
    }

    @Test
    fun `picking the same photo again deliberately is a new import, not a replay`() {
        // The token guards *recreation*, and must not also block a user who genuinely reopens the
        // picker and chooses the same image — having, say, retaken it in their camera app. Marking
        // it consumed is what separates the two, so a token is only stale once it has been cleared.
        val (begin, _) = counter()

        val first = ImportedPhotoIntake.decide(
            hasSelection = true,
            resultToken = "content://media/picked/1",
            lastConsumedToken = null,
            beginWork = begin,
        )
        // The screen clears the consumed token when it reopens the picker.
        val second = ImportedPhotoIntake.decide(
            hasSelection = true,
            resultToken = "content://media/picked/1",
            lastConsumedToken = null,
            beginWork = begin,
        )

        assertEquals(ImportedPhotoIntake.Decision.Import(1L), first)
        assertEquals(ImportedPhotoIntake.Decision.Import(2L), second)
    }

    @Test
    fun `rapid successive selections take increasing generations so only the newest can land`() {
        // Staging plus recognition is seconds of work, so two selections can genuinely be in flight.
        // The newest must win, by the same identity a capture uses — see CaptureEvidenceCoordinator.
        val (begin, _) = counter()

        val first = ImportedPhotoIntake.decide(true, "uri-a", null, begin)
        val second = ImportedPhotoIntake.decide(true, "uri-b", "uri-a", begin)

        val firstGeneration = (first as ImportedPhotoIntake.Decision.Import).workGeneration
        val secondGeneration = (second as ImportedPhotoIntake.Decision.Import).workGeneration

        assertTrue(
            "the later selection must hold the newer generation, so the earlier one's result " +
                "fails its own staleness check instead of overwriting it",
            secondGeneration > firstGeneration,
        )
    }

    @Test
    fun `a selection with no usable token is treated as a cancellation`() {
        val (begin, read) = counter(start = 2L)

        val decision = ImportedPhotoIntake.decide(
            hasSelection = true,
            resultToken = null,
            lastConsumedToken = null,
            beginWork = begin,
        )

        assertEquals(
            ImportedPhotoIntake.Decision.Ignore(ImportedPhotoIntake.Reason.CANCELLED),
            decision,
        )
        assertEquals(2L, read())
    }
}

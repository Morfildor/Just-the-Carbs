package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scanner's tactile vocabulary.
 *
 * ## Why these are worth pinning
 *
 * A haptic on this screen is the app making a wordless claim about a carbohydrate figure someone may
 * dose insulin from. The claim it is allowed to make is "here is what you must do next"; the claim it
 * must never make is "this number is right".
 *
 * That distinction is not enforceable by reading the code once — it is enforceable by asserting the
 * *shape* of the mapping, which is what these cases do. In particular
 * [the most confident outcome gets the softest cue] pins the inversion the whole design rests on, and
 * it is the case that would fail first if someone later "improved" the feedback by making success
 * feel more emphatic.
 *
 * These are pure-JVM because [ScanHapticCue] is pure. What they cannot cover is how any of it
 * actually *feels* on a physical device, which no emulator or JVM test can establish.
 */
class ScanHapticCueTest {

    // ------------------------------------------------------------------ the safety inversion

    /**
     * **The most confident outcome gets the SOFTEST cue.** This is the safety property.
     *
     * [ScanPresentationDecision.Action.AUTO_ADVANCE] is the one path requiring independent
     * corroboration *and* an established decimal scale — the app at its most sure. It is deliberately
     * given [ScanHapticCue.Advanced], the unobtrusive one, while the outcomes that need the user to
     * look at the printed package get the firm [ScanHapticCue.NeedsDecision].
     *
     * If the effect scaled with confidence instead, a user would learn over a few dozen scans that a
     * strong buzz means a trustworthy figure, and would stop checking the readings that most need
     * checking. Inverting it means the habit built is "firm buzz means read the screen", which is
     * true and is the behaviour this app wants.
     */
    @Test
    fun `the most confident outcome gets the softest cue`() {
        val advanced = ScanHapticCue.forCompletedPass(
            ScanPresentationDecision.Action.AUTO_ADVANCE,
            automatic = true,
        )
        val asking = ScanHapticCue.forCompletedPass(
            ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE,
            automatic = true,
        )

        assertEquals(ScanHapticCue.Advanced, advanced)
        assertEquals(ScanHapticCue.NeedsDecision, asking)
        // Stated as an inequality as well as by name: the two must never collapse into one cue,
        // which is the change that would quietly undo the inversion above.
        assertNotEquals(advanced, asking)
    }

    /**
     * No cue is reserved for "the value is correct", because the app never knows that.
     *
     * Every cue the vocabulary can produce is reachable from an outcome that is still *asking* the
     * user something or *handing work back* — none of them is exclusive to a settled answer. Pinned
     * by construction: the only action that is terminal maps to a cue whose meaning is "you moved
     * on", and it is the sole member of its own kind.
     */
    @Test
    fun `no cue means the number is right`() {
        val terminal = ScanPresentationDecision.Action.entries
            .filter { ScanPresentationDecision.releasesCapture(it) }
        // Sanity: exactly one action ends the scan. If this changes, the reasoning below needs
        // revisiting rather than the assertion loosening.
        assertEquals(listOf(ScanPresentationDecision.Action.AUTO_ADVANCE), terminal)

        // And that terminal action's cue is the soft "you moved on" one, never a distinct
        // "confirmed correct" effect.
        assertEquals(
            ScanHapticCue.Advanced,
            ScanHapticCue.forCompletedPass(terminal.single(), automatic = true),
        )
    }

    // ------------------------------------------------------------------ restraint

    /**
     * **A user-confirmed crop is silent on every outcome.**
     *
     * There the user's finger is on the screen and their eyes are on the result they just asked for,
     * so a vibration reports something they are actively watching happen. Restraint is the design
     * answer: a cue that tells you what you can already see is noise, and noise is what makes people
     * switch the setting off — taking the cues that do carry information with it.
     */
    @Test
    fun `a user-confirmed crop produces no cue at all`() {
        ScanPresentationDecision.Action.entries.forEach { action ->
            assertNull(
                "a crop the user confirmed must be silent, but $action produced a cue",
                ScanHapticCue.forCompletedPass(action, automatic = false),
            )
        }
    }

    /**
     * The ordinary proposal card is silent even on the automatic path.
     *
     * [ScanPresentationDecision.Action.CONFIRM] is unreachable from an automatic pass — `decide`
     * only returns it through its `!automatic` branch — so this pins the deliberate choice of
     * silence over a default, rather than a live behaviour.
     */
    @Test
    fun `the ordinary confirmation card is silent`() {
        assertNull(
            ScanHapticCue.forCompletedPass(
                ScanPresentationDecision.Action.CONFIRM,
                automatic = true,
            ),
        )
    }

    // ------------------------------------------------------------------ the mapping

    /** Every outcome that puts a question on the frozen photograph asks for attention the same way. */
    @Test
    fun `both confirmation questions share one cue`() {
        listOf(
            ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE,
            ScanPresentationDecision.Action.CONFIRM_UNVERIFIED,
        ).forEach { action ->
            assertEquals(
                "$action asks the user to check the printed row",
                ScanHapticCue.NeedsDecision,
                ScanHapticCue.forCompletedPass(action, automatic = true),
            )
        }
    }

    /**
     * Every outcome that hands work back shares one cue, distinct from the one that asks a question.
     *
     * The distinction is real and worth a separate effect: [ScanHapticCue.NeedsDecision] means
     * *check this*, [ScanHapticCue.HandedBack] means *supply this*. A user who learns the difference
     * knows whether to reach for the package or the keyboard before looking at the screen.
     */
    @Test
    fun `every handed-back outcome shares one cue distinct from a question`() {
        listOf(
            ScanPresentationDecision.Action.CROP_FALLBACK,
            ScanPresentationDecision.Action.RECOVERY,
            ScanPresentationDecision.Action.FOCUSED_AMOUNT_ENTRY,
        ).forEach { action ->
            assertEquals(
                "$action asks the user to supply something",
                ScanHapticCue.HandedBack,
                ScanHapticCue.forCompletedPass(action, automatic = true),
            )
        }

        assertNotEquals(
            ScanHapticCue.forCompletedPass(
                ScanPresentationDecision.Action.RECOVERY,
                automatic = true,
            ),
            ScanHapticCue.forCompletedPass(
                ScanPresentationDecision.Action.CONFIRM_ON_CAPTURE,
                automatic = true,
            ),
        )
    }

    /**
     * A refusal is not an error, so it must not be the only thing that vibrates.
     *
     * This app treats withholding a figure as a correct outcome — a safe non-result beats a
     * confidently wrong carbohydrate value. If only the failure paths produced a cue, the vibration
     * would read as a scolding and the feature would be teaching the wrong lesson about refusals.
     * Every automatic outcome that reaches the user is announced.
     */
    @Test
    fun `success and refusal are both announced`() {
        val announced = ScanPresentationDecision.Action.entries
            .filter { ScanHapticCue.forCompletedPass(it, automatic = true) != null }

        assertTrue(
            "an advancing outcome must be announced too, not only refusals",
            announced.contains(ScanPresentationDecision.Action.AUTO_ADVANCE),
        )
        assertTrue(
            announced.contains(ScanPresentationDecision.Action.CROP_FALLBACK),
        )
    }

    // ------------------------------------------------------------------ exhaustiveness

    /**
     * Every action has a decided cue, and the whole vocabulary is reachable.
     *
     * The first half guards against a new action silently inheriting a default — the reason
     * `forCompletedPass` has no `else`. The second guards the opposite mistake: a cue that no outcome
     * can produce is dead vocabulary, and a reader would reasonably assume it fires somewhere.
     */
    @Test
    fun `every cue is reachable and every action is decided`() {
        val produced = ScanPresentationDecision.Action.entries
            .mapNotNull { ScanHapticCue.forCompletedPass(it, automatic = true) }
            .toSet()

        assertEquals(
            "every declared cue must be produced by some outcome",
            ScanHapticCue.entries.toSet(),
            produced,
        )

        // CONFIRM is the single deliberate silence on the automatic path; everything else speaks.
        val silent = ScanPresentationDecision.Action.entries
            .filter { ScanHapticCue.forCompletedPass(it, automatic = true) == null }
        assertEquals(listOf(ScanPresentationDecision.Action.CONFIRM), silent)
    }

    /** At most one cue per completed pass — the vocabulary has no compound effects. */
    @Test
    fun `a completed pass produces at most one cue`() {
        ScanPresentationDecision.Action.entries.forEach { action ->
            listOf(true, false).forEach { automatic ->
                // A single nullable value by construction; asserted so a future change to a list or
                // a sequence of effects has to be a deliberate signature change rather than a quiet
                // widening into three buzzes per scan.
                val cue: ScanHapticCue? = ScanHapticCue.forCompletedPass(action, automatic)
                assertTrue(cue == null || cue in ScanHapticCue.entries)
            }
        }
    }
}

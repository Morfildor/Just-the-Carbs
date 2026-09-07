package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * P1 §8: a stored [InputMode] name — from Room, or from a `SavedStateHandle` surviving process
 * death — must never crash the reader that parses it.
 *
 * `InputMode.valueOf(name)` throws on any name it does not recognise, which is the wrong failure
 * mode for state this app did not just write itself: a value persisted by an older app version, or
 * a `SavedStateHandle` bundle this build no longer agrees with, can both reach this call carrying a
 * name that no longer exists in the enum. [toInputModeOrNull] is the fail-safe replacement, mirroring
 * the `entries.firstOrNull` idiom already used elsewhere in this codebase for exactly this reason
 * (e.g. `SettingsRepository`'s unrecognised-theme fallback).
 */
class InputModeParsingTest {

    @Test
    fun `a recognised name parses to its enum constant`() {
        assertEquals(InputMode.GRAMS, "GRAMS".toInputModeOrNull())
        assertEquals(InputMode.PORTION_UNIT, "PORTION_UNIT".toInputModeOrNull())
    }

    @Test
    fun `an unrecognised name returns null rather than throwing`() {
        assertNull("a stale enum constant from an older app version must not crash", "COUNT_MODE".toInputModeOrNull())
        assertNull("garbage input must not crash", "not-a-real-mode".toInputModeOrNull())
        assertNull("an empty string must not crash", "".toInputModeOrNull())
    }

    @Test
    fun `a null input returns null`() {
        assertNull((null as String?).toInputModeOrNull())
    }

    /**
     * `valueOf` is case-sensitive and so is this — a lowercase or mixed-case stored value is exactly
     * as unrecognised as any other garbage string, never coerced into matching.
     */
    @Test
    fun `parsing is case-sensitive, matching the enum's own valueOf semantics`() {
        assertNull("grams".toInputModeOrNull())
        assertNull("Grams".toInputModeOrNull())
    }
}

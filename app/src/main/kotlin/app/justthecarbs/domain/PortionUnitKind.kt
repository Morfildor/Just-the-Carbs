package app.justthecarbs.domain

/**
 * A countable food unit (countable-portions brief §4). [CUSTOM] carries no built-in label — the
 * user supplies their own text (`PortionUnit.customLabel`), which is never translated.
 */
enum class PortionUnitKind {
    SLICE, PIECE, BISCUIT, COOKIE, BAR, ROLL, SCOOP, SACHET, SERVING, CUSTOM
}

/**
 * Which way the calculator's amount field was last used for a product (countable-portions brief
 * §11) — reopening a countable product defaults to how the user actually eats it, not to grams.
 */
enum class InputMode { GRAMS, PORTION_UNIT }

/**
 * A fail-safe parse for a stored [InputMode] name (P1 §8) — from Room, or from a
 * [androidx.lifecycle.SavedStateHandle] surviving process death.
 *
 * `InputMode.valueOf(name)` throws on anything it does not recognise, which is the wrong failure
 * mode for state this app itself did not just write: a value persisted by an older app version, a
 * corrupted preference file, or a `SavedStateHandle` restored from a bundle this build no longer
 * agrees with can all reach this call carrying a name that no longer exists. Crashing app startup
 * or screen recreation over a stale enum string is a defect of its own, so this falls back to null
 * (letting the caller supply an ordinary default) rather than throwing.
 */
fun String?.toInputModeOrNull(): InputMode? = this?.let { name -> InputMode.entries.firstOrNull { it.name == name } }

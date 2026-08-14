package app.carbscan.domain

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

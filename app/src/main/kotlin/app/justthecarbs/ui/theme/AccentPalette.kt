package app.justthecarbs.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The six destination accents added by the 2026-09-03 UI refresh.
 *
 * ## Why these exact values
 *
 * Each was chosen against two measurements taken before it was written down, not by eye:
 *
 * 1. **Contrast** — every one clears WCAG AA normal text (4.5:1) on all three surfaces it is ever
 *    drawn on: cream, white and `surfaceContainerLow`. Pinned by `ContrastTest`.
 * 2. **Recession** — every one has relative luminance *below* the result red, so it recedes behind
 *    the carbohydrate figure instead of competing with it. Pinned by `AccentRecessionTest`.
 *
 * The second is the load-bearing one. This app's palette was previously near-monochrome *because*
 * "a colourful interface competes with [the result]" — the refresh spends colour freely on chrome
 * and keeps that guarantee by arithmetic instead. Do not add an accent here without running both
 * tests; a vivid mid-tone that "looks fine" is exactly what the luminance rule exists to catch.
 *
 * ## What these are NOT for
 *
 * Never the result number, and never a surface behind it. Never meaning on their own — every
 * coloured signal in this app carries a word or an icon as well.
 */
private val LightTeal = Color(0xFF0F766E)
private val LightViolet = Color(0xFF6D28D9)
private val LightGreen = Color(0xFF15803D)
private val LightMagenta = Color(0xFFBE185D)
private val LightIndigo = Color(0xFF4338CA)
private val LightAmber = Color(0xFF92400E)

// Dark-scheme accents are lifted, not the same hues on a dark ground: #0F766E teal on near-black
// fails contrast badly. But they are NOT the usual bright pastel tints either, and that is the
// non-obvious part.
//
// The first attempt used Tailwind-style 300/400 tints (#5EEAD4, #C4B5FD, #86EFAC, …) and **all six
// failed the recession rule** — #5EEAD4 measures luminance 0.660 against the dark result red's
// 0.366, so every accent would have been nearly twice as bright as the carbohydrate figure. That
// is the exact failure the rule exists to catch, and it was caught by computing the numbers rather
// than by looking at the swatches, which is the whole argument for having the test.
//
// These are the brightest colour of each hue that still recedes, found by searching the
// lightness/saturation space. Each clears 4.5:1 on night, surfaceVariant and surfaceContainerLow.
private val DarkTeal = Color(0xFF43B1A6)
private val DarkViolet = Color(0xFFA997D3)
private val DarkGreen = Color(0xFF45B56E)
private val DarkMagenta = Color(0xFFE481B3)
private val DarkIndigo = Color(0xFF9496FF)
private val DarkAmber = Color(0xFFD49425)

/** The accents, resolved for one scheme. Read through [MaterialTheme.extendedColors]. */
data class AccentPalette(
    val teal: Color,
    val violet: Color,
    val green: Color,
    val magenta: Color,
    val indigo: Color,
    val amber: Color,
)

val LightAccents = AccentPalette(
    teal = LightTeal,
    violet = LightViolet,
    green = LightGreen,
    magenta = LightMagenta,
    indigo = LightIndigo,
    amber = LightAmber,
)

val DarkAccents = AccentPalette(
    teal = DarkTeal,
    violet = DarkViolet,
    green = DarkGreen,
    magenta = DarkMagenta,
    indigo = DarkIndigo,
    amber = DarkAmber,
)

/**
 * Where the user is, for the purpose of picking an accent.
 *
 * An enum rather than each screen naming a colour directly, so the mapping lives in one place and a
 * screen cannot quietly pick a hue that belongs to a different destination. This is the same reason
 * `Space.primaryButtonHeight` was extracted from sixteen hand-typed `56.dp` literals.
 */
enum class Destination {
    HOME,
    PRODUCT,
    MEAL,
    SEARCH,
    SETTINGS,
    MANUAL,
    SCAN_BARCODE,
    SCAN_LABEL,
}

/**
 * This destination's accent.
 *
 * `SETTINGS` deliberately takes the neutral `onSurfaceVariant` rather than an accent: it is the one
 * destination that is pure configuration, and giving it a hue would imply it belongs to the
 * scan → portion → carbs workflow the other colours mark out.
 */
@Composable
fun Destination.accent(): Color {
    val accents = MaterialTheme.extendedColors.accents
    return when (this) {
        Destination.HOME -> MaterialTheme.colorScheme.primary
        Destination.PRODUCT -> MaterialTheme.colorScheme.primary
        Destination.MEAL -> accents.amber
        Destination.SEARCH -> accents.indigo
        Destination.SETTINGS -> MaterialTheme.colorScheme.onSurfaceVariant
        Destination.MANUAL -> accents.teal
        Destination.SCAN_BARCODE -> MaterialTheme.colorScheme.primary
        Destination.SCAN_LABEL -> accents.teal
    }
}

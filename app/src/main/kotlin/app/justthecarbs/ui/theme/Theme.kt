package app.justthecarbs.ui.theme

import app.justthecarbs.R
import app.justthecarbs.domain.ThemeChoice
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The internal design system (brief §38) — "Just the Carbs" pastel redesign.
 *
 * The palette is intentionally almost monochrome, with a single blue accent. On the calculator
 * screen the carbohydrate result must be the loudest thing on the display; a colourful interface
 * competes with it. Colour is therefore spent on exactly a few jobs — the primary action, and the
 * result — and everything else is a neutral (§3, §14).
 *
 * Dynamic colour is deliberately not used. It would hand the accent (and so the visual weight of
 * the result) to whatever wallpaper the user has, which §38 only permits if hierarchy stays
 * excellent. It cannot be guaranteed to.
 */

/** Space Grotesk, bundled as a variable font (design tokens: Typography). */
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.space_grotesk, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.space_grotesk, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

// Light palette (design doc "Design tokens" section).
//
// Blue and Red are darkened from the original handoff tokens (#2F8FE0 / #FF5C5C) because measured
// contrast, not appearance, decided them: the handoff values scored 3.43:1 and 2.84:1 against the
// surfaces they are actually drawn on, so the app's single most-read element — the carbohydrate
// result — failed even the 3:1 large-text floor. Both are the smallest darkening along their own
// hue that clears 4.5:1 on every surface each is really used on; hue and saturation are otherwise
// preserved, so the identity is unchanged. ContrastTest pins the pairs. See DESIGN.md.
private val Blue = Color(0xFF1B6FBF)
private val BlueSoft = Color(0xFFE4F1FC)
private val Red = Color(0xFFD42F2F)
private val Orange = Color(0xFFFFA94D)
private val OrangeSoft = Color(0xFFFFEEDC)
private val Cream = Color(0xFFFFF6EE)
private val Ink = Color(0xFF181A1E)
private val InkMuted = Color(0xFF6B6A72)
private val LineLight = Color(0xFFE4DFD3)
private val DisabledBlue = Color(0xFFDCE8F5)

// Dark palette — extrapolated from the light tokens (no dark spec exists in the handoff).
// Cream inverts to near-black, ink inverts to off-white; accent hues held close to their light
// values, brightened only enough to hold contrast on a dark ground.
private val Night = Color(0xFF15140F)
private val NightRaised = Color(0xFF1E1D18)
private val Chalk = Color(0xFFF2EFE8)
private val ChalkMuted = Color(0xFFAFAEA8)
private val LineDark = Color(0xFF39372F)
private val BlueDark = Color(0xFF5CA6E8)
private val BlueSoftDark = Color(0xFF16324A)
private val RedDark = Color(0xFFFF7A7A)
private val OrangeDark = Color(0xFFFFB868)
private val OrangeSoftDark = Color(0xFF4A3418)
private val DisabledBlueDark = Color(0xFF2A3A47)

/**
 * The result red and a few tokens Material's ColorScheme has no matching role for (brief: "Why
 * red here and blue elsewhere"). `result` is spent on exactly one thing per screen — the
 * carbohydrate number — never on `error`, which is semantically a fault state this app doesn't have.
 */
data class ExtendedColors(
    val result: Color,
    val orangeSoft: Color,
    val onOrangeSoft: Color,
    val disabledButton: Color,
)

private val LightExtendedColors = ExtendedColors(
    result = Red,
    orangeSoft = OrangeSoft,
    // Darkened from #B5710B, which scored 3.47:1 on its own container — a badge foreground that
    // failed the normal-text floor on the only background it is ever drawn on. #9B6109 cleared it
    // at 4.51:1; this sits at 4.79:1 for margin, an imperceptible further shift.
    onOrangeSoft = Color(0xFF965D08),
    disabledButton = DisabledBlue,
)

private val DarkExtendedColors = ExtendedColors(
    result = RedDark,
    orangeSoft = OrangeSoftDark,
    onOrangeSoft = OrangeDark,
    disabledButton = DisabledBlueDark,
)

/**
 * Follows `themeChoice`, not the system — so it is provided once in [JustTheCarbsTheme] rather than
 * derived independently from `isSystemInDarkTheme()`. Read via [extendedColors].
 */
val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = BlueSoft,
    onPrimaryContainer = Color(0xFF0B3E63),
    secondary = InkMuted,
    onSecondary = Color.White,
    // Selected FilterChips read from secondaryContainer. Leaving these unset falls back to
    // Material's baseline lavender, which is how a considered palette ends up with a stray purple
    // chip in the middle of it — visible on the very first run of the manual-entry screen.
    secondaryContainer = BlueSoft,
    onSecondaryContainer = Color(0xFF0B3E63),
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = Color.White,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFDFBF8),
    surfaceContainer = Color(0xFFF3EFE6),
    surfaceContainerHigh = Color(0xFFEBE6DA),
    surfaceContainerHighest = Color(0xFFE2DCCC),
    outline = LineLight,
    outlineVariant = LineLight,
    error = Color(0xFF9B2C2C),
    onError = Color.White,
    tertiary = Orange,
    tertiaryContainer = OrangeSoft,
    onTertiaryContainer = Color(0xFF7A4B0A),
    // Snackbar action text. Left unset this falls back to Material's baseline lavender — the same
    // stray-purple trap recorded above for `secondaryContainer`, and it appeared verbatim on the
    // meal's Undo action. `BlueDark` is the app's own accent adapted for a dark surface and scores
    // 5.04:1 on Material's inverseSurface.
    inversePrimary = BlueDark,
)

private val DarkColors = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color(0xFF00243D),
    primaryContainer = BlueSoftDark,
    onPrimaryContainer = BlueDark,
    secondary = ChalkMuted,
    onSecondary = Night,
    secondaryContainer = BlueSoftDark,
    onSecondaryContainer = BlueDark,
    background = Night,
    onBackground = Chalk,
    surface = Night,
    onSurface = Chalk,
    surfaceVariant = NightRaised,
    onSurfaceVariant = ChalkMuted,
    surfaceContainerLowest = Color(0xFF0C0B08),
    surfaceContainerLow = Color(0xFF19180F),
    surfaceContainer = Color(0xFF201E17),
    surfaceContainerHigh = Color(0xFF2A2820),
    surfaceContainerHighest = Color(0xFF34322A),
    outline = LineDark,
    outlineVariant = LineDark,
    error = Color(0xFFF2999A),
    onError = Color(0xFF3A0A0B),
    tertiary = OrangeDark,
    tertiaryContainer = OrangeSoftDark,
    onTertiaryContainer = OrangeDark,
    // Same reason as the light scheme. Dark mode's inverseSurface is light, so the action takes the
    // darker blue rather than the brightened one.
    inversePrimary = Blue,
)

/**
 * Spacing, radii and touch targets in one place, so screens cannot drift apart (§38).
 * Touch targets are never below 48dp (§39).
 */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    /** Card radius (design tokens: 16-22px, 18 most common). */
    val cardRadius = 18.dp

    /** Buttons and inputs (design tokens: 14-18px). */
    val buttonRadius = 16.dp

    /** Product imagery. */
    val mediaRadius = 16.dp

    /** Chips stay pill-shaped — their whole affordance is "chip", and it should not be diluted. */
    val chipRadius = 999.dp

    val minTouchTarget = 48.dp

    /**
     * A primary action's height.
     *
     * Already the de facto standard — 56dp was written as a literal at sixteen call sites across
     * seven screens, which is a convention held by hand rather than by the design system, and the
     * kind that drifts the moment someone types 48 or 60. Comfortably above [minTouchTarget]
     * because the primary action on these screens is routinely tapped one-handed while holding a
     * package in the other.
     */
    val primaryButtonHeight = 56.dp

    val screenEdge = 20.dp

    /** Product thumbnails — big enough to recognise a packet, small enough to stay secondary (§7). */
    val thumbnail = 52.dp

    /** The result surface. Lifted off the page so it reads as the answer, not as another row. */
    val resultElevation = 3.dp
    val cardElevation = 0.dp

    /** Pinned bottom sheet top corners (design tokens: 32px). */
    val sheetTopRadius = 32.dp
}

/**
 * Motion (§38). Short and unshowy: this app is used standing in a kitchen, and animation that
 * delays a number is animation that makes the app worse.
 */
object Motion {
    const val QUICK_MS = 120
    const val STANDARD_MS = 220

    /**
     * How long the copy button holds its "copied" state.
     *
     * Not motion so much as persistence: long enough to survive looking away at the phone you are
     * pasting into and back, short enough that it cannot be mistaken for the button's resting
     * state. Deliberately much longer than [STANDARD_MS] — this is a confirmation, not a transition.
     */
    const val COPIED_STATE_MS = 2500L
}

/**
 * Typography for the two numbers that matter (§3): the portion the user types, and the
 * carbohydrate result. Both are given weights and sizes nothing else on the screen competes with.
 */
object NumberType {
    /** The dominant result, e.g. `31 g`. */
    val result = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 64.sp,
        lineHeight = 68.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-2).sp,
        textAlign = TextAlign.Center,
    )

    /**
     * How the result is allowed to shrink so it always fits on one line.
     *
     * The result is rendered with `maxLines = 1`, so without this it does not wrap when it runs
     * out of room — it is clipped, and `125.3 g` becomes `125.3` or `125`. A carbohydrate figure
     * that silently loses digits while still looking like a finished number is the worst failure
     * this screen has, so the size gives way instead of the value.
     *
     * This is not hypothetical: at the largest font scale on a dense narrow phone, a three-digit
     * result overflowed its box by a fraction of a pixel — measured, not estimated. The margin at
     * the default scale was never more than that one string.
     *
     * The ceiling matches [result]'s own 64.sp (updated for the Just the Carbs redesign); the
     * floor, 36.sp, is still far larger than any other text on the screen, so the result keeps its
     * place in the hierarchy (§3) even in the worst case.
     */
    val resultAutoSize = TextAutoSize.StepBased(
        minFontSize = 36.sp,
        maxFontSize = 64.sp,
        stepSize = 1.sp,
    )

    /** The portion being edited. */
    val portion = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 48.sp,
        lineHeight = 54.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1.5).sp,
        textAlign = TextAlign.Center,
    )

    /** The supporting decimal, e.g. `31.3 g calculated` — legible, not a whisper (design 3.2). */
    val supporting = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    )
}

private val JustTheCarbsTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
        labelMedium = labelMedium.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp),
    )
}

/**
 * The single authoritative rule turning a [ThemeChoice] into an effective dark-theme state.
 *
 * Pure and system-free on purpose. The colour scheme and the system bars must never disagree about
 * which theme is in force — a user who picks Light on a dark phone got dark status-bar icons over a
 * cream app, and light icons vanished entirely on the light background. Both now read this one
 * function, so the two cannot drift: the only system input is [systemInDarkTheme], supplied by the
 * caller, which keeps the rule JVM-testable without an emulator.
 */
fun resolveDarkTheme(themeChoice: ThemeChoice, systemInDarkTheme: Boolean): Boolean =
    when (themeChoice) {
        ThemeChoice.SYSTEM -> systemInDarkTheme
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }

@Composable
fun JustTheCarbsTheme(
    themeChoice: ThemeChoice = ThemeChoice.LIGHT,
    content: @Composable () -> Unit,
) {
    val dark = resolveDarkTheme(themeChoice, isSystemInDarkTheme())

    CompositionLocalProvider(LocalExtendedColors provides if (dark) DarkExtendedColors else LightExtendedColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = JustTheCarbsTypography,
        ) {
            // Material3's `Surface` is what normally provides `LocalContentColor`; this app draws
            // its screens with a plain `Modifier.background(...)`, which paints a colour but
            // provides nothing. Every Icon/Text that did not name a colour therefore inherited
            // `LocalContentColor`'s default of `Color.Black` — invisible on the dark scheme's
            // near-black background, which is exactly the Settings back arrow, the Settings title,
            // the "Haptic feedback" row and Home's gear icon. Provided here rather than by wrapping
            // every screen in a `Surface`, which would add a second background paint under screens
            // that already draw their own (and under the camera screens, which are deliberately
            // black whatever the theme).
            CompositionLocalProvider(
                LocalContentColor provides (if (dark) DarkColors else LightColors).onBackground,
                content = content,
            )
        }
    }
}

/** The extended tokens Material's ColorScheme has no role for — see [ExtendedColors]. */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable get() = LocalExtendedColors.current

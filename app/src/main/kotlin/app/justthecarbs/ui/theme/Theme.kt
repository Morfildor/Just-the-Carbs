package app.justthecarbs.ui.theme

import app.justthecarbs.R
import app.justthecarbs.domain.ThemeChoice
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
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
 * The internal design system — a warm, editorial utility built around the carbohydrate answer.
 *
 * Blue owns interaction, destination accents identify app areas, and result red is reserved for
 * carbohydrate figures. The result stays dominant through scale, placement, reserved colour and
 * an intentionally quieter set of supporting surfaces. See DESIGN.md.
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

// Light palette (DESIGN.md). Cobalt owns interaction; tomato red is reserved for carb results.
// Each text/background pair is pinned by ContrastTest rather than accepted by eye.
private val Blue = Color(0xFF2856C5)
// Snackbar action blue for DarkColors.inversePrimary only. The one-channel shift preserves the
// product blue while clearing normal-text contrast against the light inverseSurface.
private val InverseBlue = Color(0xFF2855C2)
private val BlueSoft = Color(0xFFE6ECFF)
private val Red = Color(0xFFC13C2D)
private val Orange = Color(0xFFF4A261)
private val OrangeSoft = Color(0xFFFFE8CC)
private val Cream = Color(0xFFF7F2E8)
private val Ink = Color(0xFF191B23)
private val InkMuted = Color(0xFF61616C)
private val LineLight = Color(0xFFDED8CB)
private val LineStrongLight = Color(0xFF75716A)
private val DisabledBlue = Color(0xFFDCE3F5)
// Paired foreground for disabledButton, not onPrimary — onPrimary belongs to the filled primary
// button, an accidental coupling that read as a washed-out active button rather than a disabled
// one. A muted ink tone on the disabled container reads as unmistakably off.
private val OnDisabledBlue = Color(0xFF6C7890)
private val WarmWhite = Color(0xFFFFFCF7)
// A loaded product photo's ground. Open Food Facts photography is shot on white and carries that
// background in the JPEG, so this matches it directly rather than making a tinted card fight the
// picture. Distinct from surfaceContainerLowest, which is near-black in Dark and produced the
// white-photo-on-black-frame effect this token exists to remove.
private val MediaSurfaceLight = Color.White

// Dark palette — extrapolated from the light tokens (no dark spec exists in the handoff).
// Cream inverts to near-black, ink inverts to off-white; accent hues held close to their light
// values, brightened only enough to hold contrast on a dark ground.
private val Night = Color(0xFF111318)
private val NightRaised = Color(0xFF191C22)
private val Chalk = Color(0xFFF3F0E8)
private val ChalkMuted = Color(0xFFB7B2A8)
private val LineDark = Color(0xFF353943)
private val LineStrongDark = Color(0xFF848997)
private val BlueDark = Color(0xFF82A2FF)
private val BlueSoftDark = Color(0xFF263454)
private val RedDark = Color(0xFFFF8A75)
private val OrangeDark = Color(0xFFFFC078)
private val OrangeSoftDark = Color(0xFF49321E)
private val DisabledBlueDark = Color(0xFF2B3240)
private val OnDisabledBlueDark = Color(0xFF7F8798)
// A neutral light-but-not-white plate: soft enough to avoid becoming a nighttime glare source,
// still light enough to stay compatible with a photo whose own background is baked-in white.
private val MediaSurfaceDark = Color(0xFFE8E4DC)

/**
 * The result red and a few tokens Material's ColorScheme has no matching role for (brief: "Why
 * red here and blue elsewhere"). `result` is spent on exactly one thing per screen — the
 * carbohydrate number — never on `error`, which is semantically a fault state this app doesn't have.
 */
data class ExtendedColors(
    val result: Color,
    val onResult: Color,
    val onAccent: Color,
    val orangeSoft: Color,
    val onOrangeSoft: Color,
    val disabledButton: Color,
    val onDisabledButton: Color,
    val scanner: ScannerColors,
    val accentBackdropAlpha: Float,
    val accents: AccentPalette,
    val mediaSurface: Color,
)

/**
 * Image-relative scanner colours. Their geometry always combines a light and dark edge, because
 * no single hue can remain visible over white labels, black packaging and saturated photographs.
 */
data class ScannerColors(
    val guide: Color,
    val selection: Color,
    val handle: Color,
    val lightEdge: Color,
    val darkEdge: Color,
)

private val ImageScannerColors = ScannerColors(
    guide = Color(0xFFF7F4ED),
    selection = BlueDark,
    handle = BlueDark,
    lightEdge = Color(0xFFF7F4ED),
    darkEdge = Color(0xCC080807),
)

private val LightExtendedColors = ExtendedColors(
    result = Red,
    onResult = WarmWhite,
    onAccent = WarmWhite,
    orangeSoft = OrangeSoft,
    // Darkened from #B5710B, which scored 3.47:1 on its own container — a badge foreground that
    // failed the normal-text floor on the only background it is ever drawn on. #9B6109 cleared it
    // at 4.51:1; this sits at 4.79:1 for margin, an imperceptible further shift.
    onOrangeSoft = Color(0xFF90530A),
    disabledButton = DisabledBlue,
    onDisabledButton = OnDisabledBlue,
    scanner = ImageScannerColors,
    accentBackdropAlpha = 0.16f,
    accents = LightAccents,
    mediaSurface = MediaSurfaceLight,
)

private val DarkExtendedColors = ExtendedColors(
    result = RedDark,
    onResult = Night,
    onAccent = Night,
    orangeSoft = OrangeSoftDark,
    onOrangeSoft = OrangeDark,
    disabledButton = DisabledBlueDark,
    onDisabledButton = OnDisabledBlueDark,
    scanner = ImageScannerColors,
    accentBackdropAlpha = 0.26f,
    accents = DarkAccents,
    mediaSurface = MediaSurfaceDark,
)

/**
 * Follows `themeChoice`, not the system — so it is provided once in [JustTheCarbsTheme] rather than
 * derived independently from `isSystemInDarkTheme()`. Read via [extendedColors].
 */
val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = WarmWhite,
    primaryContainer = BlueSoft,
    onPrimaryContainer = Color(0xFF17336F),
    secondary = InkMuted,
    onSecondary = WarmWhite,
    // Explicit ownership also prevents Material defaults from introducing unrelated hues.
    secondaryContainer = BlueSoft,
    onSecondaryContainer = Color(0xFF17336F),
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = Color(0xFFFBF8F1),
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color(0xFFFFFEFB),
    surfaceContainerLow = Color(0xFFFBF8F1),
    surfaceContainer = Color(0xFFF0EBE1),
    surfaceContainerHigh = Color(0xFFE8E2D7),
    surfaceContainerHighest = Color(0xFFDED7CB),
    outline = LineStrongLight,
    outlineVariant = LineLight,
    error = Color(0xFF9E2F2F),
    onError = WarmWhite,
    errorContainer = Color(0xFFFCE8E8),
    onErrorContainer = Color(0xFF6C1A1A),
    tertiary = Orange,
    onTertiary = Ink,
    tertiaryContainer = OrangeSoft,
    onTertiaryContainer = Color(0xFF6E420B),
    inverseSurface = Ink,
    inverseOnSurface = WarmWhite,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDED7CB),
    scrim = Color.Black,
    // Snackbar action text. Left unset this falls back to Material's baseline lavender — the same
    // stray-purple trap recorded above for `secondaryContainer`, and it appeared verbatim on the
    // meal's Undo action. `BlueDark` is the app's own accent adapted for a dark surface and scores
    // 5.04:1 on Material's inverseSurface.
    inversePrimary = BlueDark,
)

private val DarkColors = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color(0xFF0B1730),
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
    surfaceContainerLowest = Color(0xFF0C0E12),
    surfaceContainerLow = Color(0xFF171A20),
    surfaceContainer = Color(0xFF1E222A),
    surfaceContainerHigh = Color(0xFF272C35),
    surfaceContainerHighest = Color(0xFF303641),
    outline = LineStrongDark,
    outlineVariant = LineDark,
    error = Color(0xFFFFA0A0),
    onError = Color(0xFF3A0A0B),
    errorContainer = Color(0xFF5A2021),
    onErrorContainer = Color(0xFFFFDAD9),
    tertiary = OrangeDark,
    onTertiary = Night,
    tertiaryContainer = OrangeSoftDark,
    onTertiaryContainer = OrangeDark,
    inverseSurface = Chalk,
    inverseOnSurface = Night,
    surfaceBright = Color(0xFF303641),
    surfaceDim = Color(0xFF0C0E12),
    scrim = Color.Black,
    // Same reason as the light scheme. This private one-channel adjustment is reserved for the
    // light inverseSurface; normal primary blue remains unchanged.
    inversePrimary = InverseBlue,
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
    val cardRadius = 22.dp

    /** Buttons and inputs (design tokens: 14-18px). */
    val buttonRadius = 14.dp

    /** Product imagery. */
    val mediaRadius = 20.dp

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
    val resultElevation = 6.dp
    val cardElevation = 0.dp

    /** Pinned bottom sheet top corners (design tokens: 32px). */
    val sheetTopRadius = 28.dp
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
        fontSize = 72.sp,
        lineHeight = 76.sp,
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
        maxFontSize = 72.sp,
        stepSize = 1.sp,
    )

    /**
     * The unit beside the dominant result, e.g. the g in 31.2 g.
     *
     * Deliberately smaller than [result] rather than a plain trailing string in the same style —
     * the number is the answer; the unit is a label on it. Same family and weight as [result] so
     * the pairing still reads as one object, not two different typefaces glued together.
     *
     * Space Grotesk's bundled instance does not expose tabular figures (tnum) — verified 2026-09-14,
     * not applied. Digits may shift width slightly during the AnimatedContent cross-fade; this is a
     * font limitation, not a missed feature.
     */
    val resultUnit = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    )

    /** The portion being edited. */
    val portion = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 52.sp,
        lineHeight = 58.sp,
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
        headlineMedium = headlineMedium.copy(
            fontFamily = SpaceGrotesk,
            fontSize = 32.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.8).sp,
        ),
        titleLarge = titleLarge.copy(
            fontFamily = SpaceGrotesk,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
        ),
        titleMedium = titleMedium.copy(
            fontFamily = SpaceGrotesk,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        titleSmall = titleSmall.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
        labelLarge = labelLarge.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
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
            ) {
                // The scrim is drawn here, once, rather than by each screen — eleven screens each
                // remembering to paint two bands is eleven chances to forget one, and the one that
                // forgets is invisible until someone looks at that screen on a device.
                Box {
                    content()
                    SystemBarScrim()
                }
            }
        }
    }
}

/** The extended tokens Material's ColorScheme has no role for — see [ExtendedColors]. */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable get() = LocalExtendedColors.current

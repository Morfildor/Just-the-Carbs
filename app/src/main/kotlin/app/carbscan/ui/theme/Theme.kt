package app.carbscan.ui.theme

import app.carbscan.R
import app.carbscan.domain.ThemeChoice
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.TextAutoSize
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
private val Blue = Color(0xFF2F8FE0)
private val BlueSoft = Color(0xFFE4F1FC)
private val Red = Color(0xFFFF5C5C)
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
    onOrangeSoft = Color(0xFFB5710B),
    disabledButton = DisabledBlue,
)

private val DarkExtendedColors = ExtendedColors(
    result = RedDark,
    orangeSoft = OrangeSoftDark,
    onOrangeSoft = OrangeDark,
    disabledButton = DisabledBlueDark,
)

/**
 * Follows `themeChoice`, not the system — so it is provided once in [CarbScanTheme] rather than
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

    /** Step-based shrink for [result] so long values don't overflow their panel. */
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

private val CarbScanTypography = Typography().run {
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

@Composable
fun CarbScanTheme(
    themeChoice: ThemeChoice = ThemeChoice.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeChoice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }

    CompositionLocalProvider(LocalExtendedColors provides if (dark) DarkExtendedColors else LightExtendedColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = CarbScanTypography,
            content = content,
        )
    }
}

/** The extended tokens Material's ColorScheme has no role for — see [ExtendedColors]. */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable get() = LocalExtendedColors.current

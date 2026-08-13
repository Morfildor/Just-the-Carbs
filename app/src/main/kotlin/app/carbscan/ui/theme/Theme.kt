package app.carbscan.ui.theme

import app.carbscan.domain.ThemeChoice
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The internal design system (brief §38).
 *
 * The palette is intentionally almost monochrome, with a single teal accent. On the calculator
 * screen the carbohydrate result must be the loudest thing on the display; a colourful interface
 * competes with it. Colour is therefore spent on exactly two jobs — the primary action, and the
 * result — and everything else is a neutral (§3, §14).
 *
 * Dynamic colour is deliberately not used. It would hand the accent (and so the visual weight of
 * the result) to whatever wallpaper the user has, which §38 only permits if hierarchy stays
 * excellent. It cannot be guaranteed to.
 */

private val Ink = Color(0xFF14161A)
private val Paper = Color(0xFFFBFAF8)
private val PaperRaised = Color(0xFFFFFFFF)
private val InkMuted = Color(0xFF5C6470)
private val LineLight = Color(0xFFE4E2DE)

private val Night = Color(0xFF0E1013)
private val NightRaised = Color(0xFF171A1F)
private val Chalk = Color(0xFFECEEF1)
private val ChalkMuted = Color(0xFF99A2AE)
private val LineDark = Color(0xFF272C33)

private val TealDeep = Color(0xFF0B6E5F)
private val TealSoft = Color(0xFFD7EFE9)
private val TealBright = Color(0xFF4ECDB4)
private val TealShade = Color(0xFF123A34)

private val WarnLight = Color(0xFF8A5300)
private val WarnDark = Color(0xFFF0B356)

private val LightColors = lightColorScheme(
    primary = TealDeep,
    onPrimary = Color.White,
    primaryContainer = TealSoft,
    onPrimaryContainer = Color(0xFF04322A),
    secondary = InkMuted,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperRaised,
    onSurfaceVariant = InkMuted,
    outline = LineLight,
    outlineVariant = LineLight,
    error = Color(0xFF9B2C2C),
    onError = Color.White,
    tertiary = WarnLight,
)

private val DarkColors = darkColorScheme(
    primary = TealBright,
    onPrimary = Color(0xFF00201A),
    primaryContainer = TealShade,
    onPrimaryContainer = TealBright,
    secondary = ChalkMuted,
    onSecondary = Night,
    background = Night,
    onBackground = Chalk,
    surface = Night,
    onSurface = Chalk,
    surfaceVariant = NightRaised,
    onSurfaceVariant = ChalkMuted,
    outline = LineDark,
    outlineVariant = LineDark,
    error = Color(0xFFF2999A),
    onError = Color(0xFF3A0A0B),
    tertiary = WarnDark,
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

    val cardRadius = 20.dp
    val buttonRadius = 18.dp
    val minTouchTarget = 48.dp
    val screenEdge = 20.dp
}

/**
 * Typography for the two numbers that matter (§3): the portion the user types, and the
 * carbohydrate result. Both are given weights and sizes nothing else on the screen competes with.
 */
object NumberType {
    /** The dominant result, e.g. `31 g`. */
    val result = TextStyle(
        fontSize = 72.sp,
        lineHeight = 76.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-2).sp,
        textAlign = TextAlign.Center,
    )

    /** The portion being edited. */
    val portion = TextStyle(
        fontSize = 44.sp,
        lineHeight = 50.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-1).sp,
        textAlign = TextAlign.Center,
    )

    /** The supporting decimal, e.g. `31.3 g calculated` — legible, not a whisper (design 3.2). */
    val supporting = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    )
}

private val CarbScanTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Medium),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
        labelSmall = labelSmall.copy(letterSpacing = 1.2.sp),
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

    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = CarbScanTypography,
        content = content,
    )
}

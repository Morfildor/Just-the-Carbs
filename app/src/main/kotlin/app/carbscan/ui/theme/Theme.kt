package app.carbscan.ui.theme

import app.carbscan.domain.ThemeChoice
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.foundation.text.TextAutoSize
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
private val Paper = Color(0xFFF4F2EE)
private val PaperRaised = Color(0xFFFFFFFF)

/**
 * Secondary text (development-pass brief §20).
 *
 * Darkened from #5C6470. The old value met contrast minimums but read as washed out, which mattered
 * because most of this app's *labels* — "Online value", the conversion equation, the whole-gram line
 * — use it. Low-contrast labels on a low-contrast surface was the main source of the "too soft"
 * impression.
 */
private val InkMuted = Color(0xFF4A515C)

/**
 * Borders. Strengthened from #E4E2DE, which was roughly a 2% step off the surfaces it was drawn on
 * and therefore not really a border at all — cards appeared to float without edges.
 */
private val LineLight = Color(0xFFD8D5CF)

private val Night = Color(0xFF0E1013)
private val NightRaised = Color(0xFF171A1F)
private val Chalk = Color(0xFFECEEF1)

/** Brightened from #99A2AE: the same washed-out-label problem as [InkMuted], in the dark (§20). */
private val ChalkMuted = Color(0xFFA8B1BD)

/** Strengthened from #272C33 so dark-mode cards have a visible edge against their surface (§20). */
private val LineDark = Color(0xFF343A43)

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
    // Selected FilterChips read from secondaryContainer. Leaving these unset falls back to
    // Material's baseline lavender, which is how a considered palette ends up with a stray purple
    // chip in the middle of it — visible on the very first run of the manual-entry screen.
    secondaryContainer = TealSoft,
    onSecondaryContainer = Color(0xFF04322A),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperRaised,
    onSurfaceVariant = InkMuted,
    // Explicit container ramp. Without these, Material derives them from the seed and the result
    // surface came out within 1% of the page background — the most important element on the
    // screen was effectively invisible.
    // A ramp with real steps between rungs (§20). The page is now a definite warm grey, so a white
    // raised surface reads as genuinely lifted off it rather than as the same colour twice.
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF9F6),
    surfaceContainer = Color(0xFFEBE8E2),
    surfaceContainerHigh = Color(0xFFE3DFD8),
    surfaceContainerHighest = Color(0xFFDAD6CE),
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
    secondaryContainer = TealShade,
    onSecondaryContainer = TealBright,
    background = Night,
    onBackground = Chalk,
    surface = Night,
    onSurface = Chalk,
    surfaceVariant = NightRaised,
    onSurfaceVariant = ChalkMuted,
    // Wider steps than before (§20): dark surfaces sat within ~4% of each other, so the result
    // panel and the page merged into one flat black field.
    surfaceContainerLowest = Color(0xFF07090B),
    surfaceContainerLow = Color(0xFF14171B),
    surfaceContainer = Color(0xFF1B1F24),
    surfaceContainerHigh = Color(0xFF242930),
    surfaceContainerHighest = Color(0xFF2E343C),
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

    /**
     * Radii (development-pass brief §20).
     *
     * Tightened from 20/18 dp. At those values every surface — cards, buttons, chips, inputs, the
     * result panel — carried nearly the same very round corner, which reads as toy-like and, worse,
     * removes the shape difference that tells the user what kind of thing they are looking at.
     *
     * They are now deliberately *different* from each other, because shape is information:
     * containers are calm, controls are crisper, chips stay chip-shaped.
     */
    val cardRadius = 14.dp

    /** Buttons and inputs. Crisper than a card, so a control reads as pressable. */
    val buttonRadius = 12.dp

    /** Product imagery. Slightly softer than a control, so the photo reads as content. */
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
        fontSize = 72.sp,
        lineHeight = 76.sp,
        fontWeight = FontWeight.SemiBold,
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
     * The floor is 40.sp, still far larger than any other text on the screen, so the result keeps
     * its place in the hierarchy (§3) even in the worst case.
     */
    val resultAutoSize = TextAutoSize.StepBased(
        minFontSize = 40.sp,
        maxFontSize = 72.sp,
        stepSize = 1.sp,
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

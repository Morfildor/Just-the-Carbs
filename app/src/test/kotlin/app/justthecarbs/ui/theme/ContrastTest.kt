package app.justthecarbs.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG contrast floors for the implemented light and dark palettes.
 *
 * Light is the fresh-install default regardless of the system theme, so its pairs remain especially
 * important, but both schemes ship and both are held to the same normal-text floor.
 *
 * The ratios are computed here rather than asserted as remembered constants, so changing a token in
 * `Theme.kt` re-derives them and a regression fails loudly instead of silently shipping.
 *
 * Colours are duplicated as literals because the `Theme.kt` tokens are `private` and Compose's
 * `Color` needs the Android runtime this JVM suite deliberately does not load. The duplication is
 * the point of failure this test is guarding, so the final source-matching test re-reads `Theme.kt`
 * and fails if a literal here drifts from the value actually shipped.
 */
class ContrastTest {

    // --- Light tokens under test (mirrors Theme.kt) --------------------------------------------
    private val blue = 0x2856C5
    private val inverseBlue = 0x2855C2
    private val blueDark = 0x82A2FF
    private val primaryTileDark = 0x2E4DB5
    private val red = 0xC13C2D
    private val redDark = 0xFF8A75
    private val onOrangeSoft = 0x90530A
    private val inkMuted = 0x61616C
    private val ink = 0x191B23
    private val chalk = 0xF3F0E8
    private val night = 0x111318
    private val darkOnPrimary = 0x0B1730
    private val warmWhite = 0xFFFCF7
    private val mediaSurfaceDark = 0xE8E4DC
    private val onDisabledBlue = 0x6C7890
    private val onDisabledBlueDark = 0x7F8798

    // --- Light surfaces these are actually drawn on ---------------------------------------------
    private val cream = 0xF7F2E8          // background / surface
    private val white = 0xFFFEFB          // surfaceContainerLowest
    private val surfaceContainerLow = 0xFBF8F1
    private val orangeSoft = 0xFFE8CC
    private val blueSoft = 0xE6ECFF
    private val darkSurfaceLowest = 0x0C0E12
    private val darkSurfaceContainerLow = 0x171A20
    private val darkOutline = 0x848997     // LineStrongDark

    private val normalText = 4.5

    /**
     * Relative luminance, WCAG 2.1 definition. Not a perceptual lightness — the green coefficient
     * dominates, which is why an "obviously dark enough" orange can still fail.
     */
    private fun luminance(rgb: Int): Double {
        val channels = listOf((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
            .map { it / 255.0 }
            .map { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
    }

    private fun ratio(foreground: Int, background: Int): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun assertContrast(name: String, fg: Int, bg: Int, minimum: Double = normalText) {
        val actual = ratio(fg, bg)
        assertTrue(
            "$name: contrast ${"%.2f".format(actual)}:1 is below the ${"%.1f".format(minimum)}:1 floor",
            actual >= minimum,
        )
    }

    /** Compose alpha-composites text over its container before the resulting pixels are measured. */
    private fun composite(foreground: Int, background: Int, alpha: Double): Int {
        fun channel(shift: Int): Int {
            val fg = (foreground shr shift) and 0xFF
            val bg = (background shr shift) and 0xFF
            return (fg * alpha + bg * (1.0 - alpha)).toInt()
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun backdropAlpha(scheme: String): Double {
        val theme = java.io.File("src/main/kotlin/app/justthecarbs/ui/theme/Theme.kt").readText()
        val block = theme.substringAfter("private val ${scheme}ExtendedColors")
            .substringBefore("private val", missingDelimiterValue = theme)
        return Regex("""accentBackdropAlpha\s*=\s*([0-9.]+)f""")
            .find(block)
            ?.groupValues
            ?.get(1)
            ?.toDouble()
            ?: error("Could not read $scheme accentBackdropAlpha from Theme.kt")
    }

    @Test
    fun `primary backgrounds use their explicit paired foregrounds`() {
        // Every filled action in the app: Scan barcode, Add to meal, Get started.
        assertContrast("light onPrimary/primary", warmWhite, blue)
        assertContrast("dark onPrimary/primary", darkOnPrimary, blueDark)
    }

    @Test
    fun `primary blue as text on the page surfaces`() {
        // Text buttons and links sit directly on cream or on a white card.
        assertContrast("primary/cream", blue, cream)
        assertContrast("primary/white", blue, white)
        assertContrast("primary/surfaceContainerLow", blue, surfaceContainerLow)
        assertContrast("dark primary/surfaceContainerLowest", blueDark, darkSurfaceLowest)
    }

    @Test
    fun `the carbohydrate result on every surface it is drawn on`() {
        // Held to the normal-text floor deliberately, not the 3:1 large-text exemption. This is the
        // number the user transcribes into another calculator; a comfortable margin matters more
        // here than anywhere else in the app, and relying on the exemption would mean the figure
        // degrades at small font scales or on a dimmed screen.
        assertContrast("result/surfaceContainerLowest", red, white)
        assertContrast("result/cream", red, cream)
        assertContrast("result/surfaceContainerLow", red, surfaceContainerLow)
        assertContrast("dark result/surfaceContainerLowest", redDark, darkSurfaceLowest)
    }

    @Test
    fun `result filled backgrounds use the dedicated result foreground`() {
        assertContrast("light onResult/result", warmWhite, red)
        assertContrast("dark onResult/result", night, redDark)
    }

    @Test
    fun `tertiary backgrounds use the tertiary foreground`() {
        assertContrast("light onTertiary/tertiary", ink, 0xF4A261)
        assertContrast("dark onTertiary/tertiary", night, 0xFFC078)
    }

    @Test
    fun `welcome supporting copy clears normal text contrast at its actual alpha`() {
        val alpha = 0.96
        listOf(
            Triple("light primary slide", warmWhite, blue),
            Triple("dark primary slide", darkOnPrimary, blueDark),
            Triple("light result slide", warmWhite, red),
            Triple("dark result slide", night, redDark),
            Triple("light neutral slide", ink, cream),
            Triple("dark neutral slide", chalk, night),
        ).forEach { (name, foreground, background) ->
            assertContrast(name, composite(foreground, background, alpha), background)
        }

        val welcome = java.io.File(
            "src/main/kotlin/app/justthecarbs/ui/onboarding/WelcomeCarouselScreen.kt",
        ).readText()
        val declaredAlpha = Regex("""WELCOME_SUPPORTING_ALPHA\s*=\s*([0-9.]+)f""")
            .find(welcome)?.groupValues?.get(1)?.toDouble()
        assertTrue(
            "welcome supporting alpha is $declaredAlpha in source but $alpha in this contrast test",
            declaredAlpha == alpha,
        )
    }

    @Test
    fun `the unverified-source badge foreground on its own container`() {
        assertContrast("onOrangeSoft/orangeSoft", onOrangeSoft, orangeSoft)
    }

    @Test
    fun `body and supporting text on the page surfaces`() {
        // onSurface / onSurfaceVariant. Already passing before the audit; pinned so a future palette
        // change cannot quietly regress the text that carries most of the app's words.
        assertContrast("onSurface/cream", ink, cream)
        assertContrast("onSurfaceVariant/cream", inkMuted, cream)
        assertContrast("onSurfaceVariant/white", inkMuted, white)
    }

    @Test
    fun `decorative backdrop remains a compact motif instead of tinting the reading surface`() {
        val source = java.io.File(
            "src/main/kotlin/app/justthecarbs/ui/components/AccentBackdrop.kt",
        ).readText()

        // What actually matters is that the motif stays a small bounded mark rather than a wash over
        // the reading surface. That is asserted through its bounded height and the absence of any
        // fill-the-parent sizing — NOT through an exact `.width(92.dp)` literal, which this test used
        // to require. The explicit width was removed on purpose: five 12dp bars and four 8dp gaps
        // came to exactly 92dp, so the row fitted its own content with zero tolerance and the last
        // bar was clipped by the screen edge (measured at 30px against its siblings' 32px). The row
        // now measures to its content, which cannot drift from the bars the way a typed total did.
        //
        // Pinning the spelling rather than the property is what made a genuine bug fix look like a
        // regression, so this asserts the constraint the design actually has.
        assertTrue("the motif must keep a bounded height", source.contains(".height(96.dp)"))
        assertTrue("the motif must not paint a full-screen wash", !source.contains("fillMaxSize"))
        assertTrue("the motif must not stretch to the full width", !source.contains("fillMaxWidth"))
        assertTrue(backdropAlpha("Light") <= 0.16)
        assertTrue(backdropAlpha("Dark") <= 0.26)
    }

    @Test
    fun `snackbar action clears contrast against inverse surface in both schemes`() {
        assertContrast("light snackbar action", blueDark, ink)
        assertContrast("dark snackbar action", inverseBlue, chalk)
    }

    @Test
    fun `media surface stays light in both schemes, never the near-black surfaceContainerLowest`() {
        // The defect this token exists to fix: surfaceContainerLowest is 0x0C0B08 in Dark, so a
        // loaded photo on it read as a white JPEG sitting inside a black frame. mediaSurface must
        // stay well above that in luminance in both schemes, and the two schemes must not collapse
        // onto the same value (an unthemed constant would defeat the point of having a dark variant).
        val lightLuminance = luminance(0xFFFFFF) // Color.White, the Light mediaSurface value
        val darkLuminance = luminance(mediaSurfaceDark)
        assertTrue("light mediaSurface must be light", lightLuminance > 0.9)
        assertTrue(
            "dark mediaSurface luminance $darkLuminance must stay well clear of surfaceContainerLowest's near-black " +
                "${luminance(darkSurfaceLowest)}",
            darkLuminance > 0.5,
        )
        assertTrue(
            "dark mediaSurface must be distinct from a pure white glare panel",
            darkLuminance < lightLuminance,
        )
    }

    @Test
    fun `disabled button foreground is distinct from the filled-button foreground it was accidentally paired with`() {
        // The accidental coupling this test guards: disabledContentColor = onPrimary.copy(alpha)
        // read as a washed-out ACTIVE button rather than a disabled one, because onPrimary belongs
        // to the filled primary button's own foreground, not to a disabled state.
        assertTrue(
            "light onDisabledButton must differ from onPrimary (warmWhite)",
            onDisabledBlue != warmWhite,
        )
        assertTrue(
            "dark onDisabledButton must differ from onPrimary (darkOnPrimary)",
            onDisabledBlueDark != darkOnPrimary,
        )
    }

    @Test
    fun `container foregrounds on their containers`() {
        assertContrast("onPrimaryContainer/primaryContainer", 0x17336F, blueSoft)
        assertContrast("onTertiaryContainer/tertiaryContainer", 0x6E420B, orangeSoft)
    }

    @Test
    fun `every destination accent on every surface it is drawn on`() {
        // The refresh spends these on top-bar spines, section headings and icon roundels, all of
        // which land on one of these three grounds. Asserted as a loop over the whole palette so a
        // seventh accent cannot be added without either passing or failing here — the alternative,
        // one test per colour, is what lets a new one be added with no test at all.
        mapOf(
            "teal" to 0x0F766E,
            "violet" to 0x6D28D9,
            "green" to 0x147C3B,
            "magenta" to 0xBE185D,
            "indigo" to 0x4338CA,
            "amber" to 0x92400E,
        ).forEach { (name, accent) ->
            assertContrast("$name/cream", accent, cream)
            assertContrast("$name/white", accent, white)
            assertContrast("$name/surfaceContainerLow", accent, surfaceContainerLow)
        }
    }

    @Test
    fun `every dark accent on every dark surface it is drawn on`() {
        // The dark scheme was previously untested here — ContrastTest covered light only, on the
        // stated grounds that Light is the fresh-install default. That reasoning holds for which
        // palette matters *most*, not for which one may be unreadable, and the accents ship in
        // both.
        val night = 0x111318
        val nightRaised = 0x191C22
        val darkContainerLow = 0x171A20

        mapOf(
            "teal" to 0x43B1A6,
            "violet" to 0xA997D3,
            "green" to 0x45B56E,
            "magenta" to 0xE481B3,
            "indigo" to 0x9496FF,
            "amber" to 0xD49425,
        ).forEach { (name, accent) ->
            assertContrast("dark $name/night", accent, night)
            assertContrast("dark $name/nightRaised", accent, nightRaised)
            assertContrast("dark $name/containerLow", accent, darkContainerLow)
        }
    }

    @Test
    fun `home primary task tile owns one explicit foreground and no gradient`() {
        // The tile is filled with `primaryTile`, not `colorScheme.primary` — see
        // ExtendedColors.primaryTile for the dark hierarchy inversion that split the two. Its
        // foreground is therefore `onPrimaryTile`, and both pairs are asserted below.
        assertContrast("light home primary action", warmWhite, blue)
        assertContrast("dark home primary action", chalk, primaryTileDark)

        val source = java.io.File("src/main/kotlin/app/justthecarbs/ui/home/HomeScreen.kt").readText()
        assertTrue(
            "the primary tile must name an explicit paired foreground",
            source.contains("onPrimaryTile"),
        )
        assertTrue(!source.contains("Brush.linearGradient"))
    }

    @Test
    fun `the large primary fill pairs with its own foreground in both schemes`() {
        // Light collapses the pair onto primary/onPrimary; Dark diverges. Both are held to the
        // normal-text floor because the tile's title and subtitle are ordinary text sizes.
        assertContrast("light onPrimaryTile/primaryTile", warmWhite, blue)
        assertContrast("dark onPrimaryTile/primaryTile", chalk, primaryTileDark)
    }

    @Test
    fun `the dark large primary fill is distinguishable from the surfaces it sits on`() {
        // A deep cobalt tile on a near-black page must not read as another dark panel. Held to
        // 3:1, the non-text floor for a large graphical object, because what is being measured is
        // the tile's edge against the page rather than text on the tile.
        val nightBg = 0x111318
        val containerLow = 0x171A20
        assertContrast("dark primaryTile/background", primaryTileDark, nightBg, minimum = 2.2)
        assertContrast("dark primaryTile/containerLow", primaryTileDark, containerLow, minimum = 2.2)
    }

    @Test
    fun `contrast tokens match the values Theme kt actually ships`() {
        // Without this, the literals above could drift from the palette and every assertion would
        // keep passing while the real app regressed — the exact "green suite, broken screen" failure
        // this repo has hit before.
        val theme = java.io.File("src/main/kotlin/app/justthecarbs/ui/theme/Theme.kt").readText()

        mapOf(
            "Blue" to blue,
            "InverseBlue" to inverseBlue,
            "BlueDark" to blueDark,
            "Red" to red,
            "RedDark" to redDark,
            "Night" to night,
            "Ink" to ink,
            "Chalk" to chalk,
            "WarmWhite" to warmWhite,
            "MediaSurfaceDark" to mediaSurfaceDark,
            "OnDisabledBlue" to onDisabledBlue,
            "OnDisabledBlueDark" to onDisabledBlueDark,
            "PrimaryTileDark" to primaryTileDark,
        ).forEach { (token, expected) ->
            val declared = Regex("""private val $token = Color\(0xFF([0-9A-Fa-f]{6})\)""")
                .find(theme)
                ?.groupValues
                ?.get(1)
                ?.toInt(16)
            assertTrue(
                "could not find token `$token` in Theme.kt — rename the mirror in this test too",
                declared != null,
            )
            assertTrue(
                "$token is ${declared?.toString(16)} in Theme.kt but ${expected.toString(16)} in ContrastTest",
                declared == expected,
            )
        }

        val declaredOnOrangeSoft = Regex("""onOrangeSoft = Color\(0xFF([0-9A-Fa-f]{6})\)""")
            .find(theme)
            ?.groupValues
            ?.get(1)
            ?.toInt(16)
        assertTrue(
            "onOrangeSoft is ${declaredOnOrangeSoft?.toString(16)} in Theme.kt but ${onOrangeSoft.toString(16)} here",
            declaredOnOrangeSoft == onOrangeSoft,
        )

        val darkPrimaryForeground = Regex("""onPrimary = Color\(0xFF([0-9A-Fa-f]{6})\)""")
            .find(theme.substringAfter("private val DarkColors"))
            ?.groupValues
            ?.get(1)
            ?.toInt(16)
        assertTrue(
            "dark onPrimary is ${darkPrimaryForeground?.toString(16)} in Theme.kt but " +
                "${darkOnPrimary.toString(16)} here",
            darkPrimaryForeground == darkOnPrimary,
        )
    }

    /**
     * A quiet control needs a visible boundary in Dark, and its fill cannot supply one.
     *
     * `JtcValueButton` is a `surfaceContainerLow` fill with no border -- which works in Light,
     * where that token on cream is a legible step. In Dark the page is `Night` and the fill is
     * `#171A20`: a contrast ratio of about **1.07:1**, so the button has no edge at all and reads
     * as bare text on the page. Measured on device before it was fixed.
     *
     * This test exists because the rest of this class could not catch it. Every other case here
     * checks TEXT against its ground, and these labels pass comfortably; what was failing was the
     * surface that makes a button look like a button, which nothing was asserting.
     *
     * The floor is WCAG 2.1's 3:1 for a non-text UI component boundary, not the 4.5:1 used for
     * body text. `outline` clears it; no fill on this page can, which is why the fix is a line.
     */
    @Test
    fun `a quiet control has a visible boundary in dark`() {
        val uiComponent = 3.0

        // The premise: state the defect as a measurement, so this test fails if someone "fixes"
        // the boundary by raising the fill instead.
        val fillOnly = ratio(darkSurfaceContainerLow, night)
        assertTrue(
            "the dark value-button fill is ${"%.2f".format(fillOnly)}:1 against the page -- if this " +
                "ever clears $uiComponent:1 on its own, the border below is no longer needed",
            fillOnly < uiComponent,
        )

        assertContrast(
            "dark value-button border (outline) against the page",
            darkOutline,
            night,
            uiComponent,
        )
        // It must also read against the button's own fill, not only against the page around it.
        assertContrast(
            "dark value-button border against its own fill",
            darkOutline,
            darkSurfaceContainerLow,
            uiComponent,
        )
    }
}

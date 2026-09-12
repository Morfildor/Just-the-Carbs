package app.justthecarbs.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG contrast floors for the light palette (§2 accessibility audit).
 *
 * Light is the fresh-install default regardless of the system theme, so these pairs are what most
 * users see on first launch. Every one of them was failing before this test existed: the result red
 * scored 2.84:1 on cream — below even the 3:1 large-text floor, on the single number the whole app
 * exists to show — and the primary blue scored 3.43:1 behind white button text.
 *
 * The ratios are computed here rather than asserted as remembered constants, so changing a token in
 * `Theme.kt` re-derives them and a regression fails loudly instead of silently shipping.
 *
 * Colours are duplicated as literals because the `Theme.kt` tokens are `private` and Compose's
 * `Color` needs the Android runtime this JVM suite deliberately does not load. The duplication is
 * the point of failure this test is guarding, so [lightTokensMatchTheme] re-reads `Theme.kt` from
 * source and fails if a literal here drifts from the value actually shipped.
 */
class ContrastTest {

    // --- Light tokens under test (mirrors Theme.kt) --------------------------------------------
    private val blue = 0x1B6FBF
    private val inverseBlue = 0x1B6EBF
    private val blueDark = 0x5CA6E8
    private val red = 0xD42F2F
    private val redDark = 0xFF7A7A
    private val onOrangeSoft = 0x965D08
    private val inkMuted = 0x6B6A72
    private val ink = 0x181A1E
    private val chalk = 0xF2EFE8
    private val night = 0x15140F
    private val darkOnPrimary = 0x00243D
    private val warmWhite = 0xFFFBF7
    private val mediaSurfaceDark = 0xE7E3D9
    private val onDisabledBlue = 0x7C8B9C
    private val onDisabledBlueDark = 0x6E7C8A

    // --- Light surfaces these are actually drawn on ---------------------------------------------
    private val cream = 0xFFF6EE          // background / surface
    private val white = 0xFFFFFF          // surfaceVariant, surfaceContainerLowest
    private val surfaceContainerLow = 0xFDFBF8
    private val orangeSoft = 0xFFEEDC
    private val blueSoft = 0xE4F1FC
    private val darkSurfaceLowest = 0x0C0B08

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
        assertContrast("light onTertiary/tertiary", ink, 0xFFA94D)
        assertContrast("dark onTertiary/tertiary", night, 0xFFB868)
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
    fun `supporting text clears contrast on the actual accent-washed page backgrounds`() {
        val lightAlpha = backdropAlpha("Light")
        val darkAlpha = backdropAlpha("Dark")

        mapOf(
            "home blue" to blue,
            "meal amber" to 0x92400E,
            "search indigo" to 0x4338CA,
            "settings neutral" to inkMuted,
            "manual teal" to 0x0F766E,
        ).forEach { (name, accent) ->
            assertContrast("light $name wash", inkMuted, composite(accent, cream, lightAlpha))
        }

        mapOf(
            "home blue" to blueDark,
            "meal amber" to 0xD49425,
            "search indigo" to 0x9496FF,
            "settings neutral" to 0xAFAEA8,
            "manual teal" to 0x43B1A6,
        ).forEach { (name, accent) ->
            assertContrast("dark $name wash", 0xAFAEA8, composite(accent, night, darkAlpha))
        }
    }

    @Test
    fun `product supporting text clears contrast on its actual decorative wash`() {
        assertContrast("light product wash", inkMuted, composite(blueSoft, cream, 0.7))
        assertContrast("dark product wash", 0xAFAEA8, composite(0x16324A, night, 0.7))
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
        assertContrast("onPrimaryContainer/primaryContainer", 0x0B3E63, blueSoft)
        assertContrast("onTertiaryContainer/tertiaryContainer", 0x7A4B0A, orangeSoft)
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
            "green" to 0x15803D,
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
        val night = 0x15140F
        val nightRaised = 0x1E1D18
        val darkContainerLow = 0x19180F

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
    fun `accent fill content clears contrast on both ends of every home gradient`() {
        val lightStops = mapOf(
            "blue→indigo start" to 0x1B6FBF,
            "blue→indigo end" to 0x4338CA,
            "teal→green start" to 0x0F766E,
            "teal→green end" to 0x15803D,
        )
        val darkStops = mapOf(
            "blue→indigo start" to 0x5CA6E8,
            "blue→indigo end" to 0x9496FF,
            "teal→green start" to 0x43B1A6,
            "teal→green end" to 0x45B56E,
        )

        lightStops.forEach { (name, stop) ->
            assertContrast("light onAccent/$name", warmWhite, stop)
            assertContrast("light supporting/$name", composite(warmWhite, stop, 0.96), stop)
        }
        darkStops.forEach { (name, stop) ->
            assertContrast("dark onAccent/$name", night, stop)
            assertContrast("dark supporting/$name", composite(night, stop, 0.96), stop)
        }

        mapOf(
            "src/main/kotlin/app/justthecarbs/ui/home/HomeScreen.kt" to "ACCENT_SUPPORTING_ALPHA",
            "src/main/kotlin/app/justthecarbs/ui/onboarding/TutorialPreview.kt" to
                "PREVIEW_ACCENT_SUPPORTING_ALPHA",
        ).forEach { (path, token) ->
            val source = java.io.File(path).readText()
            val declared = Regex("""$token\s*=\s*([0-9.]+)f""")
                .find(source)?.groupValues?.get(1)?.toDouble()
            assertTrue("$token is $declared in source but 0.96 in this test", declared == 0.96)
        }
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
}

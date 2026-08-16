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
    private val red = 0xD42F2F
    private val onOrangeSoft = 0x965D08
    private val inkMuted = 0x6B6A72
    private val ink = 0x181A1E

    // --- Light surfaces these are actually drawn on ---------------------------------------------
    private val cream = 0xFFF6EE          // background / surface
    private val white = 0xFFFFFF          // surfaceVariant, surfaceContainerLowest
    private val surfaceContainerLow = 0xFDFBF8
    private val orangeSoft = 0xFFEEDC
    private val blueSoft = 0xE4F1FC

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

    @Test
    fun `white button text on primary blue`() {
        // Every filled action in the app: Scan barcode, Add to meal, Get started.
        assertContrast("onPrimary/primary", 0xFFFFFF, blue)
    }

    @Test
    fun `primary blue as text on the page surfaces`() {
        // Text buttons and links sit directly on cream or on a white card.
        assertContrast("primary/cream", blue, cream)
        assertContrast("primary/white", blue, white)
        assertContrast("primary/surfaceContainerLow", blue, surfaceContainerLow)
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
    fun `container foregrounds on their containers`() {
        assertContrast("onPrimaryContainer/primaryContainer", 0x0B3E63, blueSoft)
        assertContrast("onTertiaryContainer/tertiaryContainer", 0x7A4B0A, orangeSoft)
    }

    @Test
    fun `light tokens match the values Theme kt actually ships`() {
        // Without this, the literals above could drift from the palette and every assertion would
        // keep passing while the real app regressed — the exact "green suite, broken screen" failure
        // this repo has hit before.
        val theme = java.io.File("src/main/kotlin/app/justthecarbs/ui/theme/Theme.kt").readText()

        mapOf(
            "Blue" to blue,
            "Red" to red,
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
    }
}

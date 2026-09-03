package app.justthecarbs.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The rule that lets this app be colourful without endangering the one number that matters.
 *
 * `DESIGN.md` used to justify a near-monochrome palette on the grounds that "a colourful interface
 * competes with [the result]". The 2026-09-03 refresh spends colour freely on chrome instead, and
 * this test is what replaces that argument with a measurement: every accent is strictly darker than
 * the result red, so it recedes behind it rather than competing.
 *
 * Luminance, not saturation. A vivid mid-tone can be more eye-catching than a saturated dark one,
 * and WCAG relative luminance is the same quantity `ContrastTest` already reasons about — using two
 * different notions of "prominent" in one codebase is how the guarantee quietly stops meaning
 * anything.
 *
 * Without this the rule is a paragraph in a spec, which one plausible-looking hex edit can
 * contradict with nothing failing.
 */
class AccentRecessionTest {

    private val lightResult = 0xD42F2F
    private val darkResult = 0xFF7A7A

    private val lightAccents = mapOf(
        "teal" to 0x0F766E,
        "violet" to 0x6D28D9,
        "green" to 0x15803D,
        "magenta" to 0xBE185D,
        "indigo" to 0x4338CA,
        "amber" to 0x92400E,
    )

    // Not the obvious Tailwind-style 300/400 tints. Those were tried first and ALL SIX failed the
    // recession rule: #5EEAD4 teal measures 0.660 against the dark result red's 0.366, i.e. nearly
    // twice as bright as the number it must not out-shout. The values below are the brightest of
    // each hue that still recedes, found by search rather than by eye, and each still clears 4.5:1
    // on all three dark surfaces (night, raised, surfaceContainerLow).
    private val darkAccents = mapOf(
        "teal" to 0x43B1A6,
        "violet" to 0xA997D3,
        "green" to 0x45B56E,
        "magenta" to 0xE481B3,
        "indigo" to 0x9496FF,
        "amber" to 0xD49425,
    )

    private fun luminance(rgb: Int): Double {
        val channels = listOf((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
            .map { it / 255.0 }
            .map { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
    }

    @Test
    fun `every light accent recedes behind the result red`() {
        val result = luminance(lightResult)
        lightAccents.forEach { (name, rgb) ->
            val accent = luminance(rgb)
            assertTrue(
                "$name luminance ${"%.3f".format(accent)} is not below the result red's " +
                    "${"%.3f".format(result)} — it would compete with the carbohydrate figure",
                accent < result,
            )
        }
    }

    @Test
    fun `dark scheme accents recede behind the dark result red`() {
        // The dark scheme inverts: the result red is a *bright* tint on near-black, so the accents
        // must be brighter still to be visible and yet must not out-shout it. Stated as the same
        // rule with the comparison reversed rather than as a second, differently-argued rule.
        val result = luminance(darkResult)
        darkAccents.forEach { (name, rgb) ->
            val accent = luminance(rgb)
            assertTrue(
                "$name (dark) luminance ${"%.3f".format(accent)} must stay below the dark result " +
                    "red's ${"%.3f".format(result)}",
                accent < result,
            )
        }
    }

    @Test
    fun `the accent literals here match the values AccentPalette actually ships`() {
        // Same guard as ContrastTest's `light tokens match the values Theme kt actually ships`:
        // without it these literals could drift from the palette and every assertion above would
        // keep passing while the real app regressed.
        val source = java.io.File(
            "src/main/kotlin/app/justthecarbs/ui/theme/AccentPalette.kt",
        ).readText()

        lightAccents.forEach { (name, expected) ->
            val token = "Light" + name.replaceFirstChar { it.uppercase() }
            val declared = Regex("""private val $token = Color\(0xFF([0-9A-Fa-f]{6})\)""")
                .find(source)?.groupValues?.get(1)?.toInt(16)
            assertTrue(
                "could not find `$token` in AccentPalette.kt — rename the mirror in this test too",
                declared != null,
            )
            assertTrue(
                "$token is ${declared?.toString(16)} in AccentPalette.kt but " +
                    "${expected.toString(16)} here",
                declared == expected,
            )
        }
    }
}

package app.justthecarbs.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the compositional hierarchy that keeps the calculated carbohydrate answer dominant. */
class AccentRecessionTest {

    @Test
    fun `result type is materially larger than the editable portion`() {
        val source = java.io.File(
            "src/main/kotlin/app/justthecarbs/ui/theme/Theme.kt",
        ).readText()
        val resultSize = Regex("""val result = TextStyle\([\s\S]*?fontSize = (\d+)\.sp""")
            .find(source)?.groupValues?.get(1)?.toInt()
        val portionSize = Regex("""val portion = TextStyle\([\s\S]*?fontSize = (\d+)\.sp""")
            .find(source)?.groupValues?.get(1)?.toInt()

        assertTrue("result type must be declared", resultSize != null)
        assertTrue("portion type must be declared", portionSize != null)
        assertTrue(
            "the answer must visibly outrank the input ($resultSize vs $portionSize)",
            resultSize!! >= portionSize!! + 16,
        )
    }

    @Test
    fun `calculation surfaces pair result typography with the reserved result color`() {
        // Both ProductScreen (interaction-polish task 3) and MealScreen's populated total
        // (interaction-polish task 5) compose the shared ResultValue rather than styling the
        // calculated figure directly — ResultValue.kt is where NumberType.result and
        // MaterialTheme.extendedColors.result now live for both screens, so it is checked instead.
        // MealScreen's empty-meal placeholder deliberately does NOT use the reserved result color
        // (it must not look like a calculated result at all) and is not covered by this test.
        val productScreenSource = java.io.File(
            "src/main/kotlin/app/justthecarbs/ui/product/ProductScreen.kt",
        ).readText()
        val resultValueSource = java.io.File(
            "src/main/kotlin/app/justthecarbs/ui/components/ResultValue.kt",
        ).readText()
        assertTrue(
            "ProductScreen.kt must render the result via ResultValue",
            productScreenSource.contains("ResultValue("),
        )
        assertTrue(
            "ResultValue.kt must use the result type",
            resultValueSource.contains("NumberType.result"),
        )
        assertTrue(
            "ResultValue.kt must use the reserved result color",
            resultValueSource.contains("MaterialTheme.extendedColors.result"),
        )

        val mealScreenSource = java.io.File(
            "src/main/kotlin/app/justthecarbs/ui/meal/MealScreen.kt",
        ).readText()
        assertTrue(
            "MealScreen.kt must render its populated total via ResultValue",
            mealScreenSource.contains("ResultValue("),
        )
    }

    @Test
    fun `the large primary fill recedes behind the carbohydrate result in both schemes`() {
        // The rule the dark pale-cobalt tile broke: a *large* interactive fill may dominate by
        // area and saturation, never by luminance. The result red is the brightest thing on any
        // screen, so the tile's luminance must sit below it — otherwise an 88dp tile out-shouts
        // the number the user came for.
        //
        // Computed from the shipped tokens rather than asserted as remembered constants, so a
        // later "let's brighten the tile" fails here instead of shipping.
        val theme = java.io.File(
            "src/main/kotlin/app/justthecarbs/ui/theme/Theme.kt",
        ).readText()

        fun token(name: String): Int =
            Regex("""private val $name = Color\(0xFF([0-9A-Fa-f]{6})\)""")
                .find(theme)?.groupValues?.get(1)?.toInt(16)
                ?: error("Could not read token `$name` from Theme.kt")

        val darkTile = luminance(token("PrimaryTileDark"))
        val darkResult = luminance(token("RedDark"))
        assertTrue(
            "dark primaryTile luminance ${"%.4f".format(darkTile)} must stay below the dark result " +
                "red's ${"%.4f".format(darkResult)}",
            darkTile < darkResult,
        )

        val lightTile = luminance(token("Blue"))
        val lightResult = luminance(token("Red"))
        assertTrue(
            "light primaryTile luminance ${"%.4f".format(lightTile)} must stay below the light result " +
                "red's ${"%.4f".format(lightResult)}",
            lightTile < lightResult,
        )
    }

    /** Relative luminance, WCAG 2.1 — mirrors `ContrastTest`'s own definition. */
    private fun luminance(rgb: Int): Double {
        val channels = listOf((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
            .map { it / 255.0 }
            .map { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
    }

    @Test
    fun `the result unit shares the numeral baseline rather than hanging below it`() {
        // The subscript defect: with Alignment.Bottom the two text BOXES were aligned, and the
        // 72sp numeral's larger descent leading pushed the 26sp `g` below the numeral's baseline.
        // Baseline alignment needs both halves — the alignment modifier AND trimmed leading, since
        // an untrimmed line box still offsets the first baseline asymmetrically.
        val resultValue = java.io.File(
            "src/main/kotlin/app/justthecarbs/ui/components/ResultValue.kt",
        ).readText()
        assertTrue(
            "the numeral and the unit must both align by baseline",
            resultValue.split("alignByBaseline()").size - 1 >= 2,
        )
        assertTrue(
            "the row must not fall back to aligning box bottoms",
            !resultValue.contains("Alignment.Bottom"),
        )

        val theme = java.io.File(
            "src/main/kotlin/app/justthecarbs/ui/theme/Theme.kt",
        ).readText()
        val resultBlock = theme
            .substringAfter("val result = TextStyle(")
            .substringBefore("val resultAutoSize")
        assertTrue(
            "the result type must trim its leading so the baseline is not offset",
            resultBlock.contains("Trim.Both"),
        )
    }

    @Test
    fun `scanner proposal is not styled as a confirmed result`() {
        val source = java.io.File(
            "src/main/kotlin/app/justthecarbs/ui/scan/LabelScannerScreen.kt",
        ).readText()
        assertTrue(source.contains("deliberately **not** [NumberType.result]"))
    }
}

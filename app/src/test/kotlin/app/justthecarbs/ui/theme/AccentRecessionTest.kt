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
    fun `scanner proposal is not styled as a confirmed result`() {
        val source = java.io.File(
            "src/main/kotlin/app/justthecarbs/ui/scan/LabelScannerScreen.kt",
        ).readText()
        assertTrue(source.contains("deliberately **not** [NumberType.result]"))
    }
}

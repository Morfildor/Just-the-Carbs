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
        listOf(
            "src/main/kotlin/app/justthecarbs/ui/product/ProductScreen.kt",
            "src/main/kotlin/app/justthecarbs/ui/meal/MealScreen.kt",
        ).forEach { path ->
            val source = java.io.File(path).readText()
            assertTrue("$path must use the result type", source.contains("NumberType.result"))
            assertTrue(
                "$path must use the reserved result color",
                source.contains("MaterialTheme.extendedColors.result"),
            )
        }
    }

    @Test
    fun `scanner proposal is not styled as a confirmed result`() {
        val source = java.io.File(
            "src/main/kotlin/app/justthecarbs/ui/scan/LabelScannerScreen.kt",
        ).readText()
        assertTrue(source.contains("deliberately **not** [NumberType.result]"))
    }
}

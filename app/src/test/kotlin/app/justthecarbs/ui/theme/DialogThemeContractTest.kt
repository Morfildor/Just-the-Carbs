package app.justthecarbs.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keeps app dialogs on one explicit container/content contract instead of Material fallbacks. */
class DialogThemeContractTest {

    @Test
    fun `every alert dialog consumes the shared visual defaults`() {
        val files = listOf(
            "ui/meal/MealScreen.kt",
            "ui/product/LabelVerificationSheet.kt",
            "ui/product/ProductScreen.kt",
            "ui/product/SaveQuickCalculation.kt",
            "ui/product/VerifyDialog.kt",
            // Manual barcode entry became a bottom sheet in 1.0.8 (ui/scan/ManualBarcodeSheet.kt)
            // and so holds no AlertDialog to check. It is off this list rather than listed with a
            // count of zero, because the list names files that *have* dialogs to constrain.
            "ui/settings/SettingsScreen.kt",
        )

        files.forEach { relative ->
            val source = java.io.File("src/main/kotlin/app/justthecarbs/$relative").readText()
            val dialogs = Regex("""\bAlertDialog\(""").findAll(source).count()
            val sharedOutlines = Regex("""modifier\s*=\s*Modifier\.jtcDialogOutline\(\)""")
                .findAll(source).count()
            assertEquals("$relative has an AlertDialog outside the shared modal contract", dialogs, sharedOutlines)
        }
    }

    @Test
    fun `gallery uses the same raised container contract`() {
        val source = java.io.File(
            "src/main/kotlin/app/justthecarbs/ui/components/ProductGalleryDialog.kt",
        ).readText()
        assertTrue(source.contains(".jtcDialogOutline()"))
        assertTrue(source.contains("color = JtcDialogDefaults.containerColor"))
        assertTrue(source.contains("tonalElevation = JtcDialogDefaults.tonalElevation"))
    }
}

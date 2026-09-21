package app.justthecarbs.ui

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.VerificationStatus
import app.justthecarbs.ui.product.RENAME_FIELD_TAG
import app.justthecarbs.ui.product.RenameProductDialog
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.math.BigDecimal

/**
 * Review captures of the *Rename on this device* editor, for human inspection (1.0.8).
 *
 * ## Why this exists rather than an assertion
 *
 * `RenameProductScreenTest` asserts what can honestly be asserted about this dialog's layout: that
 * every action is displayed, inside the dialog, non-overlapping, and keeps a 48dp touch target at
 * 1.8x. Those are the properties a machine can judge.
 *
 * What it cannot judge is whether the result *reads* well — whether the wrapped action row looks
 * deliberate or broken, whether "Remove custom name" beside "Cancel" reads as two peers when one is
 * destructive, whether the dialog is crowded against a long product name. This codebase has
 * recorded several times that a green suite coexists with a screen nobody would ship, and that only
 * a screenshot found it.
 *
 * So this writes images and asserts almost nothing. It is evidence for a person, in the same spirit
 * as `TutorialVisualTest`.
 *
 * ## Running it
 *
 * ```
 * adb shell am instrument -w -r -e class app.justthecarbs.ui.RenameProductVisualTest \
 *   app.justthecarbs.debug.test/androidx.test.runner.AndroidJUnitRunner
 * adb pull /sdcard/Android/data/app.justthecarbs.debug/files/rename-qa
 * ```
 *
 * Captures are written to the debug app's external-files directory, which needs no permission and
 * goes away with the app.
 */
class RenameProductVisualTest {

    @get:Rule
    val compose = createComposeRule()

    private val longName =
        "Griekse yoghurt met honing, walnoten en een bijzonder lange productnaam 0% vet"

    private fun product(alias: String?, name: String) = Product(
        barcode = "8712100849060",
        name = name,
        carbsPer100 = BigDecimal("41.5"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
        localAlias = alias,
    )

    private fun capture(
        label: String,
        alias: String? = "Breakfast bread",
        name: String = "AH Volkoren Tarwebrood 800g",
        fontScale: Float = 1f,
        typeIntoField: Boolean = false,
    ) {
        compose.setContent {
            val p = remember { product(alias, name) }
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                JustTheCarbsTheme {
                    RenameProductDialog(
                        currentDisplayName = p.displayName,
                        existingAlias = p.localAlias,
                        onSave = {},
                        onRemove = {},
                        onDismiss = {},
                    )
                }
            }
        }
        // Typing raises the soft keyboard on a real run, which is the "keyboard open" case; in the
        // harness it also puts the field in its focused state, which is what the capture shows.
        if (typeIntoField) {
            compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput("Ontbijt")
        }
        compose.waitForIdle()
        // The dialog animates in. `waitForIdle` returns once composition has settled, which is
        // before the enter transition has finished drawing — a capture taken then is a washed-out
        // mid-animation frame, which is useless as review evidence. Measured: this is what the
        // difference between a faint dialog and a readable one costs.
        Thread.sleep(600)

        // The whole device window, so the capture shows the dialog *in place* — its scrim, its
        // margins and how much of the screen it takes. `onRoot` is ambiguous here (a dialog is its
        // own window), and capturing only the dialog's own node would hide exactly the crowding a
        // reviewer is looking for.
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "rename-qa",
        ).apply { mkdirs() }
        File(dir, "$label.png").outputStream().use { out ->
            image.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun defaultScale() = capture("rename-1.0x")

    @Test
    fun largeScale() = capture("rename-1.8x", fontScale = 1.8f)

    @Test
    fun largeScaleWithALongProductName() =
        capture("rename-1.8x-longname", name = longName, fontScale = 1.8f)

    @Test
    fun defaultScaleWithNoAliasToRemove() = capture("rename-1.0x-no-alias", alias = null)

    @Test
    fun largeScaleWithTheFieldInUse() =
        capture("rename-1.8x-typing", fontScale = 1.8f, typeIntoField = true)
}

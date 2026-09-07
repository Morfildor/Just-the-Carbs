package app.justthecarbs.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.ocr.OcrBox
import app.justthecarbs.ui.scan.VerificationScreen
import app.justthecarbs.ui.scan.VerificationScreenMode
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/**
 * The declared-serving confirmation screen shows the PRINTED pair primarily, and the
 * normalized-for-storage figure only as secondary context — never the reverse.
 *
 * ## The defect this closes
 *
 * A label printing `6 g carbohydrate / 18 g serving` normalizes internally to `33.3 g / 100 g` for
 * storage and calculation. Before this fix, [VerificationScreen]'s [VerificationScreenMode
 * .ScaleUnresolved] state showed only that normalized figure — a number the user cannot compare
 * against anything printed on the package, on the one screen whose entire purpose is that
 * comparison. The internal `BigDecimal` arithmetic is unaffected either way; this is a display-only
 * change, pinned here rather than as a JVM test because rendered text order is a Compose concern.
 *
 * **Instrumented: needs a device or emulator.**
 */
class VerificationScreenDeclaredServingTest {

    @get:Rule
    val rule = createComposeRule()

    private fun bitmap(): Bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)

    private fun showScreen(
        value: BigDecimal = BigDecimal("33.3"),
        basis: NutritionBasis = NutritionBasis.PER_100_G,
        printedAmount: BigDecimal? = BigDecimal("6"),
        printedBasisLabel: String? = "18 g serving",
    ) {
        rule.setContent {
            JustTheCarbsTheme {
                VerificationScreen(
                    bitmap = bitmap(),
                    value = value,
                    basis = basis,
                    rowText = "Total Carb. 6 g",
                    rowInSourceSpace = OcrBox(0, 0, 100, 40),
                    mode = VerificationScreenMode.ScaleUnresolved,
                    onConfirm = { _, _ -> },
                    onReject = {},
                    onRetake = {},
                    printedAmount = printedAmount,
                    printedBasisLabel = printedBasisLabel,
                )
            }
        }
    }

    @Test
    fun aDeclaredServingReadingShowsThePrintedPairAsThePrimaryLine() {
        showScreen()

        rule.onNodeWithText("6 g per 18 g serving").assertIsDisplayed()
    }

    @Test
    fun aDeclaredServingReadingShowsTheNormalizedFigureAsSecondaryContext() {
        showScreen()

        rule.onNodeWithText("Equivalent to 33.3 g per 100 g").assertIsDisplayed()
    }

    /**
     * The Korean-sauce shape from a real per-ml basis: the printed pair must render with the
     * correct unit, not silently default to grams.
     */
    @Test
    fun aDeclaredServingBasisInMillilitresShowsItsOwnPrintedUnit() {
        showScreen(
            value = BigDecimal("0.5"),
            basis = NutritionBasis.PER_100_ML,
            printedAmount = BigDecimal("1.3"),
            printedBasisLabel = "250 ml",
        )

        rule.onNodeWithText("1.3 g per 250 ml").assertIsDisplayed()
        rule.onNodeWithText("Equivalent to 0.5 g per 100 ml").assertIsDisplayed()
    }

    /**
     * The ordinary per-100 scale-unresolved case — no declared serving at all — must be entirely
     * unaffected: one line, exactly as before this change. This is the negative control proving the
     * new display path is reached only when it should be.
     */
    @Test
    fun anOrdinaryScaleUnresolvedReadingShowsOnlyOneLine() {
        showScreen(
            value = BigDecimal("41"),
            basis = NutritionBasis.PER_100_ML,
            printedAmount = null,
            printedBasisLabel = null,
        )

        rule.onNodeWithText("41 g per 100 ml").assertIsDisplayed()
        rule.onNodeWithText("Equivalent to", substring = true).assertDoesNotExist()
    }
}

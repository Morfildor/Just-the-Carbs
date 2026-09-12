package app.justthecarbs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.ThemeChoice
import app.justthecarbs.domain.VerificationStatus
import app.justthecarbs.ui.manual.ManualEntryScreen
import app.justthecarbs.ui.manual.ManualEntryUiState
import app.justthecarbs.ui.product.ProductScreen
import app.justthecarbs.ui.product.ProductUiState
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import java.math.BigDecimal
import org.junit.Rule
import org.junit.Test

/**
 * Rendered-state checks for the visual refinement pass — real device composition, not just token
 * arithmetic. `ContrastTest` (JVM) already pins the palette values these draw from; this class
 * checks the class of defect a JVM test cannot see: whether a component actually fits and renders
 * on real hardware at the font scales/widths the app is required to survive.
 */
class ThemeRefinementVisualTest {

    @get:Rule
    val compose = createComposeRule()

    private fun product(name: String) = Product(
        barcode = "8712100849060",
        name = name,
        carbsPer100 = BigDecimal("48.2"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
    )

    /**
     * ProductTopBar deliberately kept its own two-line title support rather than adopting
     * JtcTopBar's hard `maxLines = 1` (see the KDoc on `ProductTopBar` in ProductScreen.kt). This
     * is the case that decision exists for: a long name, a narrow 320dp screen, and 2x font scale
     * — the exact combination that clipped mid-glyph before the countable-portions pass fixed it,
     * and the combination a shared-component migration risked reopening.
     */
    @Test
    fun productTopBarShowsALongTwoLineNameAtLargeFontScaleOnANarrowScreen() {
        val longName = "Volkoren Speltbrood met Zonnebloempitten en Lijnzaad Extra Groot"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                JustTheCarbsTheme(themeChoice = ThemeChoice.LIGHT) {
                    Box(Modifier.width(320.dp).fillMaxHeight()) {
                        ProductScreen(
                            state = ProductUiState(
                                loading = false,
                                product = product(longName),
                                portionText = "",
                                result = null,
                                barcode = "8712100849060",
                            ),
                            settings = AppSettings(),
                            onPortionChanged = {},
                            onAdjust = {},
                            onSetPortion = {},
                            onToggleFavorite = {},
                            onBack = {},
                            onVerify = {},
                            onDismissVerify = {},
                            onConfirmVerification = { _, _, _ -> },
                            onResetOnline = {},
                            onScanLabel = {},
                            onEnterManually = {},
                            onRetry = {},
                        )
                    }
                }
            }
        }

        // The title must still be on screen (not clipped out of the composition) at the width and
        // font scale where the bug was originally found.
        compose.onNodeWithText(longName, substring = true).assertIsDisplayed()
    }

    @Test
    fun productTopBarShowsALongTwoLineNameAtLargeFontScaleInDark() {
        val longName = "Volkoren Speltbrood met Zonnebloempitten en Lijnzaad Extra Groot"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                JustTheCarbsTheme(themeChoice = ThemeChoice.DARK) {
                    Box(Modifier.width(320.dp).fillMaxHeight()) {
                        ProductScreen(
                            state = ProductUiState(
                                loading = false,
                                product = product(longName),
                                portionText = "",
                                result = null,
                                barcode = "8712100849060",
                            ),
                            settings = AppSettings(),
                            onPortionChanged = {},
                            onAdjust = {},
                            onSetPortion = {},
                            onToggleFavorite = {},
                            onBack = {},
                            onVerify = {},
                            onDismissVerify = {},
                            onConfirmVerification = { _, _, _ -> },
                            onResetOnline = {},
                            onScanLabel = {},
                            onEnterManually = {},
                            onRetry = {},
                        )
                    }
                }
            }
        }

        compose.onNodeWithText(longName, substring = true).assertIsDisplayed()
    }

    /**
     * The Save button's disabled state (blank name/carbs -> canSave false) must still render its
     * label legibly in both themes — this is the control whose disabled colours were accidentally
     * coupled to the filled-button foreground (ManualEntryScreen.kt, fixed this pass).
     */
    @Test
    fun disabledSaveButtonRendersInLight() = disabledSaveButtonRenders(ThemeChoice.LIGHT)

    @Test
    fun disabledSaveButtonRendersInDark() = disabledSaveButtonRenders(ThemeChoice.DARK)

    private fun disabledSaveButtonRenders(theme: ThemeChoice) {
        compose.setContent {
            JustTheCarbsTheme(themeChoice = theme) {
                Box(Modifier.width(400.dp).fillMaxHeight()) {
                    ManualEntryScreen(
                        state = ManualEntryUiState(name = "", carbsPer100 = ""),
                        onNameChanged = {},
                        onCarbsChanged = {},
                        onBasisChanged = {},
                        onPackageChanged = {},
                        onSave = {},
                        onBack = {},
                    )
                }
            }
        }

        val saveLabel = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(app.justthecarbs.R.string.manual_save)
        compose.onNodeWithText(saveLabel).assertIsDisplayed()
    }
}

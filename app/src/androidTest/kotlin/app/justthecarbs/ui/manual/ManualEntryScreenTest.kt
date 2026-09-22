package app.justthecarbs.ui.manual

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Rule
import org.junit.Test

class ManualEntryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun invalidPackageSizeRendersFieldErrorTextAndSemantics() {
        compose.setContent {
            JustTheCarbsTheme {
                ManualEntryScreen(
                    state = ManualEntryUiState(
                        name = "Bread",
                        carbsPer100 = "41.2",
                        packageAmount = "0",
                        packageError = true,
                    ),
                    onNameChanged = {},
                    onCarbsChanged = {},
                    onBasisChanged = {},
                    onPackageChanged = {},
                    onSave = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText(
            "Enter a package size greater than zero, or leave it blank.",
        ).assertIsDisplayed()
        compose.onNode(
            hasSetTextAction() and hasText("0"),
            useUnmergedTree = true,
        ).assert(
            SemanticsMatcher("has error semantics") {
                it.config.contains(SemanticsProperties.Error)
            },
        )
    }
}

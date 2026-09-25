package app.justthecarbs.ui.manual

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.R
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
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
                    arrivedWithCarbs = false,
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

    // --- Keyboard flow (UX polish, wave 2): Next walks name -> carbs -> package, Done on the last
    // field is the same submit path as the Save button, and a blank form opens ready to type. ---

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val nameLabel get() = context.getString(R.string.manual_name)
    private val carbsLabel get() = context.getString(R.string.manual_carbs, NutritionBasis.PER_100_G.unitLabel)
    private val packageLabel get() = context.getString(R.string.manual_package)

    private fun showForm(
        state: ManualEntryUiState = ManualEntryUiState(),
        arrivedWithCarbs: Boolean = false,
        onSave: () -> Unit = {},
    ) {
        compose.setContent {
            JustTheCarbsTheme {
                ManualEntryScreen(
                    state = state,
                    onNameChanged = {},
                    onCarbsChanged = {},
                    onBasisChanged = {},
                    onPackageChanged = {},
                    onSave = onSave,
                    onBack = {},
                    arrivedWithCarbs = arrivedWithCarbs,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aBlankFormOpensWithTheNameFieldReadyToType() {
        showForm()

        compose.onNodeWithText(nameLabel).assertIsFocused()
    }

    @Test
    fun arrivingWithAScannedFigureDoesNotClaimTheNameField() {
        // Blank state, as the real first frame is: the carried figure lands a frame later, once the
        // route's `start` has run. The decision must come from the route, not from this state.
        showForm(ManualEntryUiState(), arrivedWithCarbs = true)

        compose.onNodeWithText(nameLabel).assertIsNotFocused()
    }

    @Test
    fun nextOnTheNameMovesToTheCarbsField() {
        showForm()
        compose.onNodeWithText(nameLabel).performClick()

        compose.onNodeWithText(nameLabel).performImeAction()

        compose.onNodeWithText(carbsLabel).assertIsFocused()
    }

    @Test
    fun nextOnTheCarbsMovesToThePackageField() {
        showForm()
        compose.onNodeWithText(carbsLabel).performClick()

        compose.onNodeWithText(carbsLabel).performImeAction()

        compose.onNodeWithText(packageLabel).assertIsFocused()
    }

    @Test
    fun doneOnThePackageFieldSavesAFormThatCanBeSaved() {
        var saves = 0
        showForm(ManualEntryUiState(name = "Bread", carbsPer100 = "41.2"), onSave = { saves++ })
        compose.onNodeWithText(packageLabel).performClick()

        compose.onNodeWithText(packageLabel).performImeAction()

        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test
    fun doneOnThePackageFieldOfAnIncompleteFormOnlyPutsTheKeyboardAway() {
        var saves = 0
        showForm(ManualEntryUiState(name = "", carbsPer100 = "41.2"), onSave = { saves++ })
        compose.onNodeWithText(packageLabel).performClick()

        compose.onNodeWithText(packageLabel).performImeAction()

        compose.runOnIdle { assertEquals("Done must not bypass the disabled Save", 0, saves) }
        compose.onNodeWithText(packageLabel).assertIsNotFocused()
    }

    /**
     * A scanned figure is selected when its field gains focus, including when a tap on its digits
     * brings the user back to it: that tap also places a caret, which used to undo the selection so
     * the next digit was inserted into the figure (2026-09-25 review).
     */
    @Test
    fun aTapBackOntoAScannedFigureStillReplacesIt() {
        var carbs by mutableStateOf("48")
        compose.setContent {
            JustTheCarbsTheme {
                ManualEntryScreen(
                    state = ManualEntryUiState(carbsPer100 = carbs),
                    onNameChanged = {},
                    onCarbsChanged = { carbs = it },
                    onBasisChanged = {},
                    onPackageChanged = {},
                    onSave = {},
                    onBack = {},
                    arrivedWithCarbs = true,
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(nameLabel).performClick()

        // On the 4: the text starts after the field's 16dp inner padding.
        compose.onNode(hasSetTextAction() and hasText(carbsLabel)).performTouchInput {
            click(Offset(20.dp.toPx(), centerY))
        }
        compose.onNode(hasSetTextAction() and hasText(carbsLabel)).performTextInput("5")

        compose.runOnIdle { assertEquals("5", carbs) }
    }
}

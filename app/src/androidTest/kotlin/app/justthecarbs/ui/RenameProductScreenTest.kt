package app.justthecarbs.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert as assertNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.VerificationStatus
import app.justthecarbs.ui.product.RENAME_CANCEL_TAG
import app.justthecarbs.ui.product.RENAME_FIELD_TAG
import app.justthecarbs.ui.product.RENAME_REMOVE_TAG
import app.justthecarbs.ui.product.RENAME_SAVE_TAG
import app.justthecarbs.ui.product.RenameProductDialog
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/**
 * The *Rename on this device* editor (1.0.8).
 *
 * Drives the real dialog rather than asserting on state, because everything worth checking here is
 * a property of the composable: what the field opens on, when Save is live, whether Done agrees
 * with the button, and whether *Remove custom name* appears only when there is a name to remove.
 */
class RenameProductScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun product(alias: String? = null) = Product(
        barcode = "8712100849060",
        name = "AH Volkoren Tarwebrood 800g",
        carbsPer100 = BigDecimal("41.5"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
        localAlias = alias,
    )

    private class Result {
        var saved: String? = null
        var removed = false
        var dismissed = false
    }

    private fun showEditor(alias: String? = null): Result {
        val result = Result()
        compose.setContent {
            val product = remember { product(alias) }
            JustTheCarbsTheme {
                RenameProductDialog(
                    currentDisplayName = product.displayName,
                    existingAlias = product.localAlias,
                    onSave = { result.saved = it },
                    onRemove = { result.removed = true },
                    onDismiss = { result.dismissed = true },
                )
            }
        }
        return result
    }

    // ---- pre-fill --------------------------------------------------------------------------------

    @Test
    fun theFieldIsEmptyForAProductWithNoAlias() {
        showEditor()
        compose.onNodeWithTag(RENAME_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(RENAME_FIELD_TAG).assertEditableTextIs("")
    }

    @Test
    fun theFieldPreFillsTheExistingAlias() {
        showEditor(alias = "Breakfast bread")
        // Reopening shows what is in force, rather than an empty box the user must retype to keep
        // most of what they already chose.
        compose.onNodeWithTag(RENAME_FIELD_TAG).assertEditableTextIs("Breakfast bread")
    }

    @Test
    fun theCurrentNameIsVisibleWhileChoosing() {
        showEditor()
        // The dialog covers the title it is renaming, so it restates it.
        compose.onNodeWithText("AH Volkoren Tarwebrood 800g", substring = true).assertIsDisplayed()
    }

    @Test
    fun theEditorSaysTheNameIsLocal() {
        showEditor()
        compose.onNodeWithText("Only you see this name.", substring = true).assertIsDisplayed()
    }

    // ---- saving ----------------------------------------------------------------------------------

    @Test
    fun aTypedNameCanBeSaved() {
        val result = showEditor()

        compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput("Breakfast bread")
        compose.onNodeWithTag(RENAME_SAVE_TAG).assertIsEnabled().performClick()

        assertEquals("Breakfast bread", result.saved)
    }

    @Test
    fun saveIsDisabledUntilSomethingIsTyped() {
        showEditor()
        // Blank cannot create an alias, and the control says so by being unavailable rather than by
        // accepting the tap and doing nothing.
        compose.onNodeWithTag(RENAME_SAVE_TAG).assertIsNotEnabled()
    }

    @Test
    fun saveIsDisabledForWhitespaceAlone() {
        showEditor()
        compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput("   ")
        compose.onNodeWithTag(RENAME_SAVE_TAG).assertIsNotEnabled()
    }

    @Test
    fun saveIsDisabledWhenTheNameHasNotChanged() {
        showEditor(alias = "Breakfast bread")
        // Nothing to do, so the button does not offer to do it.
        compose.onNodeWithTag(RENAME_SAVE_TAG).assertIsNotEnabled()
    }

    @Test
    fun saveBecomesAvailableOnceTheNameDiffers() {
        val result = showEditor(alias = "Breakfast bread")

        compose.onNodeWithTag(RENAME_FIELD_TAG).performTextReplacement("Toast bread")
        compose.onNodeWithTag(RENAME_SAVE_TAG).assertIsEnabled().performClick()

        assertEquals("Toast bread", result.saved)
    }

    @Test
    fun surroundingWhitespaceIsTrimmedBeforeSaving() {
        val result = showEditor()

        compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput("  Breakfast bread  ")
        compose.onNodeWithTag(RENAME_SAVE_TAG).performClick()

        assertEquals("Breakfast bread", result.saved)
    }

    @Test
    fun theNameIsCappedAtTheSameLimitStorageEnforces() {
        val result = showEditor()

        compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput("x".repeat(200))
        compose.onNodeWithTag(RENAME_SAVE_TAG).performClick()

        // The field cannot accept a name storage would silently shorten, so what the user sees is
        // what gets stored.
        assertEquals(Product.MAX_LOCAL_ALIAS_LENGTH, result.saved?.length)
    }

    // ---- IME Done --------------------------------------------------------------------------------

    @Test
    fun imeDoneSavesAValidName() {
        val result = showEditor()

        compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput("Breakfast bread")
        compose.onNodeWithTag(RENAME_FIELD_TAG).performImeAction()

        assertEquals("Breakfast bread", result.saved)
    }

    @Test
    fun imeDoneDoesNothingForABlankName() {
        val result = showEditor()

        compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput("   ")
        compose.onNodeWithTag(RENAME_FIELD_TAG).performImeAction()

        // Done and Save must agree about what is valid. A Done that saved what the button refuses
        // is the kind of divergence nobody notices until it has stored something odd.
        assertNull(result.saved)
    }

    @Test
    fun imeDoneDoesNothingWhenTheNameHasNotChanged() {
        val result = showEditor(alias = "Breakfast bread")

        compose.onNodeWithTag(RENAME_FIELD_TAG).performImeAction()

        assertNull(result.saved)
    }

    // ---- removing ---------------------------------------------------------------------------------

    @Test
    fun removeIsOfferedOnlyWhenThereIsANameToRemove() {
        showEditor()
        compose.onAllNodesWithTagCount(RENAME_REMOVE_TAG, 0)
    }

    @Test
    fun removeClearsTheCustomName() {
        val result = showEditor(alias = "Breakfast bread")

        compose.onNodeWithTag(RENAME_REMOVE_TAG).assertIsDisplayed().performClick()

        assertTrue(result.removed)
        assertNull("removing is not a save", result.saved)
    }

    @Test
    fun cancelIsOfferedWhenThereIsNoNameToRemove() {
        val result = showEditor()

        compose.onNodeWithTag(RENAME_CANCEL_TAG).assertIsDisplayed().performClick()

        assertTrue(result.dismissed)
    }

    @Test
    fun cancelIsStillOfferedAlongsideRemoveWhenAnAliasExists() {
        // Cancel must not be replaced by Remove custom name: the no-op escape has to stay reachable
        // whether or not there is something to remove, exactly like tapping outside or back already
        // are.
        val result = showEditor(alias = "Breakfast bread")

        compose.onNodeWithTag(RENAME_CANCEL_TAG).assertIsDisplayed().performClick()

        assertTrue(result.dismissed)
        assertTrue("cancelling is not a removal", !result.removed)
    }

    @Test
    fun removingDoesNotAlsoDismiss() {
        // Save, Cancel and Remove must each invoke only their own callback — tapping one must not
        // also fire another.
        val result = showEditor(alias = "Breakfast bread")

        compose.onNodeWithTag(RENAME_REMOVE_TAG).performClick()

        assertTrue(result.removed)
        assertTrue("removing is not a cancel", !result.dismissed)
        assertNull("removing is not a save", result.saved)
    }

    @Test
    fun savingDoesNotAlsoDismissOrRemove() {
        val result = showEditor()

        compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput("Breakfast bread")
        compose.onNodeWithTag(RENAME_SAVE_TAG).performClick()

        assertEquals("Breakfast bread", result.saved)
        assertTrue("saving is not a cancel", !result.dismissed)
        assertTrue("saving is not a removal", !result.removed)
    }

    // ---- accessibility ----------------------------------------------------------------------------

    @Test
    fun theFieldIsLabelled() {
        showEditor()
        // A screen reader reaching the box is told what it is for, not just that it is a text field.
        compose.onNodeWithText("Your name for it").assertIsDisplayed()
    }

    @Test
    fun everyControlCarriesItsOwnText() {
        showEditor(alias = "Breakfast bread")
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithText("Remove custom name").assertIsDisplayed()
        compose.onNodeWithText("Rename on this device").assertIsDisplayed()
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(
        tag: String,
        expected: Int,
    ) {
        val matcher = SemanticsMatcher.expectValue(SemanticsProperties.TestTag, tag)
        assertEquals(expected, onAllNodes(matcher).fetchSemanticsNodes().size)
    }

    /**
     * Asserts the field's own editable contents.
     *
     * Matched on [SemanticsProperties.EditableText] rather than via `assertTextEquals`, which also
     * sees the label and the placeholder — on a labelled `OutlinedTextField` that would pass for an
     * empty field whose *label* happened to match, which is the opposite of what these tests check.
     * The same idiom `SearchShortcutFocusTest` already uses.
     */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertEditableTextIs(expected: String) {
        // `assertNode`, not the bare `assert` — that resolves to Kotlin's own assert(Boolean) and
        // fails to compile against a matcher.
        assertNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(expected)))
    }
}

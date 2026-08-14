package app.justthecarbs.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.justthecarbs.domain.LabelVerdict
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.ui.product.LabelVerificationDialog
import app.justthecarbs.ui.product.VERIFY_CONFIRM_TAG
import app.justthecarbs.ui.product.VERIFY_USE_PACKAGE_TAG
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/**
 * Comparing a scanned label against the value in use (development-pass brief §12, spec §7).
 *
 * The behaviours worth pinning here are all about restraint: the app must not accept a matching
 * reading on its own, must not pick a side on a mismatch, and must not offer to apply a value it
 * cannot compare at all.
 *
 * **Instrumented: needs a device or emulator.**
 */
class LabelVerificationScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showVerdict(
        verdict: LabelVerdict,
        basis: NutritionBasis = NutritionBasis.PER_100_G,
        currentIsUserAuthored: Boolean = false,
        onConfirmMatch: () -> Unit = {},
        onUsePackageValue: (BigDecimal) -> Unit = {},
        onEditDetected: (BigDecimal) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        compose.setContent {
            JustTheCarbsTheme {
                LabelVerificationDialog(
                    verdict = verdict,
                    productBasis = basis,
                    currentIsUserAuthored = currentIsUserAuthored,
                    onConfirmMatch = onConfirmMatch,
                    onUsePackageValue = onUsePackageValue,
                    onEditDetected = onEditDetected,
                    onRescan = {},
                    onDismiss = onDismiss,
                )
            }
        }
    }

    private fun match(current: String = "48.2", detected: String = "48.2") =
        LabelVerdict.Match(BigDecimal(current), BigDecimal(detected))

    private fun mismatch(current: String = "48.2", detected: String = "47.3") =
        LabelVerdict.Mismatch(BigDecimal(current), BigDecimal(detected))

    // ---- matching values -----------------------------------------------------------------------

    @Test
    fun aMatchShowsBothFiguresAndSaysTheyAgree() {
        showVerdict(match())

        compose.onNodeWithText("ONLINE").assertIsDisplayed()
        compose.onNodeWithText("PACKAGE").assertIsDisplayed()
        compose.onNodeWithText("✓ Values match").assertIsDisplayed()
    }

    /**
     * The central rule of §12. A matching reading is still only a camera frame, so the app offers a
     * Confirm and waits — it never records "the user checked the package" on their behalf.
     */
    @Test
    fun aMatchStillRequiresAnExplicitConfirmation() {
        var confirmed = false
        showVerdict(match(), onConfirmMatch = { confirmed = true })

        compose.onNodeWithTag(VERIFY_CONFIRM_TAG).assertIsDisplayed()
        assertEquals("a match must not self-accept", false, confirmed)

        compose.onNodeWithTag(VERIFY_CONFIRM_TAG).performClick()
        assertEquals(true, confirmed)
    }

    /** A user-authored value is labelled CURRENT, not ONLINE — it never came from the database. */
    @Test
    fun aUserAuthoredValueIsNotLabelledAsAnOnlineValue() {
        showVerdict(match(), currentIsUserAuthored = true)

        compose.onNodeWithText("CURRENT").assertIsDisplayed()
        compose.onNodeWithText("ONLINE").assertDoesNotExist()
    }

    // ---- mismatching values --------------------------------------------------------------------

    @Test
    fun aMismatchShowsBothFiguresPlainly() {
        showVerdict(mismatch())

        compose.onNodeWithText("48.2 g / 100 g").assertIsDisplayed()
        compose.onNodeWithText("47.3 g / 100 g").assertIsDisplayed()
        compose.onNodeWithText("These do not match. Which is right?").assertIsDisplayed()
    }

    /** The app presents the choice; it never resolves the conflict itself (§12). */
    @Test
    fun aMismatchAppliesNothingUntilTheUserChooses() {
        var applied: BigDecimal? = null
        showVerdict(mismatch(), onUsePackageValue = { applied = it })

        assertEquals("a mismatch must not auto-apply either value", null, applied)

        compose.onNodeWithTag(VERIFY_USE_PACKAGE_TAG).performClick()
        assertEquals(0, BigDecimal("47.3").compareTo(applied!!))
    }

    @Test
    fun aMismatchOffersEditingTheDetectedValue() {
        var edited: BigDecimal? = null
        showVerdict(mismatch(), onEditDetected = { edited = it })

        compose.onNodeWithText("Edit detected value").performClick()

        assertEquals(0, BigDecimal("47.3").compareTo(edited!!))
    }

    /** Cancelling is a real option and changes nothing at all. */
    @Test
    fun aMismatchCanBeDismissedWithoutChoosingEither() {
        var dismissed = false
        var applied: BigDecimal? = null
        showVerdict(mismatch(), onUsePackageValue = { applied = it }, onDismiss = { dismissed = true })

        compose.onNodeWithText("Cancel").performClick()

        assertEquals(true, dismissed)
        assertEquals(null, applied)
    }

    // ---- incomparable readings -----------------------------------------------------------------

    /**
     * A per-100-g label against a per-100-ml product is not a mismatch to be resolved: the figures
     * measure different things, and reconciling them needs a density the app does not have (§17).
     * So *Use package value* is not offered at all — applying it would be wrong, not merely
     * unverified.
     */
    @Test
    fun anIncomparableBasisExplainsItselfAndOffersNoWayToApplyTheValue() {
        showVerdict(
            LabelVerdict.BasisMismatch(
                current = BigDecimal("9.4"),
                currentBasis = NutritionBasis.PER_100_ML,
                detected = BigDecimal("9.6"),
                detectedBasis = NutritionBasis.PER_100_G,
            ),
            basis = NutritionBasis.PER_100_ML,
        )

        compose.onNodeWithText("this app does not convert", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(VERIFY_USE_PACKAGE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(VERIFY_CONFIRM_TAG).assertDoesNotExist()
    }
}

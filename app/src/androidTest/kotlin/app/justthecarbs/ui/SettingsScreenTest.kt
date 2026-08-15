package app.justthecarbs.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.justthecarbs.BuildConfig
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.ui.settings.SettingsScreen
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Rule
import org.junit.Test

/**
 * Settings' About section (this hardening pass): a Privacy Policy entry must be present, labelled
 * exactly "Privacy Policy", and reachable the same way every other About action already is —
 * [SettingsAction], not a new navigation paradigm.
 *
 * **Instrumented: needs a device or emulator.**
 */
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show() {
        compose.setContent {
            JustTheCarbsTheme {
                SettingsScreen(
                    settings = AppSettings(),
                    onThemeChanged = {},
                    onResultStyleChanged = {},
                    onHapticsChanged = {},
                    onClearRecents = {},
                    onClearProducts = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun privacyPolicyRowIsPresentInAbout() {
        show()

        compose.onNodeWithText("Privacy Policy").assertIsDisplayed()
    }

    /**
     * There is no fake browser to intercept the intent in this test harness, so this only pins that
     * tapping the row does not crash the screen — [SettingsScreen]'s own try/catch around
     * `LocalUriHandler.openUri` is what makes an unavailable browser graceful, exercised here by
     * whatever activity (or lack of one) the test device actually resolves the URL to.
     */
    @Test
    fun tappingPrivacyPolicyDoesNotCrashTheScreen() {
        show()

        compose.onNodeWithText("Privacy Policy").performClick()

        compose.onNodeWithText("Privacy Policy").assertIsDisplayed()
    }

    @Test
    fun theConfiguredUrlIsTheOneCommittedForThePlayListing() {
        // Pins the exact production URL against accidental drift — same URL given to Play Console
        // (see branding.gradle.kts). A change here should be a deliberate owner decision, not a typo.
        assert(BuildConfig.PRIVACY_POLICY_URL == "https://morfildor.github.io/Just-the-Carbs/privacy-policy.html") {
            "unexpected PRIVACY_POLICY_URL: ${BuildConfig.PRIVACY_POLICY_URL}"
        }
    }
}

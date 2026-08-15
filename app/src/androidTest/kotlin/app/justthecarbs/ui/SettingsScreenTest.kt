package app.justthecarbs.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
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

    /**
     * Records what the screen asked the platform to open, instead of opening it.
     *
     * [SettingsScreen] navigates through Compose's [LocalUriHandler] rather than firing an Intent
     * itself, so overriding that CompositionLocal is the natural seam: no real browser launches, and
     * the assertion can be about the exact URL rather than merely "something was resolved".
     */
    private class RecordingUriHandler(private val failWith: Exception? = null) : UriHandler {
        val opened = mutableListOf<String>()

        override fun openUri(uri: String) {
            opened += uri
            failWith?.let { throw it }
        }
    }

    private fun show(uriHandler: UriHandler = RecordingUriHandler()) {
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
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
    }

    @Test
    fun privacyPolicyRowIsPresentInAbout() {
        show()

        compose.onNodeWithText("Privacy Policy").assertIsDisplayed()
    }

    /**
     * Tapping the row opens the committed policy URL and leaves the screen intact.
     *
     * The [UriHandler] is stubbed, so no real browser launches. That is what makes this test stable:
     * previously it drove the device's actual browser, which backgrounded the test activity and left
     * Compose with no hierarchy to assert against on any emulator image that ships Chrome.
     */
    @Test
    fun tappingPrivacyPolicyOpensTheCommittedUrlAndKeepsTheScreen() {
        val uriHandler = RecordingUriHandler()
        show(uriHandler)

        compose.onNodeWithText("Privacy Policy").performClick()

        assert(uriHandler.opened == listOf(BuildConfig.PRIVACY_POLICY_URL)) {
            "expected exactly the policy URL to be opened, got ${uriHandler.opened}"
        }
        compose.onNodeWithText("Privacy Policy").assertIsDisplayed()
    }

    /**
     * The no-browser fallback. [SettingsScreen] catches the failure and shows the URL as copyable
     * text rather than crashing — the branch that a device *with* a browser can never reach, and so
     * was never actually exercised before the handler became injectable.
     */
    @Test
    fun whenNoBrowserCanHandleTheLinkTheUrlIsShownAsText() {
        show(RecordingUriHandler(failWith = IllegalStateException("no activity found to handle the uri")))

        compose.onNodeWithText("Privacy Policy").performClick()

        compose.onNodeWithText(BuildConfig.PRIVACY_POLICY_URL, substring = true).assertIsDisplayed()
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

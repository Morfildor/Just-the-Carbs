package app.justthecarbs.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

        compose.onNodeWithText("Privacy Policy").performScrollTo().assertIsDisplayed()
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

        compose.onNodeWithText("Privacy Policy").performScrollTo().performClick()

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

        compose.onNodeWithText("Privacy Policy").performScrollTo().performClick()

        compose.onNodeWithText(BuildConfig.PRIVACY_POLICY_URL, substring = true).performScrollTo().assertIsDisplayed()
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

    @Test
    fun feedbackRowIsPresentInAbout() {
        show()

        compose.onNodeWithText("Send feedback / Report a problem").performScrollTo().assertIsDisplayed()
    }

    /**
     * Tapping the row opens a `mailto:` link addressed to the app's own support address, carrying a
     * subject and a body — never a bare address with no prefilled context.
     */
    @Test
    fun tappingFeedbackOpensAMailtoLinkAddressedToSupport() {
        val uriHandler = RecordingUriHandler()
        show(uriHandler)

        compose.onNodeWithText("Send feedback / Report a problem").performScrollTo().performClick()

        assert(uriHandler.opened.size == 1) { "expected exactly one URI opened, got ${uriHandler.opened}" }
        val opened = uriHandler.opened.single()
        assert(opened.startsWith("mailto:${BuildConfig.CONTACT_EMAIL}?")) {
            "expected a mailto: link to ${BuildConfig.CONTACT_EMAIL}, got $opened"
        }
        assert(opened.contains("subject=")) { "expected a prefilled subject, got $opened" }
        assert(opened.contains("body=")) { "expected a prefilled body, got $opened" }
    }

    /**
     * No auto-attach, no auto-export: the body only ever *mentions* Scan Evidence as something the
     * user can attach themselves. This pins that nothing beyond a mailto: URI is ever launched.
     */
    @Test
    fun whenNoEmailAppCanHandleFeedbackTheAddressIsShownAsText() {
        show(RecordingUriHandler(failWith = IllegalStateException("no activity found to handle the uri")))

        compose.onNodeWithText("Send feedback / Report a problem").performScrollTo().performClick()

        compose.onNodeWithText(BuildConfig.CONTACT_EMAIL, substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Send feedback / Report a problem").assertIsDisplayed()
    }

    @Test
    fun rateRowIsPresentInAbout() {
        show()

        compose.onNodeWithText("Rate JustTheCarbs").performScrollTo().assertIsDisplayed()
    }

    /**
     * The rate-us card is now a deliberate marketing surface, not a plain text row — this pins that
     * its headline and supporting copy are both present alongside the action itself.
     */
    @Test
    fun rateCardShowsItsHeadlineAndSupportingCopy() {
        show()

        compose.onNodeWithText("Enjoying Just the Carbs?").performScrollTo().assertIsDisplayed()
        // Scrolled to in its own right, not merely after its headline. Scrolling the headline into
        // view does not guarantee the line beneath it is also on screen, so this assertion's result
        // depended on how much content happened to sit above the card — it began failing when the
        // Settings list grew by one row. The same below-the-fold trap this codebase has recorded
        // several times: `assertIsDisplayed` is about the window, and it was telling the truth.
        compose.onNodeWithText(
            "A quick rating on the Play Store helps other people find the app.",
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Rate JustTheCarbs").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tappingRateOpensTheAppsOwnPlayStoreListing() {
        val uriHandler = RecordingUriHandler()
        show(uriHandler)

        compose.onNodeWithText("Rate JustTheCarbs").performScrollTo().performClick()

        assert(uriHandler.opened == listOf("market://details?id=${BuildConfig.APPLICATION_ID}")) {
            "expected the market:// listing for ${BuildConfig.APPLICATION_ID}, got ${uriHandler.opened}"
        }
    }

    /**
     * A handler that fails on the `market://` scheme but succeeds on the second, https, attempt —
     * the shape of "Play Store app not installed", where the web listing is still reachable.
     */
    private class FallbackOnHttpsUriHandler : UriHandler {
        val opened = mutableListOf<String>()

        override fun openUri(uri: String) {
            opened += uri
            if (uri.startsWith("market://")) {
                throw IllegalStateException("no activity found to handle market:// on this device")
            }
        }
    }

    @Test
    fun whenThePlayStoreAppCannotHandleTheLinkTheHttpsListingIsUsedInstead() {
        val uriHandler = FallbackOnHttpsUriHandler()
        show(uriHandler)

        compose.onNodeWithText("Rate JustTheCarbs").performScrollTo().performClick()

        assert(
            uriHandler.opened == listOf(
                "market://details?id=${BuildConfig.APPLICATION_ID}",
                "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}",
            ),
        ) { "expected a market:// attempt followed by the https fallback, got ${uriHandler.opened}" }
    }

    @Test
    fun whenNeitherPlayStoreRouteIsAvailableTheFailureIsShownAsText() {
        show(RecordingUriHandler(failWith = IllegalStateException("no activity found to handle the uri")))

        compose.onNodeWithText("Rate JustTheCarbs").performScrollTo().performClick()

        compose.onNodeWithText("Could not open the Play Store.").performScrollTo().assertIsDisplayed()
    }
}

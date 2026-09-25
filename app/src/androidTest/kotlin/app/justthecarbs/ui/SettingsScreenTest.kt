package app.justthecarbs.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.R
import app.justthecarbs.ui.settings.ClearReport
import app.justthecarbs.ui.settings.ClearedData
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
import org.junit.Assert.assertEquals
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

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /** Mirrors SettingsViewModel: a clear reports itself once it has run, and is consumed once shown. */
    private fun showStateful(initialHaptics: Boolean = true) {
        compose.setContent {
            var settings by remember { mutableStateOf(AppSettings(hapticsEnabled = initialHaptics)) }
            var cleared by remember { mutableStateOf<ClearReport?>(null) }
            JustTheCarbsTheme {
                SettingsScreen(
                    settings = settings,
                    onThemeChanged = {},
                    onResultStyleChanged = {},
                    onHapticsChanged = { settings = settings.copy(hapticsEnabled = it) },
                    onClearRecents = { cleared = ClearReport(ClearedData.RECENT_HISTORY) },
                    onClearProducts = { cleared = ClearReport(ClearedData.SAVED_PRODUCTS) },
                    cleared = cleared,
                    onClearedShown = { cleared = null },
                    onBack = {},
                )
            }
        }
    }

    /**
     * The haptics row is one control: its label toggles it, and TalkBack meets one switch carrying
     * its name rather than an unlabelled switch beside a line of text.
     */
    @Test
    fun tappingTheHapticsLabelTogglesTheSetting() {
        showStateful(initialHaptics = true)
        val row = compose.onNode(isToggleable() and hasText(string(R.string.settings_haptics)))

        row.assertIsOn()
        compose.onNodeWithText(string(R.string.settings_haptics)).performClick()

        row.assertIsOff()
    }

    @Test
    fun theThemeAndResultStyleChoicesAreEachAnnouncedAsAGroup() {
        showStateful()

        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
            .assertCountEquals(2)
    }

    @Test
    fun clearingRecentHistorySaysSoOnceItHasRun() {
        showStateful()

        compose.onNodeWithText(string(R.string.settings_clear_recents)).performScrollTo().performClick()
        compose.onNode(hasText(string(R.string.settings_confirm_clear)) and hasAnyAncestor(isDialog()))
            .performClick()

        compose.onNodeWithText(string(R.string.settings_cleared_recents)).assertIsDisplayed()
    }

    @Test
    fun clearingSavedProductsSaysSoOnceItHasRun() {
        showStateful()

        compose.onNodeWithText(string(R.string.settings_clear_products)).performScrollTo().performClick()
        compose.onNode(hasText(string(R.string.settings_confirm_clear)) and hasAnyAncestor(isDialog()))
            .performClick()

        compose.onNodeWithText(string(R.string.settings_cleared_products)).assertIsDisplayed()
    }

    @Test
    fun cancellingAClearSaysNothing() {
        showStateful()

        compose.onNodeWithText(string(R.string.settings_clear_recents)).performScrollTo().performClick()
        compose.onNode(hasText(string(R.string.settings_cancel)) and hasAnyAncestor(isDialog()))
            .performClick()

        compose.onNodeWithText(string(R.string.settings_cleared_recents)).assertDoesNotExist()
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
     * No auto-attach, no auto-export: the body only ever *suggests* a photo of the label, which the
     * user attaches themselves. This pins that nothing beyond a mailto: URI is ever launched.
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

        compose.onNodeWithText("Rate on Google Play").performScrollTo().assertIsDisplayed()
    }

    /**
     * The rating ask is one row, not a card.
     *
     * This replaces `rateCardShowsItsHeadlineAndSupportingCopy`, which pinned a bordered card with
     * a headline ("Enjoying Just the Carbs?"), a body paragraph and a saturated gold button. That
     * surface was removed deliberately (P1-11): it was the loudest object on a screen of quiet
     * rows and the only place in the app spending a saturated fill on something that is not the
     * task the user came to do.
     *
     * The assertion's real purpose -- the ask is reachable and says what it does -- is kept, and
     * the inverse is added, so the card cannot come back unnoticed.
     */
    @Test
    fun theRatingAskIsOneRowRatherThanAMarketingCard() {
        show()

        compose.onNodeWithText("Rate on Google Play").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Enjoying Just the Carbs?").assertDoesNotExist()
        compose.onNodeWithText(
            "A quick rating on the Play Store helps other people find the app.",
        ).assertDoesNotExist()
    }

    @Test
    fun tappingRateOpensTheAppsOwnPlayStoreListing() {
        val uriHandler = RecordingUriHandler()
        show(uriHandler)

        compose.onNodeWithText("Rate on Google Play").performScrollTo().performClick()

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

        compose.onNodeWithText("Rate on Google Play").performScrollTo().performClick()

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

        compose.onNodeWithText("Rate on Google Play").performScrollTo().performClick()

        compose.onNodeWithText("Could not open the Play Store.").performScrollTo().assertIsDisplayed()
    }

    private fun showReport(report: ClearReport, onShown: () -> Unit = {}) {
        compose.setContent {
            JustTheCarbsTheme {
                SettingsScreen(
                    settings = AppSettings(),
                    onThemeChanged = {},
                    onResultStyleChanged = {},
                    onHapticsChanged = {},
                    onClearRecents = {},
                    onClearProducts = {},
                    cleared = report,
                    onClearedShown = onShown,
                    onBack = {},
                )
            }
        }
    }

    /**
     * The report is consumed when it is shown, not when the Snackbar goes away (2026-09-25
     * review): consumed afterwards, leaving Settings within those seconds showed it again on return.
     */
    @Test
    fun aClearReportIsConsumedWhileItsMessageIsStillShowing() {
        var consumed = 0
        showReport(ClearReport(ClearedData.RECENT_HISTORY), onShown = { consumed++ })

        compose.onNodeWithText(string(R.string.settings_cleared_recents)).assertIsDisplayed()
        assertEquals(1, consumed)
    }

    @Test
    fun aFailedClearSaysItFailed() {
        showReport(ClearReport(ClearedData.SAVED_PRODUCTS, failed = true))

        compose.onNodeWithText(string(R.string.settings_clear_products_failed)).assertIsDisplayed()
    }
}

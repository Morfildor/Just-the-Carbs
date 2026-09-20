package app.justthecarbs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.R
import app.justthecarbs.ui.scan.SHARE_CHOOSER_BARCODE_TAG
import app.justthecarbs.ui.scan.SHARE_CHOOSER_CANCEL_TAG
import app.justthecarbs.ui.scan.SHARE_CHOOSER_LABEL_TAG
import app.justthecarbs.ui.scan.SharedImageChooserScreen
import app.justthecarbs.ui.scan.SharedImageFailedScreen
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The share chooser: two choices, no guess, and quick to leave.
 *
 * The behavioural half of the share feature's safety claim. The structural half —
 * that neither recognizer can run before a choice is made — is pinned by
 * [app.justthecarbs.ui.SharedImagePipelineConvergenceTest], because "no such code exists" is not
 * something a screen test can establish.
 */
class SharedImageChooserScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun setChooser(
        onBarcode: () -> Unit = {},
        onLabel: () -> Unit = {},
        onCancel: () -> Unit = {},
    ) = rule.setContent {
        JustTheCarbsTheme {
            SharedImageChooserScreen(
                onChooseBarcode = onBarcode,
                onChooseLabel = onLabel,
                onCancel = onCancel,
            )
        }
    }

    @Test
    fun theChooserAsksWhatTheImageContains() {
        setChooser()
        rule.onNodeWithText(context.getString(R.string.share_chooser_title)).assertIsDisplayed()
        rule.onNodeWithTag(SHARE_CHOOSER_BARCODE_TAG).assertIsDisplayed()
        rule.onNodeWithTag(SHARE_CHOOSER_LABEL_TAG).assertIsDisplayed()
    }

    @Test
    fun choosingBarcodeReportsBarcodeAndNothingElse() {
        var barcode = 0
        var label = 0
        var cancel = 0
        setChooser({ barcode++ }, { label++ }, { cancel++ })

        rule.onNodeWithTag(SHARE_CHOOSER_BARCODE_TAG).performClick()

        assertEquals(1, barcode)
        assertEquals(0, label)
        assertEquals(0, cancel)
    }

    @Test
    fun choosingTheLabelReportsTheLabelAndNothingElse() {
        var barcode = 0
        var label = 0
        var cancel = 0
        setChooser({ barcode++ }, { label++ }, { cancel++ })

        rule.onNodeWithTag(SHARE_CHOOSER_LABEL_TAG).performClick()

        assertEquals(0, barcode)
        assertEquals(1, label)
        assertEquals(0, cancel)
    }

    @Test
    fun dismissingRunsNeitherPipeline() {
        // The requirement stated directly: cancelling must not be a quiet way of picking one.
        var barcode = 0
        var label = 0
        var cancel = 0
        setChooser({ barcode++ }, { label++ }, { cancel++ })

        rule.onNodeWithTag(SHARE_CHOOSER_CANCEL_TAG).performScrollTo().performClick()

        assertEquals(0, barcode)
        assertEquals(0, label)
        assertEquals(1, cancel)
    }

    @Test
    fun simplyShowingTheChooserChoosesNothing() {
        // Composing the screen must be inert. An "if only one recognizer would answer" shortcut
        // added later would show up here first.
        var barcode = 0
        var label = 0
        setChooser({ barcode++ }, { label++ })
        rule.waitForIdle()
        assertEquals(0, barcode)
        assertEquals(0, label)
    }

    @Test
    fun bothChoicesAreRealTouchTargets() {
        setChooser()
        rule.onNodeWithTag(SHARE_CHOOSER_BARCODE_TAG)
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag(SHARE_CHOOSER_LABEL_TAG)
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun everyChoiceCarriesOneSpokenDescription() {
        // Merged semantics: TalkBack announces one actionable choice, not an icon, a title, a
        // subtitle and a chevron in sequence. Read off the merged node itself rather than searched
        // for in the tree, so a child Text matching by accident cannot satisfy this.
        setChooser()
        val expected = context.getString(
            R.string.home_action_description,
            context.getString(R.string.share_chooser_barcode),
            context.getString(R.string.share_chooser_barcode_subtitle),
        )
        val node = rule.onNodeWithTag(SHARE_CHOOSER_BARCODE_TAG).fetchSemanticsNode()
        val actual = node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
        assertEquals(expected, actual)
    }

    @Test
    fun theChooserStillFitsAtALargeFontScale() {
        // 1.8x is an ordinary accessibility setting, and the scroll is what keeps the dismissing
        // action reachable rather than severed at the viewport edge.
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = LocalDensity.current.density,
                    fontScale = 1.8f,
                ),
            ) {
                JustTheCarbsTheme {
                    SharedImageChooserScreen({}, {}, {})
                }
            }
        }

        rule.onNodeWithTag(SHARE_CHOOSER_BARCODE_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(SHARE_CHOOSER_LABEL_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(SHARE_CHOOSER_CANCEL_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theChooserFitsANarrowViewport() {
        // 320dp, the historical Android minimum width.
        rule.setContent {
            JustTheCarbsTheme {
                Box(Modifier.size(320.dp, 640.dp)) {
                    SharedImageChooserScreen({}, {}, {})
                }
            }
        }
        rule.onNodeWithTag(SHARE_CHOOSER_BARCODE_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(SHARE_CHOOSER_LABEL_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anUnreadableShareSaysSoWithoutTechnicalWording() {
        var closed = 0
        rule.setContent { JustTheCarbsTheme { SharedImageFailedScreen(onClose = { closed++ }) } }

        rule.onNodeWithText(context.getString(R.string.share_failed_title)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.share_failed_home)).performClick()
        assertEquals(1, closed)
    }

    @Test
    fun theFailureWordingNamesNoStorageOrExceptionVocabulary() {
        // The copy rule. A user cannot act on "SecurityException" or "12.4 MB".
        val body = context.getString(R.string.share_failed_body) +
            " " + context.getString(R.string.share_failed_title)
        for (forbidden in listOf("Exception", "URI", "storage", "permission", "byte", "MB", "null")) {
            assert(!body.contains(forbidden, ignoreCase = true)) {
                "share failure copy must not mention '$forbidden': $body"
            }
        }
    }
}

package app.justthecarbs.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.R
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import app.justthecarbs.ui.scan.CameraPermissionRationale
import app.justthecarbs.ui.scan.ChoosePhotoButton
import app.justthecarbs.ui.scan.CameraPermissionState
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Photo import survives a declined camera permission (1.0.8).
 *
 * This is the state the feature matters most in and the one easiest to lose: reading a photograph
 * needs no camera at all, but every scanner surface that could offer it is composed only when the
 * permission is granted. A user who declined the camera has *nothing else* on this screen that can
 * read a label, so the action disappearing here would leave them with manual transcription only.
 *
 * The rationale is shared with the barcode scanner, so these also pin that the barcode scanner is
 * unaffected — it passes no photo action and must show none.
 */
class ChoosePhotoScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun choosePhotoLabel() = context.getString(R.string.permission_choose_photo)

    private fun setRationale(
        state: CameraPermissionState = CameraPermissionState.DeniedCanAskAgain,
        onChoosePhoto: (() -> Unit)? = {},
        onEnterBarcode: (() -> Unit)? = null,
    ) {
        rule.setContent {
            JustTheCarbsTheme {
                CameraPermissionRationale(
                    state = state,
                    onAllow = {},
                    onOpenSettings = {},
                    onEnterManually = {},
                    onEnterBarcode = onEnterBarcode,
                    onChoosePhoto = onChoosePhoto,
                    onClose = {},
                )
            }
        }
    }

    @Test
    fun choosePhotoIsOfferedWhenTheCameraPermissionIsDenied() {
        setRationale(state = CameraPermissionState.DeniedCanAskAgain)

        rule.onNodeWithText(choosePhotoLabel()).assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun choosePhotoIsOfferedWhenTheCameraPermissionIsPermanentlyDenied() {
        // The state with no way back to the camera at all. Photo import is the only label-reading
        // route left, so its absence here would be the feature failing exactly where it counts.
        setRationale(state = CameraPermissionState.PermanentlyDenied)

        rule.onNodeWithText(choosePhotoLabel()).assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun choosingAPhotoFromTheDeniedStateInvokesTheAction() {
        var chosen = 0
        setRationale(onChoosePhoto = { chosen++ })

        rule.onNodeWithText(choosePhotoLabel()).performClick()

        assertEquals("the denied-state action must actually open the picker", 1, chosen)
    }

    @Test
    fun theChoosePhotoActionMeetsTheTouchTargetFloor() {
        setRationale()

        rule.onNodeWithText(choosePhotoLabel())
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun aCallerPassingNoPhotoActionShowsNone() {
        // RE-AIMED for Barcode from Photo (1.0.8), not weakened. This case previously asserted
        // that the *barcode scanner* shows no photo action, which was true while the barcode
        // scanner had no photo-reading path — it now has one, and offering it there is the point
        // of the feature (see barcodePhotoImportIsOfferedWhenTheCameraPermissionIsDenied).
        //
        // What the case was actually worth proving survives intact and is what it now states: the
        // shared rationale renders exactly the actions it is given and invents none. That is the
        // property that keeps a future third caller from inheriting an action it cannot honour.
        setRationale(onChoosePhoto = null, onEnterBarcode = {})

        val matches = rule.onAllNodesWithText(choosePhotoLabel())
            .fetchSemanticsNodes(atLeastOneRootRequired = false).size
        assertTrue(
            "a null photo action must render nothing, found $matches",
            matches == 0,
        )
        // And the control: the action it *was* given is present, so the assertion above cannot
        // pass merely because nothing rendered at all.
        rule.onNodeWithText(context.getString(R.string.scanner_enter_manually)).assertIsDisplayed()
    }

    @Test
    fun manualEntryRemainsAvailableAlongsidePhotoImport() {
        // Declining the camera must cost the user the camera, never the app. Photo import is an
        // addition to the existing ways out, never a replacement for them.
        setRationale()

        rule.onNodeWithText(context.getString(R.string.permission_manual))
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    // ---- The gallery icon in the camera chrome ----

    private fun setIcon(reading: Boolean = false, onChoose: () -> Unit = {}) {
        rule.setContent { JustTheCarbsTheme { ChoosePhotoButton(reading = reading, onChoose = onChoose) } }
    }

    @Test
    fun theGalleryIconCarriesAnExplicitSemanticLabel() {
        // The action is an icon with no visible text, so the content description IS the label. It
        // names the whole gesture rather than the bare noun: a lone "Gallery" announced over a
        // camera preview says where the picture comes from but not what tapping it will do.
        setIcon()

        rule.onNodeWithContentDescription(context.getString(R.string.ocr_choose_photo))
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun theGalleryIconMeetsTheTouchTargetFloor() {
        setIcon()

        rule.onNodeWithContentDescription(context.getString(R.string.ocr_choose_photo))
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun theGalleryIconAnnouncesThatItIsReadingRatherThanRelyingOnTheSpinner() {
        // Processing must not be communicated by animation alone. While a photo is being read the
        // node's own label says so, so TalkBack conveys the state with the action rather than
        // reading a decorative progress indicator separately.
        setIcon(reading = true)

        rule.onNodeWithContentDescription(context.getString(R.string.ocr_photo_reading))
            .assertIsDisplayed()
        assertEquals(
            "while reading, the idle label must not also be present — one node, one label",
            0,
            rule.onAllNodesWithContentDescriptionCount(context.getString(R.string.ocr_choose_photo)),
        )
    }

    @Test
    fun theGalleryIconIsNotTappableWhileAPhotoIsBeingRead() {
        setIcon(reading = true)

        rule.onNodeWithContentDescription(context.getString(R.string.ocr_photo_reading))
            .assertIsNotEnabled()
    }

    @Test
    fun tappingTheGalleryIconOpensThePicker() {
        var chosen = 0
        setIcon(onChoose = { chosen++ })

        rule.onNodeWithContentDescription(context.getString(R.string.ocr_choose_photo)).performClick()

        assertEquals(1, chosen)
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithContentDescriptionCount(
    description: String,
): Int = onAllNodesWithContentDescription(description)
    .fetchSemanticsNodes(atLeastOneRootRequired = false).size

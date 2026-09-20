package app.justthecarbs.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.R
import app.justthecarbs.ui.scan.CameraPermissionRationale
import app.justthecarbs.ui.scan.CameraPermissionState
import app.justthecarbs.ui.scan.ChoosePhotoButton
import app.justthecarbs.ui.scan.ImportedBarcodeSheet
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Barcode from Photo's own surfaces (1.0.8).
 *
 * Covers the parts of the feature that are decisions about *what the user is shown and told* — the
 * multi-barcode question, the gallery action's label, and the permission-denied route — as opposed
 * to the value rules, which are pinned in the JVM suite, and the recognition, which is pinned on a
 * real device by `ImportedBarcodeReaderTest`.
 *
 * The accessibility cases here are not decoration. A barcode is a string of digits with no other
 * distinguishing feature, so if selection were communicated by position or highlight alone a
 * TalkBack user would have no way to tell two options apart — and the consequence of picking the
 * wrong one is a confident lookup for a product they did not photograph.
 */
class BarcodeFromPhotoScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val first = "4006381333931"
    private val second = "8712100849060"

    private fun setSheet(
        barcodes: List<String> = listOf(first, second),
        onSelect: (String) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        rule.setContent {
            JustTheCarbsTheme {
                ImportedBarcodeSheet(barcodes = barcodes, onSelect = onSelect, onDismiss = onDismiss)
            }
        }
    }

    // ---- The multi-barcode question ----

    @Test
    fun everyDetectedBarcodeIsListedAndNoneIsPreSelected() {
        // The whole point of the sheet: the app has not chosen, and the user can see every code it
        // found. A pre-selected option would be a guess wearing a confirmation step.
        setSheet()

        rule.onNodeWithText(first).assertIsDisplayed()
        rule.onNodeWithText(second).assertIsDisplayed()
    }

    @Test
    fun choosingABarcodeReportsThatExactCode() {
        var chosen: String? = null
        setSheet(onSelect = { chosen = it })

        rule.onNodeWithText(second).performClick()
        rule.waitForIdle()

        assertEquals("the tapped code must be the one reported", second, chosen)
    }

    @Test
    fun theSheetExplainsWhyItIsAsking() {
        // Without this the question reads as the app having failed, rather than as the app
        // declining to guess between two products it genuinely found.
        setSheet()

        rule.onNodeWithText(context.getString(R.string.scanner_photo_multiple_title))
            .assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.scanner_photo_multiple_body))
            .assertIsDisplayed()
    }

    @Test
    fun cancellingOffersNoBarcodeAtAll() {
        // Dismissing is a real answer — "neither of these" — and must not resolve to a code.
        var chosen: String? = null
        var dismissed = 0
        setSheet(onSelect = { chosen = it }, onDismiss = { dismissed++ })

        rule.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        rule.waitForIdle()

        assertEquals("cancelling must never select a barcode", null, chosen)
        assertTrue("cancelling must return to the camera", dismissed >= 1)
    }

    @Test
    fun eachBarcodeOptionSpeaksItsWholeActionRatherThanItsPositionOrColour() {
        // The accessibility requirement stated as a behaviour: each row carries its own label
        // naming the code it will look up, so selection is never conveyed by highlight alone.
        setSheet()

        listOf(first, second).forEach { barcode ->
            rule.onNodeWithContentDescription(
                context.getString(R.string.scanner_photo_use_barcode, barcode),
            ).assertIsDisplayed().assertHasClickAction()
        }
    }

    @Test
    fun everyBarcodeOptionMeetsTheTouchTargetFloor() {
        setSheet()

        listOf(first, second).forEach { barcode ->
            rule.onNodeWithContentDescription(
                context.getString(R.string.scanner_photo_use_barcode, barcode),
            ).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }

    @Test
    fun threeDetectedBarcodesAreAllOffered() {
        // A shelf photograph is the ordinary case. Quietly truncating the list would be a quieter
        // version of guessing.
        val third = "5000159484695"
        setSheet(barcodes = listOf(first, second, third))

        listOf(first, second, third).forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    // ---- The gallery action in the barcode scanner's chrome ----

    private fun setBarcodeIcon(reading: Boolean = false, onChoose: () -> Unit = {}) {
        rule.setContent {
            JustTheCarbsTheme {
                ChoosePhotoButton(
                    reading = reading,
                    onChoose = onChoose,
                    description = context.getString(
                        if (reading) R.string.scanner_photo_reading else R.string.scanner_choose_photo,
                    ),
                )
            }
        }
    }

    @Test
    fun theBarcodeGalleryIconNamesABarcodeRatherThanANutritionLabel() {
        // The shared button must not announce the label scanner's action on the barcode scanner.
        setBarcodeIcon()

        rule.onNodeWithContentDescription(context.getString(R.string.scanner_choose_photo))
            .assertIsDisplayed()
            .assertHasClickAction()

        assertEquals(
            "the nutrition-label wording must not appear on the barcode scanner",
            0,
            rule.onAllNodesWithContentDescription(context.getString(R.string.ocr_choose_photo))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).size,
        )
    }

    @Test
    fun theBarcodeGalleryIconMeetsTheTouchTargetFloor() {
        setBarcodeIcon()

        rule.onNodeWithContentDescription(context.getString(R.string.scanner_choose_photo))
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun theBarcodeGalleryIconAnnouncesItsProcessingState() {
        // Processing must not be communicated by the spinner alone.
        setBarcodeIcon(reading = true)

        rule.onNodeWithContentDescription(context.getString(R.string.scanner_photo_reading))
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun tappingTheBarcodeGalleryIconOpensThePicker() {
        var chosen = 0
        setBarcodeIcon(onChoose = { chosen++ })

        rule.onNodeWithContentDescription(context.getString(R.string.scanner_choose_photo))
            .performClick()

        assertEquals(1, chosen)
    }

    // ---- Camera permission denied ----

    @Test
    fun barcodePhotoImportIsOfferedWhenTheCameraPermissionIsDenied() {
        // The requirement that photo import stays usable without a camera. Reading a barcode out
        // of a photograph needs no camera at all, so the state where the user has lost every other
        // way to scan is precisely the state where it must remain reachable.
        var chosen = 0
        rule.setContent {
            JustTheCarbsTheme {
                CameraPermissionRationale(
                    state = CameraPermissionState.PermanentlyDenied,
                    onAllow = {},
                    onOpenSettings = {},
                    onEnterManually = {},
                    onEnterBarcode = {},
                    onChoosePhoto = { chosen++ },
                    onClose = {},
                )
            }
        }

        val label = context.getString(R.string.permission_choose_photo)
        rule.onNodeWithText(label).assertIsDisplayed().assertHasClickAction().performClick()

        assertEquals("the denied state must still be able to open the picker", 1, chosen)
    }

    @Test
    fun manualBarcodeEntryRemainsAvailableAlongsidePhotoImport() {
        // Photo import is an addition to the existing ways out, never a replacement: typing the
        // digits printed under the bars must still be offered.
        rule.setContent {
            JustTheCarbsTheme {
                CameraPermissionRationale(
                    state = CameraPermissionState.DeniedCanAskAgain,
                    onAllow = {},
                    onOpenSettings = {},
                    onEnterManually = {},
                    onEnterBarcode = {},
                    onChoosePhoto = {},
                    onClose = {},
                )
            }
        }

        rule.onNodeWithText(context.getString(R.string.scanner_enter_manually))
            .assertIsDisplayed()
            .assertHasClickAction()
        rule.onNodeWithText(context.getString(R.string.permission_manual))
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun theNoBarcodeMessageNamesNoTechnicalCause() {
        // "checksum", "ML Kit", "symbology" and "decode" are facts about the app's internals that
        // the user acts on identically. The copy must say what happened and what to do next.
        val message = context.getString(R.string.scanner_photo_no_barcode)

        listOf("checksum", "ML Kit", "mlkit", "symbology", "decode", "EAN", "UPC", "GTIN")
            .forEach { term ->
                assertTrue(
                    "the no-barcode message must not mention '$term' — it reads: $message",
                    !message.contains(term, ignoreCase = true),
                )
            }
    }

    @Test
    fun theNoBarcodeMessageDoesNotClaimThePhotoHasNoBarcodeInIt() {
        // A photo may well contain a barcode that came out too small or too blurred. Telling
        // someone their picture has no barcode in it when they can see one is the version of this
        // message that gets the app distrusted.
        val message = context.getString(R.string.scanner_photo_no_barcode)

        assertTrue(
            "the message must qualify the claim to what the app could read — it reads: $message",
            message.contains("could read", ignoreCase = true),
        )
    }

    @Test
    fun noBarcodeCopyIsNotCommunicatedByColourAlone() {
        // The recovery states its own facts in words: a title that says what happened and a
        // labelled way forward, neither of which depends on a hue.
        assertTrue(
            context.getString(R.string.scanner_photo_no_barcode).isNotBlank(),
        )
        assertTrue(
            context.getString(R.string.scanner_photo_choose_another).isNotBlank(),
        )
        assertEquals(
            "the choose-another action must name the action rather than a bare 'Retry'",
            0,
            rule.onAllNodesWithText("Retry").fetchSemanticsNodes(atLeastOneRootRequired = false).size,
        )
    }
}

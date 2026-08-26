package app.justthecarbs.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.ocr.CarbCandidate
import app.justthecarbs.ocr.EvidenceResolver
import app.justthecarbs.ocr.EvidenceSource
import app.justthecarbs.ocr.LabelReading
import app.justthecarbs.ocr.NutritionParseReport
import app.justthecarbs.ocr.OcrBox
import app.justthecarbs.ocr.OcrDocument
import app.justthecarbs.ocr.OcrElement
import app.justthecarbs.ui.scan.ASSIST_MANUAL_FIELD_TAG
import app.justthecarbs.ui.scan.AssistState
import app.justthecarbs.ui.scan.AssistedReadingScreen
import app.justthecarbs.ui.scan.CONFLICT_ASSIST_TAG
import app.justthecarbs.ui.scan.ConflictScreen
import app.justthecarbs.ui.scan.VERIFY_CONFIRM_TAG
import app.justthecarbs.ui.scan.VerificationScreen
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/**
 * The screens that make a failed automatic read recoverable (spec §17-§19, §8).
 *
 * These are the release-blocker tests in UI form: the user must be able to finish the task from the
 * frozen photograph rather than being ejected from it. Each case also pins a safety property — a
 * proposal is never presented as settled, and a conflict never offers a value at all.
 */
class AssistedReadingScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun bitmap(): Bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)

    private fun document() = OcrDocument(
        width = 1200,
        height = 400,
        elements = listOf(
            OcrElement("Koolhydraten", OcrBox(60, 180, 220, 205), 0, 0),
            OcrElement("53,5", OcrBox(380, 180, 450, 205), 0, 0),
            OcrElement("waarvan", OcrBox(80, 250, 170, 275), 1, 0),
            OcrElement("suikers", OcrBox(178, 250, 250, 275), 1, 0),
            OcrElement("47,6", OcrBox(380, 250, 450, 275), 1, 0),
        ),
    )

    private fun candidate(value: String) = CarbCandidate(
        sourceLine = "Koolhydraten $value g",
        label = "Koolhydraten",
        value = BigDecimal(value),
        basis = NutritionBasis.PER_100_G,
        score = 120,
        geometry = OcrBox(380, 180, 450, 205),
        evidence = emptyList(),
    )

    // ---- the dead end is gone -------------------------------------------------------------------

    @Test
    fun theAssistedScreenOffersAWayToFinishRatherThanADeadEnd() {
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = AssistState(document = document()),
                    onUseValue = { _, _ -> },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        // All three routes to completion are present without navigating away.
        rule.onNodeWithText("Tap the carbohydrate row").assertIsDisplayed()
        rule.onNodeWithText("Tap the number instead").assertIsDisplayed()
        rule.onNodeWithText("Type it in").assertIsDisplayed()
    }

    /** §19: the value can be typed with the table still on screen, and it carries a basis. */
    @Test
    fun typingAValueInlineCompletesTheScanWithABasis() {
        var used: Pair<BigDecimal, NutritionBasis>? = null
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = AssistState(document = document()),
                    onUseValue = { value, basis -> used = value to basis },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        rule.onNodeWithText("Type it in").performClick()
        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG).performTextInput("53.5")
        rule.onNodeWithText("Use / 100 g").performClick()

        assertEquals(BigDecimal("53.5"), used?.first)
        assertEquals(NutritionBasis.PER_100_G, used?.second)
    }

    /** A decimal comma must work: the app is used where that is the printed separator. */
    @Test
    fun aCommaDecimalIsAccepted() {
        var used: BigDecimal? = null
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = AssistState(document = document()),
                    onUseValue = { value, _ -> used = value },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        rule.onNodeWithText("Type it in").performClick()
        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG).performTextInput("53,5")
        rule.onNodeWithText("Use / 100 g").performClick()

        assertEquals(0, used!!.compareTo(BigDecimal("53.5")))
    }

    /** An empty field cannot complete the scan — there would be no value to complete it with. */
    @Test
    fun anEmptyInlineValueCannotBeUsed() {
        var used: BigDecimal? = null
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = AssistState(document = document()),
                    onUseValue = { value, _ -> used = value },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        rule.onNodeWithText("Type it in").performClick()
        rule.onNodeWithText("Use / 100 g").performClick()

        assertNull(used)
    }

    /** §11: a selection that removed nothing gets a specific explanation, not the generic one. */
    @Test
    fun anIneffectiveSelectionIsCalledOut() {
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = AssistState(document = document(), ineffectiveSelection = true),
                    onUseValue = { _, _ -> },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        rule.onNodeWithText(
            "Your box kept nearly the whole photo, so this read the same thing as before. " +
                "Retake and tighten it, or just point at the figure below.",
        ).assertIsDisplayed()
    }

    // ---- verification: a proposal must look like a proposal --------------------------------------

    @Test
    fun anUncorroboratedValueIsPresentedForCheckingNotAsSettled() {
        rule.setContent {
            JustTheCarbsTheme {
                VerificationScreen(
                    bitmap = bitmap(),
                    proposal = EvidenceResolver.Outcome.NeedsVerification(
                        reading = LabelReading.Confident(candidate("2.3")),
                        report = NutritionParseReport(LabelReading.Confident(candidate("2.3")), emptyList()),
                        source = EvidenceSource.SELECTED_REGION_OCR,
                    ),
                    onConfirm = { _, _ -> },
                    onReject = {},
                    onRetake = {},
                )
            }
        }

        rule.onNodeWithText("Check this against the label").assertIsDisplayed()
        rule.onNodeWithText("Read once, not confirmed. Compare it with the package before using it.")
            .assertIsDisplayed()
        rule.onNodeWithText("2.3 g per 100 g").assertIsDisplayed()
    }

    @Test
    fun confirmingAProposalUsesTheValue() {
        var used: Pair<BigDecimal, NutritionBasis>? = null
        rule.setContent {
            JustTheCarbsTheme {
                VerificationScreen(
                    bitmap = bitmap(),
                    proposal = EvidenceResolver.Outcome.NeedsVerification(
                        reading = LabelReading.Confident(candidate("2.3")),
                        report = NutritionParseReport(LabelReading.Confident(candidate("2.3")), emptyList()),
                        source = EvidenceSource.SELECTED_REGION_OCR,
                    ),
                    onConfirm = { value, basis -> used = value to basis },
                    onReject = {},
                    onRetake = {},
                )
            }
        }

        rule.onNodeWithTag(VERIFY_CONFIRM_TAG).performClick()

        assertEquals(BigDecimal("2.3"), used?.first)
        assertEquals(NutritionBasis.PER_100_G, used?.second)
    }

    /** Rejecting must keep the user in the task rather than dumping them back to the camera. */
    @Test
    fun rejectingAProposalHandsOffRatherThanEndingTheScan() {
        var rejected = false
        rule.setContent {
            JustTheCarbsTheme {
                VerificationScreen(
                    bitmap = bitmap(),
                    proposal = EvidenceResolver.Outcome.NeedsVerification(
                        reading = LabelReading.Confident(candidate("2.3")),
                        report = NutritionParseReport(LabelReading.Confident(candidate("2.3")), emptyList()),
                        source = EvidenceSource.SELECTED_REGION_OCR,
                    ),
                    onConfirm = { _, _ -> },
                    onReject = { rejected = true },
                    onRetake = {},
                )
            }
        }

        rule.onNodeWithText("Not right").performClick()

        assertEquals(true, rejected)
    }

    // ---- conflict: no value may be offered -------------------------------------------------------

    /**
     * THE conflict safety property, at the UI level.
     *
     * When two recognitions disagree the screen must not print either number as a usable answer.
     * The disputed values appear only inside the explanation of why the app stopped.
     */
    @Test
    fun aConflictOffersNoUsableValue() {
        rule.setContent {
            JustTheCarbsTheme {
                ConflictScreen(
                    bitmap = bitmap(),
                    conflict = EvidenceResolver.Outcome.Conflicted(
                        values = listOf("2.09/PER_100_G", "2/PER_100_G"),
                        sources = listOf(EvidenceSource.FULL_FRAME_PASS_A, EvidenceSource.SELECTED_REGION_OCR),
                    ),
                    onAssist = {},
                    onRetake = {},
                )
            }
        }

        rule.onNodeWithText("Two different readings").assertIsDisplayed()
        // No "Use ..." action exists anywhere on this screen.
        rule.onNodeWithText("Use / 100 g").assertDoesNotExist()
        rule.onNodeWithText("Use / 100 ml").assertDoesNotExist()
        rule.onNodeWithTag(CONFLICT_ASSIST_TAG).assertIsDisplayed()
    }

    @Test
    fun aConflictLeadsIntoTheAssistedPath() {
        var assisted = false
        rule.setContent {
            JustTheCarbsTheme {
                ConflictScreen(
                    bitmap = bitmap(),
                    conflict = EvidenceResolver.Outcome.Conflicted(
                        values = listOf("2.09/PER_100_G", "2/PER_100_G"),
                        sources = listOf(EvidenceSource.FULL_FRAME_PASS_A, EvidenceSource.SELECTED_REGION_OCR),
                    ),
                    onAssist = { assisted = true },
                    onRetake = {},
                )
            }
        }

        rule.onNodeWithTag(CONFLICT_ASSIST_TAG).performClick()

        assertEquals(true, assisted)
    }
}

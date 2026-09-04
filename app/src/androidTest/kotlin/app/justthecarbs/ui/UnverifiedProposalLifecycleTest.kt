package app.justthecarbs.ui

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.ocr.CarbCandidate
import app.justthecarbs.ocr.EvidenceResolver
import app.justthecarbs.ocr.EvidenceSource
import app.justthecarbs.ocr.LabelReading
import app.justthecarbs.ocr.NutritionColumnKind
import app.justthecarbs.ocr.NutritionParseReport
import app.justthecarbs.ocr.OcrBox
import app.justthecarbs.ocr.OcrDocument
import app.justthecarbs.ocr.OcrElement
import app.justthecarbs.ui.scan.ASSIST_FOCUSED_FIELD_TAG
import app.justthecarbs.ui.scan.ASSIST_MANUAL_FIELD_TAG
import app.justthecarbs.ui.scan.AssistState
import app.justthecarbs.ui.scan.AssistedReadingScreen
import app.justthecarbs.ui.scan.VERIFY_CONFIRM_TAG
import app.justthecarbs.ui.scan.VERIFY_PHOTO_TAG
import app.justthecarbs.ui.scan.VERIFY_REJECT_TAG
import app.justthecarbs.ui.scan.VERIFY_RETAKE_TAG
import app.justthecarbs.ui.scan.VERIFY_ZOOM_TAG
import app.justthecarbs.ui.scan.VerificationScreen
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/**
 * The lifecycle branch that escaped every existing test (eighth session, `20260902-213005-691`).
 *
 * ## The state under test
 *
 * `Resolved` + `Confident` + `verification NONE` + `mayConfirm true`. On the shipped code that
 * combination released the frozen capture and rendered an ordinary `ProposalCard` over the **live
 * camera preview**. The device recording shows what that costs: a card reading `12 g / 100 g` for a
 * package printing `7,2 g`, displayed over an empty wooden table because the user had already moved
 * the package away. The proposal was unanswerable, and "the user still had to confirm" is no defence
 * when there is nothing on screen to confirm it against.
 *
 * A pure unit test cannot see any of that — [app.justthecarbs.ocr.ScanPresentationTest] pins the
 * decision, and these pin what the decision renders.
 */
class UnverifiedProposalLifecycleTest {

    @get:Rule
    val rule = createComposeRule()

    /**
     * A stand-in for the capture, sized like one: portrait, far taller than wide.
     *
     * The aspect ratio matters — the close-up exists because a 1684x3648 capture fitted into a phone
     * viewport renders an 80 px nutrition row at a few pixels.
     */
    private fun capture(): Bitmap = Bitmap.createBitmap(421, 912, Bitmap.Config.ARGB_8888)

    /** The red Lidl label's carbohydrate row, in the shape the device recognised it. */
    private fun document() = OcrDocument(
        width = 421,
        height = 912,
        elements = listOf(
            OcrElement("ø/100", OcrBox(320, 250, 370, 275), 0, 0),
            OcrElement("g", OcrBox(374, 250, 386, 275), 0, 0),
            OcrElement("Hidratos", OcrBox(50, 460, 120, 485), 1, 0),
            OcrElement("de", OcrBox(126, 460, 145, 485), 1, 0),
            OcrElement("carbono", OcrBox(150, 460, 220, 485), 1, 0),
            OcrElement("12g", OcrBox(330, 460, 372, 485), 1, 0),
        ),
    )

    private val row = OcrBox(50, 460, 372, 485)

    private fun proposal(): EvidenceResolver.Outcome.NeedsVerification {
        val candidate = CarbCandidate(
            sourceLine = "Hidratos de carbono 12g",
            label = "Hidratos de carbono",
            value = BigDecimal("12"),
            basis = NutritionBasis.PER_100_G,
            score = 105,
            geometry = row,
            evidence = emptyList(),
            column = NutritionColumnKind.PER_100_G,
        )
        val document = document()
        return EvidenceResolver.Outcome.NeedsVerification(
            reading = LabelReading.Confident(candidate),
            report = NutritionParseReport(
                reading = LabelReading.Confident(candidate),
                diagnostics = emptyList(),
            ),
            source = EvidenceSource.FULL_FRAME_PASS_A,
        )
    }

    // ------------------------------------------------------- the photograph stays on screen

    @Test
    fun theFrozenPhotographIsVisibleBehindAnUnverifiedProposal() {
        rule.setContent {
            JustTheCarbsTheme {
                VerificationScreen(
                    bitmap = capture(),
                    proposal = proposal(),
                    onConfirm = { _, _ -> },
                    onReject = {},
                    onRetake = {},
                )
            }
        }

        rule.onNodeWithTag(VERIFY_PHOTO_TAG).assertIsDisplayed()
    }

    /**
     * The close-up is what makes the proposal *checkable* rather than merely accompanied.
     *
     * Without it the row the value came from renders at a few pixels on a real capture, and a user
     * cannot tell `7,2` from `12` in that.
     */
    @Test
    fun theSourceRowIsShownEnlarged() {
        rule.setContent {
            JustTheCarbsTheme {
                VerificationScreen(
                    bitmap = capture(),
                    proposal = proposal(),
                    onConfirm = { _, _ -> },
                    onReject = {},
                    onRetake = {},
                )
            }
        }

        rule.onNodeWithTag(VERIFY_ZOOM_TAG).assertIsDisplayed()
    }

    /** Amount, basis, and the nutrient row it was read from — plus the instruction to check it. */
    @Test
    fun theProposalStatesTheAmountItsBasisAndItsRow() {
        rule.setContent {
            JustTheCarbsTheme {
                VerificationScreen(
                    bitmap = capture(),
                    proposal = proposal(),
                    onConfirm = { _, _ -> },
                    onReject = {},
                    onRetake = {},
                )
            }
        }

        rule.onNodeWithText("12 g per 100 g").assertIsDisplayed()
        rule.onNodeWithText("Check this against the label").assertIsDisplayed()
        rule.onNodeWithText("From: Hidratos de carbono 12g").assertIsDisplayed()
    }

    // ------------------------------------------------------- release only on a terminal action

    @Test
    fun theCaptureIsNotReleasedWhileTheProposalIsOnScreen() {
        val bitmap = capture()
        var released = false
        rule.setContent {
            JustTheCarbsTheme {
                VerificationScreen(
                    bitmap = bitmap,
                    proposal = proposal(),
                    onConfirm = { _, _ -> released = true },
                    onReject = {},
                    onRetake = {},
                )
            }
        }
        rule.waitForIdle()

        assertFalse("nothing terminal has happened yet", released)
        assertFalse("the bitmap must still be usable", bitmap.isRecycled)
    }

    @Test
    fun confirmingIsTerminalAndCarriesTheAmountAndBasis() {
        var confirmed: Pair<BigDecimal, NutritionBasis>? = null
        rule.setContent {
            JustTheCarbsTheme {
                VerificationScreen(
                    bitmap = capture(),
                    proposal = proposal(),
                    onConfirm = { value, basis -> confirmed = value to basis },
                    onReject = {},
                    onRetake = {},
                )
            }
        }

        rule.onNodeWithTag(VERIFY_CONFIRM_TAG).performClick()

        assertEquals(BigDecimal("12"), confirmed?.first)
        assertEquals(NutritionBasis.PER_100_G, confirmed?.second)
    }

    /**
     * Rejecting hands to the assisted path **with the photograph intact** and **without prefilling
     * the rejected number** — a prefilled `12` would be the app proposing it a second time.
     */
    @Test
    fun rejectingLeadsToFocusedEntryWithTheBitmapIntactAndNoPrefill() {
        val bitmap = capture()
        rule.setContent {
            // `remember`, not a bare `mutableStateOf`: without it the flag is recreated on every
            // recomposition and the rejection never sticks.
            var rejected by remember { mutableStateOf(false) }
            JustTheCarbsTheme {
                if (rejected) {
                    AssistedReadingScreen(
                        bitmap = bitmap,
                        state = AssistState(document = document(), scaleAmbiguous = true),
                        onUseValue = { _, _ -> },
                        onRetake = {},
                        onClose = {},
                    )
                } else {
                    VerificationScreen(
                        bitmap = bitmap,
                        proposal = proposal(),
                        onConfirm = { _, _ -> },
                        onReject = { rejected = true },
                        onRetake = {},
                    )
                }
            }
        }

        rule.onNodeWithTag(VERIFY_REJECT_TAG).performClick()
        rule.waitForIdle()

        assertFalse("the photograph must survive a rejection", bitmap.isRecycled)

        // Focused entry is reachable, and the field it opens is EMPTY. A prefilled `12` would be
        // the app proposing the rejected number a second time, which is what "Reject/Edit must not
        // prefill a rejected number" forbids.
        rule.onNodeWithText("Type it in").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))

        // The basis the label stated survives the rejection, and is stated without a picker.
        rule.onNodeWithText("100 g", substring = true).assertExists()
    }

    @Test
    fun retakingIsTerminalAndIsOfferedFromTheProposal() {
        var retaken = 0
        rule.setContent {
            JustTheCarbsTheme {
                VerificationScreen(
                    bitmap = capture(),
                    proposal = proposal(),
                    onConfirm = { _, _ -> },
                    onReject = {},
                    onRetake = { retaken++ },
                )
            }
        }

        rule.onNodeWithTag(VERIFY_RETAKE_TAG).performClick()

        assertEquals("retake must fire exactly once per tap", 1, retaken)
    }

    /**
     * Leaving the screen must not recycle the bitmap a second time.
     *
     * `PassAResult.recycle()` guards on `isRecycled`, so this is idempotent by construction — pinned
     * here because the scanner calls it from both `onDispose` and every terminal branch, and a
     * future change that drops the guard would crash on an ordinary retake.
     */
    @Test
    fun disposingTwiceDoesNotThrow() {
        val bitmap = capture()
        bitmap.recycle()
        bitmap.recycle()
        assertTrue(bitmap.isRecycled)
    }

    // ------------------------------------------------- ninth session: the correct value must show

    /**
     * The white Dutch table's carbohydrate row, as Strategy B recognised it
     * (`docs/Scan Evidence 03-09/20260903-085019-213`).
     *
     * The package prints `Koolhydraten, waarvan 2,8 g` under `Gemiddelde voedingswaarde per 100g`.
     */
    private fun whiteTableDocument() = OcrDocument(
        width = 421,
        height = 912,
        elements = listOf(
            OcrElement("Gemiddelde", OcrBox(82, 296, 163, 315), 0, 0),
            OcrElement("voedingswaarde", OcrBox(169, 297, 278, 315), 0, 0),
            OcrElement("per", OcrBox(283, 298, 303, 315), 0, 0),
            OcrElement("100g", OcrBox(309, 299, 338, 316), 0, 0),
            OcrElement("Koolhydraten,", OcrBox(85, 380, 179, 397), 1, 0),
            OcrElement("waarvan", OcrBox(184, 380, 236, 397), 1, 0),
            OcrElement("2.8", OcrBox(326, 377, 345, 392), 1, 0),
            OcrElement("g", OcrBox(350, 379, 357, 393), 1, 0),
        ),
    )

    private fun whiteTableProposal(): EvidenceResolver.Outcome.NeedsVerification {
        val candidate = CarbCandidate(
            sourceLine = "Koolhydraten, waarvan 2.8 g",
            label = "Koolhydraten,",
            value = BigDecimal("2.8"),
            basis = NutritionBasis.PER_100_G,
            score = 105,
            geometry = OcrBox(85, 377, 357, 397),
            evidence = emptyList(),
            column = NutritionColumnKind.PER_100_G,
        )
        return EvidenceResolver.Outcome.NeedsVerification(
            reading = LabelReading.Confident(candidate),
            report = NutritionParseReport(
                reading = LabelReading.Confident(candidate),
                diagnostics = emptyList(),
            ),
            source = EvidenceSource.SELECTED_REGION_OCR,
        )
    }

    /**
     * **The ninth session's defect, at the level where it was visible.**
     *
     * Three captures held a correct reading that the automatic veto discarded before any screen
     * could render it, so the user was sent to the crop screen and then offered nothing. The gate
     * that decides this is pinned in the JVM
     * ([app.justthecarbs.ocr.NinthSessionRegressionTest]); this pins that the state it now produces
     * actually shows the value, on the photograph, with the basis the label stated.
     */
    @Test
    fun aCorrectUncorroboratedReadingIsShownOnTheFrozenPhotograph() {
        rule.setContent {
            JustTheCarbsTheme {
                VerificationScreen(
                    bitmap = capture(),
                    proposal = whiteTableProposal(),
                    onConfirm = { _, _ -> },
                    onReject = {},
                    onRetake = {},
                )
            }
        }

        rule.onNodeWithTag(VERIFY_PHOTO_TAG).assertIsDisplayed()
        rule.onNodeWithTag(VERIFY_ZOOM_TAG).assertIsDisplayed()
        rule.onNodeWithText("2.8 g per 100 g").assertIsDisplayed()
        rule.onNodeWithText("From: Koolhydraten, waarvan 2.8 g").assertIsDisplayed()
    }

    /** Confirming it hands the calculator the printed value and the basis the label stated. */
    @Test
    fun confirmingTheCorrectReadingCarriesItsPrintedValue() {
        var confirmed: Pair<BigDecimal, NutritionBasis>? = null
        rule.setContent {
            JustTheCarbsTheme {
                VerificationScreen(
                    bitmap = capture(),
                    proposal = whiteTableProposal(),
                    onConfirm = { value, basis -> confirmed = value to basis },
                    onReject = {},
                    onRetake = {},
                )
            }
        }

        rule.onNodeWithTag(VERIFY_CONFIRM_TAG).performClick()

        assertEquals(0, confirmed?.first?.compareTo(BigDecimal("2.8")))
        assertEquals(NutritionBasis.PER_100_G, confirmed?.second)
    }

    /**
     * One unsuccessful step reaches focused entry — no repeated "Read table" / "Tap the row" loop.
     *
     * The reject path opens the assisted screen with the basis preserved and the field empty, so the
     * user types the digits once rather than being asked to re-aim at a label they have already
     * photographed.
     */
    @Test
    fun rejectingTheCorrectReadingStillReachesFocusedEntryInOneStep() {
        val bitmap = capture()
        rule.setContent {
            var rejected by remember { mutableStateOf(false) }
            JustTheCarbsTheme {
                if (rejected) {
                    AssistedReadingScreen(
                        bitmap = bitmap,
                        state = AssistState(document = whiteTableDocument(), scaleAmbiguous = true),
                        onUseValue = { _, _ -> },
                        onRetake = {},
                        onClose = {},
                    )
                } else {
                    VerificationScreen(
                        bitmap = bitmap,
                        proposal = whiteTableProposal(),
                        onConfirm = { _, _ -> },
                        onReject = { rejected = true },
                        onRetake = {},
                    )
                }
            }
        }

        rule.onNodeWithTag(VERIFY_REJECT_TAG).performClick()
        rule.waitForIdle()

        assertFalse("the photograph must survive a rejection", bitmap.isRecycled)
        rule.onNodeWithText("Type it in").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        rule.onNodeWithText("100 g", substring = true).assertExists()
    }

    /**
     * A withheld reading offers focused entry **immediately**, without demanding a failed tap first.
     *
     * ## The dead end this closes (eleventh session, `20260903-142926-419`)
     *
     * The Lidl cracker prints `72,0 g / 100 g`. Three sibling captures in the same session read
     * exactly that and two of them auto-advanced. On this one ML Kit landed on the Spanish row and
     * returned the value as `72g` — the separator gone — so [app.justthecarbs.ocr.ReadingEligibility]
     * refused it and the scanner routed to recovery with `scaleAmbiguous = true`.
     *
     * Every part of that is correct, and the user was still stuck: the withheld value is by
     * definition absent from the labelled choices, so the screen offered only *Tap the carbohydrate
     * row* and *Type it in*. Focused entry — the one screen that already knew the row **and** the
     * basis — was reachable only after a tap had been made and found fruitless.
     *
     * **The app had established both facts before the screen was drawn.** Requiring the user to
     * discover that by failing is the same dead-end shape the assisted path exists to remove, and
     * `EleventhSessionBaselineTest` pins that `FocusedAmountEntry.of` resolves this document's row
     * and basis with no interaction at all.
     *
     * This asserts the offer is present on arrival. It does **not** assert the digits appear: `72`
     * stays withheld on every screen, which the case below pins separately.
     */
    @Test
    fun aWithheldReadingOffersFocusedEntryWithoutRequiringAFailedTapFirst() {
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = capture(),
                    state = AssistState(document = whiteTableDocument(), scaleAmbiguous = true),
                    onUseValue = { _, _ -> },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        rule.onNodeWithText("Enter the value printed under 100 g").assertIsDisplayed()
    }

    /**
     * The offer appears **only** when the reading was withheld, never on an ordinary failed read.
     *
     * Without this the change would be "always show focused entry", which is a different and worse
     * screen: on a label where nothing was established, an offer to type "the value printed under
     * 100 g" names a basis the app never read. The tap route stays first for those, because there
     * the tap is genuinely the user's lever.
     */
    @Test
    fun anOrdinaryFailedReadDoesNotOfferFocusedEntryUpFront() {
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = capture(),
                    state = AssistState(document = whiteTableDocument(), scaleAmbiguous = false),
                    onUseValue = { _, _ -> },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        rule.onNodeWithText("Enter the value printed under 100 g").assertDoesNotExist()
        rule.onNodeWithText("Tap the carbohydrate row").assertIsDisplayed()
    }

    /**
     * Reaching focused entry from the offer still withholds the digits.
     *
     * The whole point of the refusal is that `72` is not evidence; a shortcut that arrived with it
     * prefilled would hand the user the number the app just declined to stand behind, with the
     * app's own authority attached.
     */
    @Test
    fun theFocusedEntryOfferNeverPrefillsTheWithheldDigits() {
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = capture(),
                    state = AssistState(document = whiteTableDocument(), scaleAmbiguous = true),
                    onUseValue = { _, _ -> },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        rule.onNodeWithText("Enter the value printed under 100 g").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(ASSIST_FOCUSED_FIELD_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }
}

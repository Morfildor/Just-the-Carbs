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

    /**
     * The coconut-milk shape: a plainly printed `per 100 ml` header over the value column.
     *
     * The header is laid out *centred over the column it describes*, because a header phrase on a
     * real package heads its column rather than starting at the label margin. A fixture that lays
     * every word left-to-right from one origin pushes the header hundreds of pixels away from the
     * values and models a package that does not exist — this repo has already been caught building
     * exactly that fixture once, and tuning against it.
     */
    private fun millilitreDocument() = OcrDocument(
        width = 1200,
        height = 400,
        elements = listOf(
            OcrElement("per", OcrBox(330, 90, 380, 115), 0, 0),
            OcrElement("100", OcrBox(388, 90, 440, 115), 0, 0),
            OcrElement("ml", OcrBox(448, 90, 490, 115), 0, 0),
            OcrElement("Koolhydraten", OcrBox(60, 180, 220, 205), 1, 0),
            OcrElement("2,5", OcrBox(380, 180, 450, 205), 1, 0),
            OcrElement("waarvan", OcrBox(80, 250, 170, 275), 2, 0),
            OcrElement("suikers", OcrBox(178, 250, 250, 275), 2, 0),
            OcrElement("1,9", OcrBox(380, 250, 450, 275), 2, 0),
        ),
    )

    /** The same shape stating grams, for the symmetric case. */
    private fun gramDocument() = OcrDocument(
        width = 1200,
        height = 400,
        elements = listOf(
            OcrElement("per", OcrBox(330, 90, 380, 115), 0, 0),
            OcrElement("100", OcrBox(388, 90, 440, 115), 0, 0),
            OcrElement("g", OcrBox(448, 90, 470, 115), 0, 0),
            OcrElement("Koolhydraten", OcrBox(60, 180, 220, 205), 1, 0),
            OcrElement("53,5", OcrBox(380, 180, 450, 205), 1, 0),
        ),
    )

    /** Both bases printed as separate columns — an ambiguity, not a confident basis. */
    private fun bothBasesDocument() = OcrDocument(
        width = 1200,
        height = 400,
        elements = listOf(
            OcrElement("per", OcrBox(330, 90, 380, 115), 0, 0),
            OcrElement("100", OcrBox(388, 90, 440, 115), 0, 0),
            OcrElement("g", OcrBox(448, 90, 470, 115), 0, 0),
            OcrElement("per", OcrBox(700, 90, 750, 115), 0, 0),
            OcrElement("100", OcrBox(758, 90, 810, 115), 0, 0),
            OcrElement("ml", OcrBox(818, 90, 860, 115), 0, 0),
            OcrElement("Koolhydraten", OcrBox(60, 180, 220, 205), 1, 0),
            OcrElement("53,5", OcrBox(380, 180, 450, 205), 1, 0),
            OcrElement("48,1", OcrBox(750, 180, 820, 205), 1, 0),
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

    /**
     * An empty field cannot complete the scan — there would be no value to complete it with.
     *
     * The accept actions are now *absent* rather than present-and-disabled: with no parsed value
     * there is no value to judge, so there is nothing to offer. Previously this asserted that
     * clicking a disabled button did nothing, which is a weaker claim and — as the 1.0.3 P0 work
     * showed — one that a disabled control satisfies while still telling the user nothing.
     */
    @Test
    fun anEmptyInlineValueOffersNoAcceptAction() {
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

        rule.onNodeWithText("Use / 100 g").assertDoesNotExist()
        rule.onNodeWithText("Use / 100 ml").assertDoesNotExist()
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

    // ---- P2: an unchanged crop says so, accurately -----------------------------------------------

    /**
     * Confirming a crop that had already been recognised lands here and explains why (1.0.3 P2).
     *
     * The pass is skipped because recognition is deterministic: the same rectangle over the same
     * retained elements and the same pixels produces the outcome that already declined. So the
     * user goes straight to the assisted path rather than waiting ~400 ms to be told the same
     * thing a second time.
     */
    @Test
    fun anUnchangedCropExplainsThatReadingItAgainWouldGiveTheSameAnswer() {
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = AssistState(document = document(), cropUnchanged = true),
                    onUseValue = { _, _ -> },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        rule.onNodeWithText(
            "Same box as before, so it would read the same. " +
                "Point at the carbohydrate figure below, or retake to move the box.",
        ).assertIsDisplayed()
    }

    /**
     * The unchanged-crop message must not be replaced by the too-wide one.
     *
     * They are different claims and only one is true here: "your box kept nearly the whole photo"
     * is about the box's *size*, and an unchanged box may be perfectly tight. Telling the user to
     * tighten a crop that is already tight sends them to fix something that is not wrong, which is
     * why this is a separate flag rather than a reuse of `ineffectiveSelection`.
     */
    @Test
    fun anUnchangedCropIsNotDescribedAsATooWideOne() {
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = AssistState(
                        document = document(),
                        ineffectiveSelection = true,
                        cropUnchanged = true,
                    ),
                    onUseValue = { _, _ -> },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        rule.onNodeWithText(
            "Your box kept nearly the whole photo, so this read the same thing as before. " +
                "Retake and tighten it, or just point at the figure below.",
        ).assertDoesNotExist()
    }

    // ---- P0: an impossible value never gets an ordinary accept action ----------------------------

    /**
     * THE 1.0.3 P0 property, measured on a physical device.
     *
     * A red label printing about `7,9 g` produced `790` and `794` through the assisted path, and
     * those were offered through the same two full-emphasis `Use / …` buttons an ordinary value
     * gets. 790 g of carbohydrate in 100 g of food cannot exist — the carbohydrate would outweigh
     * the food — and neither can it exist in 100 ml. So there is no unit under which the number is
     * a reading, and no accept action may be offered for it under any unit.
     *
     * The screen must still show the number. Hiding it would leave the user with no idea why the
     * app stopped, and the number is their evidence that the read went wrong.
     */
    @Test
    fun anImpossibleTypedValueIsRefusedUnderEveryBasis() {
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
        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG).performTextInput("790")

        // Neither ordinary accept action exists for a value that cannot be a carbohydrate figure.
        rule.onNodeWithText("Use / 100 g").assertDoesNotExist()
        rule.onNodeWithText("Use / 100 ml").assertDoesNotExist()
        assertNull("an impossible value must not reach the calculator", used)
    }

    /** The second value the same physical label produced. Same rule, no special-casing. */
    @Test
    fun theOtherImpossibleValueFromTheSameLabelIsAlsoRefused() {
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

        rule.onNodeWithText("Type it in").performClick()
        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG).performTextInput("794")

        rule.onNodeWithText("Use / 100 g").assertDoesNotExist()
        rule.onNodeWithText("Use / 100 ml").assertDoesNotExist()
    }

    /**
     * Refusal must say so, and must route somewhere.
     *
     * A disabled button with no explanation is the same dead end this whole screen exists to
     * remove — the user would be left tapping something that does nothing. The screen says the
     * figure cannot be right and leaves the field editable, which is the correction path.
     */
    @Test
    fun anImpossibleValueExplainsItselfRatherThanJustDisablingTheAction() {
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

        rule.onNodeWithText("Type it in").performClick()
        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG).performTextInput("790")

        rule.onNodeWithText("That can't be right — check the figure.").assertIsDisplayed()
    }

    /**
     * NO DECIMAL IS INFERRED. The refusal does not become a repair.
     *
     * `790` is not silently offered as `79.0` or `7.90`: the decimal point is what OCR is least
     * reliable about, so repositioning it guesses at exactly the wrong thing — and a wrong repair
     * is invisible where a refusal is not.
     */
    @Test
    fun anImpossibleValueIsNeverSilentlyCorrectedToAPlausibleOne() {
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

        rule.onNodeWithText("Type it in").performClick()
        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG).performTextInput("790")

        // None of the plausible re-readings of "790" is offered anywhere on screen.
        rule.onNodeWithText("79.0").assertDoesNotExist()
        rule.onNodeWithText("7.90").assertDoesNotExist()
        rule.onNodeWithText("7.9").assertDoesNotExist()
    }

    /** The barrier must not cost an ordinary value its actions. The regression guard for P0. */
    @Test
    fun anOrdinaryValueKeepsBothAcceptActions() {
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

        rule.onNodeWithText("Type it in").performClick()
        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG).performTextInput("7.9")

        rule.onNodeWithText("Use / 100 g").assertIsDisplayed()
        rule.onNodeWithText("Use / 100 ml").assertIsDisplayed()
        rule.onNodeWithText("That can't be right — check the figure.").assertDoesNotExist()
    }

    /**
     * A value impossible per 100 g but legitimate per 100 ml keeps exactly the action that fits.
     *
     * This is why the barrier is asked per basis rather than once. 150 g of carbohydrate cannot be
     * in 100 g of anything, but a concentrated syrup at 150 g per 100 ml is real — so refusing both
     * buttons here would block a correct reading, and offering both would allow an impossible one.
     */
    @Test
    fun aValuePossibleOnlyPerMillilitreKeepsOnlyThatAction() {
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
        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG).performTextInput("150")

        rule.onNodeWithText("Use / 100 g").assertDoesNotExist()
        rule.onNodeWithText("Use / 100 ml").performClick()

        assertEquals(NutritionBasis.PER_100_ML, used?.second)
        assertEquals(0, used!!.first.compareTo(BigDecimal("150")))
    }

    // ---- P1: a confidently-read basis survives value assistance -----------------------------------

    /**
     * THE 1.0.3 P1 property, also measured on a physical device.
     *
     * A coconut-milk table printed `per 100 ml` plainly enough that the parser classified the
     * column — but the *value* needed assistance, and the app then asked "2.5 g carbs — per what?".
     * Value confidence and basis confidence are separate facts, and flattening them into one
     * all-or-nothing state made the app discard something it had already established, then ask the
     * user for it again with a wrong answer one tap away.
     *
     * The basis here is read from the same document the screen already holds, so this costs no
     * recognition and invents nothing.
     */
    @Test
    fun aConfidentlyReadMillilitreBasisIsNotAskedForAgain() {
        var used: Pair<BigDecimal, NutritionBasis>? = null
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = AssistState(document = millilitreDocument()),
                    onUseValue = { value, basis -> used = value to basis },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        rule.onNodeWithText("Type it in").performClick()
        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG).performTextInput("2.5")
        rule.onNodeWithText("Use / 100 ml").performClick()

        assertEquals(NutritionBasis.PER_100_ML, used?.second)
        assertEquals(0, used!!.first.compareTo(BigDecimal("2.5")))
    }

    /** The label states the basis, so the screen says so instead of posing it as a question. */
    @Test
    fun aConfidentBasisIsStatedRatherThanAsked() {
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = AssistState(document = millilitreDocument()),
                    onUseValue = { _, _ -> },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        rule.onNodeWithText("Type it in").performClick()
        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG).performTextInput("2.5")

        rule.onNodeWithText("Read from the label as per 100 ml").assertIsDisplayed()
    }

    /** Symmetry: a confidently-read gram basis survives too. */
    @Test
    fun aConfidentlyReadGramBasisIsNotAskedForAgain() {
        var used: Pair<BigDecimal, NutritionBasis>? = null
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = AssistState(document = gramDocument()),
                    onUseValue = { value, basis -> used = value to basis },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        rule.onNodeWithText("Type it in").performClick()
        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG).performTextInput("53.5")
        rule.onNodeWithText("Use / 100 g").performClick()

        assertEquals(NutritionBasis.PER_100_G, used?.second)
    }

    /**
     * The safety half of P1: an unstated basis is still asked for.
     *
     * Preserving a basis is only safe while it is one the label actually stated. The base fixture
     * prints no per-100 header at all, so nothing here may be assumed and both choices stand —
     * which is the pre-existing behaviour, deliberately unchanged.
     */
    @Test
    fun aDocumentStatingNoBasisStillAsksTheUser() {
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

        rule.onNodeWithText("Type it in").performClick()
        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG).performTextInput("53.5")

        rule.onNodeWithText("Use / 100 g").assertIsDisplayed()
        rule.onNodeWithText("Use / 100 ml").assertIsDisplayed()
        rule.onNodeWithText("Read from the label as per 100 ml").assertDoesNotExist()
    }

    /**
     * A label stating BOTH bases is not a confident basis — it is exactly the ambiguity the user
     * must resolve. Auto-selecting either would be the "grams of what?" error with extra steps.
     */
    @Test
    fun aDocumentStatingBothBasesStillAsksTheUser() {
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = AssistState(document = bothBasesDocument()),
                    onUseValue = { _, _ -> },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        rule.onNodeWithText("Type it in").performClick()
        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG).performTextInput("53.5")

        rule.onNodeWithText("Use / 100 g").assertIsDisplayed()
        rule.onNodeWithText("Use / 100 ml").assertIsDisplayed()
    }

    /**
     * P0 and P1 together: a preserved basis does not smuggle an impossible value through.
     *
     * The basis being known says nothing about whether the number is possible, so the plausibility
     * barrier still applies — and with only one basis on offer, an impossible value leaves no
     * accept action at all.
     */
    @Test
    fun aPreservedBasisDoesNotBypassThePlausibilityBarrier() {
        var used: Pair<BigDecimal, NutritionBasis>? = null
        rule.setContent {
            JustTheCarbsTheme {
                AssistedReadingScreen(
                    bitmap = bitmap(),
                    state = AssistState(document = millilitreDocument()),
                    onUseValue = { value, basis -> used = value to basis },
                    onRetake = {},
                    onClose = {},
                )
            }
        }

        rule.onNodeWithText("Type it in").performClick()
        rule.onNodeWithTag(ASSIST_MANUAL_FIELD_TAG).performTextInput("790")

        rule.onNodeWithText("Use / 100 ml").assertDoesNotExist()
        rule.onNodeWithText("Use / 100 g").assertDoesNotExist()
        assertNull(used)
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

package app.carbscan.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.carbscan.domain.AppSettings
import app.carbscan.domain.CarbCalculator
import app.carbscan.domain.InputMode
import app.carbscan.domain.NutritionBasis
import app.carbscan.domain.PortionParser
import app.carbscan.domain.PortionResolver
import app.carbscan.domain.PortionUnit
import app.carbscan.domain.PortionUnitKind
import app.carbscan.domain.Product
import app.carbscan.domain.ProductDataOrigin
import app.carbscan.domain.VerificationStatus
import app.carbscan.ui.product.ADD_PORTION_UNIT_FIELD_TAG
import app.carbscan.ui.product.PORTION_CORRECTION_FIELD_TAG
import app.carbscan.ui.product.ProductScreen
import app.carbscan.ui.product.ProductUiState
import app.carbscan.ui.theme.CarbScanTheme
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Countable-portions brief §22: high-value Compose tests for "2 slices" style entry.
 *
 * Same philosophy as [ProductScreenTest]: state is held locally with `remember`, mirroring what
 * [app.carbscan.ui.product.ProductViewModel] does, so the test exercises the real screen and the
 * real [PortionResolver]/[CarbCalculator], not a stub.
 *
 * **Instrumented: needs a device or emulator.**
 */
class CountablePortionScreenTest {

    @get:Rule
    val compose = createComposeRule()

    /*
     * ON THE ORDER-DEPENDENT FLAKINESS (development-pass brief §27)
     *
     * Root cause, found by reading the actual full-suite failures rather than re-reading the code:
     * a control that the soft keyboard covers is not clickable, but `performClick()` on it does not
     * throw — it clicks nothing. The expected state transition silently never happens, and a later
     * assertion then fails for a reason unrelated to what it was checking. That is also why the
     * previously-recorded symptom was a `waitUntil` poll *timing out* rather than eventually
     * succeeding: nothing was ever going to change.
     *
     * The fix is per-interaction and targeted: any control that can sit below the fold while the
     * keyboard is open gets `performScrollTo()` before it is clicked. Two blunter approaches were
     * tried and rejected — a BACK keyevent in @After dismisses the keyboard only when one is open
     * and otherwise finishes the test activity (it killed the run after 30 of 45 tests), and an
     * `ime reset` per test is slow global device surgery for what is really a local mistake in the
     * test's own interaction.
     *
     * No retry, no sleep, and no weakened assertion was used.
     */

    private val now = Instant.parse("2026-08-14T10:00:00Z")

    private fun product(carbsPer100: String = "42", packageAmount: String? = null) = Product(
        barcode = "5449000000996",
        name = "Sliced Bread",
        carbsPer100 = BigDecimal(carbsPer100),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
        packageAmount = packageAmount?.let(::BigDecimal),
    )

    private fun sliceUnit(
        id: Long = 1,
        amountPerUnit: String = "36",
        verification: VerificationStatus = VerificationStatus.UNVERIFIED,
    ) = PortionUnit(
        id = id,
        productBarcode = "5449000000996",
        kind = PortionUnitKind.SLICE,
        customLabel = null,
        amountPerUnit = BigDecimal(amountPerUnit),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = verification,
        verifiedAt = if (verification == VerificationStatus.USER_VERIFIED) now else null,
        originalRemoteAmountPerUnit = BigDecimal(amountPerUnit),
        latestRemoteAmountPerUnit = BigDecimal(amountPerUnit),
        rawRemoteServingText = "1 slice ($amountPerUnit g)",
        createdAt = now,
        updatedAt = now,
    )

    /** Full local-state harness, matching [app.carbscan.ui.product.ProductViewModel]'s own logic. */
    private fun showCalculator(
        product: Product = product(),
        initialUnits: List<PortionUnit> = listOf(sliceUnit()),
    ) {
        compose.setContent {
            var portionText by remember { mutableStateOf("") }
            var countText by remember { mutableStateOf("") }
            var mode by remember { mutableStateOf(InputMode.GRAMS) }
            var selectedUnitId by remember { mutableStateOf<Long?>(null) }
            var units by remember { mutableStateOf(initialUnits) }
            var showAddForm by remember { mutableStateOf(false) }
            var newerRemoteAmount by remember { mutableStateOf<BigDecimal?>(null) }
            var correcting by remember { mutableStateOf(false) }

            fun recalcFromCount(unit: PortionUnit, text: String) {
                val count = PortionParser.parse(text)
                portionText = if (count == null) "" else {
                    PortionResolver.resolve(count, unit.amountPerUnit).stripTrailingZeros().toPlainString()
                }
            }

            val parsedPortion = PortionParser.parse(portionText)
            val result = parsedPortion?.let { CarbCalculator.calculate(product.carbsPer100, it, product.basis) }

            CarbScanTheme {
                ProductScreen(
                    state = ProductUiState(
                        loading = false,
                        product = product,
                        portionText = portionText,
                        countText = countText,
                        inputMode = mode,
                        selectedPortionUnitId = selectedUnitId,
                        portionUnits = units,
                        showAddPortionUnitForm = showAddForm,
                        newerRemotePortionUnitAmount = newerRemoteAmount,
                        correctingPortionUnit = correcting,
                        result = result,
                        barcode = product.barcode,
                    ),
                    settings = AppSettings(),
                    onPortionChanged = { portionText = it },
                    onAdjust = {},
                    onSetPortion = { portionText = it.stripTrailingZeros().toPlainString() },
                    onToggleFavorite = {},
                    onBack = {},
                    onVerify = {},
                    onDismissVerify = {},
                    onConfirmVerification = { _, _, _ -> },
                    onResetOnline = {},
                    onScanLabel = {},
                    onEnterManually = {},
                    onRetry = {},
                    onSwitchToGrams = { mode = InputMode.GRAMS; selectedUnitId = null },
                    onSwitchToPortionUnit = { id ->
                        val unit = units.first { it.id == id }
                        mode = InputMode.PORTION_UNIT
                        selectedUnitId = id
                        if (countText.isBlank()) countText = "1"
                        recalcFromCount(unit, countText)
                    },
                    onCountChanged = { text ->
                        countText = text
                        units.firstOrNull { it.id == selectedUnitId }?.let { recalcFromCount(it, text) }
                    },
                    onShowAddPortionUnitForm = { showAddForm = it },
                    onAddPortionUnit = { kind, amount, label ->
                        val saved = PortionUnit(
                            id = (units.maxOfOrNull { it.id } ?: 0) + 1,
                            productBarcode = product.barcode,
                            kind = kind,
                            customLabel = label,
                            amountPerUnit = amount,
                            basis = product.basis,
                            dataSource = ProductDataOrigin.MANUAL,
                            verificationStatus = VerificationStatus.USER_VERIFIED,
                            verifiedAt = now,
                            originalRemoteAmountPerUnit = null,
                            latestRemoteAmountPerUnit = null,
                            rawRemoteServingText = null,
                            createdAt = now,
                            updatedAt = now,
                        )
                        units = units + saved
                        showAddForm = false
                        mode = InputMode.PORTION_UNIT
                        selectedUnitId = saved.id
                        countText = "1"
                        recalcFromCount(saved, "1")
                    },
                    // Mirrors ProductViewModel: the tap OPENS the correction form rather than
                    // verifying outright, so the user confirms against the package before the app
                    // records that they did (§3.3).
                    onVerifyPortionUnit = { correcting = true },
                    onCancelPortionUnitCorrection = { correcting = false },
                    onCorrectPortionUnit = { amount ->
                        val id = selectedUnitId ?: return@ProductScreen
                        units = units.map {
                            if (it.id == id) {
                                it.copy(
                                    amountPerUnit = amount,
                                    verificationStatus = VerificationStatus.USER_VERIFIED,
                                    verifiedAt = now,
                                )
                            } else {
                                it
                            }
                        }
                        correcting = false
                        units.firstOrNull { it.id == id }?.let { recalcFromCount(it, countText) }
                    },
                    onApplyNewerRemotePortionUnit = {
                        val id = selectedUnitId ?: return@ProductScreen
                        val newer = newerRemoteAmount ?: return@ProductScreen
                        units = units.map { if (it.id == id) it.copy(amountPerUnit = newer) else it }
                        newerRemoteAmount = null
                        units.firstOrNull { it.id == id }?.let { recalcFromCount(it, countText) }
                    },
                    onDismissNewerRemotePortionUnit = { newerRemoteAmount = null },
                )
            }
        }
    }

    // ---- the acceptance test: 2 slices -> 30.2 g -----------------------------------------------

    @Test
    fun selectingSlicesAndEnteringTwoShowsTheDerivedGramsAndCorrectResult() {
        showCalculator(product())

        compose.onNodeWithText("Slices").performClick()
        compose.onNode(countField()).performTextReplacement("2")

        // 42 x 72 / 100 = 30.24
        compose.onNodeWithText("2 slices × 36 g = 72 g").assertIsDisplayed()
        compose.onNodeWithText("30.2 g").assertIsDisplayed()
    }

    /**
     * Regression for the count-field append defect (development-pass brief §3.1).
     *
     * Selecting a countable unit pre-fills the count with `1`. A user who wants 2 taps the field and
     * types `2` without clearing it first. Before the fix that produced **12**, because the field
     * did not select its contents on focus.
     *
     * Deliberately uses `performTextInput` (which types, appending at the cursor) rather than
     * `performTextReplacement` (which replaces wholesale). Every pre-existing countable test used
     * the latter, which is exactly why this defect shipped: the harness never reproduced what a real
     * thumb does.
     */
    @Test
    fun typingACountOverThePrefilledOneReplacesItRatherThanAppending() {
        showCalculator(product())

        compose.onNodeWithText("Slices").performClick()
        // The field is now pre-filled with "1". Type "2" into it without clearing.
        compose.onNode(countField()).performClick()
        compose.onNode(countField()).performTextInput("2")

        // 2 slices, not 12. 42 x 72 / 100 = 30.24 -> 30.2 g
        compose.onNodeWithText("2 slices × 36 g = 72 g").assertIsDisplayed()
        compose.onNodeWithText("30.2 g").assertIsDisplayed()
        // And the 12-slice reading (42 x 432 / 100 = 181.44) must not be anywhere on screen.
        compose.onAllNodesWithText("181.4 g").assertCountEquals(0)
    }

    @Test
    fun aProductWithNoPortionUnitsKeepsTheOriginalSingleFieldLayout() {
        showCalculator(product(), initialUnits = emptyList())

        compose.onAllNodesWithText("Slices").assertCountEquals(0)
        compose.onAllNodesWithText("Grams").assertCountEquals(0)
    }

    // ---- mode switching --------------------------------------------------------------------

    @Test
    fun switchingFromSlicesBackToGramsKeepsTheEquivalentGramAmount() {
        showCalculator(product())

        compose.onNodeWithText("Slices").performClick()
        compose.onNode(countField()).performTextReplacement("2")
        compose.onNodeWithText("30.2 g").assertIsDisplayed()

        compose.onNodeWithText("Grams").performClick()

        // Switching mode is immediate and does not clear or reset the result (§12).
        compose.onNodeWithText("30.2 g").assertIsDisplayed()
    }

    @Test
    fun switchingBackToSlicesRestoresTheSameCount() {
        showCalculator(product())

        compose.onNodeWithText("Slices").performClick()
        compose.onNode(countField()).performTextReplacement("2")
        compose.onNodeWithText("Grams").performClick()
        compose.onNodeWithText("Slices").performClick()

        compose.onNodeWithText("2 slices × 36 g = 72 g").assertIsDisplayed()
    }

    // ---- user-defined portion units (§6) -----------------------------------------------------

    @Test
    fun addingAPortionUnitThroughTheInlineFormMakesItImmediatelyUsable() {
        showCalculator(product(), initialUnits = emptyList())

        compose.onNodeWithText("+ Add portion unit").performScrollTo().performClick()
        // Addressed by tag, not by position. This previously indexed into "every text field on
        // screen" ([1]), which is what made the test order-dependent: the set of fields present
        // depends on prior state, so the index silently addressed a different field rather than
        // failing outright. See docs/known-limitations.md for the investigation.
        compose.onNodeWithTag(ADD_PORTION_UNIT_FIELD_TAG).performTextInput("36")
        // Scrolled into view first: typing opened the keyboard, which can cover this button. A
        // click on a covered node silently does nothing rather than failing — see the note above.
        compose.onNodeWithText("Save").performScrollTo().performClick()

        compose.onNodeWithText("1 slice × 36 g = 36 g").assertIsDisplayed()

        compose.onNode(countField()).performTextReplacement("2")
        compose.onNodeWithText("2 slices × 36 g = 72 g").assertIsDisplayed()
        compose.onNodeWithText("30.2 g").assertIsDisplayed()
    }

    @Test
    fun theAddPortionUnitFormRejectsSavingWithNoAmount() {
        showCalculator(product(), initialUnits = emptyList())

        compose.onNodeWithText("+ Add portion unit").performScrollTo().performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()

        // Nothing was added: the mode row still does not exist, and the form is still open.
        compose.onAllNodesWithText("Slices").assertCountEquals(0)
        compose.onNodeWithText("Add portion unit").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aSavedCustomPortionUnitCanBeUsedImmediately() {
        showCalculator(
            product(),
            initialUnits = listOf(
                PortionUnit(
                    id = 9, productBarcode = "5449000000996", kind = PortionUnitKind.CUSTOM,
                    customLabel = "Dumpling", amountPerUnit = BigDecimal("24"), basis = NutritionBasis.PER_100_G,
                    dataSource = ProductDataOrigin.MANUAL, verificationStatus = VerificationStatus.USER_VERIFIED,
                    verifiedAt = now, originalRemoteAmountPerUnit = null, latestRemoteAmountPerUnit = null,
                    rawRemoteServingText = null, createdAt = now, updatedAt = now,
                ),
            ),
        )

        compose.onNodeWithText("Dumpling").performClick()
        compose.onNode(countField()).performTextReplacement("3")

        // 42 x 72 / 100 = 30.24 (3 x 24 g = 72 g, same resolved amount as the slice example)
        compose.onNodeWithText("3 Dumpling × 24 g = 72 g").assertIsDisplayed()
        compose.onNodeWithText("30.2 g").assertIsDisplayed()
    }

    // ---- inline correction of a wrong remote weight (development-pass brief §3.3) --------------

    /**
     * Acceptance scenario C: Open Food Facts says `1 slice = 36 g`, the package says 38 g. The user
     * corrects it inline and verifies — without creating a second competing "Slice" unit.
     */
    @Test
    fun correctingTheOnlinePortionWeightInlineUsesTheCorrectedValue() {
        showCalculator(product())

        compose.onNodeWithText("Slices").performClick()
        compose.onNode(countField()).performTextReplacement("2")
        // 42 x (2 x 36) / 100 = 30.24
        compose.onNodeWithText("30.2 g").assertIsDisplayed()

        // Typing the count left the soft keyboard open, and a control the keyboard covers is not
        // clickable even though performClick() does not throw (see the note at the top of this
        // file). Committing the text field's IME action closes the keyboard first, so the click
        // that follows lands on a control the user could actually reach.
        compose.onNode(countField()).performImeAction()
        compose.waitForIdle()
        compose.onNodeWithText("Online portion").performScrollTo().performClick()
        compose.waitForIdle()
        // The form pre-fills with the current 36 so confirming a correct weight is one tap.
        //
        // Asserted on the field by tag rather than on the title text: expanding the form changes
        // the scroll region's content height, which re-runs the screen's own "keep the bottom
        // anchored" scroll. Asserting a node's on-screen position immediately after triggering
        // that scroll races it. The tagged field is the thing this test actually needs, and
        // waiting for it to exist is the honest precondition.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(PORTION_CORRECTION_FIELD_TAG).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag(PORTION_CORRECTION_FIELD_TAG).performScrollTo().performTextReplacement("38")
        // Scrolled to first: the correction form lives in the scrollable zone, so with the IME open
        // its Save button can sit below the fold. (The equation and result do not need this — that
        // is the whole point of pinning them outside the scroll region.)
        compose.onNodeWithText("Save as verified").performScrollTo().performClick()

        // 42 x (2 x 38) / 100 = 31.92, and the equation now reads 38 g per slice.
        compose.onNodeWithText("2 slices × 38 g = 76 g").assertIsDisplayed()
        compose.onNodeWithText("31.9 g").assertIsDisplayed()
        // Exactly one countable unit still exists — a correction, not a competing second unit.
        compose.onAllNodesWithText("Slices").assertCountEquals(1)
    }

    @Test
    fun correctingAPortionWeightMarksItVerifiedWhileKeepingRemoteProvenance() {
        showCalculator(product())

        compose.onNodeWithText("Slices").performClick()
        compose.onNodeWithText("Online portion").performScrollTo().performClick()
        compose.onNodeWithText("Save as verified").performScrollTo().performClick()

        // Provenance is Open Food Facts throughout; only the verification state changed, so the
        // badge moves from "Online portion" to the verified marker (§23's split, per unit).
        compose.onNodeWithText("✓ Verified by you").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Online portion").assertCountEquals(0)
    }


    // ---- session immutability (§9) -----------------------------------------------------------

    @Test
    fun aChangedOnlinePortionIsShownAsANoticeWithoutMovingTheResult() {
        // Simulates a background refresh having recorded a newer remote amount (§9): the session's
        // own result must still reflect the frozen 36 g snapshot, not the new 38 g.
        showCalculatorWithNotice(product(), sliceUnit(), BigDecimal("38"))

        compose.onNodeWithText("Online portion changed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 slices × 36 g = 72 g").assertIsDisplayed()
        compose.onNodeWithText("30.2 g").assertIsDisplayed()
    }

    @Test
    fun applyingTheNewerOnlinePortionUpdatesTheResult() {
        showCalculatorWithNotice(product(), sliceUnit(), BigDecimal("38"))
        compose.onNodeWithText("30.2 g").assertIsDisplayed()

        compose.onNodeWithText("Use new value").performScrollTo().performClick()

        // 42 x (2 x 38) / 100 = 31.92
        compose.onNodeWithText("31.9 g").assertIsDisplayed()
        compose.onAllNodesWithText("Online portion changed").assertCountEquals(0)
    }

    private fun showCalculatorWithNotice(product: Product, unit: PortionUnit, newerAmount: BigDecimal) {
        compose.setContent {
            var units by remember { mutableStateOf(listOf(unit)) }
            var newerRemoteAmount by remember { mutableStateOf<BigDecimal?>(newerAmount) }
            val countText = "2"
            val selectedUnit = units.first()
            val portionText = PortionResolver.resolve(BigDecimal("2"), selectedUnit.amountPerUnit)
                .stripTrailingZeros().toPlainString()
            val result = CarbCalculator.calculate(
                product.carbsPer100,
                BigDecimal(portionText),
                product.basis,
            )

            CarbScanTheme {
                ProductScreen(
                    state = ProductUiState(
                        loading = false,
                        product = product,
                        portionText = portionText,
                        countText = countText,
                        inputMode = InputMode.PORTION_UNIT,
                        selectedPortionUnitId = selectedUnit.id,
                        portionUnits = units,
                        newerRemotePortionUnitAmount = newerRemoteAmount,
                        result = result,
                        barcode = product.barcode,
                    ),
                    settings = AppSettings(),
                    onPortionChanged = {},
                    onAdjust = {},
                    onSetPortion = {},
                    onToggleFavorite = {},
                    onBack = {},
                    onVerify = {},
                    onDismissVerify = {},
                    onConfirmVerification = { _, _, _ -> },
                    onResetOnline = {},
                    onScanLabel = {},
                    onEnterManually = {},
                    onRetry = {},
                    onApplyNewerRemotePortionUnit = {
                        val newer = newerRemoteAmount ?: return@ProductScreen
                        units = units.map { it.copy(amountPerUnit = newer) }
                        newerRemoteAmount = null
                    },
                    onDismissNewerRemotePortionUnit = { newerRemoteAmount = null },
                )
            }
        }
    }

    private fun countField() = androidx.compose.ui.test.hasSetTextAction()
}

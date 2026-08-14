package app.carbscan.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
                    onVerifyPortionUnit = {
                        val id = selectedUnitId ?: return@ProductScreen
                        units = units.map { if (it.id == id) it.copy(verificationStatus = VerificationStatus.USER_VERIFIED) else it }
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
        compose.onNodeWithText("2 slices × 36 g = 72 g").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("30.2 g").assertIsDisplayed()
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

        compose.onNodeWithText("2 slices × 36 g = 72 g").performScrollTo().assertIsDisplayed()
    }

    // ---- user-defined portion units (§6) -----------------------------------------------------

    @Test
    fun addingAPortionUnitThroughTheInlineFormMakesItImmediatelyUsable() {
        showCalculator(product(), initialUnits = emptyList())

        compose.onNodeWithText("+ Add portion unit").performClick()
        // Index 1: index 0 is the still-visible grams field (no units exist yet, so the mode row
        // and its Slices chip have not appeared), index 1 is the new form's amount field.
        compose.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[1].performTextInput("36")
        compose.onNodeWithText("Save").performClick()

        // KNOWN FLAKY when run as part of the full instrumented suite (not in isolation — this
        // exact test passes reliably on its own, and the identical select-unit/read-equation
        // pattern is covered reliably by five other tests in this file, e.g.
        // selectingSlicesAndEnteringTwoShowsTheDerivedGramsAndCorrectResult). Investigated
        // 2026-08-14: not a recomposition-timing race (a `waitUntil` poll times out rather than
        // eventually finding the node), which points at emulator-level IME/focus state carrying
        // over between test-activity transitions rather than app logic. Documented rather than
        // silently retried or deleted — see docs/known-limitations.md.
        compose.onNodeWithText("1 slice × 36 g = 36 g").performScrollTo().assertIsDisplayed()

        compose.onNode(countField()).performTextReplacement("2")
        compose.onNodeWithText("2 slices × 36 g = 72 g").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("30.2 g").assertIsDisplayed()
    }

    @Test
    fun theAddPortionUnitFormRejectsSavingWithNoAmount() {
        showCalculator(product(), initialUnits = emptyList())

        compose.onNodeWithText("+ Add portion unit").performClick()
        compose.onNodeWithText("Save").performClick()

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
        compose.onNodeWithText("3 Dumpling × 24 g = 72 g").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("30.2 g").assertIsDisplayed()
    }

    // ---- session immutability (§9) -----------------------------------------------------------

    @Test
    fun aChangedOnlinePortionIsShownAsANoticeWithoutMovingTheResult() {
        // Simulates a background refresh having recorded a newer remote amount (§9): the session's
        // own result must still reflect the frozen 36 g snapshot, not the new 38 g.
        showCalculatorWithNotice(product(), sliceUnit(), BigDecimal("38"))

        compose.onNodeWithText("Online portion changed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 slices × 36 g = 72 g").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("30.2 g").assertIsDisplayed()
    }

    @Test
    fun applyingTheNewerOnlinePortionUpdatesTheResult() {
        showCalculatorWithNotice(product(), sliceUnit(), BigDecimal("38"))
        compose.onNodeWithText("30.2 g").assertIsDisplayed()

        compose.onNodeWithText("Use new value").performClick()

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

package app.justthecarbs.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.CarbCalculator
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionParser
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.PortionConversion
import app.justthecarbs.domain.PortionUnitKind
import app.justthecarbs.domain.PortionUsage
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.VerificationStatus
import app.justthecarbs.ui.product.ProductScreen
import app.justthecarbs.ui.product.ProductUiState
import app.justthecarbs.ui.product.PRODUCT_RESULT_TAG
import app.justthecarbs.ui.product.USUAL_PORTION_ROW_TAG
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * *Usual* portion shortcuts (development-pass brief §13).
 *
 * The behaviours worth pinning are about restraint again: the row is absent until a pattern really
 * exists, nothing is pre-selected, and a countable variant restores what the number meant rather
 * than just its digits.
 *
 * **Instrumented: needs a device or emulator.**
 */
class UsualPortionScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val barcode = "8712100849060"

    private fun product() = Product(
        barcode = barcode,
        name = "Hagelslag puur",
        carbsPer100 = BigDecimal("48.2"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
    )

    private fun sliceUnit(id: Long = 7) = PortionUnit(
        id = id,
        productBarcode = barcode,
        kind = PortionUnitKind.SLICE,
        customLabel = null,
        conversion = PortionConversion.WeightBased(BigDecimal("36"), NutritionBasis.PER_100_G),
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
        verifiedAt = null,
        originalRemoteConversion = PortionConversion.WeightBased(BigDecimal("36"), NutritionBasis.PER_100_G),
        latestRemoteConversion = PortionConversion.WeightBased(BigDecimal("36"), NutritionBasis.PER_100_G),
        rawRemoteServingText = "1 slice (36 g)",
        createdAt = Instant.parse("2026-08-14T10:00:00Z"),
        updatedAt = Instant.parse("2026-08-14T10:00:00Z"),
    )

    private fun usage(
        amount: String,
        mode: InputMode = InputMode.GRAMS,
        unitId: Long? = null,
        count: Int = 2,
    ) = PortionUsage(
        id = amount.hashCode().toLong(),
        productBarcode = barcode,
        inputMode = mode,
        portionUnitId = unitId,
        amount = BigDecimal(amount),
        usageCount = count,
        lastUsedAt = Instant.parse("2026-08-14T10:00:00Z"),
    )

    /**
     * Renders the real screen. Selecting a usual portion runs through the same resolution the
     * ViewModel does — count times per-unit amount, then [CarbCalculator] — so a passing test means
     * the number the user gets is right, not that a stub returned what the test expected.
     */
    private fun showWithUsual(
        usuals: List<PortionUsage>,
        units: List<PortionUnit> = emptyList(),
    ) {
        compose.setContent {
            val p = product()
            var portion by remember { mutableStateOf("") }
            var mode by remember { mutableStateOf(InputMode.GRAMS) }
            var selectedUnitId by remember { mutableStateOf<Long?>(null) }
            var count by remember { mutableStateOf("") }

            val parsed = PortionParser.parse(portion)

            JustTheCarbsTheme {
                ProductScreen(
                    state = ProductUiState(
                        loading = false,
                        product = p,
                        portionText = portion,
                        result = parsed?.let { CarbCalculator.calculate(p.carbsPer100, it, p.basis) },
                        barcode = p.barcode,
                        portionUnits = units,
                        inputMode = mode,
                        selectedPortionUnitId = selectedUnitId,
                        countText = count,
                        usualPortions = usuals,
                    ),
                    settings = AppSettings(),
                    onPortionChanged = { portion = it },
                    onSetPortion = { portion = it.stripTrailingZeros().toPlainString() },
                    onToggleFavorite = {},
                    onBack = {},
                    onVerify = {},
                    onDismissVerify = {},
                    onConfirmVerification = { _, _, _ -> },
                    onResetOnline = {},
                    onScanLabel = {},
                    onEnterManually = {},
                    onRetry = {},
                    onSelectUsualPortion = { chosen ->
                        val unit = chosen.portionUnitId?.let { id -> units.firstOrNull { it.id == id } }
                        if (chosen.inputMode == InputMode.PORTION_UNIT && unit != null) {
                            mode = InputMode.PORTION_UNIT
                            selectedUnitId = unit.id
                            count = chosen.amount.stripTrailingZeros().toPlainString()
                            portion = chosen.amount.multiply((unit.conversion as PortionConversion.WeightBased).amountPerUnit)
                                .stripTrailingZeros().toPlainString()
                        } else {
                            mode = InputMode.GRAMS
                            selectedUnitId = null
                            portion = chosen.amount.stripTrailingZeros().toPlainString()
                        }
                    },
                )
            }
        }
    }

    /** No pattern, no row. A shortcut after one use would be a standing recommendation (§13). */
    @Test
    fun theUsualRowIsAbsentWhenThereIsNoEstablishedPortion() {
        showWithUsual(emptyList())

        compose.onNodeWithTag(USUAL_PORTION_ROW_TAG).assertDoesNotExist()
    }

    @Test
    fun anEstablishedGramPortionIsOfferedAsAShortcut() {
        showWithUsual(listOf(usage("65")))

        compose.onNodeWithTag(USUAL_PORTION_ROW_TAG).assertIsDisplayed()
        // Read from the resource rather than typed here, so the label's wording or case can change
        // without breaking a test about whether the row is offered.
        compose.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext
                .getString(app.justthecarbs.R.string.product_usual_label),
        ).assertIsDisplayed()
        compose.onNodeWithText("65 g").assertIsDisplayed()
    }

    /**
     * Offered, never applied. The result stays empty until the user taps — an app that pre-filled
     * its own guess would be making a suggestion about what to eat, which §28 rules out.
     */
    @Test
    fun aUsualPortionIsNotAppliedUntilItIsTapped() {
        showWithUsual(listOf(usage("65")))

        compose.onNodeWithText("Enter a portion").assertIsDisplayed()
    }

    @Test
    fun tappingAUsualGramPortionCalculatesIt() {
        showWithUsual(listOf(usage("65")))

        compose.onNodeWithText("65 g").performClick()

        // 48.2 g/100 g × 65 g = 31.33 → 31.3, through the production calculator.
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("31.3 grams of carbs")
    }

    /**
     * A countable usual keeps its words. Offering "2 slices" as a bare "2" would be indistinguishable
     * from two grams, and tapping it would silently calculate the wrong thing.
     */
    @Test
    fun aCountableUsualPortionKeepsItsUnitWords() {
        showWithUsual(
            usuals = listOf(usage("2", mode = InputMode.PORTION_UNIT, unitId = 7)),
            units = listOf(sliceUnit()),
        )

        compose.onNodeWithText("2 slices").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tappingACountableUsualPortionRestoresTheCountAndItsUnit() {
        showWithUsual(
            usuals = listOf(usage("2", mode = InputMode.PORTION_UNIT, unitId = 7)),
            units = listOf(sliceUnit()),
        )

        compose.onNodeWithText("2 slices").performScrollTo().performClick()

        // 2 slices × 36 g = 72 g; 48.2 g/100 g × 72 g = 34.704 → 34.7.
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("34.7 grams of carbs")
    }

    // ---- the shortcut matching the portion is marked ---------------------------------------------

    @Test
    fun theUsualPortionMatchingThePortionIsMarkedSelected() {
        showWithUsual(listOf(usage("65"), usage("45")))

        compose.onNodeWithText("65 g").performClick()

        compose.onNodeWithText("65 g").assertIsSelected()
        compose.onNodeWithText("45 g").assertIsNotSelected()
    }

    /** By value, not by spelling: a typed `65.0` is the usual 65 g. */
    @Test
    fun aTypedPortionEqualInValueMarksTheUsualShortcut() {
        showWithUsual(listOf(usage("65")))

        compose.onNode(hasSetTextAction()).performTextInput("65.0")

        compose.onNodeWithText("65 g").assertIsSelected()
    }

    /** "2 slices" is a count of a unit, so 2 grams (or 2 of another unit) is not it. */
    @Test
    fun aCountableUsualPortionIsMarkedSelectedOnlyForItsOwnUnit() {
        showWithUsual(
            usuals = listOf(usage("2", mode = InputMode.PORTION_UNIT, unitId = 7), usage("2")),
            units = listOf(sliceUnit()),
        )

        compose.onNodeWithText("2 slices").performScrollTo().performClick()

        compose.onNodeWithText("2 slices").assertIsSelected()
        compose.onNodeWithText("2 g").assertIsNotSelected()
    }

    @Test
    fun usualShortcutsAreAnnouncedAsButtons() {
        showWithUsual(listOf(usage("65")))

        compose.onNodeWithText("65 g")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    /** §13's explicit cap: a row of shortcuts the user has to read is not a shortcut. */
    @Test
    fun atMostThreeUsualPortionsAreShown() {
        showWithUsual(listOf(usage("30"), usage("45"), usage("60")))

        compose.onNodeWithText("30 g").assertIsDisplayed()
        compose.onNodeWithText("45 g").assertIsDisplayed()
        compose.onNodeWithText("60 g").assertIsDisplayed()
    }
}

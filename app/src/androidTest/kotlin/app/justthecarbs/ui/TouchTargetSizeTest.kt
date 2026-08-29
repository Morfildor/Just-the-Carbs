package app.justthecarbs.ui

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.CarbCalculator
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionConversion
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.PortionUnitKind
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.VerificationStatus
import app.justthecarbs.ui.home.HomeScreen
import app.justthecarbs.ui.product.ProductScreen
import app.justthecarbs.ui.product.ProductUiState
import app.justthecarbs.ui.search.SearchScreen
import app.justthecarbs.ui.search.SearchUiState
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import app.justthecarbs.ui.theme.Space
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Every control the user can tap is at least [Space.minTouchTarget] on its short side.
 *
 * **Why this class exists rather than a code-review habit.** The app already applied
 * `.size(Space.minTouchTarget)` to its IconButtons and `.height(Space.minTouchTarget)` to Settings'
 * and manual entry's chips, so the convention was established and visible — and five controls still
 * shipped under it, because the two places that miss it are the two where a *container* silently
 * overrides the default:
 *
 *  - an `IconButton` inside a text field's `leadingIcon`/`trailingIcon` slot, which is measured by
 *    the field's decoration box rather than by the button's own 48dp default, and came out at 40dp;
 *  - a Material 3 `FilterChip`, whose own default height is **32dp**, not 48.
 *
 * Both are invisible by reading: the call sites look exactly like the correct ones elsewhere. They
 * were found by measuring `SemanticsNode.size` on a device, and this class is that measurement kept.
 *
 * The chips matter most. The grams/slices pair is the control that decides whether the number on
 * screen is grams or a count — a mis-tap there changes what the result is *of*, and this app is used
 * one-handed, in a shop, by someone who is about to dose from the answer.
 *
 * **Instrumented: needs a device or emulator.** Sizes are asserted in dp, so the result does not
 * depend on the device's density.
 */
class TouchTargetSizeTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * Nodes that are clickable but are not themselves a discrete control, so the rule does not apply.
     *
     * A text field is tappable along its whole width and is far taller than the minimum anyway; it
     * is listed by content description rather than excluded by shape so that a genuine control can
     * never fall into this bucket by accident.
     */
    private fun isTextField(node: SemanticsNode): Boolean =
        node.config.getOrNull(SemanticsProperties.IsEditable) == true

    private fun assertAllTargetsAreLargeEnough(minimum: androidx.compose.ui.unit.Dp = Space.minTouchTarget) {
        val density = Density(compose.density.density, compose.density.fontScale)
        val minimumPx = with(density) { minimum.toPx() }

        val undersized = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick),
            useUnmergedTree = false,
        ).fetchSemanticsNodes(atLeastOneRootRequired = false)
            .filterNot(::isTextField)
            // Only controls actually on screen: a node scrolled out of view reports empty bounds,
            // which would otherwise read as a zero-sized target and fail for the wrong reason.
            .filter { it.size.width > 0 && it.size.height > 0 }
            .filter { it.size.width < minimumPx || it.size.height < minimumPx }
            .map { node ->
                val label = node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                    ?: node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
                    ?: "<unlabelled>"
                val w = with(density) { node.size.width.toDp() }
                val h = with(density) { node.size.height.toDp() }
                "$label = ${w.value.toInt()}x${h.value.toInt()}dp"
            }

        assertTrue(
            "Controls smaller than $minimum: $undersized",
            undersized.isEmpty(),
        )
    }

    private fun product() = Product(
        barcode = "8712100849060",
        name = "Hagelslag puur",
        carbsPer100 = BigDecimal("48.2"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
    )

    private fun sliceUnit() = PortionUnit(
        id = 1L,
        productBarcode = "8712100849060",
        kind = PortionUnitKind.SLICE,
        conversion = PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G),
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
        customLabel = null,
        verifiedAt = null,
        originalRemoteConversion = PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G),
        latestRemoteConversion = PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G),
        rawRemoteServingText = "1 slice (35 g)",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    /** Home's search field carries a submit and a clear button inside its decoration slots. */
    @Test
    fun homeSearchFieldControlsMeetTheMinimum() {
        compose.setContent {
            JustTheCarbsTheme {
                HomeScreen(
                    recents = emptyList(),
                    settings = AppSettings(),
                    onScan = {},
                    onManualEntry = {},
                    onOpenProduct = {},
                    onToggleFavorite = {},
                    onOpenSettings = {},
                    // Non-blank so the clear button is rendered at all.
                    searchState = SearchUiState(query = "hagelslag"),
                )
            }
        }

        assertAllTargetsAreLargeEnough()
    }

    /**
     * Home carrying a meal in progress. The meal bar is the only way back to a half-built meal, and
     * its compact variant was 40dp tall.
     */
    @Test
    fun homeMealBarMeetsTheMinimum() {
        val item = MealItem.weightBased(
            productBarcode = "8712100849060",
            displayName = "Hagelslag puur",
            portionDescription = "65 g",
            resolvedAmount = BigDecimal("65"),
            basis = NutritionBasis.PER_100_G,
            carbsPer100 = BigDecimal("48.2"),
            exactCarbs = BigDecimal("31.3"),
            addedAt = Instant.EPOCH,
        )

        compose.setContent {
            JustTheCarbsTheme {
                HomeScreen(
                    recents = emptyList(),
                    settings = AppSettings(),
                    onScan = {},
                    onManualEntry = {},
                    onOpenProduct = {},
                    onToggleFavorite = {},
                    onOpenSettings = {},
                    mealItems = listOf(item),
                    mealTotal = CarbCalculator.calculate(
                        BigDecimal("48.2"), BigDecimal("65"), NutritionBasis.PER_100_G,
                    ),
                )
            }
        }

        assertAllTargetsAreLargeEnough()
    }

    @Test
    fun searchScreenFieldControlsMeetTheMinimum() {
        compose.setContent {
            JustTheCarbsTheme {
                SearchScreen(
                    state = SearchUiState(query = "hagelslag"),
                    onQueryChanged = {},
                    onSearchSubmit = {},
                    onSelect = {},
                    onScanLabel = {},
                    onEnterManually = {},
                    onRetry = {},
                    onBack = {},
                )
            }
        }

        assertAllTargetsAreLargeEnough()
    }

    /**
     * The calculator, including the grams/slices input-mode chips.
     *
     * This is the case the class was written for: a Material `FilterChip` is 32dp by default, and
     * these two chips decide whether the portion field means grams or a count.
     */
    @Test
    fun portionModeChipsMeetTheMinimum() {
        compose.setContent {
            JustTheCarbsTheme {
                ProductScreen(
                    state = ProductUiState(
                        loading = false,
                        product = product(),
                        inputMode = InputMode.GRAMS,
                        portionUnits = listOf(sliceUnit()),
                        barcode = product().barcode,
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
                )
            }
        }

        assertAllTargetsAreLargeEnough()
    }

    /**
     * The same calculator at a large font scale.
     *
     * A minimum expressed in dp does not grow with the font scale, so this is not asserting a
     * different threshold — it is checking that nothing *shrinks* or gets clipped when the text it
     * contains grows, which is the failure mode a fixed `height` (rather than `heightIn`) produces.
     */
    @Test
    fun portionModeChipsMeetTheMinimumAtLargeFontScale() {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.8f),
            ) {
                JustTheCarbsTheme {
                    ProductScreen(
                        state = ProductUiState(
                            loading = false,
                            product = product(),
                            inputMode = InputMode.GRAMS,
                            portionUnits = listOf(sliceUnit()),
                            barcode = product().barcode,
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
                    )
                }
            }
        }

        assertAllTargetsAreLargeEnough()
    }
}

package app.justthecarbs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.R
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.CarbCalculator
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionConversion
import app.justthecarbs.domain.PortionParser
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.PortionUnitKind
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.ResultStyle
import app.justthecarbs.domain.VerificationStatus
import app.justthecarbs.ui.meal.MEAL_ADD_TAG
import app.justthecarbs.ui.product.PRODUCT_PROTEIN_TAG
import app.justthecarbs.ui.product.PRODUCT_RESULT_TAG
import app.justthecarbs.ui.product.ProductScreen
import app.justthecarbs.ui.product.ProductUiState
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * The calculator's optional protein row (design spec 2026-09-24, section 4): a second reading after
 * the carb block, one announcement carrying both facts, a worded state when the record has none,
 * nothing at all when the setting is off, and never at the cost of the portion field.
 *
 * The screen is rendered from state computed with the production formula, the way
 * `ProductScreenTest` does it. The keyboard gate cannot be exercised here: `createComposeRule`
 * never sees an IME inset (see CLAUDE.md), so it is verified on the emulator by hand.
 */
class ProteinReadingScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun nutella(
        protein: String? = "6.3",
        origin: ProductDataOrigin = ProductDataOrigin.OPEN_FOOD_FACTS,
        verification: VerificationStatus = VerificationStatus.UNVERIFIED,
        carbs: String = "57.5",
    ) = Product(
        barcode = "8000500310427",
        name = "Nutella",
        carbsPer100 = BigDecimal(carbs),
        basis = NutritionBasis.PER_100_G,
        dataSource = origin,
        verificationStatus = verification,
        proteinPer100 = protein?.let(::BigDecimal),
        proteinOrigin = protein?.let { ProductDataOrigin.OPEN_FOOD_FACTS },
    )

    private fun stateFor(product: Product, portion: String): ProductUiState {
        val parsed = PortionParser.parse(portion)
        return ProductUiState(
            loading = false,
            product = product,
            portionText = portion,
            result = parsed?.let { CarbCalculator.calculate(product.carbsPer100, it, product.basis) },
            exactProtein = parsed?.let { amount ->
                product.proteinPer100?.let { CarbCalculator.calculate(it, amount, product.basis).exact }
            },
            barcode = product.barcode,
        )
    }

    private fun show(
        product: Product = nutella(),
        settings: AppSettings = AppSettings(proteinEnabled = true),
        portion: String = "65",
        fontScale: Float? = null,
        width: androidx.compose.ui.unit.Dp? = null,
        height: androidx.compose.ui.unit.Dp? = null,
        /** Runs inside the same theme and density as the screen, for measurements. */
        probe: (@androidx.compose.runtime.Composable () -> Unit)? = null,
    ) {
        compose.setContent {
            val content = @androidx.compose.runtime.Composable {
                JustTheCarbsTheme {
                    probe?.invoke()
                    val screen = @androidx.compose.runtime.Composable {
                        ProductScreen(
                            state = stateFor(product, portion),
                            settings = settings,
                            onPortionChanged = {},
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
                    when {
                        width != null && height != null ->
                            Box(Modifier.requiredSize(width, height)) { screen() }
                        width != null -> Box(Modifier.requiredWidth(width)) { screen() }
                        else -> screen()
                    }
                }
            }
            if (fontScale != null) {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                    content = content,
                )
            } else {
                content()
            }
        }
    }

    private fun proteinRow() = compose.onNodeWithTag(PRODUCT_PROTEIN_TAG, useUnmergedTree = false)

    @Test
    fun theRowShowsTheProteinForTheSamePortion() {
        show()

        // 6.3 g per 100 g x 65 g = 4.095 g, shown in the decimal style as 4.1 g.
        proteinRow().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.product_protein_label)).assertIsDisplayed()
        compose.onNodeWithText("4.1 g").assertIsDisplayed()
        proteinRow().assertContentDescriptionEquals("4.1 grams of protein")
    }

    @Test
    fun oneAnnouncementCarriesBothFactsCarbsFirst() {
        show()

        compose.onNodeWithTag(PRODUCT_RESULT_TAG)
            .assertContentDescriptionEquals("37.4 grams of carbs, 4.1 grams of protein")
    }

    @Test
    fun aRecordWithoutProteinSaysSoAndNeverShowsZero() {
        show(nutella(protein = null))

        compose.onNodeWithText(string(R.string.product_protein_no_online_value)).assertIsDisplayed()
        proteinRow().assertContentDescriptionEquals(string(R.string.product_protein_no_online_value_accessible))
        compose.onNodeWithTag(PRODUCT_RESULT_TAG)
            .assertContentDescriptionEquals("37.4 grams of carbs, no online value for protein")
        compose.onAllNodesWithText("0 g").assertCountEquals(0)
        compose.onAllNodesWithText("0.0 g").assertCountEquals(0)
    }

    /** Off is the default and leaves today's dock and today's announcement exactly as they were. */
    @Test
    fun offShowsNoRowAndTheCarbsOnlyAnnouncement() {
        show(settings = AppSettings())

        compose.onAllNodesWithTag(PRODUCT_PROTEIN_TAG).assertCountEquals(0)
        compose.onAllNodesWithText(string(R.string.product_protein_label)).assertCountEquals(0)
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("37.4 grams of carbs")
    }

    @Test
    fun proteinAppearsWithTheAnswerAndNotBeforeIt() {
        show(portion = "")

        compose.onAllNodesWithTag(PRODUCT_PROTEIN_TAG).assertCountEquals(0)
    }

    @Test
    fun aProductTheUserTypedInShowsNoRow() {
        show(nutella(protein = null, origin = ProductDataOrigin.MANUAL, verification = VerificationStatus.USER_VERIFIED))

        compose.onAllNodesWithTag(PRODUCT_PROTEIN_TAG).assertCountEquals(0)
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("37.4 grams of carbs")
    }

    @Test
    fun aVerifiedProductSaysTheProteinIsStillAnOnlineValue() {
        show(nutella(verification = VerificationStatus.USER_VERIFIED))

        compose.onNodeWithText(string(R.string.product_result_verified_protein_online)).assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.product_result_verified)).assertCountEquals(0)
    }

    @Test
    fun aVerifiedProductWithoutProteinKeepsItsOwnSentence() {
        show(nutella(protein = null, verification = VerificationStatus.USER_VERIFIED))

        compose.onNodeWithText(string(R.string.product_result_verified)).assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.product_result_verified_protein_online)).assertCountEquals(0)
    }

    @Test
    fun theFigureFollowsTheWholeGramStyle() {
        show(settings = AppSettings(proteinEnabled = true, resultStyle = ResultStyle.WHOLE_DOMINANT))

        compose.onNodeWithText("4 g").assertIsDisplayed()
        proteinRow().assertContentDescriptionEquals("4 grams of protein")
    }

    /** "1 grams" is wrong English; a bare 1 is singular, a shown "1.0" stays plural (2026-09-25 review). */
    @Test
    fun aWholeOneIsSpokenInTheSingular() {
        show(
            nutella(carbs = "10", protein = "10"),
            settings = AppSettings(proteinEnabled = true, resultStyle = ResultStyle.WHOLE_DOMINANT),
            portion = "10",
        )

        proteinRow().assertContentDescriptionEquals("1 gram of protein")
        compose.onNodeWithTag(PRODUCT_RESULT_TAG)
            .assertContentDescriptionEquals("1 gram of carbs, 1 gram of protein")
    }

    @Test
    fun aShownDecimalOneStaysPlural() {
        show(nutella(carbs = "10", protein = "10"), portion = "10")

        compose.onNodeWithTag(PRODUCT_RESULT_TAG)
            .assertContentDescriptionEquals("1.0 grams of carbs, 1.0 grams of protein")
    }

    /** Traversal and reading order: numeral, provenance, protein, then the meal actions. */
    @Test
    fun theRowSitsAfterTheCarbBlockAndBeforeTheMealActions() {
        show()

        val result = compose.onNodeWithTag(PRODUCT_RESULT_TAG).fetchSemanticsNode().boundsInRoot
        val provenance = compose.onNodeWithText(string(R.string.product_result_unverified))
            .fetchSemanticsNode().boundsInRoot
        val protein = proteinRow().fetchSemanticsNode().boundsInRoot
        val add = compose.onNodeWithTag(MEAL_ADD_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("result above provenance", result.bottom <= provenance.top + 1f)
        assertTrue("provenance above protein", provenance.bottom <= protein.top + 1f)
        assertTrue("protein above the meal actions", protein.bottom <= add.top + 1f)
    }

    @Test
    fun aDirectCarbPortionShowsNoRow() {
        val product = nutella()
        val slice = PortionUnit(
            id = 7,
            productBarcode = product.barcode,
            kind = PortionUnitKind.SLICE,
            customLabel = null,
            conversion = PortionConversion.DirectCarbs(BigDecimal("14.2")),
            dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
            verificationStatus = VerificationStatus.UNVERIFIED,
            verifiedAt = null,
            originalRemoteConversion = null,
            latestRemoteConversion = null,
            rawRemoteServingText = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        compose.setContent {
            JustTheCarbsTheme {
                ProductScreen(
                    state = ProductUiState(
                        loading = false,
                        product = product,
                        barcode = product.barcode,
                        portionUnits = listOf(slice),
                        inputMode = InputMode.PORTION_UNIT,
                        selectedPortionUnitId = slice.id,
                        countText = "2",
                        directCarbResult = BigDecimal("28.4"),
                    ),
                    settings = AppSettings(proteinEnabled = true),
                    onPortionChanged = {},
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

        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("28.4 grams of carbs")
        compose.onAllNodesWithTag(PRODUCT_PROTEIN_TAG).assertCountEquals(0)
    }

    /**
     * A five-digit figure at the narrowest width and the largest text is laid out whole: it drops
     * under its eyebrow rather than shrinking or losing digits.
     *
     * The frame is 320dp wide and deliberately tall. This case is about width; on a short window
     * at 2.0x the row is correctly withheld so the portion field keeps its room (see
     * [switchingProteinOnNeverTakesTheFieldAwayAtLargeText]), which would leave nothing to measure.
     * Geometry is read unclipped (position plus size), since the tall frame overhangs the window.
     */
    @Test
    fun aVeryLargeFigureIsNeverCutAtTheNarrowestWidth() {
        // 100 g per 100 g x 20000000 g: "20000000.0 g", too wide to sit beside the eyebrow at 2.0x on
        // 320dp, so the row must stack it rather than squeeze it.
        var naturalWidth = 0
        show(
            nutella(protein = "100", carbs = "10"),
            portion = "20000000",
            fontScale = 2.0f,
            width = 320.dp,
            height = 1400.dp,
            probe = {
                val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                val style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                naturalWidth = measurer.measure("20000000.0 g", style, softWrap = false, maxLines = 1).size.width
            },
        )

        val figure = compose.onNodeWithText("20000000.0 g", useUnmergedTree = true).fetchSemanticsNode()
        val row = proteinRow().fetchSemanticsNode()
        val figureRight = figure.positionInRoot.x + figure.size.width
        val rowRight = row.positionInRoot.x + row.size.width
        assertTrue(
            "figure overruns its row: figure ends at $figureRight, row at $rowRight",
            figureRight <= rowRight + 1f,
        )
        assertTrue("figure has no width", figure.size.width > 0)
        // Every digit is laid out: the figure is at least as wide as the text measured on its own
        // in the same style and density. A figure squeezed into the room beside the eyebrow (and
        // clipped) passes the bounds check above but not this one (2026-09-25 review).
        assertTrue(
            "figure is ${figure.size.width}px wide, its text needs ${naturalWidth}px",
            figure.size.width >= naturalWidth - 1,
        )
        assertTrue(
            "the figure must have dropped under the eyebrow, starting at the row's edge",
            kotlin.math.abs(figure.positionInRoot.x - row.positionInRoot.x) <= 1f,
        )
    }

    /**
     * The field wins over the row. Switching protein on must never leave the portion field less
     * visible than it was with protein off, unless the field was already a full touch target. On a
     * roomy window the row simply shows; on CI's 320x640/160 window at 1.3x with an answer the zone
     * is already a sliver, and the row is withheld there instead of pushing the field off screen.
     */
    @Test
    fun switchingProteinOnNeverTakesTheFieldAwayAtLargeText() {
        assertTheFieldSurvivesProtein(fontScale = 1.3f)
    }

    @Test
    fun switchingProteinOnNeverTakesTheFieldAwayAtTheLargestText() {
        assertTheFieldSurvivesProtein(fontScale = 1.8f)
    }

    private fun assertTheFieldSurvivesProtein(fontScale: Float) {
        var settings by mutableStateOf(AppSettings())
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                JustTheCarbsTheme {
                    ProductScreen(
                        state = stateFor(nutella(), "65"),
                        settings = settings,
                        onPortionChanged = {},
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
        compose.waitForIdle()
        val off = compose.onNode(hasSetTextAction()).fetchSemanticsNode()
        val visibleOff = off.boundsInRoot.height
        val touchTarget = 48 * off.layoutInfo.density.density

        settings = AppSettings(proteinEnabled = true)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        val visibleOn = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot.height
        assertTrue(
            "the field was ${visibleOff}px visible with protein off and ${visibleOn}px with it on",
            visibleOn >= minOf(visibleOff, touchTarget) - 1f,
        )
    }
}

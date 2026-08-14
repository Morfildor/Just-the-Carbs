package app.justthecarbs.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.justthecarbs.ui.components.PRODUCT_HERO_TAG
import app.justthecarbs.ui.components.PRODUCT_GALLERY_NEXT_TAG
import app.justthecarbs.ui.components.PRODUCT_GALLERY_PREVIOUS_TAG
import app.justthecarbs.ui.components.PRODUCT_GALLERY_TAG
import app.justthecarbs.ui.components.PRODUCT_GALLERY_ERROR_TAG
import app.justthecarbs.ui.components.ProductGalleryDialog
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.CarbCalculator
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.ProductImage
import app.justthecarbs.domain.ProductImageType
import app.justthecarbs.domain.ResultStyle
import app.justthecarbs.domain.VerificationStatus
import app.justthecarbs.ui.product.PRODUCT_RESULT_TAG
import app.justthecarbs.ui.product.ProductScreen
import app.justthecarbs.ui.product.ProductUiState
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.io.IOException
import coil3.ImageLoader
import coil3.fetch.Fetcher
import coil3.request.Options
import kotlinx.coroutines.awaitCancellation

/**
 * §60 workflow tests for the calculator.
 *
 * These assert on what the user sees, not on how it is produced. Each drives the screen the way a
 * person would — type into the portion field, tap an adjust button — and reads the result off the
 * display. None of them reach into a ViewModel or assert on internal state, so the screen can be
 * rebuilt however we like as long as the numbers stay right.
 *
 * **Instrumented: needs a device or emulator.**
 */
class ProductScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun product(
        name: String = "Hagelslag puur",
        carbs: String = "48.2",
        basis: NutritionBasis = NutritionBasis.PER_100_G,
        origin: ProductDataOrigin = ProductDataOrigin.OPEN_FOOD_FACTS,
        verification: VerificationStatus = VerificationStatus.UNVERIFIED,
        packageAmount: String? = null,
        images: List<ProductImage> = emptyList(),
    ) = Product(
        barcode = "8712100849060",
        name = name,
        carbsPer100 = BigDecimal(carbs),
        basis = basis,
        dataSource = origin,
        verificationStatus = verification,
        packageAmount = packageAmount?.let(::BigDecimal),
        images = images,
    )

    /**
     * Renders the real screen with real state. The portion is held here rather than in a
     * ViewModel so the test exercises the composable's own behaviour, and recalculates through the
     * same [CarbCalculator] production uses — not a stubbed number.
     */
    private fun showCalculator(
        product: Product = product(),
        settings: AppSettings = AppSettings(),
    ) {
        compose.setContent {
            var portion by remember { mutableStateOf("") }
            val parsed = app.justthecarbs.domain.PortionParser.parse(portion)

            JustTheCarbsTheme {
                ProductScreen(
                    state = ProductUiState(
                        loading = false,
                        product = product,
                        portionText = portion,
                        result = parsed?.let {
                            CarbCalculator.calculate(product.carbsPer100, it, product.basis)
                        },
                        barcode = product.barcode,
                    ),
                    settings = settings,
                    onPortionChanged = { portion = it },
                    onAdjust = { delta ->
                        val current = app.justthecarbs.domain.PortionParser.parse(portion) ?: BigDecimal.ZERO
                        portion = current.add(BigDecimal(delta)).max(BigDecimal.ZERO)
                            .stripTrailingZeros().toPlainString()
                    },
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
                )
            }
        }
    }

    // ---- §70 the primary acceptance test ---------------------------------------------------

    @Test
    fun typingAPortionShowsTheCarbohydrateResultImmediately() {
        showCalculator()

        compose.onNodeWithText("Enter a portion").assertIsDisplayed()
        compose.onNode(portionField()).performTextInput("65")

        // The brief's own worked example: 48.2 x 65 / 100 = 31.33.
        // Default hierarchy is decimal-dominant (correction #6).
        compose.onNodeWithText("31.3 g").assertIsDisplayed()
        compose.onNodeWithText("≈ 31 g whole grams").assertIsDisplayed()
    }

    @Test
    fun thereIsNoCalculateButtonToPress() {
        showCalculator()
        compose.onNode(portionField()).performTextInput("65")

        // §16: the result updates as you type. If a Calculate button ever appears, this fails.
        compose.onAllNodesWithText("Calculate", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        compose.onNodeWithText("31.3 g").assertIsDisplayed()
    }

    @Test
    fun clearingThePortionRemovesTheResultRatherThanShowingZero() {
        showCalculator()
        compose.onNode(portionField()).performTextInput("65")
        compose.onNodeWithText("31.3 g").assertIsDisplayed()

        compose.onNode(portionField()).performTextReplacement("")

        // §13: showing "0 g" for an empty field would be presenting a value the user never asked
        // for. The prompt must come back instead.
        compose.onNodeWithText("Enter a portion").assertIsDisplayed()
        compose.onAllNodesWithText("0 g").assertCountEquals(0)
    }

    // ---- §16 quick adjustment ----------------------------------------------------------------

    @Test
    fun quickAdjustChangesThePortionAndTheResultTogether() {
        showCalculator()
        compose.onNode(portionField()).performTextInput("65")

        compose.onNodeWithText("+10").performClick()

        // 48.2 x 75 / 100 = 36.15
        compose.onNodeWithText("36.2 g").assertIsDisplayed()
    }

    @Test
    fun quickAdjustNeverProducesANegativePortion() {
        showCalculator()
        compose.onNode(portionField()).performTextInput("5")

        compose.onNodeWithText("-10").performClick()

        // Clamped at zero: a negative portion is not a thing you can eat.
        compose.onNodeWithText("0.0 g").assertIsDisplayed()
    }

    // ---- design decision 3.1: ml is never converted to g -------------------------------------

    @Test
    fun aMillilitreProductLocksThePortionFieldToMillilitres() {
        showCalculator(product(carbs = "9.4", basis = NutritionBasis.PER_100_ML, name = "Sinaasappelsap"))

        compose.onNodeWithText("9.4 g carbs / 100 ml").assertIsDisplayed()
        compose.onNode(portionField()).performTextInput("250")

        // 9.4 x 250 / 100 = 23.5 — the same arithmetic as grams, because no density is applied.
        compose.onNodeWithText("23.5 g").assertIsDisplayed()
        compose.onNodeWithText("≈ 24 g whole grams").assertIsDisplayed()
    }

    // ---- §23 / §25 provenance is always visible ----------------------------------------------

    @Test
    fun unverifiedRemoteDataSaysSoWithoutAlarmingTheUser() {
        showCalculator()

        compose.onNodeWithText("Online value").assertIsDisplayed()
        compose.onNodeWithText("Check package if needed").assertIsDisplayed()
    }

    @Test
    fun aVerifiedProductIsMarkedAsVerifiedByTheUser() {
        showCalculator(product(verification = VerificationStatus.USER_VERIFIED))

        compose.onNodeWithText("✓ Verified by you").assertIsDisplayed()
        compose.onAllNodesWithText("Online value").assertCountEquals(0)
    }

    // ---- §4/§30 the product hero image ---------------------------------------------------------

    /**
     * The hero must be substantially larger than a Recents thumbnail (52 dp) — that size difference
     * is the entire point of the feature (§4). Asserted against the real measured height rather
     * than a screenshot, so it stays meaningful without a screenshot-testing framework (§30).
     */
    @Test
    fun theProductHeroImageIsSubstantiallyLargerThanARecentThumbnail() {
        showCalculator(product())

        val heroHeight = compose.onNodeWithTag(PRODUCT_HERO_TAG)
            .fetchSemanticsNode()
            .size
            .height

        with(compose.density) {
            // 52 dp is Space.thumbnail, what Recents uses. The hero is ~150 dp.
            assert(heroHeight.toDp() > 120.dp) {
                "hero image was ${heroHeight.toDp()}, expected well above the 52dp thumbnail"
            }
        }
    }

    /**
     * A product with no image must not collapse or shift the layout — the container holds its space
     * and shows a monogram, exactly as Recents already does (§30).
     */
    @Test
    fun aProductWithNoImageKeepsTheHeroContainerAndStillCalculates() {
        showCalculator(product())

        compose.onNodeWithTag(PRODUCT_HERO_TAG).assertIsDisplayed()

        compose.onNode(portionField()).performTextInput("65")
        compose.onNodeWithText("31.3 g").assertIsDisplayed()
    }

    private fun showGalleryWithControlledImage(data: GalleryTestImage) {
        compose.setContent {
            val context = LocalContext.current
            val imageLoader = remember {
                ImageLoader.Builder(context)
                    .components { add(GalleryTestFetcherFactory()) }
                    .build()
            }
            DisposableEffect(imageLoader) { onDispose(imageLoader::shutdown) }

            JustTheCarbsTheme {
                ProductGalleryDialog(
                    productName = "Test product",
                    images = listOf(
                        ProductImage(
                            ProductImageType.FRONT,
                            "en",
                            "https://images.openfoodfacts.org/test.400.jpg",
                        ),
                    ),
                    onDismiss = {},
                    imageLoader = imageLoader,
                    imageModel = { data },
                )
            }
        }
    }

    /**
     * The dialog must not throw on an empty image list.
     *
     * Both production call sites check `isNotEmpty()` before composing it, so this cannot be
     * reached by driving the UI — it is reachable if the product changes under a recomposition
     * while the dialog is open. A crash there would take down the calculator mid-calculation, so
     * the contract is "render nothing", not "fail loudly".
     */
    @Test
    fun aGalleryWithNoImagesRendersNothingRatherThanCrashing() {
        compose.setContent {
            JustTheCarbsTheme {
                ProductGalleryDialog(
                    productName = "Test product",
                    images = emptyList(),
                    onDismiss = {},
                )
            }
        }

        compose.onAllNodesWithTag(PRODUCT_GALLERY_TAG).assertCountEquals(0)
    }

    /**
     * Swiping the gallery must tell a screen-reader user what they landed on.
     *
     * The images are photographs, so their own descriptions cannot carry this — the caption is
     * the only thing that says which image is showing and where in the set it sits. Before this,
     * all three caption lines changed on swipe and none of them was announced, leaving a TalkBack
     * user to swipe through an unlabelled set.
     *
     * Asserted on the merged description rather than the individual `Text`s, because the
     * individual strings were already present and visible while the announcement was still
     * missing — the presence of the text was never the thing that was broken.
     */
    @Test
    fun theGalleryCaptionIsAnnouncedAsOneLiveRegion() {
        compose.setContent {
            JustTheCarbsTheme {
                ProductGalleryDialog(
                    productName = "Test product",
                    images = listOf(
                        ProductImage(
                            ProductImageType.NUTRITION,
                            "en",
                            "https://images.openfoodfacts.org/a.400.jpg",
                        ),
                        ProductImage(
                            ProductImageType.FRONT,
                            "nl",
                            "https://images.openfoodfacts.org/b.400.jpg",
                        ),
                    ),
                    onDismiss = {},
                    // No network in an instrumented test; the caption is independent of whether
                    // the bytes ever arrive, which is the point being tested.
                    imageModel = { null },
                )
            }
        }

        val caption = compose.onNodeWithContentDescription("Nutrition, EN, 1 of 2")
        caption.assertExists()
        caption.fetchSemanticsNode().config[SemanticsProperties.LiveRegion].let {
            assertTrue("The caption must be a polite live region, was $it", it == LiveRegionMode.Polite)
        }
    }

    // ---- U3: the result must survive the largest supported font scale --------------------------

    /**
     * The result must not be clipped at the accessibility font scales.
     *
     * This is the one number the whole app exists to show, and the one where truncation would be
     * worst: `125.3 g` cut to `125` is a wrong value presented with full confidence, not a
     * cosmetic defect. It renders with `maxLines = 1` inside a fixed-height panel, so nothing in
     * the layout would make truncation visible as a broken-looking screen — it would just quietly
     * show fewer digits.
     *
     * Asserted geometrically, because the semantics tree reports the whole string whether or not
     * the pixels fit — the blind spot that let four layout defects through a green suite before.
     *
     * The conditions are the hostile ones: the widest string this screen can produce, on a dense
     * narrow phone, at the largest font scale. It passes today because Android's non-linear font
     * scaling (API 34+) deliberately grows large text far less than small text, so the 72sp
     * result does not approach its 96dp box. That is a platform behaviour this app depends on
     * without stating it anywhere, which is exactly why it is pinned here: if the result style,
     * the panel height, or the minimum supported API changes, this is the test that should fail.
     */
    @Test
    fun theResultIsNotClippedAtTheLargestFontScale() {
        compose.setContent {
            // 1.8x is the scale the app is documented as supporting; Android's own accessibility
            // settings go to 2.0x, so this is the floor of the requirement, not the ceiling.
            CompositionLocalProvider(
                LocalDensity provides Density(
                    // A dense small phone: 1080px across a 5.5" screen is ~3.0 density, so the
                    // window is only ~360dp wide. Combined with the largest font scale, this is
                    // the narrowest place the widest result has to fit.
                    density = 3.0f,
                    fontScale = 2.0f,
                ),
            ) {
                JustTheCarbsTheme {
                    ProductScreen(
                        state = ProductUiState(
                            loading = false,
                            product = product(carbs = "48.2"),
                            // 260 g of a 48.2 g/100 g product is 125.3 g — three digits plus a
                            // decimal, the widest result this screen can be asked to render.
                            portionText = "260",
                            result = CarbCalculator.calculate(
                                BigDecimal("48.2"),
                                BigDecimal("260"),
                                NutritionBasis.PER_100_G,
                            ),
                            barcode = "8712100849060",
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

        // Ask the Text itself whether it overflowed, via GetTextLayoutResult.
        //
        // Comparing the node's `size` against its `boundsInRoot` does NOT work here and was tried
        // first: a constrained Text reports both as the already-constrained value, so they cannot
        // disagree and the assertion passes even when the digits are visibly cut off. Verified by
        // forcing the result into a 120dp-wide row — the bounds comparison still passed.
        // `TextLayoutResult` is the only source that reports the *desired* size independently.
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(PRODUCT_RESULT_TAG)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(layouts)
        val layout = layouts.single()

        assertTrue(
            "The result overflows its box: laid out at ${layout.size.width}x" +
                "${layout.size.height}px, longest line ${layout.multiParagraph.maxIntrinsicWidth}px",
            !layout.hasVisualOverflow,
        )
        // A truncated result would still satisfy a bounds check by simply being a shorter string,
        // so the value itself is asserted too.
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertTextEquals("125.3 g")
    }

    @Test
    fun aProductWithoutSafeGalleryImagesHasNoFakeGalleryAction() {
        showCalculator(
            product(
                images = listOf(
                    ProductImage(ProductImageType.FRONT, "en", "https://evil.example.com/front.jpg"),
                ),
            ),
        )

        compose.onNodeWithTag(PRODUCT_HERO_TAG).assertHasNoClickAction()
    }

    @Test
    fun oneProductImageOpensAClosableModalWithoutPagingControls() {
        showCalculator(
            product(
                images = listOf(
                    ProductImage(
                        ProductImageType.FRONT,
                        "nl",
                        "https://images.openfoodfacts.org/front-nl.400.jpg",
                    ),
                ),
            ),
        )

        compose.onNodeWithContentDescription("View product images").assertHasClickAction().performClick()

        compose.onNodeWithTag(PRODUCT_GALLERY_TAG).assertIsDisplayed()
        compose.onNodeWithText("Front").assertIsDisplayed()
        compose.onAllNodesWithTag(PRODUCT_GALLERY_NEXT_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(PRODUCT_GALLERY_PREVIOUS_TAG).assertCountEquals(0)

        compose.onNodeWithContentDescription("Close product images").performClick()
        compose.onAllNodesWithTag(PRODUCT_GALLERY_TAG).assertCountEquals(0)
    }

    @Test
    fun galleryArrowsPageImagesWithoutChangingTheCalculatorValue() {
        showCalculator(
            product(
                images = listOf(
                    ProductImage(
                        ProductImageType.FRONT,
                        "nl",
                        "https://images.openfoodfacts.org/front-nl.400.jpg",
                    ),
                    ProductImage(
                        ProductImageType.NUTRITION,
                        "en",
                        "https://images.openfoodfacts.org/nutrition-en.400.jpg",
                    ),
                ),
            ),
        )
        compose.onNode(portionField()).performTextInput("65")
        compose.onNodeWithText("31.3 g").assertIsDisplayed()

        compose.onNodeWithContentDescription("View product images").performClick()
        compose.onNodeWithTag(PRODUCT_GALLERY_NEXT_TAG).performClick()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithText("2 of 2").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Nutrition").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close product images").performClick()

        compose.onNodeWithText("31.3 g").assertIsDisplayed()
    }

    @Test
    fun aSlowGalleryImageKeepsAnExplicitLoadingState() {
        showGalleryWithControlledImage(GalleryTestImage.SLOW)

        compose.onNodeWithText("Loading image…").assertIsDisplayed()
        compose.onNodeWithTag(PRODUCT_GALLERY_TAG).assertIsDisplayed()
    }

    @Test
    fun aBrokenGalleryImageShowsRetryWithoutClosingTheModal() {
        showGalleryWithControlledImage(GalleryTestImage.BROKEN)

        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag(PRODUCT_GALLERY_ERROR_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("This image is unavailable.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithTag(PRODUCT_GALLERY_TAG).assertIsDisplayed()
    }

    private enum class GalleryTestImage { SLOW, BROKEN }

    private class GalleryTestFetcherFactory : Fetcher.Factory<GalleryTestImage> {
        override fun create(data: GalleryTestImage, options: Options, imageLoader: ImageLoader): Fetcher =
            Fetcher {
                when (data) {
                    GalleryTestImage.SLOW -> awaitCancellation()
                    GalleryTestImage.BROKEN -> throw IOException("Controlled gallery failure")
                }
            }
    }

    /**
     * The portion controls must not be separated from the product header by a large empty band
     * (§19: "use responsive layout to prevent excessive scrolling", §38: not "a collection of soft
     * cards" with dead space between them).
     *
     * This regression exists because the first version of the hero layout left roughly a quarter of
     * the screen blank between the per-100 figure and "How much are you eating?" — something no
     * assertion caught and only appeared when the screen was actually looked at. Measuring the gap
     * turns that into something a test can hold.
     */
    @Test
    fun thePortionControlsFollowTheProductHeaderWithoutALargeDeadBand() {
        showCalculator(product())

        val headerBottom = compose.onNodeWithText("48.2 g carbs / 100 g", substring = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .bottom
        val questionTop = compose.onNodeWithText("How much are you eating?")
            .fetchSemanticsNode()
            .boundsInRoot
            .top

        with(compose.density) {
            val gap = (questionTop - headerBottom).toDp()
            assert(gap < 140.dp) { "dead band between product header and portion question was $gap" }
        }
    }

    /** The image is identification, never a gate: the result stays reachable regardless (§30). */
    @Test
    fun theHeroImageDoesNotBlockTheResult() {
        showCalculator(product())

        compose.onNode(portionField()).performTextInput("65")

        compose.onNodeWithText("31.3 g").assertIsDisplayed()
        compose.onNodeWithText("≈ 31 g whole grams").assertIsDisplayed()
    }

    // ---- §14 pack shortcuts only when the size is actually known ------------------------------

    @Test
    fun packShortcutsAppearOnlyWhenAPackageSizeIsKnown() {
        showCalculator(product(packageAmount = "380"))

        compose.onNodeWithText("Full pack").performClick()

        // 48.2 x 380 / 100 = 183.16
        compose.onNodeWithText("183.2 g").assertIsDisplayed()
    }

    @Test
    fun packShortcutsAreHiddenWhenThePackageSizeIsUnknown() {
        showCalculator(product(packageAmount = null))

        // A guessed pack size would be a wrong portion offered as a convenience (§13).
        compose.onAllNodesWithText("Full pack").assertCountEquals(0)
        compose.onAllNodesWithText("½ pack").assertCountEquals(0)
        compose.onAllNodesWithText("¼ pack").assertCountEquals(0)
    }

    /** ¼ · ½ · Full, the set §14 settles on. ¾ is deliberately not offered. */
    @Test
    fun quarterHalfAndFullPackAreOfferedAndNothingElse() {
        showCalculator(product(packageAmount = "400"))

        compose.onNodeWithText("¼ pack").assertIsDisplayed()
        compose.onNodeWithText("½ pack").assertIsDisplayed()
        compose.onNodeWithText("Full pack").assertIsDisplayed()
        compose.onAllNodesWithText("¾ pack").assertCountEquals(0)
    }

    @Test
    fun aQuarterPackSetsAQuarterOfThePackageAmount() {
        showCalculator(product(packageAmount = "400"))

        compose.onNodeWithText("¼ pack").performClick()

        // 400 / 4 = 100 g; 48.2 x 100 / 100 = 48.2
        compose.onNodeWithText("48.2 g").assertIsDisplayed()
    }

    @Test
    fun aHalfPackStillSetsHalfThePackageAmount() {
        showCalculator(product(packageAmount = "400"))

        compose.onNodeWithText("½ pack").performClick()

        // 400 / 2 = 200 g; 48.2 x 200 / 100 = 96.4
        compose.onNodeWithText("96.4 g").assertIsDisplayed()
    }

    // ---- §43 result style --------------------------------------------------------------------

    @Test
    fun wholeDominantSettingRestoresTheOriginalHierarchy() {
        showCalculator(settings = AppSettings(resultStyle = ResultStyle.WHOLE_DOMINANT))
        compose.onNode(portionField()).performTextInput("65")

        compose.onNodeWithText("31 g").assertIsDisplayed()
        compose.onNodeWithText("31.3 g calculated").assertIsDisplayed()
    }

    /**
     * The double-rounding guard, asserted through the UI (correction #6).
     * 51.5 x 30 / 100 = 15.45 exactly. The decimal shows 15.5; the whole gram is 15, derived from
     * the exact value and NOT from the displayed 15.5, which would give 16.
     */
    @Test
    fun theWholeGramIsNeverDerivedFromTheDisplayedDecimal() {
        showCalculator(product(carbs = "51.5"))
        compose.onNode(portionField()).performTextInput("30")

        compose.onNodeWithText("15.5 g").assertIsDisplayed()
        compose.onNodeWithText("≈ 15 g whole grams").assertIsDisplayed()
        compose.onAllNodesWithText("≈ 16 g whole grams").assertCountEquals(0)
    }

    // ---- stress: the screen must survive real-world data --------------------------------------

    @Test
    fun aVeryLongProductNameDoesNotPushTheResultOffScreen() {
        showCalculator(
            product(
                name = "Biologische Volkoren Ontbijtgranen met Noten, Rozijnen en Honing " +
                    "Extra Grote Familieverpakking Voordeeldoos",
            ),
        )
        compose.onNode(portionField()).performTextInput("65")

        // The result is what matters; a long name must never displace it.
        compose.onNodeWithText("31.3 g").assertIsDisplayed()
    }

    @Test
    fun aZeroCarbProductCalculatesZeroRatherThanFailing() {
        showCalculator(product(carbs = "0", name = "Bronwater"))
        compose.onNode(portionField()).performTextInput("500")

        compose.onNodeWithText("0.0 g").assertIsDisplayed()
    }

    @Test
    fun aLargePortionStillProducesAReadableResult() {
        showCalculator()
        compose.onNode(portionField()).performTextInput("2500")

        // 48.2 x 2500 / 100 = 1205
        compose.onNodeWithText("1205.0 g").assertIsDisplayed()
    }

    @Test
    fun aDecimalPortionIsAcceptedWithEitherSeparator() {
        showCalculator()

        compose.onNode(portionField()).performTextInput("32.5")
        // 48.2 x 32.5 / 100 = 15.665
        compose.onNodeWithText("15.7 g").assertIsDisplayed()

        compose.onNode(portionField()).performTextReplacement("32,5")
        compose.onNodeWithText("15.7 g").assertIsDisplayed()
    }

    /** The portion field is the only text input on this screen. */
    private fun portionField() = androidx.compose.ui.test.hasSetTextAction()
}

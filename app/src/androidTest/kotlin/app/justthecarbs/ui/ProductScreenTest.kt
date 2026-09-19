package app.justthecarbs.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
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
import app.justthecarbs.ui.meal.MEAL_ADD_TAG
import app.justthecarbs.ui.components.PORTION_RAIL_MINUS_TAG
import app.justthecarbs.ui.components.PORTION_RAIL_PLUS_TAG
import app.justthecarbs.ui.product.PRODUCT_RESULT_TAG
import app.justthecarbs.ui.product.PRODUCT_VERIFY_INLINE_TAG
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

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
                    // Driven through the real domain operation rather than a re-implementation of
                    // it, so this harness cannot drift from what the app does — which is the whole
                    // reason the arithmetic was moved into PortionAdjustment.
                    onAdjust = { operation ->
                        val current = app.justthecarbs.domain.PortionParser.parse(portion)
                        portion = app.justthecarbs.domain.ResultFormatter.editable(
                            app.justthecarbs.domain.PortionAdjustment.apply(current, operation),
                        )
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
        typePortion("65")

        // The brief's own worked example: 48.2 x 65 / 100 = 31.33.
        // Default hierarchy is decimal-dominant (correction #6).
        //
        // The result now renders as a split numeral + unit (ResultValue), so "31.3 g" no longer
        // exists as one text node — asserted on the merged accessible description instead.
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("31.3 grams")
        compose.onNodeWithText("≈ 31 g whole grams").assertIsDisplayed()
    }

    @Test
    fun thereIsNoCalculateButtonToPress() {
        showCalculator()
        typePortion("65")

        // §16: the result updates as you type. If a Calculate button ever appears, this fails.
        compose.onAllNodesWithText("Calculate", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("31.3 grams")
    }

    @Test
    fun clearingThePortionRemovesTheResultRatherThanShowingZero() {
        showCalculator()
        typePortion("65")
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("31.3 grams")

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
        typePortion("65")

        // Scrolled into view before clicking, and that is not defensive padding.
        //
        // On a phone-sized window the quick-adjust row sits below the visible fold of the portion
        // zone's scroll container. A node scrolled out of view is still `isPlaced == true` and still
        // has a size, but Compose reports its `boundsInRoot` as an empty rect at the origin — it has
        // no clickable area. `performClick()` on it does not throw; it clicks nothing, `onAdjust`
        // never fires, and the portion silently stays where it was.
        //
        // Measured, not inferred: before the scroll the button reports
        // `bounds=Rect(0,0,0,0) size=228x126`; after it it has real bounds and the click lands.
        //
        // Same family as the keyboard-covered control recorded in CLAUDE.md — a control the user
        // cannot currently reach is a control `performClick()` cannot press.
        //
        // Found by tag, not by its printed label. The rail writes its minus with U+2212 rather
        // than a hyphen, and — more to the point — its ± label is the *package-scaled* step, so
        // there is no fixed "+10" to match: this product has no package size, so the step is the
        // ±5 default (see quickAdjustStep). A test that matched the label would silently be
        // asserting about a different button on a product with a package size.
        compose.onNodeWithTag(PORTION_RAIL_PLUS_TAG).performScrollTo().performClick()

        // 65 + 5 = 70, and 48.2 x 70 / 100 = 33.74
        //
        // Scoped to the result's own node rather than searching the whole screen for the text: the
        // portion field carries a value and a unit suffix too, so a bare text search can match the
        // input instead of the result — which is how this assertion could pass while saying nothing
        // about the result at all. The numeral and unit are now separate sibling Text nodes
        // (ResultValue), so the merged accessible description is what carries "33.7 g" as one string.
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("33.7 grams")
    }

    @Test
    fun quickAdjustNeverProducesANegativePortion() {
        showCalculator()
        typePortion("5")

        // Scrolled first, for the reason given in full on the sibling test above: below the fold,
        // this button has empty bounds and `performClick()` presses nothing.
        compose.onNodeWithTag(PORTION_RAIL_MINUS_TAG).performScrollTo().performClick()

        // Clamped at zero: a negative portion is not a thing you can eat.
        //
        // Asserted on the result's own tag rather than by searching for the text "0.0 g": once the
        // portion field is showing `0`, a plain text search matches the *field* as well as the
        // result, and the assertion silently stops being about the result at all. The numeral and
        // unit are now separate sibling Text nodes (ResultValue), so the merged accessible
        // description is what carries "0.0 g" as one string.
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("0.0 grams")
    }

    @Test
    fun theStepScalesWithThePackageSize() {
        // The rail's ± is package-scaled, which is the behaviour kept from the previous row: ±5 is
        // a quarter of a 20 g biscuit and a hundredth of a 500 g pack, so one absolute step cannot
        // serve both. A 400 g package puts the step at 25.
        showCalculator(product(packageAmount = "400"))
        typePortion("65")

        compose.onNodeWithTag(PORTION_RAIL_PLUS_TAG).performScrollTo().performClick()

        // 65 + 25 = 90, and 48.2 x 90 / 100 = 43.38
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("43.4 grams")
    }

    // ---- design decision 3.1: ml is never converted to g -------------------------------------

    @Test
    fun aMillilitreProductLocksThePortionFieldToMillilitres() {
        showCalculator(product(carbs = "9.4", basis = NutritionBasis.PER_100_ML, name = "Sinaasappelsap"))

        compose.onNodeWithText("9.4 g carbs / 100 ml").assertIsDisplayed()
        typePortion("250")

        // 9.4 x 250 / 100 = 23.5 — the same arithmetic as grams, because no density is applied.
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("23.5 grams")
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

    // ---- interaction-polish task 3: inline Verify affordance ----------------------------------

    @Test
    fun verifyIsOfferedForAnUnverifiedOnlineValue() {
        // The default fixture is already OPEN_FOOD_FACTS / UNVERIFIED — isRemoteRefreshable is
        // exactly that condition, so this is the ordinary case a scanned product first arrives in.
        showCalculator(product())

        compose.onNodeWithTag(PRODUCT_VERIFY_INLINE_TAG).assertExists()
    }

    @Test
    fun verifyIsAbsentForAVerifiedValue() {
        showCalculator(product(verification = VerificationStatus.USER_VERIFIED))

        compose.onNodeWithTag(PRODUCT_VERIFY_INLINE_TAG).assertDoesNotExist()
    }

    @Test
    fun verifyIsAbsentForAManualOrOcrValue() {
        // User-authored data is never remote-refreshable regardless of verification status — the
        // condition must not fire on data source alone ignoring the (still UNVERIFIED) status.
        showCalculator(product(origin = ProductDataOrigin.MANUAL))

        compose.onNodeWithTag(PRODUCT_VERIFY_INLINE_TAG).assertDoesNotExist()
    }

    // ---- §4/§30 the product hero image ---------------------------------------------------------

    /**
     * The hero must be substantially larger than a Recents thumbnail (52 dp) — that size difference
     * is the entire point of the feature (§4). Asserted against the real measured height rather
     * than a screenshot, so it stays meaningful without a screenshot-testing framework (§30).
     *
     * This product has **no image**, so it gets the short monogram plate rather than the tall photo
     * plate: a monogram is derived from the name shown directly above it and identifies nothing, so
     * reserving photo-sized space for it is space spent on nothing. The threshold is therefore
     * stated against the thumbnail it must beat, not against the photo height — which is what this
     * test was always about.
     */
    @Test
    fun theProductHeroImageIsSubstantiallyLargerThanARecentThumbnail() {
        showCalculator(product())

        val heroHeight = compose.onNodeWithTag(PRODUCT_HERO_TAG)
            .fetchSemanticsNode()
            .size
            .height

        with(compose.density) {
            // 52 dp is Space.thumbnail, what Recents uses.
            assert(heroHeight.toDp() > 72.dp) {
                "hero image was ${heroHeight.toDp()}, expected well above the 52dp thumbnail"
            }
        }
    }

    /**
     * A product that genuinely has a photo gets a much taller plate than a monogram placeholder.
     *
     * That size is what answers "is this the package in my hand?", and it is proportional to the
     * screen rather than a fixed dp — so the assertion is stated as "clearly taller than the
     * monogram plate" rather than as a pixel value that would only hold on one display.
     */
    @Test
    fun aProductWithAPhotoGetsATallerHeroThanOneWithout() {
        showCalculator(
            product().copy(imageUrl = "https://images.openfoodfacts.org/images/products/front.jpg"),
        )

        val photoHeight = compose.onNodeWithTag(PRODUCT_HERO_TAG)
            .fetchSemanticsNode().size.height

        with(compose.density) {
            // The monogram plate is 84 dp; a real photo must be substantially beyond it.
            assert(photoHeight.toDp() > 120.dp) {
                "photo hero was ${photoHeight.toDp()}, expected clearly taller than the 84dp " +
                    "monogram plate"
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

        typePortion("65")
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("31.3 grams")
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
            val deviceDensity = LocalDensity.current.density
            // 1.8x is the scale the app is documented as supporting; Android's own accessibility
            // settings go to 2.0x, so this is the floor of the requirement, not the ceiling.
            CompositionLocalProvider(
                LocalDensity provides Density(
                    // Preserve the physical device density while raising font scale. The CI
                    // emulator is 320px at 160dpi; imposing a 3.0 density on it invents a 106dp
                    // window and produces a meaningless zero-height result.
                    density = deviceDensity,
                    fontScale = 2.0f,
                ),
            ) {
                JustTheCarbsTheme {
                    Box(Modifier.width(320.dp)) {
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
        }

        // Ask the Text itself whether it overflowed, via GetTextLayoutResult.
        //
        // Comparing the node's `size` against its `boundsInRoot` does NOT work here and was tried
        // first: a constrained Text reports both as the already-constrained value, so they cannot
        // disagree and the assertion passes even when the digits are visibly cut off. Verified by
        // forcing the result into a 120dp-wide row — the bounds comparison still passed.
        // `TextLayoutResult` is the only source that reports the *desired* size independently.
        //
        // The numeral is now rendered by ResultValue as its own Text node, a child of the
        // PRODUCT_RESULT_TAG row rather than that tag itself — GetTextLayoutResult lives on the
        // Text node that actually lays out the digits, so the numeral's own text ("125.3") is what
        // locates it.
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("125.3")
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(layouts)
        val layout = layouts.single()
        val numeralBounds = compose.onNodeWithText("125.3").fetchSemanticsNode().boundsInRoot

        assertTrue(
            "The result overflows its box: laid out at ${layout.size.width}x" +
                "${layout.size.height}px, longest line ${layout.multiParagraph.maxIntrinsicWidth}px, " +
                "width overflow ${layout.didOverflowWidth}, height overflow ${layout.didOverflowHeight}, " +
                "line count ${layout.lineCount}, paragraph height ${layout.multiParagraph.height}, " +
                "node bounds $numeralBounds",
            !layout.hasVisualOverflow,
        )
        // A truncated result would still satisfy a bounds check by simply being a shorter string,
        // so the value itself is asserted too, via the merged accessible description.
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("125.3 grams")
        compose.onNodeWithContentDescription("Portion in g").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertIsDisplayed()
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
        typePortion("65")
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("31.3 grams")

        compose.onNodeWithContentDescription("View product images").performClick()
        compose.onNodeWithTag(PRODUCT_GALLERY_NEXT_TAG).performClick()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithText("2 of 2").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Nutrition").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close product images").performClick()

        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("31.3 grams")
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

        typePortion("65")

        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("31.3 grams")
        compose.onNodeWithText("≈ 31 g whole grams").assertIsDisplayed()
    }

    // ---- §14 pack shortcuts only when the size is actually known ------------------------------

    @Test
    fun packShortcutsAppearOnlyWhenAPackageSizeIsKnown() {
        showCalculator(product(packageAmount = "380"))

        compose.onNodeWithText("Full pack").performScrollTo().performClick()
        compose.waitForIdle()

        // 48.2 x 380 / 100 = 183.16
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("183.2 grams")
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

        compose.onNodeWithText("¼ pack").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("½ pack").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Full pack").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("¾ pack").assertCountEquals(0)
    }

    @Test
    fun aQuarterPackSetsAQuarterOfThePackageAmount() {
        showCalculator(product(packageAmount = "400"))

        compose.onNodeWithText("¼ pack").performScrollTo().performClick()
        compose.waitForIdle()

        // 400 / 4 = 100 g; 48.2 x 100 / 100 = 48.2
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("48.2 grams")
    }

    @Test
    fun aHalfPackStillSetsHalfThePackageAmount() {
        showCalculator(product(packageAmount = "400"))

        compose.onNodeWithText("½ pack").performScrollTo().performClick()
        compose.waitForIdle()

        // 400 / 2 = 200 g; 48.2 x 200 / 100 = 96.4
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("96.4 grams")
    }

    // ---- §43 result style --------------------------------------------------------------------

    @Test
    fun wholeDominantSettingRestoresTheOriginalHierarchy() {
        showCalculator(settings = AppSettings(resultStyle = ResultStyle.WHOLE_DOMINANT))
        typePortion("65")

        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("31 grams")
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
        typePortion("30")

        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("15.5 grams")
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
        typePortion("65")

        // The result is what matters; a long name must never displace it.
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("31.3 grams")
    }

    @Test
    fun aZeroCarbProductCalculatesZeroRatherThanFailing() {
        showCalculator(product(carbs = "0", name = "Bronwater"))
        typePortion("500")

        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("0.0 grams")
    }

    @Test
    fun aLargePortionStillProducesAReadableResult() {
        showCalculator()
        typePortion("2500")

        // 48.2 x 2500 / 100 = 1205
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("1205.0 grams")
    }

    @Test
    fun aDecimalPortionIsAcceptedWithEitherSeparator() {
        showCalculator()

        typePortion("32.5")
        // 48.2 x 32.5 / 100 = 15.665
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("15.7 grams")

        compose.onNode(portionField()).performTextReplacement("32,5")
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("15.7 grams")
    }

    // ---- Add-to-meal success confirmation ------------------------------------------------------

    /**
     * Renders the calculator with a controllable *Add to meal* write: `onAddToMeal` suspends on
     * [gate] before landing, mirroring how [ProductUiState.lastMealAddSucceeded] is only ever set
     * once persistence has genuinely returned (see `ProductViewModel.addCurrentToMeal`'s success
     * branch). `ProductScreen` itself is purely state-driven — it has no ViewModel or repository of
     * its own — so the write is simulated here with the same local-mutable-state pattern
     * [showCalculator] already uses for the portion field, rather than a fake repository.
     */
    private fun showCalculatorWithControllableMealAdd(gate: CompletableDeferred<Unit>) {
        compose.setContent {
            var portion by remember { mutableStateOf("65") }
            var addingToMeal by remember { mutableStateOf(false) }
            var lastMealAddSucceeded by remember { mutableStateOf<Long?>(null) }
            val scope = rememberCoroutineScope()
            val parsed = app.justthecarbs.domain.PortionParser.parse(portion)
            val product = product()

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
                        addingToMeal = addingToMeal,
                        lastMealAddSucceeded = lastMealAddSucceeded,
                    ),
                    settings = AppSettings(),
                    onPortionChanged = { portion = it },
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
                    onAddToMeal = { _, _ ->
                        addingToMeal = true
                        scope.launch {
                            gate.await()
                            addingToMeal = false
                            lastMealAddSucceeded = System.currentTimeMillis()
                        }
                    },
                )
            }
        }
    }

    /** The confirmation appears once the write behind *Add to meal* has actually landed. */
    @Test
    fun addToMealShowsABriefSuccessLabelAfterAConfirmedWrite() {
        val gate = CompletableDeferred<Unit>()
        showCalculatorWithControllableMealAdd(gate)

        compose.onNodeWithTag(MEAL_ADD_TAG).performClick()
        gate.complete(Unit)
        compose.waitForIdle()

        compose.onNodeWithText("Added").assertExists()
    }

    /**
     * The negative half of the same guarantee: while the write is still in flight, nothing has
     * landed yet, so the success label must not appear — a naive implementation might show it
     * optimistically the instant the button is tapped.
     */
    @Test
    fun addToMealNeverShowsSuccessBeforeTheWriteCompletes() {
        val gate = CompletableDeferred<Unit>()
        showCalculatorWithControllableMealAdd(gate)

        compose.onNodeWithTag(MEAL_ADD_TAG).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Added").assertDoesNotExist()
    }

    /** The portion field is the only text input on this screen. */
    private fun portionField() = androidx.compose.ui.test.hasSetTextAction()

    /**
     * Type a portion, then dismiss the soft keyboard and let the layout settle.
     *
     * Used by the tests that go on to assert something is **displayed** in the pinned result panel.
     * Those assertions are about the panel genuinely being on screen, and the soft keyboard is a
     * real window over the bottom of the screen — logcat reports Gboard as
     * `SoftKeyboardView{0,0-1080,641}` — so with it up, a pinned bottom element is legitimately not
     * displayed and `assertIsDisplayed` correctly says so.
     *
     * That is what produced this class's intermittent `product_result … is not displayed` failure:
     * measured here as 32/32 on three consecutive runs and then a failure on runs 4 and 5, without
     * any code change between them. It is a test-synchronization defect rather than a UI bug — a
     * real user who types and then reads the total has dismissed the keyboard — and it is fixed by
     * performing that dismissal rather than by retrying, sleeping or weakening the assertion.
     *
     * Tests that only assert on *absence* or on node counts deliberately keep the plain
     * `performTextInput`: they do not depend on the pinned panel being visible, and changing them
     * would be churn.
     */
    private fun typePortion(text: String) {
        compose.onNode(portionField()).performTextInput(text)
        compose.onNode(portionField()).performImeAction()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
    }
}

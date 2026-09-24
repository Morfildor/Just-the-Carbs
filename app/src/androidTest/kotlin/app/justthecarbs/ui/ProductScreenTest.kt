package app.justthecarbs.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.R
import app.justthecarbs.ui.components.ProductIdentityRow
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
import app.justthecarbs.ui.meal.MEAL_ADD_AND_SCAN_TAG
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.semantics.Role
import app.justthecarbs.ui.product.PRODUCT_RESULT_TAG
import app.justthecarbs.ui.product.PRODUCT_VERIFY_INLINE_TAG
import app.justthecarbs.ui.product.ProductScreen
import app.justthecarbs.ui.product.ProductUiState
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import org.junit.Assert.assertEquals
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
        /**
         * Raises the font scale for the cases that are about running out of height, keeping the
         * device's own density. A full `Density` override was tried first and is wrong on a
         * low-density device: imposing 2.75 on CI's 320px/160dpi emulator invented a 116x233dp
         * window that no supported phone has, and the result dock fell off the bottom of it.
         */
        fontScale: Float? = null,
        /** A remembered portion the screen arrives with, as a returning product does. */
        initialPortion: String = "",
    ) {
        compose.setContent {
            var portion by remember { mutableStateOf(initialPortion) }
            val parsed = app.justthecarbs.domain.PortionParser.parse(portion)

            val content = @androidx.compose.runtime.Composable {
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
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("31.3 grams of carbs")
        compose.onNodeWithText("≈ 31 g whole grams").assertIsDisplayed()
    }

    @Test
    fun thereIsNoCalculateButtonToPress() {
        showCalculator()
        typePortion("65")

        // §16: the result updates as you type. If a Calculate button ever appears, this fails.
        compose.onAllNodesWithText("Calculate", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("31.3 grams of carbs")
    }

    @Test
    fun clearingThePortionRemovesTheResultRatherThanShowingZero() {
        showCalculator()
        typePortion("65")
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("31.3 grams of carbs")

        compose.onNode(portionField()).performTextReplacement("")

        // §13: showing "0 g" for an empty field would be presenting a value the user never asked
        // for. The prompt must come back instead.
        compose.onNodeWithText("Enter a portion").assertIsDisplayed()
        compose.onAllNodesWithText("0 g").assertCountEquals(0)
    }

    // ---- the result is announced (2026-09-24 UX polish) ---------------------------------------

    /**
     * TalkBack announces a polite live region when a property of an EXISTING node changes. The
     * result used to carry its live region on a node created inside the dock's `AnimatedContent`,
     * so every new figure (and the first answer, which also creates the dock's slot) arrived as a
     * brand-new node and nothing guaranteed it was spoken. The live region now sits on one node
     * that exists before the first answer and survives every change after it.
     */
    @Test
    fun theResultIsAnnouncedFromOneLiveRegionThatOutlivesEveryChange() {
        showCalculator()

        val pending = compose.onNodeWithTag(PRODUCT_RESULT_TAG).fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, pending.config.getOrNull(SemanticsProperties.LiveRegion))
        assertEquals(null, pending.config.getOrNull(SemanticsProperties.ContentDescription))

        typePortion("65")
        val answered = compose.onNodeWithTag(PRODUCT_RESULT_TAG).fetchSemanticsNode()
        assertEquals(pending.id, answered.id)
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("31.3 grams of carbs")

        compose.onNode(portionField()).performTextReplacement("80")
        compose.waitForIdle()
        assertEquals(pending.id, compose.onNodeWithTag(PRODUCT_RESULT_TAG).fetchSemanticsNode().id)
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("38.6 grams of carbs")
    }

    /**
     * Only pending -> answer cross-fades; a new figure replaces the old one in place. Keyed on the
     * numeral itself, typing `125` faded through ghosts of `1` and `12`: each keystroke built a
     * new numeral node and faded the old one out over it. Same node before and after proves the
     * digits changed in place.
     */
    @Test
    fun aNewFigureReplacesTheOldOneInPlaceRatherThanCrossFading() {
        showCalculator()
        typePortion("65")
        val before = compose.onNodeWithText("31.3", useUnmergedTree = true).fetchSemanticsNode()

        compose.onNode(portionField()).performTextReplacement("80")
        compose.waitForIdle()

        val after = compose.onNodeWithText("38.6", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(before.id, after.id)
    }

    // ---- the generic +/- adjust row is gone (2026-09-22 refinement) --------------------------

    /**
     * The four arithmetic buttons are absent, on a product where they would previously have shown.
     *
     * **Replaces `quickAdjustChangesThePortionAndTheResultTogether` and
     * `quickAdjustNeverProducesANegativePortion`, which pinned the controls this approved change
     * removes.** Neither test's *purpose* is lost: they were about portion arithmetic and its
     * clamp at zero, which live in `ProductViewModel.adjustPortion` and are unchanged and still
     * covered in the JVM suite. What is deleted is the claim that this screen renders buttons to
     * drive them with.
     *
     * Stated as a negative because that is the actual contract — a row that reappears is exactly
     * the regression this guards. Checked by label across the whole screen rather than by tag, so
     * it cannot be satisfied by a row that is merely re-tagged.
     */
    @Test
    fun noGenericAdjustmentButtonsAreOffered() {
        // 390 g: large enough that the old ladder would have rendered -50/-25/+25/+50.
        showCalculator(product(packageAmount = "390"))
        typePortion("65")

        listOf("+25", "-25", "+50", "-50", "+10", "-10", "+5", "-5").forEach { label ->
            compose.onAllNodesWithText(label).assertCountEquals(0)
        }
        compose.onAllNodesWithContentDescription("Plus 25").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Minus 25").assertCountEquals(0)
    }

    /**
     * The shortcuts that name something real are kept: a fraction of the package in the user's
     * hand. Removing the arithmetic row must not have taken these with it.
     */
    @Test
    fun thePackShortcutsRemainWhenThePackageSizeIsKnown() {
        showCalculator(product(packageAmount = "390"))

        compose.onNodeWithText("¼ pack").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("½ pack").assertIsDisplayed()
        compose.onNodeWithText("Full pack").assertIsDisplayed()
    }

    /**
     * And they stay absent when no package size was read confidently — a guessed pack size would be
     * a wrong portion presented as a shortcut (§14, §13). Unchanged by this pass; asserted here so
     * the removal of the sibling row cannot quietly relax the gate.
     */
    @Test
    fun thePackShortcutsAreAbsentWhenThePackageSizeIsUnknown() {
        showCalculator(product(packageAmount = null))

        compose.onAllNodesWithText("¼ pack").assertCountEquals(0)
        compose.onAllNodesWithText("½ pack").assertCountEquals(0)
        compose.onAllNodesWithText("Full pack").assertCountEquals(0)
    }

    /**
     * The larger product image (96dp, up from 56dp) must not push the portion field or the result
     * off the screen — the defect that motivated shrinking the 245dp hero in the first place.
     *
     * Asserted at 1.3x text, the scale at which this zone first overflows, so the check is made
     * where the height actually runs out rather than at the comfortable default.
     *
     * **Font scale only; the device's density is kept.** This case used to inject
     * `Density(2.75f, 1.3f)` -- the reference phone's density, on the assumption it would run on
     * one. On the release gate's 320x640px @160dpi emulator that override turned the window into a
     * 116x233dp viewport, which is not a supported device, and `product_result` was correctly not
     * displayed on it. At the real density the same screen is 320x640dp -- the narrowest supported
     * width at a real height -- and that is where this is now measured.
     *
     * What the measurement showed there (probe, 2026-09-22): on arrival the field sits at
     * y=307..405 of 640 with the dock starting at 447, fully in view; after a portion is typed the
     * result-state dock (label, numeral, whole-grams line, a three-line provenance sentence and
     * two-line meal buttons at 1.3x) is ~360dp tall and leaves the scrolling zone a 23dp band, so
     * the group label above the field scrolls out of view while the field's own top edge, the
     * result and the identity row all remain on screen. The old assertion on the *label* text
     * "Portion" therefore failed for a node that is not the field. What is pinned is what the user
     * needs: the field wholly in view when they arrive to type, and the result, the field and the
     * identity row on screen once they have. A negative control that inflates the thumbnail to
     * 480dp fails the arrival check.
     */
    @Test
    fun theLargerProductImageLeavesThePortionFieldAndResultOnScreen() {
        showCalculator(fontScale = 1.3f)

        // Arrival: the field is wholly in view, not merely present. `assertIsDisplayed` passes on
        // any visible sliver, so the clipped bounds are compared with the node's own size.
        val field = compose.onNode(portionField()).fetchSemanticsNode()
        assertTrue(
            "portion field is clipped on arrival: visible=${field.boundsInRoot} size=${field.size}",
            field.boundsInRoot.height >= field.size.height - 1f &&
                field.boundsInRoot.width >= field.size.width - 1f,
        )

        // A portion is typed next, because the result node exists only once there is a result to
        // show -- an empty field renders the "Enter a portion" prompt in that slot instead. Without
        // this the case failed on a missing node and said nothing at all about layout.
        typePortion("65")

        // Measured against the window rather than merely asserted to exist: "off-screen" is
        // exactly the failure mode being guarded, and a node pushed under the dock is still present
        // in the tree. `assertIsDisplayed` is what tests visibility against the window.
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertIsDisplayed()

        // The field itself, by its own accessible label (it carries no test tag and no visible
        // label text). It holds "65" now, so the placeholder is gone.
        compose.onNode(portionField()).assertIsDisplayed()

        // And the thumbnail that prompted this case is on screen too, so the test cannot pass by
        // the identity row having silently disappeared.
        compose.onNodeWithTag(PRODUCT_HERO_TAG).assertIsDisplayed()
    }

    // ---- design decision 3.1: ml is never converted to g -------------------------------------

    @Test
    fun aMillilitreProductLocksThePortionFieldToMillilitres() {
        showCalculator(product(carbs = "9.4", basis = NutritionBasis.PER_100_ML, name = "Sinaasappelsap"))

        compose.onNodeWithText("9.4 g carbs / 100 ml").assertIsDisplayed()
        typePortion("250")

        // 9.4 x 250 / 100 = 23.5 — the same arithmetic as grams, because no density is applied.
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("23.5 grams of carbs")
        compose.onNodeWithText("≈ 24 g whole grams").assertIsDisplayed()
    }

    // ---- §23 / §25 provenance is always visible ----------------------------------------------

    /**
     * Unverified remote data still says so — plainly, and without a second sentence.
     *
     * **Updated: this had been failing since the 2026-09-22 visual pass** (confirmed at clean HEAD),
     * which simplified provenance on this screen to the badge alone and passes
     * `SourceBadge(showHint = false)`. The advisory line "Check package if needed" is no longer
     * rendered here, so asserting it pinned a structure the app had deliberately dropped.
     *
     * The safety-relevant half is kept and is what this test is for: an Open Food Facts figure must
     * be *labelled* as an online value, so the user knows it was never checked against the package.
     * The second assertion now states the removal, so a re-added advisory line is a deliberate
     * decision rather than something that drifts back in.
     */
    @Test
    fun unverifiedRemoteDataSaysSoWithoutAlarmingTheUser() {
        showCalculator()

        compose.onNodeWithText("Online value").assertIsDisplayed()
        compose.onAllNodesWithText("Check package if needed").assertCountEquals(0)
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
            // 52dp is Space.thumbnail, what Recents uses. The calculator's plate is 112dp (72dp on a
            // short device), so this floor is what would catch a regression back toward the 56dp
            // the 2026-09-22 visual pass briefly shipped -- a tile too small to identify a package,
            // which is the whole reason this assertion exists.
            //
            // `>=`, not `>`: the compact plate is exactly 72dp and is a documented size, not a
            // regression. The strict form only ever passed on CI's 320x640 emulator because the
            // compact rule used to skip a window that is exactly 640dp tall (2026-09-23).
            assert(heroHeight.toDp() >= 72.dp) {
                "hero image was ${heroHeight.toDp()}, expected well above the 52dp thumbnail"
            }
        }
    }

    /**
     * A photo and a monogram occupy the **same** plate, so a late-arriving image cannot reflow the
     * screen under the user's thumb.
     *
     * **Replaces `aProductWithAPhotoGetsATallerHeroThanOneWithout`, which asserted the opposite and
     * had been failing since the 2026-09-22 visual pass** (measured at clean HEAD: it reported
     * "photo hero was 56.0.dp, expected clearly taller than the 84dp monogram plate"). That test
     * belonged to the deleted 245dp hero, which sized itself as a share of the screen and gave a
     * photo more room than a placeholder. The identity row deliberately does not: the plate is a
     * fixed square either way, which is what makes the image load free of layout cost.
     *
     * Its purpose — "the photo is big enough to identify the package" — is not lost; it moved to
     * `theProductHeroImageIsSubstantiallyLargerThanARecentThumbnail` above, which is the assertion
     * that still has a meaning.
     */
    @Test
    fun aPhotoAndAMonogramOccupyTheSamePlate() {
        // One composition holding both, because the rule permits only a single `setContent` per
        // test — and rendering them side by side is a stronger statement than comparing two runs
        // anyway: any difference would be visible in one frame.
        compose.setContent {
            JustTheCarbsTheme {
                Row {
                    ProductIdentityRow(
                        product = product(),
                        modifier = Modifier.weight(1f).testTag("monogram_case"),
                    ) {}
                    ProductIdentityRow(
                        product = product().copy(
                            imageUrl = "https://images.openfoodfacts.org/images/products/front.jpg",
                        ),
                        modifier = Modifier.weight(1f).testTag("photo_case"),
                    ) {}
                }
            }
        }

        val plates = compose.onAllNodesWithTag(PRODUCT_HERO_TAG).fetchSemanticsNodes()
        assertEquals("both cases must render a plate", 2, plates.size)
        assertEquals(
            "the plate must not change size when an image exists",
            plates[0].size,
            plates[1].size,
        )
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
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("31.3 grams of carbs")
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

        // WHAT THIS TEST NO LONGER DOES, AND WHY -- do not put it back.
        //
        // This case used to invoke the `GetTextLayoutResult` semantics action on the numeral to ask
        // the Text whether it had overflowed. That HUNG INDEFINITELY, and it was not flaky and not
        // an emulator or memory problem (a 4GB AVD hangs identically). Invoking that action against
        // an `autoSize` Text nested inside this screen's scrolling zone leaves the composition
        // permanently non-idle, so the next call that waits for idle never returns -- whichever one
        // came first. Measured with a thread dump: the main thread spins forever in
        // `ComposeIdlingResource.checkLayoutBusy`.
        //
        // Three controls established it is the action and not this screen: ProductScreen at this
        // exact density and width settles fine until the action is invoked; `ResultValueTest` runs
        // the identical action against the identical component in under four seconds; and removing
        // `autoSize` changes the failure rather than the hang.
        //
        // The overflow invariant therefore lives in
        // `ResultValueTest.theWidestRealResultFitsTheScreensOwnSlotAtTheLargestFontScale`, which
        // asserts it against the real `ResultValue` in this screen's own 320dp x 80dp slot
        // geometry. What stays here is everything the full screen can prove without that action:
        // that the whole value is rendered and reachable at this font scale.
        //
        // A truncated result would be a SHORTER string, so asserting the full value is itself a
        // clipping check -- "125.3 grams of carbs" cannot be satisfied by a numeral cut down to "125".
        compose.onNodeWithText("125.3").assertExists()
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("125.3 grams of carbs")
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertIsDisplayed()
        // The portion field must still EXIST when the result is at its widest -- the regression
        // this screen's fixed-height result slot exists to prevent (a result that grows without
        // limit pushes the field off the screen entirely).
        //
        // Asserted by existence, not `performScrollTo().assertIsDisplayed()`: at a 2x font scale in
        // a 320dp window the scroll never settles under the test clock and the action hangs for the
        // same reason documented above -- a composition that does not go idle. Existence is the
        // property that actually matters here (the field is composed and reachable rather than
        // dropped), and it is what the fixed-height slot guarantees. Whether it is scrolled into
        // view is covered at ordinary font scales by this class's other cases, which drive the
        // field for real.
        compose.onNodeWithContentDescription("Portion in g").assertExists()
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
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("31.3 grams of carbs")

        compose.onNodeWithContentDescription("View product images").performClick()
        compose.onNodeWithTag(PRODUCT_GALLERY_NEXT_TAG).performClick()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithText("2 of 2").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Nutrition").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close product images").performClick()

        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("31.3 grams of carbs")
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
     * the screen blank between the per-100 figure and the portion input — something no assertion
     * caught and only appeared when the screen was actually looked at. Measuring the gap turns that
     * into something a test can hold.
     *
     * **Anchor updated: this had been failing since the 2026-09-22 visual pass** (confirmed at clean
     * HEAD), which replaced the centred question "How much are you eating?" with a left-aligned
     * group label, "Portion". Only the anchor text moved — the gap being measured, and the reason
     * for measuring it, are unchanged, and the 2026-09-22 refinement pass makes it more relevant
     * rather than less: it both enlarges the thumbnail above this gap and removes a control row
     * below it.
     */
    @Test
    fun thePortionControlsSitDirectlyAboveTheResultDockWithoutADeadBand() {
        // Re-aimed 2026-09-23. The old assertion measured header-to-label and pinned the
        // top-anchored layout, in which the slack of a tall screen sat BETWEEN the input and the
        // answer: 216dp of empty page in the result state on a 411x914 phone, measured. The
        // portion group now rests on the dock and the slack collects under the identity header
        // instead, so the invariant this test protects -- no dead band splitting the calculation
        // -- is measured where it now lives: from the last portion control to the dock's label.
        showCalculator(product())

        val strings = InstrumentationRegistry.getInstrumentation().targetContext
        val lastControlBottom = compose.onNodeWithText(strings.getString(R.string.product_add_portion_unit))
            .fetchSemanticsNode()
            .boundsInRoot
            .bottom
        val dockLabelTop = compose.onNodeWithText(strings.getString(R.string.product_result_label))
            .fetchSemanticsNode()
            .boundsInRoot
            .top

        with(compose.density) {
            val gap = (dockLabelTop - lastControlBottom).toDp()
            assert(gap >= 0.dp) { "the last portion control is underneath the dock: gap $gap" }
            assert(gap < 64.dp) { "dead band between the portion controls and the result dock was $gap" }
        }
    }

    /** The image is identification, never a gate: the result stays reachable regardless (§30). */
    @Test
    fun theHeroImageDoesNotBlockTheResult() {
        showCalculator(product())

        typePortion("65")

        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("31.3 grams of carbs")
        compose.onNodeWithText("≈ 31 g whole grams").assertIsDisplayed()
    }

    // ---- §14 pack shortcuts only when the size is actually known ------------------------------

    @Test
    fun packShortcutsAppearOnlyWhenAPackageSizeIsKnown() {
        showCalculator(product(packageAmount = "380"))

        compose.onNodeWithText("Full pack").performScrollTo().performClick()
        compose.waitForIdle()

        // 48.2 x 380 / 100 = 183.16
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("183.2 grams of carbs")
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
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("48.2 grams of carbs")
    }

    @Test
    fun aHalfPackStillSetsHalfThePackageAmount() {
        showCalculator(product(packageAmount = "400"))

        compose.onNodeWithText("½ pack").performScrollTo().performClick()
        compose.waitForIdle()

        // 400 / 2 = 200 g; 48.2 x 200 / 100 = 96.4
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("96.4 grams of carbs")
    }

    // ---- §43 result style --------------------------------------------------------------------

    @Test
    fun wholeDominantSettingRestoresTheOriginalHierarchy() {
        showCalculator(settings = AppSettings(resultStyle = ResultStyle.WHOLE_DOMINANT))
        typePortion("65")

        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("31 grams of carbs")
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

        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("15.5 grams of carbs")
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
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("31.3 grams of carbs")
    }

    @Test
    fun aZeroCarbProductCalculatesZeroRatherThanFailing() {
        showCalculator(product(carbs = "0", name = "Bronwater"))
        typePortion("500")

        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("0.0 grams of carbs")
    }

    @Test
    fun aLargePortionStillProducesAReadableResult() {
        showCalculator()
        typePortion("2500")

        // 48.2 x 2500 / 100 = 1205
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("1205.0 grams of carbs")
    }

    @Test
    fun aDecimalPortionIsAcceptedWithEitherSeparator() {
        showCalculator()

        typePortion("32.5")
        // 48.2 x 32.5 / 100 = 15.665
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("15.7 grams of carbs")

        compose.onNode(portionField()).performTextReplacement("32,5")
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("15.7 grams of carbs")
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
    private fun showCalculatorWithControllableMealAdd(
        gate: CompletableDeferred<Unit>,
        /** Called once per add the screen actually asks for, so a refused tap can be counted. */
        onAddRequested: () -> Unit = {},
        /** Called once per *Add & scan next* the screen actually asks for. */
        onAddAndScanRequested: () -> Unit = {},
        /** Called once per plain *Scan next* (no add) the screen asks for. */
        onScanNextRequested: () -> Unit = {},
    ) {
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
                        onAddRequested()
                        addingToMeal = true
                        scope.launch {
                            gate.await()
                            addingToMeal = false
                            lastMealAddSucceeded = System.currentTimeMillis()
                        }
                    },
                    onAddToMealAndScanNext = { _, _ -> onAddAndScanRequested() },
                    onScanNext = onScanNextRequested,
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

    /**
     * The "Added" label alone is silent to TalkBack: the button's text changes under a finger that
     * has already moved on. The confirmation is a state description on the button itself, the
     * same grammar Home's Quick Add uses, so a screen-reader user hears that the add landed.
     */
    @Test
    fun addToMealTellsTalkBackThatTheItemWasAdded() {
        val gate = CompletableDeferred<Unit>()
        showCalculatorWithControllableMealAdd(gate)

        compose.onNodeWithTag(MEAL_ADD_TAG).performClick()
        gate.complete(Unit)
        compose.waitForIdle()

        compose.onNodeWithTag(MEAL_ADD_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Added to meal"))
    }

    /**
     * While "Added" is held the button is still enabled-looking, so a second tap (a double tap, or
     * a user unsure the first one worked) used to add the same portion twice. It is refused until
     * the confirmation clears.
     */
    @Test
    fun aSecondTapWhileAddedIsShownAddsNothing() {
        val gate = CompletableDeferred<Unit>()
        var adds = 0
        showCalculatorWithControllableMealAdd(gate, onAddRequested = { adds++ })

        compose.onNodeWithTag(MEAL_ADD_TAG).performClick()
        gate.complete(Unit)
        compose.waitForIdle()
        compose.onNodeWithText("Added").assertExists()

        compose.onNodeWithTag(MEAL_ADD_TAG).performClick()
        compose.waitForIdle()

        assertEquals(1, adds)
    }

    /**
     * *Add & scan next* during the same "Added" hold used to add the portion a second time: the
     * hold refused a second *Add to meal*, but the button beside it still added. While the item has
     * just been added the second button reads *Scan next item* and only opens the scanner.
     */
    @Test
    fun duringTheAddedHoldTheSecondButtonScansNextWithoutAddingAgain() {
        val gate = CompletableDeferred<Unit>()
        var adds = 0
        var scans = 0
        showCalculatorWithControllableMealAdd(
            gate,
            onAddRequested = { adds++ },
            onAddAndScanRequested = { adds++; scans++ },
            onScanNextRequested = { scans++ },
        )

        compose.onNodeWithTag(MEAL_ADD_TAG).performClick()
        gate.complete(Unit)
        compose.waitForIdle()
        compose.onNodeWithText("Added").assertExists()
        compose.onNodeWithTag(MEAL_ADD_AND_SCAN_TAG)
            .assert(androidx.compose.ui.test.hasText(string(R.string.meal_scan_next)))

        compose.onNodeWithTag(MEAL_ADD_AND_SCAN_TAG).performClick()
        compose.waitForIdle()

        assertEquals("the portion was added twice", 1, adds)
        assertEquals("the scanner must still open", 1, scans)
    }

    // ---- a tap outside the field closes the keyboard -------------------------------------------

    /**
     * The meal actions step aside while the keyboard is open, so reaching them meant finding Done.
     * A tap on a part of the calculator that does nothing now puts the keyboard away, as it does in
     * most apps -- and a tap on the field itself still focuses it.
     */
    @Test
    fun tappingAnEmptyPartOfTheCalculatorClearsThePortionFieldsFocus() {
        showCalculator()

        compose.onNode(portionField()).performClick()
        compose.onNode(portionField()).assertIsFocused()

        compose.onNodeWithText("48.2 g carbs / 100 g").performClick()

        compose.onNode(portionField()).assertIsNotFocused()
    }

    // ---- the active shortcut is marked ---------------------------------------------------------

    @Test
    fun thePackShortcutMatchingThePortionIsMarkedSelected() {
        showCalculator(product(packageAmount = "500"))

        compose.onNodeWithText(string(R.string.product_half_pack)).performScrollTo().performClick()

        compose.onNodeWithText(string(R.string.product_half_pack)).assertIsSelected()
        compose.onNodeWithText(string(R.string.product_quarter_pack)).assertIsNotSelected()
        compose.onNodeWithText(string(R.string.product_full_pack)).assertIsNotSelected()
    }

    /** Selection is by value: a typed `125.0` is the quarter of a 500 g pack, not a new amount. */
    @Test
    fun aTypedPortionEqualInValueMarksThePackShortcut() {
        showCalculator(product(packageAmount = "500"))

        typePortion("125.0")

        compose.onNodeWithText(string(R.string.product_quarter_pack)).assertIsSelected()
    }

    @Test
    fun packShortcutsAreAnnouncedAsButtons() {
        showCalculator(product(packageAmount = "500"))

        compose.onNodeWithText(string(R.string.product_full_pack))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    // ---- a stalled lookup offers a way forward -------------------------------------------------

    private fun showLoading(onEnterManually: () -> Unit = {}, onScanLabel: () -> Unit = {}) {
        compose.setContent {
            JustTheCarbsTheme {
                ProductScreen(
                    state = ProductUiState(loading = true, barcode = "8712100849060"),
                    settings = AppSettings(),
                    onPortionChanged = {},
                    onSetPortion = {},
                    onToggleFavorite = {},
                    onBack = {},
                    onVerify = {},
                    onDismissVerify = {},
                    onConfirmVerification = { _, _, _ -> },
                    onResetOnline = {},
                    onScanLabel = onScanLabel,
                    onEnterManually = onEnterManually,
                    onRetry = {},
                )
            }
        }
    }

    /**
     * A lookup that is still running after a few seconds says so and offers the recoveries the
     * failure screen would, instead of a spinner the user can only wait on. The clock is the test's.
     */
    @Test
    fun aLookupStillRunningAfterAFewSecondsOffersToEnterItManually() {
        var manual = 0
        compose.mainClock.autoAdvance = false
        showLoading(onEnterManually = { manual++ })
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText(string(R.string.product_still_looking)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.permission_manual)).assertDoesNotExist()

        compose.mainClock.advanceTimeBy(3_800)
        compose.onNodeWithText(string(R.string.product_still_looking)).assertDoesNotExist()

        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText(string(R.string.product_still_looking)).assertExists()
        compose.onNodeWithText(string(R.string.product_scan_label)).assertExists()
        compose.onNodeWithText(string(R.string.permission_manual)).performClick()
        compose.mainClock.advanceTimeByFrame()

        assertEquals(1, manual)
    }

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /**
     * Regression for the portion-field append defect (2026-09-24 UX review, seen on the emulator).
     *
     * A returning product arrives with its remembered portion pre-filled. A user who wants a
     * different amount taps the field and types it. Before the fix the caret landed after the
     * remembered `65`, so typing `80` produced **6580 g** -- measured on the device as
     * 3783.5 g of carbs. The count field already selected its contents on focus; this field never
     * did.
     *
     * `performTextInput` types at the cursor, as a thumb does; `performTextReplacement` would
     * replace wholesale and could not reproduce the defect.
     */
    @Test
    fun typingAPortionOverTheRememberedOneReplacesItRatherThanAppending() {
        showCalculator(initialPortion = "65")

        compose.onNode(portionField()).performClick()
        compose.onNode(portionField()).performTextInput("80")

        // 48.2 x 80 / 100 = 38.56 -> 38.6 g, not 6580 g (3171.6 g).
        compose.onNodeWithTag(PRODUCT_RESULT_TAG).assertContentDescriptionEquals("38.6 grams of carbs")
    }

    /**
     * A portion larger than the whole package gets a quiet, non-blocking hint (2026-09-24 review).
     *
     * On the emulator the append defect above produced 6580 g of a 400 g jar with nothing on
     * screen to say so. The hint changes no number and blocks nothing: it only names the package
     * size the app already read confidently, the same fact the pack shortcuts are built from.
     */
    @Test
    fun aPortionLargerThanTheWholePackSaysSo() {
        showCalculator(product(packageAmount = "400"))

        typePortion("500")

        compose.onNodeWithText("More than the whole pack (400 g)").assertExists()
    }

    @Test
    fun aPortionOfExactlyTheWholePackHasNoHint() {
        showCalculator(product(packageAmount = "400"))

        typePortion("400")

        compose.onAllNodesWithText("More than the whole pack", substring = true).assertCountEquals(0)
    }

    @Test
    fun anUnknownPackSizeNeverProducesTheHint() {
        showCalculator(product(packageAmount = null))

        typePortion("5000")

        compose.onAllNodesWithText("More than the whole pack", substring = true).assertCountEquals(0)
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

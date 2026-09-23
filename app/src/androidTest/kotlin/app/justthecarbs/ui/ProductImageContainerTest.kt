package app.justthecarbs.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.R
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.CarbCalculator
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionParser
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.VerificationStatus
import app.justthecarbs.ui.components.COMPACT_THUMBNAIL_SIZE
import app.justthecarbs.ui.components.HERO_PHOTO_HEIGHTS
import app.justthecarbs.ui.components.PRODUCT_HERO_TAG
import app.justthecarbs.ui.components.ProductIdentityRow
import app.justthecarbs.ui.components.ROW_THUMBNAIL_SIZES
import app.justthecarbs.ui.meal.MEAL_BAR_TAG
import app.justthecarbs.ui.product.PRODUCT_VERIFY_INLINE_TAG
import app.justthecarbs.ui.product.ProductScreen
import app.justthecarbs.ui.product.ProductUiState
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.SuccessResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.time.Instant
import java.util.Collections

/**
 * The calculator's product image container (2026-09-23 hero redesign).
 *
 * The container is sized from the room the calculator leaves, in fixed steps, and never from the
 * image: a photo, a photo still loading, a photo that failed and the initials fallback all occupy
 * one box from the first layout, and a photo that arrives late moves nothing. The image pipeline is
 * replaced for the duration of each test by a loader whose answers the test controls by URL, so
 * "late", "loading" and "broken" are states the test holds rather than races it hopes to win.
 *
 * Whether a photo is on screen is read from pixels, not from the initials disappearing: the
 * container clears its children's semantics (TalkBack hears "View product images", not the
 * initials), so a wait on the initials' text would pass at once and prove nothing.
 *
 * **Instrumented: needs a device or emulator.**
 */
@OptIn(DelicateCoilApi::class)
class ProductImageContainerTest {

    @get:Rule
    val compose = createComposeRule()

    /** Released by a test to let the photo whose URL contains "late" arrive. */
    private val latePhoto = CompletableDeferred<Unit>()

    /** Every URL the controlled loader has answered, success or failure. */
    private val answered: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    @Before
    fun controlTheImagePipeline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .memoryCache(null)
                .diskCache(null)
                .components { add(ControlledImages(latePhoto, answered)) }
                .build(),
        )
    }

    /** The application is the loader factory, so a reset hands the production loader back. */
    @After
    fun restoreTheImagePipeline() {
        SingletonImageLoader.reset()
    }

    // ---- 1. photo, loading, broken and initials reserve the same container ----------------------

    @Test
    fun aPhotoALoadingPhotoABrokenPhotoAndInitialsReserveTheSameHeroContainer() {
        // Room for the largest hero with its caption, whatever the device's own window is.
        showFourHeaders(width = 300.dp, room = HERO_PHOTO_HEIGHTS.max() + 120.dp)
        awaitAnswers("photo", "broken")
        assertTrue("precondition: the loaded photo is drawn", containerShowsThePhoto(0))
        assertFalse("precondition: the slow photo is not drawn", containerShowsThePhoto(1))

        val containers = compose.onAllNodesWithTag(PRODUCT_HERO_TAG).fetchSemanticsNodes()
        assertEquals("all four headers must render a container", 4, containers.size)
        containers.forEach { node ->
            assertEquals("container ${node.size} differs from ${containers[0].size}", containers[0].size, node.size)
        }
        with(compose.density) {
            assertEquals(
                "with room to spare the container is the largest hero size",
                HERO_PHOTO_HEIGHTS.max().roundToPx(),
                containers[0].size.height,
            )
        }
    }

    @Test
    fun aPhotoALoadingPhotoABrokenPhotoAndInitialsReserveTheSameRowContainer() {
        // Too little room for any hero: the row, at its largest step that fits.
        showFourHeaders(width = 300.dp, room = HERO_PHOTO_HEIGHTS.min() - 1.dp)
        awaitAnswers("photo", "broken")
        assertTrue("precondition: the loaded photo is drawn", containerShowsThePhoto(0))

        val containers = compose.onAllNodesWithTag(PRODUCT_HERO_TAG).fetchSemanticsNodes()
        assertEquals(4, containers.size)
        containers.forEach { node -> assertEquals(containers[0].size, node.size) }
        with(compose.density) {
            val edge = containers[0].size.height
            assertEquals("a row container is square", containers[0].size.width, edge)
            assertTrue(
                "row container $edge px is not one of the fixed row sizes",
                (ROW_THUMBNAIL_SIZES + COMPACT_THUMBNAIL_SIZE).any { it.roundToPx() == edge },
            )
        }
    }

    // ---- 2. a late photo moves nothing ---------------------------------------------------------

    @Test
    fun aPhotoThatArrivesLateMovesNothingOnTheCalculator() {
        showCalculator(product = product(name = "Late photo", imageUrl = photoUrl("late")))

        val before = snapshot()
        assertFalse("precondition: nothing is drawn before the photo arrives", containerShowsThePhoto(0))

        latePhoto.complete(Unit)
        awaitAnswers("late")
        assertTrue("precondition: the late photo is drawn", containerShowsThePhoto(0))

        assertEquals("the photo arrived and something moved", before, snapshot())
    }

    // ---- 3. a long name keeps usable width -----------------------------------------------------

    @Test
    fun aThreeLineFavouriteProductNameKeepsItsWidthBesideTheStarAndMenu() {
        val name = "Biologische volkoren meergranen boterhambroodjes met zonnebloempitten, " +
            "lijnzaad en een vleugje zeezout uit de Waddenzee"
        showCalculator(product = product(name = name, imageUrl = photoUrl("photo")).copy(favorite = true))

        val strings = InstrumentationRegistry.getInstrumentation().targetContext
        val root = compose.onNodeWithTag(ROOT_TAG).fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText(name).fetchSemanticsNode().boundsInRoot
        val star = compose.onNodeWithContentDescription(strings.getString(R.string.favorite_remove))
            .fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithContentDescription(strings.getString(R.string.product_more_actions))
            .fetchSemanticsNode().boundsInRoot
        val image = compose.onNodeWithTag(PRODUCT_HERO_TAG).fetchSemanticsNode().boundsInRoot

        assertTrue("the title runs under the star: title=$title star=$star", title.right <= star.left)
        assertTrue("the star runs under the menu: star=$star menu=$menu", star.right <= menu.left)
        assertTrue("the menu is off screen: menu=$menu root=$root", menu.right <= root.right)
        // The same share of the bar the title always had: the image sits under the bar, never
        // beside the title, so it cannot take the title's width.
        assertTrue("the title keeps under 40% of the width: title=$title root=$root", title.width >= root.width * 0.4f)
        assertTrue("the image reaches into the title bar: image=$image title=$title", image.top >= title.bottom)
    }

    @Test
    fun theFactsBesideTheLargestRowThumbnailKeepUsableWidth() {
        // The row's facts column is what a larger thumbnail takes width from. 371dp is the content
        // width of a 411dp phone, the narrowest window that gets the stepped-up row sizes.
        showFourHeaders(width = 371.dp, room = HERO_PHOTO_HEIGHTS.min() - 1.dp)
        val image = compose.onAllNodesWithTag(PRODUCT_HERO_TAG).fetchSemanticsNodes()[0]
        val facts = compose.onAllNodesWithTag(FACTS_TAG).fetchSemanticsNodes()[0]
        with(compose.density) {
            // A 640dp-or-shorter window (CI's emulator) has one row size, the compact one.
            val compactWindow = InstrumentationRegistry.getInstrumentation().targetContext
                .resources.configuration.screenHeightDp <= 640
            val largest = if (compactWindow) COMPACT_THUMBNAIL_SIZE else ROW_THUMBNAIL_SIZES.max()
            assertEquals("precondition: the largest row step", largest.roundToPx(), image.size.height)
            assertTrue(
                "the facts overlap the image: facts=${facts.boundsInRoot} image=${image.boundsInRoot}",
                facts.boundsInRoot.left >= image.boundsInRoot.right,
            )
            // The column beside the image runs from the facts' left edge to the header's right edge
            // (the image's left plus the header width); the text node is only as wide as its words.
            val column = image.boundsInRoot.left + 371.dp.toPx() - facts.boundsInRoot.left
            assertTrue("the facts column is ${column.toDp()} wide", column.toDp() >= 200.dp)
            // And the per-100 figure, the longest fact, still fits on one line in it.
            assertTrue("the per-100 figure wrapped: ${facts.size.height.toDp()} tall", facts.size.height.toDp() < 36.dp)
        }
    }

    // ---- 4. compact and large-text layouts -----------------------------------------------------

    @Test
    fun atLargeTextThePortionFieldStaysAboveTheDockAndClearOfTheImage() {
        showCalculator(product = product(name = "Large text", imageUrl = photoUrl("photo")), fontScale = 1.8f)

        val strings = InstrumentationRegistry.getInstrumentation().targetContext
        val image = compose.onNodeWithTag(PRODUCT_HERO_TAG).fetchSemanticsNode()
        val field = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithText(strings.getString(R.string.product_result_label))
            .fetchSemanticsNode().boundsInRoot

        with(compose.density) {
            assertTrue("the image shrank below the compact size", image.size.height.toDp() >= COMPACT_THUMBNAIL_SIZE)
        }
        assertTrue("the portion field is under the dock: field=$field dock=$dock", field.top < dock.top)
        assertTrue(
            "the image overlaps the portion field: image=${image.boundsInRoot} field=$field",
            image.boundsInRoot.bottom <= field.top,
        )
    }

    /**
     * The header is never drawn over what sits above it. The calculator reserves the header's
     * height from its intrinsic measurement before the portion controls take theirs, and a header
     * that then measures taller than the reserve is centred on it by Compose: it rises over the meal
     * bar by half the difference. At 1.8x text beside the row's thumbnail the facts wrap onto three
     * lines (figure, badge, Verify), which is where a `FlowRow`'s estimate (one line) and its
     * measurement parted: measured on the emulator, the photo 12px over the meal bar.
     *
     * On CI's 320x640dp window the answer's dock at 1.8x leaves less height than even the smallest
     * row, so no estimate can make it fit; there the header must stay at the top and be cut at
     * its lower edge. Before that rule it rose 54dp over the meal bar.
     */
    @Test
    fun atLargeTextTheHeaderNeverRisesIntoTheMealBar() {
        showCalculator(
            product = product(name = "Large text", imageUrl = photoUrl("photo")),
            fontScale = 1.8f,
            initialPortion = "65",
            mealItems = listOf(mealItem()),
        )

        val bar = compose.onNodeWithTag(MEAL_BAR_TAG).fetchSemanticsNode().unclippedBounds()
        val image = compose.onNodeWithTag(PRODUCT_HERO_TAG).fetchSemanticsNode().unclippedBounds()
        val verify = compose.onNodeWithTag(PRODUCT_VERIFY_INLINE_TAG).fetchSemanticsNode().unclippedBounds()

        // The case that matters: the facts are taller than the image beside them, so the header's
        // height is the facts' height, and the reserve is only as good as their estimate.
        assertTrue("precondition: the facts reach below the image: verify=$verify image=$image", verify.bottom > image.bottom)
        assertTrue("the image rises into the meal bar: image=$image bar=$bar", image.top >= bar.bottom)
    }

    @Test
    fun onTheDevicesOwnWindowAt1_3xThePortionFieldIsWhollyInViewOnArrival() {
        showCalculator(product = product(name = "Own window", imageUrl = photoUrl("photo")), fontScale = 1.3f)

        // Wholly in view, not a sliver: `assertIsDisplayed` passes on one. Clipped bounds equal to
        // the node's own size means nothing -- the dock, the image, the window edge -- cuts it.
        val field = compose.onNode(hasSetTextAction()).fetchSemanticsNode()
        assertTrue(
            "portion field is clipped on arrival: visible=${field.boundsInRoot} size=${field.size}",
            field.boundsInRoot.height >= field.size.height - 1f &&
                field.boundsInRoot.width >= field.size.width - 1f,
        )
    }

    // ---- helpers -------------------------------------------------------------------------------

    private data class Snapshot(val image: Rect, val facts: Rect, val field: Rect, val dockLabel: Rect)

    private fun SemanticsNode.unclippedBounds() =
        Rect(positionInRoot, Size(size.width.toFloat(), size.height.toFloat()))

    private fun snapshot(): Snapshot {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext
        return Snapshot(
            image = compose.onNodeWithTag(PRODUCT_HERO_TAG).fetchSemanticsNode().unclippedBounds(),
            facts = compose.onNodeWithText("48.2 g carbs / 100 g").fetchSemanticsNode().unclippedBounds(),
            field = compose.onNode(hasSetTextAction()).fetchSemanticsNode().unclippedBounds(),
            dockLabel = compose.onNodeWithText(strings.getString(R.string.product_result_label))
                .fetchSemanticsNode().unclippedBounds(),
        )
    }

    /** Waits until the loader has answered a URL containing each fragment, then for the UI. */
    private fun awaitAnswers(vararg fragments: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            fragments.all { fragment -> synchronized(answered) { answered.any { fragment in it } } }
        }
        compose.waitForIdle()
    }

    /** The centre of the [index]th container is the controlled photo's red, not a plate or initials. */
    private fun containerShowsThePhoto(index: Int): Boolean {
        val bitmap = compose.onAllNodesWithTag(PRODUCT_HERO_TAG)[index].captureToImage().asAndroidBitmap()
        val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        return android.graphics.Color.red(pixel) > 150 &&
            android.graphics.Color.green(pixel) < 90 &&
            android.graphics.Color.blue(pixel) < 90
    }

    /**
     * Four headers with identical room: a photo (first, so it is on screen for the pixel check),
     * one that never finishes loading, one that fails, and one with no photo at all.
     */
    private fun showFourHeaders(width: Dp, room: Dp) {
        val cases = listOf(
            product(name = "Photo Here", imageUrl = photoUrl("photo")),
            product(name = "Slow Loading", imageUrl = photoUrl("slow")),
            product(name = "Broken Response", imageUrl = photoUrl("broken")),
            product(name = "Initials Only", imageUrl = null),
        )
        compose.setContent {
            JustTheCarbsTheme {
                // Scrolling, so every header gets its full room rather than what the window has left.
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    cases.forEach { case ->
                        Box(Modifier.requiredSize(width, room)) {
                            ProductIdentityRow(product = case, allowHero = true) {
                                Text(text = "57.5 g carbs / 100 g", modifier = Modifier.testTag(FACTS_TAG))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun showCalculator(
        product: Product,
        fontScale: Float? = null,
        initialPortion: String = "",
        mealItems: List<MealItem> = emptyList(),
    ) {
        compose.setContent {
            var portion by remember { mutableStateOf(initialPortion) }
            val parsed = PortionParser.parse(portion)
            val screen = @Composable {
                JustTheCarbsTheme {
                    Box(Modifier.testTag(ROOT_TAG)) {
                        ProductScreen(
                            state = ProductUiState(
                                loading = false,
                                product = product,
                                portionText = portion,
                                result = parsed?.let { CarbCalculator.calculate(product.carbsPer100, it, product.basis) },
                                barcode = product.barcode,
                                mealItems = mealItems,
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
                        )
                    }
                }
            }
            if (fontScale != null) {
                // The device's own density with a larger font scale, never an invented window.
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                    content = screen,
                )
            } else {
                screen()
            }
        }
        compose.waitForIdle()
    }

    private fun product(name: String, imageUrl: String?) = Product(
        barcode = "8712100849060",
        name = name,
        carbsPer100 = BigDecimal("48.2"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
        imageUrl = imageUrl,
    )

    /** One item already on the plate, so the calculator shows the meal bar above the header. */
    private fun mealItem() = MealItem.weightBased(
        id = 1L,
        productBarcode = "8712100849061",
        displayName = "Bread",
        portionDescription = "80 g",
        resolvedAmount = BigDecimal("80"),
        basis = NutritionBasis.PER_100_G,
        carbsPer100 = BigDecimal("43.5"),
        exactCarbs = BigDecimal("34.8"),
        addedAt = Instant.parse("2026-09-23T10:00:00Z"),
    )

    /** A URL the image validator accepts (HTTPS, Open Food Facts' image host). */
    private fun photoUrl(kind: String) =
        "https://images.openfoodfacts.org/images/products/871/210/084/9060/front_$kind.400.jpg"

    /**
     * Answers by URL: "late" waits for [late], "slow" never answers, "broken" fails, anything else
     * is a 300x400 red photo at once. No network, no cache.
     */
    private class ControlledImages(
        private val late: CompletableDeferred<Unit>,
        private val answered: MutableSet<String>,
    ) : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val url = chain.request.data.toString()
            if ("slow" in url) awaitCancellation()
            if ("broken" in url) {
                answered += url
                return ErrorResult(null, chain.request, IOException("controlled failure"))
            }
            if ("late" in url) late.await()
            val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.rgb(200, 40, 40))
            answered += url
            return SuccessResult(image = bitmap.asImage(), request = chain.request, dataSource = DataSource.MEMORY)
        }
    }

    private companion object {
        const val ROOT_TAG = "image_container_test_root"
        const val FACTS_TAG = "image_container_test_facts"
    }
}

package app.justthecarbs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.R
import app.justthecarbs.data.ProductRepository
import app.justthecarbs.data.local.JustTheCarbsDatabase
import app.justthecarbs.data.local.RoomMealDataSource
import app.justthecarbs.data.local.RoomPortionUnitDataSource
import app.justthecarbs.data.local.RoomPortionUsageDataSource
import app.justthecarbs.data.local.RoomProductDataSource
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.CarbCalculator
import app.justthecarbs.domain.InputMode
import app.justthecarbs.domain.MealTotal
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.Product
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.ProductDataSource
import app.justthecarbs.domain.ProductFetchResult
import app.justthecarbs.domain.ProductSearchResult
import app.justthecarbs.domain.ProductSearchSource
import app.justthecarbs.ui.home.HOME_BODY_TAG
import app.justthecarbs.ui.home.HOME_QUICK_ADD_TAG
import app.justthecarbs.ui.home.HOME_RECENT_FORGET_TAG
import app.justthecarbs.ui.home.HomeScreen
import app.justthecarbs.ui.home.HomeViewModel
import app.justthecarbs.ui.home.QuickAddStatus
import app.justthecarbs.ui.home.RecentEntry
import app.justthecarbs.ui.meal.MEAL_BAR_TAG
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * *Quick Add* on Home's remembered cards (1.0.8 slice 2).
 *
 * The stateless half pins what a person sees and what TalkBack hears; the last two tests drive the
 * real [HomeViewModel] over an in-memory Room database, because "a double tap writes one meal line"
 * is only worth asserting against the storage the app actually uses.
 *
 * **Instrumented: needs a device or emulator.**
 */
class HomeQuickAddScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var database: JustTheCarbsDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    private fun product(
        barcode: String = "1",
        name: String = "Hagelslag puur",
        lastPortion: String? = "65",
        favorite: Boolean = false,
        mode: InputMode? = null,
    ) = Product(
        barcode = barcode,
        name = name,
        carbsPer100 = BigDecimal("48.2"),
        basis = NutritionBasis.PER_100_G,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        lastPortion = lastPortion?.let(::BigDecimal),
        lastInputMode = mode,
        lastUsedAt = Instant.EPOCH,
        favorite = favorite,
    )

    private fun show(
        recents: List<RecentEntry>,
        status: Map<String, QuickAddStatus> = emptyMap(),
        onQuickAdd: (RecentEntry, String) -> Unit = { _, _ -> },
        onOpenProduct: (String) -> Unit = {},
        onToggleFavorite: (Product) -> Unit = {},
        density: Density? = null,
        widthDp: Int? = null,
    ) {
        compose.setContent {
            val content = @androidx.compose.runtime.Composable {
                JustTheCarbsTheme {
                    val screen = @androidx.compose.runtime.Composable {
                        HomeScreen(
                            recents = recents,
                            settings = AppSettings(),
                            onScan = {},
                            onManualEntry = {},
                            onOpenProduct = onOpenProduct,
                            onToggleFavorite = onToggleFavorite,
                            onOpenSettings = {},
                            quickAddStatus = status,
                            onQuickAdd = onQuickAdd,
                        )
                    }
                    if (widthDp != null) {
                        Box(Modifier.width(widthDp.dp).fillMaxHeight()) { screen() }
                    } else {
                        screen()
                    }
                }
            }
            if (density != null) {
                CompositionLocalProvider(LocalDensity provides density, content = content)
            } else {
                content()
            }
        }
    }

    private fun stringOf(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private val quickAddLabel get() = stringOf(R.string.recent_quick_add_description, "65 g", "Hagelslag puur")

    // ---- presence and wording ----------------------------------------------------------------

    /** TalkBack hears the product and the portion, not a bare "Add". */
    @Test
    fun quickAddAnnouncesTheProductAndThePortionItAdds() {
        show(recents = listOf(RecentEntry(product(), null)))

        compose.onNodeWithContentDescription(quickAddLabel)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        compose.onAllNodes(hasContentDescription("Add"))
            .assertCountEquals(0)
    }

    /** The portion is printed once, beside the button — the button itself carries no text. */
    @Test
    fun thePortionIsShownOnceAndTheButtonHasNoText() {
        show(recents = listOf(RecentEntry(product(), null)))

        compose.onNodeWithText("65 g").assertIsDisplayed()
        compose.onAllNodes(hasTestTag(HOME_QUICK_ADD_TAG), useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodes(hasText("65 g"), useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodes(hasText("Add", substring = true), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun aFavouriteThatWasNeverUsedOffersNoQuickAddAndStillOpens() {
        var opened: String? = null
        show(
            recents = listOf(RecentEntry(product(lastPortion = null, favorite = true), null)),
            onOpenProduct = { opened = it },
        )

        compose.onAllNodes(hasTestTag(HOME_QUICK_ADD_TAG), useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText(stringOf(R.string.recent_never_used)).assertIsDisplayed()
        compose.onNodeWithText("Hagelslag puur", useUnmergedTree = true).performClick()
        assertEquals("1", opened)
    }

    /** Last used as a count whose unit is gone: the card still describes it, but offers no action. */
    @Test
    fun anUnresolvableCountOffersNoQuickAdd() {
        val lost = product(mode = InputMode.PORTION_UNIT).copy(lastSelectedPortionUnitId = 9, lastCount = BigDecimal("2"))
        show(recents = listOf(RecentEntry(lost, lastUnit = null)))

        compose.onAllNodes(hasTestTag(HOME_QUICK_ADD_TAG), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun favouritesAndRecentsUseTheSameQuickAdd() {
        val added = mutableListOf<Pair<String, String>>()
        show(
            recents = listOf(
                RecentEntry(product(barcode = "1", favorite = true), null),
                RecentEntry(product(barcode = "2", name = "Melk"), null),
            ),
            onQuickAdd = { entry, description -> added += entry.product.barcode to description },
        )

        // Per card, not a global count. This used to assert two `HOME_QUICK_ADD_TAG` nodes straight
        // after rendering, which is not a property a LazyColumn offers: on CI's profile-less
        // 320x640 @160dpi emulator only the first card is composed (its pill sits at y=572 of 640),
        // so the second pill does not exist in the semantics tree and the count read 1. The
        // contract is that each card type exposes the same Quick Add, so each card is scrolled into
        // the composition and judged on its own. Reproduce with `wm size 320x640` / `wm density 160`.
        val body = compose.onNodeWithTag(HOME_BODY_TAG)
        val favouriteLabel = stringOf(R.string.recent_quick_add_description, "65 g", "Hagelslag puur")
        val recentLabel = stringOf(R.string.recent_quick_add_description, "65 g", "Melk")

        body.performScrollToNode(hasContentDescription(favouriteLabel))
        compose.onNodeWithContentDescription(favouriteLabel)
            .assert(hasTestTag(HOME_QUICK_ADD_TAG))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .performClick()

        body.performScrollToNode(hasContentDescription(recentLabel))
        compose.onNodeWithContentDescription(recentLabel)
            .assert(hasTestTag(HOME_QUICK_ADD_TAG))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .performClick()

        assertEquals(listOf("1" to "65 g", "2" to "65 g"), added)
    }

    // ---- interaction -------------------------------------------------------------------------

    @Test
    fun tappingQuickAddReportsTheCardAndItsPortionAndStaysOnHome() {
        val added = mutableListOf<Pair<String, String>>()
        var opened: String? = null
        show(
            recents = listOf(RecentEntry(product(), null)),
            onQuickAdd = { entry, description -> added += entry.product.barcode to description },
            onOpenProduct = { opened = it },
        )

        compose.onNodeWithContentDescription(quickAddLabel).performClick()

        assertEquals(listOf("1" to "65 g"), added)
        assertNull("a quick add must not also open the product", opened)
    }

    /** Only the button adds. The rest of the card — here its carb figure — still opens the product. */
    @Test
    fun theRestOfAQuickAddCardStillOpensTheProduct() {
        var adds = 0
        var opened: String? = null
        show(
            recents = listOf(RecentEntry(product(), null)),
            onQuickAdd = { _, _ -> adds++ },
            onOpenProduct = { opened = it },
        )

        compose.onNodeWithText(stringOf(R.string.recent_carbs_label), useUnmergedTree = true).performClick()
        assertEquals("1", opened)
        opened = null
        compose.onNodeWithText("Hagelslag puur", useUnmergedTree = true).performClick()
        assertEquals("1", opened)
        assertEquals(0, adds)
    }

    /**
     * The second tap of a double tap lands while the card is confirming. It must be swallowed by the
     * button — neither re-adding nor falling through to the card, which would navigate off Home.
     */
    @Test
    fun aTapWhileConfirmingIsConsumedAndNeitherAddsNorOpens() {
        var adds = 0
        var opened: String? = null
        show(
            recents = listOf(RecentEntry(product(), null)),
            status = mapOf("1" to QuickAddStatus.ADDED),
            onQuickAdd = { _, _ -> adds++ },
            onOpenProduct = { opened = it },
        )

        compose.onNodeWithTag(HOME_QUICK_ADD_TAG, useUnmergedTree = true).performClick()

        assertEquals(0, adds)
        assertNull(opened)
        compose.onNodeWithContentDescription(quickAddLabel)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, stringOf(R.string.recent_quick_added_state)))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
    }

    /**
     * The button is drawn ~30dp tall; its hit area must still be the 48dp minimum. Measured by tapping
     * *above the drawn button*, on the product-name side, where a miss would open the product instead.
     */
    @Test
    fun theHitTargetExtendsBeyondTheDrawnButton() {
        var adds = 0
        var opened: String? = null
        show(
            recents = listOf(RecentEntry(product(), null)),
            onQuickAdd = { _, _ -> adds++ },
            onOpenProduct = { opened = it },
        )

        val node = compose.onNodeWithTag(HOME_QUICK_ADD_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val density = compose.density
        val touchHeight = with(density) { node.touchBoundsInRoot.height.toDp() }
        val drawnHeight = with(density) { node.boundsInRoot.height.toDp() }
        assertTrue("drawn button is compact, was $drawnHeight", drawnHeight < 40.dp)
        assertTrue("touch target is at least 48dp, was $touchHeight", touchHeight >= 48.dp)

        val outsideAbove = with(density) { 5.dp.toPx() }
        compose.onNodeWithTag(HOME_QUICK_ADD_TAG, useUnmergedTree = true).performTouchInput {
            click(Offset(centerX, -outsideAbove))
        }

        assertEquals(1, adds)
        assertNull(opened)
    }

    @Test
    fun theFavouriteStarStillTogglesOnAQuickAddCard() {
        val toggled = mutableListOf<String>()
        var adds = 0
        show(
            recents = listOf(RecentEntry(product(), null)),
            onToggleFavorite = { toggled += it.barcode },
            onQuickAdd = { _, _ -> adds++ },
        )

        compose.onNodeWithContentDescription(stringOf(R.string.favorite_add)).performClick()

        assertEquals(listOf("1"), toggled)
        assertEquals(0, adds)
    }

    @Test
    fun longPressStillOpensRemoveFromRecentOnAQuickAddCard() {
        show(recents = listOf(RecentEntry(product(), null)))

        compose.onNodeWithText("Hagelslag puur", useUnmergedTree = true).performTouchInput { longClick() }

        compose.onNodeWithTag(HOME_RECENT_FORGET_TAG).assertIsDisplayed()
    }

    /**
     * 320dp at 1.8x text: the narrowest column this card ever gets. The portion must stay readable
     * (the button wraps below it rather than squeezing it to an ellipsis) and the button must stay
     * fully inside the card.
     */
    @Test
    fun largeFontOnANarrowScreenKeepsThePortionAndTheButton() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        show(
            recents = listOf(RecentEntry(product(name = "Griekse yoghurt met honing en walnoten, 0% vet"), null)),
            density = Density(density = base, fontScale = 1.8f),
            widthDp = 320,
        )

        // At 1.8x the recents start below the fold (the entry points come first by design), so
        // scroll the card in before judging its layout.
        val body = compose.onNodeWithTag(HOME_BODY_TAG)
        body.performScrollToNode(hasTestTag(HOME_QUICK_ADD_TAG))
        val button = compose.onNodeWithTag(HOME_QUICK_ADD_TAG, useUnmergedTree = true)
        compose.onNodeWithText("65 g").assertIsDisplayed()
        button.assertIsDisplayed()
        val bodyBounds = body.fetchSemanticsNode().boundsInRoot
        val buttonBounds = button.fetchSemanticsNode().boundsInRoot
        val portionBounds = compose.onNodeWithText("65 g").fetchSemanticsNode().boundsInRoot
        assertTrue("portion is not squeezed: ${portionBounds.width}", portionBounds.width > 0f)
        assertTrue(
            "button fits inside the constrained body: button=$buttonBounds, body=$bodyBounds",
            buttonBounds.left >= bodyBounds.left && buttonBounds.right <= bodyBounds.right,
        )
    }

    // ---- end to end, real storage ------------------------------------------------------------

    private fun realViewModel(vararg products: Product): Pair<HomeViewModel, JustTheCarbsDatabase> {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            JustTheCarbsDatabase::class.java,
        ).build()
        database = db
        val repository = ProductRepository(
            local = RoomProductDataSource(db.productDao()),
            remote = object : ProductDataSource {
                override suspend fun fetch(barcode: String): ProductFetchResult = ProductFetchResult.NotFound
            },
            portionUnits = RoomPortionUnitDataSource(db.portionUnitDao()),
            meal = RoomMealDataSource(db.mealItemDao()),
            portionUsage = RoomPortionUsageDataSource(db.portionUsageDao()),
            searchSource = object : ProductSearchSource {
                override suspend fun search(terms: String) = ProductSearchResult.NoMatches
            },
        )
        runBlocking { products.forEach { RoomProductDataSource(db.productDao()).save(it) } }
        return HomeViewModel(repository) to db
    }

    private fun showReal(vm: HomeViewModel) {
        compose.setContent {
            JustTheCarbsTheme {
                val recents by vm.recents.collectAsStateWithLifecycle()
                val meal by vm.mealItems.collectAsStateWithLifecycle()
                val status by vm.quickAdd.collectAsStateWithLifecycle()
                HomeScreen(
                    recents = recents,
                    settings = AppSettings(),
                    onScan = {},
                    onManualEntry = {},
                    onOpenProduct = {},
                    onToggleFavorite = vm::toggleFavorite,
                    onOpenSettings = {},
                    quickAddStatus = status,
                    onQuickAdd = vm::quickAdd,
                    quickAddEvents = vm.quickAddEvents,
                    mealItems = meal,
                    mealTotal = if (meal.isEmpty()) null else MealTotal.asResult(meal),
                )
            }
        }
    }

    @Test
    fun quickAddWritesTheMealLineAndTheMealBarAppearsOnHome() {
        val (vm, db) = realViewModel(product())
        showReal(vm)
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(HOME_QUICK_ADD_TAG), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(hasTestTag(MEAL_BAR_TAG)).assertCountEquals(0)

        compose.onNodeWithContentDescription(quickAddLabel).performClick()

        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(MEAL_BAR_TAG)).fetchSemanticsNodes().isNotEmpty() }
        val items = runBlocking { RoomMealDataSource(db.mealItemDao()).findItems() }
        val item = items.single()
        assertEquals("65 g", item.portionDescription)
        assertEquals("Hagelslag puur", item.displayName)
        assertEquals(
            0,
            CarbCalculator.calculate(BigDecimal("48.2"), BigDecimal("65"), NutritionBasis.PER_100_G).exact
                .compareTo(item.exactCarbs),
        )
    }

    /** A real double tap, against real storage: exactly one meal line. */
    @Test
    fun aRapidDoubleTapWritesExactlyOneMealLine() {
        val (vm, db) = realViewModel(product())
        showReal(vm)
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(HOME_QUICK_ADD_TAG), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag(HOME_QUICK_ADD_TAG, useUnmergedTree = true).performTouchInput {
            click()
            advanceEventTime(120)
            click()
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(MEAL_BAR_TAG)).fetchSemanticsNodes().isNotEmpty() }
        // The ViewModel runs on the real main looper, not Compose's test clock. Give a second write,
        // had the guard let one start, real time to land before counting.
        Thread.sleep(500)
        compose.waitForIdle()

        val items = runBlocking { RoomMealDataSource(db.mealItemDao()).findItems() }
        assertEquals(1, items.size)
    }
}

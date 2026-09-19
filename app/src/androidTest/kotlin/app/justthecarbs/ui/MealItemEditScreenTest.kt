package app.justthecarbs.ui

import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.justthecarbs.data.ProductRepository
import app.justthecarbs.data.local.JustTheCarbsDatabase
import app.justthecarbs.data.local.RoomMealDataSource
import app.justthecarbs.data.local.RoomPortionUnitDataSource
import app.justthecarbs.data.local.RoomPortionUsageDataSource
import app.justthecarbs.data.local.RoomProductDataSource
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.ProductDataSource
import app.justthecarbs.domain.ProductFetchResult
import app.justthecarbs.domain.ProductSearchResult
import app.justthecarbs.domain.ProductSearchSource
import app.justthecarbs.ui.meal.MEAL_EDITOR_CANCEL_TAG
import app.justthecarbs.ui.meal.MEAL_EDITOR_FIELD_TAG
import app.justthecarbs.ui.meal.MEAL_EDITOR_RESULT_TAG
import app.justthecarbs.ui.meal.MEAL_EDITOR_SAVE_TAG
import app.justthecarbs.ui.meal.MEAL_EDITOR_TAG
import app.justthecarbs.ui.meal.MEAL_TOTAL_TAG
import app.justthecarbs.ui.meal.MealScreen
import app.justthecarbs.ui.meal.MealViewModel
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Correcting a meal line on the meal screen (1.0.8), driven the way a user drives it: tap the line,
 * change the amount, read the carbs, Save.
 *
 * The screen runs on the real [MealViewModel] over an in-memory Room database, so "the line is
 * replaced in place" is checked against the storage the app actually uses, not a fixture.
 *
 * **Instrumented: needs a device or emulator.**
 */
class MealItemEditScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var database: JustTheCarbsDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    private val addedAt = Instant.parse("2026-09-18T12:00:00Z")

    private fun bread() = MealItem.weightBased(
        productBarcode = "111",
        displayName = "Wholegrain Bread",
        portionDescription = "2 slices",
        resolvedAmount = BigDecimal("70"),
        basis = NutritionBasis.PER_100_G,
        carbsPer100 = BigDecimal("48.2"),
        exactCarbs = BigDecimal("33.740"),
        addedAt = addedAt,
    )

    private fun juice() = MealItem.weightBased(
        productBarcode = "222",
        displayName = "Orange Juice",
        portionDescription = "200 ml",
        resolvedAmount = BigDecimal("200"),
        basis = NutritionBasis.PER_100_ML,
        carbsPer100 = BigDecimal("9.4"),
        exactCarbs = BigDecimal("18.800"),
        addedAt = addedAt.plusSeconds(60),
    )

    private fun crispbread() = MealItem.directCarbs(
        productBarcode = "333",
        displayName = "Crispbread",
        portionDescription = "4 slices",
        count = BigDecimal("4"),
        carbsPerUnit = BigDecimal("14.2"),
        exactCarbs = BigDecimal("56.8"),
        addedAt = addedAt.plusSeconds(120),
    )

    // ---- rig ---------------------------------------------------------------------------------

    private lateinit var meal: RoomMealDataSource

    private fun show(vararg items: MealItem) {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            JustTheCarbsDatabase::class.java,
        ).build()
        database = db
        meal = RoomMealDataSource(db.mealItemDao())
        val repository = ProductRepository(
            local = RoomProductDataSource(db.productDao()),
            remote = object : ProductDataSource {
                override suspend fun fetch(barcode: String): ProductFetchResult =
                    error("a meal-line correction must never look a product up")
            },
            portionUnits = RoomPortionUnitDataSource(db.portionUnitDao()),
            meal = meal,
            portionUsage = RoomPortionUsageDataSource(db.portionUsageDao()),
            searchSource = object : ProductSearchSource {
                override suspend fun search(terms: String) = ProductSearchResult.NoMatches
            },
        )
        runBlocking { items.forEach { meal.add(it) } }
        val vm = MealViewModel(repository)

        compose.setContent {
            JustTheCarbsTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                MealScreen(
                    state = state,
                    settings = AppSettings(),
                    onBack = {},
                    onRemoveItem = vm::removeItem,
                    onClear = vm::clear,
                    onShowClearConfirmation = vm::showClearConfirmation,
                    onUndoRemove = vm::undoRemove,
                    onUndoExpired = vm::clearUndo,
                    onEditItem = vm::editItem,
                    onEditAmountChange = vm::onEditAmountChange,
                    onAdjustEditAmount = vm::adjustEditAmount,
                    onSaveEdit = vm::saveEdit,
                    onCloseEditor = vm::closeEditor,
                )
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(items.last().displayName)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun stored(): List<MealItem> = runBlocking { meal.findItems() }

    private val inEditor = hasAnyAncestor(hasTestTag(MEAL_EDITOR_TAG))

    /** The meal line itself — its clickable row, never the editor's copy of the name. */
    private fun line(name: String): SemanticsNodeInteraction =
        compose.onNode(hasText(name) and hasClickAction() and !inEditor)

    private fun openEditorOn(name: String) {
        line(name).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(MEAL_EDITOR_TAG)).fetchSemanticsNodes().isNotEmpty()
        }
        // Wait for the sheet to stop moving, not merely to exist.
        //
        // A `ModalBottomSheet` animates up *and* re-measures as it settles — logged on this
        // emulator as 596 → 635 → 620 → 519 → 467 → 437dp across consecutive frames — and
        // `waitForIdle()` returns during that sequence. A node read then has bounds from a frame
        // the sheet has already left, which made assertions on the scrolling part of the sheet
        // fail on roughly one run in three: intermittent, and nothing to do with the soft
        // keyboard. Two consecutive frames reporting the same height is the settled state.
        var previous = -1
        compose.waitUntil(5_000) {
            val height = compose.onNodeWithTag(MEAL_EDITOR_TAG).fetchSemanticsNode().size.height
            val settled = height == previous
            previous = height
            settled
        }
        compose.waitForIdle()
    }

    private fun waitForEditorToClose() {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(MEAL_EDITOR_TAG)).fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
    }

    private fun field() = compose.onNodeWithTag(MEAL_EDITOR_FIELD_TAG)
    private fun save() = compose.onNodeWithTag(MEAL_EDITOR_SAVE_TAG)

    // ---- the quick-adjust rail in the sheet (1.0.8) ---------------------------------------------

    @Test
    fun theSheetKeepsItsOwnContentRatherThanTheAcceleratorRail() {
        // The measured outcome of reusing the calculator's rail here: on an ordinary phone window
        // this sheet does not show it, because its pinned content (result slot, amount field,
        // action row) already costs about 380dp of the ~437dp a content-sized bottom sheet gets.
        // Rendering the rail anyway clipped the product name and the "Currently …" line
        // intermittently — see RAIL_MIN_WINDOW_HEIGHT for the two placements that were tried.
        //
        // This asserts the *contract*, not the absence: whatever the window, the things that make
        // the sheet usable are the ones that survive. A future larger-window device showing the
        // rail is fine; a device that shows the rail and loses the line being corrected is not.
        show(bread())
        openEditorOn("Wholegrain Bread")

        compose.onNode(hasText("Wholegrain Bread") and inEditor).assertIsDisplayed()
        compose.onNode(hasText("Currently 2 slices · 33.7 g") and inEditor).assertIsDisplayed()
        field().assertIsDisplayed()
        compose.onNodeWithTag(MEAL_EDITOR_RESULT_TAG).assertIsDisplayed()
        compose.onNodeWithTag(MEAL_EDITOR_SAVE_TAG).assertIsDisplayed()
    }

    // The ViewModel side of the rail — `adjustEditAmount` applying PortionAdjustment to the
    // amount being corrected — is covered in the JVM suite (MealItemEditTest), which can call it
    // directly. Repeating it here through a composable that does not render the rail would assert
    // the same thing twice and pin nothing extra.

    /**
     * A review capture of the editor sheet with its rail, for human inspection.
     *
     * Written only with `-e editorScreenshot true`, like the other visual harnesses in this suite —
     * whether the rail reads as subordinate to the figure inside a sheet is a judgement made by
     * looking, not one an assertion can encode.
     */
    @Test
    fun captureTheEditorForReview() {
        if (InstrumentationRegistry.getArguments().getString("editorScreenshot") != "true") return
        show(bread())
        openEditorOn("Wholegrain Bread")

        val bitmap = compose.onNodeWithTag(MEAL_EDITOR_TAG).captureToImage().asAndroidBitmap()
        val dir = java.io.File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "meal-editor",
        ).apply { mkdirs() }
        java.io.File(dir, "editor-with-rail.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    // ---- opening -----------------------------------------------------------------------------

    @Test
    fun tappingALineOpensItsEditorAtTheAmountTheLineHolds() {
        show(bread())

        openEditorOn("Wholegrain Bread")

        compose.onNode(hasText("Wholegrain Bread") and inEditor).assertIsDisplayed()
        // The line as it stands, word for word, beside the grams the editor actually changes.
        // Asserted as *displayed*, with no scroll: it is pinned rather than scrolling, which is
        // what stops it disappearing behind the accelerator rail on a short sheet.
        compose.onNode(hasText("Currently 2 slices · 33.7 g") and inEditor).assertIsDisplayed()
        field().assert(hasText("70"))
        compose.onNode(hasText("48.2 g carbs / 100 g") and inEditor).assertIsDisplayed()
        save().assertIsNotEnabled()
    }

    @Test
    fun theFirstKeystrokeReplacesTheAmountRatherThanAppendingToIt() {
        show(bread())
        openEditorOn("Wholegrain Bread")

        field().performTextInput("85")

        field().assert(hasText("85"))
    }

    // ---- live result -------------------------------------------------------------------------

    @Test
    fun typingANewAmountShowsItsCarbsLive() {
        show(bread())
        openEditorOn("Wholegrain Bread")

        field().performTextReplacement("85")

        // 48.2 × 85 / 100 = 40.97, shown as 41.0.
        compose.onNodeWithTag(MEAL_EDITOR_RESULT_TAG).assertContentDescriptionEquals("41.0 grams")
        save().assertIsEnabled()
    }

    // ---- saving ------------------------------------------------------------------------------

    @Test
    fun savingCorrectsTheLineInPlaceAndTheTotalWithIt() {
        show(bread(), juice())
        val before = stored()
        openEditorOn("Wholegrain Bread")

        field().performTextReplacement("85")
        save().performClick()
        waitForEditorToClose()

        line("Wholegrain Bread").assert(hasText("85 g · 41.0 g"))
        // 40.97 + 18.8 = 59.77
        compose.onNodeWithTag(MEAL_TOTAL_TAG).assertContentDescriptionEquals("59.8 grams")

        val after = stored()
        assertEquals("still two lines, in the same order", before.map { it.id }, after.map { it.id })
        val corrected = after.first()
        assertEquals(before.first().addedAt, corrected.addedAt)
        assertEquals("111", corrected.productBarcode)
        assertEquals(0, BigDecimal("40.97").compareTo(corrected.exactCarbs))
        assertEquals(before[1], after[1])
    }

    @Test
    fun theKeyboardsDoneKeySavesACorrection() {
        show(bread())
        openEditorOn("Wholegrain Bread")

        field().performTextReplacement("85")
        field().performImeAction()
        waitForEditorToClose()

        line("Wholegrain Bread").assert(hasText("85 g · 41.0 g"))
    }

    @Test
    fun aMillilitreLineIsCorrectedInMillilitres() {
        show(juice())
        openEditorOn("Orange Juice")
        compose.onNode(hasText("ml") and inEditor).assertIsDisplayed()

        field().performTextReplacement("250")
        save().performClick()
        waitForEditorToClose()

        // 9.4 × 250 / 100 = 23.5
        line("Orange Juice").assert(hasText("250 ml · 23.5 g"))
        assertEquals(NutritionBasis.PER_100_ML, stored().single().basis)
    }

    @Test
    fun aDirectCarbLineCorrectsItsCountAndNeverGainsAWeight() {
        show(crispbread())
        openEditorOn("Crispbread")

        field().assert(hasText("4"))
        compose.onNode(hasText("14.2 g carbs each") and inEditor).assertIsDisplayed()

        field().performTextReplacement("3")
        compose.onNodeWithTag(MEAL_EDITOR_RESULT_TAG).assertContentDescriptionEquals("42.6 grams")
        save().performClick()
        waitForEditorToClose()

        line("Crispbread").assert(hasText("3 × 14.2 g carbs · 42.6 g"))
        val saved = stored().single()
        assertEquals(BigDecimal("3"), saved.count)
        assertNull(saved.resolvedAmount)
    }

    // ---- what never saves --------------------------------------------------------------------

    @Test
    fun cancelLeavesTheLineExactlyAsItWas() {
        show(bread())
        val before = stored()
        openEditorOn("Wholegrain Bread")

        field().performTextReplacement("85")
        compose.onNodeWithTag(MEAL_EDITOR_CANCEL_TAG).performClick()
        waitForEditorToClose()

        line("Wholegrain Bread").assert(hasText("2 slices · 33.7 g"))
        assertEquals(before, stored())
    }

    @Test
    fun saveStaysOffForAnUnchangedEmptyZeroOrMalformedAmount() {
        show(bread())
        openEditorOn("Wholegrain Bread")

        save().assertIsNotEnabled()

        field().performTextReplacement("70.0")
        save().assertIsNotEnabled()

        field().performTextReplacement("")
        save().assertIsNotEnabled()
        compose.onNode(hasText("Enter a portion") and inEditor).assertIsDisplayed()

        field().performTextReplacement("0")
        save().assertIsNotEnabled()
        compose.onNode(hasText("Enter a number above 0") and inEditor).assertIsDisplayed()

        field().performTextReplacement("1.2.3")
        save().assertIsNotEnabled()
        compose.onNode(hasText("Enter a number above 0") and inEditor).assertIsDisplayed()

        // Done on an unusable amount must not save either.
        field().performImeAction()
        compose.waitForIdle()
        assertEquals(BigDecimal("70"), stored().single().resolvedAmount)
    }

    // ---- the other meal actions --------------------------------------------------------------

    @Test
    fun removeStillRemovesWithoutOpeningTheEditorAndUndoStillRestores() {
        show(bread())

        compose.onNodeWithContentDescription("Remove Wholegrain Bread").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Removed Wholegrain Bread")).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag(MEAL_EDITOR_TAG).assertDoesNotExist()
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("2 slices · 33.7 g")).fetchSemanticsNodes().isNotEmpty()
        }

        line("Wholegrain Bread").assert(hasText("2 slices · 33.7 g"))
    }

    // ---- accessibility -----------------------------------------------------------------------

    @Test
    fun aLineAnnouncesItsAmountAndCarbsAndOffersEditAsItsAction() {
        show(bread())

        line("Wholegrain Bread")
            .assertContentDescriptionEquals("Wholegrain Bread, 2 slices, 33.7 grams carbs")
            .assert(
                SemanticsMatcher("its tap is announced as Edit meal item") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == "Edit meal item"
                },
            )
    }

    @Test
    fun theEditorsFieldIsNamedWithWhatItHolds() {
        show(bread(), crispbread())

        openEditorOn("Wholegrain Bread")
        field().assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Portion in g")))
        compose.onNodeWithTag(MEAL_EDITOR_CANCEL_TAG).performClick()
        waitForEditorToClose()

        openEditorOn("Crispbread")
        field().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Count, 14.2 g carbs each")),
        )
    }

    @Test
    fun theLineAndTheEditorsActionsMeetTheTouchTargetFloor() {
        show(bread())
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        fun heightDp(node: SemanticsNodeInteraction) = node.fetchSemanticsNode().boundsInRoot.height / density

        val lineHeight = heightDp(line("Wholegrain Bread"))
        assert(lineHeight >= 48f) { "the line is $lineHeight dp tall" }

        openEditorOn("Wholegrain Bread")
        val saveHeight = heightDp(save())
        val cancelHeight = heightDp(compose.onNodeWithTag(MEAL_EDITOR_CANCEL_TAG))
        assert(saveHeight >= 48f && cancelHeight >= 48f) { "Save $saveHeight dp, Cancel $cancelHeight dp" }
    }
}

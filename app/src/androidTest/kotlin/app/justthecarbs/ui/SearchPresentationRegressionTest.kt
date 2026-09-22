package app.justthecarbs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.justthecarbs.domain.AppSettings
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.ProductSearchHit
import app.justthecarbs.domain.ProductSearchResult
import app.justthecarbs.domain.ProductSearchSource
import app.justthecarbs.ui.home.HOME_SEARCH_FIELD_TAG
import app.justthecarbs.ui.home.HOME_SEARCH_SUBMIT_TAG
import app.justthecarbs.ui.home.HomeScreen
import app.justthecarbs.ui.search.SEARCH_FIELD_TAG
import app.justthecarbs.ui.search.SEARCH_STATE_REGION_TAG
import app.justthecarbs.ui.search.SEARCH_SUBMIT_TAG
import app.justthecarbs.ui.search.SearchScreen
import app.justthecarbs.ui.search.SearchUiState
import app.justthecarbs.ui.search.SearchViewModel
import app.justthecarbs.ui.theme.JustTheCarbsTheme
import java.math.BigDecimal
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Regression cover for the search **presentation** defects found by device review on 2026-09-16.
 *
 * Every case here exists because the behavioural suite could not see the defect: `assertIsDisplayed`
 * is equally happy with a node stranded at the bottom of a 2400px screen and one placed where it can
 * be read, and equally happy with a sentence rendered in full and one ellipsized to `No carbohy…`.
 *
 * So these assert **geometry** and **text layout**, not existence:
 *
 * - **S1** — informational states must sit near the top of the region they occupy, not be centred in
 *   a `weight(1f)` viewport. Measured against the region's *own* bounds rather than absolute pixels,
 *   so the assertion means the same thing on any screen size.
 * - **S2** — the "no carbohydrate value" sentence must not visually overflow, at 1x and at 1.8x.
 * - **S3** — a result's spoken description must carry every field its card shows.
 * - **M4** — Home must render the too-short refusal an explicit submission produces.
 *
 * **Instrumented: needs a device or emulator.**
 */
class SearchPresentationRegressionTest {

    @get:Rule
    val compose = createComposeRule()

    private fun hit(
        barcode: String = "8710496979125",
        name: String = "Chocoladehagel puur",
        brand: String? = "De Ruijter",
        quantity: String? = "390 gram",
        carbs: String? = "67",
    ) = ProductSearchHit(
        barcode = barcode,
        name = name,
        brand = brand,
        packageQuantity = quantity,
        carbsPer100 = carbs?.let(::BigDecimal),
        basis = NutritionBasis.PER_100_G,
        imageUrl = null,
    )

    private fun showSearch(
        state: SearchUiState,
        fontScale: Float = 1f,
        width: Dp = 411.dp,
    ) {
        compose.setContent {
            ImeProbe()
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                JustTheCarbsTheme {
                    Box(Modifier.width(width)) {
                        SearchScreen(
                            state = state,
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
            }
        }
    }

    private fun showHome(
        searchState: SearchUiState,
        onSearchSubmit: () -> Unit = {},
        onSearchQueryChanged: (String) -> Unit = {},
        fontScale: Float? = null,
    ) {
        compose.setContent {
            ImeProbe()
            val content = @androidx.compose.runtime.Composable {
                JustTheCarbsTheme {
                    HomeScreen(
                        recents = emptyList(),
                        settings = AppSettings(),
                        onScan = {},
                        onManualEntry = {},
                        onOpenProduct = {},
                        onToggleFavorite = {},
                        onOpenSettings = {},
                        onScanLabel = {},
                        searchState = searchState,
                        onSearchSubmit = onSearchSubmit,
                        onSearchQueryChanged = onSearchQueryChanged,
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

    // ---- S1: informational states are placed, not centred -------------------------------------

    /**
     * The defect this exists for: the prompt was `contentAlignment = Center` inside `weight(1f)`,
     * which on a 1080x2400 device put it at y=1403 with ~1100px of empty page above it and the
     * sentence itself pinned against the keyboard.
     *
     * Asserted as a fraction of the state region's **own** height, so it survives any screen size
     * and cannot be satisfied by a coordinate that merely happens to be small. A third is generous:
     * it fails the old centred layout (which lands at ~0.5 by construction) without pinning the
     * exact padding, which is a design choice rather than a contract.
     */
    private fun assertSitsNearTheTopOfTheStateRegion(text: String) {
        val region = compose.onNodeWithTag(SEARCH_STATE_REGION_TAG).fetchSemanticsNode().boundsInRoot
        val node = compose.onNodeWithText(text, substring = true).fetchSemanticsNode().boundsInRoot

        val offsetIntoRegion = node.top - region.top
        val regionHeight = region.height
        assertTrue(
            "'$text' should sit near the top of the state region, not be centred in it. " +
                "Region=$region node=$node offsetIntoRegion=$offsetIntoRegion " +
                "(${"%.2f".format(offsetIntoRegion / regionHeight)} of ${regionHeight}px)",
            offsetIntoRegion < regionHeight / 3f,
        )
    }

    @Test
    fun theSearchPromptIsPlacedNearTheTopRatherThanCentredInTheViewport() {
        showSearch(SearchUiState())

        compose.onNodeWithText("Type a product name to search Open Food Facts.").assertIsDisplayed()
        assertSitsNearTheTopOfTheStateRegion("Type a product name")
    }

    @Test
    fun theTooShortRefusalIsPlacedNearTheTopRatherThanCentredInTheViewport() {
        showSearch(SearchUiState(query = "ha", queryTooShort = true))

        assertSitsNearTheTopOfTheStateRegion("Type at least 3 characters")
    }

    @Test
    fun theWaitingNoticeIsPlacedNearTheTopRatherThanCentredInTheViewport() {
        showSearch(
            SearchUiState(query = "hag", searching = true, awaitingRemotePermit = true),
        )

        assertSitsNearTheTopOfTheStateRegion("Updating")
    }

    /**
     * Home's own copy of the same region. Home is the IME-sensitive case from the review — the
     * prompt was measured at y=1436 with the keyboard covering everything below it — and Home has
     * its own layout, so a fix to SearchScreen alone would leave this defect standing.
     */
    @Test
    fun homesSearchPromptIsPlacedNearTheTopRatherThanAgainstTheKeyboard() {
        showHome(SearchUiState(query = "ha"))

        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val node = compose.onNodeWithText("Type a product name", substring = true)
            .fetchSemanticsNode().boundsInRoot

        // Home has no single state-region tag (its results and its body share the weighted slot), so
        // this is asserted against the window: the prompt must sit in the upper half, which is what
        // keeps it clear of an open keyboard. The old centred layout put it at ~0.6 of the window.
        assertTrue(
            "Home's search prompt should sit in the upper half of the window so an open keyboard " +
                "cannot strand it. root=$root node=$node",
            node.top < root.height / 2f,
        )
    }

    // ---- S2: the unavailable-carb sentence is readable ----------------------------------------

    /**
     * Asks the Text itself whether it overflowed rather than comparing bounds.
     *
     * A constrained `Text` reports its already-constrained size, so it can never disagree with
     * itself even when it is visibly cut off — the same trap `ResultValueTest` and `ProductScreenTest`
     * both document. `hasVisualOverflow` is the only reliable signal.
     */
    private fun assertIsFullyReadable(text: String) {
        // `useUnmergedTree` is load-bearing. Without it the matcher resolves to the row's MERGED
        // node, whose GetTextLayoutResult reports the *first* Text it contains (the product name) —
        // so the assertion would measure a string that was never clipped and pass whatever the
        // state of the one under test. Caught by watching the 1.8x case pass against known-broken
        // code.
        val node = compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstOrNull { it.config.contains(SemanticsActions.GetTextLayoutResult) }
            ?: throw AssertionError("No text node found containing '$text'")

        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        val laidOut = layout.layoutInput.text.text

        // Assert on the string this node actually holds, so a node resolving to the wrong Text
        // cannot satisfy the check merely by being short.
        assertTrue(
            "Measured the wrong node: expected one containing '$text', got '$laidOut'",
            laidOut.contains(text),
        )

        // The defect is characters being *dropped* — "No carbohydrate value — check the package"
        // rendering as "No carbohy…". So the question asked is whether every character of the last
        // line was laid out, which is what ellipsis destroys.
        //
        // Deliberately NOT `hasVisualOverflow`: that flag also trips on sub-pixel rounding, where a
        // line measuring 648.5px in a 649px box reports overflow while rendering perfectly. Measured
        // on device — the fixed layout reports `649x42px, longest line 648.5px, lines=1`, which is a
        // single complete line, not a clipped one.
        val lastLineEnd = layout.getLineEnd(layout.lineCount - 1, visibleEnd = true)
        assertTrue(
            "'$laidOut' is truncated: only $lastLineEnd of ${laidOut.length} characters were laid " +
                "out across ${layout.lineCount} line(s), box ${layout.size.width}x" +
                "${layout.size.height}px, longest line ${layout.multiParagraph.maxIntrinsicWidth}px",
            lastLineEnd >= laidOut.length,
        )
    }

    @Test
    fun theNoCarbValueMessageIsNotClippedAtTheDefaultFontScale() {
        showSearch(SearchUiState(query = "hag", hits = listOf(hit(carbs = null))))

        assertIsFullyReadable("No carbohydrate value")
    }

    /**
     * 1.8x is where the review measured the sentence collapsing to `No carbohy…` — a string that
     * says nothing while still occupying a third of the row.
     */
    @Test
    fun theNoCarbValueMessageIsNotClippedAtALargeFontScale() {
        showSearch(
            SearchUiState(query = "hag", hits = listOf(hit(carbs = null))),
            fontScale = 1.8f,
        )

        assertIsFullyReadable("No carbohydrate value")
    }

    /** A row that *has* a value keeps its compact scannable column — the fix must not cost that. */
    @Test
    fun aRowWithACarbValueStillRendersTheValueAndItsBasis() {
        showSearch(SearchUiState(query = "hag", hits = listOf(hit(carbs = "67.4"))))

        compose.onNodeWithText("67.4", substring = true).assertIsDisplayed()
        compose.onNodeWithText("/ 100 g", substring = true).assertIsDisplayed()
        assertIsFullyReadable("67.4")
    }

    // ---- S3: the spoken description carries every visible field -------------------------------

    /**
     * The row's own merged node carries the spoken line, so each expected fragment is asserted
     * against it by substring. The package quantity is the one that regressed: the card shows
     * "De Ruijter · 390 gram" while the description said only "De Ruijter".
     */
    private fun assertRowSpeaks(vararg fragments: String) {
        fragments.forEach { fragment ->
            compose.onNodeWithContentDescription(fragment, substring = true).assertExists()
        }
    }

    @Test
    fun aResultSpeaksItsNameBrandPackageQuantityAndCarbFigure() {
        showSearch(SearchUiState(query = "hag", hits = listOf(hit())))

        assertRowSpeaks("Chocoladehagel puur", "De Ruijter", "390 gram", "67 g carbs / 100 g")
    }

    @Test
    fun aResultWithoutACarbValueSpeaksThatStateRatherThanOmittingIt() {
        showSearch(SearchUiState(query = "hag", hits = listOf(hit(carbs = null))))

        assertRowSpeaks(
            "Chocoladehagel puur",
            "De Ruijter",
            "390 gram",
            "No carbohydrate value",
        )
    }

    /** A record with no brand and no printed quantity must not speak empty fragments. */
    @Test
    fun aResultWithNoBrandOrQuantitySpeaksNeitherAsAnEmptyFragment() {
        showSearch(
            SearchUiState(query = "hag", hits = listOf(hit(brand = null, quantity = null))),
        )

        assertRowSpeaks("Chocoladehagel puur", "67 g carbs / 100 g")
        // The old format interpolated an absent brand directly, producing a doubled separator.
        compose.onNodeWithContentDescription(". .", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("null", substring = true).assertDoesNotExist()
    }

    // ---- M4: Home reports a refused submission -------------------------------------------------

    private class ImmediateSource : ProductSearchSource {
        val queries = mutableListOf<String>()
        override suspend fun search(terms: String): ProductSearchResult {
            queries += terms
            return ProductSearchResult.NoMatches
        }
    }

    /**
     * Home wired to a real [SearchViewModel], because the divergence is between what the ViewModel
     * produces and what Home renders — a hand-built state would assert the renderer against my own
     * assumption rather than against the state the app actually reaches.
     */
    private fun showLiveHome(source: ProductSearchSource): SearchViewModel {
        val viewModel = SearchViewModel(source)
        compose.setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            JustTheCarbsTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        recents = emptyList(),
                        settings = AppSettings(),
                        onScan = {},
                        onManualEntry = {},
                        onOpenProduct = {},
                        onToggleFavorite = {},
                        onOpenSettings = {},
                        onScanLabel = {},
                        searchState = state,
                        onSearchSubmit = viewModel::search,
                        onSearchQueryChanged = viewModel::onQueryChanged,
                    )
                }
            }
        }
        return viewModel
    }

    @Test
    fun typingATooShortQueryOnHomeShowsThePromptRatherThanTheRefusal() {
        val source = ImmediateSource()
        showLiveHome(source)

        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).performTextInput("ha")
        compose.waitForIdle()

        compose.onNodeWithText("Type at least 3 characters to search.").assertDoesNotExist()
        compose.onNodeWithText("Type a product name to search Open Food Facts.").assertIsDisplayed()
        assertTrue("Typing alone must not search", source.queries.isEmpty())
    }

    @Test
    fun submittingATooShortQueryOnHomeReportsTheRefusal() {
        val source = ImmediateSource()
        showLiveHome(source)

        compose.onNodeWithTag(HOME_SEARCH_FIELD_TAG).performTextInput("ha")
        compose.onNodeWithTag(HOME_SEARCH_SUBMIT_TAG).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Type at least 3 characters to search.").assertIsDisplayed()
        assertTrue("A refused submission must not reach the network", source.queries.isEmpty())
    }

    // ---- V1: recovery actions stay above an open keyboard -------------------------------------

    /**
     * The keyboard's height in px, read from inside the composition under test. Written from a
     * SideEffect so the test thread sees the value the layout actually used.
     */
    @Volatile
    private var imeBottomPx = 0

    @Composable
    private fun ImeProbe() {
        val bottom = WindowInsets.ime.getBottom(LocalDensity.current)
        SideEffect { imeBottomPx = bottom }
    }

    /**
     * Focuses [fieldTag] so the real soft keyboard opens, then asserts [text] ends above it.
     *
     * The defect this exists for, measured on device: the app is edge-to-edge (targetSdk 36), so
     * `adjustResize` no longer shrinks the window, and a `weight(1f)` region runs on underneath the
     * keyboard. A panel centred in that region put "Scan nutrition label" and "Enter manually"
     * (y=1494..1750 on a 1080x2400 screen) entirely behind a keyboard starting at y=1516.
     *
     * `assertIsDisplayed` cannot see this: the node is inside the window, only covered.
     */
    private fun assertStaysAboveTheKeyboard(fieldTag: String, text: String) {
        compose.onNodeWithTag(fieldTag).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitUntil(timeoutMillis = 5_000) { imeBottomPx > 0 }
        compose.waitForIdle()

        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val node = compose.onNodeWithText(text, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val keyboardTop = root.bottom - imeBottomPx
        assertTrue(
            "'$text' is hidden behind the keyboard: node=$node keyboardTop=$keyboardTop " +
                "(ime=${imeBottomPx}px, root=$root)",
            node.bottom <= keyboardTop,
        )
    }

    @Test
    fun homesNoMatchActionsStayAboveTheKeyboard() {
        showHome(SearchUiState(query = "zzqxvkw", noMatches = true))

        assertStaysAboveTheKeyboard(HOME_SEARCH_FIELD_TAG, "Enter manually")
    }

    @Test
    fun searchScreensNoMatchActionsStayAboveTheKeyboard() {
        showSearch(SearchUiState(query = "zzqxvkw", noMatches = true))

        assertStaysAboveTheKeyboard(SEARCH_FIELD_TAG, "Enter manually")
    }

    // ---- V2/V3: long subtitles and names at a large font scale --------------------------------

    /**
     * Measured on device at 1.8x: "Albert Heijn · 400 g" rendered as "Albert Heijn ·", dropping the
     * pack size — the one field that separates a 400 g pack from a 600 g one — and leaving a dangling
     * separator. A single-line subtitle word-wraps and the second line is simply never drawn.
     */
    @Test
    fun theSubtitleKeepsThePackSizeAtALargeFontScale() {
        showSearch(
            SearchUiState(
                query = "hag",
                hits = listOf(hit(name = "Puur Hagelslag", brand = "Fairtrade Original", quantity = "380g")),
            ),
            fontScale = 1.8f,
        )

        assertIsFullyReadable("Fairtrade Original · 380g")
    }

    /**
     * Measured on device at 1.8x: "Chocolade Hagelslag Puur" rendered as "Chocolade Hagelslag" with
     * no ellipsis — which is the name of a *different* product. A name may be cut, but the cut must
     * be visible.
     */
    @Test
    fun aNameTooLongForItsLinesEndsInAVisibleEllipsis() {
        val name = "Chocolade Hagelslag Puur met extra veel cacao en een lange naam"
        showSearch(
            SearchUiState(query = "hag", hits = listOf(hit(name = name, carbs = "65"))),
            fontScale = 1.8f,
        )

        val node = compose.onAllNodesWithText(name, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .first { it.config.contains(SemanticsActions.GetTextLayoutResult) }
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        val last = layout.lineCount - 1
        val complete = layout.getLineEnd(last, visibleEnd = true) >= name.length

        assertTrue(
            "Precondition: this name must not fit, or the test proves nothing " +
                "(lines=${layout.lineCount})",
            !complete,
        )
        assertTrue(
            "The name was cut to ${layout.lineCount} line(s) without an ellipsis, so the visible " +
                "words read as a complete (different) product name",
            layout.isLineEllipsized(last),
        )
    }

    // ---- V4: rows reflow instead of breaking words at the largest font scale -----------------

    private fun layoutOf(predicate: (String) -> Boolean): TextLayoutResult {
        val layouts = compose.onAllNodes(
            androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isRoot()),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
            .filter { it.config.contains(SemanticsActions.GetTextLayoutResult) }
            .map { node ->
                mutableListOf<TextLayoutResult>().also {
                    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it)
                }.single()
            }
        return layouts.firstOrNull { predicate(it.layoutInput.text.text) }
            ?: throw AssertionError(
                "No laid-out text matched; saw ${layouts.map { it.layoutInput.text.text }}",
            )
    }

    private val chocomel = hit(
        barcode = "1", name = "Chocomel", brand = "Chocomel", quantity = "1 l", carbs = "10.5",
    )

    /**
     * Measured at 2.0x on a 411dp screen: the value column ("10.5 g carbs") grew until the name
     * column could not hold one word, and "Chocomel" rendered as "Chocome" / "l".
     */
    @Test
    fun aOneWordNameIsNotBrokenMidWordAtTheLargestFontScale() {
        showSearch(SearchUiState(query = "choc", hits = listOf(chocomel)), fontScale = 2.0f)

        val name = layoutOf { it == "Chocomel" }
        assertTrue(
            "'Chocomel' was broken across ${name.lineCount} lines at 2.0x",
            name.lineCount == 1,
        )
    }

    /**
     * Measured at 2.0x: "Chocomel · 1 l" wrapped between "1" and "l", leaving a bare "1" at the end
     * of a line — a number with no unit, in an app about quantities.
     */
    @Test
    fun aPackSizeIsNeverSplitFromItsUnit() {
        // The width and scale the split was captured at; the break position depends on both.
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 1.5f),
            ) {
                JustTheCarbsTheme {
                    Box(Modifier.width(360.dp)) {
                        SearchScreen(
                            state = SearchUiState(query = "choc", hits = listOf(chocomel)),
                            onQueryChanged = {}, onSearchSubmit = {}, onSelect = {},
                            onScanLabel = {}, onEnterManually = {}, onRetry = {}, onBack = {},
                        )
                    }
                }
            }
        }

        val subtitle = layoutOf { it.contains("·") }
        val text = subtitle.layoutInput.text.text
        val number = text.lastIndexOf('1')
        assertTrue(
            "The pack size '1 l' was split across lines in '$text'",
            subtitle.getLineForOffset(number) == subtitle.getLineForOffset(text.length - 1),
        )
    }

    private fun boundsOfText(text: String) =
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** Control: at the default scale the figure keeps its scannable column beside the name. */
    @Test
    fun atTheDefaultScaleTheFigureSitsBesideTheName() {
        showSearch(
            SearchUiState(query = "choc", hits = listOf(chocomel)),
            fontScale = 1f,
            width = 411.dp,
        )

        val name = boundsOfText("Chocomel")
        val value = boundsOfText("10.5 g carbs")
        assertTrue("name=$name value=$value", value.left >= name.right)
    }

    @Test
    fun atTheLargestScaleTheFigureMovesBelowTheText() {
        showSearch(
            SearchUiState(query = "choc", hits = listOf(chocomel)),
            fontScale = 2.0f,
            width = 411.dp,
        )

        val name = boundsOfText("Chocomel")
        val value = boundsOfText("10.5 g carbs")
        assertTrue("name=$name value=$value", value.top >= name.bottom)
    }

    @Test
    fun atANarrowWidthTheFigureMayMoveBelowAtOnePointFiveScale() {
        showSearch(
            SearchUiState(query = "choc", hits = listOf(chocomel)),
            fontScale = 1.5f,
            width = 360.dp,
        )

        val name = boundsOfText("Chocomel")
        val value = boundsOfText("10.5 g carbs")
        assertTrue("name=$name value=$value", value.top >= name.bottom)
    }
}

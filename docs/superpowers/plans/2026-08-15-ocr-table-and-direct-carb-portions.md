# OCR Table Interpretation + Direct-Carb Countable Portions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make OCR reliably pick *total* carbohydrate on multi-column/hierarchical labels, and let countable portions (slice/piece/sachet) work from per-serving carbohydrate data alone — without ever asking the user to weigh food the source data already describes.

**Architecture:** A new geometry-first logical-table layer under `ocr/` (rows built from box geometry, not ML Kit `blockId`/`lineId`; child nutrients excluded by row *type*, not by score penalty) feeding the unchanged `LabelReading` contract. In the domain, `PortionUnit` swaps its `amountPerUnit`/`basis` pair for a sealed `PortionConversion` (`WeightBased` | `DirectCarbs`), so "we know grams per slice" and "we know carbs per slice" are two legitimate shapes rather than one shape with sentinel values. Room goes to v6 by rebuild-and-copy (SQLite cannot drop `NOT NULL` in place).

**Tech Stack:** Kotlin, Jetpack Compose, Room (SQLite), Retrofit + kotlinx.serialization, ML Kit on-device text recognition, JUnit4, Compose UI test, Room `MigrationTestHelper`.

**Spec:** `docs/superpowers/specs/2026-08-15-ocr-table-and-direct-carb-portions-design.md`

## Global Constraints

- **Do NOT commit and do NOT push.** All changes stay uncommitted for final review. Ignore any "Commit" step wording inherited from generic TDD habit — this plan deliberately contains none.
- **Do not reset/clean/checkout/revert/discard existing uncommitted work.** Three cosmetic files (`HomeScreen.kt`, `ic_launcher_foreground.xml`, `colors.xml`) are already modified and must stay untouched.
- `domain/` stays **pure Kotlin, zero Android imports** — JVM-testable with no emulator.
- `ocr/` core (`OcrDocument`, parsers, interpreter) stays pure Kotlin too; ML Kit types live only in `MlKitOcrMapper`/`LabelAnalyzer`.
- Decimals are **`BigDecimal`** in memory and **TEXT** in SQLite — never `REAL`, never `Double` arithmetic.
- Amounts are normalized with `stripTrailingZeros()` before storage (TEXT columns make `65` and `65.0` distinct otherwise).
- **Provenance (`dataSource`) and verification (`verificationStatus`) are separate fields and must stay separate.** Never merge them.
- `isRemoteRefreshable = !dataSource.isUserAuthored && verificationStatus == UNVERIFIED`. Both conditions matter, for products and per-unit alike.
- **Never destructive migrations.** No `fallbackToDestructiveMigration()`. Every version bump gets a real `Migration`. Guard `ALTER TABLE ADD COLUMN` with the existing `hasColumn` `PRAGMA table_info` helper.
- **No sentinels.** A `DIRECT_CARBS` row's weight fields are `NULL`, never `0`/`""`/`-1`.
- One formula: `CarbCalculator` alone computes weight-based carbs; `PortionResolver` alone does `count × amountPerUnit`; the new `DirectCarbCalculator` alone does `count × carbsPerUnit`. No fourth path.
- **Never claim something builds or passes without having run it.**
- **No LLM/cloud OCR, no photo upload, no average-slice-weight estimation, no category/name-based inference, no accounts/analytics, no unrelated refactor, no rewriting historical docs** under `docs/superpowers/specs/` or `docs/superpowers/plans/` (except this plan's own file).
- Displayed UI strings stay **English-only**; `ServingSizeParser` still *recognizes* Dutch input text. Do not conflate the two.
- OFF **Search** limit is **10 req/min/IP**; OFF **product read** limit is **15 req/min/IP**. Only Search-specific comments get corrected.
- Build commands (PowerShell):
  ```powershell
  $env:JAVA_HOME="C:\atools\jdk-21.0.12+8"
  $env:ANDROID_HOME="C:\atools\sdk"
  .\gradlew.bat :app:testDebugUnitTest
  ```

---

## File Structure

**Created:**

| File | Responsibility |
|---|---|
| `ocr/LogicalRow.kt` | `LogicalRow` data class + `LogicalRowBuilder` — geometry-only row reconstruction |
| `ocr/NutritionRowKind.kt` | `NutritionRowKind` enum + `RowClassifier` — hard child-nutrient exclusion |
| `ocr/NutritionColumnKind.kt` | `NutritionColumnKind` enum, `NutritionColumn`, `ColumnClassifier` |
| `ocr/NutritionTableInterpreter.kt` | Orchestrates rows → row kinds → columns → cell association → `NutritionParseReport` |
| `domain/PortionConversion.kt` | Sealed `PortionConversion` (`WeightBased` \| `DirectCarbs`) |
| `domain/ServingDescriptor.kt` | `ServingDescriptor`, `AmountWithBasis` |
| `domain/DirectCarbCalculator.kt` | `count × carbsPerUnit`, the only direct-carb formula |
| `app/src/test/kotlin/app/justthecarbs/ocr/LogicalRowBuilderTest.kt` | Row geometry tests |
| `app/src/test/kotlin/app/justthecarbs/ocr/NutritionTableInterpreterTest.kt` | Adversarial label fixtures (spec §18) |
| `app/src/test/kotlin/app/justthecarbs/domain/DirectCarbCalculatorTest.kt` | Direct-carb math |
| `app/src/test/kotlin/app/justthecarbs/domain/PortionConversionTest.kt` | Conversion-type invariants |
| `app/schemas/app.justthecarbs.data.local.JustTheCarbsDatabase/6.json` | Exported by the build |

**Modified:**

| File | Change |
|---|---|
| `ui/search/SearchViewModel.kt` | `displayedQuery` state, edit invalidation, rate-limit comment |
| `ocr/NutritionTerminology.kt` | Add dextrose/glucose/fructose/sucrose/lactose/maltose exclusions |
| `ocr/NutritionTableParser.kt` | Becomes thin adapter over `NutritionTableInterpreter`; `NutritionParseReport` gains `servingCandidate` |
| `ocr/LabelAnalyzer.kt` | `analyzeStill` callback widens to `NutritionParseReport` |
| `ui/scan/LabelScannerScreen.kt` | "Save as portion unit" affordance |
| `ui/JustTheCarbsNavHost.kt` | Thread barcode + save callback into label scanner |
| `domain/PortionUnit.kt` | `conversion: PortionConversion` replaces `amountPerUnit`/`basis` |
| `domain/ServingSizeParser.kt` | Descriptor/weight split; weight now optional |
| `domain/ProductDataSource.kt` | `PortionUnitCandidate.conversion` |
| `domain/MealItem.kt` | `MealItemKind` + nullable kind-specific fields |
| `data/local/PortionUnitEntity.kt` | Six conversion columns + mappers |
| `data/local/MealItemEntity.kt` | `itemKind` + nullable columns + mappers |
| `data/local/JustTheCarbsDatabase.kt` | v6 + `MIGRATION_5_6` |
| `data/remote/OpenFoodFactsDto.kt` | `carbohydrates_serving` |
| `data/remote/OpenFoodFactsApi.kt` | Request the new field; Search rate-limit comment |
| `data/remote/OpenFoodFactsDataSource.kt` | Case A–D conversion precedence |
| `data/ProductRepository.kt` | Conversion-aware save/refresh/freeze; direct-carb meal add |
| `domain/NutritionValueValidator.kt` | `validateCarbsPerServing` |
| `ui/product/ProductViewModel.kt` | Direct-carb calculation path |
| `ui/product/ProductScreen.kt` | Two-mode add/edit; conversion-aware display |
| `CLAUDE.md`, `docs/manual-qa.md` | §24 documentation |

---

## Task 1: Search invalidation fix (spec §0)

Self-contained, touches nothing else, and lands the regression fix before the large refactors begin.

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/search/SearchViewModel.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/data/remote/OpenFoodFactsApi.kt:39`
- Test: `app/src/test/kotlin/app/justthecarbs/ui/search/SearchViewModelTest.kt`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: no new public API. `SearchViewModel`'s public surface (`state`, `onQueryChanged`, `search`, `retry`) is unchanged.

The bug: `onQueryChanged` never touches `requestId`, so after submitting `bread` (requestId=1) and then merely *editing* the field to `bread wholegrain` (requestId still 1, no new request), the in-flight `bread` response passes `thisRequestId(1) == requestId(1)` and writes stale hits under the new editor text.

- [ ] **Step 1: Write the failing tests**

Add to `SearchViewModelTest.kt` (the existing `FakeSearchSource`, `hit()` helper and `dispatcher` are already in the file — reuse them as-is):

```kotlin
    @Test
    fun `editing the query after submitting discards the in-flight response`() = runTest {
        val source = FakeSearchSource()
        val viewModel = SearchViewModel(source)
        viewModel.onQueryChanged("bread")
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()

        // The user keeps typing without submitting. The "bread" request is still in flight.
        viewModel.onQueryChanged("bread wholegrain")
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("bread", ProductSearchResult.Found(listOf(hit(barcode = "stale-barcode"))))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList<ProductSearchHit>(), viewModel.state.value.hits)
        assertEquals("bread wholegrain", viewModel.state.value.query)
        assertFalse(viewModel.state.value.searching)
    }

    @Test
    fun `editing clears stale hits immediately before any new submission`() = runTest {
        val source = FakeSearchSource()
        val viewModel = SearchViewModel(source)
        viewModel.onQueryChanged("hagelslag")
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.state.value.hits.size)

        viewModel.onQueryChanged("hagelslag puur")

        assertEquals(emptyList<ProductSearchHit>(), viewModel.state.value.hits)
        assertFalse(viewModel.state.value.noMatches)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `editing away and back resubmits rather than being deduped`() = runTest {
        val source = FakeSearchSource()
        val viewModel = SearchViewModel(source)
        viewModel.onQueryChanged("hagelslag")
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()

        // A -> B -> A. The results for A were cleared during the edit to B, so retyping A and
        // submitting must genuinely search again rather than being swallowed as a duplicate.
        viewModel.onQueryChanged("puur")
        viewModel.onQueryChanged("hagelslag")
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        source.resolve("hagelslag", ProductSearchResult.Found(listOf(hit())))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, source.callCount)
        assertEquals(1, viewModel.state.value.hits.size)
    }

    @Test
    fun `editing cancels the running job so a late response cannot flip searching off`() = runTest {
        val source = FakeSearchSource()
        val viewModel = SearchViewModel(source)
        viewModel.onQueryChanged("bread")
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.searching)

        viewModel.onQueryChanged("bread rolls")
        dispatcher.scheduler.advanceUntilIdle()

        // Editing ends the search that was running: no spinner for a query nobody submitted.
        assertFalse(viewModel.state.value.searching)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ui.search.SearchViewModelTest"
```

Expected: the four new tests FAIL (stale hits written; `searching` still true; the A→B→A case reports `callCount == 1`). The 11 pre-existing tests still PASS.

- [ ] **Step 3: Implement the fix**

Replace the field block and `onQueryChanged`/`search`/`retry` in `SearchViewModel.kt`. Note `lastSubmittedQuery` is **deleted** — `displayedQuery` subsumes it:

```kotlin
    /**
     * The query the currently-displayed hits/noMatches/error belong to.
     *
     * Distinct from [SearchUiState.query], which is live editor text. Editing the field clears this,
     * which is what lets an A -> B -> A retype search again instead of being deduped against a
     * result set that is no longer on screen.
     */
    private var displayedQuery: String? = null
    private var searchJob: Job? = null

    /** Guards a slower, older response from overwriting a newer one (last-submitted wins). */
    private var requestId = 0L

    fun onQueryChanged(text: String) {
        // Editing invalidates whatever is on screen and whatever is in flight. Bumping requestId is
        // the part that was missing: without it, an in-flight response for the *previous* query
        // still passed runSearch's "am I the latest request" check — because nothing had raised the
        // counter since that request started — and wrote its hits underneath the new editor text.
        if (text != displayedQuery) {
            requestId++
            searchJob?.cancel()
            searchJob = null
            displayedQuery = null
        }
        _state.update {
            it.copy(
                query = text,
                hits = emptyList(),
                noMatches = false,
                error = null,
                searching = false,
            )
        }
    }

    /** Explicit search trigger — IME "Search" action or a search button, never a keystroke. */
    fun search() {
        val terms = _state.value.query.trim()
        if (terms.isBlank()) return
        if (terms.length < MIN_QUERY_LENGTH) {
            _state.update { it.copy(searching = false, hits = emptyList(), noMatches = false) }
            return
        }
        // A result for exactly this text is already showing: no duplicate submission.
        if (terms == displayedQuery) return

        displayedQuery = terms
        searchJob?.cancel()
        val thisRequestId = ++requestId
        searchJob = viewModelScope.launch { runSearch(terms, thisRequestId) }
    }
```

In `runSearch`, the existing staleness guard stays as-is (`if (thisRequestId != requestId) return`) — it now actually fires for the edit case because `onQueryChanged` bumps the counter.

Update `retry()` to clear the new field:

```kotlin
    fun retry() {
        // A retry re-runs the same query even though its result is already on screen — clear the
        // dedupe guard first so it is not silently dropped as a duplicate submission.
        displayedQuery = null
        search()
    }
```

Fix the rate-limit comment in the same file (line ~35), Search-specific only:

```kotlin
 * Search is **explicit**, not as-you-type: Open Food Facts' search endpoint is rate-limited
 * (10 reads/min/IP for search, distinct from the 15/min product-read budget) and must not be hit on
 * every keystroke. Typing only updates [SearchUiState.query]; a network request happens only when
 * [search] is called, from the field's IME "Search" action or a dedicated search button.
```

And in `OpenFoodFactsApi.kt` (line ~39), inside the `search` KDoc:

```kotlin
     * [pageSize] is deliberately small. This is a disambiguation list the user reads, not a catalogue
     * to browse, and OFF's search budget is 10 requests/min/IP.
```

Leave the 15/min comments in `OpenFoodFactsDataSource.kt:115`, `ProductRepository.kt:47` and `ProductRepositoryTest.kt:46` **unchanged** — those describe the product-read path, where 15/min is correct.

- [ ] **Step 4: Run the tests to verify they pass**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ui.search.SearchViewModelTest"
```

Expected: all 15 tests PASS.

Note: `clearing the query drops stale hits without searching` and `retry re-runs the same query even though it already ran` are pre-existing tests that must still pass — the new `onQueryChanged` clears unconditionally (a superset of the old blank-only clear), and `retry` now resets `displayedQuery`.

---

## Task 2: Child-nutrient terminology (spec §2)

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ocr/NutritionTerminology.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/ocr/NutritionTerminologyTest.kt` (create if absent)

**Interfaces:**
- Consumes: nothing.
- Produces: `NutritionTerminology.exclusionTerms` gains the specific-sugar names that Task 4's `RowClassifier` relies on for hard exclusion.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/app/justthecarbs/ocr/NutritionTerminologyTest.kt`:

```kotlin
package app.justthecarbs.ocr

import org.junit.Assert.assertTrue
import org.junit.Test

// Suite: nutrition terminology
// Invariant: every named sugar that appears as a child row on real packaging is an exclusion term,
// so a row naming one can never be mistaken for the total-carbohydrate row.
class NutritionTerminologyTest {

    @Test
    fun `named sugars are exclusion terms`() {
        val named = listOf("dextrose", "glucose", "fructose", "sucrose", "lactose", "maltose")
        named.forEach { term ->
            assertTrue(
                "$term must be an exclusion term",
                NutritionTerminology.exclusionTerms.any {
                    NutritionTerminology.containsTerm(NutritionTerminology.normalize(term), it)
                },
            )
        }
    }

    @Test
    fun `added sugars phrasings are exclusion terms`() {
        listOf("added sugars", "added sugar", "of which sugars").forEach { phrase ->
            assertTrue(
                "$phrase must be an exclusion term",
                NutritionTerminology.exclusionTerms.any {
                    NutritionTerminology.containsTerm(NutritionTerminology.normalize(phrase), it)
                },
            )
        }
    }

    @Test
    fun `carbohydrate itself is never an exclusion term`() {
        assertTrue(
            NutritionTerminology.exclusionTerms.none {
                NutritionTerminology.normalize(it) == "carbohydrate"
            },
        )
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.NutritionTerminologyTest"
```

Expected: `named sugars are exclusion terms` FAILS (dextrose/glucose/fructose/sucrose/lactose/maltose are absent today) and `added sugars phrasings` FAILS on "added sugar(s)".

- [ ] **Step 3: Extend the English exclusion set**

In `NutritionTerminology.kt`, replace the `"en"` row. The named sugars are chemically the same words across these Latin-script languages, so they go in the shared English set rather than being duplicated per language:

```kotlin
        NutritionTerms("en", setOf("carbohydrate", "carbohydrates"), setOf("of which sugars", "sugars", "sugar", "added sugars", "added sugar", "fibre", "fiber", "starch", "polyols", "polyol", "dextrose", "glucose", "fructose", "sucrose", "lactose", "maltose", "maltodextrin", "glucose syrup"), setOf("serving", "portion")),
```

Everything else in the file is untouched — the other 14 languages keep their existing terms.

- [ ] **Step 4: Run to verify it passes**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.*"
```

Expected: the new suite PASSES and the existing `NutritionLabelParserTest` still PASSES (it asserts exclusion behaviour that only gets stronger here).

---

## Task 3: `LogicalRowBuilder` — geometry-first rows (spec §1.1)

**Files:**
- Create: `app/src/main/kotlin/app/justthecarbs/ocr/LogicalRow.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/ocr/LogicalRowBuilderTest.kt`

**Interfaces:**
- Consumes: `OcrDocument`, `OcrElement`, `OcrBox` (existing, unchanged) — `OcrBox` already provides `verticalOverlapRatio`, `union`, `centerX`, `centerY`, `width`, `height`.
- Produces:
  ```kotlin
  data class LogicalRow(val elements: List<OcrElement>, val box: OcrBox, val sourceLines: Set<LineKey>) {
      val text: String
  }
  data class LineKey(val block: Int, val line: Int)
  object LogicalRowBuilder { fun build(document: OcrDocument): List<LogicalRow> }
  object LogicalRowThresholds {
      const val MIN_ROW_OVERLAP = 0.5
      const val MAX_CENTER_DISTANCE_IN_HEIGHT = 0.6
  }
  ```
  Tasks 4, 5 and 6 consume `LogicalRow`, `LineKey` and `LogicalRowBuilder.build`.

Note: `LineKey` currently exists as a `private data class` inside `NutritionTableParser`. It is promoted to a top-level type in `LogicalRow.kt`; Task 6 deletes the private copy.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/app/justthecarbs/ocr/LogicalRowBuilderTest.kt`:

```kotlin
package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Suite: geometry-first logical rows
// Invariant: row membership is decided by box geometry alone. ML Kit's blockId/lineId are carried
// for diagnostics and never consulted, because ML Kit both splits one printed row across lines and
// merges two printed rows into one — the two failures that made the old parser pick sugars.
class LogicalRowBuilderTest {

    private fun e(text: String, left: Int, top: Int, right: Int, bottom: Int, line: Int, block: Int = 0) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = block, lineId = line)

    private fun document(vararg elements: OcrElement): OcrDocument =
        OcrDocument(width = 800, height = 500, elements = elements.toList())

    @Test
    fun `elements on the same printed row merge despite different ML Kit line ids`() {
        // Same baseline, but ML Kit split the label and its value into separate lines/blocks.
        val rows = LogicalRowBuilder.build(
            document(
                e("Carbohydrate", 40, 200, 240, 230, line = 0, block = 0),
                e("45", 400, 202, 440, 232, line = 7, block = 3),
            ),
        )

        assertEquals(1, rows.size)
        assertEquals(listOf("Carbohydrate", "45"), rows.single().elements.map { it.text })
    }

    @Test
    fun `elements on different printed rows split despite a shared ML Kit line id`() {
        // ML Kit merged two printed rows onto one line id. Geometry must still separate them.
        val rows = LogicalRowBuilder.build(
            document(
                e("Carbohydrate", 40, 200, 240, 230, line = 4),
                e("45", 400, 200, 440, 230, line = 4),
                e("of which sugars", 60, 250, 260, 280, line = 4),
                e("8", 400, 250, 440, 280, line = 4),
            ),
        )

        assertEquals(2, rows.size)
        assertEquals(listOf("Carbohydrate", "45"), rows[0].elements.map { it.text })
        assertEquals(listOf("of which sugars", "8"), rows[1].elements.map { it.text })
    }

    @Test
    fun `rows are ordered top to bottom and elements left to right`() {
        val rows = LogicalRowBuilder.build(
            document(
                e("8", 400, 250, 440, 280, line = 1),
                e("of which sugars", 60, 250, 260, 280, line = 1),
                e("45", 400, 200, 440, 230, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 0),
            ),
        )

        assertEquals(listOf("Carbohydrate", "45"), rows[0].elements.map { it.text })
        assertEquals(listOf("of which sugars", "8"), rows[1].elements.map { it.text })
    }

    @Test
    fun `the row box is the union of its elements`() {
        val rows = LogicalRowBuilder.build(
            document(
                e("Carbohydrate", 40, 200, 240, 232, line = 0),
                e("45", 400, 202, 440, 230, line = 1),
            ),
        )

        val box = rows.single().box
        assertEquals(40, box.left)
        assertEquals(440, box.right)
        assertEquals(200, box.top)
        assertEquals(232, box.bottom)
    }

    @Test
    fun `source line keys are retained for diagnostics`() {
        val rows = LogicalRowBuilder.build(
            document(
                e("Carbohydrate", 40, 200, 240, 230, line = 0, block = 0),
                e("45", 400, 202, 440, 232, line = 7, block = 3),
            ),
        )

        assertEquals(setOf(LineKey(0, 0), LineKey(3, 7)), rows.single().sourceLines)
    }

    @Test
    fun `a slightly offset element still joins the row when overlap is high`() {
        // Real OCR boxes jitter by a few pixels on the same printed baseline.
        val rows = LogicalRowBuilder.build(
            document(
                e("Carbohydrate", 40, 200, 240, 230, line = 0),
                e("45", 400, 204, 440, 234, line = 1),
            ),
        )

        assertEquals(1, rows.size)
    }

    @Test
    fun `an empty document yields no rows`() {
        assertTrue(LogicalRowBuilder.build(document()).isEmpty())
    }

    @Test
    fun `tall and short elements on one baseline still merge`() {
        // A large "45" beside small "Carbohydrate" text — overlap ratio is measured against the
        // running row box, so a big element does not orphan its own row.
        val rows = LogicalRowBuilder.build(
            document(
                e("Carbohydrate", 40, 210, 240, 232, line = 0),
                e("45", 400, 196, 460, 240, line = 1),
            ),
        )

        assertEquals(1, rows.size)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.LogicalRowBuilderTest"
```

Expected: compilation FAILS — `LogicalRow`, `LogicalRowBuilder` and `LineKey` do not exist.

- [ ] **Step 3: Implement `LogicalRow.kt`**

```kotlin
package app.justthecarbs.ocr

import kotlin.math.abs

/** An ML Kit block/line pair. Diagnostics only — never a row-membership signal. */
data class LineKey(val block: Int, val line: Int)

/**
 * One printed table row, reconstructed from geometry.
 *
 * ML Kit's own line grouping is not the printed layout: it both splits a single printed row across
 * several lines (a label and its value recognized as separate blocks) and merges two printed rows
 * into one line (a total and the "of which sugars" beneath it). The old parser trusted that grouping
 * and could therefore attribute a child nutrient's value to the total-carbohydrate label. Row
 * identity is now decided by box geometry alone; [sourceLines] is kept only so diagnostics can show
 * where the text came from.
 */
data class LogicalRow(
    /** Left-to-right. */
    val elements: List<OcrElement>,
    /** Union of every element box. */
    val box: OcrBox,
    val sourceLines: Set<LineKey>,
) {
    val text: String = elements.joinToString(" ") { it.text }
}

/**
 * Row-membership geometry.
 *
 * Deliberately stricter than [NutritionParserThresholds]'s row constants and deliberately separate
 * from them. Those were tuned as one signal among many inside a scoring model, where being slightly
 * too generous only nudged a score. Here a row boundary is a hard structural claim that later stages
 * treat as authoritative, so it is tuned tight and reviewed on its own terms.
 */
object LogicalRowThresholds {
    /** Fraction of vertical overlap against the running row box required to join it. */
    const val MIN_ROW_OVERLAP = 0.5

    /** Narrow tiebreaker when overlap is present but inconclusive, in median element heights. */
    const val MAX_CENTER_DISTANCE_IN_HEIGHT = 0.6
}

object LogicalRowBuilder {

    fun build(document: OcrDocument): List<LogicalRow> {
        if (document.elements.isEmpty()) return emptyList()

        val medianHeight = medianHeight(document.elements)
        val ordered = document.elements.sortedWith(compareBy({ it.box.centerY }, { it.box.left }))

        val rows = mutableListOf<MutableList<OcrElement>>()
        var current = mutableListOf(ordered.first())
        var currentBox = ordered.first().box

        ordered.drop(1).forEach { element ->
            if (belongsToRow(element, currentBox, medianHeight)) {
                current += element
                currentBox = currentBox.union(element.box)
            } else {
                rows += current
                current = mutableListOf(element)
                currentBox = element.box
            }
        }
        rows += current

        return rows.map { elements ->
            val sorted = elements.sortedBy { it.box.left }
            LogicalRow(
                elements = sorted,
                box = sorted.drop(1).fold(sorted.first().box) { box, e -> box.union(e.box) },
                sourceLines = sorted.map { LineKey(it.blockId, it.lineId) }.toSet(),
            )
        }
    }

    /**
     * Overlap first, centre distance only as a narrow tiebreaker.
     *
     * Overlap is measured against the row's *running* union box rather than against the previous
     * element, so a tall value beside short label text does not start a spurious row.
     */
    private fun belongsToRow(element: OcrElement, rowBox: OcrBox, medianHeight: Int): Boolean {
        val overlap = rowBox.verticalOverlapRatio(element.box)
        if (overlap >= LogicalRowThresholds.MIN_ROW_OVERLAP) return true
        if (overlap <= 0.0) return false
        val allowed = medianHeight.coerceAtLeast(1) * LogicalRowThresholds.MAX_CENTER_DISTANCE_IN_HEIGHT
        return abs(rowBox.centerY - element.box.centerY) <= allowed
    }

    private fun medianHeight(elements: List<OcrElement>): Int {
        val heights = elements.map { it.box.height }.sorted()
        return heights[heights.size / 2]
    }
}
```

- [ ] **Step 4: Run to verify it passes**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.LogicalRowBuilderTest"
```

Expected: all 8 tests PASS.

If `elements on different printed rows split` fails because the two baselines overlap at exactly the threshold, do **not** loosen `MIN_ROW_OVERLAP` — the fixture's 20 px gap between a 30 px-tall row's bottom (230) and the next row's top (250) yields zero overlap, so a failure here means `verticalOverlapRatio` is being called with arguments in an unexpected order. `OcrBox.verticalOverlapRatio` is symmetric (`overlap / min(height, other.height)`), so order does not in fact matter — check the fixture coordinates instead.

---

## Task 4: `RowClassifier` — hard child-nutrient exclusion (spec §2)

**Files:**
- Create: `app/src/main/kotlin/app/justthecarbs/ocr/NutritionRowKind.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/ocr/RowClassifierTest.kt`

**Interfaces:**
- Consumes: `LogicalRow` (Task 3), `NutritionTerminology.exclusionTerms`/`carbohydrateTerms`/`servingTerms` (Task 2).
- Produces:
  ```kotlin
  enum class NutritionRowKind { TOTAL_CARBOHYDRATE, CARBOHYDRATE_CHILD, HEADER, OTHER }
  object RowClassifier { fun classify(row: LogicalRow): NutritionRowKind }
  ```
  Task 6 consumes both.

The decisive rule: a row naming a child nutrient is `CARBOHYDRATE_CHILD` **unconditionally** — a type-level exclusion, not a score penalty. `"Carbohydrate of which sugars 8g"` is a child row even though it contains the word "carbohydrate".

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/app/justthecarbs/ocr/RowClassifierTest.kt`:

```kotlin
package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

// Suite: row classification
// Invariant: a row naming any child nutrient can NEVER be TOTAL_CARBOHYDRATE, whatever else it says.
// This is a type-level exclusion, not a scoring penalty — the old scoring model could be outvoted
// by geometry and pick "of which sugars" as the total.
class RowClassifierTest {

    private fun row(vararg words: String): LogicalRow {
        val elements = words.mapIndexed { index, word ->
            OcrElement(word, OcrBox(40 + index * 100, 200, 130 + index * 100, 230), blockId = 0, lineId = 0)
        }
        return LogicalRow(
            elements = elements,
            box = elements.drop(1).fold(elements.first().box) { box, e -> box.union(e.box) },
            sourceLines = setOf(LineKey(0, 0)),
        )
    }

    @Test
    fun `a plain carbohydrate row is the total`() {
        assertEquals(NutritionRowKind.TOTAL_CARBOHYDRATE, RowClassifier.classify(row("Carbohydrate", "45", "g")))
    }

    @Test
    fun `an of-which-sugars row is a child even though it says carbohydrate`() {
        assertEquals(
            NutritionRowKind.CARBOHYDRATE_CHILD,
            RowClassifier.classify(row("Carbohydrate", "of", "which", "sugars", "8", "g")),
        )
    }

    @Test
    fun `a dextrose row is a child`() {
        assertEquals(NutritionRowKind.CARBOHYDRATE_CHILD, RowClassifier.classify(row("Dextrose", "3.1", "g")))
    }

    @Test
    fun `a polyols row is a child`() {
        assertEquals(NutritionRowKind.CARBOHYDRATE_CHILD, RowClassifier.classify(row("Polyols", "12", "g")))
    }

    @Test
    fun `a dutch koolhydraten row is the total`() {
        assertEquals(NutritionRowKind.TOTAL_CARBOHYDRATE, RowClassifier.classify(row("Koolhydraten", "45", "g")))
    }

    @Test
    fun `a dutch waarvan suikers row is a child`() {
        assertEquals(
            NutritionRowKind.CARBOHYDRATE_CHILD,
            RowClassifier.classify(row("waarvan", "suikers", "8", "g")),
        )
    }

    @Test
    fun `a per-100g header row is a header`() {
        assertEquals(NutritionRowKind.HEADER, RowClassifier.classify(row("per", "100", "g")))
    }

    @Test
    fun `a per-serving header row is a header`() {
        assertEquals(NutritionRowKind.HEADER, RowClassifier.classify(row("per", "serving")))
    }

    @Test
    fun `a protein row is other`() {
        assertEquals(NutritionRowKind.OTHER, RowClassifier.classify(row("Protein", "7.2", "g")))
    }

    @Test
    fun `a header row that also names carbohydrate is still the total row`() {
        // "Carbohydrate per 100 g" on one printed row: the nutrient wins, because this row carries
        // the value. Header detection only applies to rows that name no nutrient at all.
        assertEquals(
            NutritionRowKind.TOTAL_CARBOHYDRATE,
            RowClassifier.classify(row("Carbohydrate", "per", "100", "g", "45")),
        )
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.RowClassifierTest"
```

Expected: compilation FAILS — `NutritionRowKind` and `RowClassifier` do not exist.

- [ ] **Step 3: Implement `NutritionRowKind.kt`**

```kotlin
package app.justthecarbs.ocr

/** What one reconstructed table row is, as far as a carbohydrate reading is concerned. */
enum class NutritionRowKind {
    /** The row carrying the product's TOTAL carbohydrate figure. At most one per table. */
    TOTAL_CARBOHYDRATE,

    /** Sugars, polyols, starch, fibre, a named sugar — never a source of the total. */
    CARBOHYDRATE_CHILD,

    /** A column-header row: "per 100 g", "per serving", "%RI". Carries no nutrient value. */
    HEADER,

    OTHER,
}

/**
 * Classifies a [LogicalRow] by what it is, before any value is read from it.
 *
 * The child-nutrient rule is the whole point of this stage: a row naming any child nutrient is
 * [NutritionRowKind.CARBOHYDRATE_CHILD] **unconditionally**, so it cannot become the total no matter
 * what else it contains or how close its number sits to the word "Carbohydrate". The previous parser
 * expressed this as a scoring penalty, which geometry could outvote — that is exactly how a sugars
 * value got reported as total carbohydrate on a real package.
 */
object RowClassifier {

    fun classify(row: LogicalRow): NutritionRowKind {
        val normalized = NutritionTerminology.normalize(row.text)

        // First and unconditional. Order matters: "Carbohydrate of which sugars" hits this before
        // the carbohydrate check below, which is the entire correctness claim of this class.
        if (NutritionTerminology.exclusionTerms.any { NutritionTerminology.containsTerm(normalized, it) }) {
            return NutritionRowKind.CARBOHYDRATE_CHILD
        }

        if (NutritionTerminology.carbohydrateTerms.any { NutritionTerminology.containsTerm(normalized, it) }) {
            return NutritionRowKind.TOTAL_CARBOHYDRATE
        }

        if (isHeaderLike(normalized)) return NutritionRowKind.HEADER

        return NutritionRowKind.OTHER
    }

    /**
     * A row that names no nutrient but does name a measurement basis. Checked only after the
     * nutrient checks, so "Carbohydrate per 100 g" — a value row with an inline basis — stays the
     * total row rather than being demoted to a header that carries no value.
     */
    private fun isHeaderLike(normalizedText: String): Boolean {
        if (PER_100.containsMatchIn(normalizedText)) return true
        if (NutritionTerminology.servingTerms.any { NutritionTerminology.containsTerm(normalizedText, it) }) {
            return true
        }
        return REFERENCE_INTAKE.containsMatchIn(normalizedText)
    }

    private val PER_100 = Regex("(?:^|\\s)100\\s*(?:g|ml)(?:$|\\s)")

    /** "%RI", "%DV", "reference intake", "RI*" — the percentage column's vocabulary. */
    private val REFERENCE_INTAKE = Regex("(?:^|\\s)(?:ri|dv|gda|reference intake|daily value)(?:$|\\s)")
}
```

- [ ] **Step 4: Run to verify it passes**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.RowClassifierTest"
```

Expected: all 10 tests PASS.

---

## Task 5: `ColumnClassifier` (spec §3)

**Files:**
- Create: `app/src/main/kotlin/app/justthecarbs/ocr/NutritionColumnKind.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/ocr/ColumnClassifierTest.kt`

**Interfaces:**
- Consumes: `LogicalRow`, `LineKey` (Task 3), `NutritionRowKind`/`RowClassifier` (Task 4), `OcrBox`.
- Produces:
  ```kotlin
  enum class NutritionColumnKind { PER_100_G, PER_100_ML, PER_SERVING, REFERENCE_PERCENT, UNKNOWN }
  data class NutritionColumn(
      val kind: NutritionColumnKind,
      val headerBox: OcrBox?,
      val centerX: Double,
      val headerText: String,
  )
  object ColumnClassifier { fun classify(rows: List<LogicalRow>, documentWidth: Int): List<NutritionColumn> }
  ```
  Task 6 consumes all of it.

The percent fallback exists for one reason: a `%RI` column whose header OCR dropped must still never be mistaken for grams. It does **not** guess `PER_100_G` vs `PER_SERVING` from shape — those require a header match, else `UNKNOWN`, and an `UNKNOWN` cell is never used.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/app/justthecarbs/ocr/ColumnClassifierTest.kt`:

```kotlin
package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Suite: column classification
// Invariant: a percent column is never mistaken for a grams column, even when its header is
// unreadable — the fallback inspects the cells' own shape. PER_100_G vs PER_SERVING is never
// guessed from shape; without a header those stay UNKNOWN and are refused downstream.
class ColumnClassifierTest {

    private fun e(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = 0, lineId = top / 50)

    private fun row(vararg elements: OcrElement): LogicalRow = LogicalRow(
        elements = elements.sortedBy { it.box.left },
        box = elements.drop(1).fold(elements.first().box) { box, el -> box.union(el.box) },
        sourceLines = elements.map { LineKey(it.blockId, it.lineId) }.toSet(),
    )

    @Test
    fun `per 100 g and per serving headers become two classified columns`() {
        val header = row(
            e("per", 300, 100, 340, 130),
            e("100", 345, 100, 385, 130),
            e("g", 390, 100, 405, 130),
            e("per", 550, 100, 590, 130),
            e("serving", 595, 100, 680, 130),
        )

        val columns = ColumnClassifier.classify(listOf(header), documentWidth = 800)

        assertEquals(
            listOf(NutritionColumnKind.PER_100_G, NutritionColumnKind.PER_SERVING),
            columns.map { it.kind }.sortedBy { it.ordinal },
        )
    }

    @Test
    fun `a per 100 ml header classifies as PER_100_ML`() {
        val header = row(e("per", 300, 100, 340, 130), e("100", 345, 100, 385, 130), e("ml", 390, 100, 415, 130))

        val columns = ColumnClassifier.classify(listOf(header), documentWidth = 800)

        assertEquals(listOf(NutritionColumnKind.PER_100_ML), columns.map { it.kind })
    }

    @Test
    fun `a percent reference header classifies as REFERENCE_PERCENT`() {
        val header = row(
            e("per", 300, 100, 340, 130),
            e("100", 345, 100, 385, 130),
            e("g", 390, 100, 405, 130),
            e("%RI", 620, 100, 670, 130),
        )

        val columns = ColumnClassifier.classify(listOf(header), documentWidth = 800)

        assertTrue(columns.any { it.kind == NutritionColumnKind.REFERENCE_PERCENT })
        assertTrue(columns.any { it.kind == NutritionColumnKind.PER_100_G })
    }

    @Test
    fun `a headerless percent column is recognised from its own cells`() {
        // OCR dropped the "%RI" header entirely. The column's cells all carry a percent sign, which
        // is enough to refuse them as carbohydrate grams — the only claim this fallback makes.
        val header = row(e("per", 300, 100, 340, 130), e("100", 345, 100, 385, 130), e("g", 390, 100, 405, 130))
        val carbs = row(e("Carbohydrate", 40, 200, 240, 230), e("45", 350, 200, 390, 230), e("17%", 620, 200, 680, 230))
        val fat = row(e("Fat", 40, 250, 100, 280), e("1.5", 350, 250, 390, 280), e("2%", 620, 250, 680, 280))

        val columns = ColumnClassifier.classify(listOf(header, carbs, fat), documentWidth = 800)

        val percent = columns.firstOrNull { it.kind == NutritionColumnKind.REFERENCE_PERCENT }
        assertTrue("a headerless percent column must still be classified", percent != null)
        assertTrue("it sits at the percent cells' x position", percent!!.centerX > 600)
    }

    @Test
    fun `a value column with no header at all is not guessed`() {
        // No header row anywhere, no percent shape. Refusing to guess is the point: a wrong
        // PER_100_G guess here would produce a confident wrong carbohydrate number.
        val carbs = row(e("Carbohydrate", 40, 200, 240, 230), e("45", 350, 200, 390, 230))

        val columns = ColumnClassifier.classify(listOf(carbs), documentWidth = 800)

        assertTrue(
            "no column may be classified as a grams basis without a header",
            columns.none { it.kind == NutritionColumnKind.PER_100_G || it.kind == NutritionColumnKind.PER_100_ML },
        )
    }

    @Test
    fun `no rows yields no columns`() {
        assertTrue(ColumnClassifier.classify(emptyList(), documentWidth = 800).isEmpty())
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.ColumnClassifierTest"
```

Expected: compilation FAILS — `NutritionColumnKind`, `NutritionColumn` and `ColumnClassifier` do not exist.

- [ ] **Step 3: Implement `NutritionColumnKind.kt`**

```kotlin
package app.justthecarbs.ocr

/** What one table column measures. */
enum class NutritionColumnKind {
    PER_100_G,
    PER_100_ML,
    PER_SERVING,

    /** A "%RI"/"%DV" column. Never a carbohydrate quantity, only a percentage of a daily reference. */
    REFERENCE_PERCENT,

    /** Position known, meaning not established. A cell here is never used for any figure. */
    UNKNOWN,
}

/** One classified column, positioned by the x-centre later cells are aligned against. */
data class NutritionColumn(
    val kind: NutritionColumnKind,
    /** Null when the column was recovered from its cells rather than from a header. */
    val headerBox: OcrBox?,
    val centerX: Double,
    /** The header text this column was read from, or "" when recovered from cell shape. */
    val headerText: String,
)

/**
 * Finds the table's columns and says what each one measures.
 *
 * Header text is the primary signal. The cell-shape fallback exists for exactly one job: a column
 * whose numbers all carry a percent sign is [NutritionColumnKind.REFERENCE_PERCENT] even when OCR
 * lost its header, so a "17%" can never be handed back as 17 g of carbohydrate. It deliberately does
 * NOT try to tell per-100 from per-serving by shape — both look like bare grams, and a wrong guess
 * there is a confident wrong answer rather than a refusal. Those need a header, or they stay
 * [NutritionColumnKind.UNKNOWN] and are refused downstream.
 */
object ColumnClassifier {

    fun classify(rows: List<LogicalRow>, documentWidth: Int): List<NutritionColumn> {
        val headerRows = rows.filter { RowClassifier.classify(it) == NutritionRowKind.HEADER }
        val fromHeaders = headerRows.flatMap { columnsIn(it) }

        val percentColumns = percentColumnsFromCells(rows, documentWidth)
            .filter { candidate ->
                // A header already covering this x position wins; do not add a duplicate.
                fromHeaders.none { abs(it.centerX - candidate.centerX) <= documentWidth * NEAR_COLUMN_FRACTION }
            }

        return (fromHeaders + percentColumns).sortedBy { it.centerX }
    }

    /**
     * Walks a header row's elements, growing a span while it stays unrecognized and emitting a
     * column as soon as the span matches a known header vocabulary. This is what separates
     * "per 100 g" from "per serving" when both sit on one row.
     */
    private fun columnsIn(row: LogicalRow): List<NutritionColumn> {
        val columns = mutableListOf<NutritionColumn>()
        var spanStart = 0

        while (spanStart < row.elements.size) {
            var matched = false
            // Longest span first: "per 100 ml" must beat a bare "100" prefix.
            for (length in minOf(MAX_HEADER_SPAN, row.elements.size - spanStart) downTo 1) {
                val span = row.elements.subList(spanStart, spanStart + length)
                val text = span.joinToString(" ") { it.text }
                val kind = kindOf(NutritionTerminology.normalize(text)) ?: continue
                val box = span.drop(1).fold(span.first().box) { acc, e -> acc.union(e.box) }
                columns += NutritionColumn(kind, box, box.centerX, text)
                spanStart += length
                matched = true
                break
            }
            if (!matched) spanStart++
        }

        return columns
    }

    private fun kindOf(normalizedSpan: String): NutritionColumnKind? {
        REFERENCE_PERCENT.find(normalizedSpan)?.let { return NutritionColumnKind.REFERENCE_PERCENT }
        PER_100.find(normalizedSpan)?.let { match ->
            return if (match.groupValues[1] == "ml") NutritionColumnKind.PER_100_ML else NutritionColumnKind.PER_100_G
        }
        if (NutritionTerminology.servingTerms.any { NutritionTerminology.containsTerm(normalizedSpan, it) }) {
            return NutritionColumnKind.PER_SERVING
        }
        return null
    }

    /**
     * Recovers a percent column from the cells themselves when its header is missing or unreadable.
     * Requires at least two percent-shaped cells sharing an x position, so a single stray "17%" in
     * running text cannot invent a column.
     */
    private fun percentColumnsFromCells(rows: List<LogicalRow>, documentWidth: Int): List<NutritionColumn> {
        val percentCells = rows
            .filter { RowClassifier.classify(it) != NutritionRowKind.HEADER }
            .flatMap { it.elements }
            .filter { PERCENT_CELL.containsMatchIn(it.text) }

        if (percentCells.size < MIN_PERCENT_CELLS) return emptyList()

        val tolerance = documentWidth * NEAR_COLUMN_FRACTION
        val clusters = mutableListOf<MutableList<OcrElement>>()
        percentCells.sortedBy { it.box.centerX }.forEach { cell ->
            val cluster = clusters.lastOrNull()
            if (cluster != null && abs(cluster.last().box.centerX - cell.box.centerX) <= tolerance) {
                cluster += cell
            } else {
                clusters += mutableListOf(cell)
            }
        }

        return clusters.filter { it.size >= MIN_PERCENT_CELLS }.map { cluster ->
            NutritionColumn(
                kind = NutritionColumnKind.REFERENCE_PERCENT,
                headerBox = null,
                centerX = cluster.map { it.box.centerX }.average(),
                headerText = "",
            )
        }
    }

    private fun abs(value: Double): Double = kotlin.math.abs(value)

    /** Longest header phrase worth trying, e.g. "per 100 ml". */
    private const val MAX_HEADER_SPAN = 4

    /** Two cells make a column; one is a stray. */
    private const val MIN_PERCENT_CELLS = 2

    /** How close two x-centres must be to count as the same column. */
    private const val NEAR_COLUMN_FRACTION = 0.08

    private val PER_100 = Regex("(?:^|\\s)100\\s*(g|ml)(?:$|\\s)")
    private val REFERENCE_PERCENT = Regex("(?:^|\\s)(?:ri|dv|gda|reference intake|daily value)(?:$|\\s)")
    private val PERCENT_CELL = Regex("\\d\\s*%")
}
```

- [ ] **Step 4: Run to verify it passes**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.ColumnClassifierTest"
```

Expected: all 6 tests PASS.

Note on `%RI` normalization: `NutritionTerminology.normalize` strips non-word characters, so `"%RI"` normalizes to `"ri"` — which the `REFERENCE_PERCENT` regex matches as a standalone word. The `PERCENT_CELL` regex runs against **raw** element text (not normalized), because the `%` character is exactly what it needs and normalization would remove it.

---

## Task 6: `ServingDescriptor` + `ServingSizeParser` split (spec §6)

Comes before the interpreter because the interpreter's `ServingCarbCandidate` carries a typed `ServingDescriptor`.

**Files:**
- Create: `app/src/main/kotlin/app/justthecarbs/domain/ServingDescriptor.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/domain/ServingSizeParser.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/domain/ServingSizeParserTest.kt`

**Interfaces:**
- Consumes: `PortionUnitKind`, `NutritionBasis`, `PortionParser` (existing).
- Produces:
  ```kotlin
  data class AmountWithBasis(val amount: BigDecimal, val basis: NutritionBasis)
  data class ServingDescriptor(
      val kind: PortionUnitKind,
      val count: BigDecimal,
      val weightOrVolume: AmountWithBasis?,
      val rawText: String,
  ) {
      val amountPerUnit: AmountWithBasis?
  }
  object ServingSizeParser {
      fun parseDescriptor(rawServingSize: String?): ServingDescriptor?
      fun parse(rawServingSize: String?): ParsedServingSize?   // kept, now delegating
      internal fun kindForWord(word: String): PortionUnitKind?
  }
  ```
  Tasks 7, 9 and 10 consume these.

**Deliberate behaviour change:** the existing test `rejects a count and unit word with no weight at all` asserts `parse("1 slice") == null`. `parse` keeps that behaviour (it still requires a weight), but the *new* `parseDescriptor("1 slice")` returns a descriptor with `weightOrVolume = null`. The existing test is **kept as-is** and a new one covers the descriptor path — the old contract is not broken, it is supplemented.

- [ ] **Step 1: Write the failing tests**

Append to `app/src/test/kotlin/app/justthecarbs/domain/ServingSizeParserTest.kt`:

```kotlin
    @Test
    fun `parseDescriptor accepts a bare count and unit word with no weight`() {
        val descriptor = ServingSizeParser.parseDescriptor("1 slice")

        assertEquals(PortionUnitKind.SLICE, descriptor?.kind)
        assertEquals(0, BigDecimal.ONE.compareTo(descriptor?.count))
        assertNull("no weight was printed, so none may be invented", descriptor?.weightOrVolume)
    }

    @Test
    fun `parseDescriptor keeps a bracketed weight when one is present`() {
        val descriptor = ServingSizeParser.parseDescriptor("2 slices (70 g)")

        assertEquals(PortionUnitKind.SLICE, descriptor?.kind)
        assertEquals(0, BigDecimal("2").compareTo(descriptor?.count))
        assertEquals(0, BigDecimal("70").compareTo(descriptor?.weightOrVolume?.amount))
        assertEquals(NutritionBasis.PER_100_G, descriptor?.weightOrVolume?.basis)
    }

    @Test
    fun `parseDescriptor divides a multi-count weight down to one unit`() {
        val descriptor = ServingSizeParser.parseDescriptor("2 slices (70 g)")

        assertEquals(0, BigDecimal("35").compareTo(descriptor?.amountPerUnit?.amount))
        assertEquals(NutritionBasis.PER_100_G, descriptor?.amountPerUnit?.basis)
    }

    @Test
    fun `parseDescriptor returns null amountPerUnit when there is no weight`() {
        assertNull(ServingSizeParser.parseDescriptor("1 sachet")?.amountPerUnit)
    }

    @Test
    fun `parseDescriptor defaults a bare unit word to a count of one`() {
        val descriptor = ServingSizeParser.parseDescriptor("slice")

        assertEquals(PortionUnitKind.SLICE, descriptor?.kind)
        assertEquals(0, BigDecimal.ONE.compareTo(descriptor?.count))
    }

    @Test
    fun `parseDescriptor recognises dutch unit words`() {
        assertEquals(PortionUnitKind.SLICE, ServingSizeParser.parseDescriptor("2 sneetjes")?.kind)
        assertEquals(PortionUnitKind.SACHET, ServingSizeParser.parseDescriptor("1 zakje (15 g)")?.kind)
    }

    @Test
    fun `parseDescriptor rejects a bare weight with no unit word`() {
        assertNull(ServingSizeParser.parseDescriptor("30 g"))
        assertNull(ServingSizeParser.parseDescriptor("250 ml"))
    }

    @Test
    fun `parseDescriptor rejects an unrecognised unit word`() {
        assertNull(ServingSizeParser.parseDescriptor("2 blorps"))
    }

    @Test
    fun `parseDescriptor rejects a zero or negative count`() {
        assertNull(ServingSizeParser.parseDescriptor("0 slices (70 g)"))
    }

    @Test
    fun `parseDescriptor keeps the raw text for diagnostics`() {
        assertEquals("2 slices (70 g)", ServingSizeParser.parseDescriptor("  2 slices (70 g)  ")?.rawText)
    }

    @Test
    fun `parseDescriptor recognises a ml serving`() {
        val descriptor = ServingSizeParser.parseDescriptor("1 scoop (30 ml)")

        assertEquals(NutritionBasis.PER_100_ML, descriptor?.weightOrVolume?.basis)
    }
```

Ensure the file imports `org.junit.Assert.assertNull` (add it if missing).

- [ ] **Step 2: Run to verify it fails**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.domain.ServingSizeParserTest"
```

Expected: compilation FAILS — `parseDescriptor`, `ServingDescriptor` and `AmountWithBasis` do not exist.

- [ ] **Step 3: Create `ServingDescriptor.kt`**

```kotlin
package app.justthecarbs.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** A quantity together with the unit it is measured in. Never converted between g and ml. */
data class AmountWithBasis(val amount: BigDecimal, val basis: NutritionBasis)

/**
 * What a serving-size string *says*, separated from what it lets the app *calculate*.
 *
 * Splitting these apart is what makes direct-carb portions possible: "2 slices" is a perfectly good
 * descriptor even though it carries no weight, and a per-serving carbohydrate figure can then supply
 * the missing relationship. The old parser fused the two and returned null for any string without a
 * weight, which is why a user with a countable product and no printed weight was made to fetch a
 * kitchen scale.
 */
data class ServingDescriptor(
    val kind: PortionUnitKind,
    /** How many units the serving covers. 1 for a bare "1 slice"/"slice". Never inferred above 1. */
    val count: BigDecimal,
    /** Null when no weight/volume was printed. Never a sentinel, never estimated. */
    val weightOrVolume: AmountWithBasis?,
    val rawText: String,
) {
    /**
     * The weight of a single unit, or null when no weight was printed.
     *
     * "2 slices (70 g)" describes 35 g per slice — the app never stores "1 slice = 70 g".
     */
    val amountPerUnit: AmountWithBasis?
        get() = weightOrVolume?.let {
            AmountWithBasis(
                amount = it.amount.divide(count, SCALE, RoundingMode.HALF_UP).stripTrailingZeros(),
                basis = it.basis,
            )
        }

    private companion object {
        /** Matches the scale the previous per-unit division used. */
        const val SCALE = 4
    }
}
```

- [ ] **Step 4: Rewrite `ServingSizeParser.kt`**

Keep the class KDoc's existing English/Dutch note verbatim, and keep `ParsedServingSize` and `parse` so no caller breaks. Replace the body:

```kotlin
object ServingSizeParser {

    /** count + unit word, with an optional bracketed weight. The weight group is now optional. */
    private val DESCRIPTOR = Regex(
        """^\s*(?:(\d+(?:[.,]\d+)?)\s*)?(\p{L}+)\s*(?:[(,=]?\s*(\d+(?:[.,]\d+)?)\s*(g|ml)\)?\s*)?$""",
        RegexOption.IGNORE_CASE,
    )

    private val UNIT_WORDS: Map<String, PortionUnitKind> = mapOf(
        // English
        "slice" to PortionUnitKind.SLICE, "slices" to PortionUnitKind.SLICE,
        "piece" to PortionUnitKind.PIECE, "pieces" to PortionUnitKind.PIECE,
        "biscuit" to PortionUnitKind.BISCUIT, "biscuits" to PortionUnitKind.BISCUIT,
        "cookie" to PortionUnitKind.COOKIE, "cookies" to PortionUnitKind.COOKIE,
        "bar" to PortionUnitKind.BAR, "bars" to PortionUnitKind.BAR,
        "roll" to PortionUnitKind.ROLL, "rolls" to PortionUnitKind.ROLL,
        "scoop" to PortionUnitKind.SCOOP, "scoops" to PortionUnitKind.SCOOP,
        "sachet" to PortionUnitKind.SACHET, "sachets" to PortionUnitKind.SACHET,
        "serving" to PortionUnitKind.SERVING, "servings" to PortionUnitKind.SERVING,
        "portion" to PortionUnitKind.SERVING, "portions" to PortionUnitKind.SERVING,
        // Dutch — input recognition only, see class doc.
        "sneetje" to PortionUnitKind.SLICE, "sneetjes" to PortionUnitKind.SLICE,
        "stuk" to PortionUnitKind.PIECE, "stuks" to PortionUnitKind.PIECE,
        "koekje" to PortionUnitKind.COOKIE, "koekjes" to PortionUnitKind.COOKIE,
        "reep" to PortionUnitKind.BAR, "repen" to PortionUnitKind.BAR,
        "bolletje" to PortionUnitKind.ROLL, "bolletjes" to PortionUnitKind.ROLL,
        "broodje" to PortionUnitKind.ROLL, "broodjes" to PortionUnitKind.ROLL,
        "schepje" to PortionUnitKind.SCOOP, "schepjes" to PortionUnitKind.SCOOP,
        "zakje" to PortionUnitKind.SACHET, "zakjes" to PortionUnitKind.SACHET,
        "portie" to PortionUnitKind.SERVING, "porties" to PortionUnitKind.SERVING,
    )

    /** Recognises a unit word in isolation — used by the OCR column-header path (spec §4). */
    internal fun kindForWord(word: String): PortionUnitKind? = UNIT_WORDS[word.lowercase().trim()]

    /**
     * What the string describes, weight optional.
     *
     * A bare weight ("30 g") is still rejected: it names no countable unit, so there is nothing to
     * count. An unrecognised word is rejected rather than guessed — a false unit mapping is worse
     * than no mapping.
     */
    fun parseDescriptor(rawServingSize: String?): ServingDescriptor? {
        val raw = rawServingSize?.trim().orEmpty()
        val match = DESCRIPTOR.find(raw) ?: return null
        val countText = match.groupValues[1]
        val word = match.groupValues[2]
        val weightText = match.groupValues[3]
        val unit = match.groupValues[4]

        val kind = kindForWord(word) ?: return null
        val count = if (countText.isEmpty()) BigDecimal.ONE else PortionParser.parse(countText) ?: return null
        if (count.signum() <= 0) return null

        val weightOrVolume = if (weightText.isEmpty()) {
            null
        } else {
            val weight = PortionParser.parse(weightText) ?: return null
            if (weight.signum() <= 0) return null
            AmountWithBasis(
                amount = weight,
                basis = if (unit.equals("ml", ignoreCase = true)) {
                    NutritionBasis.PER_100_ML
                } else {
                    NutritionBasis.PER_100_G
                },
            )
        }

        return ServingDescriptor(kind = kind, count = count, weightOrVolume = weightOrVolume, rawText = raw)
    }

    /**
     * The weight-backed mapping only. Unchanged contract: a string with no printed weight still
     * returns null here, because this function's whole promise is a weight relationship. Callers
     * that can work without one use [parseDescriptor].
     */
    fun parse(rawServingSize: String?): ParsedServingSize? {
        val descriptor = parseDescriptor(rawServingSize) ?: return null
        val perUnit = descriptor.amountPerUnit ?: return null
        return ParsedServingSize(kind = descriptor.kind, amountPerUnit = perUnit.amount, basis = perUnit.basis)
    }
}
```

- [ ] **Step 5: Run to verify it passes**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.domain.ServingSizeParserTest"
```

Expected: all tests PASS, **including** every pre-existing one — `rejects a count and unit word with no weight at all` still passes because `parse` still demands a weight.

One pre-existing case needs checking: if a test asserts `parse("30 g") == null`, it still passes (no unit word). If any test asserts `parse("slice") == null` it also still passes (no weight). Run the whole suite and confirm zero regressions before moving on.

---

## Task 7: `NutritionTableInterpreter` + parser adapter (spec §4, §18)

The core of the OCR fix. Replaces the scoring model's row/column reasoning with the classified table.

**Files:**
- Create: `app/src/main/kotlin/app/justthecarbs/ocr/NutritionTableInterpreter.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/ocr/NutritionTableParser.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/ocr/NutritionTableInterpreterTest.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/ocr/NutritionLabelParserTest.kt` (existing — must still pass)

**Interfaces:**
- Consumes: `LogicalRowBuilder`, `RowClassifier`, `ColumnClassifier`, `NutritionTerminology`, `ServingSizeParser.kindForWord` + `ServingDescriptor` (Tasks 3–6); `NutritionValueValidator`, `NutritionBasis`.
- Produces:
  ```kotlin
  data class ServingCarbCandidate(
      val carbsPerServing: BigDecimal,
      val descriptor: ServingDescriptor?,
      val rawHeaderText: String,
  )
  object NutritionTableInterpreter { fun interpret(document: OcrDocument): NutritionParseReport }
  // NutritionParseReport gains: val servingCandidate: ServingCarbCandidate? = null
  ```
  Tasks 8 and 14 consume `servingCandidate`.

`LabelReading`, `CarbCandidate`, `CandidateEvidence`, `OcrDiagnostic` keep their current shapes, so `LabelAnalyzer` and `AmbiguityStabilityTracker` keep working untouched.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/app/justthecarbs/ocr/NutritionTableInterpreterTest.kt`. These are spec §18's seven adversarial cases:

```kotlin
package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionUnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

// Suite: geometry-first table interpretation
// Invariant: the TOTAL carbohydrate row's grams cell wins. A child nutrient's value can never be
// returned, however close it sits to the word "Carbohydrate", and a percent cell is never grams.
// Every fixture here has deliberately adversarial blockId/lineId vs. real geometry.
class NutritionTableInterpreterTest {

    private fun e(text: String, left: Int, top: Int, right: Int, bottom: Int, line: Int, block: Int = 0) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = block, lineId = line)

    private fun document(vararg elements: OcrElement): OcrDocument =
        OcrDocument(width = 800, height = 600, elements = elements.toList())

    private fun assertDecimal(expected: String, actual: BigDecimal?) {
        assertNotNull(actual)
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }

    private fun confidentValue(document: OcrDocument): BigDecimal {
        val reading = NutritionTableInterpreter.interpret(document).reading
        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        return (reading as LabelReading.Confident).candidate.value
    }

    @Test
    fun `same physical row split across ML Kit lines still reads the total`() {
        val value = confidentValue(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                // Label and value on the same baseline but different ML Kit lines and blocks.
                e("Carbohydrate", 40, 200, 240, 230, line = 1, block = 0),
                e("45", 350, 202, 390, 232, line = 9, block = 4),
                e("g", 395, 202, 410, 232, line = 9, block = 4),
            ),
        )

        assertDecimal("45", value)
    }

    @Test
    fun `different physical rows merged onto one ML Kit line still separate`() {
        val value = confidentValue(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                // ML Kit put all four on line 3. Geometry says two printed rows.
                e("Carbohydrate", 40, 200, 240, 230, line = 3),
                e("45", 350, 200, 390, 230, line = 3),
                e("of which sugars", 60, 260, 260, 290, line = 3),
                e("8", 350, 260, 390, 290, line = 3),
            ),
        )

        assertDecimal("45", value)
    }

    @Test
    fun `a sugars value physically closer to the carbohydrate label never wins`() {
        // The sugars cell is nearer the word "Carbohydrate" than the real total is. Row typing
        // decides before any proximity is measured, so proximity cannot rescue a child value.
        val value = confidentValue(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 235, line = 1),
                e("45", 350, 200, 390, 235, line = 1),
                e("of which sugars", 60, 240, 260, 268, line = 2),
                e("8", 350, 240, 390, 268, line = 2),
            ),
        )

        assertDecimal("45", value)
    }

    @Test
    fun `a dextrose child row is excluded and the total wins`() {
        val value = confidentValue(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("62", 350, 200, 390, 230, line = 1),
                e("Dextrose", 60, 260, 200, 290, line = 2),
                e("31", 350, 260, 390, 290, line = 2),
            ),
        )

        assertDecimal("62", value)
    }

    @Test
    fun `a two column table yields both the per-100 total and the serving figure`() {
        val report = NutritionTableInterpreter.interpret(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("per", 550, 100, 590, 130, line = 0),
                e("slice", 595, 100, 650, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("45", 350, 200, 390, 230, line = 1),
                e("16.2", 580, 200, 640, 230, line = 1),
            ),
        )

        assertTrue("a two-column table is not ambiguous", report.reading is LabelReading.Confident)
        assertDecimal("45", (report.reading as LabelReading.Confident).candidate.value)
        assertDecimal("16.2", report.servingCandidate?.carbsPerServing)
        assertEquals(PortionUnitKind.SLICE, report.servingCandidate?.descriptor?.kind)
    }

    @Test
    fun `a percent column is never selected as carbohydrate grams`() {
        val value = confidentValue(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("%RI", 620, 100, 670, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("45", 350, 200, 390, 230, line = 1),
                e("17", 630, 200, 670, 230, line = 1),
                e("Fat", 40, 260, 100, 290, line = 2),
                e("1.5", 350, 260, 390, 290, line = 2),
                e("2", 630, 260, 670, 290, line = 2),
            ),
        )

        assertDecimal("45", value)
    }

    @Test
    fun `a genuinely unresolvable layout is ambiguous rather than guessed`() {
        // Two per-100-g-aligned cells on the total row with nothing to separate them.
        val reading = NutritionTableInterpreter.interpret(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("45", 330, 200, 370, 230, line = 1),
                e("51", 380, 200, 420, 230, line = 1),
            ),
        ).reading

        assertTrue("expected Ambiguous, got $reading", reading is LabelReading.Ambiguous)
        val values = (reading as LabelReading.Ambiguous).candidates.map { it.value.toPlainString() }
        assertTrue(values.any { BigDecimal(it).compareTo(BigDecimal("45")) == 0 })
        assertTrue(values.any { BigDecimal(it).compareTo(BigDecimal("51")) == 0 })
    }

    @Test
    fun `no carbohydrate row at all is NotFound`() {
        val reading = NutritionTableInterpreter.interpret(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("Protein", 40, 200, 140, 230, line = 1),
                e("7.2", 350, 200, 390, 230, line = 1),
            ),
        ).reading

        assertEquals(LabelReading.NotFound, reading)
    }

    @Test
    fun `a per 100 ml table sets the ml basis`() {
        val reading = NutritionTableInterpreter.interpret(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("ml", 390, 100, 415, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("9.4", 350, 200, 400, 230, line = 1),
            ),
        ).reading

        assertTrue(reading is LabelReading.Confident)
        assertEquals(NutritionBasis.PER_100_ML, (reading as LabelReading.Confident).candidate.basis)
    }

    @Test
    fun `a serving column with no countable word yields a candidate with no descriptor`() {
        val report = NutritionTableInterpreter.interpret(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("per", 550, 100, 590, 130, line = 0),
                e("serving", 595, 100, 680, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("45", 350, 200, 390, 230, line = 1),
                e("16.2", 580, 200, 640, 230, line = 1),
            ),
        )

        assertDecimal("16.2", report.servingCandidate?.carbsPerServing)
        assertNull("'per serving' names no countable unit", report.servingCandidate?.descriptor)
    }

    @Test
    fun `a per-2-slices header keeps the explicit count`() {
        val report = NutritionTableInterpreter.interpret(
            document(
                e("per", 300, 100, 340, 130, line = 0),
                e("100", 345, 100, 385, 130, line = 0),
                e("g", 390, 100, 405, 130, line = 0),
                e("per", 550, 100, 590, 130, line = 0),
                e("2", 595, 100, 615, 130, line = 0),
                e("slices", 620, 100, 690, 130, line = 0),
                e("Carbohydrate", 40, 200, 240, 230, line = 1),
                e("45", 350, 200, 390, 230, line = 1),
                e("32.4", 600, 200, 660, 230, line = 1),
            ),
        )

        assertDecimal("32.4", report.servingCandidate?.carbsPerServing)
        assertDecimal("2", report.servingCandidate?.descriptor?.count)
        assertEquals(PortionUnitKind.SLICE, report.servingCandidate?.descriptor?.kind)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.NutritionTableInterpreterTest"
```

Expected: compilation FAILS — `NutritionTableInterpreter` and `ServingCarbCandidate` do not exist, and `NutritionParseReport` has no `servingCandidate`.

- [ ] **Step 3: Implement `NutritionTableInterpreter.kt`**

```kotlin
package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.NutritionValueValidator
import app.justthecarbs.domain.ServingDescriptor
import app.justthecarbs.domain.ServingSizeParser
import java.math.BigDecimal
import kotlin.math.abs

/**
 * A per-serving carbohydrate figure read from a serving column (spec §5).
 *
 * [descriptor] is typed at parse time so no later stage re-parses [rawHeaderText]: the save flow
 * reads `descriptor.count` directly. Null when the column is a valid per-serving column that names
 * no countable unit ("per serving"), which is a real and common shape — the figure is still useful,
 * there is just nothing countable to attach it to.
 */
data class ServingCarbCandidate(
    val carbsPerServing: BigDecimal,
    val descriptor: ServingDescriptor?,
    val rawHeaderText: String,
)

/**
 * Reads a nutrition table by reconstructing it, rather than by scoring proximity.
 *
 * The pipeline is: geometry-first rows ([LogicalRowBuilder]) -> row types ([RowClassifier]) ->
 * columns ([ColumnClassifier]) -> cells associated to columns. A child nutrient is eliminated at the
 * row-typing stage, before any number is looked at, so it cannot appear even in an ambiguity set.
 * A cell in a percent or unknown column is refused outright.
 */
object NutritionTableInterpreter {

    private val NUMBER = Regex("(?<!\\d)(\\d{1,3}(?:[.,]\\d{1,3})?)(?!\\d)")

    fun interpret(document: OcrDocument): NutritionParseReport {
        val diagnostics = mutableListOf<OcrDiagnostic>()
        val rows = LogicalRowBuilder.build(document)
        rows.forEach { diagnostics += OcrDiagnostic("row", "${it.text} @ ${it.box}") }

        val typed = rows.map { it to RowClassifier.classify(it) }
        typed.forEach { (row, kind) -> diagnostics += OcrDiagnostic("row-kind", "${kind.name}: ${row.text}") }

        val totalRows = typed.filter { it.second == NutritionRowKind.TOTAL_CARBOHYDRATE }.map { it.first }
        if (totalRows.isEmpty()) {
            diagnostics += OcrDiagnostic("result", "No total-carbohydrate row")
            return NutritionParseReport(LabelReading.NotFound, diagnostics, null)
        }

        val columns = ColumnClassifier.classify(rows, document.width)
        columns.forEach {
            diagnostics += OcrDiagnostic("column", "${it.kind.name} '${it.headerText}' @ x=${it.centerX}")
        }

        val totalRow = totalRows.first()
        if (totalRows.size > 1) {
            diagnostics += OcrDiagnostic("warning", "${totalRows.size} total-carbohydrate rows; using the first")
        }

        val cells = numbersIn(totalRow)
        val perHundred = mutableListOf<Pair<BigDecimal, NutritionBasis>>()
        var serving: BigDecimal? = null
        var servingColumn: NutritionColumn? = null

        cells.forEach { cell ->
            val column = columnFor(cell, columns, document.width)
            if (column == null) {
                diagnostics += OcrDiagnostic("rejected", "${cell.value.toPlainString()}: no column")
                return@forEach
            }
            when (column.kind) {
                NutritionColumnKind.PER_100_G, NutritionColumnKind.PER_100_ML -> {
                    val basis = if (column.kind == NutritionColumnKind.PER_100_ML) {
                        NutritionBasis.PER_100_ML
                    } else {
                        NutritionBasis.PER_100_G
                    }
                    val validated = NutritionValueValidator.validateCarbsPer100(cell.value.toDouble(), basis)
                    if (validated == null) {
                        diagnostics += OcrDiagnostic(
                            "rejected",
                            "${cell.value.toPlainString()}: outside the possible per-100 range",
                        )
                    } else {
                        perHundred += validated to basis
                    }
                }
                NutritionColumnKind.PER_SERVING -> {
                    serving = cell.value
                    servingColumn = column
                }
                // Never carbohydrate grams. This is the whole reason UNKNOWN exists as a kind
                // rather than being treated as "probably per 100 g".
                NutritionColumnKind.REFERENCE_PERCENT, NutritionColumnKind.UNKNOWN ->
                    diagnostics += OcrDiagnostic(
                        "rejected",
                        "${cell.value.toPlainString()}: ${column.kind.name} column",
                    )
            }
        }

        val servingCandidate = serving?.let { value ->
            ServingCarbCandidate(
                carbsPerServing = value,
                descriptor = servingColumn?.headerText?.let(::descriptorFromHeader),
                rawHeaderText = servingColumn?.headerText.orEmpty(),
            )
        }

        val distinct = perHundred.distinctBy { it.first.stripTrailingZeros() to it.second }
        val reading = when {
            distinct.isEmpty() -> {
                diagnostics += OcrDiagnostic("result", "Total-carbohydrate row found but no usable per-100 cell")
                LabelReading.NotFound
            }
            distinct.size == 1 -> {
                val (value, basis) = distinct.single()
                diagnostics += OcrDiagnostic("selected", "${value.toPlainString()} ${basis.name}")
                LabelReading.Confident(candidate(totalRow, value, basis))
            }
            else -> {
                diagnostics += OcrDiagnostic("ambiguous", "${distinct.size} per-100 cells on the total row")
                LabelReading.Ambiguous(distinct.map { (value, basis) -> candidate(totalRow, value, basis) })
            }
        }

        return NutritionParseReport(reading, diagnostics, servingCandidate)
    }

    /**
     * Turns a serving column's header into a typed descriptor.
     *
     * A count above 1 is only ever taken from an explicit leading digit in the header ("per 2
     * slices"). It is never inferred — guessing that a serving covers more than one unit would
     * silently halve or double every result computed from it.
     */
    private fun descriptorFromHeader(headerText: String): ServingDescriptor? {
        val withoutPer = headerText.trim().removePrefix("per").removePrefix("Per").trim()
        return ServingSizeParser.parseDescriptor(withoutPer)
            // "per serving"/"per portion" parse to a SERVING descriptor, but they name no countable
            // thing the user would recognise as an item, so they carry no descriptor.
            ?.takeIf { !isGenericServingWord(withoutPer) }
    }

    private fun isGenericServingWord(text: String): Boolean {
        val normalized = NutritionTerminology.normalize(text)
        return normalized == "serving" || normalized == "portion" || normalized == "portie"
    }

    private fun candidate(row: LogicalRow, value: BigDecimal, basis: NutritionBasis) = CarbCandidate(
        sourceLine = row.text,
        label = row.elements.firstOrNull()?.text.orEmpty().replaceFirstChar { it.uppercase() },
        value = value,
        basis = basis,
        score = NutritionParserThresholds.CONFIDENT_SCORE,
        geometry = row.box,
        evidence = listOf(
            CandidateEvidence("total-carbohydrate row", NutritionParserThresholds.CARBOHYDRATE_ANCHOR),
            CandidateEvidence("${basis.name} column", NutritionParserThresholds.PER_100_HEADER),
            CandidateEvidence("column-resolved cell", NutritionParserThresholds.STRONG_COLUMN_ALIGNMENT),
        ),
    )

    /** Nearest column within the loose fraction; null when the cell aligns to nothing. */
    private fun columnFor(cell: NumberCell, columns: List<NutritionColumn>, documentWidth: Int): NutritionColumn? {
        if (columns.isEmpty()) return null
        val loose = maxOf(
            NutritionParserThresholds.MIN_STRICT_COLUMN_PIXELS,
            documentWidth * NutritionParserThresholds.LOOSE_COLUMN_FRACTION,
        )
        return columns
            .map { it to abs(it.centerX - cell.box.centerX) }
            .filter { it.second <= loose }
            .minByOrNull { it.second }
            ?.first
    }

    private fun numbersIn(row: LogicalRow): List<NumberCell> = buildList {
        row.elements.forEach { element ->
            // A cell carrying a percent sign is a percentage wherever it sits. Skipping it here as
            // well as at the column stage means a stray percent cell in a grams column cannot leak.
            if (element.text.contains('%')) return@forEach
            NUMBER.findAll(element.text).forEach { match ->
                val value = match.groupValues[1].replace(',', '.').toBigDecimalOrNull() ?: return@forEach
                add(NumberCell(value, element.box))
            }
        }
    }

    private data class NumberCell(val value: BigDecimal, val box: OcrBox)
}
```

- [ ] **Step 4: Update `NutritionParseReport` and make `NutritionTableParser` an adapter**

In `NutritionTableParser.kt`, change the report type (adding a defaulted third field, so existing constructor calls keep compiling):

```kotlin
data class NutritionParseReport(
    val reading: LabelReading,
    val diagnostics: List<OcrDiagnostic>,
    /** A per-serving figure read alongside the canonical per-100 result (spec §5). Never gates live scanning. */
    val servingCandidate: ServingCarbCandidate? = null,
)
```

Then replace `parseWithDiagnostics`'s body with a delegation, and delete the now-unused private helpers (`findAnchor`, `findHeaders`, `findNumbers`, `projectBox`, `hasGramUnit`, `hasAdjacentGramUnit`, `rowEvidence`, `columnEvidence`, `unresolvedBasisOrNotFound`, and the private `LineKey`/`OcrLine`/`TextSpan`/`Anchor`/`ColumnHeader`/`NumberElement`/`ColumnMatch`/`ScoredCandidate` types, plus the `PER_100`/`NUMBER` regexes and the `kotlin.math.abs` import):

```kotlin
/**
 * Spatial nutrition-table parser. Contains no Android or ML Kit types.
 *
 * Now a thin adapter over [NutritionTableInterpreter], which reconstructs the printed table from
 * geometry instead of scoring proximity against ML Kit's own line grouping. The public contract
 * ([LabelReading], [CarbCandidate], [NutritionParseReport]) is unchanged, so `LabelAnalyzer` and
 * `AmbiguityStabilityTracker` are unaffected.
 */
object NutritionTableParser {

    fun parse(document: OcrDocument): LabelReading = parseWithDiagnostics(document).reading

    fun parseWithDiagnostics(document: OcrDocument): NutritionParseReport =
        NutritionTableInterpreter.interpret(document)
}
```

Keep `NutritionParserThresholds`, `CandidateEvidence`, `CarbCandidate`, `LabelReading` and `OcrDiagnostic` in this file exactly as they are — the interpreter references the thresholds.

- [ ] **Step 5: Run the interpreter tests**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.NutritionTableInterpreterTest"
```

Expected: all 11 tests PASS.

- [ ] **Step 6: Run the whole OCR suite and reconcile the existing parser tests**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.*"
```

Expected: `AmbiguityStabilityTrackerTest` PASSES untouched (it only builds `LabelReading` values directly).

`NutritionLabelParserTest` is the real checkpoint. Its fixtures were written with `blockId`/`lineId` already agreeing with geometry, so they should pass unchanged. Where one fails, decide which of these it is before changing anything:

- **A fixture that relied on the scoring model's tolerance** (e.g. a value with no header at all that used to arrive via `unresolvedBasisOrNotFound` as `Ambiguous` with `basis = null`). The interpreter returns `NotFound` for these instead — a deliberate, spec-driven tightening: a value with no established basis is not a reading. Update the test's expectation and add a one-line comment saying why.
- **A genuine regression** (a well-formed table that used to read correctly and now does not). Fix the interpreter, not the test.

Record every changed expectation — the final report (§25) must list them.

---

## Task 8: `PortionConversion` + `DirectCarbCalculator` (spec §8, §13)

**Files:**
- Create: `app/src/main/kotlin/app/justthecarbs/domain/PortionConversion.kt`
- Create: `app/src/main/kotlin/app/justthecarbs/domain/DirectCarbCalculator.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/domain/DirectCarbCalculatorTest.kt`

**Interfaces:**
- Consumes: `NutritionBasis`.
- Produces:
  ```kotlin
  sealed interface PortionConversion {
      data class WeightBased(val amountPerUnit: BigDecimal, val basis: NutritionBasis) : PortionConversion
      data class DirectCarbs(val carbsPerUnit: BigDecimal) : PortionConversion
  }
  object DirectCarbCalculator { fun exactCarbs(count: BigDecimal, carbsPerUnit: BigDecimal): BigDecimal }
  ```
  Tasks 9–15 consume both.

`DirectCarbCalculator` returns a plain `BigDecimal`, **not** a `CarbResult`. `CarbResult.basis` is non-null and means "per 100 g/ml" — a direct-carb result has no such basis, and widening `CarbResult` to accommodate it would put a null basis into the weight-based path that has never needed one. The UI formats the returned decimal with `ResultFormatter.decimal`/`whole`, which already take raw values.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/app/justthecarbs/domain/DirectCarbCalculatorTest.kt`:

```kotlin
package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

// Suite: direct-carb calculation
// Invariant: count x carbsPerUnit, exact, with no intermediate rounding and no grams anywhere.
// This is the only place that multiplication happens, mirroring PortionResolver's role for weights.
class DirectCarbCalculatorTest {

    private fun assertDecimal(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }

    @Test
    fun `four slices at 14 point 2 g carbs each`() {
        assertDecimal("56.8", DirectCarbCalculator.exactCarbs(BigDecimal("4"), BigDecimal("14.2")))
    }

    @Test
    fun `a single unit returns the per-unit value unchanged`() {
        assertDecimal("12.6", DirectCarbCalculator.exactCarbs(BigDecimal.ONE, BigDecimal("12.6")))
    }

    @Test
    fun `a fractional count is supported`() {
        assertDecimal("7.1", DirectCarbCalculator.exactCarbs(BigDecimal("0.5"), BigDecimal("14.2")))
    }

    @Test
    fun `zero count is zero carbs`() {
        assertDecimal("0", DirectCarbCalculator.exactCarbs(BigDecimal.ZERO, BigDecimal("14.2")))
    }

    @Test
    fun `the result is exact and not pre-rounded`() {
        // 3 x 4.567 = 13.701. Rounding here would make a meal total disagree with its own lines.
        assertDecimal("13.701", DirectCarbCalculator.exactCarbs(BigDecimal("3"), BigDecimal("4.567")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative count is rejected`() {
        DirectCarbCalculator.exactCarbs(BigDecimal("-1"), BigDecimal("14.2"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative per-unit value is rejected`() {
        DirectCarbCalculator.exactCarbs(BigDecimal.ONE, BigDecimal("-14.2"))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.domain.DirectCarbCalculatorTest"
```

Expected: compilation FAILS — `DirectCarbCalculator` does not exist.

- [ ] **Step 3: Create `PortionConversion.kt`**

```kotlin
package app.justthecarbs.domain

import java.math.BigDecimal

/**
 * How one countable unit becomes a carbohydrate figure (spec §8).
 *
 * Two legitimate shapes, made unrepresentable in any other combination:
 *
 * - [WeightBased] — "1 slice = 35 g", the app's original and preferred form. Resolves to grams via
 *   [PortionResolver] and then to carbs via [CarbCalculator], keeping one formula.
 * - [DirectCarbs] — "1 slice = 14.2 g of carbohydrate", used when the source gives per-serving carbs
 *   but no weight. There is no gram figure at all on this path and none is invented; asking the user
 *   to weigh bread the label already describes was the failure this exists to fix.
 *
 * A sealed interface rather than a nullable-field data class so that "weight-based with a null
 * weight" cannot be constructed. Room stores the discriminant plus value/basis columns, which is the
 * one place the two shapes are flattened — see `PortionUnitEntity`.
 */
sealed interface PortionConversion {

    data class WeightBased(val amountPerUnit: BigDecimal, val basis: NutritionBasis) : PortionConversion {
        init {
            require(amountPerUnit.signum() > 0) { "amountPerUnit must be positive" }
        }
    }

    data class DirectCarbs(val carbsPerUnit: BigDecimal) : PortionConversion {
        init {
            require(carbsPerUnit.signum() >= 0) { "carbsPerUnit must not be negative" }
        }
    }
}
```

- [ ] **Step 4: Create `DirectCarbCalculator.kt`**

```kotlin
package app.justthecarbs.domain

import java.math.BigDecimal

/**
 * The carbohydrate total for a direct-carb countable portion: `count x carbsPerUnit` (spec §13).
 *
 * The direct-carb counterpart to [PortionResolver], and the only place this multiplication happens.
 * Unlike [PortionResolver] — which converts to grams and hands off to [CarbCalculator] — this path
 * has no per-100 basis to go through, so it produces the carbohydrate figure itself. That does not
 * make it a second formula for the same thing: no weight exists here for [CarbCalculator] to work
 * from, which is precisely why this path exists.
 *
 * Returns a plain [BigDecimal] rather than a [CarbResult] because [CarbResult.basis] is non-null and
 * means "per 100 g/ml" — a claim a direct-carb result cannot make. The UI formats this value with
 * [ResultFormatter.decimal]/[ResultFormatter.whole], which take raw values.
 *
 * Exact, never pre-rounded: display rounding belongs to [ResultFormatter] alone, and rounding here
 * would let a meal total disagree with the lines it is the sum of.
 */
object DirectCarbCalculator {

    fun exactCarbs(count: BigDecimal, carbsPerUnit: BigDecimal): BigDecimal {
        require(count.signum() >= 0) { "count must not be negative" }
        require(carbsPerUnit.signum() >= 0) { "carbsPerUnit must not be negative" }

        return count.multiply(carbsPerUnit)
    }
}
```

- [ ] **Step 5: Run to verify it passes**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.domain.DirectCarbCalculatorTest"
```

Expected: all 7 tests PASS.

---

## Task 9: `PortionUnit.conversion` + `PortionUnitCandidate` (spec §8, §10)

A wide, mechanical change: every construction site of `PortionUnit` moves from `amountPerUnit`/`basis` to `conversion`. Do it in one task so the tree compiles again at the end.

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/domain/PortionUnit.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/domain/ProductDataSource.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/domain/PortionConversionTest.kt` (create)

**Interfaces:**
- Consumes: `PortionConversion` (Task 8).
- Produces:
  ```kotlin
  data class PortionUnit(
      val id: Long = 0,
      val productBarcode: String,
      val kind: PortionUnitKind,
      val customLabel: String?,
      val conversion: PortionConversion,
      val dataSource: ProductDataOrigin,
      val verificationStatus: VerificationStatus,
      val verifiedAt: Instant?,
      val originalRemoteConversion: PortionConversion?,
      val latestRemoteConversion: PortionConversion?,
      val rawRemoteServingText: String?,
      val createdAt: Instant,
      val updatedAt: Instant,
  ) {
      val isRemoteRefreshable: Boolean
      val remoteConversionDiffers: Boolean
  }
  data class PortionUnitCandidate(
      val kind: PortionUnitKind,
      val conversion: PortionConversion,
      val rawServingText: String,
  )
  ```
  Tasks 10–15 consume these.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/app/justthecarbs/domain/PortionConversionTest.kt`:

```kotlin
package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

// Suite: portion-unit conversion invariants
// Invariant: the freeze rule (a user-authored or user-verified unit is never silently overwritten by
// a refresh) is identical for both conversion kinds — it keys on provenance and verification, never
// on what kind of number the unit holds.
class PortionConversionTest {

    private fun unit(
        conversion: PortionConversion,
        dataSource: ProductDataOrigin = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
        latestRemoteConversion: PortionConversion? = null,
    ) = PortionUnit(
        productBarcode = "111",
        kind = PortionUnitKind.SLICE,
        customLabel = null,
        conversion = conversion,
        dataSource = dataSource,
        verificationStatus = verificationStatus,
        verifiedAt = null,
        originalRemoteConversion = null,
        latestRemoteConversion = latestRemoteConversion,
        rawRemoteServingText = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private val weight = PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G)
    private val direct = PortionConversion.DirectCarbs(BigDecimal("14.2"))

    @Test
    fun `an unverified remote weight unit is refreshable`() {
        assertTrue(unit(weight).isRemoteRefreshable)
    }

    @Test
    fun `an unverified remote direct-carb unit is refreshable on the same rule`() {
        assertTrue(unit(direct).isRemoteRefreshable)
    }

    @Test
    fun `a verified unit is frozen whichever conversion it holds`() {
        assertFalse(unit(weight, verificationStatus = VerificationStatus.USER_VERIFIED).isRemoteRefreshable)
        assertFalse(unit(direct, verificationStatus = VerificationStatus.USER_VERIFIED).isRemoteRefreshable)
    }

    @Test
    fun `a user-authored unit is frozen whichever conversion it holds`() {
        assertFalse(unit(weight, dataSource = ProductDataOrigin.MANUAL).isRemoteRefreshable)
        assertFalse(unit(direct, dataSource = ProductDataOrigin.MANUAL).isRemoteRefreshable)
    }

    @Test
    fun `a differing latest remote conversion is detected`() {
        val changed = unit(
            weight,
            latestRemoteConversion = PortionConversion.WeightBased(BigDecimal("40"), NutritionBasis.PER_100_G),
        )
        assertTrue(changed.remoteConversionDiffers)
    }

    @Test
    fun `an identical latest remote conversion does not differ`() {
        val same = unit(
            weight,
            latestRemoteConversion = PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G),
        )
        assertFalse(same.remoteConversionDiffers)
    }

    @Test
    fun `a conversion that changed kind counts as differing`() {
        assertTrue(unit(weight, latestRemoteConversion = direct).remoteConversionDiffers)
    }

    @Test
    fun `no latest remote conversion means nothing differs`() {
        assertFalse(unit(weight).remoteConversionDiffers)
    }

    @Test
    fun `a weight conversion must be positive`() {
        val failed = runCatching {
            PortionConversion.WeightBased(BigDecimal.ZERO, NutritionBasis.PER_100_G)
        }.isFailure
        assertTrue("a zero-gram slice is not a portion", failed)
    }

    @Test
    fun `a zero-carb unit is allowed`() {
        // A sugar-free sachet genuinely contains 0 g of carbohydrate. Rejecting it would be wrong.
        assertEquals(BigDecimal.ZERO, PortionConversion.DirectCarbs(BigDecimal.ZERO).carbsPerUnit)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.domain.PortionConversionTest"
```

Expected: compilation FAILS — `PortionUnit` has no `conversion` parameter.

- [ ] **Step 3: Rewrite `PortionUnit.kt`**

```kotlin
package app.justthecarbs.domain

import java.time.Instant

/**
 * A countable portion for one product — "1 slice = 36 g", or "1 slice = 14.2 g carbs"
 * (countable-portions brief §2, spec §8).
 *
 * Mirrors [Product]'s provenance/verification split exactly, and for the same reason: a unit can
 * arrive from Open Food Facts and later be verified by the user without collapsing those two facts
 * into one (owner's standing correction — see [ProductDataOrigin]/[VerificationStatus]).
 *
 * What the unit knows is held in [conversion], so a unit that only knows carbohydrate-per-item is a
 * first-class shape rather than a weight-based unit with a fabricated weight.
 */
data class PortionUnit(
    val id: Long = 0,
    val productBarcode: String,
    val kind: PortionUnitKind,
    /** Required iff [kind] is [PortionUnitKind.CUSTOM]; user text, never translated. */
    val customLabel: String?,
    /** The effective/verified conversion used in calculations. Never changed by a refresh alone. */
    val conversion: PortionConversion,
    val dataSource: ProductDataOrigin,
    val verificationStatus: VerificationStatus,
    val verifiedAt: Instant?,
    /** The first remote conversion ever seen for this unit. Immutable once set. */
    val originalRemoteConversion: PortionConversion?,
    /** The most recent remote conversion seen, recorded even when NOT applied (brief §7, §9). */
    val latestRemoteConversion: PortionConversion?,
    /** The raw OFF serving_size text this unit was parsed from, kept for diagnostics/re-parsing. */
    val rawRemoteServingText: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /**
     * Same rule as [Product.isRemoteRefreshable]: user-authored or user-verified units are frozen.
     * Deliberately independent of which [PortionConversion] the unit holds — the freeze protects the
     * user's judgement, and that is the same fact whether they confirmed a weight or a carb figure.
     */
    val isRemoteRefreshable: Boolean
        get() = !dataSource.isUserAuthored && verificationStatus == VerificationStatus.UNVERIFIED

    /**
     * Whether the provider now reports something different. A change of conversion *kind* counts:
     * moving from a carbs-per-item figure to a printed weight is a real change worth surfacing.
     *
     * Uses structural equality, which for [java.math.BigDecimal] inside a data class distinguishes
     * `35` from `35.0`. Amounts are normalized with `stripTrailingZeros()` before storage, so both
     * sides of this comparison are already in canonical form.
     */
    val remoteConversionDiffers: Boolean
        get() = latestRemoteConversion?.let { it != conversion } == true
}
```

- [ ] **Step 4: Update `PortionUnitCandidate` in `ProductDataSource.kt`**

```kotlin
/**
 * A countable unit a remote source suggested, not yet a stored [PortionUnit] (countable-portions
 * brief §7). The repository decides whether to create or update a [PortionUnit] from this — it
 * carries no id, provenance timestamps, or verification state, because those are storage concerns.
 */
data class PortionUnitCandidate(
    val kind: PortionUnitKind,
    val conversion: PortionConversion,
    val rawServingText: String,
)
```

The `java.math.BigDecimal` import in this file is now unused if nothing else references it — remove it only if the compiler flags it.

- [ ] **Step 5: Compile and fix every call site**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:compileDebugKotlin
```

Expected: FAILS with errors at each `PortionUnit(...)`/`PortionUnitCandidate(...)` construction and each `.amountPerUnit`/`.basis`/`.latestRemoteAmountPerUnit` read. Known sites — Tasks 10–15 revisit each properly, so here make only the mechanical change needed to compile:

- `data/local/PortionUnitEntity.kt` — mappers (Task 11 rewrites these; a temporary `PortionConversion.WeightBased(BigDecimal(amountPerUnit), NutritionBasis.valueOf(basis))` keeps it compiling)
- `data/ProductRepository.kt` — `newPortionUnitFromCandidate`, `saveUserPortionUnit`, `verifyPortionUnit`, `applyLatestRemotePortionUnit`, `refreshPortionUnitFromCandidate` (Task 12)
- `data/remote/OpenFoodFactsDataSource.kt` — the `servingSize` mapping (Task 13)
- `ui/product/ProductViewModel.kt` — `PortionResolver.resolve(count, unit.amountPerUnit)` in `onProductLoaded` and `recalculateFromCount`; `newerRemotePortionUnitAmount` (Task 14)
- `ui/product/ProductScreen.kt` — per-unit weight display (Task 15)
- Test files constructing `PortionUnit`/`PortionUnitCandidate`

- [ ] **Step 6: Run the new tests**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.domain.PortionConversionTest"
```

Expected: all 10 tests PASS.

---

## Task 10: `MealItem` kinds + serving-carb validation (spec §12)

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/domain/MealItem.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/domain/NutritionValueValidator.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/domain/MealItemTest.kt` (create if absent; otherwise extend)
- Test: `app/src/test/kotlin/app/justthecarbs/domain/NutritionValueValidatorTest.kt` (existing)

**Interfaces:**
- Consumes: `NutritionBasis`, `CarbResult`.
- Produces:
  ```kotlin
  enum class MealItemKind { WEIGHT_BASED, DIRECT_CARBS }
  // MealItem gains `kind`, `count`, `carbsPerUnit`; resolvedAmount/basis/carbsPer100 become nullable
  fun MealItem.Companion.weightBased(...): MealItem
  fun MealItem.Companion.directCarbs(...): MealItem
  object NutritionValueValidator { fun validateCarbsPerServing(raw: Double?): BigDecimal? }
  ```
  Tasks 11, 12 and 14 consume these.

`MealTotal.asResult` currently reads `items.firstOrNull()?.basis ?: PER_100_G`. Once `basis` is nullable that expression yields `null` from a **non-empty** list of direct-carb items, which no longer compiles into a non-null `CarbResult.basis`. It must fall back on a null basis too.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/app/justthecarbs/domain/MealItemTest.kt`:

```kotlin
package app.justthecarbs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

// Suite: meal items of both kinds
// Invariant: a DIRECT_CARBS item has NO resolved grams — not zero, not empty, null. Faking a weight
// would make "4 slices" indistinguishable from having weighed 140 g, which the app cannot know.
class MealItemTest {

    private fun weightItem(exact: String) = MealItem.weightBased(
        productBarcode = "111",
        displayName = "Bread",
        portionDescription = "72 g",
        resolvedAmount = BigDecimal("72"),
        basis = NutritionBasis.PER_100_G,
        carbsPer100 = BigDecimal("48.2"),
        exactCarbs = BigDecimal(exact),
        addedAt = Instant.EPOCH,
    )

    private fun directItem(exact: String) = MealItem.directCarbs(
        productBarcode = "222",
        displayName = "Crackers",
        portionDescription = "4 slices",
        count = BigDecimal("4"),
        carbsPerUnit = BigDecimal("14.2"),
        exactCarbs = BigDecimal(exact),
        addedAt = Instant.EPOCH,
    )

    @Test
    fun `a direct-carb item has no resolved grams`() {
        val item = directItem("56.8")

        assertEquals(MealItemKind.DIRECT_CARBS, item.kind)
        assertNull("no weight is known, so none may be recorded", item.resolvedAmount)
        assertNull(item.basis)
        assertNull(item.carbsPer100)
    }

    @Test
    fun `a direct-carb item keeps its count and per-unit value`() {
        val item = directItem("56.8")

        assertEquals(0, BigDecimal("4").compareTo(item.count))
        assertEquals(0, BigDecimal("14.2").compareTo(item.carbsPerUnit))
    }

    @Test
    fun `a weight-based item has no count or per-unit value`() {
        val item = weightItem("34.704")

        assertEquals(MealItemKind.WEIGHT_BASED, item.kind)
        assertNull(item.count)
        assertNull(item.carbsPerUnit)
    }

    @Test
    fun `a mixed meal totals both kinds exactly`() {
        val total = MealTotal.exact(listOf(weightItem("34.704"), directItem("56.8")))

        assertEquals(0, BigDecimal("91.504").compareTo(total))
    }

    @Test
    fun `a direct-carb-only meal still produces a result`() {
        // The list is non-empty but no item carries a basis. Before this change the fallback read
        // items.firstOrNull()?.basis, which is null here — a non-empty list with no basis at all.
        val result = MealTotal.asResult(listOf(directItem("56.8")))

        assertEquals(0, BigDecimal("56.8").compareTo(result.exact))
        assertEquals(NutritionBasis.PER_100_G, result.basis)
    }

    @Test
    fun `a mixed meal takes its label basis from the first item that has one`() {
        val result = MealTotal.asResult(listOf(directItem("56.8"), weightItem("34.704")))

        assertEquals(NutritionBasis.PER_100_G, result.basis)
    }

    @Test
    fun `an empty meal totals zero`() {
        assertEquals(0, BigDecimal.ZERO.compareTo(MealTotal.exact(emptyList())))
    }
}
```

Add to `NutritionValueValidatorTest.kt`:

```kotlin
    @Test
    fun `a serving carbohydrate figure above 100 is accepted`() {
        // A 500 g ready meal legitimately holds more than 100 g of carbohydrate. The per-100 ceiling
        // is arithmetic about a fixed 100 g; a serving has no such fixed size.
        assertEquals(
            0,
            java.math.BigDecimal("140.5").compareTo(NutritionValueValidator.validateCarbsPerServing(140.5)),
        )
    }

    @Test
    fun `a serving carbohydrate figure is rejected when clearly corrupt`() {
        assertNull(NutritionValueValidator.validateCarbsPerServing(50_000.0))
    }

    @Test
    fun `a negative or non-finite serving figure is rejected`() {
        assertNull(NutritionValueValidator.validateCarbsPerServing(-1.0))
        assertNull(NutritionValueValidator.validateCarbsPerServing(Double.NaN))
        assertNull(NutritionValueValidator.validateCarbsPerServing(Double.POSITIVE_INFINITY))
        assertNull(NutritionValueValidator.validateCarbsPerServing(null))
    }
```

- [ ] **Step 2: Run to verify it fails**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.domain.MealItemTest" --tests "app.justthecarbs.domain.NutritionValueValidatorTest"
```

Expected: compilation FAILS — `MealItem.weightBased`, `MealItem.directCarbs`, `MealItemKind` and `validateCarbsPerServing` do not exist.

- [ ] **Step 3: Rewrite `MealItem.kt`**

```kotlin
package app.justthecarbs.domain

import java.math.BigDecimal
import java.time.Instant

/** Which of the two legitimate meal-item shapes a row holds. Always explicit, never inferred. */
enum class MealItemKind { WEIGHT_BASED, DIRECT_CARBS }

/**
 * One line of the temporary meal (brief §7-§9).
 *
 * An **immutable snapshot** of a calculation the user already made and accepted. Every figure
 * needed to re-display and re-total the line is held here, rather than being looked up from the
 * product again — so an item added as `48.2 g/100 g x 72 g = 34.704 g` still reads that way after
 * the product is reformulated, corrected, re-verified, or deleted.
 *
 * The kind-specific fields are nullable in exactly two disciplined shapes, gated by [kind]:
 * a `WEIGHT_BASED` item has [resolvedAmount]/[basis]/[carbsPer100] and no count; a `DIRECT_CARBS`
 * item has [count]/[carbsPerUnit] and **no grams at all**. A direct-carb item must never carry a
 * fabricated [resolvedAmount] — the app does not know what four slices weigh, and writing a number
 * there would make it indistinguishable from a weighed portion. Use [weightBased]/[directCarbs]
 * rather than the constructor so an invalid mixture is not constructible by accident.
 */
data class MealItem(
    val id: Long = 0,
    /** Null for a quick calculation, which never had a barcode. */
    val productBarcode: String?,
    val displayName: String,
    /** What the user chose, in their own terms: "2 slices", "½ pack", "200 ml" (§10). */
    val portionDescription: String,
    val kind: MealItemKind,
    /** The resolved base-unit amount actually calculated with. WEIGHT_BASED only. */
    val resolvedAmount: BigDecimal?,
    /** WEIGHT_BASED only. */
    val basis: NutritionBasis?,
    /** WEIGHT_BASED only. */
    val carbsPer100: BigDecimal?,
    /** How many units. DIRECT_CARBS only. */
    val count: BigDecimal?,
    /** Carbohydrate in one unit. DIRECT_CARBS only. */
    val carbsPerUnit: BigDecimal?,
    /** The unrounded result. Summed as-is; formatting happens only after summation (§9). */
    val exactCarbs: BigDecimal,
    val addedAt: Instant,
) {
    companion object {
        fun weightBased(
            id: Long = 0,
            productBarcode: String?,
            displayName: String,
            portionDescription: String,
            resolvedAmount: BigDecimal,
            basis: NutritionBasis,
            carbsPer100: BigDecimal,
            exactCarbs: BigDecimal,
            addedAt: Instant,
        ): MealItem = MealItem(
            id = id,
            productBarcode = productBarcode,
            displayName = displayName,
            portionDescription = portionDescription,
            kind = MealItemKind.WEIGHT_BASED,
            resolvedAmount = resolvedAmount,
            basis = basis,
            carbsPer100 = carbsPer100,
            count = null,
            carbsPerUnit = null,
            exactCarbs = exactCarbs,
            addedAt = addedAt,
        )

        fun directCarbs(
            id: Long = 0,
            productBarcode: String?,
            displayName: String,
            portionDescription: String,
            count: BigDecimal,
            carbsPerUnit: BigDecimal,
            exactCarbs: BigDecimal,
            addedAt: Instant,
        ): MealItem = MealItem(
            id = id,
            productBarcode = productBarcode,
            displayName = displayName,
            portionDescription = portionDescription,
            kind = MealItemKind.DIRECT_CARBS,
            resolvedAmount = null,
            basis = null,
            carbsPer100 = null,
            count = count,
            carbsPerUnit = carbsPerUnit,
            exactCarbs = exactCarbs,
            addedAt = addedAt,
        )
    }
}

/**
 * The running total of a temporary meal (§9).
 *
 * This is **not a second carbohydrate formula**. Each [MealItem.exactCarbs] was produced by
 * [CarbCalculator] or [DirectCarbCalculator]; adding results the app has already computed is
 * addition, not a parallel calculation path.
 *
 * The total is `sum(exactCarbs)` over unrounded values, so it can never be the sum of rounded
 * display strings — `18.65 + 21.65` is `40.30`, not the `40` or `40.4` that pre-rounding would
 * produce.
 */
object MealTotal {

    /** Exact sum of every item. Zero for an empty meal — never null, so callers need no branch. */
    fun exact(items: List<MealItem>): BigDecimal =
        items.fold(BigDecimal.ZERO) { running, item -> running.add(item.exactCarbs) }

    /**
     * The same total as a [CarbResult], so the meal screen can reuse [ResultFormatter] and display
     * the decimal and whole-gram figures exactly as the calculator does.
     *
     * [CarbResult.basis] is taken from the first item that has one, purely as a label. It is never a
     * conversion factor (§17), and mixed-basis meals are summed as plain carbohydrate grams — the
     * carbohydrate in 200 ml of milk and in 72 g of bread are both grams of carbohydrate. A meal of
     * only direct-carb items has no basis anywhere, so it falls back to the same default an empty
     * meal uses; the figure is unaffected either way.
     */
    fun asResult(items: List<MealItem>): CarbResult = CarbResult(
        exact = exact(items),
        basis = items.firstNotNullOfOrNull { it.basis } ?: NutritionBasis.PER_100_G,
    )
}
```

- [ ] **Step 4: Add `validateCarbsPerServing`**

Append to `NutritionValueValidator`:

```kotlin
    /**
     * A serving's total carbohydrate, which has no fixed size to bound it (spec §7).
     *
     * [MAX_PER_100_G]'s reasoning — "100 g of anything cannot hold more than 100 g of carbohydrate"
     * — is arithmetic about a fixed 100 g and does not transfer: a 500 g ready meal can legitimately
     * carry well over 100 g. So this only rejects what is clearly corrupt rather than merely large,
     * because a false rejection here silently costs the user the countable-portion path.
     */
    fun validateCarbsPerServing(raw: Double?): BigDecimal? {
        if (raw == null) return null
        if (raw.isNaN() || raw.isInfinite()) return null
        if (raw < 0.0) return null
        if (raw > MAX_PER_SERVING) return null

        return BigDecimal.valueOf(raw)
    }

    /**
     * No edible serving holds this much carbohydrate; a figure above it is a unit error or corrupt
     * data. Deliberately far above any real serving so genuine large portions are never refused.
     */
    private const val MAX_PER_SERVING = 1_000.0
```

- [ ] **Step 5: Fix `MealItem` call sites and run**

`data/local/MealItemEntity.kt` (Task 11 rewrites it) and `data/ProductRepository.kt` (Task 12) construct `MealItem` — switch them to `MealItem.weightBased(...)` to compile.

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.domain.*"
```

Expected: the new `MealItemTest` and validator tests PASS; every pre-existing domain test still PASSES.

---

## Task 11: Room v6 — entities, mappers, `MIGRATION_5_6` (spec §11)

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/data/local/PortionUnitEntity.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/data/local/MealItemEntity.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/data/local/JustTheCarbsDatabase.kt`
- Test: `app/src/androidTest/kotlin/app/justthecarbs/data/local/JustTheCarbsDatabaseMigrationTest.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/data/local/PortionUnitEntityMappingTest.kt` (create)
- Generated: `app/schemas/app.justthecarbs.data.local.JustTheCarbsDatabase/6.json`

**Interfaces:**
- Consumes: `PortionConversion` (Task 8), `PortionUnit` (Task 9), `MealItem`/`MealItemKind` (Task 10).
- Produces: `JustTheCarbsDatabase.MIGRATION_5_6` (internal, so the migration test can use it), entity columns `conversionKind`/`conversionValue`/`conversionBasis` (+ the six remote variants) and `itemKind`/`count`/`carbsPerUnit`.

SQLite cannot drop `NOT NULL` in place, so both tables are rebuilt and copied. IDs are preserved by the copy, which is why `portion_usage`'s reference to `portion_units.id` needs no change.

- [ ] **Step 1: Write the failing mapping test**

Create `app/src/test/kotlin/app/justthecarbs/data/local/PortionUnitEntityMappingTest.kt` — a pure JVM test, no emulator (the entity mappers are plain functions):

```kotlin
package app.justthecarbs.data.local

import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionConversion
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.PortionUnitKind
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

// Suite: portion-unit persistence mapping
// Invariant: a DIRECT_CARBS unit stores NULL in conversionBasis — a carbs-per-item figure has no
// g/ml basis, and a sentinel there would later be read back as a real measurement.
class PortionUnitEntityMappingTest {

    private fun unit(conversion: PortionConversion) = PortionUnit(
        id = 7,
        productBarcode = "111",
        kind = PortionUnitKind.SLICE,
        customLabel = null,
        conversion = conversion,
        dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
        verificationStatus = VerificationStatus.UNVERIFIED,
        verifiedAt = null,
        originalRemoteConversion = null,
        latestRemoteConversion = null,
        rawRemoteServingText = "1 slice (35 g)",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `a weight conversion round-trips`() {
        val original = unit(PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G))

        assertEquals(original, original.toEntity().toDomain())
    }

    @Test
    fun `a direct-carb conversion round-trips`() {
        val original = unit(PortionConversion.DirectCarbs(BigDecimal("14.2")))

        assertEquals(original, original.toEntity().toDomain())
    }

    @Test
    fun `a direct-carb unit stores no basis`() {
        val entity = unit(PortionConversion.DirectCarbs(BigDecimal("14.2"))).toEntity()

        assertEquals("DIRECT_CARBS", entity.conversionKind)
        assertEquals("14.2", entity.conversionValue)
        assertNull("carbs per item has no g/ml basis", entity.conversionBasis)
    }

    @Test
    fun `a weight unit stores its basis`() {
        val entity = unit(PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_ML)).toEntity()

        assertEquals("WEIGHT", entity.conversionKind)
        assertEquals("35", entity.conversionValue)
        assertEquals("PER_100_ML", entity.conversionBasis)
    }

    @Test
    fun `remote conversions round-trip independently of the effective one`() {
        val original = unit(PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G)).copy(
            originalRemoteConversion = PortionConversion.WeightBased(BigDecimal("36"), NutritionBasis.PER_100_G),
            latestRemoteConversion = PortionConversion.DirectCarbs(BigDecimal("14.2")),
        )

        assertEquals(original, original.toEntity().toDomain())
    }

    @Test
    fun `an absent remote conversion stays absent`() {
        val entity = unit(PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G)).toEntity()

        assertNull(entity.latestRemoteConversionKind)
        assertNull(entity.latestRemoteConversionValue)
        assertNull(entity.latestRemoteConversionBasis)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.data.local.PortionUnitEntityMappingTest"
```

Expected: compilation FAILS — `conversionKind` etc. do not exist on the entity.

- [ ] **Step 3: Rewrite `PortionUnitEntity.kt`**

```kotlin
package app.justthecarbs.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionConversion
import app.justthecarbs.domain.PortionUnit
import app.justthecarbs.domain.PortionUnitKind
import app.justthecarbs.domain.ProductDataOrigin
import app.justthecarbs.domain.VerificationStatus
import java.math.BigDecimal
import java.time.Instant

/** Discriminant values for the stored [PortionConversion]. Persisted strings — do not rename. */
private const val KIND_WEIGHT = "WEIGHT"
private const val KIND_DIRECT_CARBS = "DIRECT_CARBS"

/**
 * The stored form of a [PortionUnit] (countable-portions brief §2, §6; spec §11).
 *
 * Same conventions as [ProductEntity]: decimal columns are TEXT, never REAL, and timestamps are
 * epoch milliseconds. Rows are deleted along with their product (`onDelete = CASCADE`) — a
 * countable unit has no meaning once the product it describes is gone.
 *
 * [PortionConversion] is a sealed type, which Room cannot store directly, so it is flattened into a
 * discriminant plus a value plus an optional basis. This is the only place that flattening happens.
 * [conversionBasis] is NULL exactly when the conversion is `DIRECT_CARBS` — a carbohydrate-per-item
 * figure has no g/ml basis, and writing one would let a later read mistake it for a measurement.
 */
@Entity(
    tableName = "portion_units",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["barcode"],
            childColumns = ["productBarcode"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["productBarcode"])],
)
data class PortionUnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productBarcode: String,
    /** [PortionUnitKind] name. */
    val kind: String,
    val customLabel: String?,
    /** "WEIGHT" or "DIRECT_CARBS". */
    val conversionKind: String,
    /** amountPerUnit for WEIGHT, carbsPerUnit for DIRECT_CARBS. */
    val conversionValue: String,
    /** [NutritionBasis] name for WEIGHT; NULL for DIRECT_CARBS. */
    val conversionBasis: String?,
    /** [ProductDataOrigin] name. */
    val dataSource: String,
    /** [VerificationStatus] name. */
    val verificationStatus: String,
    val verifiedAt: Long?,
    val originalRemoteConversionKind: String?,
    val originalRemoteConversionValue: String?,
    val originalRemoteConversionBasis: String?,
    val latestRemoteConversionKind: String?,
    val latestRemoteConversionValue: String?,
    val latestRemoteConversionBasis: String?,
    val rawRemoteServingText: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

private fun PortionConversion.kindName(): String = when (this) {
    is PortionConversion.WeightBased -> KIND_WEIGHT
    is PortionConversion.DirectCarbs -> KIND_DIRECT_CARBS
}

/** Normalized before storage: the column is TEXT, so "35" and "35.0" would otherwise differ. */
private fun PortionConversion.valueText(): String = when (this) {
    is PortionConversion.WeightBased -> amountPerUnit.stripTrailingZeros().toPlainString()
    is PortionConversion.DirectCarbs -> carbsPerUnit.stripTrailingZeros().toPlainString()
}

private fun PortionConversion.basisName(): String? = when (this) {
    is PortionConversion.WeightBased -> basis.name
    is PortionConversion.DirectCarbs -> null
}

private fun conversionFrom(kind: String?, value: String?, basis: String?): PortionConversion? {
    if (kind == null || value == null) return null
    return when (kind) {
        KIND_WEIGHT -> PortionConversion.WeightBased(
            amountPerUnit = BigDecimal(value),
            // A WEIGHT row without a basis is a corrupt row, not a defaulted one; failing loudly
            // here beats silently calling millilitres grams.
            basis = NutritionBasis.valueOf(requireNotNull(basis) { "a WEIGHT conversion requires a basis" }),
        )
        KIND_DIRECT_CARBS -> PortionConversion.DirectCarbs(carbsPerUnit = BigDecimal(value))
        else -> error("unknown conversion kind '$kind'")
    }
}

fun PortionUnit.toEntity(): PortionUnitEntity = PortionUnitEntity(
    id = id,
    productBarcode = productBarcode,
    kind = kind.name,
    customLabel = customLabel,
    conversionKind = conversion.kindName(),
    conversionValue = conversion.valueText(),
    conversionBasis = conversion.basisName(),
    dataSource = dataSource.name,
    verificationStatus = verificationStatus.name,
    verifiedAt = verifiedAt?.toEpochMilli(),
    originalRemoteConversionKind = originalRemoteConversion?.kindName(),
    originalRemoteConversionValue = originalRemoteConversion?.valueText(),
    originalRemoteConversionBasis = originalRemoteConversion?.basisName(),
    latestRemoteConversionKind = latestRemoteConversion?.kindName(),
    latestRemoteConversionValue = latestRemoteConversion?.valueText(),
    latestRemoteConversionBasis = latestRemoteConversion?.basisName(),
    rawRemoteServingText = rawRemoteServingText,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

fun PortionUnitEntity.toDomain(): PortionUnit = PortionUnit(
    id = id,
    productBarcode = productBarcode,
    kind = PortionUnitKind.valueOf(kind),
    customLabel = customLabel,
    conversion = requireNotNull(conversionFrom(conversionKind, conversionValue, conversionBasis)) {
        "a stored portion unit must have a conversion"
    },
    dataSource = ProductDataOrigin.valueOf(dataSource),
    verificationStatus = VerificationStatus.valueOf(verificationStatus),
    verifiedAt = verifiedAt?.let(Instant::ofEpochMilli),
    originalRemoteConversion = conversionFrom(
        originalRemoteConversionKind,
        originalRemoteConversionValue,
        originalRemoteConversionBasis,
    ),
    latestRemoteConversion = conversionFrom(
        latestRemoteConversionKind,
        latestRemoteConversionValue,
        latestRemoteConversionBasis,
    ),
    rawRemoteServingText = rawRemoteServingText,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)
```

Note the round-trip tests construct units with plain `BigDecimal("35")`, and `valueText()` normalizes — `BigDecimal("35").stripTrailingZeros().toPlainString()` is `"35"`, so equality holds. Were a test to use `BigDecimal("35.0")`, the round trip would return `35` and the data-class equality assert would fail; that is correct behaviour (normalization), not a mapper bug.

- [ ] **Step 4: Rewrite `MealItemEntity.kt`**

```kotlin
package app.justthecarbs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.justthecarbs.domain.MealItem
import app.justthecarbs.domain.MealItemKind
import app.justthecarbs.domain.NutritionBasis
import java.math.BigDecimal
import java.time.Instant

/**
 * One line of the temporary meal — an **immutable calculation snapshot** (brief §9).
 *
 * There is deliberately no foreign key to `products`. A meal item must keep showing the number it
 * was added with even if the product is later reformulated, re-verified, corrected or deleted; a
 * cascading FK would delete the row and a restricting FK would block the product delete. So the
 * facts needed to re-display and re-total the line are copied in at add time and never read back
 * from the product again.
 *
 * [exactCarbs] is the **unrounded** result and is present for both kinds. The weight-specific and
 * count-specific columns are nullable, gated by [itemKind]: a `DIRECT_CARBS` line has no grams at
 * all, and storing a sentinel there would make "4 slices" read back as a weighed portion.
 *
 * Every decimal is TEXT for the same reason as everywhere else in this schema: SQLite's REAL is a
 * binary double and cannot round-trip 48.2 exactly.
 */
@Entity(tableName = "current_meal_items")
data class MealItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Null for a quick calculation, which has no barcode to attribute the line to. */
    val productBarcode: String?,
    val displayName: String,
    /** Human-readable, e.g. "2 slices", "½ pack", "200 ml" — never merely the resolved grams (§10). */
    val portionDescription: String,
    /** [MealItemKind] name. Always present; never inferred from which columns are null. */
    val itemKind: String,
    val resolvedAmount: String?,
    val basis: String?,
    val carbsPer100: String?,
    val count: String?,
    val carbsPerUnit: String?,
    val exactCarbs: String,
    /** Ordering only. Never shown to the user — this is calculator memory, not a dated diary (§8). */
    val addedAt: Long,
)

fun MealItem.toEntity(): MealItemEntity = MealItemEntity(
    id = id,
    productBarcode = productBarcode,
    displayName = displayName,
    portionDescription = portionDescription,
    itemKind = kind.name,
    resolvedAmount = resolvedAmount?.toPlainString(),
    basis = basis?.name,
    carbsPer100 = carbsPer100?.toPlainString(),
    count = count?.toPlainString(),
    carbsPerUnit = carbsPerUnit?.toPlainString(),
    exactCarbs = exactCarbs.toPlainString(),
    addedAt = addedAt.toEpochMilli(),
)

fun MealItemEntity.toDomain(): MealItem = when (MealItemKind.valueOf(itemKind)) {
    MealItemKind.WEIGHT_BASED -> MealItem.weightBased(
        id = id,
        productBarcode = productBarcode,
        displayName = displayName,
        portionDescription = portionDescription,
        resolvedAmount = BigDecimal(requireNotNull(resolvedAmount) { "a weight-based item needs an amount" }),
        basis = NutritionBasis.valueOf(requireNotNull(basis) { "a weight-based item needs a basis" }),
        carbsPer100 = BigDecimal(requireNotNull(carbsPer100) { "a weight-based item needs carbsPer100" }),
        exactCarbs = BigDecimal(exactCarbs),
        addedAt = Instant.ofEpochMilli(addedAt),
    )
    MealItemKind.DIRECT_CARBS -> MealItem.directCarbs(
        id = id,
        productBarcode = productBarcode,
        displayName = displayName,
        portionDescription = portionDescription,
        count = BigDecimal(requireNotNull(count) { "a direct-carb item needs a count" }),
        carbsPerUnit = BigDecimal(requireNotNull(carbsPerUnit) { "a direct-carb item needs carbsPerUnit" }),
        exactCarbs = BigDecimal(exactCarbs),
        addedAt = Instant.ofEpochMilli(addedAt),
    )
}
```

- [ ] **Step 5: Add `MIGRATION_5_6` and bump the version**

In `JustTheCarbsDatabase.kt`, change `version = 5` to `version = 6`, add the migration after `MIGRATION_4_5`, and register it in `build`:

```kotlin
        /**
         * v5 → v6: portion units carry a [app.justthecarbs.domain.PortionConversion] instead of a
         * mandatory weight, and meal items carry a kind instead of mandatory grams (spec §11).
         *
         * Both tables are **rebuilt and copied** rather than altered, because SQLite cannot drop a
         * NOT NULL constraint in place and both changes make previously-mandatory columns optional.
         * Row ids are preserved by the copy, so `portion_usage.portionUnitId` still resolves to the
         * same unit and nothing referencing a meal item by id breaks.
         *
         * Every pre-existing row is explicitly labelled — portion units as `'WEIGHT'`, meal items as
         * `'WEIGHT_BASED'` — rather than left NULL for a mapper to interpret. "Absent means legacy"
         * is the kind of implicit rule that quietly rots; the discriminant is always present and
         * always authoritative.
         */
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `portion_units_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `productBarcode` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `customLabel` TEXT,
                        `conversionKind` TEXT NOT NULL,
                        `conversionValue` TEXT NOT NULL,
                        `conversionBasis` TEXT,
                        `dataSource` TEXT NOT NULL,
                        `verificationStatus` TEXT NOT NULL,
                        `verifiedAt` INTEGER,
                        `originalRemoteConversionKind` TEXT,
                        `originalRemoteConversionValue` TEXT,
                        `originalRemoteConversionBasis` TEXT,
                        `latestRemoteConversionKind` TEXT,
                        `latestRemoteConversionValue` TEXT,
                        `latestRemoteConversionBasis` TEXT,
                        `rawRemoteServingText` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`productBarcode`) REFERENCES `products`(`barcode`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    """
                    INSERT INTO `portion_units_new`
                        (id, productBarcode, kind, customLabel, conversionKind, conversionValue,
                         conversionBasis, dataSource, verificationStatus, verifiedAt,
                         originalRemoteConversionKind, originalRemoteConversionValue,
                         originalRemoteConversionBasis, latestRemoteConversionKind,
                         latestRemoteConversionValue, latestRemoteConversionBasis,
                         rawRemoteServingText, createdAt, updatedAt)
                    SELECT id, productBarcode, kind, customLabel, 'WEIGHT', amountPerUnit,
                           basis, dataSource, verificationStatus, verifiedAt,
                           CASE WHEN originalRemoteAmountPerUnit IS NOT NULL THEN 'WEIGHT' END,
                           originalRemoteAmountPerUnit,
                           CASE WHEN originalRemoteAmountPerUnit IS NOT NULL THEN basis END,
                           CASE WHEN latestRemoteAmountPerUnit IS NOT NULL THEN 'WEIGHT' END,
                           latestRemoteAmountPerUnit,
                           CASE WHEN latestRemoteAmountPerUnit IS NOT NULL THEN basis END,
                           rawRemoteServingText, createdAt, updatedAt
                    FROM `portion_units`
                    """.trimIndent(),
                )
                connection.execSQL("DROP TABLE `portion_units`")
                connection.execSQL("ALTER TABLE `portion_units_new` RENAME TO `portion_units`")
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_portion_units_productBarcode` ON `portion_units` (`productBarcode`)",
                )

                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `current_meal_items_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `productBarcode` TEXT,
                        `displayName` TEXT NOT NULL,
                        `portionDescription` TEXT NOT NULL,
                        `itemKind` TEXT NOT NULL,
                        `resolvedAmount` TEXT,
                        `basis` TEXT,
                        `carbsPer100` TEXT,
                        `count` TEXT,
                        `carbsPerUnit` TEXT,
                        `exactCarbs` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    """
                    INSERT INTO `current_meal_items_new`
                        (id, productBarcode, displayName, portionDescription, itemKind,
                         resolvedAmount, basis, carbsPer100, count, carbsPerUnit, exactCarbs, addedAt)
                    SELECT id, productBarcode, displayName, portionDescription, 'WEIGHT_BASED',
                           resolvedAmount, basis, carbsPer100, NULL, NULL, exactCarbs, addedAt
                    FROM `current_meal_items`
                    """.trimIndent(),
                )
                connection.execSQL("DROP TABLE `current_meal_items`")
                connection.execSQL("ALTER TABLE `current_meal_items_new` RENAME TO `current_meal_items`")
            }
        }
```

And in `build`:

```kotlin
        fun build(context: Context): JustTheCarbsDatabase =
            Room.databaseBuilder(context.applicationContext, JustTheCarbsDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
```

Note `count` is a SQLite keyword-adjacent identifier but is **not** reserved — it is a function name, and a bare `count` column is legal. The backticks in the CREATE statement cover it regardless; Room generates the same.

- [ ] **Step 6: Write the migration test**

Add to `JustTheCarbsDatabaseMigrationTest.kt`:

```kotlin
    @Test
    fun migratingFromV5PreservesPortionUnitsAsWeightConversions() {
        val db = helper.createDatabase(TEST_DB, 5)
        db.execSQL(
            """
            INSERT INTO products
            (barcode, name, carbsPer100, basis, dataSource, verificationStatus, favorite)
            VALUES ('333', 'Sliced Bread', '48.2', 'PER_100_G', 'OPEN_FOOD_FACTS', 'UNVERIFIED', 0)
            """.trimIndent(),
        )
        // An unverified remote unit, and a user-verified one whose remote figure has since moved.
        db.execSQL(
            """
            INSERT INTO portion_units
            (id, productBarcode, kind, customLabel, amountPerUnit, basis, dataSource,
             verificationStatus, verifiedAt, originalRemoteAmountPerUnit, latestRemoteAmountPerUnit,
             rawRemoteServingText, createdAt, updatedAt)
            VALUES
            (1, '333', 'SLICE', NULL, '35', 'PER_100_G', 'OPEN_FOOD_FACTS', 'UNVERIFIED', NULL,
             '35', NULL, '1 slice (35 g)', 5000, 5000),
            (2, '333', 'PIECE', NULL, '40', 'PER_100_G', 'OPEN_FOOD_FACTS', 'USER_VERIFIED', 9000,
             '38', '42', '1 piece (38 g)', 6000, 7000),
            (3, '333', 'CUSTOM', 'heel', '25', 'PER_100_G', 'MANUAL', 'USER_VERIFIED', 9500,
             NULL, NULL, NULL, 8000, 8000)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO portion_usage
            (id, productBarcode, inputMode, portionUnitId, amount, usageCount, lastUsedAt)
            VALUES (1, '333', 'PORTION_UNIT', 2, '2', 3, 9000)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO current_meal_items
            (id, productBarcode, displayName, portionDescription, resolvedAmount, basis,
             carbsPer100, exactCarbs, addedAt)
            VALUES (1, '333', 'Sliced Bread', '72 g', '72', 'PER_100_G', '48.2', '34.704', 9000)
            """.trimIndent(),
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true, JustTheCarbsDatabase.MIGRATION_5_6)

        migrated.query(
            "SELECT conversionKind, conversionValue, conversionBasis, latestRemoteConversionKind, " +
                "latestRemoteConversionValue, verificationStatus FROM portion_units ORDER BY id",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("WEIGHT", cursor.getString(0))
            assertEquals("35", cursor.getString(1))
            assertEquals("PER_100_G", cursor.getString(2))
            assertTrue("no latest remote figure was recorded", cursor.isNull(3))

            assertTrue(cursor.moveToNext())
            assertEquals("WEIGHT", cursor.getString(0))
            assertEquals("40", cursor.getString(1))
            assertEquals("WEIGHT", cursor.getString(3))
            assertEquals("42", cursor.getString(4))
            assertEquals("the user's verification survives", "USER_VERIFIED", cursor.getString(5))

            assertTrue(cursor.moveToNext())
            assertEquals("25", cursor.getString(1))
        }
    }

    @Test
    fun migratingFromV5PreservesPortionUsageForeignKeysByKeepingIds() {
        val db = helper.createDatabase(TEST_DB, 5)
        db.execSQL(
            """
            INSERT INTO products (barcode, name, carbsPer100, basis, dataSource, verificationStatus, favorite)
            VALUES ('444', 'Bread', '48.2', 'PER_100_G', 'OPEN_FOOD_FACTS', 'UNVERIFIED', 0)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO portion_units
            (id, productBarcode, kind, customLabel, amountPerUnit, basis, dataSource,
             verificationStatus, verifiedAt, originalRemoteAmountPerUnit, latestRemoteAmountPerUnit,
             rawRemoteServingText, createdAt, updatedAt)
            VALUES (42, '444', 'SLICE', NULL, '35', 'PER_100_G', 'OPEN_FOOD_FACTS', 'UNVERIFIED',
                    NULL, NULL, NULL, NULL, 5000, 5000)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO portion_usage
            (id, productBarcode, inputMode, portionUnitId, amount, usageCount, lastUsedAt)
            VALUES (1, '444', 'PORTION_UNIT', 42, '2', 3, 9000)
            """.trimIndent(),
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true, JustTheCarbsDatabase.MIGRATION_5_6)

        // The rebuild must not renumber: a usage row pointing at unit 42 must still find unit 42.
        migrated.query(
            "SELECT u.id FROM portion_units u JOIN portion_usage p ON p.portionUnitId = u.id",
        ).use { cursor ->
            assertTrue("the usage row still resolves to its unit", cursor.moveToFirst())
            assertEquals(42, cursor.getInt(0))
        }
    }

    @Test
    fun migratingFromV5LabelsExistingMealItemsWeightBased() {
        val db = helper.createDatabase(TEST_DB, 5)
        db.execSQL(
            """
            INSERT INTO current_meal_items
            (id, productBarcode, displayName, portionDescription, resolvedAmount, basis,
             carbsPer100, exactCarbs, addedAt)
            VALUES (1, '555', 'Bread', '72 g', '72', 'PER_100_G', '48.2', '34.704', 9000)
            """.trimIndent(),
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true, JustTheCarbsDatabase.MIGRATION_5_6)

        migrated.query(
            "SELECT itemKind, resolvedAmount, basis, carbsPer100, count, carbsPerUnit, exactCarbs " +
                "FROM current_meal_items WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("WEIGHT_BASED", cursor.getString(0))
            assertEquals("72", cursor.getString(1))
            assertEquals("PER_100_G", cursor.getString(2))
            assertEquals("48.2", cursor.getString(3))
            assertTrue("a legacy row has no count", cursor.isNull(4))
            assertTrue("a legacy row has no per-unit carbs", cursor.isNull(5))
            assertEquals("34.704", cursor.getString(6))
        }
    }

    @Test
    fun aDirectCarbMealItemCanBeStoredAfterMigration() {
        helper.createDatabase(TEST_DB, 5).close()
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true, JustTheCarbsDatabase.MIGRATION_5_6)

        // The whole point of the rebuild: these three columns must now accept NULL.
        migrated.execSQL(
            """
            INSERT INTO current_meal_items
            (id, productBarcode, displayName, portionDescription, itemKind, resolvedAmount, basis,
             carbsPer100, count, carbsPerUnit, exactCarbs, addedAt)
            VALUES (2, '666', 'Crackers', '4 slices', 'DIRECT_CARBS', NULL, NULL, NULL,
                    '4', '14.2', '56.8', 9100)
            """.trimIndent(),
        )

        migrated.query(
            "SELECT resolvedAmount, basis, carbsPer100, count, carbsPerUnit FROM current_meal_items WHERE id = 2",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue("no fake grams", cursor.isNull(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
            assertEquals("4", cursor.getString(3))
            assertEquals("14.2", cursor.getString(4))
        }
    }

    @Test
    fun aDirectCarbPortionUnitCanBeStoredAfterMigration() {
        val db = helper.createDatabase(TEST_DB, 5)
        db.execSQL(
            """
            INSERT INTO products (barcode, name, carbsPer100, basis, dataSource, verificationStatus, favorite)
            VALUES ('777', 'Crackers', '62', 'PER_100_G', 'OPEN_FOOD_FACTS', 'UNVERIFIED', 0)
            """.trimIndent(),
        )
        db.close()
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true, JustTheCarbsDatabase.MIGRATION_5_6)

        migrated.execSQL(
            """
            INSERT INTO portion_units
            (id, productBarcode, kind, customLabel, conversionKind, conversionValue, conversionBasis,
             dataSource, verificationStatus, verifiedAt, originalRemoteConversionKind,
             originalRemoteConversionValue, originalRemoteConversionBasis, latestRemoteConversionKind,
             latestRemoteConversionValue, latestRemoteConversionBasis, rawRemoteServingText,
             createdAt, updatedAt)
            VALUES (1, '777', 'SLICE', NULL, 'DIRECT_CARBS', '14.2', NULL, 'OPEN_FOOD_FACTS',
                    'UNVERIFIED', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2 slices', 5000, 5000)
            """.trimIndent(),
        )

        migrated.query("SELECT conversionKind, conversionValue, conversionBasis FROM portion_units WHERE id = 1")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("DIRECT_CARBS", cursor.getString(0))
                assertEquals("14.2", cursor.getString(1))
                assertTrue("carbs per item has no g/ml basis", cursor.isNull(2))
            }
    }
```

- [ ] **Step 7: Build to export schema 6, then run the JVM tests**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL, and `app/schemas/app.justthecarbs.data.local.JustTheCarbsDatabase/6.json` now exists. If Room reports a schema mismatch, the CREATE TABLE in the migration disagrees with the entity — align the migration SQL to what Room expects (Room's error message prints both schemas; the entity is the source of truth).

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.data.local.*"
```

Expected: mapping tests PASS. The migration tests are instrumented and run in Task 17.

---

## Task 12: `ProductRepository` — conversion-aware save/refresh/freeze (spec §9, §10, §12)

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/data/ProductRepository.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/data/ProductRepositoryTest.kt`

**Interfaces:**
- Consumes: `PortionConversion`, `PortionUnit`, `PortionUnitCandidate`, `MealItem`/`MealItemKind`.
- Produces:
  ```kotlin
  suspend fun saveUserPortionUnit(
      barcode: String, kind: PortionUnitKind, conversion: PortionConversion, customLabel: String? = null,
  ): PortionUnit
  suspend fun verifyPortionUnit(unitId: Long, confirmedConversion: PortionConversion? = null): PortionUnit
  suspend fun addDirectCarbMealItem(
      productBarcode: String?, displayName: String, portionDescription: String,
      count: BigDecimal, carbsPerUnit: BigDecimal, exactCarbs: BigDecimal,
  ): MealItem
  ```
  Task 14 consumes these. `addMealItem` keeps its current signature (weight-based) and now calls `MealItem.weightBased`.

Note `saveUserPortionUnit` drops its `basis` parameter — the basis now lives inside `PortionConversion.WeightBased`, and a `DirectCarbs` unit has none.

- [ ] **Step 1: Write the failing tests**

Add to `ProductRepositoryTest.kt` (reuse the file's existing fakes and `repository` construction — match whatever helper names are already there):

```kotlin
    @Test
    fun `a remote direct-carb candidate becomes a stored direct-carb unit`() = runTest {
        val candidate = PortionUnitCandidate(
            kind = PortionUnitKind.SLICE,
            conversion = PortionConversion.DirectCarbs(BigDecimal("12.6")),
            rawServingText = "2 slices",
        )
        remote.result = ProductFetchResult.Found(product("111"), portionUnitCandidate = candidate)

        repository.lookup("111")

        val stored = repository.findPortionUnits("111").single()
        assertEquals(PortionConversion.DirectCarbs(BigDecimal("12.6")), stored.conversion)
        assertEquals(ProductDataOrigin.OPEN_FOOD_FACTS, stored.dataSource)
        assertEquals(VerificationStatus.UNVERIFIED, stored.verificationStatus)
    }

    @Test
    fun `a verified direct-carb unit is not overwritten by a refresh`() = runTest {
        remote.result = ProductFetchResult.Found(product("111"))
        repository.lookup("111")
        val saved = repository.saveUserPortionUnit(
            barcode = "111",
            kind = PortionUnitKind.SLICE,
            conversion = PortionConversion.DirectCarbs(BigDecimal("14.2")),
        )

        remote.result = ProductFetchResult.Found(
            product("111"),
            portionUnitCandidate = PortionUnitCandidate(
                kind = PortionUnitKind.SLICE,
                conversion = PortionConversion.DirectCarbs(BigDecimal("20.0")),
                rawServingText = "1 slice",
            ),
        )
        repository.refreshFromRemote("111")

        val after = repository.findPortionUnit(saved.id)
        assertEquals(
            "the user's own figure stands",
            PortionConversion.DirectCarbs(BigDecimal("14.2")),
            after?.conversion,
        )
    }

    @Test
    fun `an unverified remote direct-carb unit is refreshed in place`() = runTest {
        remote.result = ProductFetchResult.Found(
            product("111"),
            portionUnitCandidate = PortionUnitCandidate(
                kind = PortionUnitKind.SLICE,
                conversion = PortionConversion.DirectCarbs(BigDecimal("12.6")),
                rawServingText = "2 slices",
            ),
        )
        repository.lookup("111")

        remote.result = ProductFetchResult.Found(
            product("111"),
            portionUnitCandidate = PortionUnitCandidate(
                kind = PortionUnitKind.SLICE,
                conversion = PortionConversion.DirectCarbs(BigDecimal("13.1")),
                rawServingText = "2 slices",
            ),
        )
        repository.refreshFromRemote("111")

        assertEquals(
            PortionConversion.DirectCarbs(BigDecimal("13.1")),
            repository.findPortionUnits("111").single().conversion,
        )
    }

    @Test
    fun `verifying a direct-carb unit can correct its value while keeping provenance`() = runTest {
        remote.result = ProductFetchResult.Found(
            product("111"),
            portionUnitCandidate = PortionUnitCandidate(
                kind = PortionUnitKind.SLICE,
                conversion = PortionConversion.DirectCarbs(BigDecimal("12.6")),
                rawServingText = "2 slices",
            ),
        )
        repository.lookup("111")
        val unit = repository.findPortionUnits("111").single()

        val verified = repository.verifyPortionUnit(unit.id, PortionConversion.DirectCarbs(BigDecimal("13.0")))

        assertEquals(PortionConversion.DirectCarbs(BigDecimal("13.0")), verified.conversion)
        assertEquals(VerificationStatus.USER_VERIFIED, verified.verificationStatus)
        assertEquals("provenance survives verification", ProductDataOrigin.OPEN_FOOD_FACTS, verified.dataSource)
    }

    @Test
    fun `a direct-carb meal item records no resolved grams`() = runTest {
        val item = repository.addDirectCarbMealItem(
            productBarcode = "111",
            displayName = "Crackers",
            portionDescription = "4 slices",
            count = BigDecimal("4"),
            carbsPerUnit = BigDecimal("14.2"),
            exactCarbs = BigDecimal("56.8"),
        )

        assertEquals(MealItemKind.DIRECT_CARBS, item.kind)
        assertNull(item.resolvedAmount)
        assertEquals(0, BigDecimal("56.8").compareTo(repository.findMealItems().single().exactCarbs))
    }
```

- [ ] **Step 2: Run to verify it fails**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.data.ProductRepositoryTest"
```

Expected: compilation FAILS — `addDirectCarbMealItem` does not exist and the signatures differ.

- [ ] **Step 3: Update the portion-unit methods**

Replace in `ProductRepository.kt`:

```kotlin
    /**
     * A unit the user defines themselves (§6): a known [kind] with their own weight or their own
     * carbs-per-item figure, or a fully custom label. Always counts as verified — the user is
     * reading their own kitchen scale or package, exactly what verification means elsewhere.
     */
    suspend fun saveUserPortionUnit(
        barcode: String,
        kind: PortionUnitKind,
        conversion: PortionConversion,
        customLabel: String? = null,
    ): PortionUnit {
        require(kind != PortionUnitKind.CUSTOM || !customLabel.isNullOrBlank()) {
            "a custom portion unit requires a label"
        }
        val now = clock.instant()
        return portionUnits.save(
            PortionUnit(
                productBarcode = barcode,
                kind = kind,
                customLabel = customLabel,
                conversion = conversion,
                dataSource = ProductDataOrigin.MANUAL,
                verificationStatus = VerificationStatus.USER_VERIFIED,
                verifiedAt = now,
                originalRemoteConversion = null,
                latestRemoteConversion = null,
                rawRemoteServingText = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /**
     * The user checked a remote-sourced unit against the package (§7), optionally correcting it
     * while doing so. Mirrors [saveVerification]: provenance stays Open Food Facts, only
     * verification state and the effective conversion change. A correction may also change the
     * conversion *kind* — reading a printed weight on a unit the app only knew carbs for is a
     * genuine upgrade, not a new unit.
     */
    suspend fun verifyPortionUnit(unitId: Long, confirmedConversion: PortionConversion? = null): PortionUnit {
        val existing = requirePortionUnit(unitId)
        val verified = existing.copy(
            conversion = confirmedConversion ?: existing.conversion,
            verificationStatus = VerificationStatus.USER_VERIFIED,
            verifiedAt = clock.instant(),
            updatedAt = clock.instant(),
        )
        return portionUnits.save(verified)
    }

    /** Deliberate acceptance of a newer remote conversion (§7, mirrors [applyLatestRemoteValue]). */
    suspend fun applyLatestRemotePortionUnit(unitId: Long): PortionUnit {
        val existing = requirePortionUnit(unitId)
        val latest = existing.latestRemoteConversion ?: return existing
        val applied = existing.copy(
            conversion = latest,
            verificationStatus = VerificationStatus.UNVERIFIED,
            verifiedAt = null,
            updatedAt = clock.instant(),
        )
        return portionUnits.save(applied)
    }
```

And the two private helpers:

```kotlin
    private fun newPortionUnitFromCandidate(barcode: String, candidate: PortionUnitCandidate): PortionUnit {
        val now = clock.instant()
        return PortionUnit(
            productBarcode = barcode,
            kind = candidate.kind,
            customLabel = null,
            conversion = candidate.conversion,
            dataSource = ProductDataOrigin.OPEN_FOOD_FACTS,
            verificationStatus = VerificationStatus.UNVERIFIED,
            verifiedAt = null,
            originalRemoteConversion = candidate.conversion,
            latestRemoteConversion = candidate.conversion,
            rawRemoteServingText = candidate.rawServingText,
            createdAt = now,
            updatedAt = now,
        )
    }

    /**
     * Same rule as the product's own carbohydrate refresh, applied per unit (§7, §9): an unverified
     * Open-Food-Facts-sourced unit is kept in sync outright, while a user-verified or user-authored
     * one only has its "latest remote" conversion recorded for a notice, never its effective one.
     *
     * The rule keys on provenance and verification alone, never on which [PortionConversion] kind
     * the unit or the candidate holds — a user who confirmed "14.2 g carbs per slice" is owed the
     * same protection as one who confirmed "35 g per slice".
     */
    private suspend fun refreshPortionUnitFromCandidate(barcode: String, candidate: PortionUnitCandidate?) {
        if (candidate == null) return
        val existingUnits = portionUnits.findByBarcode(barcode)
        val matching = existingUnits.firstOrNull {
            it.dataSource == ProductDataOrigin.OPEN_FOOD_FACTS && it.kind == candidate.kind
        }

        if (matching == null) {
            portionUnits.save(newPortionUnitFromCandidate(barcode, candidate))
            return
        }

        if (matching.isRemoteRefreshable) {
            portionUnits.save(
                matching.copy(
                    conversion = candidate.conversion,
                    latestRemoteConversion = candidate.conversion,
                    rawRemoteServingText = candidate.rawServingText,
                    updatedAt = clock.instant(),
                ),
            )
        } else {
            portionUnits.save(
                matching.copy(
                    latestRemoteConversion = candidate.conversion,
                    rawRemoteServingText = candidate.rawServingText,
                    updatedAt = clock.instant(),
                ),
            )
        }
    }
```

- [ ] **Step 4: Update meal item creation**

Change `addMealItem`'s body to use the factory, and add the direct-carb sibling:

```kotlin
    suspend fun addMealItem(
        productBarcode: String?,
        displayName: String,
        portionDescription: String,
        resolvedAmount: BigDecimal,
        basis: NutritionBasis,
        carbsPer100: BigDecimal,
        exactCarbs: BigDecimal,
    ): MealItem = meal.add(
        MealItem.weightBased(
            productBarcode = productBarcode?.takeIf { it.isNotEmpty() },
            displayName = displayName,
            portionDescription = portionDescription,
            resolvedAmount = resolvedAmount,
            basis = basis,
            carbsPer100 = carbsPer100,
            exactCarbs = exactCarbs,
            addedAt = clock.instant(),
        ),
    )

    /**
     * Add a completed direct-carb calculation to the meal (§12).
     *
     * Deliberately takes no resolved amount and no basis: on this path the app does not know what
     * the portion weighs. Writing a derived gram figure here would make a counted portion
     * indistinguishable from a weighed one, which is the exact confusion [MealItemKind] exists to
     * prevent. As with [addMealItem], [exactCarbs] is the number the user already saw — nothing is
     * recomputed here.
     */
    suspend fun addDirectCarbMealItem(
        productBarcode: String?,
        displayName: String,
        portionDescription: String,
        count: BigDecimal,
        carbsPerUnit: BigDecimal,
        exactCarbs: BigDecimal,
    ): MealItem = meal.add(
        MealItem.directCarbs(
            productBarcode = productBarcode?.takeIf { it.isNotEmpty() },
            displayName = displayName,
            portionDescription = portionDescription,
            count = count,
            carbsPerUnit = carbsPerUnit,
            exactCarbs = exactCarbs,
            addedAt = clock.instant(),
        ),
    )
```

Add `import app.justthecarbs.domain.PortionConversion` at the top.

- [ ] **Step 5: Run to verify it passes**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.data.ProductRepositoryTest"
```

Expected: the 5 new tests PASS and every pre-existing repository test still PASSES — in particular the weight-based freeze tests, which must be unaffected.

---

## Task 13: OFF `carbohydrates_serving` + Case A–D precedence (spec §7, §9, §16)

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/data/remote/OpenFoodFactsDto.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/data/remote/OpenFoodFactsApi.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/data/remote/OpenFoodFactsDataSource.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/data/remote/OpenFoodFactsDataSourceTest.kt`

**Interfaces:**
- Consumes: `ServingSizeParser.parseDescriptor` (Task 6), `PortionConversion` (Task 8), `PortionUnitCandidate` (Task 9), `NutritionValueValidator.validateCarbsPerServing` (Task 10).
- Produces: no new public API; `fetch` now returns conversion-carrying candidates.

Precedence (spec §16): weight present → `WeightBased`; no weight but `carbohydrates_serving` present → `DirectCarbs`; neither → **no candidate at all**.

- [ ] **Step 1: Write the failing tests**

Add to `OpenFoodFactsDataSourceTest.kt` (reuse the file's existing fake-API/JSON helper style):

```kotlin
    @Test
    fun `case A - a serving size with a weight yields a weight-based unit`() = runTest {
        val result = fetchWith(servingSize = "2 slices (70 g)", carbsPerServing = null)

        val candidate = (result as ProductFetchResult.Found).portionUnitCandidate
        assertEquals(PortionUnitKind.SLICE, candidate?.kind)
        assertEquals(
            PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G),
            candidate?.conversion,
        )
    }

    @Test
    fun `case B - no weight but per-serving carbs yields a direct-carb unit`() = runTest {
        val result = fetchWith(servingSize = "2 slices", carbsPerServing = 25.2)

        val candidate = (result as ProductFetchResult.Found).portionUnitCandidate
        assertEquals(PortionUnitKind.SLICE, candidate?.kind)
        assertEquals(PortionConversion.DirectCarbs(BigDecimal("12.6")), candidate?.conversion)
    }

    @Test
    fun `case C - a weight wins even when per-serving carbs are also present`() = runTest {
        val result = fetchWith(servingSize = "2 slices (70 g)", carbsPerServing = 25.2)

        val candidate = (result as ProductFetchResult.Found).portionUnitCandidate
        assertEquals(
            "a printed weight is the stronger relationship",
            PortionConversion.WeightBased(BigDecimal("35"), NutritionBasis.PER_100_G),
            candidate?.conversion,
        )
    }

    @Test
    fun `case D - neither a weight nor per-serving carbs yields no candidate`() = runTest {
        val result = fetchWith(servingSize = "1 slice", carbsPerServing = null)

        // The app knows the product comes in slices but not what one contains. It does not guess;
        // the UI asks the user once instead.
        assertNull((result as ProductFetchResult.Found).portionUnitCandidate)
    }

    @Test
    fun `a serving-size basis that disagrees with the product basis is rejected`() = runTest {
        // A ml serving on a per-100-g product: the two never convert into each other (§17).
        val result = fetchWith(servingSize = "1 scoop (30 ml)", carbsPerServing = null, quantity = "500 g")

        assertNull((result as ProductFetchResult.Found).portionUnitCandidate)
    }

    @Test
    fun `a corrupt per-serving carbohydrate figure is refused`() = runTest {
        val result = fetchWith(servingSize = "2 slices", carbsPerServing = 50_000.0)

        assertNull((result as ProductFetchResult.Found).portionUnitCandidate)
    }

    @Test
    fun `a direct-carb candidate keeps the raw serving text`() = runTest {
        val result = fetchWith(servingSize = "2 slices", carbsPerServing = 25.2)

        assertEquals("2 slices", (result as ProductFetchResult.Found).portionUnitCandidate?.rawServingText)
    }
```

Add a helper matching the file's existing fake-response conventions:

```kotlin
    /** Builds a one-product OFF response with the fields these cases turn on. */
    private suspend fun fetchWith(
        servingSize: String?,
        carbsPerServing: Double?,
        quantity: String = "500 g",
    ): ProductFetchResult
```

Implement it against whatever fake `OpenFoodFactsApi` the test file already uses — do not introduce a second faking style.

- [ ] **Step 2: Run to verify it fails**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.data.remote.OpenFoodFactsDataSourceTest"
```

Expected: compilation FAILS or the Case B/D tests FAIL — the DTO has no `carbohydrates_serving` and the mapping still produces weight-only candidates.

- [ ] **Step 3: Add the DTO field**

In `OpenFoodFactsDto.kt`, extend `OffNutriments`:

```kotlin
@Serializable
data class OffNutriments(
    /**
     * TOTAL carbohydrate per 100 g/ml. This is the only nutrient the app reads.
     *
     * It is never substituted with sugars, fibre, net carbs, energy or protein (§12) — those are
     * different quantities, and quietly standing in for a missing total would produce a confident
     * wrong number at the exact moment the user needs a right one.
     */
    @SerialName("carbohydrates_100g") val carbohydrates100g: Double? = null,
    /**
     * TOTAL carbohydrate in one serving, as OFF reports it (spec §7).
     *
     * Read only to build a countable portion when `serving_size` names a unit but prints no weight —
     * it is never a substitute for [carbohydrates100g] and never feeds the per-100 calculation.
     */
    @SerialName("carbohydrates_serving") val carbohydratesServing: Double? = null,
)
```

- [ ] **Step 4: Request the field**

In `OpenFoodFactsApi.kt`, `nutriments` is already requested and returns the whole object including `carbohydrates_serving`, so `COMMON_FIELDS` needs **no change**. Verify this by checking that the fake responses in the test include the field inside `nutriments` — if the live API turns out to require explicit field naming, add `"nutriments"` is already present, so no edit is needed here. (The rate-limit comment fix for this file was already made in Task 1.)

- [ ] **Step 5: Implement the precedence in `OpenFoodFactsDataSource.kt`**

Replace the `servingSize` block inside `toResult`:

```kotlin
        val portionUnitCandidate = portionUnitCandidate(remote, basis)
```

and add the private method:

```kotlin
    /**
     * Turns `serving_size` plus `carbohydrates_serving` into a countable unit, or into nothing
     * (spec §9, §16).
     *
     * Precedence, in order:
     *
     * - **A/C** a printed weight wins whenever one is present, even if per-serving carbs are also
     *   available. A weight is the stronger relationship: it survives a reformulation of the recipe,
     *   and it feeds the app's single existing calculation path.
     * - **B** no weight, but per-serving carbs → a direct-carb unit. This is the case that used to
     *   send the user to fetch a kitchen scale.
     * - **D** neither → no candidate. The app may still know the product is sold in slices, but it
     *   does not invent a relationship it was not given; the UI asks the user once instead.
     *
     * A parsed serving size is only trustworthy if its basis matches the product's own — a countable
     * unit measured in ml has no meaning for a product whose carbs are per 100 g, and the two never
     * converting into each other (§17) rules out silently coercing one to the other here too. That
     * check applies to the weight path only: a direct-carb figure carries no basis to disagree.
     */
    private fun portionUnitCandidate(remote: OffProduct, basis: NutritionBasis): PortionUnitCandidate? {
        val descriptor = ServingSizeParser.parseDescriptor(remote.servingSize) ?: return null

        descriptor.amountPerUnit?.let { perUnit ->
            if (perUnit.basis != basis) return null
            return PortionUnitCandidate(
                kind = descriptor.kind,
                conversion = PortionConversion.WeightBased(perUnit.amount, perUnit.basis),
                rawServingText = remote.servingSize.orEmpty(),
            )
        }

        val carbsPerServing = NutritionValueValidator.validateCarbsPerServing(
            remote.nutriments?.carbohydratesServing,
        ) ?: return null

        val carbsPerUnit = carbsPerServing
            .divide(descriptor.count, CARBS_PER_UNIT_SCALE, RoundingMode.HALF_UP)
            .stripTrailingZeros()

        return PortionUnitCandidate(
            kind = descriptor.kind,
            conversion = PortionConversion.DirectCarbs(carbsPerUnit),
            rawServingText = remote.servingSize.orEmpty(),
        )
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429

        /** Matches the scale [ServingDescriptor] uses for its own per-unit division. */
        const val CARBS_PER_UNIT_SCALE = 4
    }
```

Add imports: `app.justthecarbs.domain.PortionConversion`, `java.math.RoundingMode`. The existing `private companion object` is replaced by the one above — do not add a second.

- [ ] **Step 6: Run to verify it passes**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.data.remote.*"
```

Expected: the 7 new tests PASS and every pre-existing data-source test still PASSES.

---

## Task 14: `ProductViewModel` — the direct-carb calculation path (spec §14)

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/product/ProductViewModel.kt`
- Test: `app/src/test/kotlin/app/justthecarbs/ui/product/ProductViewModelTest.kt`

**Interfaces:**
- Consumes: `PortionConversion`, `DirectCarbCalculator`, `PortionResolver`, `CarbCalculator`, the Task 12 repository methods.
- Produces:
  ```kotlin
  // ProductUiState gains:
  val directCarbResult: BigDecimal?   // set only in direct-carb mode; null otherwise
  // ProductViewModel gains:
  fun addPortionUnit(kind: PortionUnitKind, conversion: PortionConversion, customLabel: String?)
  fun correctSelectedPortionUnit(confirmedConversion: PortionConversion)
  ```
  Task 15 consumes these.

The two paths must not contaminate each other. In direct-carb mode `portionText` is **not** filled with a derived gram figure — there is no gram figure. `result` (a `CarbResult`) stays null and `directCarbResult` carries the value.

- [ ] **Step 1: Write the failing tests**

Add to `ProductViewModelTest.kt` (reuse the file's existing fake repository and `viewModel` construction):

```kotlin
    @Test
    fun `selecting a direct-carb unit calculates without any grams`() = runTest {
        val unit = directCarbUnit(id = 1, carbsPerUnit = "14.2")
        repository.portionUnits += unit
        viewModel.load("111")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.switchToPortionUnit(1)
        viewModel.onCountChanged("4")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, BigDecimal("56.8").compareTo(viewModel.state.value.directCarbResult))
        assertNull("there is no per-100 result on this path", viewModel.state.value.result)
        assertEquals("no gram figure may be invented", "", viewModel.state.value.portionText)
    }

    @Test
    fun `switching from a direct-carb unit back to grams clears the direct result`() = runTest {
        repository.portionUnits += directCarbUnit(id = 1, carbsPerUnit = "14.2")
        viewModel.load("111")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.switchToPortionUnit(1)
        viewModel.onCountChanged("4")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.switchToGrams()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.directCarbResult)
    }

    @Test
    fun `a weight-based unit still resolves to grams and a CarbResult`() = runTest {
        repository.portionUnits += weightUnit(id = 1, amountPerUnit = "35")
        viewModel.load("111")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.switchToPortionUnit(1)
        viewModel.onCountChanged("4")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("140", viewModel.state.value.portionText)
        assertNotNull(viewModel.state.value.result)
        assertNull(viewModel.state.value.directCarbResult)
    }

    @Test
    fun `an empty count clears the direct-carb result`() = runTest {
        repository.portionUnits += directCarbUnit(id = 1, carbsPerUnit = "14.2")
        viewModel.load("111")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.switchToPortionUnit(1)
        viewModel.onCountChanged("4")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onCountChanged("")
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.directCarbResult)
    }

    @Test
    fun `adding a direct-carb portion to the meal records no grams`() = runTest {
        repository.portionUnits += directCarbUnit(id = 1, carbsPerUnit = "14.2")
        viewModel.load("111")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.switchToPortionUnit(1)
        viewModel.onCountChanged("4")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.addCurrentToMeal("4 slices")
        dispatcher.scheduler.advanceUntilIdle()

        val item = repository.findMealItems().single()
        assertEquals(MealItemKind.DIRECT_CARBS, item.kind)
        assertNull(item.resolvedAmount)
        assertEquals(0, BigDecimal("56.8").compareTo(item.exactCarbs))
    }
```

Add the two fixture helpers next to the file's existing ones:

```kotlin
    private fun weightUnit(id: Long, amountPerUnit: String) = portionUnit(
        id = id,
        conversion = PortionConversion.WeightBased(BigDecimal(amountPerUnit), NutritionBasis.PER_100_G),
    )

    private fun directCarbUnit(id: Long, carbsPerUnit: String) = portionUnit(
        id = id,
        conversion = PortionConversion.DirectCarbs(BigDecimal(carbsPerUnit)),
    )
```

with `portionUnit(...)` building a `PortionUnit` in the style the file already uses.

- [ ] **Step 2: Run to verify it fails**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ui.product.ProductViewModelTest"
```

Expected: compilation FAILS — `directCarbResult` does not exist.

- [ ] **Step 3: Add the state field**

In `ProductUiState`, after `result`:

```kotlin
    /**
     * The total for a direct-carb countable portion (spec §14).
     *
     * Separate from [result] rather than folded into it: [CarbResult] carries a non-null
     * [NutritionBasis] meaning "per 100 g/ml", which is a claim this path cannot make — it never
     * knew a weight. Exactly one of the two is non-null at any time.
     */
    val directCarbResult: BigDecimal? = null,
```

- [ ] **Step 4: Branch the calculation on conversion kind**

Replace `recalculateFromCount`:

```kotlin
    private fun recalculateFromCount(unit: PortionUnit, countText: String) {
        val count = PortionParser.parse(countText)
        if (count == null) {
            _state.update { it.copy(result = null, directCarbResult = null) }
            return
        }

        when (val conversion = unit.conversion) {
            is PortionConversion.WeightBased -> {
                val resolved = PortionResolver.resolve(count, conversion.amountPerUnit)
                val portionText = resolved.stripTrailingZeros().toPlainString()
                savedState[KEY_PORTION] = portionText
                _state.update { it.copy(portionText = portionText, directCarbResult = null) }
                recalculate()
            }
            is PortionConversion.DirectCarbs -> {
                // No grams exist on this path, so none are written into portionText. Filling it with
                // a derived figure would put a weight the app never knew in front of the user.
                _state.update {
                    it.copy(
                        result = null,
                        directCarbResult = DirectCarbCalculator.exactCarbs(count, conversion.carbsPerUnit),
                    )
                }
            }
        }
    }
```

In `switchToGrams`, clear the direct result:

```kotlin
    fun switchToGrams() {
        savedState[KEY_MODE] = InputMode.GRAMS.name
        savedState.remove<Long>(KEY_SELECTED_UNIT)
        _state.update { it.copy(inputMode = InputMode.GRAMS, selectedPortionUnitId = null, directCarbResult = null) }
        recalculate()
    }
```

In `onProductLoaded`, the restored-portion block calls `PortionResolver.resolve(count, unit.amountPerUnit)`. Replace that expression:

```kotlin
            val portionText = if (resolvedMode == InputMode.PORTION_UNIT && resolvedSelectedId != null) {
                val unit = units.first { it.id == resolvedSelectedId }
                when (val conversion = unit.conversion) {
                    is PortionConversion.WeightBased -> {
                        val count = PortionParser.parse(countText) ?: BigDecimal.ONE
                        PortionResolver.resolve(count, conversion.amountPerUnit).stripTrailingZeros().toPlainString()
                    }
                    // A restored direct-carb selection has no grams to pre-fill. The count alone
                    // reproduces the calculation.
                    is PortionConversion.DirectCarbs -> ""
                }
            } else {
                restoredPortion
                    ?: product.lastPortion?.stripTrailingZeros()?.toPlainString()
                    ?: ""
            }
```

Immediately after the `_state.update { ... }` in `onProductLoaded`, replace the bare `recalculate()` with a branch so a restored direct-carb session recalculates too:

```kotlin
            val restoredUnit = units.firstOrNull { it.id == resolvedSelectedId }
            if (restoredUnit != null && resolvedMode == InputMode.PORTION_UNIT) {
                recalculateFromCount(restoredUnit, countText)
            } else {
                recalculate()
            }
```

The frozen-unit notice block below it reads `frozenSelected.amountPerUnit` — change to compare conversions:

```kotlin
            val frozenSelected = units.firstOrNull { it.id == resolvedSelectedId }
            if (frozenSelected != null) {
                val refreshed = repository.findPortionUnits(product.barcode)
                    .firstOrNull { it.id == frozenSelected.id }
                val latest = refreshed?.latestRemoteConversion
                if (latest != null && latest != frozenSelected.conversion) {
                    _state.update { it.copy(newerRemotePortionUnit = latest) }
                }
            }
```

Rename the state field `newerRemotePortionUnitAmount: BigDecimal?` to `newerRemotePortionUnit: PortionConversion?` and update `dismissNewerRemotePortionUnit`/`applyNewerRemotePortionUnit` accordingly (the latter sets `newerRemotePortionUnit = null`).

- [ ] **Step 5: Update the add/correct/meal methods**

```kotlin
    /** A unit the user defines themselves (§6). Always saved as verified — they read their own scale. */
    fun addPortionUnit(kind: PortionUnitKind, conversion: PortionConversion, customLabel: String?) {
        val product = _state.value.product ?: return
        if (product.barcode.isEmpty()) return
        viewModelScope.launch {
            val saved = repository.saveUserPortionUnit(
                barcode = product.barcode,
                kind = kind,
                conversion = conversion,
                customLabel = customLabel,
            )
            _state.update { it.copy(portionUnits = it.portionUnits + saved, showAddPortionUnitForm = false) }
            switchToPortionUnit(saved.id)
        }
    }

    fun correctSelectedPortionUnit(confirmedConversion: PortionConversion) {
        val unit = _state.value.selectedPortionUnit ?: return
        viewModelScope.launch {
            val verified = repository.verifyPortionUnit(unit.id, confirmedConversion)
            _state.update { st ->
                st.copy(
                    portionUnits = st.portionUnits.map { if (it.id == verified.id) verified else it },
                    correctingPortionUnit = false,
                )
            }
            // The corrected value is a deliberate user action, so unlike a background refresh it
            // *should* move the open session's result (§9 protects against surprise, not intent).
            if (_state.value.selectedPortionUnitId == verified.id) {
                recalculateFromCount(verified, _state.value.countText)
            }
        }
    }
```

`addCurrentToMeal` must route by mode:

```kotlin
    fun addCurrentToMeal(portionDescription: String) {
        val product = _state.value.product ?: return
        val barcode = product.barcode.takeIf { !_state.value.unsaved }
        val directCarbs = _state.value.directCarbResult
        val unit = _state.value.selectedPortionUnit

        viewModelScope.launch {
            if (directCarbs != null && unit != null && unit.conversion is PortionConversion.DirectCarbs) {
                val count = PortionParser.parse(_state.value.countText) ?: return@launch
                repository.addDirectCarbMealItem(
                    productBarcode = barcode,
                    displayName = product.name,
                    portionDescription = portionDescription,
                    count = count,
                    carbsPerUnit = (unit.conversion as PortionConversion.DirectCarbs).carbsPerUnit,
                    exactCarbs = directCarbs,
                )
            } else {
                val result = _state.value.result ?: return@launch
                val resolved = PortionParser.parse(_state.value.portionText) ?: return@launch
                repository.addMealItem(
                    productBarcode = barcode,
                    displayName = product.name,
                    portionDescription = portionDescription,
                    resolvedAmount = resolved,
                    basis = product.basis,
                    carbsPer100 = product.carbsPer100,
                    exactCarbs = result.exact,
                )
            }
            rememberUsage()
            _state.update { it.copy(addedToMeal = true) }
        }
    }
```

`rememberUsage` parses `portionText`, which is empty in direct-carb mode. Make it record the count instead:

```kotlin
    fun rememberUsage() {
        val product = _state.value.product ?: return
        if (_state.value.unsaved || product.barcode.isEmpty()) return
        val mode = _state.value.inputMode
        val count = if (mode == InputMode.PORTION_UNIT) PortionParser.parse(_state.value.countText) else null
        // A direct-carb portion has no grams to remember; the count is the whole portion.
        val portion = PortionParser.parse(_state.value.portionText) ?: count ?: return
        viewModelScope.launch {
            repository.recordUse(
                product.barcode,
                portion,
                mode = mode,
                portionUnitId = _state.value.selectedPortionUnitId,
                count = count,
            )
        }
    }
```

Add imports: `app.justthecarbs.domain.PortionConversion`, `app.justthecarbs.domain.DirectCarbCalculator`.

- [ ] **Step 6: Run to verify it passes**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ui.product.*"
```

Expected: the 5 new tests PASS and every pre-existing ViewModel test still PASSES — especially the session-immutability regression tests, which must be untouched.

---

## Task 15: Product UI — two-mode add/edit and conversion-aware display (spec §14, §15)

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/product/ProductScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/kotlin/app/justthecarbs/ui/product/` (extend the existing calculator UI test file)

**Interfaces:**
- Consumes: `ProductUiState.directCarbResult`, `PortionConversion`, `ProductViewModel.addPortionUnit`/`correctSelectedPortionUnit` (Task 14).
- Produces: no new API.

- [ ] **Step 1: Add the strings**

In `strings.xml`, alongside the existing `product_one_unit_equals`:

```xml
    <string name="product_unit_mode_weight">Weight</string>
    <string name="product_unit_mode_carbs">Carbs per unit</string>
    <string name="product_one_unit_equals_carbs">1 %1$s contains</string>
    <string name="product_unit_carbs_suffix">g carbs</string>
    <!-- e.g. "4 slices × 14.2 g carbs" — the direct-carb equivalent of the weight breakdown. -->
    <string name="product_direct_carb_breakdown">%1$s × %2$s g carbs</string>
```

- [ ] **Step 2: Make the per-unit displays conversion-aware**

Three sites in `ProductScreen.kt` read `unit.amountPerUnit`/`unit.basis` (lines ~944–946, ~1025, ~1054, ~1105). Each becomes a `when` on `unit.conversion`.

At the breakdown line (~944), replace the weight-only text with:

```kotlin
        when (val conversion = unit.conversion) {
            is PortionConversion.WeightBased -> stringResource(
                R.string.product_unit_breakdown,
                unit.unitLabel(count = pluralQuantity),
                conversion.amountPerUnit.stripTrailingZeros().toPlainString(),
                conversion.basis.unitLabel,
            )
            // No grams anywhere on this path — the user was never asked for a weight and must not
            // be shown one.
            is PortionConversion.DirectCarbs -> stringResource(
                R.string.product_direct_carb_breakdown,
                unit.unitLabel(count = pluralQuantity),
                conversion.carbsPerUnit.stripTrailingZeros().toPlainString(),
            )
        }
```

At the inline correction form (~1025), seed the editor from whichever value the unit holds:

```kotlin
    var amountText by rememberSaveable(unit.id) {
        mutableStateOf(
            when (val conversion = unit.conversion) {
                is PortionConversion.WeightBased -> conversion.amountPerUnit.stripTrailingZeros().toPlainString()
                is PortionConversion.DirectCarbs -> conversion.carbsPerUnit.stripTrailingZeros().toPlainString()
            },
        )
    }
```

and its suffix/label (~1044, ~1054):

```kotlin
            Text(
                text = when (unit.conversion) {
                    is PortionConversion.WeightBased ->
                        stringResource(R.string.product_one_unit_equals, unit.unitLabel(count = 1))
                    is PortionConversion.DirectCarbs ->
                        stringResource(R.string.product_one_unit_equals_carbs, unit.unitLabel(count = 1))
                },
            )
            // ...
                suffix = {
                    Text(
                        when (val conversion = unit.conversion) {
                            is PortionConversion.WeightBased -> conversion.basis.unitLabel
                            is PortionConversion.DirectCarbs -> stringResource(R.string.product_unit_carbs_suffix)
                        },
                    )
                },
```

The confirm button hands back a conversion of the same kind:

```kotlin
                onConfirm = {
                    val amount = PortionParser.parse(amountText) ?: return@Button
                    if (amount.signum() <= 0) return@Button
                    onCorrect(
                        when (val conversion = unit.conversion) {
                            is PortionConversion.WeightBased ->
                                PortionConversion.WeightBased(amount, conversion.basis)
                            is PortionConversion.DirectCarbs -> PortionConversion.DirectCarbs(amount)
                        },
                    )
                },
```

- [ ] **Step 3: Add the two-mode toggle to the add form**

In the add-portion-unit form (the composable behind `showAddPortionUnitForm`), add a mode selector above the amount field and build the conversion from it:

```kotlin
    var weightMode by rememberSaveable { mutableStateOf(true) }

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = weightMode,
            onClick = { weightMode = true },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(stringResource(R.string.product_unit_mode_weight)) }
        SegmentedButton(
            selected = !weightMode,
            onClick = { weightMode = false },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(stringResource(R.string.product_unit_mode_carbs)) }
    }
```

and on save:

```kotlin
            val amount = PortionParser.parse(amountText) ?: return@Button
            if (amount.signum() <= 0) return@Button
            onAdd(
                selectedKind,
                if (weightMode) {
                    PortionConversion.WeightBased(amount, product.basis)
                } else {
                    PortionConversion.DirectCarbs(amount)
                },
                customLabel.takeIf { selectedKind == PortionUnitKind.CUSTOM },
            )
```

Only one field is ever shown, so "neither is required if the other is chosen" holds by construction — there is no second field to leave blank. The suffix follows the mode: `product.basis.unitLabel` for weight, `R.string.product_unit_carbs_suffix` for carbs.

- [ ] **Step 4: Show the direct-carb result**

Wherever the screen renders `state.result` into the hero figure, add the direct-carb branch. `ResultFormatter.decimal`/`whole` take raw values, so no `CarbResult` is needed:

```kotlin
    val exact = state.result?.exact ?: state.directCarbResult
    if (exact != null) {
        // Same hierarchy as the weight-based path: decimal dominant, whole grams beneath
        // (owner decision #2). The formatter is shared, so the two paths cannot drift apart.
        ResultDisplay(
            decimal = ResultFormatter.decimal(exact, locale),
            whole = ResultFormatter.whole(exact.setScale(0, RoundingMode.HALF_UP).toInt(), locale),
        )
    }
```

Match the existing call shape in the file — if it currently passes a `CarbResult` into a `ResultDisplay`-style composable, change that composable to take the two formatted strings rather than duplicating it.

- [ ] **Step 5: Extend the instrumented UI test**

In the existing calculator UI test file, add:

```kotlin
    @Test
    fun aDirectCarbUnitShowsCarbsPerUnitAndNeverGrams() {
        // A product whose only countable unit knows carbs, not weight.
        composeRule.setContent { /* the screen, seeded with a DirectCarbs unit — match the file's harness */ }

        composeRule.onNodeWithText("Slices").performClick()
        composeRule.onNodeWithTag("count_field").performScrollTo().performTextInput("4")

        composeRule.onNodeWithText("56.8 g", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("× 35 g", substring = true).assertDoesNotExist()
    }
```

`performScrollTo()` before interacting is mandatory here — a control covered by the soft keyboard is not clickable and `performClick()` silently no-ops on it, which is what produced the previously "flaky" test.

- [ ] **Step 6: Build and run the JVM suite**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL and all JVM tests PASS. (Instrumented tests run in Task 17.)

---

## Task 16: OCR → "Save as portion unit" (spec §17)

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ocr/LabelAnalyzer.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/scan/LabelScannerScreen.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/ui/JustTheCarbsNavHost.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `NutritionParseReport.servingCandidate` (Task 7), `PortionConversion` (Task 8), `ProductRepository.saveUserPortionUnit` (Task 12).
- Produces: `LabelAnalyzer.analyzeStill(context, file, onComplete: (NutritionParseReport) -> Unit)`.

Shown **only** when the report is `Confident`, `servingCandidate` is non-null, and `servingCandidate.descriptor` is non-null. Never from a live frame — only from a still capture that already passed the explicit accept step.

- [ ] **Step 1: Widen the analyzer callback**

In `LabelAnalyzer.kt`, change `StillRequest.onComplete` and `analyzeStill` from `(LabelReading) -> Unit` to `(NutritionParseReport) -> Unit`. The three failure paths pass a report instead of a bare reading:

```kotlin
    fun analyzeStill(context: Context, file: File, onComplete: (NutritionParseReport) -> Unit) {
        val request = StillRequest(context.applicationContext, file, onComplete)
        // ... unchanged ...
            replaced.onComplete(NutritionParseReport(LabelReading.NotFound, emptyList()))
```

and the success path drops its `.reading`:

```kotlin
                    request.onComplete(parse(text, dimensions.outWidth, dimensions.outHeight, started))
```

`parse` already returns `NutritionParseReport`, so this is a narrowing removal, not new work. **Do not change the live-frame path** — `AmbiguityStabilityTracker` still keys off `LabelReading` alone, exactly as spec §5 requires.

- [ ] **Step 2: Thread the save callback into the scanner**

In `LabelScannerScreen.kt`, extend the signature:

```kotlin
fun LabelScannerScreen(
    onUseValue: (BigDecimal, NutritionBasis) -> Unit,
    onEditManually: () -> Unit,
    onClose: () -> Unit,
    /** Null when there is no product to attach a unit to (a scan with no barcode context). */
    onSavePortionUnit: ((PortionUnitKind, PortionConversion) -> Unit)? = null,
)
```

Hold the report rather than only the reading:

```kotlin
    var report by remember { mutableStateOf<NutritionParseReport?>(null) }
    // ...
                    analyzer.analyzeStill(context, file) { result -> report = result }
```

and in the `ProposalCard` branch, add the affordance:

```kotlin
                val serving = report?.servingCandidate
                val descriptor = serving?.descriptor
                if (onSavePortionUnit != null && descriptor != null && reading is LabelReading.Confident) {
                    TextButton(
                        onClick = {
                            // Explicit acceptance, from a still capture only. The count comes off the
                            // typed descriptor — the header text is never re-parsed here.
                            onSavePortionUnit(
                                descriptor.kind,
                                PortionConversion.DirectCarbs(
                                    serving.carbsPerServing
                                        .divide(descriptor.count, 4, RoundingMode.HALF_UP)
                                        .stripTrailingZeros(),
                                ),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.label_save_as_portion_unit, descriptor.kind.name.lowercase()))
                    }
                }
```

Add the string:

```xml
    <!-- e.g. "Save as a slice portion" — offered only when the label named a countable unit. -->
    <string name="label_save_as_portion_unit">Save as a %1$s portion</string>
```

Spec §17 also allows saving `WeightBased` when the same capture read a weight-bearing descriptor. `ServingCarbCandidate.descriptor.weightOrVolume` carries that when present, so prefer it:

```kotlin
                                descriptor.amountPerUnit?.let { perUnit ->
                                    PortionConversion.WeightBased(perUnit.amount, perUnit.basis)
                                } ?: PortionConversion.DirectCarbs(/* as above */),
```

- [ ] **Step 3: Wire the nav host**

At the `LABEL_SCAN` route (`JustTheCarbsNavHost.kt` ~330–409), `barcode` is already available as a route param. Pass a callback that saves through the repository:

```kotlin
                onSavePortionUnit = barcode?.takeIf { it.isNotEmpty() }?.let { code ->
                    { kind, conversion ->
                        scope.launch {
                            container.productRepository.saveUserPortionUnit(
                                barcode = code,
                                kind = kind,
                                conversion = conversion,
                            )
                        }
                        Unit
                    }
                },
```

Match the file's existing scope/container access pattern — if the route has no `scope`, use `rememberCoroutineScope()` at the composable's top, as neighbouring routes do.

Provenance note: `saveUserPortionUnit` stamps `MANUAL`/`USER_VERIFIED`. Spec §17 asks for `ProductDataOrigin.OCR`. Since the user explicitly tapped to accept a value they read off a package, `USER_VERIFIED` is right; the provenance should record that it came from OCR. Add an `origin` parameter defaulting to `MANUAL`:

```kotlin
    suspend fun saveUserPortionUnit(
        barcode: String,
        kind: PortionUnitKind,
        conversion: PortionConversion,
        customLabel: String? = null,
        origin: ProductDataOrigin = ProductDataOrigin.MANUAL,
    ): PortionUnit {
        require(origin.isUserAuthored) { "$origin is not a user-authored origin" }
        // ... dataSource = origin
```

and pass `origin = ProductDataOrigin.OCR` from the scanner path. Verify `ProductDataOrigin.OCR.isUserAuthored` is `true` before relying on the `require`; if it is not, drop the `require` for this call and document why in one line.

- [ ] **Step 4: Build**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

---

## Task 17: Full verification (spec §23)

**Files:** none modified unless a failure demands it.

- [ ] **Step 1: JVM unit tests**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:testDebugUnitTest
```

Expected: all PASS. Record the total count (the baseline before this pass was 259).

- [ ] **Step 2: Lint**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:lintDebug
```

Expected: clean, matching the project's existing standard.

- [ ] **Step 3: Debug build**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:assembleDebug
```

- [ ] **Step 4: Minified release build**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:assembleRelease
```

Expected: BUILD SUCCESSFUL (unsigned unless a `keystore.properties` exists — do not create one). R8 risk to check: `PortionConversion` and `MealItemKind` are read via `valueOf`/serialization paths, and `proguard-rules.pro` keeps `app.justthecarbs.**`. Confirm the new domain types fall under an existing keep rule; if a rule is added, note it in the report.

- [ ] **Step 5: Instrumented tests**

Start the emulator (boot takes ~90 s):

```powershell
C:\atools\sdk\emulator\emulator.exe -avd carbscan -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect
```

Then, once `C:\atools\sdk\platform-tools\adb.exe devices` shows it:

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"; $env:ANDROID_HOME="C:\atools\sdk"; .\gradlew.bat :app:connectedDebugAndroidTest
```

Expected: all PASS, including the five new v5→v6 migration tests. Record the count (baseline 106).

If the emulator cannot be started in this environment, **say so plainly in the report and mark the instrumented suite as not run** — do not describe it as passing. The migration tests in particular are the ones that would catch a bad `MIGRATION_5_6`, so an unrun suite is a real gap, not a formality.

- [ ] **Step 6: Signing-material check**

```powershell
git status --porcelain
git ls-files | Select-String -Pattern "\.jks$|\.keystore$|keystore\.properties$|local\.properties$"
```

Expected: no `.jks`, no `keystore.properties`, no `local.properties`, no secrets staged or tracked. The three pre-existing cosmetic modifications (`HomeScreen.kt`, `ic_launcher_foreground.xml`, `colors.xml`) must still be present and unmodified by this work.

- [ ] **Step 7: Confirm nothing was committed**

```powershell
git log --oneline -3
```

Expected: HEAD is still the same commit it was at the start of implementation. All work from this plan is uncommitted.

---

## Task 18: Documentation (spec §24)

**Files:**
- Modify: `CLAUDE.md`
- Modify: `docs/manual-qa.md`

Living docs only. **Do not** touch historical prose under `docs/superpowers/specs/` or `docs/superpowers/plans/` (other than this plan).

- [ ] **Step 1: Update `CLAUDE.md`**

In the **Countable portions** section, add:

```markdown
### Direct-carb conversions (2026-08-15)

`PortionUnit` no longer stores `amountPerUnit`/`basis`. It stores a sealed
`PortionConversion`:

- `WeightBased(amountPerUnit, basis)` — "1 slice = 35 g", resolved via `PortionResolver` and
  `CarbCalculator` exactly as before.
- `DirectCarbs(carbsPerUnit)` — "1 slice = 14.2 g carbs", used when OFF gives
  `carbohydrates_serving` but `serving_size` prints no weight. `DirectCarbCalculator` is the only
  place `count × carbsPerUnit` happens. **No gram figure exists on this path and none is invented** —
  `portionText` stays empty, and a direct-carb `MealItem` has `resolvedAmount == null`.

OFF precedence: a printed weight always wins (Cases A and C); no weight plus `carbohydrates_serving`
gives `DirectCarbs` (Case B); neither gives **no candidate at all** (Case D) and the UI asks once.

The freeze rule is unchanged and applies identically to both kinds: `isRemoteRefreshable` keys on
provenance and verification, never on which conversion the unit holds.
```

In the **Architecture** section, update the Room line to v6 and add:

```markdown
- Room schema is at **v6**; `MIGRATION_5_6` rebuilds **both** `portion_units` (weight columns →
  `conversionKind`/`conversionValue`/`conversionBasis` + six remote variants) and
  `current_meal_items` (adds `itemKind`, makes `resolvedAmount`/`basis`/`carbsPer100` nullable).
  Rebuild-and-copy, because SQLite cannot drop `NOT NULL` in place. **Row ids are preserved**, so
  `portion_usage.portionUnitId` still resolves. Every migrated row is explicitly labelled
  (`'WEIGHT'` / `'WEIGHT_BASED'`) — never left NULL for a mapper to infer.
```

Add a new section on the OCR rewrite:

```markdown
## Geometry-first nutrition table parsing (2026-08-15)

The OCR parser previously grouped text into rows using ML Kit's `blockId`/`lineId` and then scored
candidates by proximity. On real multi-column and hierarchical labels that could return a child
nutrient's value as total carbohydrate — ML Kit both splits one printed row across several lines and
merges two printed rows into one, and a proximity score can be outvoted by geometry.

The pipeline is now four pure-Kotlin stages under `ocr/`:

1. `LogicalRowBuilder` — rows from box geometry alone (vertical overlap ≥ 0.5 against the running row
   box, centre-distance tiebreaker at 0.6 median heights). `blockId`/`lineId` are retained for
   diagnostics and **never** consulted for row membership. Thresholds live in `LogicalRowThresholds`,
   deliberately separate from and stricter than `NutritionParserThresholds`.
2. `RowClassifier` — `TOTAL_CARBOHYDRATE` / `CARBOHYDRATE_CHILD` / `HEADER` / `OTHER`. A row naming
   any child nutrient (sugars, polyols, starch, fibre, dextrose, glucose, fructose, sucrose, lactose,
   maltose, …) is `CARBOHYDRATE_CHILD` **unconditionally** — a type-level exclusion, not a score
   penalty. This is the correctness claim of the whole rewrite.
3. `ColumnClassifier` — `PER_100_G` / `PER_100_ML` / `PER_SERVING` / `REFERENCE_PERCENT` / `UNKNOWN`.
   Headers are the primary signal; a cell-shape fallback recovers a percent column whose header OCR
   lost. It never guesses per-100 vs per-serving from shape — those stay `UNKNOWN`, and an `UNKNOWN`
   cell is never used.
4. `NutritionTableInterpreter` — associates the total row's cells to columns and produces the
   unchanged `LabelReading` plus a new `servingCandidate`.

`NutritionTableParser` is now a thin adapter. `LabelReading`, `CarbCandidate` and
`AmbiguityStabilityTracker` are unchanged, so live-scan stability behaviour is untouched.
```

Under **Open findings needing the owner**, add:

```markdown
7. **Direct-carb countable portions against real packaging.** The Case B path (per-serving carbs, no
   printed weight) is covered by fixtures and emulator runs only. No live OFF product with that exact
   shape has been scanned and checked against its package.
8. **The rebuilt OCR table interpreter on physical hardware.** All seven adversarial fixtures pass as
   unit tests; the two real-device failures that motivated this pass have not been re-tested on the
   original packages.
```

- [ ] **Step 2: Update `docs/manual-qa.md`**

Add unchecked rows:

```markdown
- [ ] §15b Scan a product whose OFF `serving_size` names a unit with no weight (e.g. "2 slices") and
      whose `carbohydrates_serving` is present. Confirm the countable unit appears, the count field
      accepts "4", the result matches 4 × (serving carbs ÷ 2), and **no gram figure is shown**.
- [ ] §15c Add that direct-carb portion to the meal. Confirm the line reads "4 slices", the total is
      correct, and the item survives a force-stop and relaunch.
- [ ] §15d Photograph a multi-column label with a "of which sugars" row and a %RI column. Confirm the
      total carbohydrate is reported, not the sugars figure and not the percentage.
- [ ] §15e Photograph a label whose serving column names a unit ("per slice"). Confirm the
      "Save as a slice portion" action appears after the still capture and creates a usable unit.
```

- [ ] **Step 3: Verify the docs build nothing and change nothing else**

```powershell
git status --porcelain
```

Expected: only the intended files, plus the three pre-existing cosmetic modifications.

---

## Task 19: Final implementation report (spec §25)

- [ ] **Step 1: Write the 25-point report**

Deliver it in chat (not as a file unless asked), covering: what changed per spec section; every test added and the before/after counts; every pre-existing test whose expectation changed and why; which builds ran and their outcomes; whether instrumented tests actually ran; the signing-material check result; confirmation that nothing was committed; any defect found and fixed in passing; and anything deliberately left undone.

State plainly anything that was **not** verified. Do not describe an unrun suite as passing.

---

## Self-Review

**Spec coverage:**

| Spec section | Task |
|---|---|
| §0 Search invalidation | 1 |
| §1 `LogicalRowBuilder` | 3 |
| §2 Row classification + child terms | 2, 4 |
| §3 Column classification | 5 |
| §4 Cell association + confidence | 7 |
| §5 `NutritionParseReport`/`ServingCarbCandidate` | 7 |
| §6 `ServingDescriptor` split | 6 |
| §7 OFF `carbohydrates_serving` | 10 (validator), 13 (DTO/mapping) |
| §8 `PortionConversion` | 8, 9 |
| §9 Conversion precedence A–D | 13 |
| §10 Provenance/verification preserved | 9, 12 |
| §11 Room v6 | 11 |
| §12 Meal persistence, no fake grams | 10, 11, 12 |
| §13 Centralized math | 8 |
| §14 Product UI direct-carb | 14, 15 |
| §15 Two-mode add/edit | 15 |
| §16 OFF worked examples | 13 |
| §17 OCR → save | 16 |
| §18 OCR regression fixtures | 7 |
| §19 Portion/domain tests | 6, 8, 9, 10, 11, 12, 13, 14 |
| §20 No regressions | 1, 7, 12, 14, 17 |
| §23 Verification | 17 |
| §24 Documentation | 18 |
| §25 Final report | 19 |

**Type consistency check:** `PortionConversion.WeightBased(amountPerUnit, basis)` and `DirectCarbs(carbsPerUnit)` are used with those exact property names in Tasks 8–16. `MealItem.weightBased`/`directCarbs` factories are used consistently in Tasks 10, 11, 12. `ServingDescriptor.amountPerUnit` (Task 6) is consumed in Tasks 13 and 16. `NutritionParseReport`'s third parameter is `servingCandidate` throughout.

**Known ordering constraint:** Task 9 leaves the tree temporarily compiling via mechanical edits that Tasks 11–15 then do properly. Do not run the full suite between 9 and 11 and treat failures there as real — the checkpoint is the end of Task 15.

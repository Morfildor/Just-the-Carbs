# Real-Image OCR Generalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generalize the nutrition-table parser across nine real package photographs — adding a prose-label reader, a tabular weight-based serving column, and provenance diagnostics — without weakening any semantic safeguard.

**Architecture:** The geometry-first pipeline (`LogicalRowBuilder` → `RowClassifier` → `ColumnClassifier` → `NutritionTableInterpreter`) is unchanged for tabular labels. A new pure `ProseNutritionReader` runs **only** on tabular `NotFound` **and** only when a positive prose-eligibility predicate holds, so it cannot touch any label the table path already reads. Provenance is added to `NutritionParseReport` at row granularity for tabular results and span granularity for prose results.

**Tech Stack:** Kotlin (pure, Android-free in `ocr/`), JUnit4 JVM tests, AndroidX instrumentation tests, ML Kit Text Recognition v2, Gradle/AGP 9.3.1, JDK 21.

**Spec:** `docs/superpowers/specs/2026-08-16-real-image-ocr-generalization-design.md`

## Global Constraints

- **A false confident value is substantially worse than `NotFound`.** Never trade a semantic safeguard for recognition rate.
- **No overfitting.** Forbidden in production code: value literals from fixtures (`61.9`, `53.5`, `46`), brand names, fixture filenames, per-fixture coordinates. Every change must be stated as a general rule about a class of labels.
- **`ocr/` stays pure Kotlin** — zero Android imports, JVM-testable with no emulator. ML Kit types cross the boundary only in `MlKitOcrMapper` and `LabelAnalyzer`.
- **`blockId`/`lineId` are never a row-membership signal.** Diagnostics only. `RowSlopeEstimator` may consume them for the global skew scalar and nothing else.
- **Child-nutrient exclusion beats total-carbohydrate**, always. Row-scoped on the tabular path; span-scoped **only** inside `ProseNutritionReader`.
- **Prose reader is per-100 only.** It never emits a serving, piece, or count figure.
- **Prose activation gate:** tabular `NotFound` only — never `Confident`, never `Ambiguous` — AND prose-eligibility predicate true.
- **Do not loosen global geometry tolerances** to fix a fixture. Measure, then change the smallest general rule.
- **Golden values are settled evidence** (owner-confirmed 2026-08-16). If the parser disagrees with the spec's transcription table, re-read the photograph before concluding the parser is wrong.
- **Barcode code is out of scope.** Do not modify `BarcodeStabilityTracker`, `BarcodeFrameReader`, or `BarcodeAnalyzer`.
- **Do not commit.** The entire pass is left uncommitted for owner review (owner instruction §22). Steps below say `git add -A --dry-run` where a commit would normally go.
- **Build env:** `$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"`, `$env:ANDROID_HOME="C:\atools\sdk"`, emulator AVD `carbscan` (already running on `emulator-5554`).
- **Instrumented-test filtering uses the runner argument, never `--tests`.** `:app:connectedDebugAndroidTest` does not accept Gradle's `--tests` filter; use `-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>` (verified working during Task 2). `--tests` remains correct for `:app:testDebugUnitTest`, which is an ordinary Gradle `Test` task.

### The nine fixtures and their golden values

| # | Fixture | Total | Basis | Serving | Must never be returned |
|---|---|---|---|---|---|
| 1 | `real_juice_bilingual_per100ml_01.jpg` | 9.0 | PER_100_ML | none | — (sugars == total; provenance proves it) |
| 2 | `real_grated_cheese_multicolumn_02.jpg` | 2.0 | PER_100_G | 1.0 g / 50 g | 0.5, 50 |
| 3 | `real_jar_prose_multilingual_03.jpg` | 1.6 | PER_100_G | none (asserted absent) | 20, 500 |
| 4 | `real_lid_prose_curved_04.jpg` | 3 | PER_100_G | none (asserted absent) | 2.5, 19, 125 |
| 5 | `real_witte_kaas_single_column_05.jpg` | 2.3 | PER_100_G | none | 200 |
| 6 | `real_stokbrood_prose_dense_06.jpg` | 46 | PER_100_G | none (asserted absent) | 1.0, 4.7, 12 |
| 7 | `real_yoghurt_serving_column_07.jpg` | 5.0 | PER_100_G | 7.5 g / 150 g | 3.0, 150 |
| 8 | `sondey_multilingual_100g.jpg` | 61.9 | PER_100_G | none | 47.6 |
| 9 | `kinder_multicolumn_piece.jpg` | 53.5 | PER_100_G | 6.7 / PIECE / 12.5 g | 3, 7, 53.3 |

---

## File Structure

**Created:**
- `app/src/main/kotlin/app/justthecarbs/ocr/ProseNutritionReader.kt` — the prose stage: eligibility predicate, declaration/basis binding, span-scoped exclusion, span-level result.
- `app/src/main/kotlin/app/justthecarbs/ocr/CandidateProvenance.kt` — the sealed provenance type (row vs span).
- `app/src/test/kotlin/app/justthecarbs/ocr/ProseNutritionReaderTest.kt`
- `app/src/test/kotlin/app/justthecarbs/ocr/ProseEligibilityTest.kt`
- `app/src/test/kotlin/app/justthecarbs/ocr/ServingColumnWeightTest.kt`
- `app/src/test/kotlin/app/justthecarbs/ocr/CandidateProvenanceTest.kt`
- `app/src/androidTest/assets/ocr_real/*.jpg` — nine sanitized fixtures.
- `tools/derive-ocr-fixtures.md` — how the crops were produced, for reproducibility.

**Modified:**
- `NutritionTableParser.kt` — `NutritionParseReport` gains `provenance`; `CarbCandidate` unchanged.
- `NutritionTableInterpreter.kt` — emits provenance; delegates to `ProseNutritionReader` at the two `NotFound` exits (lines 48 and 168).
- `NutritionColumnKind.kt` — `ColumnClassifier` recognizes a serving header carrying its own weight.
- `OcrDiagnosticsReport.kt` — renders provenance.
- `RealImageOcrTest.kt` — nine fixtures, mandatory (no `assumeTrue`).
- `.gitignore` — track sanitized fixtures, keep full-frame originals ignored.
- `app/src/androidTest/assets/ocr_real/README.md` — document the nine.
- `CLAUDE.md`, `docs/manual-qa.md` — record the pass; keep the camera gate open.

---

## Task 1: Sanitized fixture corpus

**Files:**
- Create: `app/src/androidTest/assets/ocr_real/real_juice_bilingual_per100ml_01.jpg` (+ six more; Sondey/Kinder already present, to be cropped)
- Create: `tools/derive-ocr-fixtures.md`
- Modify: `.gitignore`
- Modify: `app/src/androidTest/assets/ocr_real/README.md`

**Interfaces:**
- Consumes: nothing.
- Produces: nine committed JPEGs at the exact filenames in the table above; every later task's real-image test depends on these names.

- [ ] **Step 1: Record the originals before touching anything**

The seven originals are source evidence and must not be edited, recompressed, rotated or renamed. Record their properties into `tools/derive-ocr-fixtures.md`:

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"
# For each original, record: filename, pixel dimensions, EXIF orientation, byte size.
# Use any read-only inspector; do NOT write back to the file.
```

Write the table into `tools/derive-ocr-fixtures.md` under a heading `## Source originals (never modified)`.

- [ ] **Step 2: Produce the crops**

Crop each original to the nutrition panel plus the headers and serving text needed to interpret it. Remove hands, background, unrelated packaging.

**Cropping only.** Explicitly forbidden: deskew, rotation correction, sharpening, contrast normalization, denoise, upscaling. The tilt, glare, curvature and JPEG compression are the variables under test — a cleaned fixture would pass while proving nothing. Save at JPEG quality 95 or higher.

Record each crop's source rectangle in `tools/derive-ocr-fixtures.md` so the derivation is reproducible.

Also crop the two existing full-frame photographs to `sondey_multilingual_100g.jpg` and `kinder_multicolumn_piece.jpg` (same filenames — they replace the gitignored full frames in the assets dir).

- [ ] **Step 3: Flip the gitignore policy**

Replace the existing block in `.gitignore`:

```gitignore
# Sanitized nutrition-table fixtures for RealImageOcrTest are COMMITTED (2026-08-16), so CI cannot
# silently skip the highest-value regression tests. They are cropped to the nutrition panel only —
# no surroundings, no people, no unrelated packaging. Full-frame originals stay local.
app/src/androidTest/assets/ocr_real/originals/
```

Delete the previous `app/src/androidTest/assets/ocr_real/*` / `!README.md` pair.

- [ ] **Step 4: Verify exactly nine fixtures are tracked and no original is**

```powershell
git status --short app/src/androidTest/assets/ocr_real/
git check-ignore -v app/src/androidTest/assets/ocr_real/originals/ 2>&1
```

Expected: nine `.jpg` files plus `README.md` staged as additions; the `originals/` directory ignored. Confirm no `*.jks`, `keystore.properties`, `local.properties`, APK/AAB or full-frame photo appears.

- [ ] **Step 5: Rewrite the README for nine mandatory fixtures**

Update `app/src/androidTest/assets/ocr_real/README.md`: the nine filenames with their golden values (copy the table from this plan), the fact that they are **committed and mandatory**, that a missing fixture **fails** rather than skips, and that sanitization was cropping only. Remove the "why these are not committed" section and the "every case skips" paragraph — both are now false.

- [ ] **Step 6: Stage check (no commit — owner review pending)**

```bash
git add -A --dry-run
git diff --check
```

---

## Task 2: Nine-fixture baseline, before any production change

**Files:**
- Create: `app/src/androidTest/kotlin/app/justthecarbs/ocr/RealImageBaselineTest.kt`
- Create: `docs/plans/2026-08-16-real-image-baseline.md`

**Interfaces:**
- Consumes: the nine fixtures from Task 1.
- Produces: `docs/plans/2026-08-16-real-image-baseline.md` — the measured per-fixture failure stage that every later task's justification refers to.

**This task must complete before any production code changes.** Fixing a stage before measuring which stage failed is how a downstream heuristic ends up hiding an upstream bug.

- [ ] **Step 1: Write the baseline harness**

A test that runs every fixture through the production pipeline and dumps diagnostics rather than asserting. It is a measurement instrument, not a regression test.

```kotlin
package app.justthecarbs.ocr

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Measurement, not regression. Prints the full pipeline state for every fixture so the failure STAGE
 * is known before any production code is touched. Delete or keep as a diagnostic aid — it asserts
 * nothing and can never fail for a parser reason.
 */
class RealImageBaselineTest {

    private val testContext get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun dumpEveryFixture() {
        FIXTURES.forEach { name ->
            val stream = testContext.assets.open("ocr_real/$name")
            val bitmap = stream.use { BitmapFactory.decodeStream(it) }
            checkNotNull(bitmap) { "$name did not decode" }

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val latch = CountDownLatch(1)
            var text: Text? = null
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { text = it; latch.countDown() }
                .addOnFailureListener { latch.countDown() }
            latch.await(30, TimeUnit.SECONDS)
            recognizer.close()

            val document = MlKitOcrMapper.toDocument(text!!, bitmap.width, bitmap.height)
            val report = NutritionTableParser.parseWithDiagnostics(document)

            Log.i(TAG, "===== $name ${bitmap.width}x${bitmap.height} =====")
            Log.i(TAG, "RAW: ${text!!.text.replace("\n", " | ")}")
            Log.i(TAG, OcrDiagnosticsReport.render(document, report))
            Log.i(TAG, "READING: ${report.reading}")
            Log.i(TAG, "SERVING: ${report.servingCandidate}")
        }
    }

    private companion object {
        const val TAG = "OcrBaseline"
        val FIXTURES = listOf(
            "real_juice_bilingual_per100ml_01.jpg",
            "real_grated_cheese_multicolumn_02.jpg",
            "real_jar_prose_multilingual_03.jpg",
            "real_lid_prose_curved_04.jpg",
            "real_witte_kaas_single_column_05.jpg",
            "real_stokbrood_prose_dense_06.jpg",
            "real_yoghurt_serving_column_07.jpg",
            "sondey_multilingual_100g.jpg",
            "kinder_multicolumn_piece.jpg",
        )
    }
}
```

- [ ] **Step 2: Run it and capture the output**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"
$env:ANDROID_HOME="C:\atools\sdk"
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=app.justthecarbs.ocr.RealImageBaselineTest
```

Then capture the log (the test prints via `Log.i`, which `connectedAndroidTest` does not echo):

```powershell
C:\atools\sdk\platform-tools\adb.exe logcat -d -s OcrBaseline:I > baseline.txt
```

Note: `connectedAndroidTest` **uninstalls the app afterwards**. That is fine here — nothing later in this task needs it installed.

- [ ] **Step 3: Write the baseline document**

Create `docs/plans/2026-08-16-real-image-baseline.md` with one section per fixture recording: raw ML Kit text, logical rows, row classifications, detected columns, numeric candidates, final `LabelReading`, serving candidate, and the diagnostic reason.

Then the comparison table:

| Fixture | Expected | Actual | Outcome | Failure stage |
|---|---|---|---|---|

Failure-stage vocabulary (use exactly these): `ML_KIT_RECOGNITION`, `OCR_MAPPING`, `ROW_RECONSTRUCTION`, `ROW_CLASSIFICATION`, `TERMINOLOGY`, `COLUMN_CLASSIFICATION`, `CELL_ASSOCIATION`, `NUMERIC_PARSING`, `PERCENT_FILTERING`, `SERVING_INTERPRETATION`, `BASIS_INTERPRETATION`, `OTHER`.

- [ ] **Step 4: Classify each failure as recognition vs interpretation**

For every non-passing fixture answer explicitly: **did ML Kit actually recognize the required words and numbers?**

- **Recognized but lost** → interpretation failure. Fix the stage that lost it. This is the expected case for photos 3, 4 and 6.
- **Not recognized** → recognition failure. Do **not** patch parser semantics to compensate for absent text. Record it as a preprocessing candidate for Task 8 and nothing more.

Record the answer per fixture in the baseline document. This determines which later tasks are needed at all.

- [ ] **Step 5: Checkpoint — report to the owner**

Report the baseline table before proceeding. If it reveals a concrete incompatibility with the approved design (for example: ML Kit fragments photo 3's sentence so badly that no declaration relationship survives, making the spec's structural basis rule unimplementable), stop and raise it. Otherwise continue to Task 3.

---

## Task 3: Candidate provenance

**Files:**
- Create: `app/src/main/kotlin/app/justthecarbs/ocr/CandidateProvenance.kt`
- Create: `app/src/test/kotlin/app/justthecarbs/ocr/CandidateProvenanceTest.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/ocr/NutritionTableParser.kt:55-60`
- Modify: `app/src/main/kotlin/app/justthecarbs/ocr/NutritionTableInterpreter.kt:165-186`
- Modify: `app/src/main/kotlin/app/justthecarbs/ocr/OcrDiagnosticsReport.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `CandidateProvenance` sealed interface with `FromRow(rowText: String, rowBox: OcrBox)` and `FromProseSpan(nutrientTerm: String, valueElementIndices: Set<Int>, rowText: String)`; `NutritionParseReport.provenance: CandidateProvenance?`. Tasks 4 and 6 both populate it.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateProvenanceTest {

    /**
     * A label whose total and its child print the SAME value. Asserting the number alone cannot
     * distinguish a correct read from a sugars read, which is the case five of the seven new real
     * photographs present. Provenance is what makes such a test mean anything.
     */
    @Test
    fun tabularResultReportsTheRowItsValueCameFrom() {
        val document = OcrDocument(
            width = 1000,
            height = 400,
            elements = listOf(
                element("Voedingswaarde", 40, 20, 300, 60),
                element("per", 320, 20, 380, 60),
                element("100", 390, 20, 450, 60),
                element("g", 460, 20, 490, 60),
                element("Koolhydraten", 40, 120, 300, 160),
                element("2,3", 400, 120, 470, 160),
                element("g", 480, 120, 510, 160),
                element("waarvan", 40, 200, 200, 240),
                element("suikers", 210, 200, 340, 240),
                element("2,3", 400, 200, 470, 240),
                element("g", 480, 200, 510, 240),
            ),
        )

        val report = NutritionTableParser.parseWithDiagnostics(document)

        val reading = report.reading
        assertTrue("expected a confident reading, got $reading", reading is LabelReading.Confident)
        assertEquals(NutritionBasis.PER_100_G, (reading as LabelReading.Confident).candidate.basis)

        val provenance = report.provenance
        assertTrue("expected row provenance, got $provenance", provenance is CandidateProvenance.FromRow)
        val rowText = (provenance as CandidateProvenance.FromRow).rowText.lowercase()
        assertTrue("value must come from the Koolhydraten row, not the sugars row: $rowText", "koolhydraten" in rowText)
        assertTrue("value must NOT come from the sugars row: $rowText", "suikers" !in rowText)
    }

    private fun element(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = 0, lineId = 0)
}
```

- [ ] **Step 2: Run it to verify it fails**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.CandidateProvenanceTest"
```

Expected: compilation failure — `CandidateProvenance` and `NutritionParseReport.provenance` do not exist.

- [ ] **Step 3: Create the provenance type**

```kotlin
package app.justthecarbs.ocr

/**
 * Where an accepted carbohydrate value came from, at a granularity fine enough to prove it was the
 * total rather than a child nutrient printing the same number.
 *
 * Row granularity is sufficient for a table — the total and its "of which sugars" occupy different
 * rows. It is NOT sufficient for a prose label, where both share one reconstructed row and, on four
 * of this repo's real fixtures, the same printed value. There, only the bound nutrient term
 * distinguishes them, so the prose variant carries the span.
 */
sealed interface CandidateProvenance {

    /** A value read from a reconstructed table row. */
    data class FromRow(val rowText: String, val rowBox: OcrBox) : CandidateProvenance

    /**
     * A value bound to a nutrient term inside a prose declaration.
     *
     * [nutrientTerm] is the term the value bound to — the assertion target. [valueElementIndices]
     * locates the value within [rowText]'s row for diagnostics.
     */
    data class FromProseSpan(
        val nutrientTerm: String,
        val valueElementIndices: Set<Int>,
        val rowText: String,
    ) : CandidateProvenance
}
```

- [ ] **Step 4: Add the field to the report**

In `NutritionTableParser.kt`, extend `NutritionParseReport`:

```kotlin
data class NutritionParseReport(
    val reading: LabelReading,
    val diagnostics: List<OcrDiagnostic>,
    /** A per-serving figure read alongside the canonical per-100 result (spec §5). Never gates live scanning. */
    val servingCandidate: ServingCarbCandidate? = null,
    /**
     * Where the accepted value came from. Null when there is no accepted value (`NotFound`) or when
     * the reading is `Ambiguous` — an ambiguity has no single source to attribute.
     */
    val provenance: CandidateProvenance? = null,
)
```

Defaulted, so no existing construction site breaks.

- [ ] **Step 5: Populate it for the confident tabular case**

In `NutritionTableInterpreter.interpret`, the `distinct.size == 1` branch (line ~170) already computes `rowFor(value, basis, contributingRows, totalRows)`. Capture that row and attach it. Replace the `reading` block's confident branch and the final return:

```kotlin
        var provenance: CandidateProvenance? = null

        val reading = when {
            distinct.isEmpty() -> {
                diagnostics += OcrDiagnostic("result", "Total-carbohydrate row found but no usable per-100 cell")
                LabelReading.NotFound
            }
            distinct.size == 1 -> {
                val (value, basis) = distinct.single()
                diagnostics += OcrDiagnostic("selected", "${value.toPlainString()} ${basis.name}")
                val row = rowFor(value, basis, contributingRows, totalRows)
                provenance = CandidateProvenance.FromRow(row.text, row.box)
                LabelReading.Confident(candidate(row, value, basis))
            }
            else -> {
                diagnostics += OcrDiagnostic("ambiguous", "${distinct.size} distinct total-carbohydrate readings")
                LabelReading.Ambiguous(
                    distinct.map { (value, basis) ->
                        candidate(rowFor(value, basis, contributingRows, totalRows), value, basis)
                    },
                )
            }
        }

        return NutritionParseReport(reading, diagnostics, servingCandidate, provenance)
```

Check `rowFor`'s return type at `NutritionTableInterpreter.kt:244` and adapt if it returns something other than `LogicalRow` — pass whatever exposes `.text` and `.box`.

- [ ] **Step 6: Render provenance in diagnostics**

In `OcrDiagnosticsReport.render`, append a line for the provenance when present:

```kotlin
        report.provenance?.let { provenance ->
            append("provenance: ")
            when (provenance) {
                is CandidateProvenance.FromRow -> appendLine("row '${provenance.rowText}'")
                is CandidateProvenance.FromProseSpan ->
                    appendLine("prose span '${provenance.nutrientTerm}' -> elements ${provenance.valueElementIndices}")
            }
        }
```

Match the surrounding builder style in that file.

- [ ] **Step 7: Run the test to verify it passes**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.CandidateProvenanceTest"
```

Expected: PASS.

- [ ] **Step 8: Run the whole JVM suite — nothing may regress**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: all previously passing tests still pass (baseline 537 + 1 new).

- [ ] **Step 9: Stage check (no commit)**

```bash
git add -A --dry-run && git diff --check
```

---

## Task 4: Prose eligibility predicate

**Files:**
- Create: `app/src/main/kotlin/app/justthecarbs/ocr/ProseNutritionReader.kt` (predicate only in this task)
- Create: `app/src/test/kotlin/app/justthecarbs/ocr/ProseEligibilityTest.kt`

**Interfaces:**
- Consumes: `LogicalRow` from `LogicalRowBuilder.build`; `NutritionColumn` / `NutritionColumnKind` from `ColumnClassifier`.
- Produces: `ProseNutritionReader.isProseLabel(rows: List<LogicalRow>, columns: List<NutritionColumn>): Boolean`. Task 6 gates on it and must pass the columns it already computed.

The predicate is a **positive structural statement** that the label is prose. "The table stage failed" is not sufficient — a table the parser failed to read is not a prose label, and running a second reader over it converts a safe refusal into a guess.

**Co-occurrence on one row is NOT the predicate, and must not be used as one.** A total term and a child term landing on the same reconstructed row is exactly what a *failed table* produces through row merging — it is the signature of the 2026-08-16 chaining bug, where the carbohydrate row swallowed the sugars row. Keying eligibility on that shape would hand the prose reader precisely the tables it must never touch. The baseline confirms how narrow it is on its own: **exactly 1 of 9 fixtures** exhibits it, and the two other prose fixtures (3 and 4) keep parent and child on separate rows.

The predicate therefore requires **both** of the following, and neither alone suffices:

1. **Positive sentence-like structure** — a `nutrient → value → child → value` sequence in reading order, evaluated **over the declaration's token stream** (amended by the owner 2026-08-17 — see below). A merged table row fails this: its two values sit adjacent (both in the same value column) or its child term precedes the second value with no intervening total value in sequence.

#### Amendment: the sequence spans the declaration, not one row (owner, 2026-08-17)

The first formulation evaluated the sequence *within a single reconstructed row's text*. Measured against the real corpus, ML Kit wraps a printed sentence wherever the line happens to end, so the child clause routinely lands on the **next** reconstructed row:

- fixture 4: `[TOTAL_CARBOHYDRATE] '…Kulhydrat: 3g. dont'` then `[CARBOHYDRATE_CHILD] 'sures /waarvan suikers … 2,5g …'`
- fixture 3: the total spans two rows, with `Sucre / heraf sukkerarter: 1,6 g` on a third

So `nutrient → value → child → value` never completes inside one row, and fixture 6 passed only because its wrap happened to leave the clause intact — luck, not structure. This mirrors the asymmetry already present in the file: `read` was document-wide over declarations while only the predicate was row-scoped.

**The predicate itself is unchanged.** The required sequence is still exactly `TOTAL_CARBOHYDRATE → total value → CARBOHYDRATE_CHILD → child value`. Only the *window* it is evaluated in changes, and that window is constrained so this cannot become document-wide accidental chaining:

- Tokens may cross reconstructed row boundaries **only within the same declaration span** already assembled by `ProseNutritionReader` — reuse that assembly, do not build a second one.
- **Preserve original reading order** (rows top to bottom, elements left to right).
- **Do not skip** across another nutrient declaration, an unrelated prose block, or a usable table structure to complete the sequence. A sequence completed by jumping over one of those is not a sentence.
- **Condition 2 is unchanged**: any *usable* basis column still rejects prose eligibility, so the merged-table negative stays blocked for exactly the reason it is blocked today.

Required negative test: the four tokens exist in correct document order but belong to **separate declarations**. That must NOT qualify — it is the precise failure mode this constraint exists to prevent.
2. **Absence of a USABLE basis column** (amended by the owner 2026-08-17 — see below). If the table stage found a real, structurally participating basis column and still failed, the failure is somewhere else in the table path, and the fix belongs there (Task 8), not in a prose reader.

Condition 2 requires the predicate to see column evidence, so `isProseLabel` takes the classified columns as well as the rows.

#### Amendment: "usable", not "resolved" (owner, 2026-08-17)

The first formulation said *no resolved `PER_100_G`/`PER_100_ML`/`PER_SERVING` column*. Measured against the real corpus, that predicate **can never be satisfied by any prose label**, because on a prose label the basis phrase is embedded in a running text line and `ColumnClassifier` resolves a column from it regardless:

| Fixture | Columns resolved | Header the column came from |
|---|---|---|
| 3 jar | 1 | `Naringsindhold (100g): Energiel eneri` |
| 4 lid | 2 | `PourPerlPro 100g: Energie /` |
| 6 stokbrood | 3 | two of them from the **ingredients** prose (`waarvan toegevoegde suikers 0 g per 100 g`) |

Those columns are real *text* but meaningless as *columns*: their x-positions correspond to no value column. So condition 2 tests usability, defined narrowly so the merged-table protection is untouched:

- A basis column is **usable** only when its x-position **participates in the nutrition table structure** — that is, it has aligned numeric nutrient-value cells associated with it.
- A basis phrase occurring inside running or header prose **does not** become usable merely because `ColumnClassifier` assigned it an x-position.
- Ingredient prose (`toegevoegde suikers 0 g per 100 g`) must **never** establish a usable nutrition basis column.
- **Do not infer usability from the existence of a `NutritionColumn` object alone.** The object's existence is precisely what the old predicate wrongly trusted.

**The merged-table fixture must still fail prose eligibility**, and does so for a reason this amendment leaves fully intact: its resolved basis column has actual aligned nutrient values, so it is usable, so condition 2 refuses. The amendment separates *resolved-but-unusable* from *resolved-and-usable*; it does not weaken the guard against a failed table being handed to the prose reader.

Tests must distinguish the two cases explicitly — a resolved-but-unusable column (prose) and a resolved-and-usable one (table, including the merged-row table).

**Consult the baseline before finalizing the sequence check.** `docs/plans/2026-08-16-real-image-baseline.md` records the actual ML Kit segmentation of fixtures 3, 4 and 6, including whether fixture 6's `'ren 6,4 g, koolhydraten 46g,waarvan suikers 1,0 g.'` row tokenizes such that the four-part sequence is recoverable. If it is not recoverable as specified, report that as a finding and propose the smallest amendment — do not loosen the predicate to "contains both terms", which is the shape this section exists to forbid.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.justthecarbs.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProseEligibilityTest {

    /**
     * Prose: one row reads as a sentence — nutrient, its value, child, its value, in that order,
     * with no resolved basis column anywhere in the document.
     */
    @Test
    fun aSentenceCarryingNutrientValueChildValueIsProse() {
        val document = OcrDocument(
            width = 1400,
            height = 200,
            elements = listOf(
                element("Voedingswaarde", 20, 40, 240, 80),
                element("per", 250, 40, 300, 80),
                element("100", 310, 40, 370, 80),
                element("g:", 380, 40, 420, 80),
                element("koolhydraten", 430, 40, 650, 80),
                element("46", 660, 40, 710, 80),
                element("g,", 720, 40, 760, 80),
                element("waarvan", 770, 40, 900, 80),
                element("suikers", 910, 40, 1020, 80),
                element("1,0", 1030, 40, 1090, 80),
                element("g", 1100, 40, 1130, 80),
            ),
        )
        val rows = LogicalRowBuilder.build(document)

        assertTrue(
            "a one-row sentence in nutrient-value-child-value order is prose",
            ProseNutritionReader.isProseLabel(rows, ColumnClassifier.classify(rows, document)),
        )
    }

    /**
     * A table is NOT prose, even a table the parser could not read. The predicate must not be
     * satisfied merely by the table stage having failed.
     */
    @Test
    fun aTableWithNutrientsOnSeparateRowsIsNotProse() {
        val document = OcrDocument(
            width = 1000,
            height = 400,
            elements = listOf(
                element("per", 380, 20, 440, 60),
                element("100", 450, 20, 510, 60),
                element("g", 520, 20, 550, 60),
                element("Koolhydraten", 40, 120, 300, 160),
                element("2,3", 400, 120, 470, 160),
                element("g", 480, 120, 510, 160),
                element("waarvan", 40, 220, 200, 260),
                element("suikers", 210, 220, 340, 260),
                element("2,3", 400, 220, 470, 260),
                element("g", 480, 220, 510, 260),
            ),
        )
        val rows = LogicalRowBuilder.build(document)

        assertFalse(
            "nutrients on their own rows are a table",
            ProseNutritionReader.isProseLabel(rows, ColumnClassifier.classify(rows, document)),
        )
    }

    /**
     * THE CASE THAT MATTERS. A genuine two-column table whose rows chained during reconstruction, so
     * the carbohydrate row and the sugars row merged into one — exactly the 2026-08-16 tilt bug. The
     * merged row now contains a total term AND a child term, which is why bare co-occurrence is not
     * an admissible predicate. Two things must save it: the values sit in column order (value, value)
     * rather than sentence order (nutrient, value, child, value), and the document has a resolved
     * per-100 column. Either alone is sufficient to refuse; both hold here.
     */
    @Test
    fun aMergedTableRowIsNotProse() {
        val document = OcrDocument(
            width = 1000,
            height = 300,
            elements = listOf(
                element("per", 380, 20, 440, 60),
                element("100", 450, 20, 510, 60),
                element("g", 520, 20, 550, 60),
                // One reconstructed row: the table's two printed rows chained together.
                element("Koolhydraten", 40, 130, 300, 170),
                element("2,3", 400, 128, 470, 168),
                element("waarvan", 40, 150, 200, 190),
                element("suikers", 210, 152, 340, 192),
                element("2,3", 400, 154, 470, 194),
            ),
        )
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document)

        // Assert the REASON, not only the verdict. This table's basis column has real values aligned
        // under it, so it is USABLE, so condition 2 refuses — which is precisely the property the
        // 2026-08-17 usability amendment had to preserve. If a future change makes this column
        // "unusable", the merged-table guard has silently eroded and this assertion catches it.
        assertTrue(
            "precondition: the merged table's basis column must be usable structure",
            columns.any { it.kind == NutritionColumnKind.PER_100_G },
        )
        assertFalse(
            "a merged table row must never be mistaken for a sentence",
            ProseNutritionReader.isProseLabel(rows, columns),
        )
    }

    /**
     * RESOLVED-BUT-UNUSABLE. The basis phrase is inside a running sentence, so `ColumnClassifier`
     * resolves a PER_100_G column for it — but no nutrient values are aligned to its x-position. That
     * column is text, not structure, and must not defeat prose eligibility. Every real prose fixture
     * in the corpus has this shape; the un-amended predicate could never fire because of it.
     */
    @Test
    fun aBasisPhraseInRunningTextDoesNotMakeAUsableColumn() {
        val document = OcrDocument(
            width = 1400,
            height = 400,
            elements = listOf(
                // A sentence. The basis phrase sits mid-line, far from where any value prints.
                element("Voedingswaarde", 20, 40, 240, 80),
                element("per", 250, 40, 300, 80),
                element("100", 310, 40, 370, 80),
                element("g:", 380, 40, 420, 80),
                element("koolhydraten", 430, 40, 650, 80),
                element("46", 660, 40, 710, 80),
                element("g,", 720, 40, 760, 80),
                element("waarvan", 770, 40, 900, 80),
                element("suikers", 910, 40, 1020, 80),
                element("1,0", 1030, 40, 1090, 80),
                element("g", 1100, 40, 1130, 80),
            ),
        )
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document)

        // The point of the test is that a column IS resolved and eligibility still holds.
        assertTrue(
            "precondition: this document resolves a basis column",
            columns.any { it.kind == NutritionColumnKind.PER_100_G },
        )
        assertTrue(
            "a basis phrase in running text is not usable column structure",
            ProseNutritionReader.isProseLabel(rows, columns),
        )
    }

    /**
     * RESOLVED-AND-USABLE, on ingredient prose specifically. "waarvan toegevoegde suikers 0 g per
     * 100 g" is a legal claim about an ingredient, not a nutrition table header, and must never
     * establish a usable basis column.
     */
    @Test
    fun ingredientProseDoesNotEstablishAUsableBasisColumn() {
        val document = OcrDocument(
            width = 1400,
            height = 400,
            elements = listOf(
                element("Ingredienten:", 20, 40, 240, 80),
                element("bloem,", 250, 40, 360, 80),
                element("waarvan", 370, 40, 500, 80),
                element("toegevoegde", 510, 40, 700, 80),
                element("suikers", 710, 40, 820, 80),
                element("0", 830, 40, 860, 80),
                element("g", 870, 40, 900, 80),
                element("per", 910, 40, 960, 80),
                element("100", 970, 40, 1030, 80),
                element("g.", 1040, 40, 1080, 80),
                element("Voedingswaarde", 20, 140, 240, 180),
                element("per", 250, 140, 300, 180),
                element("100", 310, 140, 370, 180),
                element("g:", 380, 140, 420, 180),
                element("koolhydraten", 430, 140, 650, 180),
                element("46", 660, 140, 710, 180),
                element("g,", 720, 140, 760, 180),
                element("waarvan", 770, 140, 900, 180),
                element("suikers", 910, 140, 1020, 180),
                element("1,0", 1030, 140, 1090, 180),
                element("g", 1100, 140, 1130, 180),
            ),
        )
        val rows = LogicalRowBuilder.build(document)

        assertTrue(
            "ingredient prose must not block prose eligibility",
            ProseNutritionReader.isProseLabel(rows, ColumnClassifier.classify(rows, document)),
        )
    }

    @Test
    fun anEmptyDocumentIsNotProse() {
        assertFalse(ProseNutritionReader.isProseLabel(emptyList(), emptyList()))
    }

    private fun element(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrElement(text, OcrBox(left, top, right, bottom), blockId = 0, lineId = 0)
}
```

Confirm `ColumnClassifier.classify`'s actual signature and return type by reading `NutritionColumnKind.kt` before writing these calls; adapt the call sites if it differs, but keep the predicate taking resolved columns as a parameter rather than re-running classification itself.

- [ ] **Step 2: Run it to verify it fails**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.ProseEligibilityTest"
```

Expected: compilation failure — `ProseNutritionReader` does not exist.

- [ ] **Step 3: Implement the predicate**

```kotlin
package app.justthecarbs.ocr

/**
 * Reads a total carbohydrate figure from a label with no tabular structure — a run-on multilingual
 * sentence such as `Voedingswaarde per 100 g: ... koolhydraten 46 g, waarvan suikers 1,0 g, ...`.
 *
 * This is a recognizer of one printed form, NOT a scoring model. It has no notion of a best
 * candidate: it either finds a span matching the required shape or yields nothing. There is nothing
 * here to tune, which is what keeps it from becoming a second scoring path of the kind the
 * geometry-first rewrite removed.
 *
 * It runs only on tabular `NotFound` and only when [isProseLabel] holds — see
 * [NutritionTableInterpreter]. Both conditions independently exclude every tabular fixture.
 */
object ProseNutritionReader {

    /**
     * Whether this label is prose, stated positively — both conditions required.
     *
     * 1. Some row reads as a sentence: a carbohydrate term, then a numeric value, then a child term,
     *    then a numeric value, in that order.
     * 2. No basis column was resolved anywhere in the document.
     *
     * Bare co-occurrence of a total term and a child term on one row is deliberately NOT the
     * predicate. That is the signature of a *merged table row* — the failure mode the geometry-first
     * rewrite was built to fix — and using it here would hand the prose reader the tables it must
     * never touch. Condition 1's ordering requirement separates a sentence from a merged row (whose
     * values sit adjacent in one value column rather than interleaved with their nutrient terms), and
     * condition 2 independently refuses any document where the table stage found real column
     * structure and failed for some other reason.
     */
    fun isProseLabel(rows: List<LogicalRow>, columns: List<NutritionColumn>): Boolean {
        if (columns.any { it.kind in BASIS_KINDS && isUsable(it, rows) }) return false
        return rows.any { hasNutrientValueChildValueSequence(it) }
    }

    /**
     * Whether [column] actually participates in the table's structure, rather than merely having
     * been resolved from a basis phrase that happened to appear in running text.
     *
     * A column is usable when **aligned numeric nutrient-value cells** sit at its x-position. The
     * existence of the [NutritionColumn] object proves nothing on its own: on a prose label the basis
     * phrase is embedded in a sentence, so a column is resolved for it whose x-position corresponds
     * to no value column at all — and on a dense label, ingredient prose ("waarvan toegevoegde
     * suikers 0 g per 100 g") resolves one too.
     *
     * This keeps the merged-table guard exactly as strong as before: a genuine table whose rows
     * chained still has real values aligned under its basis column, so the column is usable, so the
     * label is refused. Only the resolved-but-structurally-absent case changes.
     */
    private fun isUsable(column: NutritionColumn, rows: List<LogicalRow>): Boolean {
        TODO("count nutrient rows carrying a numeric cell aligned to column's x-position; " +
            "require at least MIN_ALIGNED_VALUES of them")
    }

    private val BASIS_KINDS = setOf(
        NutritionColumnKind.PER_100_G,
        NutritionColumnKind.PER_100_ML,
        NutritionColumnKind.PER_SERVING,
    )

    /**
     * True when the row's tokens, in reading order, match
     * `<carbohydrate term> … <number> … <child term> … <number>`.
     *
     * The ordering is the whole test. A merged table row produces
     * `<carbohydrate term> <number> <child term> <number>` only by coincidence of column layout; far
     * more often it produces the two values adjacent, or the child term before the total's value.
     * Requiring the interleaving is what makes this a statement about sentences rather than about
     * proximity.
     */
    private fun hasNutrientValueChildValueSequence(row: LogicalRow): Boolean {
        // Implement over the row's elements in left-to-right order, normalizing each token through
        // NutritionTerminology.normalize. Walk a four-state machine: seek carbohydrate term, seek
        // number, seek child term, seek number. Reaching the final state is a match.
        TODO("walk the row's tokens in reading order; see the state machine described above")
    }
}
```

Replace the `TODO` with the state machine before running the tests — it is described rather than transcribed because the exact token accessor on `LogicalRow` (`elements` vs a text list) must be read off `LogicalRow.kt` first. Do not substitute a regex over `row.text`: `normalize()` strips punctuation, and the comma in `46 g, waarvan` is part of what distinguishes a sentence from a column.

- [ ] **Step 4: Run the test to verify it passes**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.ProseEligibilityTest"
```

Expected: PASS, all four cases — `aMergedTableRowIsNotProse` above all, since it is the one that fails if the predicate degrades back to bare co-occurrence.

- [ ] **Step 5: Stage check (no commit)**

```bash
git add -A --dry-run && git diff --check
```

---

## Task 5: Prose declaration basis + span-scoped binding

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ocr/ProseNutritionReader.kt`
- Create: `app/src/test/kotlin/app/justthecarbs/ocr/ProseNutritionReaderTest.kt`

**Interfaces:**
- Consumes: `ProseNutritionReader.isProseLabel` from Task 4; `CandidateProvenance.FromProseSpan` from Task 3.
- Produces: `ProseNutritionReader.read(rows: List<LogicalRow>): ProseResult`, where `ProseResult` is `data class ProseResult(val reading: LabelReading, val provenance: CandidateProvenance.FromProseSpan?)`. Task 6 calls it.

The two rules this task implements, verbatim from the spec:

1. **Basis (structural):** one unique explicit per-100 basis governing the same prose declaration applies to that declaration's nutrient spans. Competing or absent bases → `NotFound`.
2. **Binding (span-scoped exclusion):** a value binds to its nearest preceding nutrient term; a child term claims every value up to the next nutrient term.

### A declaration is not a row, and aggregation is document-wide

**Measured, not assumed:** the Task 2 baseline found the basis phrase on the same logical row as the carbohydrate term and value in **0 of 3** prose fixtures. Every basis phrase reconstructs as its own standalone `HEADER` row; photo 4 puts `Pour/Per/Pro 100g:` three printed lines above its carbohydrate value. A row-scoped reader would therefore never fire on any real prose label in this corpus.

So the unit of interpretation is the **declaration**, which may span several `LogicalRow`s:

- Flatten the document into one token stream in reading order — rows top to bottom, elements left to right within each row.
- A declaration **opens** at a basis phrase and **extends until the next basis phrase or the end of the document**. Tokens before the first basis phrase belong to no declaration and are not read.
- The basis that opens a declaration governs every nutrient span inside it. That is the structural relationship the spec asks for; it is not a distance rule and has no tunable radius.
- A declaration containing more than one basis phrase cannot exist by construction (a second phrase opens a new declaration). Two *different* bases opening two declarations that each yield a total is handled by aggregation below, not by refusing outright.

### Aggregation is document-wide, never first-match

`read` collects **every** eligible total-carbohydrate interpretation across **all** declarations, then decides once:

| Distinct eligible results | Outcome |
|---|---|
| 0 | `LabelReading.NotFound` |
| 1 | `LabelReading.Confident` |
| ≥2 differing | `LabelReading.Ambiguous` |

Distinctness is on `(value.stripTrailingZeros(), basis)`, matching the tabular path's `distinctBy` so `46` and `46.0` are one interpretation rather than a fabricated disagreement.

**Never return the first accepted result.** A first-match return is a positional preference masquerading as a rule: it silently resolves a genuine conflict in favour of whichever declaration happens to sit higher on the package, and it hides the second reading from the user entirely. On a multilingual label where the same figure is printed in several languages, aggregation collapses them to one `Confident` reading; where they genuinely disagree, the user is asked. Both are correct; first-match is correct only by luck.

`provenance` is non-null only in the `Confident` case — an ambiguity has no single span that produced it.

- [ ] **Step 1: Write the failing tests**

```kotlin
package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ProseNutritionReaderTest {

    /** The real AH stokbrood relationship: total, then sugars, then fibre, all on one line. */
    @Test
    fun bindsTheTotalAndLetsChildTermsClaimTheirOwnValues() {
        val result = ProseNutritionReader.read(rowsOf(STOKBROOD))

        val reading = result.reading
        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        val candidate = (reading as LabelReading.Confident).candidate
        assertEquals(BigDecimal("46"), candidate.value.stripTrailingZeros())
        assertEquals(NutritionBasis.PER_100_G, candidate.basis)

        val provenance = result.provenance
        assertEquals("koolhydraten", provenance?.nutrientTerm)
    }

    /** 1,0 is sugars and 4,7 is fibre. Neither may ever surface as the total. */
    @Test
    fun neverOffersAChildNutrientsValue() {
        val reading = ProseNutritionReader.read(rowsOf(STOKBROOD)).reading
        val offered = when (reading) {
            is LabelReading.Confident -> listOf(reading.candidate.value)
            is LabelReading.Ambiguous -> reading.candidates.map { it.value }
            LabelReading.NotFound -> emptyList()
        }
        listOf("1.0", "4.7", "12").forEach { forbidden ->
            assertTrue(
                "$forbidden is a child nutrient or unrelated value, never the total",
                offered.none { it.compareTo(BigDecimal(forbidden)) == 0 },
            )
        }
    }

    /** No basis printed anywhere: the value is readable but unplaceable. Refuse. */
    @Test
    fun refusesWhenNoBasisGovernsTheDeclaration() {
        val reading = ProseNutritionReader.read(rowsOf("koolhydraten 46 g, waarvan suikers 1,0 g")).reading
        assertEquals(LabelReading.NotFound, reading)
    }

    /**
     * Two bases in immediate succession. The second opens a new declaration, so the carbohydrate span
     * is governed by `per 100 ml` alone — but nothing is governed by `per 100 g`, and no reading may
     * be attributed to it. The point of the assertion is that the reader does not silently attach the
     * value to the FIRST basis it saw.
     */
    @Test
    fun aSecondBasisPhraseOpensANewDeclaration() {
        val text = "per 100 g per 100 ml koolhydraten 9,0 g, waarvan suikers 9,0 g"
        val reading = ProseNutritionReader.read(rowsOf(text)).reading
        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        assertEquals(NutritionBasis.PER_100_ML, (reading as LabelReading.Confident).candidate.basis)
    }

    /** A value with no gram unit is not a nutrient quantity. */
    @Test
    fun requiresAGramUnitOnTheValue() {
        val text = "Voedingswaarde per 100 g: koolhydraten 46, waarvan suikers 1,0 g"
        assertEquals(LabelReading.NotFound, ProseNutritionReader.read(rowsOf(text)).reading)
    }

    /** Per 100 ml prose, so the basis is carried through rather than assumed to be grams. */
    @Test
    fun carriesAMillilitreBasisThrough() {
        val text = "gemiddeld per 100 ml: koolhydraten 9,0 g, waarvan suikers 9,0 g"
        val reading = ProseNutritionReader.read(rowsOf(text)).reading
        assertTrue(reading is LabelReading.Confident)
        assertEquals(NutritionBasis.PER_100_ML, (reading as LabelReading.Confident).candidate.basis)
        assertEquals(BigDecimal("9"), reading.candidate.value.stripTrailingZeros())
    }

    /**
     * Two independent declarations disagreeing is ambiguity, not a pick — and specifically not the
     * first one. This is the test that fails if `read` ever regresses to returning on first success.
     */
    @Test
    fun conflictingTotalsAcrossDeclarationsAreAmbiguous() {
        val text = "per 100 g: koolhydraten 46 g. per 100 g: koolhydraten 12 g"
        val reading = ProseNutritionReader.read(rowsOf(text)).reading
        assertTrue("expected Ambiguous, got $reading", reading is LabelReading.Ambiguous)
        val values = (reading as LabelReading.Ambiguous).candidates.map { it.value.stripTrailingZeros() }
        assertTrue("both readings must be offered, got $values", values.size == 2)
    }

    /**
     * THE CASE THE 0-OF-3 BASELINE MEASUREMENT DEMANDS. The basis phrase is on its own reconstructed
     * row, three rows above the carbohydrate value — the shape of every real prose fixture in the
     * corpus. A row-scoped reader returns NotFound here and would never fire on a real label.
     */
    @Test
    fun aDeclarationGovernsNutrientSpansOnLaterRows() {
        val rows = multiRow(
            "Pour Per Pro 100g:",
            "energie 1312 kJ",
            "vetten 19 g waarvan verzadigde 13 g",
            "koolhydraten 3 g waarvan suikers 2,5 g",
        )

        val reading = ProseNutritionReader.read(rows).reading
        assertTrue("expected Confident across rows, got $reading", reading is LabelReading.Confident)
        val candidate = (reading as LabelReading.Confident).candidate
        assertEquals(BigDecimal("3"), candidate.value.stripTrailingZeros())
        assertEquals(NutritionBasis.PER_100_G, candidate.basis)
    }

    /**
     * A multilingual label printing the same figure twice is ONE interpretation, not an ambiguity.
     * Distinctness is on (value, basis) exactly as the tabular path deduplicates.
     */
    @Test
    fun theSameFigureRepeatedInTwoDeclarationsIsOneReading() {
        val text = "per 100 g: koolhydraten 46 g. per 100 g: carbohydrate 46 g"
        val reading = ProseNutritionReader.read(rowsOf(text)).reading
        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        assertEquals(BigDecimal("46"), (reading as LabelReading.Confident).candidate.value.stripTrailingZeros())
    }

    private companion object {
        const val STOKBROOD =
            "Voedingswaarde per 100 g: energie 1312 kJ, vetten 7,8 g, " +
                "koolhydraten 46 g, waarvan suikers 1,0 g, vezels 4,7 g, eiwitten 12 g"
    }

    /** One row, tokens laid out left to right at a uniform pitch. */
    private fun rowsOf(text: String): List<LogicalRow> = multiRow(text)

    /**
     * One reconstructed row per line, at a 100 px pitch that is comfortably clear of the 40 px text
     * height so no two lines can chain. This is the layout every real prose fixture actually has.
     */
    private fun multiRow(vararg lines: String): List<LogicalRow> {
        var maxX = 0
        val elements = lines.flatMapIndexed { line, text ->
            val top = 40 + line * 100
            var x = 20
            text.split(' ').filter { it.isNotBlank() }.map { word ->
                val width = word.length * 18
                val box = OcrBox(x, top, x + width, top + 40)
                x += width + 12
                maxX = maxOf(maxX, x)
                OcrElement(word, box, blockId = line, lineId = line)
            }
        }
        return LogicalRowBuilder.build(OcrDocument(maxX + 40, 40 + lines.size * 100 + 60, elements))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.ProseNutritionReaderTest"
```

Expected: compilation failure — `read` and `ProseResult` do not exist.

- [ ] **Step 3: Implement the reader**

Append to `ProseNutritionReader.kt`. Key design points: the unit of interpretation is a **declaration** spanning any number of rows; exclusion is decided by which term a value **binds to**, never by distance; and results are aggregated across the whole document before a single decision is taken.

```kotlin
    /** The prose stage's answer plus, when it accepted one, the span that produced it. */
    data class ProseResult(
        val reading: LabelReading,
        val provenance: CandidateProvenance.FromProseSpan?,
    )

    /** One token in the document's reading order, with the element it came from. */
    private data class Token(val index: Int, val normalized: String, val element: OcrElement, val rowText: String)

    /** A nutrient term found in the token stream, and whether it is a child. */
    private data class TermHit(val index: Int, val term: String, val isChild: Boolean)

    /** A value token: a number carrying a gram/millilitre unit, either joined or as the next token. */
    private data class ValueHit(val index: Int, val value: java.math.BigDecimal)

    /** A basis phrase and everything governed by it, up to the next basis phrase. */
    private data class Declaration(val basis: NutritionBasis, val tokens: List<Token>)

    /** One accepted total-carbohydrate interpretation, before aggregation decides what to do with it. */
    private data class ProseInterpretation(
        val value: java.math.BigDecimal,
        val basis: NutritionBasis,
        val term: TermHit,
        val valueToken: Token,
    )

    fun read(rows: List<LogicalRow>): ProseResult {
        // Rows top to bottom, elements left to right — the order a person reads the package in. A
        // declaration is a run of this stream, so it is free to cross row boundaries. It must be:
        // the baseline measured the basis phrase on a DIFFERENT row from the carbohydrate value in
        // every real prose fixture, so a row-scoped reader would never fire.
        val tokens = flatten(rows)

        val interpretations = declarationsIn(tokens).flatMap(::interpret)

        // Document-wide, never first-match. A first accepted result would silently prefer whichever
        // declaration sits highest on the package and hide any disagreement from the user.
        val distinct = interpretations.distinctBy { it.value.stripTrailingZeros() to it.basis }

        return when (distinct.size) {
            0 -> ProseResult(LabelReading.NotFound, null)
            1 -> {
                val only = distinct.single()
                ProseResult(
                    LabelReading.Confident(proseCandidate(only)),
                    CandidateProvenance.FromProseSpan(
                        nutrientTerm = only.term.term,
                        valueElementIndices = setOf(only.valueToken.index),
                        rowText = only.valueToken.rowText,
                    ),
                )
            }
            // Rule 8: competing eligible totals are an ambiguity, never a pick. No single span
            // produced this, so there is no provenance to report.
            else -> ProseResult(LabelReading.Ambiguous(distinct.map(::proseCandidate)), null)
        }
    }

    /** Every total-carbohydrate interpretation this declaration supports. Usually zero or one. */
    private fun interpret(declaration: Declaration): List<ProseInterpretation> {
        val terms = termsIn(declaration.tokens)
        if (terms.none { !it.isChild }) return emptyList()

        val values = valuesIn(declaration.tokens)

        // Rules 3 + 6: each value binds to its nearest PRECEDING nutrient term, and a child term
        // claims every value up to the next term. Exclusion is a property of the binding, not a
        // distance — which is what stops a sugars figure being reachable from the total's term.
        return values.mapNotNull { value ->
            val owner = terms.lastOrNull { it.index < value.index } ?: return@mapNotNull null
            if (owner.isChild) return@mapNotNull null
            val token = declaration.tokens.first { it.index == value.index }
            ProseInterpretation(value.value, declaration.basis, owner, token)
        }
    }
```

Implement the helpers in the same object:

- `flatten(rows: List<LogicalRow>): List<Token>` — rows in top-to-bottom order, elements left-to-right within each row, indices assigned sequentially across the whole document. Each token carries its own row's text so provenance can name the line the value was read from.
- `declarationsIn(tokens: List<Token>): List<Declaration>` — find every basis phrase; each opens a declaration that runs until the next basis phrase or the end of the stream. Tokens preceding the first basis phrase belong to no declaration and are dropped (rule 5: a value with no governing basis is unplaceable). A document with no basis phrase yields no declarations, hence `NotFound`.
- `basisPhraseAt(tokens, i): Pair<NutritionBasis, Int>?` — a connective from `NutritionTerminology.connectives` followed by `100` followed by `g`/`ml`, returning the basis and the phrase's length in tokens. Reuse the shape `InlineBasisSpans.spanAt` already recognizes rather than inventing a second vocabulary.
- `termsIn(tokens: List<Token>): List<TermHit>` — walks the token list marking each index that starts a `NutritionTerminology.carbohydrateTerms` or `exclusionTerms` phrase. Multi-word terms ("waarvan suikers") must match across adjacent tokens. **Check exclusions first**, so a phrase that is both (a child term containing a carbohydrate word) is a child — the same precedence `RowClassifier` applies.
- `valuesIn(tokens: List<Token>): List<ValueHit>` — a number token whose own text carries a gram unit (`46g`) or whose next token is the unit (`46` `g`). Reuse the decimal-comma handling the codebase already uses (`replace(',', '.')`). Reject a token consumed by a basis phrase (the literal `100` in `per 100 g`) and reject values with no unit (rule 4).
- `proseCandidate(interpretation): CarbCandidate` — build with `sourceLine = interpretation.valueToken.rowText`, `label = interpretation.term.term`, `score = 0` (there is no scoring on this path; the field exists for the shared type), `geometry = interpretation.valueToken.element.box`, `evidence = listOf(CandidateEvidence("prose declaration basis ${interpretation.basis.name}", 0))`.

Note what is deliberately absent: no distance threshold, no ranking, no "closest number", and no early return on the first success. The only tunable quantity in this file is the terminology lists, which are shared with the tabular path.

- [ ] **Step 4: Run the tests to verify they pass**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.ProseNutritionReaderTest"
```

Expected: PASS, all nine cases. Two of them are load-bearing and must not be weakened to make the others pass: `aDeclarationGovernsNutrientSpansOnLaterRows` (a row-scoped reader cannot satisfy it, and the baseline says every real prose label has this shape) and `conflictingTotalsAcrossDeclarationsAreAmbiguous` (a first-match return cannot satisfy it).

- [ ] **Step 5: Run the whole JVM suite**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: no regressions. `ProseNutritionReader` is not yet wired into the pipeline, so nothing else can change.

- [ ] **Step 6: Stage check (no commit)**

```bash
git add -A --dry-run && git diff --check
```

---

## Task 6: Wire the prose reader behind the activation gate

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ocr/NutritionTableInterpreter.kt:44-48` and `:165-186`
- Create: `app/src/test/kotlin/app/justthecarbs/ocr/ProseActivationGateTest.kt`

**Interfaces:**
- Consumes: `ProseNutritionReader.isProseLabel`, `ProseNutritionReader.read` (Tasks 4–5); `CandidateProvenance` (Task 3).
- Produces: no new API. `NutritionTableParser.parseWithDiagnostics` gains prose behaviour behind the gate.

- [ ] **Step 1: Write the failing gate test**

```kotlin
package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ProseActivationGateTest {

    /** A prose label the table path cannot read now returns a confident value. */
    @Test
    fun proseIsReadWhenTheTablePathFindsNothing() {
        val report = NutritionTableParser.parseWithDiagnostics(prose())
        val reading = report.reading
        assertTrue("expected Confident, got $reading", reading is LabelReading.Confident)
        assertEquals(BigDecimal("46"), (reading as LabelReading.Confident).candidate.value.stripTrailingZeros())
        assertTrue(report.provenance is CandidateProvenance.FromProseSpan)
    }

    /**
     * The gate's other direction: a TABLE that the parser reads confidently must never reach the
     * prose stage. Its provenance must be a row, which is only possible via the tabular path.
     */
    @Test
    fun aReadableTableNeverReachesTheProseStage() {
        val report = NutritionTableParser.parseWithDiagnostics(table())
        assertTrue(report.reading is LabelReading.Confident)
        assertTrue(
            "a table's provenance must be a row, not a prose span",
            report.provenance is CandidateProvenance.FromRow,
        )
    }

    /**
     * End-to-end guard for the same case `ProseEligibilityTest.aMergedTableRowIsNotProse` pins in
     * isolation: a table whose rows chained produces a `NotFound` the prose reader must NOT rescue.
     * A confident answer here would mean the fallback had converted a safe refusal into a guess about
     * a label it has no business reading — the exact failure the activation gate exists to prevent.
     */
    @Test
    fun aFailedTableIsNotRescuedByTheProseStage() {
        val report = NutritionTableParser.parseWithDiagnostics(mergedTable())
        assertEquals(LabelReading.NotFound, report.reading)
    }

    private fun prose(): OcrDocument {
        val words = ("Voedingswaarde per 100 g: koolhydraten 46 g, waarvan suikers 1,0 g")
            .split(' ')
        var x = 20
        val elements = words.map { word ->
            val width = word.length * 18
            val box = OcrBox(x, 40, x + width, 80)
            x += width + 12
            OcrElement(word, box, blockId = 0, lineId = 0)
        }
        return OcrDocument(x + 40, 200, elements)
    }

    private fun table(): OcrDocument = OcrDocument(
        width = 1000,
        height = 400,
        elements = listOf(
            OcrElement("per", OcrBox(320, 20, 380, 60), 0, 0),
            OcrElement("100", OcrBox(390, 20, 450, 60), 0, 0),
            OcrElement("g", OcrBox(460, 20, 490, 60), 0, 0),
            OcrElement("Koolhydraten", OcrBox(40, 120, 300, 160), 0, 1),
            OcrElement("2,3", OcrBox(400, 120, 470, 160), 0, 1),
            OcrElement("g", OcrBox(480, 120, 510, 160), 0, 1),
            OcrElement("waarvan", OcrBox(40, 220, 200, 260), 0, 2),
            OcrElement("suikers", OcrBox(210, 220, 340, 260), 0, 2),
            OcrElement("2,3", OcrBox(400, 220, 470, 260), 0, 2),
            OcrElement("g", OcrBox(480, 220, 510, 260), 0, 2),
        ),
    )

    /** The same table with its two nutrient rows overlapping enough to reconstruct as one row. */
    private fun mergedTable(): OcrDocument = OcrDocument(
        width = 1000,
        height = 300,
        elements = listOf(
            OcrElement("Koolhydraten", OcrBox(40, 130, 300, 170), 0, 1),
            OcrElement("2,3", OcrBox(400, 128, 470, 168), 0, 1),
            OcrElement("waarvan", OcrBox(40, 150, 200, 190), 0, 2),
            OcrElement("suikers", OcrBox(210, 152, 340, 192), 0, 2),
            OcrElement("2,3", OcrBox(400, 154, 470, 194), 0, 2),
        ),
    )
}
```

If `mergedTable()` as written does not actually reconstruct into a single row, adjust the boxes until it does and say so in the report — the test is worthless unless the merge really happens. Verify by asserting `LogicalRowBuilder.build(mergedTable()).size` in a scratch run before relying on it.

- [ ] **Step 2: Run to verify it fails**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.ProseActivationGateTest"
```

Expected: `proseIsReadWhenTheTablePathFindsNothing` FAILS (reading is `NotFound`). The other two cases may already pass — that is fine, they are guards against the change you are about to make.

- [ ] **Step 3: Add the gate at both NotFound exits**

`NutritionTableInterpreter.interpret` returns `NotFound` in two places: no total-carbohydrate row (line ~48) and no usable per-100 cell (line ~167). Both are tabular `NotFound` and both must offer the prose fallback. Add a helper inside the object:

```kotlin
    /**
     * The prose fallback, behind its two independent gates.
     *
     * Reached only from a tabular `NotFound` — never from `Confident` and never from `Ambiguous`. An
     * ambiguity means the table stage found competing legitimate interpretations, and resolving that
     * competition with a different stage's answer would be exactly the confident-wrong-answer the
     * architecture refuses; it is surfaced unchanged.
     */
    private fun proseFallback(
        rows: List<LogicalRow>,
        columns: List<NutritionColumn>,
        diagnostics: MutableList<OcrDiagnostic>,
    ): NutritionParseReport? {
        // The predicate needs the columns the table stage already resolved: a document with a real
        // basis column is not a prose label however badly the rest of the table read.
        if (!ProseNutritionReader.isProseLabel(rows, columns)) {
            diagnostics += OcrDiagnostic("prose", "not a prose label; no fallback")
            return null
        }
        val result = ProseNutritionReader.read(rows)
        if (result.reading == LabelReading.NotFound) {
            diagnostics += OcrDiagnostic("prose", "prose label, but no bindable total with a governing basis")
            return null
        }
        diagnostics += OcrDiagnostic("prose", "read from a prose declaration")
        // Per-100 only: a prose label never yields a serving candidate (spec, rule 7).
        return NutritionParseReport(result.reading, diagnostics, null, result.provenance)
    }
```

Then at the first exit. Note the ordering constraint: `totalRows.isEmpty()` currently returns **before** columns are classified, so the classification must be hoisted above it (or the call sites reordered) for the predicate to have columns to inspect. Read the current control flow and make the smaller change; do not duplicate the classification call.

```kotlin
        if (totalRows.isEmpty()) {
            diagnostics += OcrDiagnostic("result", "No total-carbohydrate row")
            proseFallback(rows, columns, diagnostics)?.let { return it }
            return NutritionParseReport(LabelReading.NotFound, diagnostics, null)
        }
```

And in the `reading` `when`, the `distinct.isEmpty()` branch cannot early-return because `reading` is assigned. Restructure that branch to return directly instead:

```kotlin
            distinct.isEmpty() -> {
                diagnostics += OcrDiagnostic("result", "Total-carbohydrate row found but no usable per-100 cell")
                proseFallback(rows, columns, diagnostics)?.let { return it }
                LabelReading.NotFound
            }
```

- [ ] **Step 4: Run the gate test to verify it passes**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.ProseActivationGateTest"
```

Expected: PASS, all three cases.

- [ ] **Step 5: Run the whole JVM suite — this is the regression-sensitive step**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: no regressions. Pay particular attention to any existing test asserting `NotFound` — if one now returns a prose reading, decide deliberately: either the fixture genuinely is prose and the test's expectation should change (record why), or the eligibility predicate is too loose and **the predicate** must be tightened. Never loosen an assertion to make this pass.

- [ ] **Step 6: Stage check (no commit)**

```bash
git add -A --dry-run && git diff --check
```

---

## Task 7: Tabular serving column carrying its own weight

**Files:**
- Modify: `app/src/main/kotlin/app/justthecarbs/ocr/NutritionColumnKind.kt`
- Modify: `app/src/main/kotlin/app/justthecarbs/ocr/NutritionTableInterpreter.kt:195-242`
- Create: `app/src/test/kotlin/app/justthecarbs/ocr/ServingColumnWeightTest.kt`

**Interfaces:**
- Consumes: `ServingWeightAssociator.agreesWithTable(carbsPer100, servingWeight, printedCarbsPerServing): Boolean` — the **existing** corroboration function at `ServingWeightAssociator.kt:91`.
- Produces: no new public API. `ServingCarbCandidate.descriptor.weightOrVolume` becomes populated for headers that print their own weight; a header weight the arithmetic refuses drops the whole `ServingCarbCandidate` to `null`.

Photo 2 prints `ø/portie 50 g` and photo 7 prints `schaaltje (150 g)` — the weight is **inside the header**, whereas `ServingWeightAssociator.find` handles a weight on its **own line** beneath the header. Different acquisition sites, one corroboration rule. Route both through `agreesWithTable`; do **not** write a second tolerance.

**The two acquisition sites fail differently, and that asymmetry is deliberate.** A weight found on its own line by `ServingWeightAssociator.find` is the parser's own geometric inference: if the arithmetic refuses it, the inference was wrong and dropping just the weight leaves the per-serving carbohydrate figure — which the label really did print in a real column — intact on the direct-carbs path. A weight stated **inside the header** is different: the header is the one piece of text asserting what this column *is*. If `2,0/100 g` cannot produce the printed per-serving figure from the header's own stated `50 g`, then the header and the column disagree about their own contents, and there is no residual claim worth keeping. Keeping `carbsPerServing` while silently discarding the weight would hand the user a per-serving carbohydrate figure sourced from a column the parser has just concluded it cannot read. **A header-weight corroboration failure therefore drops the entire `ServingCarbCandidate`.** The canonical per-100 reading is unaffected either way.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class ServingColumnWeightTest {

    /** The Lidl grated-cheese shape: 2,0 per 100 g and 1,0 per 50 g portion. 2,0 x 50 / 100 = 1,0. */
    @Test
    fun adoptsAServingWeightPrintedInTheColumnHeaderWhenArithmeticAgrees() {
        val report = NutritionTableParser.parseWithDiagnostics(gratedCheese(servingCarbs = "1,0"))

        val serving = report.servingCandidate
        assertEquals(BigDecimal("1"), serving?.carbsPerServing?.stripTrailingZeros())
        assertEquals(
            BigDecimal("50"),
            serving?.descriptor?.weightOrVolume?.amount?.stripTrailingZeros(),
        )
    }

    /**
     * The same label with a per-serving figure the header's own weight cannot explain:
     * 2,0 x 50 / 100 = 1,0, not 9,9. The header and the column contradict each other, so there is no
     * trustworthy serving claim left to keep — the WHOLE candidate is dropped, not merely its weight.
     * Keeping carbsPerServing here would surface a figure read out of a column the parser has just
     * concluded it cannot read. The canonical per-100 reading is unaffected.
     */
    @Test
    fun dropsTheWholeServingCandidateWhenHeaderArithmeticDisagrees() {
        val report = NutritionTableParser.parseWithDiagnostics(gratedCheese(servingCarbs = "9,9"))

        assertEquals(
            BigDecimal("2"),
            (report.reading as LabelReading.Confident).candidate.value.stripTrailingZeros(),
        )
        assertNull(
            "a header weight the table's arithmetic refuses invalidates the whole serving candidate",
            report.servingCandidate,
        )
    }

    private fun gratedCheese(servingCarbs: String) = OcrDocument(
        width = 1200,
        height = 500,
        elements = listOf(
            OcrElement("per", OcrBox(300, 20, 360, 60), 0, 0),
            OcrElement("100", OcrBox(370, 20, 440, 60), 0, 0),
            OcrElement("g", OcrBox(450, 20, 480, 60), 0, 0),
            OcrElement("per", OcrBox(650, 20, 710, 60), 0, 0),
            OcrElement("portie", OcrBox(720, 20, 830, 60), 0, 0),
            OcrElement("50", OcrBox(840, 20, 890, 60), 0, 0),
            OcrElement("g", OcrBox(900, 20, 930, 60), 0, 0),
            OcrElement("Koolhydraten", OcrBox(40, 140, 290, 180), 0, 1),
            OcrElement("2,0", OcrBox(380, 140, 450, 180), 0, 1),
            OcrElement("g", OcrBox(460, 140, 490, 180), 0, 1),
            OcrElement(servingCarbs, OcrBox(790, 140, 860, 180), 0, 1),
            OcrElement("g", OcrBox(870, 140, 900, 180), 0, 1),
            OcrElement("waarvan", OcrBox(40, 240, 190, 280), 0, 2),
            OcrElement("suikers", OcrBox(200, 240, 330, 280), 0, 2),
            OcrElement("0,5", OcrBox(380, 240, 450, 280), 0, 2),
            OcrElement("g", OcrBox(460, 240, 490, 280), 0, 2),
        ),
    )
}
```

- [ ] **Step 2: Run to verify it fails**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.ServingColumnWeightTest"
```

Expected: `adoptsAServingWeight...` FAILS — the header weight is not currently parsed, so `weightOrVolume` is null.

- [ ] **Step 3: Parse the weight out of the serving header**

`descriptorFromHeader` (called at `NutritionTableInterpreter.kt:150`) already routes header text through `ServingSizeParser`. Confirm by inspection whether `ServingSizeParser.parseDescriptor` already returns a `weightOrVolume` for `"per portie 50 g"` and `"schaaltje (150 g)"`. If it does, no parser change is needed and only Step 4's corroboration applies.

If it does not, extend the header-to-descriptor path so a trailing weight in the header is captured — a general rule: *a serving header may state its own weight, and when it does that weight belongs to the serving it names.* Do not add a brand or fixture special case.

- [ ] **Step 4: Corroborate a header-supplied weight with the same function**

In `withPrintedWeight` (`NutritionTableInterpreter.kt:195`), the early return at line 204 (`if (descriptor.weightOrVolume != null) return descriptor`) currently trusts a header weight **without** arithmetic corroboration. That was safe when the only header weights were explicit phrases like "per 2 slices (70 g)"; it is not safe now that a header weight is routinely parsed.

The function's return type cannot express "reject the whole candidate" today — it returns a `ServingDescriptor`. Widen it to a small sealed result so the two outcomes are distinguishable at the type level rather than by a null that also means "no weight found":

```kotlin
    /**
     * The outcome of attaching a printed serving weight to [ServingDescriptor].
     *
     * [Rejected] exists because a header that states its own weight is making a claim about what the
     * column IS. When the table's arithmetic refuses that claim, the header and the column disagree,
     * and no part of the serving reading survives — including the per-serving carbohydrate figure,
     * which was read out of that same column. A geometric weight (found on its own line) failing the
     * same check only invalidates the parser's own inference, so it degrades to [Accepted] with the
     * weight left off, which is the pre-existing direct-carbs fallback.
     */
    private sealed interface PrintedWeightResult {
        data class Accepted(val descriptor: ServingDescriptor) : PrintedWeightResult
        data object Rejected : PrintedWeightResult
    }
```

Change `withPrintedWeight`'s return type to `PrintedWeightResult`, wrap each existing `return descriptor` / `return descriptor.copy(...)` as `Accepted(...)`, and replace the line-204 early return with:

```kotlin
        // A weight the header itself states is still checked against the table's own arithmetic —
        // the same rule, and the same function, used for a weight printed on its own line. The
        // acquisition site differs; the evidence standard must not. Writing a second tolerance here
        // is how the two drift apart. What differs is the CONSEQUENCE: a refused header weight
        // discredits the column the header names, so the whole candidate goes.
        descriptor.weightOrVolume?.let { stated ->
            val reference = perHundred.singleOrNull() ?: return PrintedWeightResult.Accepted(descriptor)
            if (!ServingWeightAssociator.agreesWithTable(reference.first, stated.amount, carbsPerServing)) {
                diagnostics += OcrDiagnostic(
                    "serving-weight",
                    "header weight ${stated.amount.toPlainString()} g rejected: " +
                        "${reference.first.toPlainString()}/100 does not give ${carbsPerServing.toPlainString()}" +
                        " — dropping the serving candidate",
                )
                return PrintedWeightResult.Rejected
            }
            return PrintedWeightResult.Accepted(descriptor)
        }
```

Then rework the `servingCandidate` construction at `NutritionTableInterpreter.kt:149-163` so `Rejected` collapses the candidate itself. Note that the existing code only calls `withPrintedWeight` when `descriptor != null`; a serving column whose header yielded no descriptor at all is unchanged by this task.

```kotlin
        val servingCandidate = serving?.let { value ->
            val descriptor = servingColumn?.headerText?.let(::descriptorFromHeader)
            val resolved = descriptor?.let {
                withPrintedWeight(it, servingColumn, rows, document, distinct, value, diagnostics)
            }
            // Rejected means the header's own stated weight contradicts the table. The per-serving
            // figure came out of that same column, so it is not salvaged.
            if (resolved is PrintedWeightResult.Rejected) return@let null
            ServingCarbCandidate(
                carbsPerServing = value,
                descriptor = (resolved as? PrintedWeightResult.Accepted)?.descriptor,
                rawHeaderText = servingColumn?.headerText.orEmpty(),
            )
        }
```

Keep the existing explanatory comment on the `descriptor =` line (the ServingWeightAssociator / direct-carbs-fallback note); move it onto the `withPrintedWeight` call so it stays attached to the code it describes.

- [ ] **Step 5: Run the test to verify it passes**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.justthecarbs.ocr.ServingColumnWeightTest"
```

Expected: PASS, both cases.

- [ ] **Step 6: Run the whole JVM suite**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: no regressions. The Kinder synthetic fixtures in `NutritionTableInterpreterTest` and `ServingWeightAssociatorTest` exercise the separate-line path and must still pass — if one fails, the corroboration you added is rejecting a weight it previously accepted, which needs understanding rather than a loosened tolerance.

- [ ] **Step 7: Stage check (no commit)**

```bash
git add -A --dry-run && git diff --check
```

---

## Task 8: Baseline-driven fixes (conditional)

**Files:** determined by Task 2's baseline. Candidates named by the measurements below: `NutritionColumnKind.kt` (spurious basis column), `NutritionTerminology.kt` (unseen language forms), `NutritionTableInterpreter.kt` `NUMBER` regex (token fragmentation). **Not** `RowSlopeEstimator.kt` — see below.

**Interfaces:**
- Consumes: `docs/plans/2026-08-16-real-image-baseline.md` from Task 2.
- Produces: nothing new; existing behaviour generalized.

**Execute this task only for failures Task 2 actually measured.** Do not pre-emptively implement any of the below.

### What the baseline actually measured, and what it means for this task

Two of the nine-fixture results are **not** what the plan anticipated, and this task is where they land.

**Fixtures 3 and 4 return a confident WRONG value today — the saturated-fat figure (20.0 and 19.0) as total carbohydrate.** They are not `NotFound`. This matters twice over:

- It is the worst outcome the whole pass exists to prevent, and it exists on `main` right now.
- **The prose reader cannot fix it.** The activation gate is tabular-`NotFound`-only, and these are `Confident`. Tasks 4–6 will not touch these fixtures at all. Whatever is producing a confident fat figure must be fixed *in the tabular path*, in this task, and only then can the resulting `NotFound` be picked up by the prose stage.

So the fix order for 3 and 4 is: first make the tabular path stop returning a wrong confident value (its current mechanism, per the baseline, is cross-row column binding with no distance or relevance constraint, letting a column resolved from a distant standalone header apply to an unrelated row); then verify they fall through to `NotFound`; then confirm the prose stage reads them. Assert all three states — a fixture that goes straight from wrong-confident to right-confident without a demonstrable `NotFound` in between has not proven which stage answered it, which is what `report.provenance` is for.

**Fixture 7 fails through a shape the plan never named:** an incidental phrase elsewhere on the crop resolves as a second, spurious basis column that geometrically out-competes the real one. The general rule to look for is about *what may become a column* — a basis phrase appearing in running text is not a column header — not about loosening the real column's acceptance. Do not fix it by preferring the column nearer the values; that is a proximity score, and it is the mechanism the geometry-first rewrite removed.

**Curvature is a closed question: do not spend time on it.** The baseline measured fixture 4 at 27 logical rows with no fragmentation attributable to curvature (uniform whole-image skew −0.0139). `RowSlopeEstimator` handled the curved lid correctly. Record this as a negative result and change nothing there.

**Fixtures 1, 2 and 5 are `ML_KIT_RECOGNITION` failures** — an OCR-mangled basis header or a unit-into-digit substitution. Per the Global Constraints, do not change the parser for these; they are Step 4's preprocessing candidates and nothing else. Note that fixture 2 is *also* confidently wrong (2.09 for 2.0), which is a recognition artifact rather than an interpretation defect: 2.09 is what ML Kit produced, so no downstream rule can honestly recover 2.0. If preprocessing does not fix it, the honest outcome is to record it as an accepted recognition-stage failure rather than to write a rule that repairs this particular digit.

- [ ] **Step 1: For each remaining failing fixture, write the smallest pure regression test first**

The synthetic test represents the *underlying condition*, never the photograph. Examples of the right shape:

- A column resolved from a distant standalone header applied to an unrelated row → a synthetic document where a header sits far from a nutrient row, asserting the value is **not** attributed to it (fixtures 3/4).
- A basis phrase in running text became a column → a synthetic document with a real basis column plus an incidental `per 100 g` in a sentence, asserting one column, not two (fixture 7).
- `"53,"` + `"5g"` arrived as two tokens → a numeric-tokenization fixture.
- A language form was unrecognized → a `NutritionTerminologyTest` case using the **observed** ML Kit text.

- [ ] **Step 2: Run it to verify it fails, then make the smallest general change**

Rules that bind here:
- Terminology may be extended **only** with forms observed in these nine fixtures' actual ML Kit output. No speculative languages.
- Geometry: measure, then change the smallest general rule. Do **not** raise `LogicalRowThresholds.MAX_CENTER_DISTANCE_IN_HEIGHT` — that is the single-linkage failure mode the slope estimator exists to avoid.
- If a failure is `ML_KIT_RECOGNITION`, do **not** change the parser. Record it for Step 4.

- [ ] **Step 3: Re-run the pure suite plus the real-image suite after each fix**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=app.justthecarbs.ocr.RealImageOcrTest
```

A fix that improves one fixture while breaking another is not a fix. Sondey and Kinder are the canaries.

- [ ] **Step 4: Preprocessing experiment — only if a recognition-stage failure was measured**

If and only if Task 2 recorded `ML_KIT_RECOGNITION` for a fixture, benchmark cheap options across **all nine**: original RGB, grayscale, mild contrast normalization, adaptive crop margin. Retain a transformation only if it improves at least one fixture and degrades none, and only if production applies the identical transformation. Record the result — including "tried, not retained" — in the baseline document.

If no recognition failure was measured, skip this step entirely and record that it was skipped and why.

- [ ] **Step 5: Stage check (no commit)**

```bash
git add -A --dry-run && git diff --check
```

---

## Task 9: Mandatory nine-fixture real-image suite

**Files:**
- Modify: `app/src/androidTest/kotlin/app/justthecarbs/ocr/RealImageOcrTest.kt`

**Interfaces:**
- Consumes: everything above.
- Produces: the mandatory regression suite.

- [ ] **Step 1: Remove the skip mechanism**

Delete every `assumeTrue(...)` call and change `loadAsset` so a missing fixture **fails**:

```kotlin
    /**
     * The fixture, or a test failure.
     *
     * These images are committed (2026-08-16) precisely so CI cannot skip the highest-value
     * regression tests. A missing fixture is a broken checkout, not a reason to report green — the
     * previous `Assume`-based skip is exactly how a 400-test green suite coexisted with a scanner
     * that failed on real packaging.
     */
    private fun loadAsset(name: String): Bitmap {
        val stream = try {
            testContext.assets.open("ocr_real/$name")
        } catch (e: IOException) {
            throw AssertionError("ocr_real/$name is missing; it is a committed, mandatory fixture", e)
        }
        return stream.use {
            requireNotNull(BitmapFactory.decodeStream(it)) { "ocr_real/$name is present but could not be decoded" }
        }
    }
```

Update the two existing Sondey/Kinder call sites to drop `!!` and the `assumeTrue` lines.

- [ ] **Step 2: Add a case per new fixture, asserting value, basis, negatives and provenance**

Follow the existing method style. For each of fixtures 1–7 write a test asserting the canonical value and basis from the plan's golden table, plus the forbidden values from its last column. For fixtures 1, 5 and 7 assert `report.provenance is CandidateProvenance.FromRow` and that the row text names the carbohydrate term, not the sugars term. For fixtures 3, 4 and 6 assert `report.provenance is CandidateProvenance.FromProseSpan` and that `nutrientTerm` is a carbohydrate term.

**Provenance is part of the golden assertion for the prose fixtures, not a decoration (owner, 2026-08-17).**

- **Fixture 3 prints its total AND its sugars as `1,6 g`.** A numeric match on `1.6` therefore proves nothing whatsoever — a sugars misread passes it. Success requires `FromProseSpan` **anchored to the total-carbohydrate declaration**: assert that `nutrientTerm` is a carbohydrate term and is **not** a child term. Without that assertion this test is decorative.
- **Fixture 4** likewise asserts `FromProseSpan` with total **3.0**, and **`2.5` remains explicitly forbidden** — it is that label's sugars figure.

```kotlin
    @Test
    fun juiceReadsNinePerHundredMillilitresFromTheTotalRowNotTheSugarsRow() {
        val document = documentFor(loadAsset(JUICE))
        val report = NutritionTableParser.parseWithDiagnostics(document)

        val reading = report.reading
        assertTrue("expected a confident reading${explain(document, report)}", reading is LabelReading.Confident)
        val candidate = (reading as LabelReading.Confident).candidate
        assertEquals("wrong total${explain(document, report)}", BigDecimal("9"), candidate.value.stripTrailingZeros())
        assertEquals(app.justthecarbs.domain.NutritionBasis.PER_100_ML, candidate.basis)

        // Total and sugars both print 9,0 on this label, so the value alone proves nothing.
        val provenance = report.provenance
        assertTrue("expected row provenance${explain(document, report)}", provenance is CandidateProvenance.FromRow)
        val rowText = NutritionTerminology.normalize((provenance as CandidateProvenance.FromRow).rowText)
        assertTrue("must not be the sugars row${explain(document, report)}", "suikers" !in rowText && "zucker" !in rowText)
    }

    @Test
    fun stokbroodReadsFortySixFromProseAndNeverItsSugarsOrFibre() {
        val document = documentFor(loadAsset(STOKBROOD))
        val report = NutritionTableParser.parseWithDiagnostics(document)

        val reading = report.reading
        assertTrue("expected a confident reading${explain(document, report)}", reading is LabelReading.Confident)
        assertEquals(BigDecimal("46"), (reading as LabelReading.Confident).candidate.value.stripTrailingZeros())

        listOf("1.0", "4.7", "12").forEach { forbidden ->
            assertTrue(
                "$forbidden must never be the total${explain(document, report)}",
                valuesOffered(report.reading).none { it.compareTo(BigDecimal(forbidden)) == 0 },
            )
        }
        // Per-100 only: prose never yields a serving figure.
        assertNull("prose must not produce a serving${explain(document, report)}", report.servingCandidate)
    }
```

Write the remaining cases in the same shape. Assert serving candidates for fixtures 2 (`1.0` g per `50` g) and 7 (`7.5` g per `150` g), and `assertNull(report.servingCandidate)` for 3, 4 and 6.

- [ ] **Step 3: Add the gate assertion for the two legacy fixtures**

```kotlin
    /**
     * The prose reader must be unreachable for a readable table. Both fixtures return `Confident`
     * from the tabular path, so row provenance is the observable proof it was never consulted.
     */
    @Test
    fun theProseReaderIsNeverConsultedForSondeyOrKinder() {
        listOf(SONDEY, KINDER).forEach { name ->
            val document = documentFor(loadAsset(name))
            val report = NutritionTableParser.parseWithDiagnostics(document)
            assertTrue(
                "$name must be read by the table path${explain(document, report)}",
                report.provenance is CandidateProvenance.FromRow,
            )
        }
    }
```

- [ ] **Step 4: Run the real-image suite**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=app.justthecarbs.ocr.RealImageOcrTest
```

Expected: every case passes. A failure here means either a real defect (go back to Task 8 with the diagnostics) or a wrong golden value — and the golden values are owner-confirmed, so re-read the photograph before doubting them.

- [ ] **Step 5: Stage check (no commit)**

```bash
git add -A --dry-run && git diff --check
```

---

## Task 10: Full verification and documentation

**Files:**
- Modify: `CLAUDE.md`
- Modify: `docs/manual-qa.md`

**Interfaces:**
- Consumes: everything above.
- Produces: the final report.

- [ ] **Step 1: Run every suite, for real — no `UP-TO-DATE`**

```powershell
$env:JAVA_HOME="C:\atools\jdk-21.0.12+8"
$env:ANDROID_HOME="C:\atools\sdk"
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Record exact counts for each. `connectedDebugAndroidTest` covers the migration/DAO tests as well as the UI and real-image ones.

Note the known UTP flake: a full instrumented run occasionally aborts with `TEST_EXECUTION_FAILED` part way through. It does not reproduce and affected classes pass in isolation. If it happens, re-run and say so explicitly in the report — do not report it as a code failure, and do not report a partial run as a pass.

- [ ] **Step 2: Verify R8 still strips the diagnostics**

```powershell
Select-String -Path app\build\outputs\mapping\release\mapping.txt -Pattern "OcrDiagnosticsReport|CandidateProvenance" -SimpleMatch
```

Expected: no match for `OcrDiagnosticsReport`. If `CandidateProvenance` survives, that is acceptable only if it is genuinely reachable from non-debug code — check, and if it is only used by diagnostics, confirm it is stripped too.

- [ ] **Step 3: Confirm nothing forbidden became tracked**

```bash
git status --short
git diff --stat
git diff --check
```

Expected: no `*.jks`, `keystore.properties`, `local.properties`, `*.apk`, `*.aab`, no full-frame originals. Nine sanitized fixtures are intentionally tracked.

- [ ] **Step 4: Update `CLAUDE.md`**

Add a section dated 2026-08-16 covering: the nine-fixture mandatory corpus and the gitignore policy reversal; the prose reader and its two independent gates; span- vs row-level provenance and why row level is insufficient for prose; the header-weight corroboration now routing through `agreesWithTable`; and the measured baseline findings.

Correct the existing "The images live in `app/src/androidTest/assets/ocr_real/` (git-ignored…)" sentence in the real-device scanner section — it is now false.

- [ ] **Step 5: Update `docs/manual-qa.md`**

Keep §15f (physical camera gate) **open**. Add a line making explicit what nine passing JPEGs do and do not prove:

> Proven: real photographed JPEG → ML Kit → `MlKitOcrMapper` → parser, on nine real packages.
> Still requires hardware: live CameraX capture → ViewPort → shutter → ImageCapture → EXIF → ROI crop → OCR.

- [ ] **Step 6: Write the final report**

Cover all 31 items the owner asked for, including: the exact nine filenames; transcribed expected values; baseline result per fixture; ML Kit raw-recognition quality per fixture; failure-stage classification; production changes and the general rule each represents; geometry/terminology/numeric/column/serving changes; preprocessing experiments and whether retained; the BEFORE/AFTER table; Sondey and Kinder results after the changes; fixture and test counts; every suite's result; R8 verification; memory observations; any image still failing with its exact failure stage; the outstanding physical CameraX gate; `git diff --stat`; `git status --short`; and the current HEAD SHA.

State BEFORE/AFTER per stage, never as "OCR improved substantially". Do not call the OCR system perfect.

- [ ] **Step 7: Leave everything uncommitted**

Per the owner's §22, do not commit or push. Report `git status --short` and let the owner review.

---

## Self-Review

**Spec coverage:** Prose reader with all ten contract rules → Tasks 4–6. Activation gate incl. the `Ambiguous` exclusion → Task 6 Step 3. Prose-eligibility predicate → Task 4. Declaration-based basis binding, competing/absent → `NotFound` → Task 5. Span-scoped exclusion → Task 5. Per-100-only (no serving from prose) → Task 6 Step 3 + Task 9 Step 2 null assertions. Tabular serving weight with arithmetic gate through the single shared function → Task 7. Provenance at matched granularity → Task 3, asserted in Task 9. Nine committed fixtures, mandatory → Tasks 1 and 9. Baseline before code → Task 2. Curvature/terminology/numeric, conditional on measurement → Task 8. Preprocessing only on measured recognition failure → Task 8 Step 4. Full verification, R8, docs, camera gate open → Task 10.

**Placeholder scan:** No TBD/TODO. Task 8 is deliberately conditional on Task 2's measurements — its steps specify the *method* and the binding rules, which is the most that can honestly be written before the baseline exists. Task 1 Step 2's cropping is a manual image operation; the constraints (crop only, no enhancement, quality ≥95) are stated exactly.

**Type consistency:** `CandidateProvenance.FromRow(rowText, rowBox)` and `FromProseSpan(nutrientTerm, valueElementIndices, rowText)` defined in Task 3, used identically in Tasks 5, 6 and 9. `ProseNutritionReader.isProseLabel(List<LogicalRow>, List<NutritionColumn>): Boolean` (Task 4) and `read(List<LogicalRow>): ProseResult` (Task 5) called as defined in Task 6, which passes the columns the table stage already classified rather than re-classifying. `withPrintedWeight` returns `PrintedWeightResult` (Task 7), whose `Rejected` case nulls the whole `ServingCarbCandidate`. `ServingWeightAssociator.agreesWithTable(BigDecimal, BigDecimal, BigDecimal): Boolean` matches the existing signature at `ServingWeightAssociator.kt:91`. `NutritionParseReport`'s fourth parameter `provenance` is defaulted, so pre-existing three-argument constructions still compile.

**Known adaptation point:** Task 3 Step 5 depends on `rowFor`'s return type at `NutritionTableInterpreter.kt:244`, which the plan instructs the implementer to check rather than assume.

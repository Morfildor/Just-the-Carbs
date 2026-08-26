package app.justthecarbs.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import java.io.IOException

/**
 * DIAGNOSTIC — asserts nothing. Shows the exact recognized tokens around the basis phrase on the two
 * fixtures whose declarations never open.
 *
 * ## MEASURED RESULT: widening the declaration opener would NOT fix either fixture.
 *
 * Run 2026-08-17. The recognized tokens are exactly as the deferral note predicted —
 * `naringsindhold` `100g` and the fused `pourperlpro` `100g` — but **that is not where either
 * fixture fails**:
 *
 * ```
 * real_jar_prose_multilingual_03  column: PER_100_G 'Naringsindhold (100g): Energiel eneri' @ x=405.0
 *                                 result: Total-carbohydrate row found but no usable per-100 cell
 *                                 prose:  not a prose label; no fallback
 *
 * real_lid_prose_curved_04        column: PER_100_G 'PourPerlPro 100g: Energie /' @ x=231.5
 *                                 result: Total-carbohydrate row found but no usable per-100 cell
 *                                 prose:  not a prose label; no fallback
 * ```
 *
 * Both already **resolve a `PER_100_G` column** from the very phrase that supposedly cannot be
 * recognised — `ColumnClassifier` matches `100g` without needing a connective. Both then find a
 * total-carbohydrate row and fail to place a value in that column. The prose reader is only ever
 * reached from a tabular `NotFound` and is refused here by the *eligibility* gate, which is behaving
 * correctly: a document with a resolved basis column is claimed by the tabular path.
 *
 * So the blocker is **cell-to-column association on a curved or prose-formatted label**, not the
 * declaration grammar. Building a constrained heading-plus-`(100 g)` opener is real work with real
 * risk — and on this evidence it would buy nothing on either fixture. Measure before building it.
 *
 * Fixtures 3 and 4 return NotFound not because the prose reader refuses them but because neither
 * opens a declaration at all: `basisPhraseAt` requires a connective, and these print
 * `Næringsindhold (100g):` (a Danish noun) and a fused `PourPerlPro 100g:`.
 *
 * Widening the opener is explicitly gated on knowing what the recognizer actually returns. The
 * deferral note warns that fixture 3 prints the SAME number for its total and its sugars, so a wrongly
 * bound term there is undetectable by value — which makes guessing at a grammar from the printed
 * package, rather than from the recognized tokens, exactly the wrong method.
 *
 * Read the tag `JustTheCarbsDecl`.
 */
class BlockedDeclarationDiagnosticTest {

    private val testContext get() = InstrumentationRegistry.getInstrumentation().context

    private fun assetBitmap(name: String): Bitmap {
        val stream = try {
            testContext.assets.open("ocr_real/$name")
        } catch (e: IOException) {
            throw AssertionError("ocr_real/$name missing", e)
        }
        return stream.use { requireNotNull(BitmapFactory.decodeStream(it)) }
    }

    private fun documentOf(bitmap: Bitmap): OcrDocument {
        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
            com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS,
        )
        return try {
            val text = com.google.android.gms.tasks.Tasks.await(
                recognizer.process(com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)),
                60,
                java.util.concurrent.TimeUnit.SECONDS,
            )
            MlKitOcrMapper.toDocument(text, bitmap.width, bitmap.height)
        } finally {
            recognizer.close()
        }
    }

    @Test
    fun showTheTokensAroundEveryHundredOnTheBlockedFixtures() {
        val fixtures = listOf(
            "real_jar_prose_multilingual_03.jpg",
            "real_lid_prose_curved_04.jpg",
        )

        Log.i(TAG, "=".repeat(96))
        Log.i(TAG, "BLOCKED DECLARATIONS — what precedes '100 g' in the RECOGNIZED text")
        Log.i(TAG, "A declaration opener must be read off these tokens, never off the printed package.")
        Log.i(TAG, "=".repeat(96))

        fixtures.forEach { name ->
            val bitmap = assetBitmap(name)
            try {
                val document = documentOf(bitmap)
                val rows = LogicalRowBuilder.build(document)
                Log.i(TAG, "")
                Log.i(TAG, "--- $name  ${document.elements.size} elements, ${rows.size} rows ---")

                // Every place a "100" or a fused "100g"/"100ml" appears, with the three tokens
                // before it. That window is what any opener grammar would have to match.
                rows.forEach { row ->
                    val words = row.elements.map { NutritionTerminology.normalize(it.text) }
                    words.forEachIndexed { index, word ->
                        if (!HUNDRED.containsMatchIn(word)) return@forEachIndexed
                        val before = words.subList(maxOf(0, index - 3), index)
                        val after = words.subList(index, minOf(words.size, index + 3))
                        Log.i(
                            TAG,
                            "  before=[${before.joinToString(" | ")}]  AT=[${after.joinToString(" | ")}]",
                        )
                        Log.i(TAG, "      raw row: '${row.text.take(90)}'")
                    }
                }

                val report = NutritionTableParser.parseWithDiagnostics(document)
                Log.i(TAG, "  parse outcome: ${report.reading}")
                report.diagnostics
                    .filter { it.stage in setOf("prose", "result", "column") }
                    .forEach { Log.i(TAG, "      ${it.stage}: ${it.message}") }
            } finally {
                bitmap.recycle()
            }
        }
        Log.i(TAG, "=".repeat(96))
    }

    private companion object {
        const val TAG = "JustTheCarbsDecl"

        /** A bare 100, or a 100 fused to its unit, in normalized text. */
        val HUNDRED = Regex("^100(g|ml)?$")
    }
}

package app.justthecarbs.ocr

import app.justthecarbs.ocr.DutchLabelFixtures.element
import app.justthecarbs.ocr.DutchLabelFixtures.offeredValues
import app.justthecarbs.ocr.DutchLabelFixtures.outcome
import app.justthecarbs.ocr.EuropeanLabelFixtures.LANGUAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nutrition tables in the European languages and Turkish — what `EuropeanLabelDiagnosticTest`
 * measured on 2026-09-17, before and after the vocabulary and basis-phrase work.
 *
 * Hungarian is the exception throughout: its word for carbohydrate is deliberately not vocabulary
 * (see `NutritionTerminology`'s `hu` entry), so a Hungarian-only table reads nothing.
 */
class EuropeanNutritionTableTest {

    private val readable = LANGUAGES.filter { it.code != "hu" }
    private fun language(code: String) = LANGUAGES.first { it.code == code }

    @Test
    fun `a table in every language reads the per-100 figure`() {
        readable.forEach { language ->
            assertEquals(language.code, "Confident 62.5 PER_100_G", outcome(EuropeanLabelFixtures.table(language)))
            language.carbohydrateSpellings.forEach { spelling ->
                assertEquals(
                    "${language.code} '$spelling'",
                    "Confident 62.5 PER_100_G",
                    outcome(EuropeanLabelFixtures.table(language, carbohydrate = spelling)),
                )
            }
        }
    }

    @Test
    fun `a drink in every language reads per 100 ml`() {
        readable.forEach { language ->
            assertEquals(language.code, "Confident 4.5 PER_100_ML", outcome(EuropeanLabelFixtures.drink(language)))
        }
    }

    /**
     * Per 100, per portion and percentage, with and without `%` in the percentage cells. Before
     * 2026-09-17 eleven of these languages left the portion column unrecognised (`w porcji`,
     * `Porsiyonda`, `por ración`, ...), and every language whose header is not `%RI` lost the
     * percentage column when its cells carried no `%`.
     */
    @Test
    fun `a three-column table resolves all three columns in every language`() {
        readable.forEach { language ->
            listOf(true, false).forEach { signs ->
                val doc = EuropeanLabelFixtures.table(language, columns = 3, percentSigns = signs)
                val label = "${language.code} %signs=$signs"
                assertEquals(label, "Confident 62.5 PER_100_G", outcome(doc))
                val kinds = columnKinds(doc)
                assertTrue("$label: $kinds", NutritionColumnKind.PER_100_G in kinds)
                assertTrue("$label: $kinds", NutritionColumnKind.PER_SERVING in kinds)
                assertTrue("$label: $kinds", NutritionColumnKind.REFERENCE_PERCENT in kinds)
            }
        }
    }

    /** `100 g'da`, `100 g-ban`, `100 g:ssa`: a case suffix on the unit no longer hides the basis. */
    @Test
    fun `a basis unit carrying a case suffix heads a per-100 column`() {
        listOf("tr", "hu", "fi").forEach { code ->
            val doc = EuropeanLabelFixtures.table(language(code))
            assertTrue(code, NutritionColumnKind.PER_100_G in columnKinds(doc))
        }
        assertEquals("Confident 62.5 PER_100_G", outcome(EuropeanLabelFixtures.table(language("fi"))))
        assertEquals("Confident 4.5 PER_100_ML", outcome(EuropeanLabelFixtures.drink(language("fi"))))
    }

    /**
     * Two printed rows merged into one, the child's figure in the total's column. Before these words
     * were listed — singular sugars, the official Polish and Czech polyols, Lithuanian `cukrūs`, the
     * Finnish partitive — the total and the child figure were offered side by side.
     */
    @Test
    fun `a merged row naming a child nutrient never offers its figure`() {
        LANGUAGES.forEach { language ->
            language.childTerms.forEach { child ->
                assertEquals(
                    "${language.code} '$child'",
                    emptyList<String>(),
                    offeredValues(EuropeanLabelFixtures.mergedRow(language, child)),
                )
            }
        }
    }

    /**
     * A row that states its own basis. The `100` of `por 100 g` / `100 g kohta` read as the
     * carbohydrate figure — `Confident 100.0` in Spanish, Portuguese and Estonian — until `por`, `je`,
     * `pour`, `ve`, `v` and `la` were connectives and `kohta`, `için` and case-suffixed units marked a
     * basis the same way.
     */
    @Test
    fun `the 100 of a basis phrase is never read as the carbohydrate figure`() {
        LANGUAGES.forEach { language ->
            val doc = EuropeanLabelFixtures.inlineBasisRows(language)
            assertFalse(language.code, outcome(doc).startsWith("Confident 100"))
            // Latvian and Lithuanian print `100 g` with no word marking it as a basis, which is
            // indistinguishable from an amount of 100 g: offered as a choice, never read.
            if (language.code !in setOf("lv", "lt")) {
                assertFalse("${language.code}: ${offeredValues(doc)}", "100" in offeredValues(doc))
            }
        }
        listOf("de", "fr", "cs", "sk", "ro", "fi", "tr").forEach { code ->
            assertEquals(code, "Confident 62.5 PER_100_G", outcome(EuropeanLabelFixtures.inlineBasisRows(language(code))))
        }
    }

    /**
     * The carbohydrate row's gram figure lost, its bare percentage `7` left. With a percentage header
     * the classifier did not know (`%RM*`, `%RWS*`, `%BRD*`, ...), that `7` read as `Confident 7.0` in
     * fourteen languages.
     */
    @Test
    fun `a percentage beside a lost carbohydrate figure is never read as grams`() {
        LANGUAGES.forEach { language ->
            val doc = EuropeanLabelFixtures.tableWithLostCarbohydrateCell(language)
            assertEquals(language.code, "NotFound", outcome(doc))
            assertTrue(language.code, NutritionColumnKind.REFERENCE_PERCENT in columnKinds(doc))
        }
    }

    /** The declaration as a sentence: the total, never the fat or the sugars figure beside it. */
    @Test
    fun `a running-text declaration never offers another nutrient's figure`() {
        LANGUAGES.forEach { language ->
            val offered = offeredValues(EuropeanLabelFixtures.prose(language))
            assertTrue("${language.code}: $offered", offered.all { it == "46" })
        }
        LANGUAGES.map { it.code }.filterNot { it in setOf("hu", "lv", "lt") }.forEach { code ->
            assertEquals(code, "Confident 46.0 PER_100_G", outcome(EuropeanLabelFixtures.prose(language(code))))
        }
    }

    /**
     * A footer naming portions in the running text (`Opakowanie zawiera 10 porcji`, `Paket 10
     * porsiyon içerir`) is not a column header that changes the reading — laid out under the value
     * columns, where a portion word could put a column beside the per-100 one.
     */
    @Test
    fun `a sentence counting portions does not disturb the reading`() {
        mapOf(
            "pl" to "Opakowanie zawiera 10 porcji",
            "tr" to "Paket 10 porsiyon içerir",
            "es" to "Este envase contiene 10 raciones",
            "cs" to "Balení obsahuje 10 porci",
        ).forEach { (code, footer) ->
            val table = EuropeanLabelFixtures.table(language(code), columns = 3)
            var x = 560
            val footerElements = footer.split(' ').map { word ->
                element(word, x, 760, x + 14 * word.length, 790, line = 9).also { x += 14 * word.length + 10 }
            }
            val doc = table.copy(elements = table.elements + footerElements, height = 900)
            assertEquals(code, "Confident 62.5 PER_100_G", outcome(doc))
        }
    }

    /**
     * The real Indomie capture, with the wrapped line under its multilingual carbohydrate row
     * (`Szénhidrát`) replaced by the Dutch word — a known term, as any wrapped language would be.
     *
     * The row builder merged that line with the start of the sugars clause and its `29g` (the 2,9 g
     * sugars figure). The carbohydrate row above it printed `73` with no unit, refused. Without the
     * interpreter's refused-elsewhere rule, the second total line's `29` read as `Confident 29.0`.
     */
    @Test
    fun `a wrapped carbohydrate line never substitutes its figure for the refused one above it`() {
        val capture = SeventeenthSessionFixtures.c20260904_134501_895()
        val wrapped = capture.copy(
            elements = capture.elements.map { if (it.text == "Szénhidrát") it.copy(text = "Koolhydraten") else it },
        )
        val rows = LogicalRowBuilder.build(wrapped)
        val totals = RowClassifier.classifyAll(rows).count { it == NutritionRowKind.TOTAL_CARBOHYDRATE }
        assertEquals("precondition: the wrapped line is a second total row", 2, totals)

        assertEquals("NotFound", outcome(wrapped))
        assertEquals(emptyList<String>(), offeredValues(wrapped))
    }

    /** The unmodified capture reads nothing, exactly as it did on the device. */
    @Test
    fun `the Indomie capture itself still reads nothing and keeps its focused-entry target`() {
        val capture = SeventeenthSessionFixtures.c20260904_134501_895()
        assertEquals("NotFound", outcome(capture))
        assertTrue(FocusedAmountEntry.of(capture) != null)
    }

    @Test
    fun `a Hungarian-only table reads nothing`() {
        val hungarian = language("hu")
        assertEquals("NotFound", outcome(EuropeanLabelFixtures.table(hungarian)))
        assertEquals(emptyList<String>(), offeredValues(EuropeanLabelFixtures.table(hungarian, columns = 3)))
    }

    private fun columnKinds(doc: OcrDocument): List<NutritionColumnKind> =
        ColumnClassifier.classify(LogicalRowBuilder.build(doc), doc.width).map { it.kind }
}

package app.justthecarbs.ocr

import app.justthecarbs.domain.NutritionBasis
import app.justthecarbs.domain.PortionUnitKind
import app.justthecarbs.domain.ServingSizeParser
import app.justthecarbs.ocr.DutchLabelFixtures.CARBOHYDRATE_SPELLINGS
import app.justthecarbs.ocr.DutchLabelFixtures.CHILD_TERMS
import app.justthecarbs.ocr.DutchLabelFixtures.mergedTotalAndChildRow
import app.justthecarbs.ocr.DutchLabelFixtures.offeredValues
import app.justthecarbs.ocr.DutchLabelFixtures.outcome
import app.justthecarbs.ocr.DutchLabelFixtures.table
import app.justthecarbs.ocr.DutchLabelFixtures.twoColumnTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dutch nutrition tables, end to end through the real interpreter (2026-08-26).
 *
 * The app's interface is English by decision, and this is the other half of that decision: the
 * *packaging* it reads is Dutch, and Dutch is the owner's own market. Every case here was found by
 * running [DutchLabelDiagnosticTest] against printed Dutch label forms and reading what came back —
 * not by inspecting the vocabulary and imagining what might be missing.
 *
 * ## What was actually wrong
 *
 * 1. **`per 100 gram` resolved no column at all**, so a correct value on a correct total row was
 *    discarded and the scan returned `NotFound`. Four stages each held their own `(g|ml)` literal;
 *    fixing one was not enough, because [RowClassifier] has to type the row `HEADER` before
 *    [ColumnClassifier] will look at it.
 * 2. **Dutch child-nutrient names were missing** — Dutch prints `sacharose` where English prints
 *    `sucrose`, and uses transparent compounds (`melksuiker`, `druivensuiker`, `vruchtensuiker`)
 *    that share no stem with their Latin equivalents. On a merged row that produced
 *    `Ambiguous [62, 35]`: the app asked someone about to dose insulin to choose between the total
 *    and the sugars figure, with nothing on screen to say which was which.
 * 3. **`Koolhydraat`, `Koolhydr.` and the hyphen-split `Kool-hydraten` were not the word for
 *    carbohydrate**, so those labels read nothing at all.
 */
class DutchNutritionTableTest {

    private fun confident(document: OcrDocument): LabelReading.Confident {
        val reading = NutritionTableInterpreter.interpret(document).reading
        assertTrue("expected Confident, got ${outcome(document)}", reading is LabelReading.Confident)
        return reading as LabelReading.Confident
    }

    private fun standardTable(carbLabel: String, header: List<String> = listOf("per", "100", "g")) =
        table(
            header = header,
            rows = listOf(
                "Vetten" to listOf("12"),
                carbLabel to listOf("62"),
                "waarvan suikers" to listOf("35"),
                "Eiwitten" to listOf("6,2"),
            ),
        )

    // ---- the carbohydrate term ----------------------------------------------------------------

    @Test
    fun `every Dutch spelling of carbohydrate reads the total`() {
        CARBOHYDRATE_SPELLINGS.forEach { spelling ->
            val reading = confident(standardTable(spelling))
            assertEquals(
                "\"$spelling\" must read the total row's value",
                0,
                java.math.BigDecimal("62").compareTo(reading.candidate.value),
            )
        }
    }

    /**
     * Dutch hyphenates long compounds across a line break, and normalization turns the hyphen into a
     * space — so the printed `Kool-hydraten` reaches the matcher as two words. The same applies to
     * German `Kohlen-hydrate`.
     */
    @Test
    fun `a hyphen-split carbohydrate compound is still the carbohydrate row`() {
        assertEquals(0, java.math.BigDecimal("62").compareTo(confident(standardTable("Kool-hydraten")).candidate.value))
        assertEquals(
            0,
            java.math.BigDecimal("62").compareTo(
                confident(
                    table(
                        header = listOf("pro", "100", "g"),
                        rows = listOf("Kohlen-hydrate" to listOf("62"), "davon Zucker" to listOf("35")),
                    ),
                ).candidate.value,
            ),
        )
    }

    /** "kool" alone is Dutch for cabbage. The two-word form must not match it on its own. */
    @Test
    fun `the split form does not make a bare kool a carbohydrate row`() {
        val document = table(
            header = listOf("per", "100", "g"),
            rows = listOf("Rode kool" to listOf("62"), "Eiwitten" to listOf("6,2")),
        )
        assertEquals("NotFound", outcome(document))
    }

    // ---- the basis header ---------------------------------------------------------------------

    @Test
    fun `Dutch basis headers resolve the right basis`() {
        val gramHeaders = listOf(
            listOf("per", "100", "g"),
            listOf("per", "100", "gram"),
            listOf("per", "100g"),
            listOf("Voedingswaarde", "per", "100", "g"),
            listOf("Voedingswaarden", "per", "100", "g"),
            listOf("Gemiddelde", "voedingswaarde", "per", "100", "g"),
            listOf("Voedingswaarde", "100", "g"),
            listOf("Voedingswaarde", "(100", "g)"),
        )
        gramHeaders.forEach { header ->
            val reading = confident(standardTable("Koolhydraten", header))
            assertEquals(
                "\"${header.joinToString(" ")}\" must resolve grams",
                NutritionBasis.PER_100_G,
                reading.candidate.basis,
            )
        }

        val millilitreHeaders = listOf(
            listOf("per", "100", "ml"),
            listOf("per", "100", "milliliter"),
            listOf("Per", "100", "ML"),
            listOf("Voedingswaarde", "per", "100", "ml"),
        )
        millilitreHeaders.forEach { header ->
            val reading = confident(standardTable("Koolhydraten", header))
            assertEquals(
                "\"${header.joinToString(" ")}\" must resolve millilitres",
                NutritionBasis.PER_100_ML,
                reading.candidate.basis,
            )
        }
    }

    /**
     * The spelled-out unit must not become a way to read *any* unit as grams. An ounce is not a
     * basis this app has, and inventing one would be the guess every other rule here exists to stop.
     */
    @Test
    fun `a unit the app cannot represent still resolves nothing`() {
        listOf(listOf("per", "100", "oz"), listOf("per", "100", "ounces"), listOf("per", "100", "cal"))
            .forEach { header ->
                assertEquals(
                    "\"${header.joinToString(" ")}\" must not resolve a basis",
                    "NotFound",
                    outcome(standardTable("Koolhydraten", header)),
                )
            }
    }

    // ---- child nutrients ----------------------------------------------------------------------

    /**
     * The safety claim, stated as one property over every Dutch child term: **the child's figure is
     * never offered as the carbohydrate value**, on its own row or merged onto the total's.
     *
     * `35` is the sugars figure and `62` the total. Refusing entirely is a pass; offering 35 — even
     * as one of two choices — is not, because the user has no way to tell the two apart.
     */
    @Test
    fun `no Dutch child nutrient value is ever offered as the carbohydrate value`() {
        CHILD_TERMS.forEach { child ->
            val separate = table(
                header = listOf("per", "100", "g"),
                rows = listOf("Koolhydraten" to listOf("62"), child to listOf("35")),
            )
            assertEquals(
                "\"$child\" on its own row must leave the total winning",
                listOf("62"),
                offeredValues(separate),
            )

            val merged = mergedTotalAndChildRow(child)
            assertFalse(
                "\"$child\" merged onto the total row offered the child's value: ${outcome(merged)}",
                offeredValues(merged).contains("35"),
            )
        }
    }

    /**
     * The classification is asserted as well as the value, because the two can agree by accident.
     * Before this pass the unlisted terms typed `TOTAL_CARBOHYDRATE`, and the reason their value was
     * sometimes still refused was geometry, not vocabulary — a reading that would come back the
     * moment the label was laid out slightly differently.
     */
    @Test
    fun `every Dutch child term types as a carbohydrate child`() {
        CHILD_TERMS.forEach { child ->
            val row = LogicalRow(
                elements = listOf(
                    DutchLabelFixtures.element("Koolhydraten", 40, 200, 240, 230, line = 0),
                    DutchLabelFixtures.element(child, 260, 200, 500, 230, line = 0),
                ),
                box = OcrBox(40, 200, 500, 230),
                sourceLines = setOf(LineKey(0, 0)),
            )
            assertEquals(
                "\"$child\" merged with the total must type as a child row",
                NutritionRowKind.CARBOHYDRATE_CHILD,
                RowClassifier.classify(row),
            )
        }
    }

    // ---- serving columns ----------------------------------------------------------------------

    /**
     * A Dutch serving column must not disturb the per-100 reading. Every one of these was measured;
     * none is speculative coverage.
     */
    @Test
    fun `a Dutch serving column never displaces the per-100 value`() {
        val servingHeaders = listOf(
            "per portie", "per portie (30 g)", "per stuk", "per plak", "per plakje", "per sneetje",
            "per zakje", "per beker", "per glas", "per eetlepel", "gemiddelde portie",
        )
        servingHeaders.forEach { serving ->
            val document = twoColumnTable(
                first = listOf("per", "100", "g"),
                second = serving.split(' '),
                rows = listOf(
                    "Koolhydraten" to listOf("62", "18,6"),
                    "waarvan suikers" to listOf("35", "10,5"),
                ),
            )
            assertEquals(
                "\"$serving\" changed the per-100 reading: ${outcome(document)}",
                listOf("62"),
                offeredValues(document),
            )
        }
    }

    /**
     * The Dutch words for a countable item, so a `per plak` column can become a saved portion rather
     * than being silently ignored. These only ever create a `PER_SERVING` column, which by
     * construction can never supply the per-100 figure.
     */
    @Test
    fun `Dutch countable unit words are recognised`() {
        val expected = mapOf(
            "plak" to PortionUnitKind.SLICE,
            "plakje" to PortionUnitKind.SLICE,
            "plakjes" to PortionUnitKind.SLICE,
            "sneetje" to PortionUnitKind.SLICE,
            "snee" to PortionUnitKind.SLICE,
            "stuk" to PortionUnitKind.PIECE,
            "blokje" to PortionUnitKind.PIECE,
            "koekje" to PortionUnitKind.COOKIE,
            "wafel" to PortionUnitKind.BISCUIT,
            "reep" to PortionUnitKind.BAR,
            "bolletje" to PortionUnitKind.ROLL,
            "bol" to PortionUnitKind.ROLL,
            "zakje" to PortionUnitKind.SACHET,
            "schepje" to PortionUnitKind.SCOOP,
            "portie" to PortionUnitKind.SERVING,
            "beker" to PortionUnitKind.SERVING,
            "glas" to PortionUnitKind.SERVING,
            "eetlepel" to PortionUnitKind.SERVING,
        )
        expected.forEach { (word, kind) ->
            assertEquals("\"$word\"", kind, ServingSizeParser.kindForWord(word))
        }
    }

    /** A serving weight printed with the unit spelled out, as Dutch packaging does. */
    @Test
    fun `a Dutch serving weight spelled out in grams is read`() {
        val descriptor = ServingSizeParser.parseDescriptor("1 plak (20 gram)")
        assertNotNull("\"1 plak (20 gram)\" should parse", descriptor)
        assertEquals(PortionUnitKind.SLICE, descriptor!!.kind)
        val weight = requireNotNull(descriptor.weightOrVolume)
        assertEquals(0, java.math.BigDecimal("20").compareTo(weight.amount))
        assertEquals(NutritionBasis.PER_100_G, weight.basis)
    }
}

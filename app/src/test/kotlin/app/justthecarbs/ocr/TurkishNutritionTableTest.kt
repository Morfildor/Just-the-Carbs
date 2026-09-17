package app.justthecarbs.ocr

import app.justthecarbs.ocr.DutchLabelFixtures.element
import app.justthecarbs.ocr.DutchLabelFixtures.offeredValues
import app.justthecarbs.ocr.DutchLabelFixtures.outcome
import app.justthecarbs.ocr.DutchLabelFixtures.table
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Turkish nutrition tables — the forms `TurkishLabelDiagnosticTest` showed failing on 2026-09-17,
 * and the ones that already worked, so the fix cannot cost them.
 */
class TurkishNutritionTableTest {

    private val rows = listOf(
        "Enerji" to listOf("1650"),
        "Yağ" to listOf("12,0"),
        "Karbonhidrat" to listOf("62,5"),
        "Şekerler" to listOf("24,0"),
        "Protein" to listOf("6,2"),
        "Tuz" to listOf("0,4"),
    )

    private val drink = listOf(
        "Enerji" to listOf("78"),
        "Karbonhidrat" to listOf("4,5"),
        "Şekerler" to listOf("4,5"),
    )

    /**
     * Turkish attaches a case suffix to an abbreviation with an apostrophe: `100 g'da` is "in 100 g".
     * It is the usual Turkish header, and every one of these read nothing.
     */
    @Test
    fun `a gram header carrying a Turkish case suffix is read`() {
        listOf(
            listOf("100", "g'da"),
            listOf("100", "gr'da"),
            listOf("100", "G'DA"),
            listOf("100", "g’da"),
            listOf("100", "g'daki"),
            listOf("100", "gramda"),
            listOf("Besin", "Değerleri", "100", "g'da"),
        ).forEach { header ->
            assertEquals(header.joinToString(" "), "Confident 62.5 PER_100_G", outcome(table(header, rows)))
        }
    }

    @Test
    fun `a millilitre header carrying a Turkish case suffix is read`() {
        listOf(listOf("100", "ml'de"), listOf("100", "ML'DE")).forEach { header ->
            assertEquals(header.joinToString(" "), "Confident 4.5 PER_100_ML", outcome(table(header, drink)))
        }
    }

    @Test
    fun `the forms that already worked still read`() {
        listOf(
            listOf("100", "g"),
            listOf("100g'da"),
            listOf("100", "g", "için"),
            listOf("BESİN", "DEĞERLERİ", "100", "G"),
        ).forEach { header ->
            assertEquals(header.joinToString(" "), "Confident 62.5 PER_100_G", outcome(table(header, rows)))
        }
        listOf("KARBONHİDRAT", "KARBONHIDRAT", "Karbonhidrat (g)", "Toplam karbonhidrat").forEach { spelling ->
            assertEquals(spelling, "Confident 62.5 PER_100_G", outcome(withCarbohydrateLabel(spelling)))
        }
    }

    @Test
    fun `the plural Karbonhidratlar names the total`() {
        assertEquals("Confident 62.5 PER_100_G", outcome(withCarbohydrateLabel("Karbonhidratlar")))
    }

    /** A suffix only ever comes off a basis unit: nothing else is turned into one. */
    @Test
    fun `a suffix after anything but a basis unit reads nothing`() {
        assertEquals("NotFound", outcome(table(listOf("100", "kcal'de"), rows)))
        assertEquals("NotFound", outcome(table(listOf("100", "adet'te"), rows)))
    }

    /**
     * Two printed rows merged into one. Before these words were listed each came back
     * `Ambiguous [62.0, 35.0]` — the total and the child figure offered side by side with nothing to
     * say which is which. The documented outcome for a row the parser cannot separate is `NotFound`.
     */
    @Test
    fun `a merged row naming a Turkish child nutrient never offers its figure`() {
        listOf(
            "Şekerler", "Şekerleri", "bunun şekerleri", "Diyet lifi", "Lifler",
            "Sakaroz", "Laktoz", "Glikoz", "Fruktoz", "Maltoz", "Dekstroz",
            "Maltodekstrin", "Glikoz şurubu",
        ).forEach { child ->
            assertEquals(child, emptyList<String>(), offeredValues(mergedRow(child)))
        }
    }

    private fun withCarbohydrateLabel(label: String) =
        table(listOf("100", "g"), rows.map { if (it.first == "Karbonhidrat") label to it.second else it })

    /** [DutchLabelFixtures.mergedTotalAndChildRow], with Turkish words. */
    private fun mergedRow(child: String): OcrDocument {
        val elements = mutableListOf<OcrElement>()
        elements += element("100", 378, 100, 444, 130, line = 0)
        elements += element("g", 456, 100, 478, 130, line = 0)

        elements += element("Karbonhidrat", 40, 200, 208, 240, line = 1)
        elements += element("62", 380, 200, 424, 240, line = 1, block = 4)

        var x = 60
        child.split(' ').forEach { word ->
            elements += element(word, x, 215, x + 14 * word.length, 255, line = 1)
            x += 14 * word.length + 10
        }
        elements += element("35", 380, 215, 424, 255, line = 1, block = 4)

        elements += element("Protein", 40, 320, 138, 360, line = 2)
        elements += element("6,2", 380, 320, 446, 360, line = 2, block = 4)
        return OcrDocument(width = 1200, height = 500, elements = elements)
    }
}

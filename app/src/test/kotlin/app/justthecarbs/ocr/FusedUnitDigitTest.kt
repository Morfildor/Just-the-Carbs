package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * A value cell whose **fused unit glyph** was recognised as a digit must not be read as a number.
 *
 * ## The failure, measured on the thirteenth session
 *
 * `docs/Scan evidence 04-09 1st test/20260904-081307-240` is a Lidl drink carton printing
 * `Koolhydraten 6,2 g` in large, flat, high-contrast type. The whole label typesets its values with
 * the unit **fused to the number**, and ML Kit read that column as:
 *
 * ```
 * Vetten          '0g'      [1139,1758,1219,1834]
 * verz. vetzuren  '0g'      [1144,1841,1219,1914]
 * Koolhydraten    '6,20'    [1080,1936,1226,2013]   <- printed `6,2 g`; the `g` became a `0`
 * waarvan suikers '6,09'    [1087,2020,1242,2107]   <- printed `6,0 g`; the `g` became a `9`
 * Vezels          '0g'      [1143,2127,1225,2208]
 * Zout            '0,01g'   [1077,2327,1220,2419]
 * ```
 *
 * `6,20` parses as a perfectly ordinary `6.20`, so every content rule passed it —
 * [CarbUnitAccompaniment] then declined it for stating no unit, the label read `NotFound`, and
 * recovery suppressed the same number again. **The user saw a large, clear `6,2 g` and the app
 * offered nothing at all**, twice over.
 *
 * ## Why this is a distinct hazard from the one the accompaniment rule was built for
 *
 * That rule exists because a *bare* number might be a corrupted `0,5 g`. Here the number is not
 * bare and not merely unaccompanied: the corruption is **inside** it. `6,20` and `6.2` are
 * different quantities, so had the column resolved slightly differently this token could have been
 * accepted as `6.20 g/100 ml` — a wrong value, not a missing one. Declining it is right; declining
 * it *silently, on a label whose every other value cell carries its unit* is what costs the reading.
 *
 * ## The observable signal, and why it is not a magnitude heuristic
 *
 * The label states its own convention: five other value cells on this document carry a fused unit
 * (`0g`, `0g`, `0g`, `0g`, `0,01g`). Against that convention a numeric token in the same column
 * with **no unit at all** is anomalous — which is exactly what [UnitAccompanimentPolicy] already
 * establishes, and this test asserts the consequence rather than inventing a new rule.
 *
 * The green drink proves the same point within one capture: on `20260904-080926-938` the sugars row
 * read `0.5g` and the carbohydrate row read `0.59`, same column, same printed form. The difference
 * is recognition damage, not typesetting.
 *
 * ## What must NOT happen
 *
 * `6,20` must never become `6.2`, and `0.59` must never become `0.5`. There is no repair path here
 * and none may be added — the trailing glyph is the one OCR is least certain about, and a wrong
 * repair is invisible in a way a refusal is not. These tests assert only that the value is
 * **refused**, and that the user is routed somewhere they can supply it.
 */
class FusedUnitDigitTest {

    private val document = ThirteenthSessionFixtures.lidlDrinkSixPointTwoUnitLost()

    @Test
    fun `the damaged token is never read as a carbohydrate value`() {
        val report = NutritionTableParser.parseWithDiagnostics(document)
        val confident = report.reading as? LabelReading.Confident
        // Whatever else happens, `6.20` must not be presented as the carbohydrate figure. That is
        // the safety half and it is unconditional.
        assertTrue(
            "6.20 must never be accepted as the value; got ${confident?.candidate?.value}",
            confident?.candidate?.value?.compareTo(BigDecimal("6.20")) != 0,
        )
    }

    @Test
    fun `no recovery candidate offers the damaged token either`() {
        val offers = RecoveryCandidates.of(document).map { it.reading.amount }
        assertFalse(
            "recovery must not offer 6.20; offered $offers",
            offers.any { it.compareTo(BigDecimal("6.20")) == 0 },
        )
    }

    @Test
    fun `the label demonstrates that it prints units on its value cells`() {
        // The precondition the whole finding rests on. Without it the accompaniment rule would not
        // be asked at all and this fixture would be measuring something else.
        val rows = LogicalRowBuilder.build(document)
        assertTrue(
            "this label must demonstrate the unit convention, or the fixture is wrong",
            UnitAccompanimentPolicy.mayDeclineBareValues(document, rows),
        )
    }

    @Test
    fun `the carbohydrate row and its basis column are both established`() {
        // The row was found and the basis resolved — only the digits are in doubt. That is what
        // makes focused entry the honest next screen, and it is asserted so a future change that
        // loses the basis is visible here rather than only in a device session.
        val rows = LogicalRowBuilder.build(document)
        val total = rows.filter { RowClassifier.classify(it) == NutritionRowKind.TOTAL_CARBOHYDRATE }
        assertEquals(1, total.size)

        val target = FocusedAmountEntry.of(document)
        assertEquals(
            "the label states per-100-ml and focused entry must preserve it",
            app.justthecarbs.domain.NutritionBasis.PER_100_ML,
            target?.basis,
        )
    }

    @Test
    fun `a genuine unit-bearing cell on the same label still reads`() {
        // The non-vacuity control. `0,01g` is the salt row's cell on this very document: fused unit,
        // undamaged. If the rule under test also refused this, it would be refusing the label's
        // typesetting rather than the damage.
        val rows = LogicalRowBuilder.build(document)
        val salt = rows.first { it.elements.any { e -> e.text.trim() == "0,01g" } }
        val cell = salt.elements.first { it.text.trim() == "0,01g" }
        assertTrue(
            "an undamaged fused-unit cell must still count as accompanied",
            CarbUnitAccompaniment.isAccompanied(cell, salt.elements),
        )
    }

    @Test
    fun `the green drink's undamaged sugars cell reads while its damaged carb cell does not`() {
        // Same hazard, different label, and the two cells sit in one capture — so this cannot be
        // explained by lighting, framing or the package.
        val green = ThirteenthSessionFixtures.greenDrinkUnitLostZeroFiveNine()
        val rows = LogicalRowBuilder.build(green)
        val sugars = rows.first { r -> r.elements.any { it.text.trim() == "0.5g" } }
        val sugarsCell = sugars.elements.first { it.text.trim() == "0.5g" }
        assertTrue(CarbUnitAccompaniment.isAccompanied(sugarsCell, sugars.elements))

        val carb = rows.first { r -> r.elements.any { it.text.trim() == "0.59" } }
        val carbCell = carb.elements.first { it.text.trim() == "0.59" }
        assertFalse(CarbUnitAccompaniment.isAccompanied(carbCell, carb.elements))

        val confident = NutritionTableParser.parseWithDiagnostics(green).reading as? LabelReading.Confident
        assertNull("0.59 must not be read as the carbohydrate value", confident)
    }
}

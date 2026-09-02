package app.justthecarbs.ocr

import app.justthecarbs.domain.CarbBasis
import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The seventh phone session: the truffle label is **safe** and **unrecoverable**.
 *
 * ## What the device proved
 *
 * The sixth session's scale-safety work holds. Neither capture displays or offers `89`, the genuine
 * high-carbohydrate label still advances at `41 g / 100 ml`, and the cracker, drink and linear sauce
 * are all unchanged. Nothing below may weaken any of that.
 *
 * What the recording then showed is a dead end. The user tapped the carbohydrate value and got
 * *"This looks like sugars or fibre. Tap the total carbohydrate row instead."*; returning to
 * recovery left *Type it in* disabled. Measured on both captures, every element on the row answered
 * the tap identically — including a tap on the word `Kohlenhydrate` itself.
 *
 * ## The proven cause, in three layers
 *
 * On this package ML Kit puts the carbohydrate clause **and** the `waarvan suikers` clause that
 * follows it on one reconstructed row. Three separate places then read that whole row as one unit:
 *
 * 1. [NutrientRowSegments] finds a valid total clause but discards the entire row, because its
 *    merged-row guard requires *every* segment to carry a value and the trailing language-variant
 *    clause (`sucres/tavon Žucker`, no number of its own) does not.
 * 2. [RecoveryCandidates.isChildRowAt] and `candidatesOn` classify the whole row, so a tap inside
 *    the total clause is answered with a fact about the sugars clause.
 * 3. Focused entry is gated on a tap that produced nothing *and was not a child row*, and
 *    [FocusedAmountEntry] itself needs a `TOTAL_CARBOHYDRATE` row — so neither could ever fire.
 *
 * The fix is confined to the **tap** path, which knows something the automatic path does not: the
 * user pointed at a particular clause. Automatic classification is deliberately unchanged — these
 * rows stay `CARBOHYDRATE_CHILD`, `8`/`13` stay non-automatic, and `89` stays suppressed.
 */
class SeventhSessionRegressionTest {

    private val separatorless = SeventhSessionFixtures.truffleSeparatorlessPair()
    private val damagedUnit = SeventhSessionFixtures.truffleDamagedUnitGlyph()

    /** The `8,9` element on `141703`, and the damaged `a,` unit glyph immediately after it. */
    private val valueY = 1853
    private val valueX = 872
    private val damagedUnitX = 913

    /** A point inside the later sugars clause on the same row of `141703`. */
    private val sugarsX = 1335

    // ---- Preconditions: the fixtures really do reproduce the hazard ---------------------------

    @Test
    fun `precondition - both captures merge the carbohydrate and sugars clauses onto one row`() {
        listOf("141642" to separatorless, "141703" to damagedUnit).forEach { (name, document) ->
            val rows = LogicalRowBuilder.build(document)
            val row = rows.single { row ->
                val n = NutritionTerminology.normalize(row.text)
                n.contains("hydrat") && NutritionTerminology.exclusionTerms.any {
                    NutritionTerminology.containsTerm(n, it)
                }
            }
            assertEquals(
                "$name: the merged row must classify as a child, or there is no hazard to fix",
                NutritionRowKind.CARBOHYDRATE_CHILD,
                RowClassifier.classify(row),
            )
        }
    }

    @Test
    fun `precondition - the label establishes per 100 ml on both captures`() {
        assertEquals(NutritionBasis.PER_100_ML, StatedBasis.of(separatorless))
        assertEquals(NutritionBasis.PER_100_ML, StatedBasis.of(damagedUnit))
    }

    // ---- 1. Total-clause taps are distinguished from child-clause taps ------------------------

    @Test
    fun `tapping the value in the total clause does not report a child row`() {
        assertFalse(
            "a tap on '8,9' is inside the total-carbohydrate clause",
            RecoveryCandidates.isChildRowAt(damagedUnit, valueY, valueX),
        )
    }

    @Test
    fun `tapping the damaged unit glyph beside the value does not report a child row`() {
        // The printed `g` came back as `a,` at [900,1822,926,1884]. The finger lands on the value
        // and its unit as one target; both must resolve to the same clause.
        assertFalse(
            "a tap on the damaged unit belongs to the value it follows",
            RecoveryCandidates.isChildRowAt(damagedUnit, valueY, damagedUnitX),
        )
    }

    @Test
    fun `tapping the second value inside the total clause does not report a child row`() {
        // `13g(<1%;Manvan` on 141642 sits inside the total clause on that capture's geometry.
        assertFalse(
            "a tap on the serving-column value is still inside the carbohydrate declaration",
            RecoveryCandidates.isChildRowAt(separatorless, 1973, 966),
        )
    }

    @Test
    fun `tapping inside the sugars clause is still reported as a child row`() {
        // The other half of the rule, and the one that must not regress: a genuine tap on the
        // sugars clause is refused exactly as before.
        assertTrue(
            "a tap on 'suikers/dant' is a child-clause tap",
            RecoveryCandidates.isChildRowAt(damagedUnit, valueY, sugarsX),
        )
    }

    @Test
    fun `a child-clause tap offers no candidates`() {
        assertTrue(
            "the sugars clause can never supply the total: " +
                RecoveryCandidates.onRowAt(damagedUnit, valueY, sugarsX).joinToString { it.label },
            RecoveryCandidates.onRowAt(damagedUnit, valueY, sugarsX).isEmpty(),
        )
    }

    // ---- 2. Focused entry is reachable, under the established basis ---------------------------

    @Test
    fun `an accepted total-clause tap with unusable numbers opens focused entry under per 100 ml`() {
        // The values are genuinely unusable — `8,9` states no unit on a label that prints them, and
        // `8`/`13` lost their separators. So the honest outcome is not a proposal but a request for
        // the number, under the basis the label itself stated.
        listOf("141642" to separatorless, "141703" to damagedUnit).forEach { (name, document) ->
            val target = FocusedAmountEntry.of(document)
            assertNotNull("$name: focused entry must be reachable", target)
            assertEquals(
                "$name: the basis is the one the label stated, never a picker",
                NutritionBasis.PER_100_ML,
                target!!.basis,
            )
        }
    }

    @Test
    fun `the focused-entry row is the carbohydrate row, not the sugars row`() {
        val target = FocusedAmountEntry.of(damagedUnit)!!
        val normalized = NutritionTerminology.normalize(target.rowText)
        assertTrue(
            "the echoed row must name carbohydrate: '${target.rowText.take(80)}'",
            normalized.contains("hydrat"),
        )
    }

    // ---- 3. Nothing is invented, and nothing suppressed comes back ----------------------------

    @Test
    fun `no 89 proposal appears on either capture`() {
        listOf("141642" to separatorless, "141703" to damagedUnit).forEach { (name, document) ->
            val offered = RecoveryCandidates.of(document).map { it.reading.amount }
            assertTrue(
                "$name: 89 must never be offered, got $offered",
                offered.none { it.compareTo(BigDecimal("89")) == 0 },
            )
            // And not through a tap either.
            val tapped = RecoveryCandidates.onRowAt(document, valueY, valueX).map { it.reading.amount } +
                RecoveryCandidates.onRowAt(document, 1973, 790).map { it.reading.amount }
            assertTrue(
                "$name: 89 must never be reachable by tapping, got $tapped",
                tapped.none { it.compareTo(BigDecimal("89")) == 0 },
            )
        }
    }

    @Test
    fun `8 point 9 is never invented from the separatorless capture`() {
        // `141642` recognised `8` and `13` with no separator anywhere. The printed answer is 8,9 —
        // and the app must not produce it, because nothing in the recognised text says so.
        // Repairing a decimal here would be the same class of error as dividing 89 by ten.
        val reachable = RecoveryCandidates.of(separatorless).map { it.reading.amount } +
            separatorless.elements.flatMap { element ->
                RecoveryCandidates.onRowAt(
                    separatorless,
                    element.box.centerY.toInt(),
                    element.box.centerX.toInt(),
                ).map { it.reading.amount }
            }
        assertTrue(
            "8.9 must never be manufactured, got $reachable",
            reachable.none { it.compareTo(BigDecimal("8.9")) == 0 },
        )
    }

    @Test
    fun `the separatorless pair is still withheld as scale-ambiguous`() {
        // The sixth session's guarantee, re-pinned here because the tap path now reaches this row.
        // Tapping does not make a value whose decimal scale is unestablished safe to offer.
        val offered = RecoveryCandidates.onRowAt(separatorless, 1973, 790).map { it.reading.amount }
        assertTrue(
            "8 must not be offered as a carbohydrate reading, got $offered",
            offered.none { it.compareTo(BigDecimal("8")) == 0 },
        )
    }

    @Test
    fun `the unit-less value on the damaged-glyph capture is still withheld`() {
        // `8,9` carries its separator but states no unit on a label that prints them, and the
        // automatic path declined it for exactly that reason. The tap must not re-offer it.
        val offered = RecoveryCandidates.onRowAt(damagedUnit, valueY, valueX).map { it.reading.amount }
        assertTrue(
            "8.9 must not be offered as a clean reading, got $offered",
            offered.none { it.compareTo(BigDecimal("8.9")) == 0 },
        )
    }

    // ---- 4. The automatic path is untouched ---------------------------------------------------

    @Test
    fun `the merged rows still classify as child rows in the automatic path`() {
        // The load-bearing non-regression: the fix is in the tap path only. If these rows became
        // TOTAL_CARBOHYDRATE, `8` and `13` would become automatic candidates on a label whose
        // decimal separators did not survive.
        listOf(separatorless, damagedUnit).forEach { document ->
            val rows = LogicalRowBuilder.build(document)
            val row = rows.single { row ->
                val n = NutritionTerminology.normalize(row.text)
                n.contains("hydrat") && NutritionTerminology.exclusionTerms.any {
                    NutritionTerminology.containsTerm(n, it)
                }
            }
            assertEquals(
                NutritionRowKind.CARBOHYDRATE_CHILD,
                RowClassifier.classify(row),
            )
        }
    }

    @Test
    fun `neither capture produces a confident automatic reading`() {
        assertTrue(
            "141703 must stay unread automatically",
            NutritionTableInterpreter.interpret(damagedUnit).reading is LabelReading.NotFound,
        )
        assertFalse(
            "141642 must not become confident",
            NutritionTableInterpreter.interpret(separatorless).reading is LabelReading.Confident,
        )
    }

    @Test
    fun `no candidate on either capture states a serving basis`() {
        // The fifth session's rule, re-checked on this session's geometry: a per-100 value may never
        // wear a neighbouring column's serving basis.
        listOf(separatorless, damagedUnit).forEach { document ->
            RecoveryCandidates.of(document).forEach { candidate ->
                assertFalse(
                    "no fabricated serving basis: ${candidate.label}",
                    candidate.reading.basis is CarbBasis.PerUnknownServing,
                )
            }
        }
    }

    @Test
    fun `a tap on an unrelated row still offers nothing`() {
        // The ingredients rows carry percentages and numbers. Making the tap clause-aware must not
        // make every row tappable.
        val rows = LogicalRowBuilder.build(damagedUnit)
        val ingredients = rows.first {
            NutritionTerminology.normalize(it.text).contains("ingredienten")
        }
        assertTrue(
            "an ingredients row contributes nothing",
            RecoveryCandidates.onRowAt(
                damagedUnit,
                ingredients.box.centerY.toInt(),
                ingredients.box.centerX.toInt(),
            ).isEmpty(),
        )
    }

    @Test
    fun `stated basis is unchanged by the tap-path fix`() {
        assertEquals(NutritionBasis.PER_100_ML, StatedBasis.of(damagedUnit))
        assertNull(
            "no serving declaration is invented on a label that prints none",
            ServingDeclaration.of(LogicalRowBuilder.build(damagedUnit)),
        )
    }
}

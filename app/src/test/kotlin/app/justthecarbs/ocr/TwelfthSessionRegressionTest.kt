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
 * The twelfth session's defects, each pinned on the device's own geometry so none can return
 * silently.
 *
 * Every fixture here is the whole document the phone recognised — see [TwelfthSessionFixtures] — so
 * the contamination these cases are about (an ingredient panel merged into a table row, a marketing
 * paragraph naming sugar) is present rather than trimmed away.
 */
class TwelfthSessionRegressionTest {

    // ------------------------------------------------------------------------------------------
    // P0 — the pickle's serving figure may never wear the per-100 basis
    // ------------------------------------------------------------------------------------------

    /**
     * `20260903-212700-478`. The pickle prints `5,4 g / 100 g` and `1,6 g / 30 g part`.
     *
     * ML Kit read the per-100 cell's unit as a digit (`5,4` `9`), so [CarbUnitAccompaniment]
     * declined `5,4` as a value — correctly, and unchanged. With the per-100 column's own cell out
     * of the running, `1,6` bound to that column from 243 px away and the app reported
     * **`Confident 1.6/PER_100_G`**: the 30 g serving figure at a third of the printed per-100
     * value, with no confirmation step of its own beyond the ordinary one.
     */
    @Test
    fun `the pickle never reports the serving figure as a per-100 reading`() {
        val reading = NutritionTableParser.parse(TwelfthSessionFixtures.pickle())
        if (reading is LabelReading.Confident) {
            assertFalse(
                "1.6 is the 30 g serving figure and must never carry a per-100 basis, got $reading",
                reading.candidate.value.compareTo(BigDecimal("1.6")) == 0 &&
                    reading.candidate.basis == NutritionBasis.PER_100_G,
            )
        }
        assertEquals(
            "with its own per-100 cell unusable the table states no per-100 answer",
            LabelReading.NotFound,
            reading,
        )
    }

    /**
     * And the same figure is not offered by the recovery screen under a per-100 basis either.
     *
     * The two surfaces ask [ColumnOwnership] the same question, so this cannot pass while the
     * automatic path refuses — but it is asserted rather than inherited, because the recovery screen
     * being more permissive than the parser it backs up is a defect this repo has already shipped
     * once (`20260902-103936-423`, `72 g / serving`).
     */
    @Test
    fun `recovery offers the pickle's serving figure only under its own printed serving size`() {
        val offers = RecoveryCandidates.of(TwelfthSessionFixtures.pickle())
        offers.forEach { candidate ->
            assertFalse(
                "offered '${candidate.label}' — a per-100 basis this cell never had",
                candidate.reading.amount.compareTo(BigDecimal("1.6")) == 0 &&
                    candidate.reading.basis is CarbBasis.PerHundred,
            )
        }
        val serving = offers.single()
        assertEquals(0, serving.reading.amount.compareTo(BigDecimal("1.6")))
        assertEquals(
            "the basis is the 30 g the label printed above that column",
            CarbBasis.PerQuantity(BigDecimal("30"), NutritionBasis.PER_100_G),
            serving.reading.basis,
        )
        // 1.6 per 30 g is 5.333… per 100 g, which the screen shows as 5.3; the package prints 5,4.
        // Asserted at the precision the user is shown rather than on the raw quotient, and never
        // rounded to the printed figure — the app converts, it does not reconcile.
        assertEquals(
            BigDecimal("5.3"),
            serving.reading.normalizedToPerHundred()!!.amount
                .setScale(1, java.math.RoundingMode.HALF_UP),
        )
    }

    /**
     * The structural reason, asserted directly so it cannot be satisfied by luck.
     *
     * `5,4` sits 17 px from the per-100 column's centre and `1,6` sits 243 px from it, against a
     * strict tolerance of 202 px. The near cell is refused **as a value** and still owns the column.
     */
    @Test
    fun `a declined cell still owns the column it was printed in`() {
        val document = TwelfthSessionFixtures.pickle()
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)
        val perHundred = columns.single { it.kind == NutritionColumnKind.PER_100_G && it.centerX > 1000 }
        val totalRow = rows.single { RowClassifier.classify(it) == NutritionRowKind.TOTAL_CARBOHYDRATE }
        val competing = ColumnOwnership.competingCells(totalRow)

        val near = competing.single { it.text == "5,4" }
        val far = competing.single { it.text == "1,6" }
        assertTrue(
            "precondition: the declined cell really is the near one",
            Math.abs(perHundred.centerX - near.box.centerX) <
                Math.abs(perHundred.centerX - far.box.centerX),
        )
        assertTrue(ColumnOwnership.claims(perHundred, near.box, competing, document.width))
        assertFalse(
            "a cell 243 px away may not claim a column another cell sits 17 px inside",
            ColumnOwnership.claims(perHundred, far.box, competing, document.width),
        )
    }

    /**
     * The negative control for the whole rule, stated as a property rather than a fixture.
     *
     * Two cells both printed *inside* the column are a genuine ambiguity and must both keep their
     * claim. Resolving them by nearness would be a proximity score, which is the mechanism the
     * geometry-first rewrite removed.
     */
    @Test
    fun `two cells inside the same column both keep their claim`() {
        val document = OcrDocument(
            width = 700,
            height = 400,
            elements = listOf(
                OcrElement("per", OcrBox(300, 100, 340, 130), 0, 0),
                OcrElement("100", OcrBox(345, 100, 385, 130), 0, 0),
                OcrElement("g", OcrBox(390, 100, 405, 130), 0, 0),
                OcrElement("Carbohydrate", OcrBox(40, 200, 240, 230), 0, 1),
                OcrElement("45", OcrBox(330, 200, 370, 230), 0, 1),
                OcrElement("51", OcrBox(380, 200, 420, 230), 0, 1),
            ),
        )
        val rows = LogicalRowBuilder.build(document)
        val column = ColumnClassifier.classify(rows, document.width).single()
        val row = rows.single { RowClassifier.classify(it) == NutritionRowKind.TOTAL_CARBOHYDRATE }
        val competing = ColumnOwnership.competingCells(row)
        competing.forEach {
            assertTrue(
                "'${it.text}' is printed inside the column and must keep its claim",
                ColumnOwnership.claims(column, it.box, competing, document.width),
            )
        }
        assertTrue(
            NutritionTableParser.parse(document) is LabelReading.Ambiguous,
        )
    }

    // ------------------------------------------------------------------------------------------
    // Agility — the serving column whose size is printed on the line below
    // ------------------------------------------------------------------------------------------

    /**
     * `20260903-212442-653`. The Lidl cracker prints `72 g / 100 g` and `18 g / 4 crackers (25 g)`.
     *
     * OCR recovered only the serving cell. Its column's header is split across two reconstructed
     * rows — `4 crackers` on the header row, `(25 g)` on the row beneath — so no serving column
     * resolved, `18` was claimed by nothing, and the recovery screen suppressed it as stating no
     * basis. A correct figure, on the correct row, unreachable by every route.
     */
    @Test
    fun `the cracker's split serving header resolves its column`() {
        val document = TwelfthSessionFixtures.crackersServingColumnOnly()
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)
        val serving = columns.single { it.kind == NutritionColumnKind.PER_SERVING }
        assertEquals("25 g", serving.headerText)
        assertTrue(
            "the column stands over the serving cells at x~1520, got ${serving.centerX}",
            serving.centerX > 1400 && serving.centerX < 1650,
        )
    }

    /** And the printed 18 g is then offerable, carrying the size the label printed above it. */
    @Test
    fun `the cracker offers its serving figure and it normalizes to the printed per-100 value`() {
        val offer = RecoveryCandidates.of(TwelfthSessionFixtures.crackersServingColumnOnly()).single()
        assertEquals(0, offer.reading.amount.compareTo(BigDecimal("18")))
        assertEquals(
            CarbBasis.PerQuantity(BigDecimal("25"), NutritionBasis.PER_100_G),
            offer.reading.basis,
        )
        assertEquals(
            "18 g per 25 g is the 72 g / 100 g the package prints",
            0,
            offer.reading.normalizedToPerHundred()!!.amount.compareTo(BigDecimal("72")),
        )
    }

    /**
     * The guard that separates the table's serving header from an identical-looking token elsewhere.
     *
     * The pickle prints **two** `(30 g)` tokens on the same reconstructed row: one at x≈339, part of
     * the drained-weight sentence `uitgelekt gewicht 360 g / 2 parten (30g)`, and one at x≈1337
     * heading the table's serving column. Only the second has a column of values beneath it, and
     * only the second becomes a column.
     */
    @Test
    fun `a printed size with no values beneath it heads no column`() {
        val document = TwelfthSessionFixtures.pickle()
        val rows = LogicalRowBuilder.build(document)
        val serving = ColumnClassifier.classify(rows, document.width)
            .filter { it.kind == NutritionColumnKind.PER_SERVING }
        assertEquals("exactly one serving column, not one per printed '(30 g)'", 1, serving.size)
        assertTrue(
            "it is the one over the table's values, got ${serving.single().centerX}",
            serving.single().centerX > 1200,
        )
    }

    // ------------------------------------------------------------------------------------------
    // Manual selection — the false "looks like sugars"
    // ------------------------------------------------------------------------------------------

    /**
     * `20260903-212828-161`. The tortilla's marketing paragraph reconstructs as one row:
     *
     * ```
     * IStorbritannien. edetortila med fuldkom. Ingredienser: Contains stablser naturally (EA15),
     * occurring Room sugars. termperature.dced Packaged ina protective d package
     * ```
     *
     * Eighteen words, no numbers, and the single word `sugars.` typed the whole thing
     * `CARBOHYDRATE_CHILD`. Every tap anywhere in that band — a wide region of the photograph — was
     * answered *"This looks like sugars or fibre. Tap the total carbohydrate row instead."*
     */
    @Test
    fun `a prose paragraph naming sugar is not reported as a sugars row`() {
        val document = TwelfthSessionFixtures.balticTortilla()
        val row = LogicalRowBuilder.build(document).single { it.text.startsWith("IStorbritannien.") }
        assertEquals(
            "precondition: the classifier really does type this prose as a child row",
            NutritionRowKind.CARBOHYDRATE_CHILD,
            RowClassifier.classify(row),
        )
        assertEquals("precondition: it states no quantity", 0, ColumnOwnership.competingCells(row).size)

        row.elements.forEach { element ->
            assertFalse(
                "a tap on '${element.text}' was answered as a sugars row",
                RecoveryCandidates.isChildRowAt(
                    document,
                    element.box.centerY.toInt(),
                    element.box.centerX.toInt(),
                ),
            )
        }
    }

    /**
     * `20260903-212700-478`. The pickle's fat line reconstructs together with a slice of the
     * ingredient list printed beside it:
     *
     * ```
     * aDin, suiker, zout   vetten,   0,2 g   0,06 g
     *        ^x=213         ^x=602    ^1075   ^1313
     * ```
     *
     * A tap on the fat figures, 800 px from the word `suiker`, is not a sugars tap.
     */
    @Test
    fun `a tap in another nutrient's clause is not reported as a sugars tap`() {
        val document = TwelfthSessionFixtures.pickle()
        val row = LogicalRowBuilder.build(document).single { it.text.startsWith("aDin,") }
        assertEquals(
            "precondition: the ingredient word really does type this row as a child row",
            NutritionRowKind.CARBOHYDRATE_CHILD,
            RowClassifier.classify(row),
        )
        val y = row.box.centerY.toInt()

        listOf("0,2", "0,06", "vetten,").forEach { text ->
            val element = row.elements.first { it.text == text }
            assertFalse(
                "a tap on the fat clause's '$text' was answered as a sugars tap",
                RecoveryCandidates.isChildRowAt(document, element.box.centerY.toInt(), element.box.centerX.toInt()),
            )
        }
        // And the ingredient list's own sugar word still is one.
        val sugar = row.elements.first { it.text == "suiker," }
        assertTrue(
            RecoveryCandidates.isChildRowAt(document, sugar.box.centerY.toInt(), sugar.box.centerX.toInt()),
        )
        assertTrue(
            "nothing on a child row is ever offered, whatever the message says",
            RecoveryCandidates.onRowAt(document, y, 1075).isEmpty(),
        )
    }

    /**
     * The correction that must survive: a real sugars row still refuses, everywhere on it.
     *
     * `- suikers 2,3 0,6g` on the cracker. Both the printed name and both printed values.
     */
    @Test
    fun `a real sugars row is still refused and still says so`() {
        val document = TwelfthSessionFixtures.crackersServingColumnOnly()
        val row = LogicalRowBuilder.build(document).single { it.text.contains("suikers") }
        row.elements.filter { it.text != "-" }.forEach { element ->
            val x = element.box.centerX.toInt()
            val y = element.box.centerY.toInt()
            assertTrue(
                "a tap on the sugars row's '${element.text}' must be reported as one",
                RecoveryCandidates.isChildRowAt(document, y, x),
            )
            assertTrue(
                "and must offer nothing",
                RecoveryCandidates.onRowAt(document, y, x).isEmpty(),
            )
        }
    }

    /**
     * Carbohydrate printed above sugars: tapping the carbohydrate value resolves the total row.
     *
     * This is the shape the brief names first, on the device's own geometry rather than a
     * construction: `koolhydraten, waarvan 18 g` with `- suikers 2,3 0,6g` printed directly beneath
     * it, their reconstructed boxes overlapping by 9 px.
     */
    @Test
    fun `tapping the carbohydrate value resolves the total row and offers it`() {
        val document = TwelfthSessionFixtures.crackersServingColumnOnly()
        val row = LogicalRowBuilder.build(document).single { it.text.startsWith("koolhydraten,") }
        row.elements.forEach { element ->
            val x = element.box.centerX.toInt()
            val y = element.box.centerY.toInt()
            assertFalse(
                "a tap on the carbohydrate row's '${element.text}' was called sugars",
                RecoveryCandidates.isChildRowAt(document, y, x),
            )
            assertEquals(
                "and resolves the carbohydrate row itself",
                row.text,
                RecoveryCandidates.rowTextAt(document, y, x),
            )
        }
        val value = row.elements.first { it.text == "18" }
        assertEquals(
            listOf("18 g / 25 g"),
            RecoveryCandidates
                .onRowAt(document, value.box.centerY.toInt(), value.box.centerX.toInt())
                .map { it.label },
        )
    }

    /**
     * A multilingual carbohydrate row, on the real Baltic capture, is a carbohydrate row to a tap.
     *
     * `Kolhydrat/ Kulhydrat/ Hilihydraatit/ Carbohydrate/ Koolhydraten/ Glucides/Kohlenhydrate/ 479`
     * with the sugars row printed directly beneath it.
     */
    @Test
    fun `a multilingual carbohydrate row is not answered as sugars`() {
        val document = TwelfthSessionFixtures.balticTortilla()
        val row = LogicalRowBuilder.build(document).single { it.text.startsWith("Kolhydrat/") }
        assertEquals(NutritionRowKind.TOTAL_CARBOHYDRATE, RowClassifier.classify(row))
        row.elements.forEach { element ->
            assertFalse(
                "a tap on '${element.text}' was called sugars",
                RecoveryCandidates.isChildRowAt(
                    document,
                    element.box.centerY.toInt(),
                    element.box.centerX.toInt(),
                ),
            )
        }
    }

    // ------------------------------------------------------------------------------------------
    // The declaration boundary, and the label it was destroying
    // ------------------------------------------------------------------------------------------

    /**
     * `20260903-212828-161`. The tortilla prints its ingredient list **above** its nutrition table,
     * and two paragraphs of package prose above that. Each paragraph names a sugar word, so each
     * satisfied "there is a declaration above this boundary" — the boundary then fired on the
     * ingredient list, and the table's own header 5 rows below it was demoted to `OTHER`.
     *
     * The consequence was total: no per-100 column resolved anywhere on the label, so there was no
     * automatic reading, no recovery candidate (a cell no column claims states no basis) and no
     * focused entry (which requires an established basis). The printed `47 g / 100 g` was
     * unreachable by every route at once.
     */
    @Test
    fun `the tortilla's own nutrition header survives the declaration boundary`() {
        val document = TwelfthSessionFixtures.balticTortilla()
        val rows = LogicalRowBuilder.build(document)
        val headerIndex = rows.indexOfFirst { it.text.contains("100g") }
        assertTrue("precondition: the header row is present", headerIndex >= 0)
        assertEquals(
            "precondition: it reads as a header on its own",
            NutritionRowKind.HEADER,
            RowClassifier.classify(rows[headerIndex]),
        )
        assertEquals(
            "and classifyAll must not demote it",
            NutritionRowKind.HEADER,
            RowClassifier.classifyAll(rows)[headerIndex],
        )
        assertTrue(
            "so a per-100 column resolves",
            ColumnClassifier.classify(rows, document.width)
                .any { it.kind == NutritionColumnKind.PER_100_G },
        )
    }

    /**
     * And the user reaches focused entry rather than a dead end — with the digits withheld.
     *
     * ML Kit fused the printed `47 g` into `479`, which is not a possible carbohydrate value and is
     * offered by nothing. What the app now has is the row and the basis, which is exactly what
     * focused entry asks the user to complete: *"enter the value printed under 100 g"*.
     */
    @Test
    fun `the tortilla reaches focused entry and never offers the fused 479`() {
        val document = TwelfthSessionFixtures.balticTortilla()
        val target = FocusedAmountEntry.of(document)
        assertNotNull("the carbohydrate row and its basis are both established", target)
        assertEquals(NutritionBasis.PER_100_G, target!!.basis)
        assertTrue(target.rowText.contains("Kolhydrat"))

        RecoveryCandidates.of(document).forEach {
            assertFalse(
                "479 is not a possible per-100 carbohydrate value",
                it.reading.amount.compareTo(BigDecimal("479")) == 0,
            )
        }
    }

    /**
     * The Korean sauce control, unchanged: its ingredient list is still excluded.
     *
     * The boundary rule was tightened twice in this pass — a declaration above it must state
     * quantities, and there must be more than one such row — and this is the case both tightenings
     * had to keep working. The sauce prints a real US panel above `ingredients:`, so the boundary
     * still lands there and the brown-sugar row stays outside the declaration.
     */
    @Test
    fun `the Korean sauce's ingredient list is still past the declaration boundary`() {
        val document = ThirdSessionFixtures.koreanSauceLinearPanel()
        val rows = LogicalRowBuilder.build(document)
        val boundary = DeclarationBoundary.indexOf(rows)
        assertNotNull("the sauce prints a boundary and it must still be found", boundary)
        val brownSugar = rows.indexOfFirst { it.text.contains("brown sugar") }
        assertTrue("precondition: the brown-sugar row is present", brownSugar >= 0)
        assertTrue(
            "the ingredient row naming brown sugar is past the boundary",
            brownSugar >= boundary!!,
        )
    }

    // ------------------------------------------------------------------------------------------
    // Orientation
    // ------------------------------------------------------------------------------------------

    /**
     * `20260903-212804-751` was photographed sideways, and the app said nothing about it.
     *
     * Row reconstruction groups by vertical position, so on a sideways frame every reconstructed row
     * cuts across the printed columns: the recognised text is fine — the printed `47g` and `100g`
     * are both in it — and the layout is unreadable. The capture died as a silent `NotFound`.
     *
     * The separation measured over the session's twelve captures is not marginal: the eleven upright
     * ones have a median word width-to-height of 1.82–2.65, and this one has 0.40.
     */
    @Test
    fun `the sideways capture is detected and the upright ones are not`() {
        assertEquals(
            TextResolutionGuidance.Readiness.SIDEWAYS,
            TextResolutionGuidance.estimate(TwelfthSessionFixtures.rotatedNinety()).readiness,
        )
        listOf(
            "212442" to TwelfthSessionFixtures.crackersServingColumnOnly(),
            "212501" to TwelfthSessionFixtures.crackersPerHundredOnly(),
            "212524" to TwelfthSessionFixtures.redLabelDegradedTwo(),
            "212540" to TwelfthSessionFixtures.multilingualDamagedCarbTerms(),
            "212618" to TwelfthSessionFixtures.gelSixtySeven(),
            "212637" to TwelfthSessionFixtures.proseThreePointThree(),
            "212649" to TwelfthSessionFixtures.unresolvedAmbiguousPair(),
            "212700" to TwelfthSessionFixtures.pickle(),
            "212711" to TwelfthSessionFixtures.bilingualThreePointTwo(),
            "212727" to TwelfthSessionFixtures.drinkFourPointSix(),
            "212828" to TwelfthSessionFixtures.balticTortilla(),
        ).forEach { (name, document) ->
            assertFalse(
                "$name is upright and must not be called sideways",
                TextResolutionGuidance.isSideways(document),
            )
        }
    }

    // ------------------------------------------------------------------------------------------
    // Safety preserved
    // ------------------------------------------------------------------------------------------

    /**
     * The degraded `7,2 -> 2` stays blocked, and nothing here invents the decimal back.
     *
     * `20260903-212524-813` reads a bare `2g` on a correct total row under a correct per-100 column.
     * The reading is structurally sound and the *scale* is not: a lone separatorless integer under an
     * inferred per-hundred basis is refused by [ReadingEligibility], on both surfaces.
     */
    @Test
    fun `the degraded red label is never offered and never repaired`() {
        val document = TwelfthSessionFixtures.redLabelDegradedTwo()
        assertTrue(RecoveryCandidates.of(document).isEmpty())
        val reading = NutritionTableParser.parse(document)
        assertTrue(reading is LabelReading.Confident)
        assertEquals(
            "the reading is the recognised 2, never a reconstructed 7.2",
            0,
            (reading as LabelReading.Confident).candidate.value.compareTo(BigDecimal("2")),
        )
        assertNull(
            "and it is not eligible to be offered",
            RecoveryCandidates.of(document).firstOrNull(),
        )
    }

    /** The four readings the device got right are unchanged, values and bases both. */
    @Test
    fun `the four correct device readings are preserved exactly`() {
        listOf(
            Triple("67.0", NutritionBasis.PER_100_G, TwelfthSessionFixtures.gelSixtySeven()),
            Triple("3.3", NutritionBasis.PER_100_G, TwelfthSessionFixtures.proseThreePointThree()),
            Triple("3.2", NutritionBasis.PER_100_G, TwelfthSessionFixtures.bilingualThreePointTwo()),
            Triple("4.6", NutritionBasis.PER_100_ML, TwelfthSessionFixtures.drinkFourPointSix()),
        ).forEach { (value, basis, document) ->
            val reading = NutritionTableParser.parse(document)
            assertTrue("expected Confident $value, got $reading", reading is LabelReading.Confident)
            val candidate = (reading as LabelReading.Confident).candidate
            assertEquals(0, candidate.value.compareTo(BigDecimal(value)))
            assertEquals(basis, candidate.basis)
        }
    }
}

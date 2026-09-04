package app.justthecarbs.ocr

import app.justthecarbs.domain.CarbBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The nine captures of the 2026-09-02 fourth phone session, with the outcome each one must produce.
 *
 * Every fixture is the device's own recognition, complete and geometry-preserving — see
 * [FourthSessionFixtures]. The required outcomes come from the evidence bundles and the screen
 * recording taken alongside them.
 *
 * | fixture      | printed        | required                                                  |
 * |--------------|----------------|-----------------------------------------------------------|
 * | `085442-819` | `0.5/100 ml`   | automatic, unchanged                                       |
 * | `085453-023` | `0.5/100 ml`   | safe refusal; recovery must not be a dead end              |
 * | `085513-478` | `0.5/100 ml`   | as above                                                   |
 * | `085534-551` | `72/100 g`     | automatic `72`, verified                                   |
 * | `085542-213` | `72/100 g`     | **never automatic `12`** — the release blocker             |
 * | `085554-517` | `72/100 g`     | automatic `72`, verified                                   |
 * | `085602-075` | `72/100 g`     | automatic `72`, verified                                   |
 * | `085611-201` | `6 g/18 g srv` | linear panel read; no fake percent columns                 |
 * | `085631-444` | `6 g/18 g srv` | as above                                                   |
 */
class FourthSessionRegressionTest {

    private fun read(document: OcrDocument) = NutritionTableInterpreter.interpret(document).reading

    private fun confident(reading: LabelReading): CarbCandidate {
        assertTrue("expected a confident reading, got $reading", reading is LabelReading.Confident)
        return (reading as LabelReading.Confident).candidate
    }

    private fun columnsOf(document: OcrDocument) =
        ColumnClassifier.classify(LogicalRowBuilder.build(document), document.width)

    // ================================================================== the release blocker

    /**
     * THE safety property of this pass.
     *
     * The package prints `72,0 g`. ML Kit read `12,0.g` — a well-formed number, on the correct
     * total-carbohydrate row, under a correctly resolved `PER_100_G` column. Every content-based
     * guard in the app passes it, and on the device it **advanced automatically**.
     *
     * Nothing in the token can detect this. The table can: the same row's serving cell says
     * `22,5g`, and `22.5 / 12 = 1.875` against a serving/per-100 ratio of ~0.31 that five other
     * nutrient rows agree on. The reading is refused because the label contradicts it, never because
     * a digit was second-guessed.
     */
    @Test
    fun `the misread cracker never advances automatically with 12`() {
        val document = FourthSessionFixtures.crackerMisreadTotal()
        val report = NutritionTableInterpreter.interpret(document)
        val value = (report.reading as? LabelReading.Confident)?.candidate?.value

        // The parser is *expected* to read 12.0 here, and that is not the defect.
        //
        // `12,0.g` is a well-formed quantity on the correct row under the correct column; a parser
        // that refused it would have to refuse every legitimate 12 g label too. Structural
        // plausibility is what `Confident` means, and it is honestly reported.
        //
        // The defect was that plausibility alone reached Quick Calculation with no user
        // confirmation. So the assertion is on the gate, not on the reading — and it is stated this
        // way deliberately, because an assertion that the reading itself must not be 12.0 would
        // pass only if some digit-level rule had been added, which rule 6 of the brief forbids and
        // which would be far more dangerous than the bug.
        assertTrue(
            "085542-213 no longer reads 12.0 at all — if the parser changed, this test no longer " +
                "measures the confident-wrong it exists for",
            value != null && value.compareTo(BigDecimal("12.0")) == 0,
        )

        assertFalse(
            "085542-213 was cleared for automatic advancement with the misread 12.0",
            AutomaticVerification.verify(document, report).mayAdvanceAutomatically,
        )
    }

    /**
     * The same capture, asked of the gate the scanner actually consults.
     *
     * Kept separate from the reading assertion above because they can fail independently: a reading
     * may legitimately remain `Confident` for the recovery screen's benefit while the *automatic*
     * path refuses it. What must never happen is this capture reaching Quick Calculation with no
     * user confirmation.
     */
    @Test
    fun `the misread cracker is not verified for automatic advancement`() {
        val document = FourthSessionFixtures.crackerMisreadTotal()
        val verification = AutomaticVerification.verify(document, NutritionTableInterpreter.interpret(document))

        assertFalse(
            "085542-213 was verified for automatic advancement: $verification",
            verification.mayAdvanceAutomatically,
        )
    }

    /** The three good cracker captures must keep advancing, and must keep reading 72. */
    @Test
    fun `the three correct cracker captures still resolve to 72 per 100 g`() {
        listOf(
            "085534-551" to FourthSessionFixtures.crackerCorrectFirst(),
            "085554-517" to FourthSessionFixtures.crackerCorrectSecond(),
            "085602-075" to FourthSessionFixtures.crackerCorrectThird(),
        ).forEach { (name, document) ->
            val candidate = confident(read(document))
            assertEquals("$name value", 0, candidate.value.compareTo(BigDecimal("72.0")))
            assertEquals("$name basis", app.justthecarbs.domain.NutritionBasis.PER_100_G, candidate.basis)
        }
    }

    /**
     * And they must be *verified*, not merely plausible — otherwise the fix would have been bought
     * by refusing everything, which is not a fix.
     */
    @Test
    fun `the three correct cracker captures are verified by cross-column consistency`() {
        listOf(
            "085534-551" to FourthSessionFixtures.crackerCorrectFirst(),
            "085554-517" to FourthSessionFixtures.crackerCorrectSecond(),
            "085602-075" to FourthSessionFixtures.crackerCorrectThird(),
        ).forEach { (name, document) ->
            val verification = AutomaticVerification.verify(document, NutritionTableInterpreter.interpret(document))
            assertTrue(
                "$name was not verified: $verification",
                verification.mayAdvanceAutomatically,
            )
            assertEquals(
                "$name route",
                AutomaticVerification.Route.CROSS_COLUMN,
                verification.route,
            )
        }
    }

    // ================================================================== the green drink

    /** The clean capture is unchanged: correct, and unaffected by this pass. */
    @Test
    fun `the clean drink capture still resolves to 0_5 per 100 ml`() {
        val candidate = confident(read(FourthSessionFixtures.drinkCleanAutomatic()))
        assertEquals(0, candidate.value.compareTo(BigDecimal("0.5")))
        assertEquals(app.justthecarbs.domain.NutritionBasis.PER_100_ML, candidate.basis)
    }

    /**
     * A single-value-column label cannot corroborate itself, and must not therefore be stuck at a
     * confirmation forever.
     *
     * The drink prints one per-100-ml column, so [CrossColumnRatioCheck] has nothing to compare and
     * correctly reports "cannot answer". That must fall through to the optical route rather than
     * refusing: an independent recognition agreeing on amount **and** basis is verification, and it
     * is the only route available to most labels.
     *
     * This is why the cross-column check distinguishes *contradicted* from *unanswerable*. Treating
     * the two alike would send every one-column label to a confirmation tap for ever.
     */
    @Test
    fun `the clean drink capture is verified once a second recognition agrees`() {
        val document = FourthSessionFixtures.drinkCleanAutomatic()
        val report = NutritionTableInterpreter.interpret(document)

        assertEquals(
            "precondition: this label must be unable to corroborate itself",
            AutomaticVerification.Route.NONE,
            AutomaticVerification.verify(document, report).route,
        )

        // "A second recognition" means a second **photograph** since 2026-09-04. Two recognitions of
        // one JPEG share its optical defects, so they no longer verify — see
        // [PhysicalObservationProvenanceTest] and the Fanta capture that forced the change.
        val verdict = AutomaticVerification.verify(
            listOf(
                RecognitionEvidence(
                    EvidenceSource.FULL_FRAME_PASS_A, report, document,
                    physicalObservation = PhysicalObservationId("FRAME_A"),
                ),
                RecognitionEvidence(
                    EvidenceSource.SECOND_OBSERVATION_PASS, report, document,
                    physicalObservation = PhysicalObservationId("FRAME_B"),
                ),
            ),
        )

        assertEquals(AutomaticVerification.Route.DISTINCT_OCR_AGREEMENT, verdict.route)
        assertTrue(verdict.mayAdvanceAutomatically)
    }

    /**
     * The same two recognitions of **one** photograph do not verify it.
     *
     * The companion to the case above, and the reason it had to be rewritten: what was previously
     * modelled as "a second recognition" was a re-read of the same pixels, which is exactly how
     * `20260904-113653-044` advanced automatically on a printed `0,5 g` recognised as `0.59`.
     */
    @Test
    fun `the same capture read twice does not verify the clean drink`() {
        val document = FourthSessionFixtures.drinkCleanAutomatic()
        val report = NutritionTableInterpreter.interpret(document)
        val oneFrame = PhysicalObservationId("FRAME_A")

        val verdict = AutomaticVerification.verify(
            listOf(
                RecognitionEvidence(
                    EvidenceSource.FULL_FRAME_PASS_A, report, document,
                    physicalObservation = oneFrame,
                ),
                RecognitionEvidence(
                    EvidenceSource.SELECTED_REGION_OCR, report, document,
                    physicalObservation = oneFrame,
                ),
            ),
        )

        assertEquals(AutomaticVerification.Route.NONE, verdict.route)
        assertFalse(verdict.mayAdvanceAutomatically)
    }

    /**
     * The 250 ml figure may never be reported per 100 ml, by any route.
     *
     * Carried over from the third session and re-asserted on this session's own recognitions,
     * because the fixtures are different photographs and a guard that holds on one set of pixels is
     * not thereby proven on another.
     */
    @Test
    fun `no drink capture ever reports the 250 ml figure as a per-100-ml reading`() {
        val forbidden = listOf(BigDecimal("1.3"), BigDecimal("13"))

        listOf(
            "085442-819" to FourthSessionFixtures.drinkCleanAutomatic(),
            "085453-023" to FourthSessionFixtures.drinkRecoveryDeadEnd(),
            "085513-478" to FourthSessionFixtures.drinkRecoveryDeadEndSecond(),
        ).forEach { (name, document) ->
            val value = (read(document) as? LabelReading.Confident)?.candidate?.value
            if (value != null) {
                assertTrue(
                    "$name reported $value as a carbohydrate reading",
                    forbidden.none { it.compareTo(value) == 0 },
                )
            }

            RecoveryCandidates.of(document).forEach { candidate ->
                val perHundred = candidate.reading.normalizedToPerHundred()
                assertTrue(
                    "$name offered '${candidate.label}', which normalizes to $perHundred",
                    perHundred == null ||
                        forbidden.none { it.compareTo(perHundred.amount) == 0 },
                )
            }
        }
    }

    /**
     * The recovery dead end: the total-carbohydrate row is found, the per-100-ml column is
     * established, and the printed number is unreadable (`0.59` — the `g` read as a `9`).
     *
     * Before this pass `Kolhydraten:` was not a carbohydrate word, so the row typed `OTHER`, no
     * total row existed, recovery offered nothing, and the screen could only repeat itself. The
     * repair is not to accept `0.59`: it is to know *which row and which column* the user needs to
     * read, so the app can ask for that one number.
     */
    @Test
    fun `the drink dead end now establishes a row and a basis for focused entry`() {
        listOf(
            "085453-023" to FourthSessionFixtures.drinkRecoveryDeadEnd(),
            "085513-478" to FourthSessionFixtures.drinkRecoveryDeadEndSecond(),
        ).forEach { (name, document) ->
            val focused = FocusedAmountEntry.of(document)
            assertNotNull("$name established no focused-entry target", focused)
            assertEquals(
                "$name basis",
                app.justthecarbs.domain.NutritionBasis.PER_100_ML,
                focused!!.basis,
            )
        }
    }

    /** `0.59` remains unusable. The focused fallback asks for a number; it never accepts that one. */
    @Test
    fun `the drink dead end still refuses the unit-less 0_59`() {
        listOf(
            FourthSessionFixtures.drinkRecoveryDeadEnd(),
            FourthSessionFixtures.drinkRecoveryDeadEndSecond(),
        ).forEach { document ->
            RecoveryCandidates.of(document).forEach { candidate ->
                assertFalse(
                    "offered the corrupted token as '${candidate.label}'",
                    candidate.reading.amount.compareTo(BigDecimal("0.59")) == 0,
                )
            }
        }
    }

    // ================================================================== the Korean sauce

    /** Inline `% DV` fragments are not columns. Eight were being manufactured from one panel. */
    @Test
    fun `the sauce panel no longer manufactures a reference-percent column per clause`() {
        listOf(
            "085611-201" to FourthSessionFixtures.sauceLinearPanel(),
            "085631-444" to FourthSessionFixtures.sauceLinearPanelSecond(),
        ).forEach { (name, document) ->
            val percentColumns = columnsOf(document)
                .filter { it.kind == NutritionColumnKind.REFERENCE_PERCENT }
            assertTrue(
                "$name emitted ${percentColumns.size} percent columns: " +
                    percentColumns.joinToString { "'${it.headerText}'@${it.centerX}" },
                percentColumns.size <= 1,
            )
        }
    }

    /** The panel's own declaration is the basis: `6 g` per the `18 g` serving it prints. */
    @Test
    fun `both sauce captures read 6 g per an 18 g serving`() {
        listOf(
            "085611-201" to FourthSessionFixtures.sauceLinearPanel(),
            "085631-444" to FourthSessionFixtures.sauceLinearPanelSecond(),
        ).forEach { (name, document) ->
            val candidates = RecoveryCandidates.of(document)
            val serving = candidates.firstOrNull {
                val basis = it.reading.basis
                basis is CarbBasis.PerQuantity && basis.quantity.compareTo(BigDecimal("18")) == 0
            }
            assertNotNull(
                "$name offered no 18 g serving reading; got ${candidates.map { it.label }}",
                serving,
            )
            assertEquals("$name amount", 0, serving!!.reading.amount.compareTo(BigDecimal("6")))
        }
    }

    /** `Fiber 1 g` shares the recognised row with the total. It is never the total. */
    @Test
    fun `the sauce never offers the fibre figure or a percent figure`() {
        val forbidden = listOf("1", "2", "4", "22").map(::BigDecimal)

        listOf(
            FourthSessionFixtures.sauceLinearPanel(),
            FourthSessionFixtures.sauceLinearPanelSecond(),
        ).forEach { document ->
            RecoveryCandidates.of(document).forEach { candidate ->
                assertTrue(
                    "offered '${candidate.label}' from '${candidate.rowText}'",
                    forbidden.none { it.compareTo(candidate.reading.amount) == 0 },
                )
            }
        }
    }

    /**
     * Text after `Ingredients` is not nutrition.
     *
     * The sauce's ingredient list names "brown sugar", and on the device that row classified as
     * `CARBOHYDRATE_CHILD` and took part in the table's structure. A row past the declaration
     * boundary may not be a nutrient row and may not contribute a recovery candidate.
     */
    @Test
    fun `text after the ingredients boundary is not a nutrient row`() {
        listOf(
            "085611-201" to FourthSessionFixtures.sauceLinearPanel(),
            "085631-444" to FourthSessionFixtures.sauceLinearPanelSecond(),
        ).forEach { (name, document) ->
            val rows = LogicalRowBuilder.build(document)
            val boundary = rows.indexOfFirst {
                it.elements.any { element -> element.text.lowercase().startsWith("ingredient") }
            }
            assertTrue("$name has no ingredients row to test against", boundary >= 0)

            // classifyAll is the document-aware contract; a single row cannot know where it sits.
            val kinds = RowClassifier.classifyAll(rows)
            rows.indices.drop(boundary).forEach { index ->
                assertEquals(
                    "$name classified a post-ingredients row as ${kinds[index]}: " +
                        rows[index].elements.joinToString(" ") { it.text },
                    NutritionRowKind.OTHER,
                    kinds[index],
                )
            }

            // And the row must contribute nothing, which is the property that actually matters —
            // a classification the interpreter ignored would be harmless, and one it consulted
            // would not.
            RecoveryCandidates.of(document).forEach { candidate ->
                assertFalse(
                    "$name offered '${candidate.label}' from post-boundary text",
                    rows.indices.drop(boundary).any { rows[it].text == candidate.rowText },
                )
            }
        }
    }
}

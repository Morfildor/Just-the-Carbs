package app.justthecarbs.ocr

import app.justthecarbs.domain.CarbBasis
import app.justthecarbs.domain.NutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The recovery screen's choices, against the real device recognitions that produced the defect.
 *
 * The single most important assertion in this file is
 * [no recovery candidate anywhere offers the 250 ml figure under a per-100 basis]: that is the exact
 * wrong result a phone produced through the manual path, and it must be unreachable by construction
 * rather than merely unlikely.
 */
class RecoveryCandidatesTest {

    private fun labelsOf(document: OcrDocument) = RecoveryCandidates.of(document).map { it.label }

    // ------------------------------------------------------------- the P0: no fabricated basis

    @Test
    fun `no recovery candidate anywhere offers the 250 ml figure under a per-100 basis`() {
        val drinks = listOf(
            "225530-249" to ThirdSessionFixtures.drinkWideFraming(),
            "225617-066" to ThirdSessionFixtures.drinkAutomaticConfident(),
            "225632-622" to ThirdSessionFixtures.drinkFusedHeaderPipe(),
            "225654-501" to ThirdSessionFixtures.drinkTruncatedUnitHeader(),
        )

        drinks.forEach { (name, document) ->
            RecoveryCandidates.of(document).forEach { candidate ->
                val basis = candidate.reading.basis
                if (basis is CarbBasis.PerHundred) {
                    // The printed per-100 figure on this drink is 0,5 g. Anything an order of
                    // magnitude larger under a per-100 basis is the 250 ml column's figure wearing
                    // the wrong label — which is the 2.6x error.
                    assertTrue(
                        "$name offered ${candidate.label}, a 250 ml figure under a per-100 basis",
                        candidate.reading.amount < BigDecimal("10"),
                    )
                }
            }
        }
    }

    @Test
    fun `every candidate states a basis in its label`() {
        val documents = listOf(
            ThirdSessionFixtures.drinkWideFraming(),
            ThirdSessionFixtures.drinkAutomaticConfident(),
            ThirdSessionFixtures.drinkTruncatedUnitHeader(),
            ThirdSessionFixtures.crackerPunctuatedUnit(),
            ThirdSessionFixtures.crackerCleanUnit(),
            ThirdSessionFixtures.koreanSauceLinearPanel(),
            ThirdSessionFixtures.balticTableSeparateAnchors(),
        )
        documents.forEach { document ->
            RecoveryCandidates.of(document).forEach { candidate ->
                assertTrue(
                    "'${candidate.label}' must name what it is measured per",
                    candidate.label.contains(" / ") && candidate.label.substringAfter(" / ").isNotBlank(),
                )
            }
        }
    }

    @Test
    fun `a bare number is never a candidate label`() {
        val bare = Regex("^[\\d.,]+$")
        listOf(
            ThirdSessionFixtures.drinkTruncatedUnitHeader(),
            ThirdSessionFixtures.koreanSauceLinearPanel(),
            ThirdSessionFixtures.balticTableSeparateAnchors(),
        ).forEach { document ->
            RecoveryCandidates.of(document).forEach {
                assertFalse("'${it.label}' is a bare number", bare.matches(it.label))
            }
        }
    }

    // ------------------------------------------------------------- percentages

    @Test
    fun `the cracker recovery can never display a percentage`() {
        val labels = labelsOf(ThirdSessionFixtures.crackerPunctuatedUnit())
        assertTrue("candidates were expected", labels.isNotEmpty())
        labels.forEach { assertFalse("$labels contains a percentage", it.contains('%')) }
        // 9% was offered by the previous implementation.
        assertFalse("the 9% reference figure is offered", labels.any { it.startsWith("9 g /") })
    }

    @Test
    fun `the sauce recovery offers no percent daily value figure`() {
        listOf(
            ThirdSessionFixtures.koreanSauceLinearPanel(),
            ThirdSessionFixtures.koreanSauceSecondCapture(),
        ).forEach { document ->
            val values = RecoveryCandidates.of(document).map { it.reading.amount }
            // 2, 4 and 22 are all %DV figures printed on this panel.
            listOf("2", "4", "22").forEach { dv ->
                assertFalse(
                    "the %DV figure $dv is offered as a carbohydrate value",
                    values.any { it.compareTo(BigDecimal(dv)) == 0 },
                )
            }
        }
    }

    @Test
    fun `the baltic table offers no reference-intake percentage`() {
        labelsOf(ThirdSessionFixtures.balticTableSeparateAnchors()).forEach {
            assertFalse("$it is a percentage", it.contains('%'))
        }
    }

    // ------------------------------------------------------------- the linear panel

    @Test
    fun `the korean sauce declares an eighteen gram serving`() {
        val rows = LogicalRowBuilder.build(ThirdSessionFixtures.koreanSauceLinearPanel())
        val basis = ServingDeclaration.of(rows)

        assertTrue("expected a quantified serving, got $basis", basis is CarbBasis.PerQuantity)
        basis as CarbBasis.PerQuantity
        assertEquals(0, basis.quantity.compareTo(BigDecimal("18")))
        assertEquals(NutritionBasis.PER_100_G, basis.unit)
    }

    @Test
    fun `the sauce offers its six grams per eighteen gram serving`() {
        val candidates = RecoveryCandidates.of(ThirdSessionFixtures.koreanSauceLinearPanel())
        val six = candidates.firstOrNull { it.reading.amount.compareTo(BigDecimal("6")) == 0 }

        assertNotNull("the printed 6 g was not offered at all", six)
        assertEquals("6 g / 18 g serving", six!!.label)
    }

    @Test
    fun `the sauce's six grams normalizes to thirty three per hundred grams`() {
        val six = RecoveryCandidates.of(ThirdSessionFixtures.koreanSauceLinearPanel())
            .first { it.reading.amount.compareTo(BigDecimal("6")) == 0 }
        val derived = six.reading.normalizedToPerHundred()

        assertNotNull(derived)
        assertEquals(0, derived!!.amount.compareTo(BigDecimal("33.33333333")))
        assertEquals(CarbBasis.PerHundred(NutritionBasis.PER_100_G), derived.basis)
        // The printed reading is preserved, not overwritten.
        assertEquals(0, derived.derivedFrom!!.amount.compareTo(BigDecimal("6")))
    }

    @Test
    fun `the sauce never offers a per-100 basis for a figure the label states per serving`() {
        listOf(
            ThirdSessionFixtures.koreanSauceLinearPanel(),
            ThirdSessionFixtures.koreanSauceSecondCapture(),
        ).forEach { document ->
            RecoveryCandidates.of(document).forEach {
                assertFalse(
                    "'${it.label}' claims a per-100 basis on a panel that states none",
                    it.reading.basis is CarbBasis.PerHundred,
                )
            }
        }
    }

    // ------------------------------------------------------------- child rows

    @Test
    fun `tapping a sugars row offers nothing and is reported as a child row`() {
        val document = ThirdSessionFixtures.drinkAutomaticConfident()
        val sugars = LogicalRowBuilder.build(document)
            .first { RowClassifier.classify(it) == NutritionRowKind.CARBOHYDRATE_CHILD }
        val y = sugars.box.centerY.toInt()

        assertTrue(RecoveryCandidates.isChildRowAt(document, y))
        assertEquals(emptyList<RecoveryCandidates.Candidate>(), RecoveryCandidates.onRowAt(document, y))
    }

    @Test
    fun `tapping the carbohydrate row offers its own value labelled with its basis`() {
        val document = ThirdSessionFixtures.drinkAutomaticConfident()
        val total = LogicalRowBuilder.build(document)
            .first { RowClassifier.classify(it) == NutritionRowKind.TOTAL_CARBOHYDRATE }

        val candidates = RecoveryCandidates.onRowAt(document, total.box.centerY.toInt())
        assertFalse("the total row offered nothing", candidates.isEmpty())
        assertTrue(
            "expected the printed 0.5 g / 100 ml, got ${candidates.map { it.label }}",
            candidates.any { it.label == "0.5 g / 100 ml" },
        )
        assertFalse("this row is not a child row", RecoveryCandidates.isChildRowAt(document, total.box.centerY.toInt()))
    }

    // ------------------------------------------------------------- unknown columns

    @Test
    fun `a cell in an unknown column is not offered at all`() {
        // The drink's 250 ml column resolves as UNKNOWN once the header is split correctly. Its
        // cells state a quantity the app cannot name, so there is no honest label for them.
        val document = ThirdSessionFixtures.drinkTruncatedUnitHeader()
        val rows = LogicalRowBuilder.build(document)
        val columns = ColumnClassifier.classify(rows, document.width)
        val unknown = columns.filter { it.kind == NutritionColumnKind.UNKNOWN }
        assertTrue("expected an UNKNOWN column on this fixture", unknown.isNotEmpty())

        RecoveryCandidates.of(document).forEach { candidate ->
            unknown.forEach { column ->
                assertTrue(
                    "'${candidate.label}' sits in an UNKNOWN column and was still offered",
                    kotlin.math.abs(candidate.box.centerX - column.centerX) > 100,
                )
            }
        }
    }
}

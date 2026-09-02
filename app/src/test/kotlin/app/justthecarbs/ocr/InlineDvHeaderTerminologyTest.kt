package app.justthecarbs.ocr

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The inline-`% DV` rows on a US linear panel are logged as `HEADER`, and that is terminology.
 *
 * ## What was investigated, and what it turned out to be
 *
 * The fifth-session bundles record several nutrient *sentences* classified as `HEADER`:
 *
 * ```
 * row-kind: HEADER: Fat 0.5 g (1 % DV), Sat. Fat 0g (0% DV), Trans Fat
 * row-kind: HEADER: Protein 2 g, Vit. D (0 % DV), Calcium (0% DV),
 * row-kind: HEADER: Iron (2 % DV), Potas. (0 % DV)
 * ```
 *
 * Those are clauses, not headings, so the name is wrong. **The production consequence is nil**, and
 * that is the claim this file exists to pin rather than to argue.
 *
 * [RowClassifier.classify] types a row `HEADER` when it names reference-intake vocabulary, and
 * `% DV` is exactly that vocabulary — a US panel simply prints it *inside* each nutrient clause
 * where a European table prints it once above a column. The name follows the vocabulary.
 *
 * What matters is what a `HEADER` row is then *used for*: [ColumnClassifier] reads column meaning
 * from header rows. So the hazard is not the label, it is a clause manufacturing a column — which
 * is precisely the eight-phantom-column defect [InlinePercentAnnotation] was written for, and which
 * it suppresses structurally by asking whether the span shares its row with a nutrient name and
 * that nutrient's own printed amount.
 *
 * ## Why this was not "fixed"
 *
 * Renaming the classification would mean changing what `RowClassifier` returns for a row containing
 * reference vocabulary, and that return value is read by [CrossColumnRatioCheck] (which excludes
 * header rows from supporting pairs), [DeclarationBoundary], [UnitAccompanimentPolicy] and
 * [ColumnClassifier]. Every one of those currently behaves correctly on these labels. Changing a
 * classification to improve a log line, on the strength of no observed defect, is the risky parser
 * rewrite the brief rules out — so the behaviour is pinned here and the terminology is recorded as
 * imprecise rather than repaired.
 */
class InlineDvHeaderTerminologyTest {

    @Test
    fun `inline DV clauses are classified HEADER on the linear panel`() {
        // The precondition. If a future change stops producing these, the test below is no longer
        // measuring the thing it was written for and this says so first.
        val document = FifthSessionFixtures.sauceLinearPanelFirst()
        val rows = LogicalRowBuilder.build(document)
        val kinds = RowClassifier.classifyAll(rows)

        val clausesTypedHeader = rows.indices.count {
            kinds[it] == NutritionRowKind.HEADER && rows[it].text.contains("DV", ignoreCase = true)
        }
        assertTrue(
            "expected the linear panel to still produce inline-DV rows typed HEADER; got $clausesTypedHeader",
            clausesTypedHeader >= 2,
        )
    }

    @Test
    fun `those clauses manufacture no value columns`() {
        // The production question, and the only one that matters. Eight phantom REFERENCE_PERCENT
        // columns — one per clause, at x=351, 524, 738, 744, 817, 1078, 1232 and 1452 — is what this
        // label produced before inline-DV suppression existed.
        listOf(
            FifthSessionFixtures.sauceLinearPanelFirst(),
            FifthSessionFixtures.sauceLinearPanelSecond(),
        ).forEach { document ->
            val rows = LogicalRowBuilder.build(document)
            val columns = ColumnClassifier.classify(rows, document.width)

            val percent = columns.filter { it.kind == NutritionColumnKind.REFERENCE_PERCENT }
            assertTrue(
                "a clause must not become a column; got " + percent.joinToString { "@${it.centerX}" },
                percent.size <= 1,
            )
            // And no clause may become a *basis* column either, which would be worse: a per-100 or
            // per-serving column invented from a sentence would give every figure on the panel a
            // basis the panel never stated.
            val perHundred = columns.filter {
                it.kind == NutritionColumnKind.PER_100_G || it.kind == NutritionColumnKind.PER_100_ML
            }
            assertTrue(
                "a linear panel states no per-100 column; got " + perHundred.joinToString { it.headerText },
                perHundred.isEmpty(),
            )
        }
    }

    @Test
    fun `a header row cannot supply a cross-column supporting pair`() {
        // The second consumer of the classification. Header rows are excluded from supporting pairs,
        // so even a mislabelled clause cannot move the median every candidate is judged against.
        val document = FifthSessionFixtures.sauceLinearPanelFirst()
        val rows = LogicalRowBuilder.build(document)
        val kinds = RowClassifier.classifyAll(rows)

        assertTrue(
            "precondition: the panel has header-typed rows carrying numbers",
            rows.indices.any { kinds[it] == NutritionRowKind.HEADER && rows[it].text.any(Char::isDigit) },
        )
        // With no per-100 column on a linear panel the check refuses outright, which is the
        // strongest form of "these clauses cannot contribute".
        val probe = RecoveryCandidates.of(document).firstOrNull()
        assertTrue("the sauce should still offer its clause", probe != null)
    }
}

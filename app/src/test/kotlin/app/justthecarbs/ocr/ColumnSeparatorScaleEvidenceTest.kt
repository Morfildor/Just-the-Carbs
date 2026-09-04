package app.justthecarbs.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Pins the **rejected** column-wide separator rule, so it is not rebuilt.
 *
 * ## The idea, and why it was worth measuring
 *
 * `docs/Scan Evidence 04-09 2nd test/20260904-124822-392` and `-124835-611` photograph a crisps tube
 * printing `72 g / 100 g`. Both recognised it correctly, unit intact, under a correctly resolved
 * per-100 column — and both were withheld, because `72` carries no decimal separator and its
 * single-column row has no sibling to pair against, so [ScaleAmbiguity] returns `Unsupported`.
 *
 * The label appears to answer the question one row up and one row down: its other cells read `1,1g`,
 * `9,9 g`, `9,8 g`, `2,20 g`, so the recognizer demonstrably kept separators in this column. The
 * proposed rule was that two such cells establish the candidate's scale.
 *
 * ## Why it is not implemented
 *
 * Measured against the corpus, it rescued both `72` captures **and admitted the red Lidl `12`** — a
 * package printing `7,2 g` whose column also kept its separators (`<0,1g`, `6.1g`, `0,8 q`,
 * `0,25 9`) while the carbohydrate cell arrived as a bare `12g`, the `7,` having been fused into the
 * multilingual nutrient text.
 *
 * **A column preserving separators elsewhere says nothing about whether this cell's separator
 * survived.** Glyph loss is local. The two cases below are that measurement, kept as executable
 * evidence rather than prose: they assert what the two labels have in common, which is precisely why
 * no rule keyed on that property can separate them.
 */
class ColumnSeparatorScaleEvidenceTest {

    private fun separatedCellsInColumnOf(capture: SixteenthSessionCorpus.Capture): Int {
        val document = capture.passA()
        return LogicalRowBuilder.build(document)
            .filter { RowClassifier.classify(it) != NutritionRowKind.HEADER }
            .count { row ->
                row.elements.any { DECIMAL_BETWEEN_DIGITS.containsMatchIn(it.text) }
            }
    }

    private fun capture(suffix: String) =
        SixteenthSessionCorpus.captures.single { it.bundle.endsWith(suffix) }

    /**
     * The correct-but-withheld reading. Its column keeps separators; its own cell does not.
     */
    @Test
    fun `the correct seventy two sits in a column that kept its separators`() {
        val crisps = capture("124835-611")

        assertEquals(0, crisps.printedCarbs!!.compareTo(BigDecimal("72")))
        assertTrue(
            "the crisps column must show separated cells, or the rejected rule had nothing to act on",
            separatedCellsInColumnOf(crisps) >= 2,
        )
    }

    /**
     * The counter-example, and the reason the rule cannot exist.
     *
     * This capture has the **same observable property** as the one above — a column full of
     * separators around a separatorless carbohydrate cell — and here that cell is wrong by a factor
     * the user would dose insulin on. Any rule keyed on the property admits both or neither.
     */
    @Test
    fun `the wrong twelve sits in a column that also kept its separators`() {
        val lidl = capture("124620-112")

        assertEquals(0, lidl.printedCarbs!!.compareTo(BigDecimal("7.2")))
        assertTrue(
            "the red Lidl column also shows separated cells; this is what falsifies the rule",
            separatedCellsInColumnOf(lidl) >= 2,
        )
    }

    /**
     * The behavioural consequence: `12` is never offered, on either capture of that package.
     *
     * This is the assertion that would fail if the rejected rule were reintroduced.
     */
    @Test
    fun `the red Lidl twelve is never presented`() {
        listOf("124620-112", "124643-457").forEach { suffix ->
            val result = SixteenthSessionReplay.replay(capture(suffix))
            assertTrue(
                "$suffix offered ${result.offeredValue}",
                result.offeredValue == null ||
                    result.offeredValue.compareTo(BigDecimal("12")) != 0,
            )
        }
    }

    private companion object {
        val DECIMAL_BETWEEN_DIGITS = Regex("\\d[.,]\\d")
    }
}
